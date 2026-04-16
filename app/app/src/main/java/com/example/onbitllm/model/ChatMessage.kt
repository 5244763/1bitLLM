package com.example.onbitllm.model

import android.net.Uri
import java.util.concurrent.atomic.AtomicLong

/**
 * チャットメッセージのデータモデル
 */
data class ChatMessage(
    val id: Long = IdGenerator.next(),
    val role: MessageRole,
    val content: String,
    val imageUri: Uri? = null,
    val isVoiceInput: Boolean = false,
    // AI応答メタデータ（roleがASSISTANTの場合のみ使用）
    val responseTimeMs: Long? = null,   // 応答時間（ミリ秒）
    val tokensPerSecond: Float? = null, // 生成速度（tok/s）
    val isStreaming: Boolean = false     // ストリーミング中フラグ
)

/**
 * 一意なメッセージIDを生成するカウンター
 */
object IdGenerator {
    private val counter = AtomicLong(0)
    fun next(): Long = counter.incrementAndGet()
}

enum class MessageRole {
    USER,
    ASSISTANT
}

/**
 * 選択可能なモデル
 */
enum class LlmModel(
    val displayName: String,
    val description: String,
    val supportsMultimodal: Boolean
) {
    BONSAI_8B(
        displayName = "Bonsai-8B",
        description = "1-bit LLM · テキスト",
        supportsMultimodal = false
    ),
    GEMMA_4_E4B(
        displayName = "Gemma 4 E4B",
        description = "マルチモーダル · テキスト・画像・音声",
        supportsMultimodal = true
    )
}
