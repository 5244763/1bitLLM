package com.example.onbitllm.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * llama.cpp JNIブリッジ
 *
 * ネイティブライブラリ "llama_bridge" をロードし、JNI経由で推論を実行する。
 * .so がない場合、またはネイティブのモデルロードが失敗した場合は
 * MockLlamaEngine にフォールバックする。
 *
 * 重要: useNativeForInference フラグにより、ロードに成功したエンジン側で
 * 推論を行う。ネイティブロード失敗 → モックロード成功の場合、
 * 以降の推論もモック側で実行される。
 */
class LlamaBridge : LlamaEngine {

    companion object {
        private var nativeLibLoaded = false

        init {
            nativeLibLoaded = try {
                System.loadLibrary("llama_bridge")
                true
            } catch (e: UnsatisfiedLinkError) {
                false
            } catch (e: Exception) {
                false
            }
        }

        fun isNativeLibAvailable(): Boolean = nativeLibLoaded
    }

    private val mockEngine = MockLlamaEngine()

    /** ネイティブ側でモデルロードに成功したかを追跡 */
    private var useNativeForInference = false

    // JNI native メソッド宣言
    private external fun nativeLoadModel(modelPath: String): Boolean
    private external fun nativeUnloadModel()
    private external fun nativeGenerate(prompt: String, maxTokens: Int, nativeHandle: Long): String
    private external fun nativeGenerateWithImage(prompt: String, imagePath: String, maxTokens: Int, nativeHandle: Long): String
    private external fun nativeIsModelLoaded(): Boolean
    private external fun nativeGetModelMemoryUsageMb(): Long

    override fun loadModel(modelPath: String): Boolean {
        useNativeForInference = false

        if (nativeLibLoaded) {
            try {
                val result = nativeLoadModel(modelPath)
                if (result) {
                    useNativeForInference = true
                    return true
                }
            } catch (_: Exception) { }
        }
        // ネイティブが使えないかロード失敗 → モックにフォールバック
        return mockEngine.loadModel(modelPath)
    }

    override fun unloadModel() {
        if (useNativeForInference) {
            try { nativeUnloadModel() } catch (_: Exception) { }
        }
        mockEngine.unloadModel()
        useNativeForInference = false
    }

    override suspend fun generate(
        prompt: String,
        maxTokens: Int,
        callback: (String) -> Unit
    ): String {
        if (useNativeForInference) {
            return withContext(Dispatchers.IO) {
                try {
                    val result = nativeGenerate(prompt, maxTokens, 0L)
                    for (char in result) {
                        callback(char.toString())
                    }
                    result
                } catch (e: Exception) {
                    // ネイティブ推論が失敗 → モックにフォールバック
                    useNativeForInference = false
                    mockEngine.generate(prompt, maxTokens, callback)
                }
            }
        }
        return mockEngine.generate(prompt, maxTokens, callback)
    }

    override suspend fun generateWithImage(
        prompt: String,
        imagePath: String,
        maxTokens: Int,
        callback: (String) -> Unit
    ): String {
        if (useNativeForInference) {
            return withContext(Dispatchers.IO) {
                try {
                    val result = nativeGenerateWithImage(prompt, imagePath, maxTokens, 0L)
                    for (char in result) {
                        callback(char.toString())
                    }
                    result
                } catch (e: Exception) {
                    useNativeForInference = false
                    mockEngine.generateWithImage(prompt, imagePath, maxTokens, callback)
                }
            }
        }
        return mockEngine.generateWithImage(prompt, imagePath, maxTokens, callback)
    }

    override fun isModelLoaded(): Boolean {
        if (useNativeForInference) {
            return try { nativeIsModelLoaded() } catch (_: Exception) { false }
        }
        return mockEngine.isModelLoaded()
    }

    override fun getModelMemoryUsageMb(): Long {
        if (useNativeForInference) {
            return try { nativeGetModelMemoryUsageMb() } catch (_: Exception) { 0L }
        }
        return mockEngine.getModelMemoryUsageMb()
    }
}
