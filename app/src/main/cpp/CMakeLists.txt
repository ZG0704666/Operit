cmake_minimum_required(VERSION 3.22.1)

project(streamnative)

set(WEBRTC_AEC3_DIR "${CMAKE_CURRENT_SOURCE_DIR}/thirdparty/webrtc_aec3")
add_subdirectory("${WEBRTC_AEC3_DIR}" "${CMAKE_BINARY_DIR}/webrtc_aec3_build")

if(ANDROID)
    # Remove incompatible x86/mips optimized sources from upstream AEC3 subtree.
    # Android arm64 build should keep generic/neon paths only.
    get_target_property(webrtc_aec3_sources webrtc_aec3 SOURCES)
    if(webrtc_aec3_sources)
        list(
                FILTER
                webrtc_aec3_sources
                EXCLUDE
                REGEX
                "cpu_features_android\\.c$|_sse2?\\.cc$|_mips\\.cc$"
        )
        set_property(TARGET webrtc_aec3 PROPERTY SOURCES ${webrtc_aec3_sources})
    endif()
endif()

add_library(
        streamnative
        SHARED
        streamnative/native_xml_splitter.cpp
        streamnative/native_markdown_splitter.cpp
        streamnative/StreamOperators.cpp
        streamnative/plugins/StreamXmlPlugin.cpp
        streamnative/plugins/BaseJsonPlugin.cpp
        streamnative/plugins/StreamJsonPlugin.cpp
        streamnative/plugins/StreamPureJsonPlugin.cpp
        streamnative/plugins/StreamPlanExecutionPlugin.cpp
        streamnative/plugins/StreamMarkdownPlugin.cpp
        streamnative/StreamBuilders.cpp
        streamnative/HotStream.cpp
        streamnative/StringExtensions.cpp
)

target_include_directories(
        streamnative
        PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}
)

add_library(
        audioaec
        SHARED
        audioaec/webrtc_aec3_jni.cpp
)

target_include_directories(
        audioaec
        PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}
)

if(ANDROID)
    target_compile_definitions(
            webrtc_aec3
            PUBLIC
            WEBRTC_ANDROID
            WEBRTC_POSIX
    )
endif()

# The bundled AEC3 subtree depends on absl APIs removed in C++20
# (e.g. std::result_of). Build this target with C++17 explicitly.
set_target_properties(
        webrtc_aec3
        PROPERTIES
        CXX_STANDARD 17
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS ON
)

find_library(
        log-lib
        log
)

target_link_libraries(
        streamnative
        ${log-lib}
)

target_link_libraries(
        audioaec
        webrtc_aec3
        ${log-lib}
)
