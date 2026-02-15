package com.ai.assistance.operit.core.tools.javascript

internal fun buildComposeDslRuntimeWrappedScript(script: String): String {
    return """
        $script

        (function() {
            function __operit_is_promise(__value) {
                return !!(__value && typeof __value.then === 'function');
            }

            function __operit_wrap_compose_response(__bundle, __tree) {
                return {
                    tree: __tree,
                    state: __bundle.state,
                    memo: __bundle.memo
                };
            }

            function __operit_build_compose_response(__bundle, __entry) {
                var __tree = __entry(__bundle.ctx);
                if (__operit_is_promise(__tree)) {
                    return __tree.then(function(__resolvedTree) {
                        return __operit_wrap_compose_response(__bundle, __resolvedTree);
                    });
                }
                return __operit_wrap_compose_response(__bundle, __tree);
            }

            function __operitResolveComposeEntry() {
                try {
                    if (typeof module !== 'undefined' && module && module.exports) {
                        if (typeof module.exports.default === 'function') {
                            return module.exports.default;
                        }
                        if (typeof module.exports.Screen === 'function') {
                            return module.exports.Screen;
                        }
                    }
                    if (typeof exports !== 'undefined' && exports) {
                        if (typeof exports.default === 'function') {
                            return exports.default;
                        }
                        if (typeof exports.Screen === 'function') {
                            return exports.Screen;
                        }
                    }
                    if (typeof window !== 'undefined') {
                        if (typeof window.default === 'function') {
                            return window.default;
                        }
                        if (typeof window.Screen === 'function') {
                            return window.Screen;
                        }
                    }
                } catch (e) {
                    console.error('resolve compose entry failed:', e);
                }
                return null;
            }

            function __operit_render_compose_dsl(__runtimeOptions) {
                if (typeof OperitComposeDslRuntime === 'undefined') {
                    throw new Error('OperitComposeDslRuntime bridge is not initialized');
                }
                var __bundle = OperitComposeDslRuntime.createContext(__runtimeOptions || {});
                var __entry = __operitResolveComposeEntry();
                if (typeof __entry !== 'function') {
                    throw new Error(
                        'compose_dsl entry function not found, expected default export or Screen function'
                    );
                }
                if (typeof window !== 'undefined') {
                    window.__operit_compose_bundle = __bundle;
                    window.__operit_compose_entry = __entry;
                }
                return __operit_build_compose_response(__bundle, __entry);
            }

            function __operit_dispatch_compose_dsl_action(__actionRequest) {
                if (typeof window === 'undefined') {
                    throw new Error('compose action dispatch requires window runtime');
                }
                var __bundle = window.__operit_compose_bundle;
                var __entry = window.__operit_compose_entry;
                if (!__bundle || typeof __entry !== 'function') {
                    throw new Error('compose_dsl runtime is not initialized, render first');
                }
                if (typeof __bundle.invokeAction !== 'function') {
                    throw new Error('compose_dsl runtime action bridge is not available');
                }

                var __request =
                    __actionRequest && typeof __actionRequest === 'object'
                        ? __actionRequest
                        : {};
                var __actionId = String(
                    __request.__action_id || __request.actionId || ''
                ).trim();
                if (!__actionId) {
                    throw new Error('compose action id is required');
                }

                var __payload =
                    Object.prototype.hasOwnProperty.call(__request, '__action_payload')
                        ? __request.__action_payload
                        : __request.payload;

                function __operit_send_intermediate_result(__value) {
                    if (
                        typeof NativeInterface === 'undefined' ||
                        !NativeInterface ||
                        typeof NativeInterface.sendIntermediateResult !== 'function'
                    ) {
                        return;
                    }
                    NativeInterface.sendIntermediateResult(JSON.stringify(__value));
                }

                var __maybePromise = __bundle.invokeAction(__actionId, __payload);
                if (__maybePromise && typeof __maybePromise.then === 'function') {
                    try {
                        var __intermediateResponse = __operit_build_compose_response(__bundle, __entry);
                        if (__operit_is_promise(__intermediateResponse)) {
                            __intermediateResponse.then(function(__resolvedIntermediate) {
                                __operit_send_intermediate_result(__resolvedIntermediate);
                            });
                        } else {
                            __operit_send_intermediate_result(__intermediateResponse);
                        }
                    } catch (__intermediateError) {
                        try {
                            console.warn('compose intermediate render failed:', __intermediateError);
                        } catch (__ignore) {
                        }
                    }
                    return __maybePromise.then(function() {
                        return __operit_build_compose_response(__bundle, __entry);
                    });
                }
                return __operit_build_compose_response(__bundle, __entry);
            }

            if (typeof exports !== 'undefined' && exports) {
                exports.__operit_render_compose_dsl = __operit_render_compose_dsl;
                exports.__operit_dispatch_compose_dsl_action =
                    __operit_dispatch_compose_dsl_action;
            }
            if (typeof module !== 'undefined' && module && module.exports) {
                module.exports.__operit_render_compose_dsl = __operit_render_compose_dsl;
                module.exports.__operit_dispatch_compose_dsl_action =
                    __operit_dispatch_compose_dsl_action;
            }
            if (typeof window !== 'undefined') {
                window.__operit_render_compose_dsl = __operit_render_compose_dsl;
                window.__operit_dispatch_compose_dsl_action =
                    __operit_dispatch_compose_dsl_action;
            }
        })();
    """.trimIndent()
}
