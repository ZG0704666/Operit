/**
 * Software settings type definitions for Assistance Package Tools
 */

import {
    StringResultData,
    ModelConfigsResultData,
    ModelConfigCreateResultData,
    ModelConfigUpdateResultData,
    ModelConfigDeleteResultData,
    FunctionModelConfigsResultData,
    FunctionModelBindingResultData,
    ModelConfigConnectionTestResultData
} from './results';

/**
 * Software settings operations namespace
 */
export namespace SoftwareSettings {
    interface ModelConfigUpdateOptions {
        name?: string;
        api_provider_type?: string;
        api_endpoint?: string;
        api_key?: string;
        model_name?: string;
        enable_direct_image_processing?: boolean;
        enable_direct_audio_processing?: boolean;
        enable_direct_video_processing?: boolean;
        enable_google_search?: boolean;
        enable_tool_call?: boolean;
        strict_tool_call?: boolean;
        mnn_forward_type?: number;
        mnn_thread_count?: number;
        llama_thread_count?: number;
        llama_context_size?: number;
        request_limit_per_minute?: number;
        max_concurrent_requests?: number;
    }

    /**
     * Read current value of an environment variable.
     * @param key - Environment variable key
     */
    function readEnvironmentVariable(key: string): Promise<StringResultData>;

    /**
     * Write an environment variable; empty value clears the variable.
     * @param key - Environment variable key
     * @param value - Variable value (empty string clears)
     */
    function writeEnvironmentVariable(key: string, value?: string): Promise<StringResultData>;

    /**
     * List sandbox packages (built-in and external) with enabled states and management paths.
     */
    function listSandboxPackages(): Promise<StringResultData>;

    /**
     * Enable or disable a sandbox package.
     * @param packageName - Sandbox package name
     * @param enabled - true to enable, false to disable
     */
    function setSandboxPackageEnabled(
        packageName: string,
        enabled: boolean | string | number
    ): Promise<StringResultData>;

    /**
     * Restart MCP startup flow and collect per-plugin startup logs.
     * @param timeoutMs - Optional max wait time in milliseconds
     */
    function restartMcpWithLogs(timeoutMs?: number | string): Promise<StringResultData>;

    /**
     * List all model configs and current function bindings.
     */
    function listModelConfigs(): Promise<ModelConfigsResultData>;

    /**
     * Create a model config.
     * @param options - Optional initial fields
     */
    function createModelConfig(options?: Partial<ModelConfigUpdateOptions> & { name?: string }): Promise<ModelConfigCreateResultData>;

    /**
     * Update an existing model config by id.
     * @param configId - Model config id
     * @param updates - Fields to update
     */
    function updateModelConfig(
        configId: string,
        updates?: Partial<ModelConfigUpdateOptions>
    ): Promise<ModelConfigUpdateResultData>;

    /**
     * Delete a model config by id.
     * @param configId - Model config id
     */
    function deleteModelConfig(configId: string): Promise<ModelConfigDeleteResultData>;

    /**
     * List function model config bindings.
     */
    function listFunctionModelConfigs(): Promise<FunctionModelConfigsResultData>;

    /**
     * Bind one function type to a model config.
     * @param functionType - Function type enum name
     * @param configId - Model config id
     * @param modelIndex - Optional model index
     */
    function setFunctionModelConfig(
        functionType: string,
        configId: string,
        modelIndex?: number | string
    ): Promise<FunctionModelBindingResultData>;

    /**
     * Test one model config with the same checks as model settings UI.
     * @param configId - Model config id
     * @param modelIndex - Optional model index
     */
    function testModelConfigConnection(
        configId: string,
        modelIndex?: number | string
    ): Promise<ModelConfigConnectionTestResultData>;
}
