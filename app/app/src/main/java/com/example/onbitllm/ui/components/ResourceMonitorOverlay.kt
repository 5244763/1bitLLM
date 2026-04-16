package com.example.onbitllm.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.onbitllm.model.LlmModel
import com.example.onbitllm.ui.theme.AccentCyan
import com.example.onbitllm.ui.theme.BackgroundCard
import com.example.onbitllm.ui.theme.OverlayBackground
import com.example.onbitllm.ui.theme.ResourceBarCpu
import com.example.onbitllm.ui.theme.ResourceBarModel
import com.example.onbitllm.ui.theme.ResourceBarSystem
import com.example.onbitllm.ui.theme.TextMuted
import com.example.onbitllm.ui.theme.TextPrimary
import com.example.onbitllm.ui.theme.TextSecondary
import com.example.onbitllm.viewmodel.ResourceViewModel

/**
 * リソースモニターオーバーレイ
 * d.png を参考に:
 * - 半透明ダーク背景 + 中央カード
 * - タイトル: "Resource Monitor"
 * - Model RAM / System RAM / CPU プログレスバー
 * - 閉じるボタン
 * - 1秒ごとにリアルタイム更新
 *
 * Sprint 4
 */
@Composable
fun ResourceMonitorOverlay(
    selectedModel: LlmModel,
    onDismiss: () -> Unit,
    engineProvider: (() -> com.example.onbitllm.engine.LlamaEngine?)? = null
) {
    val context = LocalContext.current
    val resourceViewModel: ResourceViewModel = if (engineProvider != null) {
        viewModel(factory = ResourceViewModel.FactoryWithEngine(context, engineProvider))
    } else {
        viewModel(factory = ResourceViewModel.Factory(context))
    }
    val state by resourceViewModel.state.collectAsState()

    // 表示時にモニタリング開始、非表示時に停止
    DisposableEffect(selectedModel) {
        resourceViewModel.startMonitoring(selectedModel)
        onDispose {
            resourceViewModel.stopMonitoring()
        }
    }

    // 半透明オーバーレイ背景（タップで閉じる）
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OverlayBackground)
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        // カードパネル（タップを消費して背景に伝播させない）
        Column(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .clip(RoundedCornerShape(20.dp))
                .background(BackgroundCard)
                .clickable(enabled = false, onClick = {})
                .padding(horizontal = 24.dp, vertical = 24.dp)
        ) {
            // タイトル行: アイコン + "Resource Monitor"
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.BarChart,
                    contentDescription = null,
                    tint = AccentCyan,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Resource Monitor",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Model RAM プログレスバー（青系）
            ResourceBar(
                label = "Model RAM",
                valueText = String.format("%.2f GB", state.modelRamGb),
                progress = (state.modelRamGb / state.modelRamMaxGb).coerceIn(0f, 1f),
                trackColor = Color(0xFF1A1A4A),
                barColor = ResourceBarModel
            )

            Spacer(modifier = Modifier.height(20.dp))

            // System RAM プログレスバー（紫系）
            ResourceBar(
                label = "System RAM",
                valueText = String.format(
                    "%.1f GB / %.0f GB",
                    state.systemUsedGb,
                    state.systemTotalGb
                ),
                progress = (state.systemUsedGb / state.systemTotalGb.coerceAtLeast(1f)).coerceIn(0f, 1f),
                trackColor = Color(0xFF1A1A4A),
                barColor = ResourceBarSystem
            )

            Spacer(modifier = Modifier.height(20.dp))

            // CPU プログレスバー（シアン/緑系）
            ResourceBar(
                label = "CPU",
                valueText = String.format("%.0f%%", state.cpuPercent),
                progress = (state.cpuPercent / 100f).coerceIn(0f, 1f),
                trackColor = Color(0xFF1A1A4A),
                barColor = ResourceBarCpu,
                valueColor = AccentCyan
            )

            Spacer(modifier = Modifier.height(28.dp))

            // 閉じるボタン（右寄せ）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF2A2A5A))
                ) {
                    Text(
                        text = "閉じる",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }
        }
    }
}

/**
 * 個別リソースバー
 * ラベル + 値（右端）+ プログレスバー
 */
@Composable
private fun ResourceBar(
    label: String,
    valueText: String,
    progress: Float,
    trackColor: Color,
    barColor: Color,
    valueColor: Color = TextSecondary
) {
    // プログレスのアニメーション
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 500),
        label = "progress_$label"
    )

    Column {
        // ラベル行 + 値
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = valueText,
                color = valueColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // プログレスバー
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = barColor,
            trackColor = trackColor,
            strokeCap = StrokeCap.Round
        )
    }
}
