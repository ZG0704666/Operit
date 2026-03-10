/* METADATA
{
    "name": "apktool",
    "display_name": {
        "zh": "Apktool 直调",
        "en": "Apktool Direct Bridge"
    },
    "description": {
        "zh": "通过 ToolPkg.readResource + Java.loadJar 载入内置 apktool runtime，并在每次执行前自动确保 runtime 与默认 framework 就绪。",
        "en": "Load the bundled apktool runtime through ToolPkg.readResource + Java.loadJar, and automatically ensure the runtime and default framework before each command."
    },
    "enabledByDefault": true,
    "category": "System",
    "tools": [
        {
            "name": "usage_advice",
            "description": {
                "zh": "返回当前直调版 apktool 支持的 CLI 对齐能力说明。",
                "en": "Return CLI-parity support notes for this direct-bridge apktool package."
            },
            "parameters": [],
            "advice": true
        },
        {
            "name": "apktool_decode",
            "description": {
                "zh": "直接调用 brut.androlib.ApkDecoder 解包 APK 到目录，并对齐 apktool decode 主要 CLI 参数。",
                "en": "Directly call brut.androlib.ApkDecoder and expose the main apktool decode CLI options."
            },
            "parameters": [
                { "name": "input_apk_path", "description": { "zh": "要解包的 APK 文件路径。", "en": "Path to the APK file to decode." }, "type": "string", "required": true },
                { "name": "output_dir", "description": { "zh": "可选，输出目录。省略时按 apktool 默认规则生成。", "en": "Optional output directory. Uses apktool-style default when omitted." }, "type": "string", "required": false },
                { "name": "jobs", "description": { "zh": "可选，对齐 -j/--jobs。", "en": "Optional. Mirrors -j/--jobs." }, "type": "string", "required": false },
                { "name": "frame_path", "description": { "zh": "可选，对齐 -p/--frame-path。", "en": "Optional. Mirrors -p/--frame-path." }, "type": "string", "required": false },
                { "name": "frame_tag", "description": { "zh": "可选，对齐 -t/--frame-tag。", "en": "Optional. Mirrors -t/--frame-tag." }, "type": "string", "required": false },
                { "name": "lib", "description": { "zh": "可选，对齐 -l/--lib。可传单个 package:file，或 JSON 数组 / 逗号换行分隔。", "en": "Optional. Mirrors -l/--lib. Accepts a single package:file, or a JSON array / comma-newline separated list." }, "type": "string", "required": false },
                { "name": "force", "description": { "zh": "可选，对齐 -f/--force。", "en": "Optional. Mirrors -f/--force." }, "type": "string", "required": false },
                { "name": "all_src", "description": { "zh": "可选，对齐 -a/--all-src。", "en": "Optional. Mirrors -a/--all-src." }, "type": "string", "required": false },
                { "name": "no_src", "description": { "zh": "可选，对齐 -s/--no-src。", "en": "Optional. Mirrors -s/--no-src." }, "type": "string", "required": false },
                { "name": "no_debug_info", "description": { "zh": "可选，对齐 --no-debug-info。", "en": "Optional. Mirrors --no-debug-info." }, "type": "string", "required": false },
                { "name": "no_res", "description": { "zh": "可选，对齐 -r/--no-res。", "en": "Optional. Mirrors -r/--no-res." }, "type": "string", "required": false },
                { "name": "only_manifest", "description": { "zh": "可选，对齐 --only-manifest。", "en": "Optional. Mirrors --only-manifest." }, "type": "string", "required": false },
                { "name": "res_resolve_mode", "description": { "zh": "可选，对齐 --res-resolve-mode，支持 default/greedy/lazy。", "en": "Optional. Mirrors --res-resolve-mode with default/greedy/lazy." }, "type": "string", "required": false },
                { "name": "keep_broken_res", "description": { "zh": "可选，对齐 --keep-broken-res。", "en": "Optional. Mirrors --keep-broken-res." }, "type": "string", "required": false },
                { "name": "ignore_raw_values", "description": { "zh": "可选，对齐 --ignore-raw-values。", "en": "Optional. Mirrors --ignore-raw-values." }, "type": "string", "required": false },
                { "name": "match_original", "description": { "zh": "可选，对齐 --match-original。", "en": "Optional. Mirrors --match-original." }, "type": "string", "required": false },
                { "name": "no_assets", "description": { "zh": "可选，对齐 --no-assets。", "en": "Optional. Mirrors --no-assets." }, "type": "string", "required": false },
                { "name": "verbose", "description": { "zh": "可选，对齐 -v/--verbose。", "en": "Optional. Mirrors -v/--verbose." }, "type": "string", "required": false },
                { "name": "quiet", "description": { "zh": "可选，对齐 -q/--quiet。", "en": "Optional. Mirrors -q/--quiet." }, "type": "string", "required": false },
                { "name": "decode_sources", "description": { "zh": "可选，兼容旧参数。支持 full/main/none，也兼容 decode_source / decode_scource。", "en": "Optional legacy compatibility parameter. Supports full/main/none and aliases decode_source / decode_scource." }, "type": "string", "required": false },
                { "name": "config_json", "description": { "zh": "可选，JSON 对象。仍支持旧版 config_json，并会与直接参数合并。", "en": "Optional JSON object. Legacy config_json is still supported and merged with direct parameters." }, "type": "string", "required": false }
            ]
        },
        {
            "name": "apktool_build",
            "description": {
                "zh": "直接调用 brut.androlib.ApkBuilder 回编 APK，并对齐 apktool build 主要 CLI 参数。",
                "en": "Directly call brut.androlib.ApkBuilder and expose the main apktool build CLI options."
            },
            "parameters": [
                { "name": "input_dir", "description": { "zh": "可选，已解包工程目录。省略时默认为当前目录。", "en": "Optional decoded project directory. Defaults to current directory." }, "type": "string", "required": false },
                { "name": "output_apk_path", "description": { "zh": "可选，输出 APK 路径。开启 no_apk 时可省略。", "en": "Optional output APK path. May be omitted when no_apk is enabled." }, "type": "string", "required": false },
                { "name": "jobs", "description": { "zh": "可选，对齐 -j/--jobs。", "en": "Optional. Mirrors -j/--jobs." }, "type": "string", "required": false },
                { "name": "frame_path", "description": { "zh": "可选，对齐 -p/--frame-path。", "en": "Optional. Mirrors -p/--frame-path." }, "type": "string", "required": false },
                { "name": "lib", "description": { "zh": "可选，对齐 -l/--lib。可传单个 package:file，或 JSON 数组 / 逗号换行分隔。", "en": "Optional. Mirrors -l/--lib. Accepts a single package:file, or a JSON array / comma-newline separated list." }, "type": "string", "required": false },
                { "name": "force", "description": { "zh": "可选，对齐 -f/--force。", "en": "Optional. Mirrors -f/--force." }, "type": "string", "required": false },
                { "name": "no_apk", "description": { "zh": "可选，对齐 --no-apk。", "en": "Optional. Mirrors --no-apk." }, "type": "string", "required": false },
                { "name": "no_crunch", "description": { "zh": "可选，对齐 --no-crunch。", "en": "Optional. Mirrors --no-crunch." }, "type": "string", "required": false },
                { "name": "copy_original", "description": { "zh": "可选，对齐 --copy-original。", "en": "Optional. Mirrors --copy-original." }, "type": "string", "required": false },
                { "name": "debuggable", "description": { "zh": "可选，对齐 --debuggable。", "en": "Optional. Mirrors --debuggable." }, "type": "string", "required": false },
                { "name": "net_sec_conf", "description": { "zh": "可选，对齐 --net-sec-conf。", "en": "Optional. Mirrors --net-sec-conf." }, "type": "string", "required": false },
                { "name": "aapt", "description": { "zh": "可选，对齐 --aapt。传入 aapt2 可执行文件路径。", "en": "Optional. Mirrors --aapt. Provide the aapt2 binary path." }, "type": "string", "required": false },
                { "name": "verbose", "description": { "zh": "可选，对齐 -v/--verbose。", "en": "Optional. Mirrors -v/--verbose." }, "type": "string", "required": false },
                { "name": "quiet", "description": { "zh": "可选，对齐 -q/--quiet。", "en": "Optional. Mirrors -q/--quiet." }, "type": "string", "required": false },
                { "name": "config_json", "description": { "zh": "可选，JSON 对象。仍支持旧版 config_json，并会与直接参数合并。", "en": "Optional JSON object. Legacy config_json is still supported and merged with direct parameters." }, "type": "string", "required": false }
            ]
        },
        {
            "name": "apktool_list_frameworks",
            "description": {
                "zh": "直接调用 brut.androlib.res.Framework.listDirectory，并对齐 apktool list-frameworks CLI 参数。",
                "en": "Directly call brut.androlib.res.Framework.listDirectory and expose the apktool list-frameworks CLI options."
            },
            "parameters": [
                { "name": "frame_path", "description": { "zh": "可选，对齐 -p/--frame-path。", "en": "Optional. Mirrors -p/--frame-path." }, "type": "string", "required": false },
                { "name": "frame_tag", "description": { "zh": "可选，对齐 -t/--frame-tag。", "en": "Optional. Mirrors -t/--frame-tag." }, "type": "string", "required": false },
                { "name": "all", "description": { "zh": "可选，对齐 -a/--all。", "en": "Optional. Mirrors -a/--all." }, "type": "string", "required": false },
                { "name": "verbose", "description": { "zh": "可选，对齐 -v/--verbose。", "en": "Optional. Mirrors -v/--verbose." }, "type": "string", "required": false },
                { "name": "quiet", "description": { "zh": "可选，对齐 -q/--quiet。", "en": "Optional. Mirrors -q/--quiet." }, "type": "string", "required": false },
                { "name": "config_json", "description": { "zh": "可选，JSON 对象。仍支持旧版 config_json，并会与直接参数合并。", "en": "Optional JSON object. Legacy config_json is still supported and merged with direct parameters." }, "type": "string", "required": false }
            ]
        }
    ]
}
*/

