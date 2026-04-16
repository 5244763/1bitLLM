package com.example.onbitllm.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.onbitllm.ui.theme.AccentCyan
import com.example.onbitllm.ui.theme.AccentPurpleLight
import com.example.onbitllm.ui.theme.BackgroundDeepDark
import com.example.onbitllm.ui.theme.InputBackground
import com.example.onbitllm.ui.theme.SendButtonBg
import com.example.onbitllm.ui.theme.TextMuted
import com.example.onbitllm.ui.theme.TextPrimary

/**
 * 録音中の入力バー
 * b.png を参考に:
 * - 上部: 赤丸 + 「録音中...」テキスト
 * - 中部: 経過時間（00:XX形式）
 * - 下部: 波形アニメーション（実際の音声レベルに連動）
 * - 最下部: ゴミ箱ボタン(キャンセル) + 「停止して送信」ボタン
 *
 * Sprint 3: audioLevel パラメータで実際の録音レベルを反映
 */
@Composable
fun RecordingInputBar(
    elapsedSeconds: Int,
    onStopAndSend: () -> Unit,
    onCancel: () -> Unit,
    audioLevel: Float = 0f,
    modifier: Modifier = Modifier
) {
    val minutes = elapsedSeconds / 60
    val seconds = elapsedSeconds % 60
    val timeText = "%02d:%02d".format(minutes, seconds)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(BackgroundDeepDark)
            .padding(horizontal = 16.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 録音中ラベル（赤丸 + テキスト）
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            RecordingDot()
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "録音中...",
                color = Color(0xFFFF4444),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 経過時間（大きな数字）
        Text(
            text = timeText,
            color = TextPrimary,
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )

        Spacer(modifier = Modifier.height(18.dp))

        // 波形アニメーション（音声レベルに連動）
        WaveformAnimation(audioLevel = audioLevel)

        Spacer(modifier = Modifier.height(22.dp))

        // ボタン行: キャンセル(ゴミ箱) + 停止して送信
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // キャンセルボタン（ゴミ箱）
            IconButton(
                onClick = onCancel,
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(InputBackground)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "録音をキャンセル",
                    tint = TextMuted,
                    modifier = Modifier.size(24.dp)
                )
            }

            // 停止して送信ボタン
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
                    .height(52.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(SendButtonBg)
                    .clickable(onClick = onStopAndSend),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = null,
                    tint = TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "停止して送信",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * 録音中の点滅する赤ドット
 */
@Composable
private fun RecordingDot() {
    val infiniteTransition = rememberInfiniteTransition(label = "recording_dot")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_alpha"
    )

    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(Color(0xFFFF4444).copy(alpha = alpha))
    )
}

/**
 * 波形アニメーション（バー型）
 * b.png のデザインを参考に紫/シアン系のバー
 * audioLevel > 0 の場合は実際の音声レベルでスケールを増幅
 */
@Composable
private fun WaveformAnimation(audioLevel: Float = 0f) {
    // バーの高さパターン（中央が高い山型）
    val barHeights = listOf(14, 22, 34, 46, 54, 60, 54, 60, 46, 34, 46, 32, 22, 16, 14)
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")

    // 音声レベルが来ている場合は増幅係数 (1.0〜2.5)
    val levelBoost = 1.0f + (audioLevel * 1.5f)

    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(64.dp)
    ) {
        barHeights.forEachIndexed { index, baseHeight ->
            // 各バーの位相をずらしてアニメーション
            val animatedScale by infiniteTransition.animateFloat(
                initialValue = 0.35f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 500 + (index * 60) % 300,
                        delayMillis = (index * 80) % 400,
                        easing = FastOutSlowInEasing
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar_$index"
            )

            val barColor = when (index % 3) {
                0 -> AccentPurpleLight
                1 -> AccentCyan.copy(alpha = 0.85f)
                else -> AccentPurpleLight.copy(alpha = 0.65f)
            }

            // 実際の音声レベルを反映させたバー高さ
            val finalHeight = (baseHeight * animatedScale * levelBoost).coerceAtMost(62f)

            Box(
                modifier = Modifier
                    .width(5.dp)
                    .height(finalHeight.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(barColor)
            )
        }
    }
}
