package com.example.onbitllm.ui.screens

import android.Manifest
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.onbitllm.engine.EngineManager
import com.example.onbitllm.engine.LlamaBridge
import com.example.onbitllm.model.LlmModel
import com.example.onbitllm.ui.components.ChatBubble
import com.example.onbitllm.ui.components.ChatHeader
import com.example.onbitllm.ui.components.ChatInputBar
import com.example.onbitllm.ui.components.ImagePreviewBar
import com.example.onbitllm.ui.components.ImageSourceBottomSheet
import com.example.onbitllm.ui.components.ModelSelectorDialog
import com.example.onbitllm.ui.components.RecordingInputBar
import com.example.onbitllm.ui.components.ResourceMonitorOverlay
import com.example.onbitllm.ui.components.SessionListSheet
import com.example.onbitllm.ui.theme.AccentCyan
import com.example.onbitllm.ui.theme.BackgroundDark
import com.example.onbitllm.ui.theme.TextMuted
import com.example.onbitllm.viewmodel.ChatViewModel
import kotlinx.coroutines.launch
import java.io.File

/**
 * チャット画面のメインスクリーン
 *
 * Sprint 5:
 * - ChatViewModel を外部から受け取る（MainActivity が Factory で生成したインスタンス）
 * - モデルファイル欠如時にウェルカムメッセージでadb pushパスを案内
 *
 * Sprint 7:
 * - Snackbar のカスタマイズ（背景・文字色）
 * - ファイルコピー中ダイアログ表示
 * - セッション一覧サイドシート
 * - isLoadingModel を ChatBubble に渡す
 */
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // ダイアログ・BottomSheet の表示状態
    var showModelSelector by remember { mutableStateOf(false) }
    var showResourceMonitor by remember { mutableStateOf(false) }
    var showImageSourceSheet by remember { mutableStateOf(false) }
    var showSessionList by remember { mutableStateOf(false) }

    // カメラ撮影用: 一時ファイルのURI
    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }

    // カメラ起動フラグ（権限許可後にLaunchedEffectで起動するために使用）
    var pendingCameraLaunch by remember { mutableStateOf(false) }

    // ---- ランチャー定義 ----

    // モデルファイル選択ランチャー（SAF: Storage Access Framework）
    val modelFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                val model = uiState.selectedModel
                val fileName = when (model) {
                    LlmModel.BONSAI_8B -> EngineManager.BONSAI_FILE
                    LlmModel.GEMMA_4_E4B -> EngineManager.GEMMA_FILE
                }
                val modelsDir = File(context.filesDir, "models")
                modelsDir.mkdirs()
                val destFile = File(modelsDir, fileName)

                // フェーズ1: ファイルコピー（IOスレッドで実行）
                viewModel.onFileCopyStart()
                val copySuccess = try {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        val input = context.contentResolver.openInputStream(it)
                        if (input == null) {
                            false
                        } else {
                            input.use { ins ->
                                destFile.outputStream().use { out ->
                                    ins.copyTo(out, bufferSize = 8192)
                                }
                            }
                            true
                        }
                    }
                } catch (e: Exception) {
                    viewModel.onModelLoadEnd()
                    snackbarHostState.showSnackbar("error:コピー失敗: ${e.message}")
                    return@launch
                }

                if (!copySuccess) {
                    viewModel.onModelLoadEnd()
                    snackbarHostState.showSnackbar("error:ファイルを開けませんでした")
                    return@launch
                }

                // コピー結果の検証
                val fileSizeMb = destFile.length() / 1024 / 1024
                if (fileSizeMb < 10) {
                    viewModel.onModelLoadEnd()
                    snackbarHostState.showSnackbar("error:コピーされたファイルが小さすぎます (${fileSizeMb}MB)")
                    return@launch
                }

                // フェーズ2: モデルをメモリに強制ロード
                viewModel.onFileCopyEnd()
                val loadResult = viewModel.loadModelAfterCopy()

                when {
                    loadResult == "native" -> snackbarHostState.showSnackbar("success:モデル準備完了！（${fileSizeMb}MB ネイティブ推論）")
                    loadResult == "mock" -> snackbarHostState.showSnackbar("error:ネイティブロード失敗（${fileSizeMb}MB）。デモモードで動作します。")
                    else -> snackbarHostState.showSnackbar("error:ロード失敗: $loadResult")
                }
            }
        }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            cameraImageUri?.let { uri ->
                val resized = com.example.onbitllm.util.resizeImageIfNeeded(context, uri)
                viewModel.setAttachedImage(resized)
            }
        } else {
            cameraImageUri = null
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingCameraLaunch = true
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("error:カメラ権限が必要です。設定から許可してください。")
            }
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startRecording(context)
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("error:マイク権限が必要です。設定から許可してください。")
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            val resized = com.example.onbitllm.util.resizeImageIfNeeded(context, it)
            viewModel.setAttachedImage(resized)
        }
    }

    // カメラ起動フラグが立ったらカメラを起動
    LaunchedEffect(pendingCameraLaunch) {
        if (pendingCameraLaunch) {
            pendingCameraLaunch = false
            if (cameraImageUri == null) {
                cameraImageUri = createCameraImageUri(context)
            }
            cameraImageUri?.let { uri ->
                takePictureLauncher.launch(uri)
            }
        }
    }

    // 新しいメッセージが追加されたら最下部にスクロール
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    // ストリーミング中も最下部にスクロール
    LaunchedEffect(uiState.messages.lastOrNull()?.content) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // ヘッダー
            ChatHeader(
                selectedModel = uiState.selectedModel,
                onModelSelectorClick = { showModelSelector = true },
                onResourceMonitorClick = { showResourceMonitor = true },
                onSessionListClick = { showSessionList = true }
            )

            // チャットエリア
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                if (uiState.messages.isEmpty()) {
                    item {
                        EmptyStateMessage(
                            model = uiState.selectedModel,
                            context = context,
                            isEngineLoaded = viewModel.getEngine()?.isModelLoaded() == true,
                            onLoadModel = {
                                modelFileLauncher.launch(arrayOf("*/*"))
                            }
                        )
                    }
                }
                items(
                    items = uiState.messages,
                    key = { it.id }
                ) { message ->
                    // 最後のAIメッセージがストリーミング中 + モデルロード中の場合のみフラグを渡す
                    val isLastMsg = message == uiState.messages.lastOrNull()
                    ChatBubble(
                        message = message,
                        isLoadingModel = isLastMsg && uiState.isLoadingModel
                    )
                }
            }

            // ---- 入力エリア（モデルによって切り替え） ----

            AnimatedContent(
                targetState = uiState.isRecording,
                transitionSpec = {
                    (slideInVertically { it } + fadeIn()) togetherWith
                            (slideOutVertically { it } + fadeOut())
                },
                label = "input_bar_transition"
            ) { isRecording ->
                if (isRecording) {
                    RecordingInputBar(
                        elapsedSeconds = uiState.recordingElapsedSeconds,
                        onStopAndSend = viewModel::stopRecordingAndSend,
                        onCancel = viewModel::cancelRecording,
                        audioLevel = uiState.audioLevel
                    )
                } else {
                    Column {
                        if (uiState.selectedModel.supportsMultimodal) {
                            ImagePreviewBar(
                                imageUri = uiState.attachedImageUri,
                                onClear = viewModel::clearAttachedImage
                            )
                        }

                        ChatInputBar(
                            inputText = uiState.inputText,
                            onInputChange = viewModel::onInputTextChange,
                            onSend = {
                                if (uiState.selectedModel.supportsMultimodal) {
                                    viewModel.sendMessageWithImage()
                                } else {
                                    viewModel.sendMessage()
                                }
                            },
                            isGenerating = uiState.isGenerating,
                            selectedModel = uiState.selectedModel,
                            onImageClick = { showImageSourceSheet = true },
                            onMicClick = {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            },
                            enabled = uiState.loadingPhase.isEmpty()
                        )
                    }
                }
            }
        }

        // Snackbar（カスタムカラー）
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        ) { snackbarData ->
            val message = snackbarData.visuals.message
            // プレフィックスで成功/エラーを判定
            val isSuccess = message.startsWith("success:")
            val isError = message.startsWith("error:")
            val displayMessage = when {
                isSuccess -> message.removePrefix("success:")
                isError -> message.removePrefix("error:")
                else -> message
            }
            val bgColor = when {
                isSuccess -> Color(0xFF1B7A5A)   // 緑系
                isError -> Color(0xFF8B2020)     // 赤系
                else -> Color(0xFF00897B)        // AccentCyan 系（デフォルト）
            }
            Snackbar(
                snackbarData = snackbarData,
                containerColor = bgColor,
                contentColor = Color(0xFFEEEEFF),
                modifier = Modifier.padding(8.dp)
            )
        }

        // モデル選択ダイアログ
        if (showModelSelector) {
            ModelSelectorDialog(
                selectedModel = uiState.selectedModel,
                onModelSelected = { model ->
                    viewModel.selectModel(model)
                    showModelSelector = false
                },
                onDismiss = { showModelSelector = false }
            )
        }

        // 画像ソース選択BottomSheet
        if (showImageSourceSheet) {
            ImageSourceBottomSheet(
                onCameraSelected = {
                    showImageSourceSheet = false
                    cameraImageUri = createCameraImageUri(context)
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                },
                onGallerySelected = {
                    showImageSourceSheet = false
                    galleryLauncher.launch(
                        androidx.activity.result.PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly
                        )
                    )
                },
                onDismiss = { showImageSourceSheet = false }
            )
        }

        // ResourceMonitorOverlay
        if (showResourceMonitor) {
            ResourceMonitorOverlay(
                selectedModel = uiState.selectedModel,
                onDismiss = { showResourceMonitor = false },
                engineProvider = { viewModel.getEngine() }
            )
        }

        // ファイルコピー中 / モデルロード中ダイアログ（キャンセル不可）
        if (uiState.loadingPhase == "copying" || uiState.loadingPhase == "loading") {
            val dialogTitle = when (uiState.loadingPhase) {
                "copying" -> "ファイルをコピー中..."
                "loading" -> "モデルを読み込み中..."
                else -> ""
            }
            val dialogBody = when (uiState.loadingPhase) {
                "copying" -> "しばらくお待ちください"
                "loading" -> "これには数十秒かかります"
                else -> ""
            }
            AlertDialog(
                onDismissRequest = { /* キャンセル不可 */ },
                properties = androidx.compose.ui.window.DialogProperties(
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false
                ),
                title = {
                    Text(
                        text = dialogTitle,
                        color = Color(0xFFEEEEFF)
                    )
                },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = AccentCyan,
                            strokeWidth = 3.dp
                        )
                        Text(
                            text = dialogBody,
                            color = Color(0xFFAAAAAA),
                            fontSize = 13.sp
                        )
                    }
                },
                confirmButton = {},
                containerColor = Color(0xFF1A1A3A)
            )
        }

        // セッション一覧サイドシート
        if (showSessionList) {
            SessionListSheet(
                sessions = uiState.allSessions,
                currentSessionId = uiState.currentSessionId,
                onSessionClick = { sessionId ->
                    viewModel.switchToSession(sessionId)
                },
                onSessionDelete = { sessionId ->
                    viewModel.deleteSession(sessionId)
                },
                onNewSession = {
                    viewModel.createNewSession()
                },
                onDismiss = { showSessionList = false }
            )
        }
    }
}