const PACKAGE_VERSION = "0.4.0";
const APKTOOL_VERSION = "3.0.1";
const APKTOOL_RUNTIME_RESOURCE_KEY = "apktool_runtime_android_jar";
const APKTOOL_ANDROID_FRAMEWORK_RESOURCE_KEY = "apktool_android_framework_jar";
const APKTOOL_RUNTIME_OUTPUT_FILE_NAME = "apktool-runtime-android.jar";
const APKTOOL_ANDROID_FRAMEWORK_OUTPUT_FILE_NAME = "android-framework.jar";
const APKTOOL_RUNTIME_SOURCE_ARTIFACT = "org.apktool:apktool-cli:3.0.1";
const JVM_COMPAT_OS_NAME = "Linux";
const JVM_COMPAT_OS_ARCH = "aarch64";
const JVM_COMPAT_ARCH_DATA_MODEL = "64";
const APKTOOL_BRIDGE_CLASS_NAMES = [
  "brut.androlib.Config",
  "brut.androlib.ApkDecoder",
  "brut.androlib.ApkBuilder",
  "brut.androlib.res.Framework",
  "brut.androlib.Config$DecodeSources",
  "brut.androlib.Config$DecodeResources",
  "brut.androlib.Config$DecodeAssets",
  "brut.androlib.Config$DecodeResolve",
  "java.io.File"
];

