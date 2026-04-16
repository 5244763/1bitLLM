package com.example.onbitllm.engine

import kotlinx.coroutines.delay

/**
 * llama.cpp の .so ファイルが存在しない場合に使用するモック実装。
 *
 * Sprint 5: ChatViewModel から移植したモック応答ロジック + ストリーミング風遅延出力。
 */
class MockLlamaEngine : LlamaEngine {

    private var modelLoaded = false
    private var currentModelPath: String = ""

    // モック: モデルファイルが "存在" する体で動作する
    override fun loadModel(modelPath: String): Boolean {
        currentModelPath = modelPath
        modelLoaded = true
        return true
    }

    override fun unloadModel() {
        modelLoaded = false
        currentModelPath = ""
    }

    override suspend fun generate(
        prompt: String,
        maxTokens: Int,
        callback: (String) -> Unit
    ): String {
        // モック: 応答開始まで待機
        delay(300L)

        val response = buildMockResponse(prompt)
        streamTokens(response, callback)
        return response
    }

    override suspend fun generateWithImage(
        prompt: String,
        imagePath: String,
        maxTokens: Int,
        callback: (String) -> Unit
    ): String {
        delay(300L)

        val response = buildMockResponse(prompt)
        streamTokens(response, callback)
        return response
    }

    override fun isModelLoaded(): Boolean = modelLoaded

    /**
     * モック: モデルパスからモデル種別を推定してメモリ使用量を返す。
     * 実際の .so が存在しない場合のフォールバック値。
     */
    override fun getModelMemoryUsageMb(): Long {
        if (!modelLoaded) return 0L
        return when {
            currentModelPath.contains("bonsai", ignoreCase = true) -> 1178L  // ~1.15 GB
            currentModelPath.contains("gemma", ignoreCase = true) -> 2560L   // ~2.5 GB
            else -> 0L
        }
    }

    // -------------------------------------------------------------------------
    // 内部ユーティリティ
    // -------------------------------------------------------------------------

    /**
     * テキストをトークン単位でストリーミング風に出力する。
     * 日本語文字は 30ms、ASCII文字は 20ms の遅延で出力。
     */
    private suspend fun streamTokens(text: String, callback: (String) -> Unit) {
        for (char in text) {
            callback(char.toString())
            delay(if (char.code > 127) 30L else 20L)
        }
    }

    /**
     * 入力テキストに応じたモック応答を返す。
     * Sprint 1〜4 の ChatViewModel.getMockResponse から移植。
     */
    private fun buildMockResponse(input: String): String {
        // モデルパスからプレフィックスを決定
        val modelPrefix = when {
            currentModelPath.contains("bonsai", ignoreCase = true) -> "[Bonsai-8B] "
            currentModelPath.contains("gemma", ignoreCase = true) -> "[Gemma 4 E4B] "
            else -> "[Mock] "
        }

        return when {
            input.contains("俳句") || input.contains("haiku", ignoreCase = true) ->
                "${modelPrefix}桜散りて\n風に舞いし春の夢\nまた来年も"

            input.contains("こんにちは") || input.contains("hello", ignoreCase = true) ->
                "${modelPrefix}こんにちは！何かお役に立てることはありますか？"

            input.contains("1-bit") || input.contains("Bonsai") ->
                "${modelPrefix}Bonsai-8Bは1-bit量子化されたLLMです。通常のLLMと比較して大幅にメモリ使用量を削減しながら、優れた推論能力を維持しています。重みは-1、0、1の3値のみで表現されており、約1.15GBのメモリで動作します。"

            input.contains("Gemma") || input.contains("マルチモーダル") ->
                "${modelPrefix}Gemma 4 E4Bはマルチモーダル対応のオンデバイスLLMです。テキスト・画像・音声の入力に対応しており、約2.5GBのメモリで動作します。"

            input.contains("[音声入力]") || input.contains("[音声メッセージ") ->
                "${modelPrefix}音声入力を受け取りました。Gemma 4 E4Bの内蔵音声エンコーダで処理しています。llama.cppの実際の音声推論が統合されると、音声の内容を直接認識して回答します。"

            input.contains("[画像") ->
                "${modelPrefix}画像を受け取りました。Gemma 4 E4Bのビジョン機能で画像を解析しています。llama.cppのマルチモーダル推論が統合されると、画像の内容を詳しく説明できます。"

            input.length < 10 ->
                "${modelPrefix}ご質問ありがとうございます。もう少し詳しく教えていただけますか？"

            else ->
                "${modelPrefix}ご質問の「${input.take(20)}...」についてお答えします。\n\nこれはデモアプリのモック応答です。llama.cppの実際の推論エンジンが統合されると、実際のモデルが応答を生成します。\n\n現在はストリーミング表示のデモとして機能しています。"
        }
    }
}
