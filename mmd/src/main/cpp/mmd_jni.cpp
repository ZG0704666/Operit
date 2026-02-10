#include <jni.h>

#include <android/log.h>

#include <algorithm>
#include <cctype>
#include <cstdint>
#include <initializer_list>
#include <mutex>
#include <string>
#include <vector>

#if defined(OPERIT_HAS_SABA) && OPERIT_HAS_SABA
#include "Saba/Model/MMD/PMDFile.h"
#include "Saba/Model/MMD/PMXFile.h"
#include "Saba/Model/MMD/VMDFile.h"
#endif

#define TAG "MmdNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace {

constexpr int64_t kModelFormatPmd = 1;
constexpr int64_t kModelFormatPmx = 2;

constexpr const char* kUnavailableReason =
    "saba submodule not found. Ensure mmd/third_party/saba exists and is initialized.";

std::mutex gLastErrorMutex;
std::string gLastError;

void setLastError(const std::string& error) {
    {
        std::lock_guard<std::mutex> lock(gLastErrorMutex);
        gLastError = error;
    }
    LOGE("%s", error.c_str());
}

void clearLastError() {
    std::lock_guard<std::mutex> lock(gLastErrorMutex);
    gLastError.clear();
}

std::string getLastErrorCopy() {
    std::lock_guard<std::mutex> lock(gLastErrorMutex);
    return gLastError;
}

std::string jstringToString(JNIEnv* env, jstring jvalue) {
    if (jvalue == nullptr) {
        return "";
    }

    const char* cvalue = env->GetStringUTFChars(jvalue, nullptr);
    if (cvalue == nullptr) {
        return "";
    }

    std::string value(cvalue);
    env->ReleaseStringUTFChars(jvalue, cvalue);
    return value;
}

jstring stringToJString(JNIEnv* env, const std::string& value) {
    return env->NewStringUTF(value.c_str());
}

std::string toLowerAscii(std::string value) {
    std::transform(value.begin(), value.end(), value.begin(), [](unsigned char character) {
        return static_cast<char>(std::tolower(character));
    });
    return value;
}

std::string getFileExtension(const std::string& filepath) {
    const size_t dotPosition = filepath.find_last_of('.');
    if (dotPosition == std::string::npos) {
        return "";
    }

    return toLowerAscii(filepath.substr(dotPosition + 1));
}

#if defined(OPERIT_HAS_SABA) && OPERIT_HAS_SABA

enum class ModelFileType {
    Unknown = 0,
    Pmd,
    Pmx,
};

struct ModelParseResult {
    ModelFileType fileType = ModelFileType::Unknown;
    std::string modelName;
    int64_t vertexCount = 0;
    int64_t faceCount = 0;
    int64_t materialCount = 0;
    int64_t boneCount = 0;
    int64_t morphCount = 0;
    int64_t rigidBodyCount = 0;
    int64_t jointCount = 0;
};

struct MotionParseResult {
    std::string modelName;
    int64_t motionCount = 0;
    int64_t morphCount = 0;
    int64_t cameraCount = 0;
    int64_t lightCount = 0;
    int64_t shadowCount = 0;
    int64_t ikCount = 0;
};

ModelFileType detectModelFileType(const std::string& modelPath) {
    const std::string extension = getFileExtension(modelPath);
    if (extension == "pmd") {
        return ModelFileType::Pmd;
    }
    if (extension == "pmx") {
        return ModelFileType::Pmx;
    }
    return ModelFileType::Unknown;
}

