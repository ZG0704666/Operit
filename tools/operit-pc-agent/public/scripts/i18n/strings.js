const STORAGE_KEY = "operit.pc-agent.locale";

const RESOURCES = {
  en: {
    language: {
      english: "English",
      chinese: "中文"
    },
    ui: {
      title: "Operit PC Agent Console",
      subtitle: "Windows bridge for mobile integration, secure HTTP relay, and command validation"
    },
    nav: {
      wizard: "Setup Wizard",
      commands: "Commands",
      settings: "Settings"
    },
    action: {
      refreshAll: "Refresh All",
      refreshHealth: "Refresh Health",
      runPreset: "Run Preset",
      runRaw: "Run Raw",
      saveConfig: "Save Config",
      runOpenSshSetup: "Run Verification",
      saveAndNext: "Save and Next",
      verifyAndNext: "Verify and Next",
      skipToNext: "Skip to Next",
      generateMobileSnippet: "Generate Mobile Snippet",
      copyJson: "Copy JSON",
      copyEnv: "Copy ENV",
      oneClickFill: "One-click Fill",
      copyPayload: "Copy",
      toggleAdvancedShow: "Advanced",
      toggleAdvancedHide: "Hide Advanced",
      toCommands: "Go to Commands",
      toSettings: "Go to Settings",
      working: "Working...",
      running: "Running...",
      refreshing: "Refreshing..."
    },
    status: {
      host: "Host",
      lanIp: "LAN IPv4",
      pid: "PID",
      uptime: "Uptime",
      ssh: "Mode",
      version: "Version",
      unknown: "unknown",
      ready: "Ready",
      loading: "Loading config and health data...",
      healthRefreshed: "Health refreshed",
      healthRefreshFailed: "Health refresh failed: {error}",
      dataRefreshed: "Data refreshed",
      refreshFailed: "Refresh failed: {error}",
      initializationFailed: "Initialization failed: {error}"
    },
    card: {
      healthTitle: "Health Status",
      healthSubtitle: "Agent runtime, bind target and HTTP relay status",
      commandTitle: "Command Runner",
      commandSubtitle: "Preset mode is safer. Use raw mode only when needed",
      configTitle: "Configuration",
      configSubtitle: "Manage bind address, token, timeout and allowed presets",
      openSshTitle: "Verification",
      openSshSubtitle: "Use command presets to verify HTTP relay"
    },
    panel: {
      presetMode: "Preset Mode",
      rawMode: "Raw Mode",
      allowedPresets: "Allowed Presets"
    },
    wizard: {
      title: "Mobile Integration Wizard",
      subtitle: "Use this guided flow to configure PC-side bridge and generate mobile-side env values.",
      stepNav1: "1. Base Config",
      stepNav2: "2. Verify Relay",
      stepNav3: "3. Verify Commands",
      stepNav4: "4. Mobile Fill",
      step1Title: "Step 1: Make PC reachable from phone",
      step1Desc: "Use LAN IPv4 for bind address (for example 192.168.x.x). Keep port default unless needed. API token is required.",
      step1Hint: "Do not use 127.0.0.1 for phone access. Restart service after changing bind address or port.",
      step2Title: "Step 2: Verify HTTP relay reachability",
      step2Desc: "Run health_probe and whoami to ensure this PC agent can execute commands from mobile.",
      step3Title: "Step 3: Verify command pipeline",
      step3Desc: "Run list_processes and hostname to validate command responses.",
      step4Title: "Step 4: Mobile Paste Config",
      step4Desc: "Use one-click fill, then copy the config text to mobile app.",
      step4Hint: "Required: WINDOWS_AGENT_BASE_URL and WINDOWS_AGENT_TOKEN.",
      oneClickTitle: "Config Text",
      oneClickDesc: "Auto-fills available values and generates text for mobile paste.",
      advancedTitle: "Advanced override",
      mobileJsonTitle: "Configuration Text",
      mobileEnvTitle: "Configuration Text"
    },
    field: {
      token: "Token",
      preset: "Preset",
      shell: "Shell",
      command: "Command",
      bindAddress: "Bind Address",
      port: "Port",
      maxCommandTimeoutMs: "Max Command Timeout (ms)",
      apiToken: "API Token",
      allowRawHighRisk: "Allow raw commands (high risk)",
      windowsAgentBaseUrl: "WINDOWS_AGENT_BASE_URL",
      windowsAgentToken: "WINDOWS_AGENT_TOKEN",
      windowsAgentDefaultShell: "WINDOWS_AGENT_DEFAULT_SHELL",
      windowsAgentTimeoutMs: "WINDOWS_AGENT_TIMEOUT_MS"
    },
    placeholder: {
      tokenRequiredWhenApiTokenEnabled: "Required",
      rawCommandExample: "Example: Get-Process | Select-Object -First 5 Name,Id",
      apiTokenEmptyDisable: "Leave empty to auto-generate a new token",
      noPresetsAvailable: "(no presets available)",
      loadingPresets: "(loading...)",
      mobileHost: "LAN IP or reachable host, not 127.0.0.1 for phone",
      mobileBaseUrl: "Example: http://192.168.1.8:58321",
      passwordOrPrivateKey: "Provide password or private key"
    },
    preset: {
      disabledSuffix: " (disabled)"
    },
    message: {
      configSaved: "Configuration saved",
      configSavedRestartRequired: "Configuration saved. Restart service is required because bind address or port changed.",
      configSaveFailed: "Failed to save config: {error}",
      openSshFinished: "Verification finished",
      openSshWarn: "Verification returned warnings",
      openSshFailed: "Verification failed: {error}",
      openSshRequiresAdmin: "",
      openSshElevationCancelled: "",
      openSshBlockedByPolicy: "",
      presetFinished: "Preset command finished",
      presetWarn: "Preset command returned warnings",
      presetFailed: "Preset command failed: {error}",
      rawFinished: "Raw command finished",
      rawWarn: "Raw command returned warnings",
      rawFailed: "Raw command failed: {error}",
      wizardStep1Done: "Step 1 completed",
      wizardStep2Done: "Step 2 completed",
      wizardStep3Done: "Verification succeeded. Ready for mobile fill.",
      wizardStep3Warn: "Verification completed with warnings. Check output details.",
      wizardStep3Failed: "Verification failed: {error}",
      mobileSnippetGenerated: "Mobile snippet generated",
      oneClickFilled: "Auto fill completed",
      copyPayloadSuccess: "Copied",
      copyJsonSuccess: "Copied",
      copyEnvSuccess: "Copied",
      copyFailed: "Copy failed: {error}"
    },
    error: {
      unknown: "Unknown error"
    }
  },
  zh: {
    language: {
      english: "English",
      chinese: "中文"
    },
    ui: {
      title: "Operit PC Agent 控制台",
      subtitle: "面向移动端对接的 Windows 桥接服务：安全 HTTP 中转与命令验证"
    },
    nav: {
      wizard: "配置向导",
      commands: "命令执行",
      settings: "高级设置"
    },
    action: {
      refreshAll: "刷新全部",
      refreshHealth: "刷新健康状态",
      runPreset: "运行预设",
      runRaw: "运行 Raw",
      saveConfig: "保存配置",
      runOpenSshSetup: "执行连通验证",
      saveAndNext: "保存并下一步",
      verifyAndNext: "验证并下一步",
      skipToNext: "跳过并下一步",
      generateMobileSnippet: "生成移动端片段",
      copyJson: "复制",
      copyEnv: "复制",
      oneClickFill: "一键填写",
      copyPayload: "复制",
      toggleAdvancedShow: "高级配置",
      toggleAdvancedHide: "收起高级",
      toCommands: "前往命令页",
      toSettings: "前往设置页",
      working: "处理中...",
      running: "执行中...",
      refreshing: "刷新中..."
    },
    status: {
      host: "主机",
      lanIp: "局域网IPv4",
      pid: "PID",
      uptime: "运行时长",
      ssh: "模式",
      version: "版本",
      unknown: "未知",
      ready: "就绪",
      loading: "正在加载配置与健康状态...",
      healthRefreshed: "健康状态已刷新",
      healthRefreshFailed: "健康状态刷新失败: {error}",
      dataRefreshed: "数据刷新完成",
      refreshFailed: "刷新失败: {error}",
      initializationFailed: "初始化失败: {error}"
    },
    card: {
      healthTitle: "健康状态",
      healthSubtitle: "查看 Agent 运行状态、绑定信息与 HTTP 中转状态",
      commandTitle: "命令执行",
      commandSubtitle: "预设模式更安全，Raw 模式仅在必要时使用",
      configTitle: "配置中心",
      configSubtitle: "管理监听地址、令牌、超时与允许预设",
      openSshTitle: "连通验证",
      openSshSubtitle: "使用预设命令验证 HTTP 中转"
    },
    panel: {
      presetMode: "预设模式",
      rawMode: "Raw 模式",
      allowedPresets: "允许的预设"
    },
    wizard: {
      title: "移动端对接配置向导",
      subtitle: "按步骤完成电脑端桥接配置，并生成移动端一键配置可直接填写的环境变量。",
      stepNav1: "1. 基础配置",
      stepNav2: "2. 中转验证",
      stepNav3: "3. 命令验证",
      stepNav4: "4. 移动端填写",
      step1Title: "步骤 1：让手机能访问这台电脑",
      step1Desc: "绑定地址用局域网 IPv4（如 192.168.x.x）。端口默认即可。API 令牌必填。",
      step1Hint: "给手机访问时不要用 127.0.0.1。改了绑定地址或端口后要重启服务。",
      step2Title: "步骤 2：验证 HTTP 中转可达性",
      step2Desc: "执行 health_probe 与 whoami，确认手机侧可通过 Agent 调用命令。",
      step3Title: "步骤 3：验证命令通路",
      step3Desc: "执行 list_processes 与 hostname，确认命令返回链路正常。",
      step4Title: "步骤 4：移动端粘贴配置",
      step4Desc: "先一键填写，再复制配置文本到移动端粘贴。",
      step4Hint: "必填：WINDOWS_AGENT_BASE_URL 与 WINDOWS_AGENT_TOKEN。",
      oneClickTitle: "配置文本",
      oneClickDesc: "自动填充可获取项，并生成给移动端粘贴的配置文本。",
      advancedTitle: "高级覆盖",
      mobileJsonTitle: "配置文本",
      mobileEnvTitle: "配置文本"
    },
    field: {
      token: "令牌",
      preset: "预设",
      shell: "Shell",
      command: "命令",
      bindAddress: "绑定地址",
      port: "端口",
      maxCommandTimeoutMs: "最大命令超时 (ms)",
      apiToken: "API 令牌",
      allowRawHighRisk: "允许 Raw 命令（高风险）",
      windowsAgentBaseUrl: "WINDOWS_AGENT_BASE_URL",
      windowsAgentToken: "WINDOWS_AGENT_TOKEN",
      windowsAgentDefaultShell: "WINDOWS_AGENT_DEFAULT_SHELL",
      windowsAgentTimeoutMs: "WINDOWS_AGENT_TIMEOUT_MS"
    },
    placeholder: {
      tokenRequiredWhenApiTokenEnabled: "必填",
      rawCommandExample: "示例: Get-Process | Select-Object -First 5 Name,Id",
      apiTokenEmptyDisable: "留空会自动生成新令牌",
      noPresetsAvailable: "（暂无可用预设）",
      loadingPresets: "（加载中...）",
      mobileHost: "填写局域网 IP 或可达主机，不要给手机端写 127.0.0.1",
      mobileBaseUrl: "示例: http://192.168.1.8:58321",
      passwordOrPrivateKey: "填写密码或私钥其中之一"
    },
    preset: {
      disabledSuffix: "（已禁用）"
    },
    message: {
      configSaved: "配置保存成功",
      configSavedRestartRequired: "配置已保存。由于绑定地址或端口变化，需要重启服务。",
      configSaveFailed: "配置保存失败: {error}",
      openSshFinished: "验证完成",
      openSshWarn: "验证返回告警",
      openSshFailed: "验证失败: {error}",
      openSshRequiresAdmin: "",
      openSshElevationCancelled: "",
      openSshBlockedByPolicy: "",
      presetFinished: "预设命令执行完成",
      presetWarn: "预设命令返回告警",
      presetFailed: "预设命令执行失败: {error}",
      rawFinished: "Raw 命令执行完成",
      rawWarn: "Raw 命令返回告警",
      rawFailed: "Raw 命令执行失败: {error}",
      wizardStep1Done: "步骤 1 已完成",
      wizardStep2Done: "步骤 2 已完成",
      wizardStep3Done: "验证通过，可进入移动端填写。",
      wizardStep3Warn: "验证存在告警，请查看输出详情。",
      wizardStep3Failed: "验证失败: {error}",
      mobileSnippetGenerated: "移动端片段已生成",
      oneClickFilled: "一键填写完成",
      copyPayloadSuccess: "已复制",
      copyJsonSuccess: "已复制",
      copyEnvSuccess: "已复制",
      copyFailed: "复制失败: {error}"
    },
    error: {
      unknown: "未知错误"
    }
  }
};

