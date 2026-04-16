package com.example.onbitllm.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * llama.cpp JNIブリッジ
 *
 * Sprint 5: ネイティブライブラリ "llama_bridge" をロードし、
 * JNI経由で llama.cpp 推論エンジンを呼び出す。
 * .so ファイルが存在しない場合は UnsatisfiedLinkError をキャッチし、
 * MockLlamaEngine にフォールバックする。
 */
class LlamaBridge : LlamaEngine {

    companion object {
        private var nativeLibLoaded = false

        init {
            nativeLibLoaded = try {
                System.loadLibrary("llama_bridge")
                true
            } catch (e: UnsatisfiedLinkError) {
                // .so ファイルが存在しない場合: モック動作にフォールバック
                false
            } catch (e: Exception) {
                false
            }
        }

        /** ネイティブライブラリがロードされているか */
        fun isNativeLibAvailable(): Boolean = nativeLibLoaded
    }

    // ネイティブライブラリが利用できない場合のフォールバック
    private val mockEngine = MockLlamaEngine()

    // -------------------------------------------------------------------------
    // JNI native メソッド宣言
    // (NDKビルドが有効でllama_bridge.soが存在する場合に呼び出される)
    // -------------------------------------------------------------------------

    private external fun nativeLoadModel(modelPath: String): Boolean
    private external fun nativeUnloadModel()
    private external fun nativeGenerate(
        prompt: String,
        maxTokens: Int,
        nativeHandle: Long
    ): String
    private external fun nativeGenerateWithImage(
        prompt: String,
        imagePath: String,
        maxTokens: Int,
        nativeHandle: Long
    ): String
    private external fun nativeIsModelLoaded(): Boolean
    private external fun nativeGetModelMemoryUsageMb(): Long

    // -------------------------------------------------------------------------
    // LlamaEngine 実装
    // -------------------------------------------------------------------------

    override fun loadModel(modelPath: String): Boolean {
        return if (nativeLibLoaded) {
            try {
                val result = nativeLoadModel(modelPath)
                if (!result) mockEngine.loadModel(modelPath) else true
            } catch (e: Exception) {
                // native 呼び出しで例外が発生した場合はモックにフォールバック
                mockEngine.loadModel(modelPath)
            }
        } else {
            mockEngine.loadModel(modelPath)
        }
    }

    override fun unloadModel() {
        if (nativeLibLoaded) {
            try {
                nativeUnloadModel()
            } catch (e: Exception) {
                mockEngine.unloadModel()
            }
        } else {
            mockEngine.unloadModel()
        }
    }

    override suspend fun generate(
        prompt: String,
        maxTokens: Int,
        callback: (String) -> Unit
    ): String {
        return if (nativeLibLoaded) {
            withContext(Dispatchers.IO) {
                try {
                    // native 実装ではコールバックを JNI 経由で渡す設計
                    // 現時点では nativeGenerate を呼び出してから全テキストをそのまま返す
                    val result = nativeGenerate(prompt, maxTokens, 0L)
                    // ストリーミング: 結果テキストをトークンとしてコールバック
                    for (char in result) {
                        callback(char.toString())
                    }
                    result
                } catch (e: Exception) {
                    mockEngine.generate(prompt, maxTokens, callback)
                }
            }
        } else {
            mockEngine.generate(prompt, maxTokens, callback)
        }
    }

    override suspend fun generateWithImage(
        prompt: String,
        imagePath: String,
        maxTokens: Int,
        callback: (String) -> Unit
    ): String {
        return if (nativeLibLoaded) {
            withContext(Dispatchers.IO) {
                try {
                    val result = nativeGenerateWithImage(prompt, imagePath, maxTokens, 0L)
                    for (char in result) {
                        callback(char.toString())
                    }
                    result
                } catch (e: Exception) {
                    mockEngine.generateWithImage(prompt, imagePath, maxTokens, callback)
                }
            }
        } else {
            mockEngine.generateWithImage(prompt, imagePath, maxTokens, callback)
        }
    }

    override fun isModelLoaded(): Boolean {
        return if (nativeLibLoaded) {
            try {
                nativeIsModelLoaded()
            } catch (e: Exception) {
                mockEngine.isModelLoaded()
            }
        } else {
            mockEngine.isModelLoaded()
        }
    }

    override fun getModelMemoryUsageMb(): Long {
        return if (nativeLibLoaded) {
            try {
                nativeGetModelMemoryUsageMb()
            } catch (e: Exception) {
                mockEngine.getModelMemoryUsageMb()
            }
        } else {
            mockEngine.getModelMemoryUsageMb()
        }
    }
}