type AppliedConfigValue = string | number | boolean | string[];
type AppliedConfig = Record<string, AppliedConfigValue> & {
  version: string;
};

function asText(value) {
  if (value === undefined || value === null) {
    return "";
  }
  return String(value);
}

function hasOwn(object, key) {
  return !!object && Object.prototype.hasOwnProperty.call(object, key);
}

function isProvided(value) {
  if (value === undefined || value === null) {
    return false;
  }
  if (Array.isArray(value)) {
    return value.length > 0;
  }
  if (typeof value === "string") {
    return value.trim().length > 0;
  }
  return true;
}

function toErrorText(error) {
  if (error instanceof Error) {
    return error.message || String(error);
  }
  return asText(error) || "unknown error";
}

function requireText(params, key) {
  const value = asText(params && params[key]).trim();
  if (!value) {
    throw new Error(`Missing required parameter: ${key}`);
  }
  return value;
}

function optionalText(params, key) {
  const value = asText(params && params[key]).trim();
  return value || undefined;
}

function parseConfigJson(params) {
  if (!hasOwn(params, "config_json")) {
    return {};
  }
  const raw = asText(params.config_json).trim();
  if (!raw) {
    return {};
  }
  const parsed = JSON.parse(raw);
  if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
    throw new Error("config_json must be a JSON object");
  }
  return parsed;
}

function pickOptionValue(params, configJson, keys) {
  for (const key of keys) {
    if (hasOwn(params, key) && isProvided(params[key])) {
      return params[key];
    }
  }
  for (const key of keys) {
    if (hasOwn(configJson, key) && isProvided(configJson[key])) {
      return configJson[key];
    }
  }
  return undefined;
}

function parseBoolean(value, key) {
  if (typeof value === "boolean") {
    return value;
  }
  const normalized = asText(value).trim().toLowerCase();
  if (["1", "true", "yes", "y", "on"].includes(normalized)) {
    return true;
  }
  if (["0", "false", "no", "n", "off"].includes(normalized)) {
    return false;
  }
  throw new Error(`${key} must be a boolean`);
}

function parseInteger(value, key) {
  const parsed = Number(value);
  if (!Number.isInteger(parsed)) {
    throw new Error(`${key} must be an integer`);
  }
  return parsed;
}

function parseEnumToken(value) {
  return asText(value).trim().toLowerCase().replace(/[\s-]+/g, "_");
}

function parseStringList(value, key) {
  if (Array.isArray(value)) {
    const list = value.map((item) => asText(item).trim()).filter(Boolean);
    if (list.length === 0) {
      throw new Error(`${key} must not be empty`);
    }
    return list;
  }
  const raw = asText(value).trim();
  if (!raw) {
    throw new Error(`${key} must not be blank`);
  }
  if (raw.startsWith("[")) {
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed)) {
      throw new Error(`${key} JSON form must be an array`);
    }
    return parseStringList(parsed, key);
  }
  const list = raw.split(/[\r\n,]+/).map((item) => item.trim()).filter(Boolean);
  if (list.length === 0) {
    throw new Error(`${key} must not be empty`);
  }
  return list;
}

function ensureJvmCompatibilitySystemProperties() {
  const System = Java.type("java.lang.System");
  const Locale = Java.type("java.util.Locale");
  const File = Java.type("java.io.File");
  const filesDir = new File("/data/data/com.ai.assistance.operit/files");
  const cacheDir = new File("/data/data/com.ai.assistance.operit/cache");
  const locale = Locale.getDefault();
  const country = asText(locale.getCountry()).trim();

  ensureSystemProperty(System, "os.name", JVM_COMPAT_OS_NAME);
  ensureSystemProperty(System, "os.arch", JVM_COMPAT_OS_ARCH);
  ensureSystemProperty(System, "sun.arch.data.model", JVM_COMPAT_ARCH_DATA_MODEL);
  ensureSystemProperty(System, "user.home", asText(filesDir.getAbsolutePath()));
  ensureSystemProperty(System, "user.dir", asText(filesDir.getAbsolutePath()));
  ensureSystemProperty(System, "java.io.tmpdir", asText(cacheDir.getAbsolutePath()));
  ensureSystemProperty(System, "user.language", asText(locale.getLanguage()).trim() || "en");
  if (country) {
    ensureSystemProperty(System, "user.country", country);
  }
}