/**
 * カメラ撮影用の一時ファイルURIを作成
 */
private fun createCameraImageUri(context: Context): Uri? {
    return try {
        val cacheDir = File(context.cacheDir, "camera_images")
        cacheDir.mkdirs()
        val imageFile = File.createTempFile("camera_", ".jpg", cacheDir)
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            imageFile
        )
    } catch (e: Exception) {
        null
    }
}

/**
 * チャット履歴がない場合のウェルカムメッセージ
 *
 * Sprint 5: モデルファイルが存在しない場合は adb push のパスを案内する。
 */
@Composable
private fun EmptyStateMessage(
    model: LlmModel,
    context: Context,
    isEngineLoaded: Boolean = false,
    onLoadModel: () -> Unit = {}
) {
    // エンジンがロード済みならモデルは利用可能
    val modelReady = isEngineLoaded

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = model.displayName,
                color = AccentCyan,
                fontSize = 20.sp,
                textAlign = TextAlign.Center
            )
            Text(
                text = model.description,
                color = TextMuted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
            if (!modelReady) {
                Text(
                    text = "モデルファイルが見つかりません。\nGGUFファイルをダウンロードして\n下のボタンから読み込んでください。",
                    color = TextMuted,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 20.dp)
                )
                androidx.compose.material3.Button(
                    onClick = onLoadModel,
                    modifier = Modifier.padding(top = 16.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = AccentCyan.copy(alpha = 0.8f),
                        contentColor = BackgroundDark
                    )
                ) {
                    Text("モデルファイルを選択", fontSize = 14.sp)
                }
            } else {
                Text(
                    text = "メッセージを入力してチャットを開始",
                    color = TextMuted,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 24.dp)
                )
            }
        }
    }
}
