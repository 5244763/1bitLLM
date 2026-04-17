package com.example.onbitllm.viewmodel

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.onbitllm.engine.EngineManager
import com.example.onbitllm.model.ChatMessage
import com.example.onbitllm.model.IdGenerator
import com.example.onbitllm.model.LlmModel
import com.example.onbitllm.model.MessageRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/**
 * チャット画面のUIステート
 */
data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val selectedModel: LlmModel = LlmModel.BONSAI_8B,
    val isGenerating: Boolean = false,
    val inputText: String = "",
    // Sprint 2/3: マルチモーダル入力状態
    val attachedImageUri: Uri? = null,
    val isRecording: Boolean = false,
    val recordingElapsedSeconds: Int = 0,
    // Sprint 3: 録音の音声レベル（0.0〜1.0）
    val audioLevel: Float = 0f,
    // Sprint 5: モデルファイルが存在しないことを示すフラグ
    val modelFilesMissing: Boolean = false,
    val missingModelPath: String = ""
)

/**
 * チャット画面のViewModel
 *
 * Sprint 5:
 * - EngineManager 経由で LlamaEngine を呼び出し
 * - generateMockResponse() を generateResponse() にリネーム
 * - 初回メッセージ送信時にモデルをロード（遅延ロード）
 * - モデル切り替え時に旧モデルをアンロード → 新モデルを次回使用時にロード
 */
