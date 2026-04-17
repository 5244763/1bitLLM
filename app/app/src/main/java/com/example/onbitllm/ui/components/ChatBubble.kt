package com.example.onbitllm.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.onbitllm.model.ChatMessage
import com.example.onbitllm.model.MessageRole
import com.example.onbitllm.ui.theme.AccentCyan
import com.example.onbitllm.ui.theme.AccentPurpleLight
import com.example.onbitllm.ui.theme.AiBubble
import com.example.onbitllm.ui.theme.InputBackground
import com.example.onbitllm.ui.theme.TextAccent
import com.example.onbitllm.ui.theme.TextMuted
import com.example.onbitllm.ui.theme.TextPrimary
import com.example.onbitllm.ui.theme.UserBubble

/**
 * チャット吹き出しコンポーネント
 * ユーザー: 右寄せ青系
 * AI: 左寄せグレー系、下部に応答時間・速度を表示
 *
 * Sprint 3: 画像サムネイル表示 + 音声入力ラベル対応
 * Sprint 7: isLoadingModel 時にモデルロード中インジケーターを表示
 */
@Composable
fun ChatBubble(
    message: ChatMessage,
    isLoadingModel: Boolean = false,
    modifier: Modifier = Modifier
) {
    val isUser = message.role == MessageRole.USER

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (isUser) {
            UserBubbleContent(message)
        } else {
            AiBubbleContent(message, isLoadingModel)
        }
    }
}

@Composable
private fun UserBubbleContent(message: ChatMessage) {
    Box(
        modifier = Modifier
            .widthIn(max = 280.dp)
            .clip(
                RoundedCornerShape(
                    topStart = 18.dp,
                    topEnd = 18.dp,
                    bottomStart = 18.dp,
                    bottomEnd = 4.dp
                )
            )
            .background(UserBubble)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Column {
            // 音声入力ラベル（Sprint 3-5）
            if (message.isVoiceInput) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        tint = AccentPurpleLight,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "音声入力",
                        color = AccentPurpleLight,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // 画像サムネイル（Sprint 3-4: e.png参照: 画像の下にテキスト）
            if (message.imageUri != null) {
                AsyncImage(
                    model = message.imageUri,
                    contentDescription = "添付画像",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(width = 200.dp, height = 150.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(InputBackground)
                )
                if (message.content.isNotEmpty() && message.content != "[画像を送信]") {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // テキスト（画像なし、または画像の下）
            if (message.imageUri == null || (message.content.isNotEmpty() && message.content != "[画像を送信]")) {
                val displayText = if (message.isVoiceInput && message.content.startsWith("[音声メッセージ")) {
                    message.content
                } else {
                    message.content
                }
                if (displayText.isNotEmpty()) {
                    Text(
                        text = displayText,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        lineHeight = 22.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun AiBubbleContent(message: ChatMessage, isLoadingModel: Boolean = false) {
    Column(
        modifier = Modifier.widthIn(max = 300.dp)
    ) {
        Box(
            modifier = Modifier
                .clip(
                    RoundedCornerShape(
                        topStart = 4.dp,
                        topEnd = 18.dp,
                        bottomStart = 18.dp,
                        bottomEnd = 18.dp
                    )
                )
                .background(AiBubble)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            if (message.isStreaming && message.content.isEmpty() && isLoadingModel) {
                // モデルロード中: プログレスインジケーター + テキスト
                ModelLoadingIndicator()
            } else if (message.isStreaming && message.content.isEmpty()) {
                // コンテンツなしストリーミング中: ドット点滅アニメーション
                StreamingIndicator()
            } else {
                Text(
                    text = message.content,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                )
            }
        }

        // AI応答メタデータ（ストリーミング完了後に表示）
        if (!message.isStreaming && message.responseTimeMs != null && message.tokensPerSecond != null) {
            Spacer(modifier = Modifier.height(4.dp))
            ResponseMetaInfo(
                responseTimeMs = message.responseTimeMs,
                tokensPerSecond = message.tokensPerSecond
            )
        }
    }
}

/**
 * AI応答時間・生成速度の表示
 * 例: ⏱ 1.2s  44 tok/s
 */
@Composable
private fun ResponseMetaInfo(
    responseTimeMs: Long,
    tokensPerSecond: Float
) {
    val timeSeconds = responseTimeMs / 1000f
    val timeStr = if (timeSeconds < 10f) {
        String.format(java.util.Locale.US, "%.1f", timeSeconds)
    } else {
        String.format(java.util.Locale.US, "%.0f", timeSeconds)
    }
    val tokStr = String.format(java.util.Locale.US, "%.0f", tokensPerSecond)

    Row(
        modifier = Modifier.padding(start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "\u23F1 ${timeStr}s",
            color = TextAccent,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = "${tokStr} tok/s",
            color = TextMuted,
            fontSize = 11.sp
        )
    }
}

/**
 * モデルロード中のインジケーター表示
 */
@Composable
private fun ModelLoadingIndicator() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            color = AccentCyan,
            strokeWidth = 2.dp
        )
        Text(
            text = "モデルを読み込み中...",
            color = TextMuted,
            fontSize = 13.sp
        )
    }
}

/**
 * ストリーミング中のドットアニメーション
 */
@Composable
private fun StreamingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "streaming")

    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        repeat(3) { index ->
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 600,
                        delayMillis = index * 150,
                        easing = LinearEasing
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot_$index"
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .alpha(alpha)
                    .background(
                        color = AccentCyan,
                        shape = RoundedCornerShape(50)
                    )
            )
        }
    }
}
