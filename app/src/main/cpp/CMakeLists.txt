cmake_minimum_required(VERSION 3.22.1)

project(streamnative)

set(WEBRTC_AEC3_DIR "${CMAKE_CURRENT_SOURCE_DIR}/thirdparty/webrtc_aec3")
add_subdirectory("${WEBRTC_AEC3_DIR}" "${CMAKE_BINARY_DIR}/webrtc_aec3_build")

function(force_release_flags_for_debug target_name)
    if(TARGET ${target_name})
        # Keep app variant as Debug, but compile heavy native libs with release-like optimization.
        target_compile_definitions(${target_name} PRIVATE $<$<CONFIG:Debug>:NDEBUG>)
        target_compile_options(${target_name} PRIVATE $<$<CONFIG:Debug>:-O3 -g0>)
    endif()
endfunction()

if(ANDROID)
    # Build sherpa-ncnn as part of Android Studio externalNativeBuild.
    # Use local submodules to avoid downloading a different ncnn build.
    list(PREPEND CMAKE_MODULE_PATH "${CMAKE_CURRENT_SOURCE_DIR}/cmake")
    list(APPEND CMAKE_MODULE_PATH "${CMAKE_CURRENT_SOURCE_DIR}/thirdparty/sherpa-ncnn/cmake")
    include_directories("${CMAKE_CURRENT_SOURCE_DIR}/thirdparty/sherpa-ncnn")

    set(FETCHCONTENT_SOURCE_DIR_NCNN "${CMAKE_CURRENT_SOURCE_DIR}/thirdparty/ncnn" CACHE PATH "" FORCE)

    set(SHERPA_NCNN_ENABLE_PORTAUDIO OFF CACHE BOOL "" FORCE)
    set(SHERPA_NCNN_ENABLE_BINARY OFF CACHE BOOL "" FORCE)
    set(SHERPA_NCNN_ENABLE_TEST OFF CACHE BOOL "" FORCE)
    set(SHERPA_NCNN_ENABLE_C_API OFF CACHE BOOL "" FORCE)
    set(SHERPA_NCNN_ENABLE_JNI ON CACHE BOOL "" FORCE)
    set(SHERPA_NCNN_ENABLE_PYTHON OFF CACHE BOOL "" FORCE)
    set(SHERPA_NCNN_ENABLE_FFMPEG_EXAMPLES OFF CACHE BOOL "" FORCE)
    set(SHERPA_NCNN_ENABLE_GENERATE_INT8_SCALE_TABLE OFF CACHE BOOL "" FORCE)

    # Keep sherpa/ncnn static inside libsherpa-ncnn-jni and avoid OpenMP abort issues.
    set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
    set(NCNN_OPENMP OFF CACHE BOOL "" FORCE)

    add_subdirectory(
            "${CMAKE_CURRENT_SOURCE_DIR}/thirdparty/sherpa-ncnn"
            "${CMAKE_BINARY_DIR}/sherpa_ncnn_build"
    )

    force_release_flags_for_debug(ncnn)
    force_release_flags_for_debug(sherpa-ncnn-core)
    force_release_flags_for_debug(sherpa-ncnn-jni)
    force_release_flags_for_debug(webrtc_aec3)
endif()

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
