package com.example.onbitllm.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.onbitllm.model.LlmModel
import com.example.onbitllm.ui.theme.HeaderBackground
import com.example.onbitllm.ui.theme.TextSecondary

/**
 * チャット画面ヘッダー
 * 左: モデル選択ドロップダウン
 * 右: リソースモニターボタン
 */
@Composable
fun ChatHeader(
    selectedModel: LlmModel,
    onModelSelectorClick: () -> Unit,
    onResourceMonitorClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(HeaderBackground)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左: モデル選択ボタン
        ModelSelectorButton(
            selectedModel = selectedModel,
            onClick = onModelSelectorClick
        )

        // 右: リソースモニターボタン
        IconButton(onClick = onResourceMonitorClick) {
            Icon(
                imageVector = Icons.Default.BarChart,
                contentDescription = "リソースモニター",
                tint = TextSecondary
            )
        }
    }
}
