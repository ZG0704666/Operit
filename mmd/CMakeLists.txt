cmake_minimum_required(VERSION 3.22.1)

project("mmd_jni")

set(SABA_DIR "${CMAKE_CURRENT_SOURCE_DIR}/third_party/saba")
set(SABA_DIR_FALLBACK "${CMAKE_CURRENT_SOURCE_DIR}/../third_party/saba")

if (EXISTS "${SABA_DIR}/CMakeLists.txt")
    set(OPERIT_SABA_DIR "${SABA_DIR}")
elseif (EXISTS "${SABA_DIR_FALLBACK}/CMakeLists.txt")
    set(OPERIT_SABA_DIR "${SABA_DIR_FALLBACK}")
endif()

add_library(
    MmdWrapper
    SHARED
    src/main/cpp/mmd_jni.cpp
)

if (DEFINED OPERIT_SABA_DIR)
    target_compile_definitions(MmdWrapper PRIVATE OPERIT_HAS_SABA=1)

    target_include_directories(MmdWrapper PRIVATE
        "${OPERIT_SABA_DIR}/src"
        "${OPERIT_SABA_DIR}/external/glm/include"
        "${OPERIT_SABA_DIR}/external/spdlog/include"
        "${OPERIT_SABA_DIR}/external/stb/include"
    )

    target_sources(MmdWrapper PRIVATE
        "${OPERIT_SABA_DIR}/src/Saba/Base/File.cpp"
        "${OPERIT_SABA_DIR}/src/Saba/Base/Log.cpp"
        "${OPERIT_SABA_DIR}/src/Saba/Base/Path.cpp"
        "${OPERIT_SABA_DIR}/src/Saba/Base/Singleton.cpp"
        "${OPERIT_SABA_DIR}/src/Saba/Base/UnicodeUtil.cpp"
        "${OPERIT_SABA_DIR}/src/Saba/Model/MMD/SjisToUnicode.cpp"
        "${OPERIT_SABA_DIR}/src/Saba/Model/MMD/MMDIkSolver.cpp"
        "${OPERIT_SABA_DIR}/src/Saba/Model/MMD/MMDMaterial.cpp"
        "${OPERIT_SABA_DIR}/src/Saba/Model/MMD/MMDModel.cpp"
        "${OPERIT_SABA_DIR}/src/Saba/Model/MMD/MMDMorph.cpp"
        "${OPERIT_SABA_DIR}/src/Saba/Model/MMD/MMDNode.cpp"
        "${OPERIT_SABA_DIR}/src/Saba/Model/MMD/PMDFile.cpp"
        "${OPERIT_SABA_DIR}/src/Saba/Model/MMD/PMDModel.cpp"
        "${OPERIT_SABA_DIR}/src/Saba/Model/MMD/PMXFile.cpp"
        "${OPERIT_SABA_DIR}/src/Saba/Model/MMD/PMXModel.cpp"
        "${OPERIT_SABA_DIR}/src/Saba/Model/MMD/VMDAnimation.cpp"
        "${OPERIT_SABA_DIR}/src/Saba/Model/MMD/VMDFile.cpp"
        "${OPERIT_SABA_DIR}/src/Saba/Model/MMD/VPDFile.cpp"
        "src/main/cpp/mmd_physics_stub.cpp"
    )
else()
    target_compile_definitions(MmdWrapper PRIVATE OPERIT_HAS_SABA=0)
endif()

target_link_libraries(
    MmdWrapper
    android
    log
)

# 16KB page size support (Android 15+ requirement)
target_link_options(MmdWrapper PRIVATE "-Wl,-z,max-page-size=16384")