function normalizeLocale(raw) {
  const value = String(raw || "").trim().toLowerCase();
  if (value.startsWith("zh")) {
    return "zh";
  }
  return "en";
}

function getByPath(target, path) {
  return String(path)
    .split(".")
    .reduce((current, key) => (current && Object.prototype.hasOwnProperty.call(current, key) ? current[key] : undefined), target);
}

function interpolate(template, values) {
  if (!values) {
    return template;
  }

  return template.replace(/\{([a-zA-Z0-9_]+)\}/g, (match, key) => {
    if (Object.prototype.hasOwnProperty.call(values, key)) {
      return String(values[key]);
    }
    return match;
  });
}

function readStoredLocale() {
  try {
    return window.localStorage.getItem(STORAGE_KEY) || "";
  } catch {
    return "";
  }
}

function writeStoredLocale(locale) {
  try {
    window.localStorage.setItem(STORAGE_KEY, locale);
  } catch {
    // ignore storage errors
  }
}

export function createI18n() {
  const browserLocale = typeof navigator !== "undefined" ? navigator.language : "en";
  let locale = normalizeLocale(readStoredLocale() || browserLocale);

  function t(key, values) {
    const primary = getByPath(RESOURCES[locale], key);
    const fallback = getByPath(RESOURCES.en, key);
    const text = primary !== undefined ? primary : fallback;
    if (typeof text === "string") {
      return interpolate(text, values);
    }
    return key;
  }

  function setLocale(nextLocale) {
    locale = normalizeLocale(nextLocale);
    writeStoredLocale(locale);
    return locale;
  }

  function getLocale() {
    return locale;
  }

  return {
    t,
    setLocale,
    getLocale
  };
}