bool parseModelFile(const std::string& modelPath, ModelParseResult* outResult, std::string* outError) {
    if (outResult == nullptr) {
        if (outError != nullptr) {
            *outError = "internal error: model output buffer is null.";
        }
        return false;
    }

    const ModelFileType modelFileType = detectModelFileType(modelPath);
    if (modelFileType == ModelFileType::Unknown) {
        if (outError != nullptr) {
            *outError = "unsupported model extension; expected .pmd or .pmx.";
        }
        return false;
    }

    if (modelFileType == ModelFileType::Pmd) {
        saba::PMDFile pmdFile;
        if (!saba::ReadPMDFile(&pmdFile, modelPath.c_str())) {
            if (outError != nullptr) {
                *outError = "failed to parse PMD file: " + modelPath;
            }
            return false;
        }

        outResult->fileType = modelFileType;
        outResult->modelName = pmdFile.m_header.m_modelName.ToUtf8String();
        outResult->vertexCount = static_cast<int64_t>(pmdFile.m_vertices.size());
        outResult->faceCount = static_cast<int64_t>(pmdFile.m_faces.size());
        outResult->materialCount = static_cast<int64_t>(pmdFile.m_materials.size());
        outResult->boneCount = static_cast<int64_t>(pmdFile.m_bones.size());
        outResult->morphCount = static_cast<int64_t>(pmdFile.m_morphs.size());
        outResult->rigidBodyCount = static_cast<int64_t>(pmdFile.m_rigidBodies.size());
        outResult->jointCount = static_cast<int64_t>(pmdFile.m_joints.size());
        return true;
    }

    saba::PMXFile pmxFile;
    if (!saba::ReadPMXFile(&pmxFile, modelPath.c_str())) {
        if (outError != nullptr) {
            *outError = "failed to parse PMX file: " + modelPath;
        }
        return false;
    }

    outResult->fileType = modelFileType;
    outResult->modelName = pmxFile.m_info.m_modelName;
    outResult->vertexCount = static_cast<int64_t>(pmxFile.m_vertices.size());
    outResult->faceCount = static_cast<int64_t>(pmxFile.m_faces.size());
    outResult->materialCount = static_cast<int64_t>(pmxFile.m_materials.size());
    outResult->boneCount = static_cast<int64_t>(pmxFile.m_bones.size());
    outResult->morphCount = static_cast<int64_t>(pmxFile.m_morphs.size());
    outResult->rigidBodyCount = static_cast<int64_t>(pmxFile.m_rigidbodies.size());
    outResult->jointCount = static_cast<int64_t>(pmxFile.m_joints.size());
    return true;
}

bool parseMotionFile(const std::string& motionPath, MotionParseResult* outResult, std::string* outError) {
    if (outResult == nullptr) {
        if (outError != nullptr) {
            *outError = "internal error: motion output buffer is null.";
        }
        return false;
    }

    const std::string extension = getFileExtension(motionPath);
    if (extension != "vmd") {
        if (outError != nullptr) {
            *outError = "unsupported motion extension; expected .vmd.";
        }
        return false;
    }

    saba::VMDFile vmdFile;
    if (!saba::ReadVMDFile(&vmdFile, motionPath.c_str())) {
        if (outError != nullptr) {
            *outError = "failed to parse VMD file: " + motionPath;
        }
        return false;
    }

    outResult->modelName = vmdFile.m_header.m_modelName.ToUtf8String();
    outResult->motionCount = static_cast<int64_t>(vmdFile.m_motions.size());
    outResult->morphCount = static_cast<int64_t>(vmdFile.m_morphs.size());
    outResult->cameraCount = static_cast<int64_t>(vmdFile.m_cameras.size());
    outResult->lightCount = static_cast<int64_t>(vmdFile.m_lights.size());
    outResult->shadowCount = static_cast<int64_t>(vmdFile.m_shadows.size());
    outResult->ikCount = static_cast<int64_t>(vmdFile.m_iks.size());
    return true;
}

jlongArray buildLongArray(JNIEnv* env, std::initializer_list<jlong> values) {
    jlongArray result = env->NewLongArray(static_cast<jsize>(values.size()));
    if (result == nullptr) {
        return nullptr;
    }

    std::vector<jlong> buffer(values);
    env->SetLongArrayRegion(result, 0, static_cast<jsize>(buffer.size()), buffer.data());
    return result;
}

int64_t toModelFormatId(ModelFileType fileType) {
    if (fileType == ModelFileType::Pmd) {
        return kModelFormatPmd;
    }
    if (fileType == ModelFileType::Pmx) {
        return kModelFormatPmx;
    }
    return 0;
}

#endif

} // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_com_ai_assistance_mmd_MmdNative_nativeIsAvailable(JNIEnv*, jclass) {
#if defined(OPERIT_HAS_SABA) && OPERIT_HAS_SABA
    return JNI_TRUE;
#else
    return JNI_FALSE;
