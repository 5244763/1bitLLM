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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
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

    // カメラ撮影用: 一時ファイルのURI
    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }

    // カメラ起動フラグ（権限許可後にLaunchedEffectで起動するために使用）
    var pendingCameraLaunch by remember { mutableStateOf(false) }

    // ---- ランチャー定義 ----

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
                snackbarHostState.showSnackbar("カメラ権限が必要です。設定から許可してください。")
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
                snackbarHostState.showSnackbar("マイク権限が必要です。設定から許可してください。")
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
                onResourceMonitorClick = { showResourceMonitor = true }
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
                            context = context
                        )
                    }
                }
                items(
                    items = uiState.messages,
                    key = { it.id }
                ) { message ->
                    ChatBubble(message = message)
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
                            }
                        )
                    }
                }
            }
        }

        // Snackbar（権限拒否時メッセージ）
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

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
private fun EmptyStateMessage(model: LlmModel, context: Context) {
    val modelFileName = when (model) {
        LlmModel.BONSAI_8B -> "bonsai-8b-q1_0.gguf"
        LlmModel.GEMMA_4_E4B -> "gemma-4-e4b-q4_k_m.gguf"
    }
    // 内部ストレージ（実際のロード先）で確認
    val internalFile = File(File(context.filesDir, "models"), modelFileName)
    // 外部ストレージ（adb push先）も確認
    val externalFile = File(File(context.getExternalFilesDir(null), "models"), modelFileName)
    val modelFileExists = internalFile.exists() || externalFile.exists()
    val nativeLibAvailable = LlamaBridge.isNativeLibAvailable()
    val pushPath = "/sdcard/Android/data/${context.packageName}/files/models/$modelFileName"

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 60.dp),
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
            if (!modelFileExists) {
                Text(
                    text = "Model file not found.\nadb push to:\n$pushPath",
                    color = TextMuted,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp)
                )
            } else {
                Text(
                    text = "メッセージを入力してチャットを開始",
                    color = TextMuted,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 24.dp)
                )
            }
            // 診断情報
            Text(
                text = "Native: ${if (nativeLibAvailable) "ON" else "OFF"} | Model: ${if (modelFileExists) "Found" else "Missing"}",
                color = TextMuted.copy(alpha = 0.5f),
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}
