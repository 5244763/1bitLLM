package com.example.onbitllm.engine

import android.content.Context
import com.example.onbitllm.model.LlmModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * LlamaEngine のライフサイクル管理クラス
 *
 * Sprint 5:
 * - 初回メッセージ送信時にモデルをロード（遅延ロード）
 * - モデル切り替え時: 旧モデルをアンロード → 新モデルをロード
 * - アプリがバックグラウンドに移行したらアンロード、フォアグラウンド復帰でリロード
 * - モデルファイルが存在しない場合は MockLlamaEngine で動作
 *
 * モデルファイルのパス:
 *   {filesDir}/models/bonsai-8b-q1_0.gguf
 *   {filesDir}/models/gemma-4-e4b-q4_k_m.gguf
 */
class EngineManager(private val context: Context) {

    private val engine: LlamaEngine = LlamaBridge()
    private var currentModel: LlmModel? = null
    private var lastLoadedModelPath: String? = null

    companion object {
        /** Bonsai-8B モデルファイル名 */
        const val BONSAI_FILE = "bonsai-8b-q1_0.gguf"

        /** Gemma 4 E4B モデルファイル名 */
        const val GEMMA_FILE = "gemma-4-e4b-q4_k_m.gguf"
    }

    /**
     * モデルファイルの保存ディレクトリ（内部ストレージ: アプリが確実にアクセス可能）
     */
    private val internalModelsDir: File
        get() {
            val dir = File(context.filesDir, "models")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    /**
     * adb push 先のディレクトリ（外部ストレージ）
     * ユーザーはここにモデルファイルを配置する
     */
    private val externalModelsDir: File
        get() {
            val dir = File(context.getExternalFilesDir(null), "models")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    /**
     * 外部ストレージにモデルファイルがあれば内部ストレージにコピーする。
     * adb push → 外部、アプリ利用 → 内部 のブリッジ。
     */
    fun syncModelsFromExternal() {
        val files = externalModelsDir.listFiles() ?: return
        for (file in files) {
            if (file.extension == "gguf") {
                val dest = File(internalModelsDir, file.name)
                if (!dest.exists() || dest.length() != file.length()) {
                    file.copyTo(dest, overwrite = true)
                }
            }
        }
    }

    /**
     * 指定モデルの GGUF ファイルパスを返す。
     * 複数の候補パスを順に探し、最初に見つかったものを返す。
     * C++ネイティブコード(fopen/mmap)はJavaと異なりファイルアクセス制限が緩いため、
     * Javaで見えないパスでもネイティブからは開ける可能性がある。
     */
    fun getModelPath(model: LlmModel): String {
        val fileName = when (model) {
            LlmModel.BONSAI_8B -> BONSAI_FILE
            LlmModel.GEMMA_4_E4B -> GEMMA_FILE
        }

        // 候補パスを優先度順に列挙
        val candidates = listOf(
            File(internalModelsDir, fileName),                                     // 内部ストレージ
            File(externalModelsDir, fileName),                                     // 外部ストレージ (getExternalFilesDir)
            File("/storage/emulated/0/Android/data/${context.packageName}/files/models", fileName), // 直接パス
            File("/sdcard/Android/data/${context.packageName}/files/models", fileName),             // symlink パス
        )

        for (candidate in candidates) {
            if (candidate.exists() && candidate.length() > 0) {
                android.util.Log.i("EngineManager", "getModelPath: found at ${candidate.absolutePath} (${candidate.length() / 1024 / 1024}MB)")
                return candidate.absolutePath
            }
        }

        // どこにも見つからない場合は内部ストレージのパスを返す（エラーになるが呼び出し側で処理）
        android.util.Log.e("EngineManager", "getModelPath: not found in any location for $fileName")
        return File(internalModelsDir, fileName).absolutePath
    }

    /**
     * adb push 先のパスを返す（UI案内用）。
     */
    fun getExternalModelPath(model: LlmModel): String {
        val fileName = when (model) {
            LlmModel.BONSAI_8B -> BONSAI_FILE
            LlmModel.GEMMA_4_E4B -> GEMMA_FILE
        }
        return File(externalModelsDir, fileName).absolutePath
    }

    /**
     * 指定モデルのファイルが端末上に存在するか確認する。
     */
    fun isModelFilePresent(model: LlmModel): Boolean {
        return File(getModelPath(model)).exists()
    }

    /**
     * 指定モデルをロードする（必要なら既存モデルを先にアンロード）。
     *
     * @param model ロード対象のモデル
     * @return ロード成功なら true（ファイルが存在しない場合でも MockLlamaEngine で true）
     */
    suspend fun loadModelIfNeeded(model: LlmModel): Boolean = withContext(Dispatchers.IO) {
        // 同じモデルが既にロード済みならスキップ
        if (currentModel == model && engine.isModelLoaded()) {
            return@withContext true
        }

        // 旧モデルをアンロード
        if (engine.isModelLoaded()) {
            engine.unloadModel()
        }

        val path = getModelPath(model)
        currentModel = model
        lastLoadedModelPath = path

        // ファイルが存在しない場合: MockLlamaEngine（LlamaBridge経由）がパスを記録して動作する
        engine.loadModel(path)
    }

    /**
     * モデルを強制的にアンロード→再ロードする。
     * ファイルコピー後の初回ロードに使用。loadModelIfNeeded のキャッシュをバイパスする。
     */
    suspend fun forceLoadModel(model: LlmModel): Boolean = withContext(Dispatchers.IO) {
        // 既存モデルを確実にアンロード
        engine.unloadModel()

        val path = getModelPath(model)
        currentModel = model
        lastLoadedModelPath = path

        // ファイル存在確認
        val file = java.io.File(path)
        if (!file.exists()) {
            android.util.Log.e("EngineManager", "forceLoadModel: file not found: $path")
            return@withContext false
        }
        android.util.Log.i("EngineManager", "forceLoadModel: loading ${file.length() / 1024 / 1024}MB from $path")

        val startTime = System.currentTimeMillis()
        val result = engine.loadModel(path)
        val elapsed = System.currentTimeMillis() - startTime
        android.util.Log.i("EngineManager", "forceLoadModel: result=$result, elapsed=${elapsed}ms, native=${isNativeInference()}")

        result
    }

    /**
     * 現在ロードされているモデルをアンロードする。
     * バックグラウンド移行時に呼び出す。
     */
    suspend fun unloadCurrentModel() = withContext(Dispatchers.IO) {
        if (engine.isModelLoaded()) {
            engine.unloadModel()
        }
    }

    /**
     * 前回ロードしていたモデルを再ロードする。
     * フォアグラウンド復帰時に呼び出す。
     */
    suspend fun reloadLastModel() = withContext(Dispatchers.IO) {
        val model = currentModel ?: return@withContext
        if (!engine.isModelLoaded()) {
            val path = getModelPath(model)
            engine.loadModel(path)
        }
    }

    /**
     * テキスト推論を実行する。
     * モデルが未ロードの場合は loadModelIfNeeded を先に呼ぶこと。
     */
    suspend fun generate(
        prompt: String,
        maxTokens: Int = 512,
        callback: (String) -> Unit = {}
    ): String {
        return engine.generate(prompt, maxTokens, callback)
    }

    /**
     * 画像付き推論を実行する（Gemma 4 E4B 向け）。
     */
    suspend fun generateWithImage(
        prompt: String,
        imagePath: String,
        maxTokens: Int = 512,
        callback: (String) -> Unit = {}
    ): String {
        return engine.generateWithImage(prompt, imagePath, maxTokens, callback)
    }

    /**
     * 推論エンジンへの参照を返す（ResourceViewModel からメモリ量取得に使用）。
     */
    fun getEngine(): LlamaEngine = engine

    /**
     * 現在選択されているモデルを返す。
     */
    fun getCurrentModel(): LlmModel? = currentModel

    /**
     * モデルがロード済みかを返す。
     */
    fun isLoaded(): Boolean = engine.isModelLoaded()

    /** ネイティブ推論が有効か（モックフォールバックではないか） */
    fun isNativeInference(): Boolean {
        return (engine as? LlamaBridge)?.isUsingNativeInference() ?: false
    }
}