#endif
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_ai_assistance_mmd_MmdNative_nativeGetUnavailableReason(JNIEnv* env, jclass) {
#if defined(OPERIT_HAS_SABA) && OPERIT_HAS_SABA
    return stringToJString(env, "");
#else
    return stringToJString(env, kUnavailableReason);
#endif
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_ai_assistance_mmd_MmdNative_nativeGetLastError(JNIEnv* env, jclass) {
    return stringToJString(env, getLastErrorCopy());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_ai_assistance_mmd_MmdNative_nativeReadModelName(JNIEnv* env, jclass, jstring pathModel) {
#if defined(OPERIT_HAS_SABA) && OPERIT_HAS_SABA
    const std::string modelPath = jstringToString(env, pathModel);
    if (modelPath.empty()) {
        setLastError("model path is empty.");
        return nullptr;
    }

    ModelParseResult parsedModel;
    std::string parseError;
    if (!parseModelFile(modelPath, &parsedModel, &parseError)) {
        setLastError(parseError);
        return nullptr;
    }

    clearLastError();
    return stringToJString(env, parsedModel.modelName);
#else
    setLastError(kUnavailableReason);
    (void) pathModel;
    return nullptr;
#endif
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_com_ai_assistance_mmd_MmdNative_nativeReadModelSummary(JNIEnv* env, jclass, jstring pathModel) {
#if defined(OPERIT_HAS_SABA) && OPERIT_HAS_SABA
    const std::string modelPath = jstringToString(env, pathModel);
    if (modelPath.empty()) {
        setLastError("model path is empty.");
        return nullptr;
    }

    ModelParseResult parsedModel;
    std::string parseError;
    if (!parseModelFile(modelPath, &parsedModel, &parseError)) {
        setLastError(parseError);
        return nullptr;
    }

    clearLastError();
    return buildLongArray(
        env,
        {
            static_cast<jlong>(toModelFormatId(parsedModel.fileType)),
            static_cast<jlong>(parsedModel.vertexCount),
            static_cast<jlong>(parsedModel.faceCount),
            static_cast<jlong>(parsedModel.materialCount),
            static_cast<jlong>(parsedModel.boneCount),
            static_cast<jlong>(parsedModel.morphCount),
            static_cast<jlong>(parsedModel.rigidBodyCount),
            static_cast<jlong>(parsedModel.jointCount),
        }
    );
#else
    setLastError(kUnavailableReason);
    (void) env;
    (void) pathModel;
    return nullptr;
#endif
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_ai_assistance_mmd_MmdNative_nativeReadMotionModelName(JNIEnv* env, jclass, jstring pathMotion) {
#if defined(OPERIT_HAS_SABA) && OPERIT_HAS_SABA
    const std::string motionPath = jstringToString(env, pathMotion);
    if (motionPath.empty()) {
        setLastError("motion path is empty.");
        return nullptr;
    }

    MotionParseResult parsedMotion;
    std::string parseError;
    if (!parseMotionFile(motionPath, &parsedMotion, &parseError)) {
        setLastError(parseError);
        return nullptr;
    }

    clearLastError();
    return stringToJString(env, parsedMotion.modelName);
#else
    setLastError(kUnavailableReason);
    (void) pathMotion;
    return nullptr;
#endif
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_com_ai_assistance_mmd_MmdNative_nativeReadMotionSummary(JNIEnv* env, jclass, jstring pathMotion) {
#if defined(OPERIT_HAS_SABA) && OPERIT_HAS_SABA
    const std::string motionPath = jstringToString(env, pathMotion);
    if (motionPath.empty()) {
        setLastError("motion path is empty.");
        return nullptr;
    }

    MotionParseResult parsedMotion;
    std::string parseError;
    if (!parseMotionFile(motionPath, &parsedMotion, &parseError)) {
        setLastError(parseError);
        return nullptr;
    }

    clearLastError();
    return buildLongArray(
        env,
        {
            static_cast<jlong>(parsedMotion.motionCount),
            static_cast<jlong>(parsedMotion.morphCount),
            static_cast<jlong>(parsedMotion.cameraCount),
            static_cast<jlong>(parsedMotion.lightCount),
            static_cast<jlong>(parsedMotion.shadowCount),
            static_cast<jlong>(parsedMotion.ikCount),
        }
    );
#else
    setLastError(kUnavailableReason);
    (void) env;
    (void) pathMotion;
    return nullptr;
#endif
}