class ChatViewModel(private val engineManager: EngineManager) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    /** ResourceMonitorOverlay がエンジンのメモリ使用量を取得するためのアクセサ */
    fun getEngine(): com.example.onbitllm.engine.LlamaEngine? = engineManager.getEngine()

    // Sprint 3: AudioRecord インスタンス
    private var audioRecord: AudioRecord? = null
    private var recordingFile: File? = null

    /**
     * テキスト入力の更新
     */
    fun onInputTextChange(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    /**
     * メッセージを送信して推論応答を生成
     */
    fun sendMessage() {
        val currentState = _uiState.value
        val inputText = currentState.inputText.trim()
        if (inputText.isEmpty() || currentState.isGenerating) return

        val userMessage = ChatMessage(
            role = MessageRole.USER,
            content = inputText
        )

        _uiState.update {
            it.copy(
                messages = it.messages + userMessage,
                inputText = "",
                isGenerating = true
            )
        }

        viewModelScope.launch {
            generateResponse(inputText, currentState.selectedModel)
        }
    }

    /**
     * LlamaEngine 経由で応答を生成し、ストリーミング風に表示する。
     * モデルが未ロードの場合は loadModelIfNeeded で初回ロードを行う。
     */
    private suspend fun generateResponse(userInput: String, model: LlmModel) {
        val startTime = System.currentTimeMillis()

        try {
            // モデルが未ロードなら遅延ロード
            engineManager.loadModelIfNeeded(model)
        } catch (e: Exception) {
            // ロード失敗: エラーメッセージを表示して終了
            val errorMsg = ChatMessage(
                role = MessageRole.ASSISTANT,
                content = "[Error] モデルのロードに失敗しました: ${e.message}\nモック応答モードで動作します。",
                isStreaming = false
            )
            _uiState.update { it.copy(messages = it.messages + errorMsg, isGenerating = false) }
            return
        }

        // ストリーミング用プレースホルダーメッセージを追加
        val streamingMessageId = IdGenerator.next()
        val streamingMessage = ChatMessage(
            id = streamingMessageId,
            role = MessageRole.ASSISTANT,
            content = "",
            isStreaming = true
        )
        _uiState.update {
            it.copy(messages = it.messages + streamingMessage)
        }

        val sb = StringBuilder()

        // コールバック: 各トークン（文字）をストリーミング表示
        val streamCallback: (String) -> Unit = { token ->
            sb.append(token)
            val currentContent = sb.toString()
            _uiState.update { state ->
                val updatedMessages = state.messages.map { msg ->
                    if (msg.id == streamingMessageId) {
                        msg.copy(content = currentContent)
                    } else {
                        msg
                    }
                }
                state.copy(messages = updatedMessages)
            }
        }

        // 推論実行（EngineManager 経由）- タイムアウト付き
        val fullResponse = try {
            kotlinx.coroutines.withTimeout(120_000L) {
                engineManager.generate(
                    prompt = buildPrompt(userInput, model),
                    maxTokens = 512,
                    callback = streamCallback
                )
            }
        } catch (e: Exception) {
            val errorText = sb.toString().ifEmpty { "[Error] 推論に失敗しました: ${e.message}" }
            errorText
        }

        // 完了: 応答時間と速度を計算して確定
        val elapsedMs = System.currentTimeMillis() - startTime
        val tokenCount = estimateTokenCount(fullResponse)
        val tokensPerSecond = if (elapsedMs > 0) {
            tokenCount.toFloat() / (elapsedMs / 1000f)
        } else {
            0f
        }

        _uiState.update { state ->
            val finalMessages = state.messages.map { msg ->
                if (msg.id == streamingMessageId) {
                    msg.copy(
                        content = fullResponse,
                        isStreaming = false,
                        responseTimeMs = elapsedMs,
                        tokensPerSecond = tokensPerSecond
                    )
                } else {
                    msg
                }
            }
            state.copy(
                messages = finalMessages,
                isGenerating = false
            )
        }
    }

    /**
     * モデルに応じたプロンプトを構築する。
     * 現時点ではユーザー入力をそのまま渡す（llama.cpp 統合後にテンプレートを適用する想定）。
     */
    private fun buildPrompt(userInput: String, model: LlmModel): String {
        return userInput
    }

    /**
     * トークン数の概算（日本語1文字 ≈ 1トークン、英語は4文字≈1トークン）
     */
    private fun estimateTokenCount(text: String): Int {
        var count = 0
        for (char in text) {
            count += if (char.code > 127) 3 else 1
        }
        return maxOf(count / 3, 1)
    }

    /**
     * モデルを切り替え、チャット履歴をリセット。
     * 旧モデルをアンロードし、次回メッセージ送信時に新モデルをロードする。
     */
    fun selectModel(model: LlmModel) {
        recordingTimerJob?.cancel()
        recordingTimerJob = null
        stopAudioRecord()

        // 旧モデルをアンロード（非同期）
        viewModelScope.launch(Dispatchers.IO) {
            engineManager.unloadCurrentModel()
        }

        _uiState.update {
            it.copy(
                selectedModel = model,
                messages = emptyList(),
                inputText = "",
                isGenerating = false,
                attachedImageUri = null,
                isRecording = false,
                recordingElapsedSeconds = 0,
                audioLevel = 0f,
                modelFilesMissing = false,
                missingModelPath = ""
            )
        }
    }

    /**
     * アプリがバックグラウンドに移行した時にモデルをアンロードする。
     * MainActivity から呼び出す（ON_STOP）。
     */
    fun onAppBackground() {
        viewModelScope.launch {
            engineManager.unloadCurrentModel()
        }
    }

    /**
     * アプリがフォアグラウンドに戻った時にモデルを再ロードする。
     * MainActivity から呼び出す（ON_START）。
     */
    fun onAppForeground() {
        viewModelScope.launch {
            engineManager.reloadLastModel()
        }
    }

    // -------------------------------------------------------------------------
    // Sprint 3: 画像添付
    // -------------------------------------------------------------------------

    /**
     * 添付画像URIをセット
     */
    fun setAttachedImage(uri: Uri?) {
        _uiState.update { it.copy(attachedImageUri = uri) }
    }

    /**
     * 添付画像をクリア
     */
    fun clearAttachedImage() {
        _uiState.update { it.copy(attachedImageUri = null) }
    }

    // -------------------------------------------------------------------------
    // Sprint 3: 録音（AudioRecord実連携）
    // -------------------------------------------------------------------------

    /** 録音タイマー用ジョブ */
    private var recordingTimerJob: kotlinx.coroutines.Job? = null
    /** 音声レベル監視ジョブ */
    private var audioLevelJob: kotlinx.coroutines.Job? = null

    /**
     * 録音開始（AudioRecord実連携）
     */
    fun startRecording(context: Context? = null) {
        _uiState.update { it.copy(isRecording = true, recordingElapsedSeconds = 0, audioLevel = 0f) }

        // タイマー
        recordingTimerJob?.cancel()
        recordingTimerJob = viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(1000L)
                _uiState.update { it.copy(recordingElapsedSeconds = it.recordingElapsedSeconds + 1) }
                // 60秒で自動停止
                if (_uiState.value.recordingElapsedSeconds >= 60) {
                    stopRecordingAndSend()
                    break
                }
            }
        }

        // AudioRecord 初期化
        val sampleRate = 44100
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, encoding)

        try {
            @Suppress("MissingPermission")
            val ar = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                encoding,
                bufferSize * 4
            )
            audioRecord = ar
            ar.startRecording()

            // 音声レベル監視（バックグラウンド）
            audioLevelJob?.cancel()
            audioLevelJob = viewModelScope.launch(Dispatchers.IO) {
                val buffer = ShortArray(bufferSize)
                while (_uiState.value.isRecording) {
                    val read = ar.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        var sum = 0.0
                        for (i in 0 until read) {
                            sum += buffer[i].toLong() * buffer[i].toLong()
                        }
                        val rms = Math.sqrt(sum / read)
                        val level = (rms / 32768.0).coerceIn(0.0, 1.0).toFloat()
                        _uiState.update { it.copy(audioLevel = level) }
                    }
                }
            }
        } catch (e: Exception) {
            audioRecord = null
        }
    }

    /**
     * AudioRecord を停止して解放
     */
    private fun stopAudioRecord() {
        audioLevelJob?.cancel()
        audioLevelJob = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            // 無視
        }
        audioRecord = null
    }

    /**
     * 録音停止 + 音声メッセージを送信
     */
    fun stopRecordingAndSend() {
        recordingTimerJob?.cancel()
        recordingTimerJob = null
        stopAudioRecord()

        val elapsed = _uiState.value.recordingElapsedSeconds

        val voiceMessage = ChatMessage(
            role = MessageRole.USER,
            content = "[音声メッセージ ${elapsed}秒]",
            isVoiceInput = true
        )

        _uiState.update {
            it.copy(
                isRecording = false,
                recordingElapsedSeconds = 0,
                audioLevel = 0f,
                messages = it.messages + voiceMessage,
                isGenerating = true
            )
        }

        val model = _uiState.value.selectedModel
        viewModelScope.launch {
            generateResponse("[音声入力]", model)
        }
    }

    /**
     * 録音をキャンセル（送信せずに録音UI終了）
     */
    fun cancelRecording() {
        recordingTimerJob?.cancel()
        recordingTimerJob = null
        stopAudioRecord()
        _uiState.update { it.copy(isRecording = false, recordingElapsedSeconds = 0, audioLevel = 0f) }
    }

    /**
     * 画像添付付きでメッセージを送信
     */
    fun sendMessageWithImage() {
        val currentState = _uiState.value
        val inputText = currentState.inputText.trim()
        val imageUri = currentState.attachedImageUri
        if ((inputText.isEmpty() && imageUri == null) || currentState.isGenerating) return

        val displayText = if (inputText.isEmpty()) "[画像を送信]" else inputText

        val userMessage = ChatMessage(
            role = MessageRole.USER,
            content = displayText,
            imageUri = imageUri
        )

        _uiState.update {
            it.copy(
                messages = it.messages + userMessage,
                inputText = "",
                attachedImageUri = null,
                isGenerating = true
            )
        }

        val imagePath = imageUri?.path
        viewModelScope.launch {
            if (imagePath != null) {
                generateResponseWithImage(displayText, imagePath, currentState.selectedModel)
            } else {
                generateResponse(displayText, currentState.selectedModel)
            }
        }
    }

    /**
     * 画像付き推論を実行する（Gemma 4 E4B 向け）。
     */
    private suspend fun generateResponseWithImage(
        userInput: String,
        imagePath: String,
        model: LlmModel
    ) {
        val startTime = System.currentTimeMillis()

        engineManager.loadModelIfNeeded(model)

        val streamingMessageId = IdGenerator.next()
        val streamingMessage = ChatMessage(
            id = streamingMessageId,
            role = MessageRole.ASSISTANT,
            content = "",
            isStreaming = true
        )
        _uiState.update {
            it.copy(messages = it.messages + streamingMessage)
        }

        val sb = StringBuilder()
        val streamCallback: (String) -> Unit = { token ->
            sb.append(token)
            val currentContent = sb.toString()
            _uiState.update { state ->
                val updatedMessages = state.messages.map { msg ->
                    if (msg.id == streamingMessageId) {
                        msg.copy(content = currentContent)
                    } else {
                        msg
                    }
                }
                state.copy(messages = updatedMessages)
            }
        }

        val fullResponse = engineManager.generateWithImage(
            prompt = buildPrompt(userInput, model),
            imagePath = imagePath,
            maxTokens = 512,
            callback = streamCallback
        )

        val elapsedMs = System.currentTimeMillis() - startTime
        val tokenCount = estimateTokenCount(fullResponse)
        val tokensPerSecond = if (elapsedMs > 0) {
            tokenCount.toFloat() / (elapsedMs / 1000f)
        } else {
            0f
        }

        _uiState.update { state ->
            val finalMessages = state.messages.map { msg ->
                if (msg.id == streamingMessageId) {
                    msg.copy(
                        content = fullResponse,
                        isStreaming = false,
                        responseTimeMs = elapsedMs,
                        tokensPerSecond = tokensPerSecond
                    )
                } else {
                    msg
                }
            }
            state.copy(
                messages = finalMessages,
                isGenerating = false
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        recordingTimerJob?.cancel()
        audioLevelJob?.cancel()
        stopAudioRecord()
    }

    // -------------------------------------------------------------------------
    // ViewModel Factory
    // -------------------------------------------------------------------------

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val engineManager = EngineManager(context.applicationContext)
            return ChatViewModel(engineManager) as T
        }
    }
}