function ensureSystemProperty(System, key, value) {
  const normalizedValue = asText(value).trim();
  if (!normalizedValue) {
    return;
  }
  const current = asText(System.getProperty(key)).trim();
  if (!current) {
    System.setProperty(key, normalizedValue);
  }
}

function collectJvmCompatibilitySystemProperties() {
  const System = Java.type("java.lang.System");
  return {
    osName: asText(System.getProperty("os.name")),
    osArch: asText(System.getProperty("os.arch")),
    sunArchDataModel: asText(System.getProperty("sun.arch.data.model")),
    userHome: asText(System.getProperty("user.home")),
    userDir: asText(System.getProperty("user.dir")),
    javaIoTmpdir: asText(System.getProperty("java.io.tmpdir")),
    userLanguage: asText(System.getProperty("user.language")),
    userCountry: asText(System.getProperty("user.country"))
  };
}

function configureJavaLogging(mode) {
  const Logger = Java.type("java.util.logging.Logger");
  const Level = Java.type("java.util.logging.Level");
  const root = Logger.getLogger("");
  const level = mode === "quiet"
    ? Level.OFF
    : mode === "verbose"
      ? Level.ALL
      : Level.INFO;
  root.setLevel(level);
  const handlers = root.getHandlers();
  const length = Number(handlers.length);
  for (let index = 0; index < length; index += 1) {
    handlers[index].setLevel(level);
  }
}

async function ensureRuntimeLoaded() {
  ensureJvmCompatibilitySystemProperties();
  const runtimeJarPath = await ToolPkg.readResource(
    APKTOOL_RUNTIME_RESOURCE_KEY,
    APKTOOL_RUNTIME_OUTPUT_FILE_NAME,
    true
  );
  const loadInfo = Java.loadJar(runtimeJarPath);
  return {
    runtimeJarPath,
    loadInfo,
    sourceArtifact: APKTOOL_RUNTIME_SOURCE_ARTIFACT
  };
}

async function ensureDefaultFrameworkInstalled(classes, config) {
  const framework = new classes.Framework(config);
  const frameworkDirectory = framework.getDirectory();
  const frameworkApk = new classes.File(frameworkDirectory, "1.apk");
  const frameworkExists = frameworkApk.exists();
  const frameworkSize = frameworkExists ? Number(frameworkApk.length()) : 0;
  if (frameworkExists && frameworkSize > 0) {
    return {
      frameworkDirectory: asText(frameworkDirectory.getAbsolutePath()),
      frameworkApkPath: asText(frameworkApk.getAbsolutePath()),
      installed: false,
      frameworkSize
    };
  }
  if (frameworkExists) {
    frameworkApk.delete();
  }
  const frameworkJarPath = await ToolPkg.readResource(
    APKTOOL_ANDROID_FRAMEWORK_RESOURCE_KEY,
    APKTOOL_ANDROID_FRAMEWORK_OUTPUT_FILE_NAME,
    true
  );
  framework.install(new classes.File(frameworkJarPath));
  return {
    frameworkDirectory: asText(frameworkDirectory.getAbsolutePath()),
    frameworkApkPath: asText(frameworkApk.getAbsolutePath()),
    installed: true,
    sourceJarPath: frameworkJarPath,
    frameworkSize: Number(frameworkApk.length())
  };
}

function getBridgeClasses() {
  return {
    File: Java.type("java.io.File"),
    Config: Java.type("brut.androlib.Config"),
    ApkDecoder: Java.type("brut.androlib.ApkDecoder"),
    ApkBuilder: Java.type("brut.androlib.ApkBuilder"),
    Framework: Java.type("brut.androlib.res.Framework"),
    DecodeSources: Java.type("brut.androlib.Config$DecodeSources"),
    DecodeResources: Java.type("brut.androlib.Config$DecodeResources"),
    DecodeAssets: Java.type("brut.androlib.Config$DecodeAssets"),
    DecodeResolve: Java.type("brut.androlib.Config$DecodeResolve")
  };
}

function resolveDecodeSources(classes, value) {
  const token = parseEnumToken(value);
  if (["full", "smali", "all"].includes(token)) {
    return classes.DecodeSources.FULL;
  }
  if (["only_main_classes", "only_main", "main", "main_classes", "classes"].includes(token)) {
    return classes.DecodeSources.ONLY_MAIN_CLASSES;
  }
  if (["none", "no", "off"].includes(token)) {
    return classes.DecodeSources.NONE;
  }
  throw new Error(`Unsupported decode_sources value: ${value}`);
}

