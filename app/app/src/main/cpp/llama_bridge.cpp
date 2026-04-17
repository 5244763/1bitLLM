/**
 * llama_bridge.cpp
 *
 * Sprint 6: llama.cpp JNI 実装
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
#include <vector>
#include <cstring>

#define LOG_TAG "LlamaBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

#include "llama.h"
#include "sampling.h"

// ----------------------------------------------------------------------------
// グローバル状態
// ----------------------------------------------------------------------------
static llama_model   * g_model   = nullptr;
static llama_context * g_ctx     = nullptr;

// CPU スレッド数（端末の物理コア数の半分程度を使用）
static constexpr int N_THREADS = 4;

// デフォルトのコンテキストサイズ（トークン数）
static constexpr int N_CTX = 2048;

// ----------------------------------------------------------------------------
// ヘルパー: llama.cpp のログを Android logcat に転送
// ----------------------------------------------------------------------------
static void llama_log_callback_android(ggml_log_level level, const char *text, void * /*user_data*/) {
    switch (level) {
        case GGML_LOG_LEVEL_INFO:
            LOGI("[llama] %s", text);
            break;
        case GGML_LOG_LEVEL_WARN:
            LOGI("[llama WARN] %s", text);
            break;
        case GGML_LOG_LEVEL_ERROR:
            LOGE("[llama ERR] %s", text);
            break;
        default:
            LOGD("[llama] %s", text);
            break;
    }
}

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

    // 既にロード済みならアンロードしてから再ロード
    if (g_ctx != nullptr) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model != nullptr) {
        llama_model_free(g_model);
        g_model = nullptr;
    }

    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    LOGI("nativeLoadModel: %s", path);

    // llama.cpp ログコールバックを設定
    llama_log_set(llama_log_callback_android, nullptr);

    // バックエンド初期化
    llama_backend_init();

    // モデルパラメータ設定
    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0;  // CPU のみ

    // モデルロード
    g_model = llama_model_load_from_file(path, model_params);
    env->ReleaseStringUTFChars(modelPath, path);

    if (g_model == nullptr) {
        LOGE("nativeLoadModel: failed to load model");
        return JNI_FALSE;
    }

    // コンテキスト作成
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx     = N_CTX;
    ctx_params.n_threads = N_THREADS;
    ctx_params.n_threads_batch = N_THREADS;

    g_ctx = llama_init_from_model(g_model, ctx_params);
    if (g_ctx == nullptr) {
        LOGE("nativeLoadModel: failed to create context");
        llama_model_free(g_model);
        g_model = nullptr;
        return JNI_FALSE;
    }

    LOGI("nativeLoadModel: success");
    return JNI_TRUE;
}

/**
 * モデルをアンロードする。
 */
JNIEXPORT void JNICALL
Java_com_example_onbitllm_engine_LlamaBridge_nativeUnloadModel(
        JNIEnv * /*env*/,
        jobject /* this */) {
    LOGI("nativeUnloadModel");
    if (g_ctx != nullptr) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model != nullptr) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
}

/**
 * テキスト推論を実行する。
 * nativeHandle は将来のストリーミングコールバック用（現在未使用）。
 */
