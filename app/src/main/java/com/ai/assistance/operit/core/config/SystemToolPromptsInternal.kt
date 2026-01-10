package com.ai.assistance.operit.core.config

import com.ai.assistance.operit.data.model.SystemToolPromptCategory
import com.ai.assistance.operit.data.model.ToolPrompt
import com.ai.assistance.operit.data.model.ToolParameterSchema

object SystemToolPromptsInternal {

    val internalToolCategoriesEn: List<SystemToolPromptCategory> =
        listOf(
            SystemToolPromptCategory(
                categoryName = "Internal Tools",
                tools =
                    listOf(
                        ToolPrompt(
                            name = "execute_shell",
                            description = "Execute a device shell command.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "command",
                                        type = "string",
                                        description = "shell command to execute",
                                        required = true
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "create_terminal_session",
                            description = "Create or get a terminal session.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "session_name",
                                        type = "string",
                                        description = "terminal session name",
                                        required = true
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "execute_in_terminal_session",
                            description = "Execute a command in a terminal session and collect full output.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "session_id",
                                        type = "string",
                                        description = "terminal session id",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "command",
                                        type = "string",
                                        description = "command to execute",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "timeout_ms",
                                        type = "integer",
                                        description = "optional, command timeout in milliseconds",
                                        required = false,
                                        default = "1800000"
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "close_terminal_session",
                            description = "Close a terminal session.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "session_id",
                                        type = "string",
                                        description = "terminal session id",
                                        required = true
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "calculate",
                            description = "Evaluate a math expression.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "expression",
                                        type = "string",
                                        description = "math expression, e.g. \"(1+2)*3\"",
                                        required = true
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "execute_intent",
                            description = "Execute an Android Intent (activity/broadcast/service).",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "action",
                                        type = "string",
                                        description = "optional, intent action",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "uri",
                                        type = "string",
                                        description = "optional, data URI",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "package",
                                        type = "string",
                                        description = "optional, package name",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "component",
                                        type = "string",
                                        description = "optional, component in \"package/class\" format",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "type",
                                        type = "string",
                                        description = "optional, one of activity/broadcast/service",
                                        required = false,
                                        default = "activity"
                                    ),
                                    ToolParameterSchema(
                                        name = "flags",
                                        type = "string",
                                        description = "optional, JSON array string of int flags (or a single int)",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "extras",
                                        type = "string",
                                        description = "optional, JSON object string for extras",
                                        required = false
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "device_info",
                            description = "Get device information.",
                            parametersStructured = listOf()
                        )
                    )
            ),
            SystemToolPromptCategory(
                categoryName = "Tasker Tools",
                tools =
                    listOf(
                        ToolPrompt(
                            name = "trigger_tasker_event",
                            description = "Trigger a Tasker event.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "task_type",
                                        type = "string",
                                        description = "Tasker event type",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "arg1",
                                        type = "string",
                                        description = "optional",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "arg2",
                                        type = "string",
                                        description = "optional",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "arg3",
                                        type = "string",
                                        description = "optional",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "arg4",
                                        type = "string",
                                        description = "optional",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "arg5",
                                        type = "string",
                                        description = "optional",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "args_json",
                                        type = "string",
                                        description = "optional, JSON object string",
                                        required = false
                                    )
                                )
                        )
                    )
            ),
            SystemToolPromptCategory(
                categoryName = "Workflow Tools",
                tools =
                    listOf(
                        ToolPrompt(
                            name = "get_all_workflows",
                            description = "Get all workflows.",
                            parametersStructured = listOf()
                        ),
                        ToolPrompt(
                            name = "create_workflow",
                            description = "Create a workflow.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "name",
                                        type = "string",
                                        description = "workflow name",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "description",
                                        type = "string",
                                        description = "optional",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "nodes",
                                        type = "string",
                                        description = "optional, nodes JSON array string",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "connections",
                                        type = "string",
                                        description = "optional, connections JSON array string",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "enabled",
                                        type = "boolean",
                                        description = "optional",
                                        required = false,
                                        default = "true"
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "get_workflow",
                            description = "Get workflow detail.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "workflow_id",
                                        type = "string",
                                        description = "workflow id",
                                        required = true
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "update_workflow",
                            description = "Update a workflow.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "workflow_id",
                                        type = "string",
                                        description = "workflow id",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "name",
                                        type = "string",
                                        description = "optional",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "description",
                                        type = "string",
                                        description = "optional",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "nodes",
                                        type = "string",
                                        description = "optional, nodes JSON array string",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "connections",
                                        type = "string",
                                        description = "optional, connections JSON array string",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "enabled",
                                        type = "boolean",
                                        description = "optional",
                                        required = false
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "delete_workflow",
                            description = "Delete a workflow.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "workflow_id",
                                        type = "string",
                                        description = "workflow id",
                                        required = true
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "trigger_workflow",
                            description = "Trigger a workflow execution.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "workflow_id",
                                        type = "string",
                                        description = "workflow id",
                                        required = true
                                    )
                                )
                        )
                    )
            ),
            SystemToolPromptCategory(
                categoryName = "Chat Tools",
                tools =
                    listOf(
                        ToolPrompt(
                            name = "start_chat_service",
                            description = "Start the floating chat service.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "initial_mode",
                                        type = "string",
                                        description = "optional, initial floating mode: WINDOW, BALL, VOICE_BALL, FULLSCREEN, RESULT_DISPLAY, SCREEN_OCR",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "auto_enter_voice_chat",
                                        type = "boolean",
                                        description = "optional, if true then enter voice mode automatically when opening FULLSCREEN",
                                        required = false,
                                        default = "false"
                                    ),
                                    ToolParameterSchema(
                                        name = "wake_launched",
                                        type = "boolean",
                                        description = "optional, true if launched by wake word so UI can adjust behavior",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "timeout_ms",
                                        type = "integer",
                                        description = "optional, auto close the floating window after this timeout (milliseconds). <=0 disables auto-exit.",
                                        required = false
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "stop_chat_service",
                            description = "Stop the floating chat service.",
                            parametersStructured = listOf()
                        ),
                        ToolPrompt(
                            name = "create_new_chat",
                            description = "Create a new chat.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "group",
                                        type = "string",
                                        description = "optional group name for the new chat",
                                        required = false
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "list_chats",
                            description = "List chats.",
                            parametersStructured = listOf()
                        ),
                        ToolPrompt(
                            name = "switch_chat",
                            description = "Switch to a chat.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "chat_id",
                                        type = "string",
                                        description = "target chat id",
                                        required = true
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "send_message_to_ai",
                            description = "Send a user message to AI.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "message",
                                        type = "string",
                                        description = "message content",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "chat_id",
                                        type = "string",
                                        description = "optional, target chat id",
                                        required = false
                                    )
                                )
                        )
                    )
            ),
            SystemToolPromptCategory(
                categoryName = "Internal File Tools",
                tools =
                    listOf(
                        ToolPrompt(
                            name = "read_file_full",
                            description = "Read the full content of a file without enforcing size limit.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "path",
                                        type = "string",
                                        description = "file path",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "environment",
                                        type = "string",
                                        description = "optional, \"android\" (default) or \"linux\"",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "text_only",
                                        type = "boolean",
                                        description = "optional",
                                        required = false,
                                        default = "false"
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "read_file_binary",
                            description = "Read binary file and return base64 content.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "path",
                                        type = "string",
                                        description = "file path",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "environment",
                                        type = "string",
                                        description = "optional, \"android\" (default) or \"linux\"",
                                        required = false
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "write_file",
                            description = "Write content to a file.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "path",
                                        type = "string",
                                        description = "file path",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "content",
                                        type = "string",
                                        description = "file content",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "append",
                                        type = "boolean",
                                        description = "optional",
                                        required = false,
                                        default = "false"
                                    ),
                                    ToolParameterSchema(
                                        name = "environment",
                                        type = "string",
                                        description = "optional, \"android\" (default) or \"linux\"",
                                        required = false
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "write_file_binary",
                            description = "Write base64 content to a binary file.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "path",
                                        type = "string",
                                        description = "file path",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "base64Content",
                                        type = "string",
                                        description = "base64 encoded content",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "environment",
                                        type = "string",
                                        description = "optional, \"android\" (default) or \"linux\"",
                                        required = false
                                    )
                                )
                        )
                    )
            ),
            SystemToolPromptCategory(
                categoryName = "Internal UI Tools",
                tools =
                    listOf(
                        ToolPrompt(
                            name = "get_page_info",
                            description = "Get current page/window UI information.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "format",
                                        type = "string",
                                        description = "optional, xml/json",
                                        required = false,
                                        default = "xml"
                                    ),
                                    ToolParameterSchema(
                                        name = "detail",
                                        type = "string",
                                        description = "optional",
                                        required = false,
                                        default = "summary"
                                    ),
                                    ToolParameterSchema(
                                        name = "display",
                                        type = "string",
                                        description = "optional, display id for multi-display",
                                        required = false
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "tap",
                            description = "Tap at screen coordinates.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "x",
                                        type = "integer",
                                        description = "x coordinate",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "y",
                                        type = "integer",
                                        description = "y coordinate",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "display",
                                        type = "string",
                                        description = "optional, display id",
                                        required = false
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "long_press",
                            description = "Long press at screen coordinates.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "x",
                                        type = "integer",
                                        description = "x coordinate",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "y",
                                        type = "integer",
                                        description = "y coordinate",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "display",
                                        type = "string",
                                        description = "optional, display id",
                                        required = false
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "swipe",
                            description = "Swipe from start to end coordinates.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "start_x",
                                        type = "integer",
                                        description = "start x",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "start_y",
                                        type = "integer",
                                        description = "start y",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "end_x",
                                        type = "integer",
                                        description = "end x",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "end_y",
                                        type = "integer",
                                        description = "end y",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "duration",
                                        type = "integer",
                                        description = "optional, duration in ms",
                                        required = false,
                                        default = "300"
                                    ),
                                    ToolParameterSchema(
                                        name = "display",
                                        type = "string",
                                        description = "optional, display id",
                                        required = false
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "click_element",
                            description = "Click a UI element by resource id / class name / content description / bounds.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "resourceId",
                                        type = "string",
                                        description = "optional",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "className",
                                        type = "string",
                                        description = "optional",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "contentDesc",
                                        type = "string",
                                        description = "optional",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "bounds",
                                        type = "string",
                                        description = "optional, format: [left,top][right,bottom]",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "partialMatch",
                                        type = "boolean",
                                        description = "optional, enable partial match for selectors",
                                        required = false,
                                        default = "false"
                                    ),
                                    ToolParameterSchema(
                                        name = "index",
                                        type = "integer",
                                        description = "optional",
                                        required = false,
                                        default = "0"
                                    ),
                                    ToolParameterSchema(
                                        name = "display",
                                        type = "string",
                                        description = "optional, display id",
                                        required = false
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "set_input_text",
                            description = "Set input text in focused field.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "text",
                                        type = "string",
                                        description = "text to input (can be empty to clear)",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "display",
                                        type = "string",
                                        description = "optional, display id",
                                        required = false
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "press_key",
                            description = "Press a key via keyevent.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "key_code",
                                        type = "string",
                                        description = "key code, e.g. KEYCODE_HOME",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "display",
                                        type = "string",
                                        description = "optional, display id",
                                        required = false
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "capture_screenshot",
                            description = "Capture a screenshot and return a file path.",
                            parametersStructured = listOf()
                        ),
                        ToolPrompt(
                            name = "run_ui_subagent",
                            description = "Run a lightweight UI automation subagent.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "intent",
                                        type = "string",
                                        description = "task description",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "max_steps",
                                        type = "integer",
                                        description = "optional",
                                        required = false,
                                        default = "20"
                                    ),
                                    ToolParameterSchema(
                                        name = "agent_id",
                                        type = "string",
                                        description = "optional, reuse agent session id",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "target_app",
                                        type = "string",
                                        description = "optional, target app package name",
                                        required = false
                                    )
                                )
                        )
                    )
            ),
            SystemToolPromptCategory(
                categoryName = "Internal System Tools",
                tools =
                    listOf(
                        ToolPrompt(
                            name = "close_all_virtual_displays",
                            description = "Close all virtual display overlays.",
                            parametersStructured = listOf()
                        ),
                        ToolPrompt(
                            name = "modify_system_setting",
                            description = "Modify a system setting.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "setting",
                                        type = "string",
                                        description = "setting key (alias: key)",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "value",
                                        type = "string",
                                        description = "setting value",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "namespace",
                                        type = "string",
                                        description = "optional, system/secure/global",
                                        required = false,
                                        default = "system"
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "get_system_setting",
                            description = "Get a system setting.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "setting",
                                        type = "string",
                                        description = "setting key (alias: key)",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "namespace",
                                        type = "string",
                                        description = "optional, system/secure/global",
                                        required = false,
                                        default = "system"
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "install_app",
                            description = "Request installing an APK.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "path",
                                        type = "string",
                                        description = "APK file path (alias: apk_path)",
                                        required = true
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "uninstall_app",
                            description = "Request uninstalling an app.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "package_name",
                                        type = "string",
                                        description = "app package name",
                                        required = true
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "list_installed_apps",
                            description = "List installed apps.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "include_system_apps",
                                        type = "boolean",
                                        description = "optional",
                                        required = false,
                                        default = "false"
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "start_app",
                            description = "Start an app.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "package_name",
                                        type = "string",
                                        description = "app package name",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "activity",
                                        type = "string",
                                        description = "optional, activity class name",
                                        required = false
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "stop_app",
                            description = "Stop an app background process.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "package_name",
                                        type = "string",
                                        description = "app package name",
                                        required = true
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "get_notifications",
                            description = "Get device notifications.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "limit",
                                        type = "integer",
                                        description = "optional",
                                        required = false,
                                        default = "10"
                                    ),
                                    ToolParameterSchema(
                                        name = "include_ongoing",
                                        type = "boolean",
                                        description = "optional",
                                        required = false,
                                        default = "false"
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "get_device_location",
                            description = "Get device location.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "timeout",
                                        type = "integer",
                                        description = "optional, seconds",
                                        required = false,
                                        default = "10"
                                    ),
                                    ToolParameterSchema(
                                        name = "high_accuracy",
                                        type = "boolean",
                                        description = "optional",
                                        required = false,
                                        default = "false"
                                    ),
                                    ToolParameterSchema(
                                        name = "include_address",
                                        type = "boolean",
                                        description = "optional",
                                        required = false,
                                        default = "true"
                                    )
                                )
                        )
                    )
            ),
            SystemToolPromptCategory(
                categoryName = "FFmpeg Tools",
                tools =
                    listOf(
                        ToolPrompt(
                            name = "ffmpeg_execute",
                            description = "Execute an FFmpeg command.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "command",
                                        type = "string",
                                        description = "ffmpeg command",
                                        required = true
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "ffmpeg_info",
                            description = "Get FFmpeg information.",
                            parametersStructured = listOf()
                        ),
                        ToolPrompt(
                            name = "ffmpeg_convert",
                            description = "Convert a video file using FFmpeg.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "input_path",
                                        type = "string",
                                        description = "input file path",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "output_path",
                                        type = "string",
                                        description = "output file path",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "format",
                                        type = "string",
                                        description = "optional",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "resolution",
                                        type = "string",
                                        description = "optional, e.g. 1280x720",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "bitrate",
                                        type = "string",
                                        description = "optional, e.g. 1000k",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "audio_codec",
                                        type = "string",
                                        description = "optional",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "video_codec",
                                        type = "string",
                                        description = "optional",
                                        required = false
                                    )
                                )
                        )
                    )
            )
        )

    val internalToolCategoriesCn: List<SystemToolPromptCategory> =
        listOf(
            SystemToolPromptCategory(
                categoryName = "内部工具",
                tools =
                    listOf(
                        ToolPrompt(
                            name = "execute_shell",
                            description = "执行设备 Shell 命令。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "command",
                                        type = "string",
                                        description = "要执行的命令",
                                        required = true
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "create_terminal_session",
                            description = "创建或获取终端会话。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "session_name",
                                        type = "string",
                                        description = "终端会话名称",
                                        required = true
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "execute_in_terminal_session",
                            description = "在终端会话中执行命令，并一次性返回完整输出。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "session_id",
                                        type = "string",
                                        description = "终端会话 ID",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "command",
                                        type = "string",
                                        description = "要执行的命令",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "timeout_ms",
                                        type = "integer",
                                        description = "可选，超时时间（毫秒）",
                                        required = false,
                                        default = "1800000"
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "close_terminal_session",
                            description = "关闭终端会话。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "session_id",
                                        type = "string",
                                        description = "终端会话 ID",
                                        required = true
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "calculate",
                            description = "计算数学表达式。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "expression",
                                        type = "string",
                                        description = "数学表达式，例如 \"(1+2)*3\"",
                                        required = true
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "execute_intent",
                            description = "执行 Android Intent（activity/broadcast/service）。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "action",
                                        type = "string",
                                        description = "可选，Intent action",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "uri",
                                        type = "string",
                                        description = "可选，data URI",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "package",
                                        type = "string",
                                        description = "可选，包名",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "component",
                                        type = "string",
                                        description = "可选，\"package/class\" 格式",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "type",
                                        type = "string",
                                        description = "可选，activity/broadcast/service",
                                        required = false,
                                        default = "activity"
                                    ),
                                    ToolParameterSchema(
                                        name = "flags",
                                        type = "string",
                                        description = "可选，flag 整数数组 JSON 字符串（或单个整数）",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "extras",
                                        type = "string",
                                        description = "可选，extras 的 JSON 对象字符串",
                                        required = false
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "device_info",
                            description = "获取设备信息。",
                            parametersStructured = listOf()
                        )
                    )
            ),
            SystemToolPromptCategory(
                categoryName = "Tasker 工具",
                tools =
                    listOf(
                        ToolPrompt(
                            name = "trigger_tasker_event",
                            description = "触发 Tasker 事件。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "task_type",
                                        type = "string",
                                        description = "Tasker 事件类型",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "arg1",
                                        type = "string",
                                        description = "可选",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "arg2",
                                        type = "string",
                                        description = "可选",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "arg3",
                                        type = "string",
                                        description = "可选",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "arg4",
                                        type = "string",
                                        description = "可选",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "arg5",
                                        type = "string",
                                        description = "可选",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "args_json",
                                        type = "string",
                                        description = "可选，JSON 对象字符串",
                                        required = false
                                    )
                                )
                        )
                    )
            ),
            SystemToolPromptCategory(
                categoryName = "工作流工具",
                tools =
                    listOf(
                        ToolPrompt(
                            name = "get_all_workflows",
                            description = "获取所有工作流列表。",
                            parametersStructured = listOf()
                        ),
                        ToolPrompt(
                            name = "create_workflow",
                            description = "创建工作流。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "name",
                                        type = "string",
                                        description = "工作流名称",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "description",
                                        type = "string",
                                        description = "可选",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "nodes",
                                        type = "string",
                                        description = "可选，节点 JSON 数组字符串",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "connections",
                                        type = "string",
                                        description = "可选，连线 JSON 数组字符串",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "enabled",
                                        type = "boolean",
                                        description = "可选",
                                        required = false,
                                        default = "true"
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "get_workflow",
                            description = "获取工作流详情。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "workflow_id",
                                        type = "string",
                                        description = "工作流 ID",
                                        required = true
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "update_workflow",
                            description = "更新工作流。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "workflow_id",
                                        type = "string",
                                        description = "工作流 ID",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "name",
                                        type = "string",
                                        description = "可选",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "description",
                                        type = "string",
                                        description = "可选",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "nodes",
                                        type = "string",
                                        description = "可选，节点 JSON 数组字符串",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "connections",
                                        type = "string",
                                        description = "可选，连线 JSON 数组字符串",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "enabled",
                                        type = "boolean",
                                        description = "可选",
                                        required = false
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "delete_workflow",
                            description = "删除工作流。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "workflow_id",
                                        type = "string",
                                        description = "工作流 ID",
                                        required = true
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "trigger_workflow",
                            description = "触发工作流执行。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "workflow_id",
                                        type = "string",
                                        description = "工作流 ID",
                                        required = true
                                    )
                                )
                        )
                    )
            ),
            SystemToolPromptCategory(
                categoryName = "对话工具",
                tools =
                    listOf(
                        ToolPrompt(
                            name = "start_chat_service",
                            description = "启动对话服务（悬浮窗）。",
                            parametersStructured = listOf()
                        ),
                        ToolPrompt(
                            name = "stop_chat_service",
                            description = "停止对话服务（悬浮窗）。",
                            parametersStructured = listOf()
                        ),
                        ToolPrompt(
                            name = "create_new_chat",
                            description = "创建新的对话。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "group",
                                        type = "string",
                                        description = "新对话分组名（可选）",
                                        required = false
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "list_chats",
                            description = "列出所有对话。",
                            parametersStructured = listOf()
                        ),
                        ToolPrompt(
                            name = "switch_chat",
                            description = "切换到指定对话。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "chat_id",
                                        type = "string",
                                        description = "目标对话 ID",
                                        required = true
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "send_message_to_ai",
                            description = "向 AI 发送消息。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "message",
                                        type = "string",
                                        description = "消息内容",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "chat_id",
                                        type = "string",
                                        description = "可选，目标对话 ID",
                                        required = false
                                    )
                                )
                        )
                    )
            ),
            SystemToolPromptCategory(
                categoryName = "内部文件工具",
                tools =
                    listOf(
                        ToolPrompt(
                            name = "read_file_full",
                            description = "读取完整文件内容（不限制大小）。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "path",
                                        type = "string",
                                        description = "文件路径",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "environment",
                                        type = "string",
                                        description = "可选，\"android\"（默认）或 \"linux\"",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "text_only",
                                        type = "boolean",
                                        description = "可选",
                                        required = false,
                                        default = "false"
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "read_file_binary",
                            description = "读取二进制文件并返回 Base64 内容。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "path",
                                        type = "string",
                                        description = "文件路径",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "environment",
                                        type = "string",
                                        description = "可选，\"android\"（默认）或 \"linux\"",
                                        required = false
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "write_file",
                            description = "写入文件内容。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "path",
                                        type = "string",
                                        description = "文件路径",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "content",
                                        type = "string",
                                        description = "文件内容",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "append",
                                        type = "boolean",
                                        description = "可选",
                                        required = false,
                                        default = "false"
                                    ),
                                    ToolParameterSchema(
                                        name = "environment",
                                        type = "string",
                                        description = "可选，\"android\"（默认）或 \"linux\"",
                                        required = false
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "write_file_binary",
                            description = "将 Base64 内容写入二进制文件。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "path",
                                        type = "string",
                                        description = "文件路径",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "base64Content",
                                        type = "string",
                                        description = "Base64 编码内容",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "environment",
                                        type = "string",
                                        description = "可选，\"android\"（默认）或 \"linux\"",
                                        required = false
                                    )
                                )
                        )
                    )
            ),
            SystemToolPromptCategory(
                categoryName = "内部 UI 工具",
                tools =
                    listOf(
                        ToolPrompt(
                            name = "get_page_info",
                            description = "获取当前页面/窗口 UI 信息。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "format",
                                        type = "string",
                                        description = "可选，xml/json",
                                        required = false,
                                        default = "xml"
                                    ),
                                    ToolParameterSchema(
                                        name = "detail",
                                        type = "string",
                                        description = "可选",
                                        required = false,
                                        default = "summary"
                                    ),
                                    ToolParameterSchema(
                                        name = "display",
                                        type = "string",
                                        description = "可选，多屏 display id",
                                        required = false
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "tap",
                            description = "点击屏幕坐标。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "x",
                                        type = "integer",
                                        description = "x 坐标",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "y",
                                        type = "integer",
                                        description = "y 坐标",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "display",
                                        type = "string",
                                        description = "可选，多屏 display id",
                                        required = false
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "long_press",
                            description = "长按屏幕坐标。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "x",
                                        type = "integer",
                                        description = "x 坐标",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "y",
                                        type = "integer",
                                        description = "y 坐标",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "display",
                                        type = "string",
                                        description = "可选，多屏 display id",
                                        required = false
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "swipe",
                            description = "执行滑动手势。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "start_x",
                                        type = "integer",
                                        description = "起始 x",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "start_y",
                                        type = "integer",
                                        description = "起始 y",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "end_x",
                                        type = "integer",
                                        description = "结束 x",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "end_y",
                                        type = "integer",
                                        description = "结束 y",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "duration",
                                        type = "integer",
                                        description = "可选，持续时间（毫秒）",
                                        required = false,
                                        default = "300"
                                    ),
                                    ToolParameterSchema(
                                        name = "display",
                                        type = "string",
                                        description = "可选，多屏 display id",
                                        required = false
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "click_element",
                            description = "点击 UI 元素（resourceId / className / contentDesc / bounds）。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "resourceId",
                                        type = "string",
                                        description = "可选",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "className",
                                        type = "string",
                                        description = "可选",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "contentDesc",
                                        type = "string",
                                        description = "可选",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "bounds",
                                        type = "string",
                                        description = "可选，格式：[left,top][right,bottom]",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "partialMatch",
                                        type = "boolean",
                                        description = "可选，是否启用部分匹配",
                                        required = false,
                                        default = "false"
                                    ),
                                    ToolParameterSchema(
                                        name = "index",
                                        type = "integer",
                                        description = "可选",
                                        required = false,
                                        default = "0"
                                    ),
                                    ToolParameterSchema(
                                        name = "display",
                                        type = "string",
                                        description = "可选，多屏 display id",
                                        required = false
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "set_input_text",
                            description = "设置输入框文本（可传空字符串以清空）。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "text",
                                        type = "string",
                                        description = "要输入的文本",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "display",
                                        type = "string",
                                        description = "可选，多屏 display id",
                                        required = false
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "press_key",
                            description = "按下按键（keyevent）。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "key_code",
                                        type = "string",
                                        description = "按键码，例如 KEYCODE_HOME",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "display",
                                        type = "string",
                                        description = "可选，多屏 display id",
                                        required = false
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "capture_screenshot",
                            description = "截取屏幕截图并返回文件路径。",
                            parametersStructured = listOf()
                        ),
                        ToolPrompt(
                            name = "run_ui_subagent",
                            description = "运行轻量 UI 自动化子代理。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "intent",
                                        type = "string",
                                        description = "任务描述",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "max_steps",
                                        type = "integer",
                                        description = "可选",
                                        required = false,
                                        default = "20"
                                    ),
                                    ToolParameterSchema(
                                        name = "agent_id",
                                        type = "string",
                                        description = "可选，可复用的 agent 会话 ID",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "target_app",
                                        type = "string",
                                        description = "可选，目标应用包名",
                                        required = false
                                    )
                                )
                        )
                    )
            ),
            SystemToolPromptCategory(
                categoryName = "内部系统工具",
                tools =
                    listOf(
                        ToolPrompt(
                            name = "close_all_virtual_displays",
                            description = "关闭所有虚拟屏幕。",
                            parametersStructured = listOf()
                        ),
                        ToolPrompt(
                            name = "modify_system_setting",
                            description = "修改系统设置。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "setting",
                                        type = "string",
                                        description = "设置项 key（别名：key）",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "value",
                                        type = "string",
                                        description = "设置值",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "namespace",
                                        type = "string",
                                        description = "可选，system/secure/global",
                                        required = false,
                                        default = "system"
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "get_system_setting",
                            description = "获取系统设置。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "setting",
                                        type = "string",
                                        description = "设置项 key（别名：key）",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "namespace",
                                        type = "string",
                                        description = "可选，system/secure/global",
                                        required = false,
                                        default = "system"
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "install_app",
                            description = "请求安装 APK（需要用户确认）。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "path",
                                        type = "string",
                                        description = "APK 文件路径（别名：apk_path）",
                                        required = true
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "uninstall_app",
                            description = "请求卸载应用（需要用户确认）。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "package_name",
                                        type = "string",
                                        description = "应用包名",
                                        required = true
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "list_installed_apps",
                            description = "列出已安装应用。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "include_system_apps",
                                        type = "boolean",
                                        description = "可选",
                                        required = false,
                                        default = "false"
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "start_app",
                            description = "启动应用。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "package_name",
                                        type = "string",
                                        description = "应用包名",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "activity",
                                        type = "string",
                                        description = "可选，Activity 类名",
                                        required = false
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "stop_app",
                            description = "停止应用后台进程。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "package_name",
                                        type = "string",
                                        description = "应用包名",
                                        required = true
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "get_notifications",
                            description = "获取设备通知。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "limit",
                                        type = "integer",
                                        description = "可选",
                                        required = false,
                                        default = "10"
                                    ),
                                    ToolParameterSchema(
                                        name = "include_ongoing",
                                        type = "boolean",
                                        description = "可选",
                                        required = false,
                                        default = "false"
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "get_device_location",
                            description = "获取设备位置信息。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "timeout",
                                        type = "integer",
                                        description = "可选，超时（秒）",
                                        required = false,
                                        default = "10"
                                    ),
                                    ToolParameterSchema(
                                        name = "high_accuracy",
                                        type = "boolean",
                                        description = "可选",
                                        required = false,
                                        default = "false"
                                    ),
                                    ToolParameterSchema(
                                        name = "include_address",
                                        type = "boolean",
                                        description = "可选",
                                        required = false,
                                        default = "true"
                                    )
                                )
                        )
                    )
            ),
            SystemToolPromptCategory(
                categoryName = "FFmpeg 工具",
                tools =
                    listOf(
                        ToolPrompt(
                            name = "ffmpeg_execute",
                            description = "执行 FFmpeg 命令。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "command",
                                        type = "string",
                                        description = "FFmpeg 命令",
                                        required = true
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "ffmpeg_info",
                            description = "获取 FFmpeg 信息。",
                            parametersStructured = listOf()
                        ),
                        ToolPrompt(
                            name = "ffmpeg_convert",
                            description = "使用 FFmpeg 转换视频文件。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "input_path",
                                        type = "string",
                                        description = "输入文件路径",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "output_path",
                                        type = "string",
                                        description = "输出文件路径",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "format",
                                        type = "string",
                                        description = "可选",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "resolution",
                                        type = "string",
                                        description = "可选，例如 1280x720",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "bitrate",
                                        type = "string",
                                        description = "可选，例如 1000k",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "audio_codec",
                                        type = "string",
                                        description = "可选",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "video_codec",
                                        type = "string",
                                        description = "可选",
                                        required = false
                                    )
                                )
                        )
                    )
            )
        )
}
