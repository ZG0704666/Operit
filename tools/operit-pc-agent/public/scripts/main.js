import { api } from "./services/api.js";
import { createI18n } from "./i18n/strings.js";
import { mount, render } from "./ui/runtime.js";
import * as W from "./ui/widgets.js";
import { createWizardPage, createWizardController } from "./features/wizard-page.js";
import { createCommandsPage, createCommandsController } from "./features/commands-page.js";
import { createSettingsPage, createSettingsController } from "./features/settings-page.js";
import { createProcessesPage, createProcessesController } from "./features/processes-page.js";

const refs = {};
const i18n = createI18n();
const t = (key, values) => i18n.t(key, values);

const ROUTES = {
  WIZARD: "wizard",
  COMMANDS: "commands",
  MANAGE: "manage",
  SETTINGS: "settings"
};

const ROUTE_ORDER = [ROUTES.WIZARD, ROUTES.COMMANDS, ROUTES.MANAGE, ROUTES.SETTINGS];

const state = {
  route: ROUTES.WIZARD,
  wizardStep: 0,
  wizardAdvancedVisible: false,
  wizardBindAutoApplied: false,
  health: null,
  config: null,
  presetItems: []
};

let wizardController = null;
let commandsController = null;
let manageController = null;
let settingsController = null;
let toastSeq = 0;