JNIEXPORT jstring JNICALL
Java_com_example_onbitllm_engine_LlamaBridge_nativeGenerate(
        JNIEnv *env,
        jobject /* this */,
        jstring prompt,
        jint maxTokens,
        jlong /* nativeHandle */) {

    if (g_model == nullptr || g_ctx == nullptr) {
        LOGE("nativeGenerate: model not loaded");
        return env->NewStringUTF("");
    }

    const char *promptStr = env->GetStringUTFChars(prompt, nullptr);
    LOGI("nativeGenerate: maxTokens=%d prompt_len=%zu", maxTokens, strlen(promptStr));

    // ----- トークナイズ -----
    const struct llama_vocab *vocab = llama_model_get_vocab(g_model);
    const int n_prompt_tokens_max = static_cast<int>(strlen(promptStr)) + 10;
    std::vector<llama_token> tokens(n_prompt_tokens_max);
    int n_prompt = llama_tokenize(
        vocab,
        promptStr,
        static_cast<int32_t>(strlen(promptStr)),
        tokens.data(),
        n_prompt_tokens_max,
        /*add_special=*/true,
        /*parse_special=*/true
    );
    env->ReleaseStringUTFChars(prompt, promptStr);

    if (n_prompt < 0) {
        // バッファが足りなければリサイズして再試行
        tokens.resize(-n_prompt);
        n_prompt = llama_tokenize(
            vocab,
            promptStr,
            -1,  // 0-terminated
            tokens.data(),
            static_cast<int32_t>(tokens.size()),
            /*add_special=*/true,
            /*parse_special=*/true
        );
    }
    if (n_prompt <= 0) {
        LOGE("nativeGenerate: tokenize failed");
        return env->NewStringUTF("");
    }
    tokens.resize(n_prompt);
    LOGI("nativeGenerate: n_prompt=%d", n_prompt);

    // ----- サンプラー初期化 -----
    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    llama_sampler *smpl = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(smpl, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_p(0.9f, 1));
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(0.7f));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    // ----- プロンプトの decode -----
    // KV キャッシュをクリア
    llama_memory_clear(llama_get_memory(g_ctx), false);

    // バッチで一括デコード
    {
        llama_batch batch = llama_batch_get_one(tokens.data(), n_prompt);
        if (llama_decode(g_ctx, batch) != 0) {
            LOGE("nativeGenerate: prompt decode failed");
            llama_sampler_free(smpl);
            return env->NewStringUTF("");
        }
    }

    // ----- トークン生成ループ -----
    const llama_token eos_token = llama_vocab_eos(vocab);
    const llama_token eot_token = llama_vocab_eot(vocab);

    std::string result;
    result.reserve(static_cast<size_t>(maxTokens) * 4);

    char piece_buf[256];
    llama_pos n_pos = static_cast<llama_pos>(n_prompt);

    for (int i = 0; i < maxTokens; ++i) {
        // 現在のロジットからトークンをサンプリング
        llama_token new_token = llama_sampler_sample(smpl, g_ctx, -1);

        // EOS / EOT で生成終了
        if (new_token == eos_token || new_token == eot_token) {
            LOGI("nativeGenerate: EOS/EOT at step %d", i);
            break;
        }

        // トークンをテキストに変換
        int piece_len = llama_token_to_piece(
            vocab,
            new_token,
            piece_buf,
            static_cast<int32_t>(sizeof(piece_buf)),
            0,      // lstrip
            true    // special
        );
        if (piece_len > 0) {
            result.append(piece_buf, static_cast<size_t>(piece_len));
        }

        // 次のデコードのためにバッチ投入
        llama_batch batch = llama_batch_get_one(&new_token, 1);
        if (llama_decode(g_ctx, batch) != 0) {
            LOGE("nativeGenerate: decode failed at step %d", i);
            break;
        }
        n_pos++;
    }

    llama_sampler_free(smpl);

    LOGI("nativeGenerate: result_len=%zu", result.size());
    return env->NewStringUTF(result.c_str());
}

/**
 * 画像付き推論を実行する（現在はテキストのみフォールバック）。
 */
JNIEXPORT jstring JNICALL
Java_com_example_onbitllm_engine_LlamaBridge_nativeGenerateWithImage(
        JNIEnv *env,
        jobject thiz,
        jstring prompt,
        jstring /* imagePath */,
        jint maxTokens,
        jlong nativeHandle) {
    LOGI("nativeGenerateWithImage: falling back to text-only generation");
    // 現時点では画像なしのテキスト生成にフォールバック
    return Java_com_example_onbitllm_engine_LlamaBridge_nativeGenerate(
        env, thiz, prompt, maxTokens, nativeHandle
    );
}

/**
 * モデルがロード済みかを返す。
 */
JNIEXPORT jboolean JNICALL
Java_com_example_onbitllm_engine_LlamaBridge_nativeIsModelLoaded(
        JNIEnv * /*env*/,
        jobject /* this */) {
    return (g_model != nullptr && g_ctx != nullptr) ? JNI_TRUE : JNI_FALSE;
}

/**
 * モデルが使用しているメモリ量を MB 単位で返す。
 */
JNIEXPORT jlong JNICALL
Java_com_example_onbitllm_engine_LlamaBridge_nativeGetModelMemoryUsageMb(
        JNIEnv * /*env*/,
        jobject /* this */) {
    if (g_model == nullptr) {
        return 0L;
    }
    const uint64_t bytes = llama_model_size(g_model);
    return static_cast<jlong>(bytes / (1024ULL * 1024ULL));
}

} // extern "C"