function resolveDecodeResources(classes, value) {
  const token = parseEnumToken(value);
  if (token === "full") {
    return classes.DecodeResources.FULL;
  }
  if (["only_manifest", "manifest"].includes(token)) {
    return classes.DecodeResources.ONLY_MANIFEST;
  }
  if (["none", "no", "off"].includes(token)) {
    return classes.DecodeResources.NONE;
  }
  throw new Error(`Unsupported decode_resources value: ${value}`);
}

function resolveDecodeAssets(classes, value) {
  const token = parseEnumToken(value);
  if (token === "full") {
    return classes.DecodeAssets.FULL;
  }
  if (["none", "no", "off"].includes(token)) {
    return classes.DecodeAssets.NONE;
  }
  throw new Error(`Unsupported decode_assets value: ${value}`);
}

function resolveDecodeResolve(classes, value) {
  const token = parseEnumToken(value);
  if (token === "default") {
    return classes.DecodeResolve.DEFAULT;
  }
  if (token === "greedy") {
    return classes.DecodeResolve.GREEDY;
  }
  if (token === "lazy") {
    return classes.DecodeResolve.LAZY;
  }
  throw new Error(`Unsupported decode_resolve value: ${value}`);
}

function decodeSourcesNameFromValue(value) {
  const token = parseEnumToken(value);
  if (["full", "smali", "all"].includes(token)) {
    return "full";
  }
  if (["only_main_classes", "only_main", "main", "main_classes", "classes"].includes(token)) {
    return "only_main_classes";
  }
  if (["none", "no", "off"].includes(token)) {
    return "none";
  }
  throw new Error(`Unsupported decode_sources value: ${value}`);
}

function decodeResourcesNameFromValue(value) {
  const token = parseEnumToken(value);
  if (token === "full") {
    return "full";
  }
  if (["only_manifest", "manifest"].includes(token)) {
    return "only_manifest";
  }
  if (["none", "no", "off"].includes(token)) {
    return "none";
  }
  throw new Error(`Unsupported decode_resources value: ${value}`);
}

function decodeAssetsNameFromValue(value) {
  const token = parseEnumToken(value);
  if (token === "full") {
    return "full";
  }
  if (["none", "no", "off"].includes(token)) {
    return "none";
  }
  throw new Error(`Unsupported decode_assets value: ${value}`);
}

function requireNoConflict(condition, message) {
  if (condition) {
    throw new Error(message);
  }
}

