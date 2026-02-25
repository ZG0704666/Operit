#include <jni.h>

#include <algorithm>
#include <cstdint>
#include <cstring>
#include <memory>
#include <mutex>
#include <vector>

#include "api/echo_canceller3_config.h"
#include "api/echo_control.h"
#include "api/echo_canceller3_factory.h"
#include "audio_processing/audio_buffer.h"
#include "audio_processing/audio_frame.h"
#include "audio_processing/high_pass_filter.h"

namespace {

class Aec3Engine final {
public:
    Aec3Engine(int sample_rate_hz, int channels)
        : sample_rate_hz_(sample_rate_hz),
          channels_(channels),
          frame_samples_(std::max(1, sample_rate_hz / 100)),
          frame_buffer_(static_cast<size_t>(frame_samples_), 0) {
        webrtc::EchoCanceller3Config config;
        config.filter.export_linear_aec_output = false;

        webrtc::EchoCanceller3Factory factory(config);
        echo_control_ = factory.Create(sample_rate_hz_, channels_, channels_);
        high_pass_filter_ =
            std::make_unique<webrtc::HighPassFilter>(sample_rate_hz_, channels_);

        webrtc::StreamConfig stream_config(sample_rate_hz_, channels_, true);
        render_audio_ = std::make_unique<webrtc::AudioBuffer>(
            stream_config.sample_rate_hz(), stream_config.num_channels(),
            stream_config.sample_rate_hz(), stream_config.num_channels(),
            stream_config.sample_rate_hz(), stream_config.num_channels());
        capture_audio_ = std::make_unique<webrtc::AudioBuffer>(
            stream_config.sample_rate_hz(), stream_config.num_channels(),
            stream_config.sample_rate_hz(), stream_config.num_channels(),
            stream_config.sample_rate_hz(), stream_config.num_channels());
    }

    void AnalyzeRender(const int16_t* render_data, int samples) {
        if (!render_data || samples <= 0) return;

        std::lock_guard<std::mutex> guard(lock_);
        if (!echo_control_) return;

        int offset = 0;
        while (offset < samples) {
            const int count = std::min(frame_samples_, samples - offset);
            std::fill(frame_buffer_.begin(), frame_buffer_.end(), 0);
            std::memcpy(frame_buffer_.data(), render_data + offset,
                        static_cast<size_t>(count) * sizeof(int16_t));

            render_frame_.UpdateFrame(0, frame_buffer_.data(), frame_samples_,
                                      sample_rate_hz_,
                                      webrtc::AudioFrame::kNormalSpeech,
                                      webrtc::AudioFrame::kVadActive, channels_);

            render_audio_->CopyFrom(&render_frame_);
            high_pass_filter_->Process(render_audio_.get(), false);
            echo_control_->AnalyzeRender(render_audio_.get());

            offset += count;
        }
    }

    void ProcessCapture(int16_t* capture_data, int samples) {
        if (!capture_data || samples <= 0) return;

        std::lock_guard<std::mutex> guard(lock_);
        if (!echo_control_) return;

        int offset = 0;
        while (offset < samples) {
            const int count = std::min(frame_samples_, samples - offset);
            std::fill(frame_buffer_.begin(), frame_buffer_.end(), 0);
            std::memcpy(frame_buffer_.data(), capture_data + offset,
                        static_cast<size_t>(count) * sizeof(int16_t));

            capture_frame_.UpdateFrame(0, frame_buffer_.data(), frame_samples_,
                                       sample_rate_hz_,
                                       webrtc::AudioFrame::kNormalSpeech,
                                       webrtc::AudioFrame::kVadActive,
                                       channels_);

            capture_audio_->CopyFrom(&capture_frame_);
            high_pass_filter_->Process(capture_audio_.get(), false);
            echo_control_->AnalyzeCapture(capture_audio_.get());
            echo_control_->ProcessCapture(capture_audio_.get(), true);
            capture_audio_->CopyTo(&capture_frame_);

            std::memcpy(capture_data + offset, capture_frame_.data(),
                        static_cast<size_t>(count) * sizeof(int16_t));

            offset += count;
        }
    }

private:
    const int sample_rate_hz_;
    const int channels_;
    const int frame_samples_;

    std::mutex lock_;

    std::unique_ptr<webrtc::EchoControl> echo_control_;
    std::unique_ptr<webrtc::HighPassFilter> high_pass_filter_;
    std::unique_ptr<webrtc::AudioBuffer> render_audio_;
    std::unique_ptr<webrtc::AudioBuffer> capture_audio_;

    webrtc::AudioFrame render_frame_;
    webrtc::AudioFrame capture_frame_;
    std::vector<int16_t> frame_buffer_;
};

Aec3Engine* FromHandle(jlong handle) {
    return reinterpret_cast<Aec3Engine*>(handle);
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_ai_assistance_operit_core_audio_WebRtcAec3Processor_nativeCreate(
    JNIEnv* env,
    jobject /* thiz */,
    jint sample_rate,
    jint channels) {
    if (sample_rate <= 0 || channels <= 0) return 0;

    try {
        auto* engine = new Aec3Engine(sample_rate, channels);
        return reinterpret_cast<jlong>(engine);
    } catch (...) {
        return 0;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_ai_assistance_operit_core_audio_WebRtcAec3Processor_nativeRelease(
    JNIEnv* env,
    jobject /* thiz */,
    jlong handle) {
    auto* engine = FromHandle(handle);
    delete engine;
}

extern "C" JNIEXPORT void JNICALL
Java_com_ai_assistance_operit_core_audio_WebRtcAec3Processor_nativeAnalyzeRender(
    JNIEnv* env,
    jobject /* thiz */,
    jlong handle,
    jshortArray render_pcm,
    jint length) {
    auto* engine = FromHandle(handle);
    if (!engine || !render_pcm || length <= 0) return;

    const jsize array_len = env->GetArrayLength(render_pcm);
    const jint safe_len = std::min<jint>(length, array_len);
    if (safe_len <= 0) return;

    jboolean is_copy = JNI_FALSE;
    jshort* data = env->GetShortArrayElements(render_pcm, &is_copy);
    if (!data) return;

    engine->AnalyzeRender(reinterpret_cast<int16_t*>(data), safe_len);
    env->ReleaseShortArrayElements(render_pcm, data, JNI_ABORT);
}

extern "C" JNIEXPORT void JNICALL
Java_com_ai_assistance_operit_core_audio_WebRtcAec3Processor_nativeProcessCapture(
    JNIEnv* env,
    jobject /* thiz */,
    jlong handle,
    jshortArray capture_pcm,
    jint length) {
    auto* engine = FromHandle(handle);
    if (!engine || !capture_pcm || length <= 0) return;

    const jsize array_len = env->GetArrayLength(capture_pcm);
    const jint safe_len = std::min<jint>(length, array_len);
    if (safe_len <= 0) return;

    jboolean is_copy = JNI_FALSE;
    jshort* data = env->GetShortArrayElements(capture_pcm, &is_copy);
    if (!data) return;

    engine->ProcessCapture(reinterpret_cast<int16_t*>(data), safe_len);
    env->ReleaseShortArrayElements(capture_pcm, data, 0);
}
