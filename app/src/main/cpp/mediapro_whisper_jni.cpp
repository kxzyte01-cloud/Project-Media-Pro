#include <jni.h>
#include <cstring>
#include <string>
#include <vector>
#include "whisper.h"

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_projectmediapro_feature_captions_WhisperNativeBridge_nativeInitContext(
        JNIEnv *env,
        jobject /* thiz */,
        jstring modelPathStr) {
    if (modelPathStr == nullptr) {
        return 0L;
    }
    const char *modelPath = env->GetStringUTFChars(modelPathStr, nullptr);
    if (modelPath == nullptr) {
        return 0L;
    }
    struct whisper_context_params cparams = whisper_context_default_params();
    struct whisper_context *ctx = whisper_init_from_file_with_params(modelPath, cparams);
    env->ReleaseStringUTFChars(modelPathStr, modelPath);
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT void JNICALL
Java_com_projectmediapro_feature_captions_WhisperNativeBridge_nativeFreeContext(
        JNIEnv * /* env */,
        jobject /* thiz */,
        jlong contextPtr) {
    if (contextPtr == 0L) {
        return;
    }
    auto *ctx = reinterpret_cast<struct whisper_context *>(contextPtr);
    whisper_free(ctx);
}

JNIEXPORT jint JNICALL
Java_com_projectmediapro_feature_captions_WhisperNativeBridge_nativeFullTranscribe(
        JNIEnv *env,
        jobject /* thiz */,
        jlong contextPtr,
        jint numThreads,
        jfloatArray audioData) {
    if (contextPtr == 0L || audioData == nullptr) {
        return -1;
    }
    auto *ctx = reinterpret_cast<struct whisper_context *>(contextPtr);
    const jsize length = env->GetArrayLength(audioData);
    if (length <= 0) {
        return 0;
    }
    jfloat *samples = env->GetFloatArrayElements(audioData, nullptr);
    if (samples == nullptr) {
        return -2;
    }

    struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.n_threads = numThreads > 0 ? numThreads : 4;
    params.print_progress = false;
    params.print_special = false;
    params.print_realtime = false;
    params.print_timestamps = false;
    params.translate = false;
    params.language = "auto";
    params.no_context = true;
    params.single_segment = false;
    params.token_timestamps = false;
    params.greedy.best_of = 1;

    const int result = whisper_full(ctx, params, samples, static_cast<int>(length));
    env->ReleaseFloatArrayElements(audioData, samples, JNI_ABORT);
    return result;
}

JNIEXPORT jint JNICALL
Java_com_projectmediapro_feature_captions_WhisperNativeBridge_nativeGetSegmentCount(
        JNIEnv * /* env */,
        jobject /* thiz */,
        jlong contextPtr) {
    if (contextPtr == 0L) {
        return 0;
    }
    auto *ctx = reinterpret_cast<struct whisper_context *>(contextPtr);
    return whisper_full_n_segments(ctx);
}

JNIEXPORT jbyteArray JNICALL
Java_com_projectmediapro_feature_captions_WhisperNativeBridge_nativeGetSegmentTextBytes(
        JNIEnv *env,
        jobject /* thiz */,
        jlong contextPtr,
        jint index) {
    if (contextPtr == 0L || index < 0) {
        return env->NewByteArray(0);
    }
    auto *ctx = reinterpret_cast<struct whisper_context *>(contextPtr);
    const char *text = whisper_full_get_segment_text(ctx, index);
    if (text == nullptr) {
        return env->NewByteArray(0);
    }
    const jsize len = static_cast<jsize>(std::strlen(text));
    jbyteArray out = env->NewByteArray(len);
    if (out != nullptr && len > 0) {
        env->SetByteArrayRegion(out, 0, len, reinterpret_cast<const jbyte *>(text));
    }
    return out;
}

JNIEXPORT jlong JNICALL
Java_com_projectmediapro_feature_captions_WhisperNativeBridge_nativeGetSegmentT0Ms(
        JNIEnv * /* env */,
        jobject /* thiz */,
        jlong contextPtr,
        jint index) {
    if (contextPtr == 0L || index < 0) {
        return 0L;
    }
    auto *ctx = reinterpret_cast<struct whisper_context *>(contextPtr);
    // whisper_full_get_segment_t0 returns centiseconds (10 ms units).
    return static_cast<jlong>(whisper_full_get_segment_t0(ctx, index)) * 10L;
}

JNIEXPORT jlong JNICALL
Java_com_projectmediapro_feature_captions_WhisperNativeBridge_nativeGetSegmentT1Ms(
        JNIEnv * /* env */,
        jobject /* thiz */,
        jlong contextPtr,
        jint index) {
    if (contextPtr == 0L || index < 0) {
        return 0L;
    }
    auto *ctx = reinterpret_cast<struct whisper_context *>(contextPtr);
    return static_cast<jlong>(whisper_full_get_segment_t1(ctx, index)) * 10L;
}

} // extern "C"
