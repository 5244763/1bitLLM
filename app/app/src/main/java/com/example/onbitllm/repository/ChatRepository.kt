package com.example.onbitllm.repository

import android.content.Context
import com.example.onbitllm.model.ChatMessage
import com.example.onbitllm.model.ChatMessageJson
import com.example.onbitllm.model.ChatSession
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * チャットセッションの永続化リポジトリ
 *
 * 保存先: {filesDir}/chat_sessions/
 * 各セッションは {id}.json ファイルとして保存する
 */
class ChatRepository(context: Context) {

    private val sessionsDir: File = File(context.filesDir, "chat_sessions").also { it.mkdirs() }
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    /** 現在アクティブなセッションIDを保持するファイル */
    private val currentSessionFile: File = File(context.filesDir, "current_session_id.txt")

    // ---------- セッション保存・読み込み ----------

    /**
     * セッションを保存する（非同期）
     */
    suspend fun saveSession(session: ChatSession) = withContext(Dispatchers.IO) {
        val file = File(sessionsDir, "${session.id}.json")
        file.writeText(gson.toJson(session))
    }

    /**
     * セッションをIDで読み込む
     */
    suspend fun loadSession(id: String): ChatSession? = withContext(Dispatchers.IO) {
        val file = File(sessionsDir, "$id.json")
        if (!file.exists()) return@withContext null
        try {
            gson.fromJson(file.readText(), ChatSession::class.java)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 全セッションを updatedAt 降順で取得
     */
    suspend fun loadAllSessions(): List<ChatSession> = withContext(Dispatchers.IO) {
        val files = sessionsDir.listFiles { f -> f.extension == "json" } ?: return@withContext emptyList()
        files.mapNotNull { file ->
            try {
                gson.fromJson(file.readText(), ChatSession::class.java)
            } catch (e: Exception) {
                null
            }
        }.sortedByDescending { it.updatedAt }
    }

    /**
     * セッションを削除する
     */
    suspend fun deleteSession(id: String) = withContext(Dispatchers.IO) {
        File(sessionsDir, "$id.json").delete()
    }

    // ---------- 現在のセッションID ----------

    /**
     * 現在のセッションIDを保存
     */
    suspend fun saveCurrentSessionId(id: String) = withContext(Dispatchers.IO) {
        currentSessionFile.writeText(id)
    }

    /**
     * 現在のセッションIDを読み込む（なければ null）
     */
    suspend fun loadCurrentSessionId(): String? = withContext(Dispatchers.IO) {
        if (!currentSessionFile.exists()) return@withContext null
        val id = currentSessionFile.readText().trim()
        // ファイルが実際に存在するか確認
        if (File(sessionsDir, "$id.json").exists()) id else null
    }

    // ---------- ヘルパー ----------

    /**
     * ChatMessage リストを ChatMessageJson リストに変換
     */
    fun toJsonMessages(messages: List<ChatMessage>): List<ChatMessageJson> {
        return messages.map { ChatMessageJson.fromChatMessage(it) }
    }

    /**
     * ChatMessageJson リストを ChatMessage リストに変換
     */
    fun fromJsonMessages(messages: List<ChatMessageJson>): List<ChatMessage> {
        return messages.map { it.toChatMessage() }
    }

    /**
     * セッションタイトルを最初のユーザーメッセージから生成する
     */
    fun generateTitle(messages: List<ChatMessage>): String {
        val firstUserMsg = messages.firstOrNull { it.role == com.example.onbitllm.model.MessageRole.USER }
        return if (firstUserMsg != null) {
            val raw = firstUserMsg.content.trim()
            if (raw.length > 30) raw.take(30) + "..." else raw.ifEmpty { "新しい会話" }
        } else {
            "新しい会話"
        }
    }
}