function buildExecutionContext(classes, params, operation) {
  const configJson = parseConfigJson(params);
  const config = new classes.Config(APKTOOL_VERSION);
  const applied: AppliedConfig = {
    version: APKTOOL_VERSION
  };

  const jobsValue = pickOptionValue(params, configJson, ["jobs"]);
  if (jobsValue !== undefined) {
    const jobs = parseInteger(jobsValue, "jobs");
    config.setJobs(jobs);
    applied.jobs = jobs;
  }

  const framePathValue = pickOptionValue(params, configJson, ["frame_path", "framework_directory"]);
  if (framePathValue !== undefined) {
    const framePath = asText(framePathValue).trim();
    if (!framePath) {
      throw new Error("frame_path must not be blank");
    }
    config.setFrameworkDirectory(framePath);
    applied.frame_path = framePath;
  }

  const frameTagValue = pickOptionValue(params, configJson, ["frame_tag", "framework_tag"]);
  if (frameTagValue !== undefined) {
    const frameTag = asText(frameTagValue).trim();
    if (!frameTag) {
      throw new Error("frame_tag must not be blank");
    }
    config.setFrameworkTag(frameTag);
    applied.frame_tag = frameTag;
  }

  const libValue = pickOptionValue(params, configJson, ["lib", "library_files"]);
  if (libValue !== undefined) {
    const libraries = parseStringList(libValue, "lib");
    config.setLibraryFiles(libraries);
    applied.lib = libraries;
  }

  const forceValue = pickOptionValue(params, configJson, ["force"]);
  if (forceValue !== undefined) {
    const force = parseBoolean(forceValue, "force");
    config.setForced(force);
    applied.force = force;
  }

  const verboseValue = pickOptionValue(params, configJson, ["verbose"]);
  const quietValue = pickOptionValue(params, configJson, ["quiet"]);
  const verbose = verboseValue !== undefined ? parseBoolean(verboseValue, "verbose") : false;
  const quiet = quietValue !== undefined ? parseBoolean(quietValue, "quiet") : false;
  requireNoConflict(verbose && quiet, "verbose cannot be used together with quiet");
  if (verboseValue !== undefined) {
    config.setVerbose(verbose);
    applied.verbose = verbose;
  }
  if (quietValue !== undefined) {
    applied.quiet = quiet;
  }
  configureJavaLogging(quiet ? "quiet" : verbose ? "verbose" : "normal");

  if (operation === "decode") {
    const decodeSourcesValue = pickOptionValue(params, configJson, [
      "decode_sources",
      "decode_source",
      "decode_scource",
      "decode_scources"
    ]);
    const allSrcValue = pickOptionValue(params, configJson, ["all_src"]);
    const noSrcValue = pickOptionValue(params, configJson, ["no_src"]);
    const allSrc = allSrcValue !== undefined ? parseBoolean(allSrcValue, "all_src") : false;
    const noSrc = noSrcValue !== undefined ? parseBoolean(noSrcValue, "no_src") : false;
    requireNoConflict(allSrc && noSrc, "all_src cannot be used together with no_src");
    if (allSrcValue !== undefined) {
      applied.all_src = allSrc;
    }
    if (noSrcValue !== undefined) {
      applied.no_src = noSrc;
    }
    if (decodeSourcesValue !== undefined) {
      const mode = decodeSourcesNameFromValue(decodeSourcesValue);
      requireNoConflict(allSrc && mode !== "full", "decode_sources conflicts with all_src");
      requireNoConflict(noSrc && mode !== "none", "decode_sources conflicts with no_src");
      config.setDecodeSources(resolveDecodeSources(classes, decodeSourcesValue));
      applied.decode_sources = mode;
    } else if (allSrc) {
      config.setDecodeSources(classes.DecodeSources.FULL);
      applied.decode_sources = "full";
    } else if (noSrc) {
      config.setDecodeSources(classes.DecodeSources.NONE);
      applied.decode_sources = "none";
    }

    const baksmaliDebugValue = pickOptionValue(params, configJson, ["baksmali_debug_mode"]);
    if (baksmaliDebugValue !== undefined) {
      const baksmaliDebug = parseBoolean(baksmaliDebugValue, "baksmali_debug_mode");
      config.setBaksmaliDebugMode(baksmaliDebug);
      applied.baksmali_debug_mode = baksmaliDebug;
    }
    const noDebugInfoValue = pickOptionValue(params, configJson, ["no_debug_info"]);
    if (noDebugInfoValue !== undefined) {
      const noDebugInfo = parseBoolean(noDebugInfoValue, "no_debug_info");
      requireNoConflict(
        noDebugInfo && applied.decode_sources === "none",
        "no_debug_info cannot be used when sources are disabled"
      );
      requireNoConflict(
        noDebugInfo && applied.baksmali_debug_mode === true,
        "no_debug_info conflicts with baksmali_debug_mode=true"
      );
      if (noDebugInfo) {
        config.setBaksmaliDebugMode(false);
      }
      applied.no_debug_info = noDebugInfo;
    }

    const decodeResourcesValue = pickOptionValue(params, configJson, ["decode_resources"]);
    const noResValue = pickOptionValue(params, configJson, ["no_res"]);
    const onlyManifestValue = pickOptionValue(params, configJson, ["only_manifest"]);
    const noRes = noResValue !== undefined ? parseBoolean(noResValue, "no_res") : false;
    const onlyManifest = onlyManifestValue !== undefined ? parseBoolean(onlyManifestValue, "only_manifest") : false;
    requireNoConflict(noRes && onlyManifest, "no_res cannot be used together with only_manifest");
    if (noResValue !== undefined) {
      applied.no_res = noRes;
    }
    if (onlyManifestValue !== undefined) {
      applied.only_manifest = onlyManifest;
    }
    if (decodeResourcesValue !== undefined) {
      const mode = decodeResourcesNameFromValue(decodeResourcesValue);
      requireNoConflict(noRes && mode !== "none", "decode_resources conflicts with no_res");
      requireNoConflict(onlyManifest && mode !== "only_manifest", "decode_resources conflicts with only_manifest");
      config.setDecodeResources(resolveDecodeResources(classes, decodeResourcesValue));
      applied.decode_resources = mode;
    } else if (noRes) {
      config.setDecodeResources(classes.DecodeResources.NONE);
      applied.decode_resources = "none";
    } else if (onlyManifest) {
      config.setDecodeResources(classes.DecodeResources.ONLY_MANIFEST);
      applied.decode_resources = "only_manifest";
    }

    const decodeResolveValue = pickOptionValue(params, configJson, ["res_resolve_mode", "decode_resolve"]);
    if (decodeResolveValue !== undefined) {
      requireNoConflict(applied.decode_resources === "none", "res_resolve_mode cannot be used with no_res");
      requireNoConflict(applied.decode_resources === "only_manifest", "res_resolve_mode cannot be used with only_manifest");
      const mode = asText(decodeResolveValue).trim();
      config.setDecodeResolve(resolveDecodeResolve(classes, mode));
      applied.decode_resolve = parseEnumToken(mode);
    }

    const keepBrokenResValue = pickOptionValue(params, configJson, ["keep_broken_res", "keep_broken_resources"]);
    if (keepBrokenResValue !== undefined) {
      const keepBrokenRes = parseBoolean(keepBrokenResValue, "keep_broken_res");
      requireNoConflict(keepBrokenRes && applied.decode_resources === "none", "keep_broken_res cannot be used with no_res");
      requireNoConflict(keepBrokenRes && applied.decode_resources === "only_manifest", "keep_broken_res cannot be used with only_manifest");
      config.setKeepBrokenResources(keepBrokenRes);
      applied.keep_broken_res = keepBrokenRes;
    }

    const ignoreRawValuesValue = pickOptionValue(params, configJson, ["ignore_raw_values"]);
    if (ignoreRawValuesValue !== undefined) {
      const ignoreRawValues = parseBoolean(ignoreRawValuesValue, "ignore_raw_values");
      requireNoConflict(ignoreRawValues && applied.decode_resources === "none", "ignore_raw_values cannot be used with no_res");
      config.setIgnoreRawValues(ignoreRawValues);
      applied.ignore_raw_values = ignoreRawValues;
    }

    const analysisModeValue = pickOptionValue(params, configJson, ["analysis_mode"]);
    if (analysisModeValue !== undefined) {
      const analysisMode = parseBoolean(analysisModeValue, "analysis_mode");
      config.setAnalysisMode(analysisMode);
      applied.analysis_mode = analysisMode;
    }
    const matchOriginalValue = pickOptionValue(params, configJson, ["match_original"]);
    if (matchOriginalValue !== undefined) {
      const matchOriginal = parseBoolean(matchOriginalValue, "match_original");
      requireNoConflict(matchOriginal && applied.analysis_mode === false, "match_original conflicts with analysis_mode=false");
      if (matchOriginal) {
        config.setAnalysisMode(true);
      }
      applied.match_original = matchOriginal;
    }

    const decodeAssetsValue = pickOptionValue(params, configJson, ["decode_assets"]);
    const noAssetsValue = pickOptionValue(params, configJson, ["no_assets"]);
    const noAssets = noAssetsValue !== undefined ? parseBoolean(noAssetsValue, "no_assets") : false;
    if (noAssetsValue !== undefined) {
      applied.no_assets = noAssets;
    }
    if (decodeAssetsValue !== undefined) {
      const mode = decodeAssetsNameFromValue(decodeAssetsValue);
      requireNoConflict(noAssets && mode !== "none", "decode_assets conflicts with no_assets");
      config.setDecodeAssets(resolveDecodeAssets(classes, decodeAssetsValue));
      applied.decode_assets = mode;
    } else if (noAssets) {
      config.setDecodeAssets(classes.DecodeAssets.NONE);
      applied.decode_assets = "none";
    }
  }

  if (operation === "build") {
    const noApkValue = pickOptionValue(params, configJson, ["no_apk"]);
    if (noApkValue !== undefined) {
      const noApk = parseBoolean(noApkValue, "no_apk");
      config.setNoApk(noApk);
      applied.no_apk = noApk;
    }
    const noCrunchValue = pickOptionValue(params, configJson, ["no_crunch"]);
    if (noCrunchValue !== undefined) {
      const noCrunch = parseBoolean(noCrunchValue, "no_crunch");
      config.setNoCrunch(noCrunch);
      applied.no_crunch = noCrunch;
    }
    const copyOriginalValue = pickOptionValue(params, configJson, ["copy_original"]);
    if (copyOriginalValue !== undefined) {
      const copyOriginal = parseBoolean(copyOriginalValue, "copy_original");
      config.setCopyOriginal(copyOriginal);
      applied.copy_original = copyOriginal;
    }
    const debuggableValue = pickOptionValue(params, configJson, ["debuggable"]);
    if (debuggableValue !== undefined) {
      const debuggable = parseBoolean(debuggableValue, "debuggable");
      config.setDebuggable(debuggable);
      applied.debuggable = debuggable;
    }
    const netSecConfValue = pickOptionValue(params, configJson, ["net_sec_conf"]);
    if (netSecConfValue !== undefined) {
      const netSecConf = parseBoolean(netSecConfValue, "net_sec_conf");
      config.setNetSecConf(netSecConf);
      applied.net_sec_conf = netSecConf;
    }
    const aaptValue = pickOptionValue(params, configJson, ["aapt", "aapt_binary"]);
    if (aaptValue !== undefined) {
      const aaptBinary = asText(aaptValue).trim();
      if (!aaptBinary) {
        throw new Error("aapt must not be blank");
      }
      config.setAaptBinary(aaptBinary);
      applied.aapt = aaptBinary;
    }
  }

  if (operation === "list_frameworks") {
    const allValue = pickOptionValue(params, configJson, ["all"]);
    if (allValue !== undefined) {
      const all = parseBoolean(allValue, "all");
      requireNoConflict(all && isProvided(frameTagValue), "all cannot be used together with frame_tag");
      if (all) {
        config.setForced(true);
      }
      applied.all = all;
    }
  }

  return {
    config,
    applied
  };
}

