const { readJsonBody, sendJson, parseBoolean } = require("../lib/http-utils");

const FILE_WRITE_JSON_MAX_BODY_BYTES = 8 * 1024 * 1024;
const FILE_WRITE_BASE64_JSON_MAX_BODY_BYTES = 24 * 1024 * 1024;

function buildPublicConfig(config, versionInfo) {
  return {
    bindAddress: config.bindAddress,
    port: config.port,
    allowRawCommands: !!config.allowRawCommands,
    maxCommandMs: config.maxCommandMs,
    allowedPresets: config.allowedPresets,
    apiTokenConfigured: !!config.apiToken,
    apiToken: config.apiToken || "",
    version: versionInfo.agentVersion
  };
}

function isAuthorized(config, token) {
  if (!config.apiToken) {
    return false;
  }

  return String(token || "") === config.apiToken;
}

function createApiHandler({
  state,
  configStore,
  processService,
  fileService,
  logger,
  presetCommands,
  runtimeInfo,
  versionInfo
}) {
  function getPresetList(config) {
    return Object.entries(presetCommands).map(([name, item]) => ({
      name,
      shell: item.shell,
      description: item.description,
      allowed: config.allowedPresets.includes(name)
    }));
  }

  function unauthorized(res, routeName, tokenProvided, extra = {}) {
    logger.warn(`${routeName}.unauthorized`, {
      tokenProvided: !!tokenProvided,
      ...extra
    });
    sendJson(res, 401, { ok: false, error: "Unauthorized" });
  }

  async function handleApiRequest(req, res, url) {
    const config = state.config;

    if (req.method === "GET" && url.pathname === "/api/health") {
      const network = processService.getNetworkSnapshot();
      const user = processService.getUserSnapshot();

      sendJson(res, 200, {
        ok: true,
        pid: runtimeInfo.pid(),
        host: runtimeInfo.host(),
        uptimeSec: runtimeInfo.uptimeSec(),
        bindAddress: config.bindAddress,
        port: config.port,
        mode: "http-agent",
        version: versionInfo.agentVersion,
        network,
        user
      });
      return true;
    }

    if (req.method === "GET" && url.pathname === "/api/config") {
      sendJson(res, 200, buildPublicConfig(config, versionInfo));
      return true;
    }

    if (req.method === "GET" && url.pathname === "/api/presets") {
      sendJson(res, 200, {
        items: getPresetList(config)
      });
      return true;
    }

    if (req.method === "POST" && url.pathname === "/api/config") {
      try {
        const body = await readJsonBody(req);
        const nextConfig = { ...state.config };

        if (typeof body.bindAddress === "string" && body.bindAddress.trim()) {
          nextConfig.bindAddress = body.bindAddress.trim();
        }

        if (body.port !== undefined) {
          const parsed = Number(body.port);
          if (!Number.isFinite(parsed) || parsed <= 0 || parsed > 65535) {
            sendJson(res, 400, { ok: false, error: "Invalid port" });
            return true;
          }
          nextConfig.port = Math.floor(parsed);
        }

        if (body.maxCommandMs !== undefined) {
          const parsed = Number(body.maxCommandMs);
          if (!Number.isFinite(parsed) || parsed < 1000 || parsed > 600000) {
            sendJson(res, 400, { ok: false, error: "maxCommandMs must be 1000..600000" });
            return true;
          }
          nextConfig.maxCommandMs = Math.floor(parsed);
        }

        if (body.allowRawCommands !== undefined) {
          nextConfig.allowRawCommands = parseBoolean(body.allowRawCommands, false);
        }

        if (body.apiToken !== undefined) {
          nextConfig.apiToken = configStore.ensureApiToken(body.apiToken);
        }

        nextConfig.apiToken = configStore.ensureApiToken(nextConfig.apiToken);

        if (Array.isArray(body.allowedPresets)) {
          nextConfig.allowedPresets = configStore.normalizeAllowedPresets(body.allowedPresets);
        }

        const restartRequired =
          nextConfig.port !== state.config.port || nextConfig.bindAddress !== state.config.bindAddress;

        state.config = nextConfig;
        configStore.saveConfig(state.config);

        logger.info("config.update.success", {
          bindAddress: state.config.bindAddress,
          port: state.config.port,
          allowRawCommands: state.config.allowRawCommands,
          maxCommandMs: state.config.maxCommandMs,
          allowedPresets: state.config.allowedPresets,
          restartRequired
        });

        sendJson(res, 200, {
          ok: true,
          restartRequired,
          config: buildPublicConfig(state.config, versionInfo)
        });
      } catch (error) {
        logger.error("config.update.error", { error: error.message });
        sendJson(res, 400, { ok: false, error: error.message });
      }

      return true;
    }

    if (req.method === "POST" && url.pathname === "/api/command/execute") {
      try {
        const body = await readJsonBody(req);

        logger.info("command.execute.request", {
          hasPreset: !!body.preset,
          preset: body.preset || null,
          shell: body.shell || null,
          rawCommandLength: typeof body.command === "string" ? body.command.length : 0,
          timeoutMs: body.timeoutMs || null,
          tokenProvided: !!body.token
        });

        if (!isAuthorized(state.config, body.token)) {
          unauthorized(res, "command.execute", body.token, {
            preset: body.preset || null
          });
          return true;
        }

        const timeout =
          typeof body.timeoutMs === "number" && body.timeoutMs > 0
            ? Math.min(body.timeoutMs, 600000)
            : state.config.maxCommandMs;

        let shell = "powershell";
        let command = "";

        if (body.preset) {
          if (!presetCommands[body.preset]) {
            logger.warn("command.execute.invalidPreset", { preset: body.preset });
            sendJson(res, 400, { ok: false, error: "Unknown preset" });
            return true;
          }

          if (!state.config.allowedPresets.includes(body.preset)) {
            logger.warn("command.execute.presetNotAllowed", { preset: body.preset });
            sendJson(res, 403, { ok: false, error: "Preset is not allowed by config" });
            return true;
          }

          shell = presetCommands[body.preset].shell;
          command = presetCommands[body.preset].command;
        } else {
          if (!state.config.allowRawCommands) {
            logger.warn("command.execute.rawDisabled", {});
            sendJson(res, 403, { ok: false, error: "Raw command mode is disabled" });
            return true;
          }

          if (!body.command || typeof body.command !== "string") {
            logger.warn("command.execute.missingCommand", {});
            sendJson(res, 400, { ok: false, error: "Missing command" });
            return true;
          }

          shell = String(body.shell || "powershell").toLowerCase();
          command = body.command;
        }

        const result = await processService.runCommand(shell, command, timeout);

        logger.info("command.execute.result", {
          ok: result.exitCode === 0,
          shell,
          exitCode: result.exitCode,
          timedOut: result.timedOut,
          durationMs: result.durationMs,
          stdoutLength: result.stdout.length,
          stderrLength: result.stderr.length
        });

        sendJson(res, 200, {
          ok: result.exitCode === 0,
          shell,
          command,
          exitCode: result.exitCode,
          timedOut: result.timedOut,
          durationMs: result.durationMs,
          stdout: result.stdout,
          stderr: result.stderr
        });
      } catch (error) {
        logger.error("command.execute.error", { error: error.message });
        sendJson(res, 400, { ok: false, error: error.message });
      }

      return true;
    }

    if (req.method === "POST" && url.pathname === "/api/file/list") {
      try {
        const body = await readJsonBody(req);
        if (!isAuthorized(state.config, body.token)) {
          unauthorized(res, "file.list", body.token);
          return true;
        }

        const result = fileService.listDirectory(body.path, body.depth);
        logger.info("file.list.success", {
          path: result.path,
          depth: result.depth,
          itemCount: result.items.length
        });

        sendJson(res, 200, {
          ok: true,
          ...result
        });
      } catch (error) {
        logger.error("file.list.error", { error: error.message });
        sendJson(res, 400, { ok: false, error: error.message });
      }

      return true;
    }

    if (req.method === "POST" && url.pathname === "/api/file/read") {
      try {
        const body = await readJsonBody(req);
        if (!isAuthorized(state.config, body.token)) {
          unauthorized(res, "file.read", body.token);
          return true;
        }

        const result = fileService.readTextFile(body.path, body.encoding);
        logger.info("file.read.success", {
          path: result.path,
          sizeBytes: result.sizeBytes,
          encoding: result.encoding
        });

        sendJson(res, 200, {
          ok: true,
          ...result
        });
      } catch (error) {
        logger.error("file.read.error", { error: error.message });
        sendJson(res, 400, { ok: false, error: error.message });
      }

      return true;
    }

    if (req.method === "POST" && url.pathname === "/api/file/read_segment") {
      try {
        const body = await readJsonBody(req);
        if (!isAuthorized(state.config, body.token)) {
          unauthorized(res, "file.read_segment", body.token);
          return true;
        }

        const result = fileService.readTextSegment(body.path, {
          offset: body.offset,
          length: body.length,
          encoding: body.encoding
        });

        logger.info("file.read_segment.success", {
          path: result.path,
          offset: result.offset,
          length: result.length,
          totalBytes: result.totalBytes,
          eof: result.eof,
          encoding: result.encoding
        });

        sendJson(res, 200, {
          ok: true,
          ...result
        });
      } catch (error) {
        logger.error("file.read_segment.error", { error: error.message });
        sendJson(res, 400, { ok: false, error: error.message });
      }

      return true;
    }

    if (req.method === "POST" && url.pathname === "/api/file/read_lines") {
      try {
        const body = await readJsonBody(req);
        if (!isAuthorized(state.config, body.token)) {
          unauthorized(res, "file.read_lines", body.token);
          return true;
        }

        const result = fileService.readTextLines(body.path, {
          line_start: body.line_start,
          line_end: body.line_end,
          encoding: body.encoding
        });

        logger.info("file.read_lines.success", {
          path: result.path,
          lineStart: result.lineStart,
          lineEnd: result.lineEnd,
          totalLines: result.totalLines,
          eof: result.eof,
          encoding: result.encoding
        });

        sendJson(res, 200, {
          ok: true,
          ...result
        });
      } catch (error) {
        logger.error("file.read_lines.error", { error: error.message });
        sendJson(res, 400, { ok: false, error: error.message });
      }

      return true;
    }

    if (req.method === "POST" && url.pathname === "/api/file/write") {
      try {
        const body = await readJsonBody(req, {
          maxBodyBytes: FILE_WRITE_JSON_MAX_BODY_BYTES
        });
        if (!isAuthorized(state.config, body.token)) {
          unauthorized(res, "file.write", body.token);
          return true;
        }

        const result = fileService.writeTextFile(body.path, body.content, body.encoding);
        logger.info("file.write.success", {
          path: result.path,
          sizeBytes: result.sizeBytes,
          encoding: result.encoding
        });

        sendJson(res, 200, {
          ok: true,
          ...result
        });
      } catch (error) {
        logger.error("file.write.error", { error: error.message });
        sendJson(res, 400, { ok: false, error: error.message });
      }

      return true;
    }

    if (req.method === "POST" && url.pathname === "/api/file/edit") {
      try {
        const body = await readJsonBody(req, {
          maxBodyBytes: FILE_WRITE_JSON_MAX_BODY_BYTES
        });
        if (!isAuthorized(state.config, body.token)) {
          unauthorized(res, "file.edit", body.token);
          return true;
        }

        const result = fileService.editTextFile(
          body.path,
          body.old_text,
          body.new_text,
          body.expected_replacements,
          body.encoding
        );
        logger.info("file.edit.success", {
          path: result.path,
          sizeBytes: result.sizeBytes,
          encoding: result.encoding,
          replacements: result.replacements,
          expectedReplacements: result.expectedReplacements
        });

        sendJson(res, 200, {
          ok: true,
          ...result
        });
      } catch (error) {
        logger.error("file.edit.error", { error: error.message });
        sendJson(res, 400, { ok: false, error: error.message });
      }

      return true;
    }

    if (req.method === "POST" && url.pathname === "/api/file/read_base64") {
      try {
        const body = await readJsonBody(req);
        if (!isAuthorized(state.config, body.token)) {
          unauthorized(res, "file.read_base64", body.token);
          return true;
        }

        const result = fileService.readBase64File(body.path, {
          offset: body.offset,
          length: body.length
        });

        logger.info("file.read_base64.success", {
          path: result.path,
          offset: result.offset,
          length: result.length,
          totalBytes: result.totalBytes,
          eof: result.eof
        });

        sendJson(res, 200, {
          ok: true,
          ...result
        });
      } catch (error) {
        logger.error("file.read_base64.error", { error: error.message });
        sendJson(res, 400, { ok: false, error: error.message });
      }

      return true;
    }

    if (req.method === "POST" && url.pathname === "/api/file/write_base64") {
      try {
        const body = await readJsonBody(req, {
          maxBodyBytes: FILE_WRITE_BASE64_JSON_MAX_BODY_BYTES
        });
        if (!isAuthorized(state.config, body.token)) {
          unauthorized(res, "file.write_base64", body.token);
          return true;
        }

        const result = fileService.writeBase64File(body.path, body.base64);
        logger.info("file.write_base64.success", {
          path: result.path,
          sizeBytes: result.sizeBytes
        });

        sendJson(res, 200, {
          ok: true,
          ...result
        });
      } catch (error) {
        logger.error("file.write_base64.error", { error: error.message });
        sendJson(res, 400, { ok: false, error: error.message });
      }

      return true;
    }

    return false;
  }

  return {
    handleApiRequest
  };
}

module.exports = {
  createApiHandler
};
