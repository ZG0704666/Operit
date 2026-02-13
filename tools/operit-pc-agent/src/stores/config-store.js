const fs = require("fs");
const crypto = require("crypto");
const { ensureDir } = require("../lib/fs-utils");

function createConfigStore({ dataDir, configPath, defaultConfig, presetCommands }) {
  function generateApiToken() {
    return crypto.randomBytes(24).toString("base64url");
  }

  function ensureApiToken(value) {
    const token = String(value || "").trim();
    if (token) {
      return token;
    }
    return generateApiToken();
  }

  function normalizeAllowedPresets(value) {
    if (!Array.isArray(value)) {
      return [...defaultConfig.allowedPresets];
    }

    return value.filter((name) => presetCommands[name]);
  }

  function loadConfig() {
    ensureDir(dataDir);

    if (!fs.existsSync(configPath)) {
      const initialConfig = {
        ...defaultConfig,
        apiToken: ensureApiToken(defaultConfig.apiToken)
      };
      fs.writeFileSync(configPath, JSON.stringify(initialConfig, null, 2), "utf8");
      return initialConfig;
    }

    try {
      const parsed = JSON.parse(fs.readFileSync(configPath, "utf8"));
      const normalized = {
        ...defaultConfig,
        ...parsed,
        allowedPresets: normalizeAllowedPresets(parsed.allowedPresets),
        apiToken: ensureApiToken(parsed.apiToken)
      };

      fs.writeFileSync(configPath, JSON.stringify(normalized, null, 2), "utf8");
      return normalized;
    } catch {
      const backup = configPath + ".broken." + Date.now();
      fs.renameSync(configPath, backup);
      const fallbackConfig = {
        ...defaultConfig,
        apiToken: ensureApiToken(defaultConfig.apiToken)
      };
      fs.writeFileSync(configPath, JSON.stringify(fallbackConfig, null, 2), "utf8");
      return fallbackConfig;
    }
  }

  function saveConfig(config) {
    ensureDir(dataDir);
    fs.writeFileSync(configPath, JSON.stringify(config, null, 2), "utf8");
  }

  return {
    loadConfig,
    saveConfig,
    normalizeAllowedPresets,
    generateApiToken,
    ensureApiToken
  };
}

module.exports = {
  createConfigStore
};