function javaFileListToPaths(list) {
  const paths: string[] = [];
  const size = Number(list.size());
  for (let index = 0; index < size; index += 1) {
    const file = list.get(index);
    paths.push(asText(file.getAbsolutePath()));
  }
  return paths;
}

function defaultDecodeOutputDir(inputApkPath) {
  const trimmed = asText(inputApkPath).trim();
  if (!trimmed) {
    throw new Error("input_apk_path must not be blank");
  }
  if (trimmed.toLowerCase().endsWith(".apk")) {
    return trimmed.slice(0, -4).trim();
  }
  return `${trimmed}.out`;
}

function baseSuccessPayload(runtime) {
  return {
    success: true,
    packageName: "apktool",
    packageVersion: PACKAGE_VERSION,
    apktoolVersion: APKTOOL_VERSION,
    runtimeSourceArtifact: APKTOOL_RUNTIME_SOURCE_ARTIFACT,
    runtimeJarPath: runtime.runtimeJarPath,
    loadInfo: runtime.loadInfo,
    jvmCompatibilitySystemProperties: collectJvmCompatibilitySystemProperties()
  };
}

function baseFailurePayload(error) {
  return {
    success: false,
    packageName: "apktool",
    packageVersion: PACKAGE_VERSION,
    apktoolVersion: APKTOOL_VERSION,
    runtimeSourceArtifact: APKTOOL_RUNTIME_SOURCE_ARTIFACT,
    error: toErrorText(error)
  };
}

