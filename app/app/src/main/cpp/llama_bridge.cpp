/**
 * llama_bridge.cpp
 *
 * Sprint 5: llama.cpp JNI エントリポイント
 *
 * 現状: llama.cpp の .so が存在しないため、スタブ実装として空の関数を提供する。
 * llama.cpp の共有ライブラリが用意できた段階で、以下の各関数に実装を追加する。
 *
 * 対応 native メソッド (LlamaBridge.kt の external fun と対応):
 *   nativeLoadModel         -> loadModel(modelPath)
 *   nativeUnloadModel       -> unloadModel()
 *   nativeGenerate          -> generate(prompt, maxTokens, handle)
 *   nativeGenerateWithImage -> generateWithImage(prompt, imagePath, maxTokens, handle)
 *   nativeIsModelLoaded     -> isModelLoaded()
 *   nativeGetModelMemoryUsageMb -> getModelMemoryUsageMb()
 */

#include <jni.h>
#include <android/log.h>
#include <string>

#define LOG_TAG "LlamaBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// TODO: llama.cpp ヘッダーが利用可能になったらコメントを外す
// #include "llama.h"

// グローバル状態（llama.cpp コンテキスト）
// TODO: 実装時に llama_context* ctx_g = nullptr; に変更
static bool g_model_loaded = false;

// ----------------------------------------------------------------------------
// JNI エントリポイント
// ----------------------------------------------------------------------------

extern "C" {

/**
 * モデルをロードする。
 * @param modelPath GGUF ファイルの絶対パス
 * @return true: 成功, false: 失敗
 */
JNIEXPORT jboolean JNICALL
Java_com_example_onbitllm_engine_LlamaBridge_nativeLoadModel(
        JNIEnv *env,
        jobject /* this */,
        jstring modelPath) {
    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    LOGI("nativeLoadModel: %s", path);

    // TODO: llama.cpp 統合後に実装
    // llama_model_params model_params = llama_model_default_params();
    // model_params.n_gpu_layers = 0; // CPU のみ
    // llama_model * model = llama_load_model_from_file(path, model_params);
    // if (model == nullptr) { return JNI_FALSE; }
    // ... context 作成 ...

    env->ReleaseStringUTFChars(modelPath, path);

    // スタブ: 常に false を返す（Kotlin 側でフォールバックする）
    return JNI_FALSE;
}

/**
 * モデルをアンロードする。
 */
JNIEXPORT void JNICALL
Java_com_example_onbitllm_engine_LlamaBridge_nativeUnloadModel(
        JNIEnv *env,
        jobject /* this */) {
    LOGI("nativeUnloadModel");
    // TODO: llama.cpp 統合後に実装
    // if (g_ctx != nullptr) { llama_free(g_ctx); g_ctx = nullptr; }
    // if (g_model != nullptr) { llama_free_model(g_model); g_model = nullptr; }
    g_model_loaded = false;
}

/**
 * テキスト推論を実行する。
 */
JNIEXPORT jstring JNICALL
Java_com_example_onbitllm_engine_LlamaBridge_nativeGenerate(
        JNIEnv *env,
        jobject /* this */,
        jstring prompt,
        jint maxTokens,
        jlong nativeHandle) {
    const char *promptStr = env->GetStringUTFChars(prompt, nullptr);
    LOGI("nativeGenerate: prompt=%s maxTokens=%d", promptStr, maxTokens);
    env->ReleaseStringUTFChars(prompt, promptStr);

    // TODO: llama.cpp 統合後に実装
    // llama_decode() を呼び出してテキスト生成
    // コールバックで Kotlin 側にストリーミング配信する設計に変更予定

    // スタブ: 空文字列を返す（Kotlin 側でフォールバックする）
    return env->NewStringUTF("");
}

/**
 * 画像付き推論を実行する（Gemma 4 E4B 向け）。
 */
JNIEXPORT jstring JNICALL
Java_com_example_onbitllm_engine_LlamaBridge_nativeGenerateWithImage(
        JNIEnv *env,
        jobject /* this */,
        jstring prompt,
        jstring imagePath,
        jint maxTokens,
        jlong nativeHandle) {
    const char *promptStr = env->GetStringUTFChars(prompt, nullptr);
    const char *imagePathStr = env->GetStringUTFChars(imagePath, nullptr);
    LOGI("nativeGenerateWithImage: imagePath=%s maxTokens=%d", imagePathStr, maxTokens);
    env->ReleaseStringUTFChars(prompt, promptStr);
    env->ReleaseStringUTFChars(imagePath, imagePathStr);

    // TODO: llama.cpp マルチモーダル統合後に実装
    // clip_image_load_from_file() などを使用

    // スタブ: 空文字列を返す
    return env->NewStringUTF("");
}

/**
 * モデルがロード済みかを返す。
 */
JNIEXPORT jboolean JNICALL
Java_com_example_onbitllm_engine_LlamaBridge_nativeIsModelLoaded(
        JNIEnv *env,
        jobject /* this */) {
    return g_model_loaded ? JNI_TRUE : JNI_FALSE;
}

/**
 * モデルが使用しているメモリ量を MB 単位で返す。
 */
JNIEXPORT jlong JNICALL
Java_com_example_onbitllm_engine_LlamaBridge_nativeGetModelMemoryUsageMb(
        JNIEnv *env,
        jobject /* this */) {
    // TODO: llama.cpp 統合後に実装
    // llama_model_size() などで取得
    return 0L;
}

} // extern "C"