function getRouteFromHash() {
  const hash = String(window.location.hash || "").replace(/^#\/?/, "").trim();
  if (ROUTE_ORDER.includes(hash)) {
    return hash;
  }
  return ROUTES.WIZARD;
}

function navigateToRoute(route) {
  const safeRoute = ROUTE_ORDER.includes(route) ? route : ROUTES.WIZARD;
  const nextHash = `#/${safeRoute}`;
  if (window.location.hash !== nextHash) {
    window.location.hash = nextHash;
    return;
  }
  state.route = safeRoute;
  applyRouteUi();
}

function setJsonOutput(refName, payload) {
  const node = refs[refName];
  if (!node) {
    return;
  }

  if (typeof payload === "string") {
    node.textContent = payload;
    return;
  }

  node.textContent = JSON.stringify(payload, null, 2);
}

function setNotice(tone, text) {
  const stack = refs.toastStack;
  const content = String(text || "").trim();
  if (!stack || !content) {
    return;
  }

  const normalizedTone = tone === "ok" || tone === "warn" || tone === "error" ? tone : "";
  const toast = document.createElement("div");
  toast.className = `toast${normalizedTone ? ` is-${normalizedTone}` : ""}`;
  toast.dataset.toastId = String(++toastSeq);
  toast.textContent = content;

  stack.appendChild(toast);
  requestAnimationFrame(() => {
    toast.classList.add("is-visible");
  });

  const ttlMs = normalizedTone === "error" ? 5200 : normalizedTone === "warn" ? 4200 : 2400;
  let removed = false;

  const removeToast = () => {
    if (removed) {
      return;
    }
    removed = true;
    toast.classList.remove("is-visible");
    window.setTimeout(() => {
      if (toast.parentNode === stack) {
        stack.removeChild(toast);
      }
    }, 180);
  };

  const timerId = window.setTimeout(removeToast, ttlMs);
  toast.addEventListener(
    "click",
    () => {
      window.clearTimeout(timerId);
      removeToast();
    },
    { once: true }
  );
}

function asErrorMessage(error) {
  return error && error.message ? error.message : t("error.unknown");
}

function setBusy(buttonRefName, busy, idleText, busyText) {
  const button = refs[buttonRefName];
  if (!button) {
    return;
  }

  button.disabled = !!busy;
  button.textContent = busy ? busyText : idleText;
}

function createControllers() {
  const helpers = {
    setBusy,
    setNotice,
    setJsonOutput,
    asErrorMessage
  };

  commandsController = createCommandsController({ api, refs, t, helpers });

  manageController = createProcessesController({ api, refs, t, helpers });

  settingsController = createSettingsController({
    api,
    refs,
    t,
    W,
    render,
    helpers,
    callbacks: {
      onConfigSaved: async () => {
        await Promise.all([loadConfigAndPresets({ showOutput: false }), refreshHealth()]);
      }
    }
  });

  wizardController = createWizardController({
    api,
    refs,
    state,
    t,
    helpers,
    callbacks: {
      refreshHealth,
      reloadConfigAndHealth: async () => {
        await Promise.all([loadConfigAndPresets({ showOutput: false }), refreshHealth()]);
      },
      onConfigUpdated: (config) => {
        state.config = config;
        settingsController.fillSettingsForm(config);
        wizardController.fillWizardStep1Form(config);
      }
    }
  });
}

function createTree() {
  return W.Shell(
    {},
    W.Header(
      {},
      W.HeaderTop(
        {},
        W.Box({}, W.Title(t("ui.title")), W.Subtitle(t("ui.subtitle"))),
        W.Toolbar(
          {},
          W.Select({
            ref: "localeSelect",
            className: "locale-select",
            options: [
              { value: "zh", label: t("language.chinese") },
              { value: "en", label: t("language.english") }
            ],
            on: { change: handleLocaleChange }
          }),
          W.Button({ text: t("action.refreshAll"), variant: "soft", ref: "refreshAllButton", on: { click: refreshAll } })
        )
      ),
      W.StatusRow(
        {},
        W.StatusItem({ label: t("status.host"), valueRef: "hostValue" }),
        W.StatusItem({ label: t("status.lanIp"), valueRef: "lanIpValue" }),
        W.StatusItem({ label: t("status.pid"), valueRef: "pidValue" }),
        W.StatusItem({ label: t("status.uptime"), valueRef: "uptimeValue" }),
        W.StatusItem({ label: t("status.ssh"), valueRef: "sshValue" }),
        W.StatusItem({ label: t("status.version"), valueRef: "versionValue" })
      )
    ),

    W.Box(
      { className: "route-tabs" },
      W.Button({ text: t("nav.wizard"), variant: "soft", className: "route-tab", ref: "tabWizardButton", on: { click: () => navigateToRoute(ROUTES.WIZARD) } }),
      W.Button({ text: t("nav.commands"), variant: "soft", className: "route-tab", ref: "tabCommandsButton", on: { click: () => navigateToRoute(ROUTES.COMMANDS) } }),
      W.Button({ text: t("nav.manage"), variant: "soft", className: "route-tab", ref: "tabManageButton", on: { click: () => navigateToRoute(ROUTES.MANAGE) } }),
      W.Button({ text: t("nav.settings"), variant: "soft", className: "route-tab", ref: "tabSettingsButton", on: { click: () => navigateToRoute(ROUTES.SETTINGS) } })
    ),

    W.Box(
      { className: "route-pages" },
      createWizardPage({
        t,
        W,
        on: {
          wizardStep1: () => wizardController.setWizardStep(0),
          wizardStep2: () => wizardController.setWizardStep(1),
          wizardStep3: () => wizardController.setWizardStep(2),
          wizardStep4: () => wizardController.setWizardStep(3),
          wizardSaveNext: wizardController.handleWizardStep1SaveNext,
          wizardToSettings: () => navigateToRoute(ROUTES.SETTINGS),
          wizardRunSsh: wizardController.handleWizardStep2Run,
          wizardSkipSsh: () => wizardController.setWizardStep(2),
          wizardVerify: wizardController.handleWizardStep3Verify,
          wizardSkipVerify: () => wizardController.setWizardStep(3),
          wizardOneClickFill: wizardController.handleWizardOneClickFill,
          wizardCopyPayload: wizardController.handleWizardCopyPayload,
          wizardToggleAdvanced: wizardController.handleWizardToggleAdvanced,
          mobileInput: wizardController.handleMobileSnippetInput
        }
      }),
      createCommandsPage({
        t,
        W,
        on: {
          runPreset: commandsController.handleRunPreset,
          runRaw: commandsController.handleRunRaw
        }
      }),
      createProcessesPage({
        t,
        W,
        on: {
          refreshSessions: manageController.refreshSessions,
          createSession: manageController.createSession,
          sendCtrlC: manageController.sendCtrlC,
          closeSelectedSession: manageController.closeSelectedSession
        }
      }),
      createSettingsPage({
        t,
        W,
        on: {
          saveConfig: settingsController.saveConfigFromSettings
        }
      })
    ),

    W.Box({ className: "toast-stack", ref: "toastStack" })
  );
}

function updateHealthSummary(health) {
  const preferredLan =
    health && health.network && health.network.preferredLan
      ? String(health.network.preferredLan).trim()
      : "";
  const recommendedHost =
    health && health.network && health.network.recommendedHost
      ? String(health.network.recommendedHost).trim()
      : "";

  refs.hostValue.textContent = health.host || "-";
  refs.lanIpValue.textContent = preferredLan || (recommendedHost && recommendedHost !== "127.0.0.1" ? recommendedHost : "-");
  refs.pidValue.textContent = health.pid ? String(health.pid) : "-";
  refs.uptimeValue.textContent = health.uptimeSec !== undefined ? `${health.uptimeSec}s` : "-";
  refs.sshValue.textContent = health.mode ? String(health.mode) : t("status.unknown");
  refs.versionValue.textContent = health.version ? String(health.version) : "-";
}

async function refreshHealth() {
  const health = await api.getHealth();
  state.health = health;
  updateHealthSummary(health);
  wizardController.syncFromState({ forceMobileDefaults: false });
  return health;
}

async function loadConfigAndPresets(options = {}) {
  const showOutput = options.showOutput !== false;
  const [config, presets] = await Promise.all([api.getConfig(), api.getPresets()]);

  state.config = config;
  state.presetItems = presets.items || [];

  settingsController.fillSettingsForm(config);
  wizardController.fillWizardStep1Form(config);

  if (refs.versionValue) {
    refs.versionValue.textContent = config.version ? String(config.version) : "-";
  }

  settingsController.renderPresetChecklist(state.presetItems, config.allowedPresets || []);
  commandsController.renderPresetSelect(state.presetItems, config.allowedPresets || []);

  if (refs.commandTokenInput && !refs.commandTokenInput.value.trim()) {
    refs.commandTokenInput.value = config.apiToken ? String(config.apiToken) : "";
  }

  if (manageController) {
    manageController.prefillToken(config.apiToken ? String(config.apiToken) : "");
    if (state.route === ROUTES.MANAGE) {
      void manageController.refreshSessions();
    }
  }

  if (showOutput) {
    setJsonOutput("configOutput", {
      loaded: true,
      apiTokenConfigured: !!config.apiTokenConfigured,
      restartRequiredOnAddressOrPortChange: true
    });
  }

  wizardController.syncFromState({ forceMobileDefaults: false });
  return { config, presets };
}

function handleLocaleChange() {
  const nextLocale = refs.localeSelect.value;
  if (nextLocale && nextLocale !== i18n.getLocale()) {
    i18n.setLocale(nextLocale);
    window.location.reload();
  }
}

function handleRouteChange() {
  state.route = getRouteFromHash();
  applyRouteUi();
}

function applyRouteUi() {
  refs.wizardPage.hidden = state.route !== ROUTES.WIZARD;
  refs.commandsPage.hidden = state.route !== ROUTES.COMMANDS;
  refs.managePage.hidden = state.route !== ROUTES.MANAGE;
  refs.settingsPage.hidden = state.route !== ROUTES.SETTINGS;

  refs.tabWizardButton.classList.toggle("is-active", state.route === ROUTES.WIZARD);
  refs.tabCommandsButton.classList.toggle("is-active", state.route === ROUTES.COMMANDS);
  refs.tabManageButton.classList.toggle("is-active", state.route === ROUTES.MANAGE);
  refs.tabSettingsButton.classList.toggle("is-active", state.route === ROUTES.SETTINGS);

  if (state.route === ROUTES.MANAGE && manageController) {
    const token = refs.manageTokenInput ? String(refs.manageTokenInput.value || "").trim() : "";
    if (token) {
      void manageController.refreshSessions();
    }
  }
}

async function refreshAll() {
  setBusy("refreshAllButton", true, t("action.refreshAll"), t("action.refreshing"));

  try {
    await Promise.all([loadConfigAndPresets(), refreshHealth()]);
    setNotice("ok", t("status.dataRefreshed"));
  } catch (error) {
    setNotice("error", t("status.refreshFailed", { error: asErrorMessage(error) }));
  } finally {
    setBusy("refreshAllButton", false, t("action.refreshAll"), t("action.refreshing"));
  }
}

async function bootstrap() {
  createControllers();

  const appRoot = document.getElementById("app");
  mount(appRoot, createTree(), { refs });

  const locale = i18n.getLocale();
  refs.localeSelect.value = locale;
  document.documentElement.lang = locale === "zh" ? "zh-CN" : "en";
  document.title = t("ui.title");

  if (!window.location.hash) {
    window.location.hash = "#/wizard";
  }

  state.route = getRouteFromHash();
  wizardController.setWizardStep(0);
  manageController.renderEmptyState();
  applyRouteUi();

  window.addEventListener("hashchange", handleRouteChange);

  try {
    await refreshAll();
  } catch (error) {
    setNotice("error", t("status.initializationFailed", { error: asErrorMessage(error) }));
  }
}

window.addEventListener("DOMContentLoaded", bootstrap);