export async function usage_advice() {
  return {
    success: true,
    packageName: "apktool",
    packageVersion: PACKAGE_VERSION,
    apktoolVersion: APKTOOL_VERSION,
    runtimeSourceArtifact: APKTOOL_RUNTIME_SOURCE_ARTIFACT,
    runtimeLoadMode: "ToolPkg.readResource + Java.loadJar",
    note: "This package extracts a dex-jar runtime, automatically ensures the default framework, and directly calls brut.androlib.* classes through the Java bridge instead of starting a JVM subprocess.",
    supportedCommands: [
      "decode",
      "build",
      "list-frameworks"
    ],
    directCliParity: {
      decode: [
        "jobs",
        "frame_path",
        "frame_tag",
        "lib",
        "force",
        "all_src",
        "no_src",
        "no_debug_info",
        "no_res",
        "only_manifest",
        "res_resolve_mode",
        "keep_broken_res",
        "ignore_raw_values",
        "match_original",
        "no_assets",
        "verbose",
        "quiet"
      ],
      build: [
        "jobs",
        "frame_path",
        "lib",
        "force",
        "no_apk",
        "no_crunch",
        "copy_original",
        "debuggable",
        "net_sec_conf",
        "aapt",
        "verbose",
        "quiet"
      ],
      listFrameworks: [
        "frame_path",
        "frame_tag",
        "all",
        "verbose",
        "quiet"
      ]
    },
    compatibilityKeys: [
      "config_json",
      "decode_sources",
      "framework_directory",
      "framework_tag",
      "aapt_binary",
      "library_files"
    ]
  };
}

export async function apktool_decode(params) {
  try {
    const inputApkPath = requireText(params, "input_apk_path");
    const outputDir = optionalText(params, "output_dir") || defaultDecodeOutputDir(inputApkPath);
    const runtime = await ensureRuntimeLoaded();
    const classes = getBridgeClasses();
    const context = buildExecutionContext(classes, params, "decode");
    const frameworkInfo = await ensureDefaultFrameworkInstalled(classes, context.config);
    const decoder = new classes.ApkDecoder(
      new classes.File(inputApkPath),
      context.config
    );
    decoder.decode(new classes.File(outputDir));
    return {
      ...baseSuccessPayload(runtime),
      operation: "decode",
      inputApkPath,
      outputDir,
      frameworkInfo,
      appliedConfig: context.applied
    };
  } catch (error) {
    return {
      ...baseFailurePayload(error),
      operation: "decode"
    };
  }
}

export async function apktool_build(params) {
  try {
    const inputDir = optionalText(params, "input_dir") || ".";
    const outputApkPath = optionalText(params, "output_apk_path");
    const runtime = await ensureRuntimeLoaded();
    const classes = getBridgeClasses();
    const context = buildExecutionContext(classes, params, "build");
    const frameworkInfo = await ensureDefaultFrameworkInstalled(classes, context.config);
    requireNoConflict(context.applied.no_apk === true && !!outputApkPath, "output_apk_path cannot be used together with no_apk");
    requireNoConflict(context.applied.no_apk !== true && !outputApkPath, "output_apk_path is required unless no_apk is enabled");
    const builder = new classes.ApkBuilder(
      new classes.File(inputDir),
      context.config
    );
    builder.build(outputApkPath ? new classes.File(outputApkPath) : null);
    return {
      ...baseSuccessPayload(runtime),
      operation: "build",
      inputDir,
      outputApkPath: outputApkPath || null,
      frameworkInfo,
      appliedConfig: context.applied
    };
  } catch (error) {
    return {
      ...baseFailurePayload(error),
      operation: "build"
    };
  }
}

export async function apktool_list_frameworks(params) {
  try {
    const runtime = await ensureRuntimeLoaded();
    const classes = getBridgeClasses();
    const context = buildExecutionContext(classes, params, "list_frameworks");
    const defaultFrameworkInfo = await ensureDefaultFrameworkInstalled(classes, context.config);
    const framework = new classes.Framework(context.config);
    const frameworkDirectory = asText(framework.getDirectory().getAbsolutePath());
    const installedFrameworks = javaFileListToPaths(framework.listDirectory());
    return {
      ...baseSuccessPayload(runtime),
      operation: "list_frameworks",
      frameworkDirectory,
      installedFrameworks,
      frameworkCount: installedFrameworks.length,
      defaultFrameworkInfo,
      appliedConfig: context.applied
    };
  } catch (error) {
    return {
      ...baseFailurePayload(error),
      operation: "list_frameworks"
    };
  }
}
