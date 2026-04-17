package com.example.onbitllm.model

import java.util.UUID

/**
 * チャットセッションのデータモデル
 * JSON ファイルで内部ストレージに保存する
 */
data class ChatSession(
    val id: String = UUID.randomUUID().toString(),
    /** 最初のユーザーメッセージから自動生成するタイトル */
    val title: String,
    /** 使用モデル名 */
    val modelName: String,
    /** メッセージリスト（imageUri は String に変換して保存） */
    val messages: List<ChatMessageJson>,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * JSON シリアライズ用の ChatMessage ラッパー
 * Uri 型を String に変換して保持する
 */
data class ChatMessageJson(
    val id: Long,
    val role: String,           // MessageRole.name
    val content: String,
    val imageUriString: String? = null,   // Uri.toString() or null
    val isVoiceInput: Boolean = false,
    val responseTimeMs: Long? = null,
    val tokensPerSecond: Float? = null,
    val isStreaming: Boolean = false
) {
    /** ChatMessage に変換する */
    fun toChatMessage(): ChatMessage {
        val uri = imageUriString?.let { android.net.Uri.parse(it) }
        return ChatMessage(
            id = id,
            role = MessageRole.valueOf(role),
            content = content,
            imageUri = uri,
            isVoiceInput = isVoiceInput,
            responseTimeMs = responseTimeMs,
            tokensPerSecond = tokensPerSecond,
            isStreaming = false // 保存時はストリーミング完了済み
        )
    }

    companion object {
        /** ChatMessage から変換する */
        fun fromChatMessage(msg: ChatMessage): ChatMessageJson {
            return ChatMessageJson(
                id = msg.id,
                role = msg.role.name,
                content = msg.content,
                imageUriString = msg.imageUri?.toString(),
                isVoiceInput = msg.isVoiceInput,
                responseTimeMs = msg.responseTimeMs,
                tokensPerSecond = msg.tokensPerSecond,
                isStreaming = false
            )
        }
    }
}
