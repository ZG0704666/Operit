const { spawn } = require("child_process");
const os = require("os");

function createProcessService({ projectRoot, logger }) {
  function withPowerShellUtf8(command) {
    const prelude = [
      "$utf8NoBom = [System.Text.UTF8Encoding]::new($false)",
      "[Console]::InputEncoding = $utf8NoBom",
      "[Console]::OutputEncoding = $utf8NoBom",
      "$OutputEncoding = $utf8NoBom"
    ].join("; ");

    return `${prelude}; ${command}`;
  }

  function runProcess(executable, args, timeoutMs, options = {}) {
    return new Promise((resolve) => {
      const startedAt = Date.now();
      const windowsHide = options.windowsHide === undefined ? true : !!options.windowsHide;
      logger.info("runProcess.start", { executable, args, timeoutMs, windowsHide });

      const child = spawn(executable, args, {
        windowsHide,
        cwd: projectRoot
      });

      let stdout = "";
      let stderr = "";
      let timedOut = false;

      child.stdout.on("data", (chunk) => {
        stdout += chunk.toString();
      });

      child.stderr.on("data", (chunk) => {
        stderr += chunk.toString();
      });

      const timer = setTimeout(() => {
        timedOut = true;
        child.kill();
      }, timeoutMs);

      child.on("close", (code) => {
        clearTimeout(timer);
        const result = {
          exitCode: code === null ? -1 : code,
          stdout,
          stderr,
          timedOut,
          durationMs: Date.now() - startedAt
        };

        logger.info("runProcess.close", {
          executable,
          exitCode: result.exitCode,
          timedOut: result.timedOut,
          durationMs: result.durationMs,
          stdoutLength: result.stdout.length,
          stderrLength: result.stderr.length
        });
        resolve(result);
      });

      child.on("error", (error) => {
        clearTimeout(timer);
        const result = {
          exitCode: -1,
          stdout,
          stderr: (stderr ? stderr + "\n" : "") + error.message,
          timedOut,
          durationMs: Date.now() - startedAt
        };

        logger.error("runProcess.error", {
          executable,
          error: error.message,
          durationMs: result.durationMs
        });
        resolve(result);
      });
    });
  }

  function runCommand(shell, command, timeoutMs) {
    const mode = (shell || "powershell").toLowerCase();

    if (mode === "cmd") {
      const cmdCommand = `chcp 65001>nul & ${command}`;
      return runProcess("cmd.exe", ["/c", cmdCommand], timeoutMs);
    }

    if (mode === "pwsh") {
      const psCommand = withPowerShellUtf8(command);
      return runProcess("pwsh", ["-NoProfile", "-NonInteractive", "-Command", psCommand], timeoutMs);
    }

    const psCommand = withPowerShellUtf8(command);
    return runProcess(
      "powershell.exe",
      ["-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-Command", psCommand],
      timeoutMs
    );
  }

  function isPrivateLanIpv4(ipv4) {
    if (!ipv4 || typeof ipv4 !== "string") {
      return false;
    }

    if (ipv4.startsWith("10.")) {
      return true;
    }

    if (ipv4.startsWith("192.168.")) {
      return true;
    }

    const match = /^172\.(\d+)\./.exec(ipv4);
    if (!match) {
      return false;
    }

    const second = Number(match[1]);
    return Number.isFinite(second) && second >= 16 && second <= 31;
  }

  function getNetworkSnapshot() {
    const interfaces = os.networkInterfaces();
    const ipv4Candidates = [];

    for (const entries of Object.values(interfaces)) {
      for (const item of entries || []) {
        if (!item || item.family !== "IPv4" || item.internal) {
          continue;
        }
        if (typeof item.address === "string" && item.address.startsWith("169.254.")) {
          continue;
        }
        ipv4Candidates.push(item.address);
      }
    }

    const preferredLan = ipv4Candidates.find((ip) => isPrivateLanIpv4(ip));

    return {
      ipv4Candidates,
      preferredLan,
      recommendedHost: preferredLan || ipv4Candidates[0] || ""
    };
  }

  function getUserSnapshot() {
    const username = String(process.env.USERNAME || "").trim();
    const userDomain = String(process.env.USERDOMAIN || "").trim();
    const domainQualified = username && userDomain ? `${userDomain}\\${username}` : username;

    return {
      username,
      domain: userDomain,
      domainQualified
    };
  }

  return {
    runProcess,
    runCommand,
    getNetworkSnapshot,
    getUserSnapshot
  };
}

module.exports = {
  createProcessService
};
