package com.example.onbitllm.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.onbitllm.model.LlmModel
import com.example.onbitllm.ui.theme.AccentCyan
import com.example.onbitllm.ui.theme.AccentPurpleLight
import com.example.onbitllm.ui.theme.BackgroundDeepDark
import com.example.onbitllm.ui.theme.InputBackground
import com.example.onbitllm.ui.theme.InputBorder
import com.example.onbitllm.ui.theme.SendButtonBg
import com.example.onbitllm.ui.theme.SendButtonDisabled
import com.example.onbitllm.ui.theme.TextMuted
import com.example.onbitllm.ui.theme.TextPrimary

/**
 * テキスト入力バー（Sprint 2: モデル別UI切り替え対応）
 *
 * - BONSAI_8B: テキスト入力 + 送信ボタンのみ（Sprint 1と同じ）
 * - GEMMA_4_E4B: 画像ボタン + マイクボタン + テキスト入力 + 送信ボタン
 *
 * @param selectedModel  現在選択中のモデル
 * @param onImageClick   画像ボタンタップコールバック（Gemma時のみ表示）
 * @param onMicClick     マイクボタンタップコールバック（Gemma時のみ表示）
 * @param enabled        false のときは入力全体を無効化（モデルロード中などに使用）
 */
@Composable
fun ChatInputBar(
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    isGenerating: Boolean,
    selectedModel: LlmModel = LlmModel.BONSAI_8B,
    onImageClick: () -> Unit = {},
    onMicClick: () -> Unit = {},
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val isMultimodal = selectedModel.supportsMultimodal
    val canSend = inputText.isNotBlank() && !isGenerating && enabled

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(BackgroundDeepDark)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        // Gemma 時: 画像ボタン
        if (isMultimodal) {
            IconButton(
                onClick = onImageClick,
                enabled = !isGenerating && enabled,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(InputBackground)
            ) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = "画像を添付",
                    tint = if (!enabled || isGenerating) TextMuted else AccentCyan,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Gemma 時: マイクボタン
            IconButton(
                onClick = onMicClick,
                enabled = !isGenerating && enabled,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(InputBackground)
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "音声入力",
                    tint = if (!enabled || isGenerating) TextMuted else AccentPurpleLight,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))
        }

        // テキスト入力フィールド
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(if (enabled) InputBackground else InputBackground.copy(alpha = 0.5f))
                .border(
                    width = 1.dp,
                    color = if (enabled) InputBorder else InputBorder.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            BasicTextField(
                value = inputText,
                onValueChange = { if (enabled) onInputChange(it) },
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                textStyle = TextStyle(
                    color = if (enabled) TextPrimary else TextMuted,
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                ),
                cursorBrush = SolidColor(AccentCyan),
                maxLines = 3,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Send
                ),
                keyboardActions = KeyboardActions(
                    onSend = { if (canSend) onSend() }
                ),
                decorationBox = { innerTextField ->
                    if (inputText.isEmpty()) {
                        Text(
                            text = if (!enabled) "モデルを読み込み中..."
                                   else if (isMultimodal) "Gemmaに質問する..."
                                   else "メッセージを入力...",
                            color = TextMuted,
                            fontSize = 15.sp
                        )
                    }
                    innerTextField()
                }
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // 送信ボタン
        IconButton(
            onClick = { if (canSend) onSend() },
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    if (canSend) SendButtonBg else SendButtonDisabled
                )
        ) {
            if (isGenerating) {
                // 生成中インジケーター
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(20.dp)
                ) {
                    Text(
                        text = "...",
                        color = TextPrimary,
                        fontSize = 12.sp
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "送信",
                    tint = TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
