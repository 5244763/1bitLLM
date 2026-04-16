package com.example.onbitllm.engine

/**
 * llama.cpp 推論エンジンの Kotlin インターフェース
 *
 * Sprint 5: LlamaBridge (JNI) または MockLlamaEngine にディスパッチする。
 * .so ファイルがロードできない場合は MockLlamaEngine にフォールバックする。
 */
interface LlamaEngine {

    /**
     * GGUFモデルファイルをロードする。
     *
     * @param modelPath モデルファイルの絶対パス
     * @return ロード成功なら true、失敗なら false
     */
    fun loadModel(modelPath: String): Boolean

    /**
     * ロード済みモデルをアンロードしてメモリを解放する。
     */
    fun unloadModel()

    /**
     * テキスト推論を実行する。
     *
     * @param prompt 入力プロンプト文字列
     * @param maxTokens 最大生成トークン数
     * @param callback 各トークン生成時のストリーミングコールバック
     * @return 生成された全テキスト
     */
    suspend fun generate(
        prompt: String,
        maxTokens: Int = 512,
        callback: (String) -> Unit = {}
    ): String

    /**
     * 画像付き推論を実行する（Gemma 4 E4B 向け）。
     *
     * @param prompt テキストプロンプト
     * @param imagePath 画像ファイルの絶対パス
     * @param maxTokens 最大生成トークン数
     * @param callback 各トークン生成時のストリーミングコールバック
     * @return 生成された全テキスト
     */
    suspend fun generateWithImage(
        prompt: String,
        imagePath: String,
        maxTokens: Int = 512,
        callback: (String) -> Unit = {}
    ): String

    /**
     * モデルがロード済みかどうかを返す。
     */
    fun isModelLoaded(): Boolean

    /**
     * モデルが使用しているメモリ量を返す (MB単位)。
     * モデル未ロード時は 0 を返す。
     */
    fun getModelMemoryUsageMb(): Long
}
