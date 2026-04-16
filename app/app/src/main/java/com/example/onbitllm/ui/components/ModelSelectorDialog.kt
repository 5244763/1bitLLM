package com.example.onbitllm.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.onbitllm.model.LlmModel
import com.example.onbitllm.ui.theme.AccentCyan
import com.example.onbitllm.ui.theme.BackgroundCard
import com.example.onbitllm.ui.theme.BackgroundCardDark
import com.example.onbitllm.ui.theme.OverlayBackground
import com.example.onbitllm.ui.theme.PrimaryPurple
import com.example.onbitllm.ui.theme.TextMuted
import com.example.onbitllm.ui.theme.TextPrimary
import com.example.onbitllm.ui.theme.TextSecondary

/**
 * モデル選択ダイアログ（Sprint 1ではUI表示のみ）
 * Sprint 2でドロップダウンとして完全実装予定
 *
 * c.png のデザインに合わせた実装
 * 説明文: Bonsai: "1-bit LLM · テキスト" / Gemma: "マルチモーダル · テキスト・画像・音声"
 */
@Composable
fun ModelSelectorDialog(
    selectedModel: LlmModel,
    onModelSelected: (LlmModel) -> Unit,
    onDismiss: () -> Unit
) {
    // 背景タップで閉じる
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OverlayBackground)
            .clickable(onClick = onDismiss)
    ) {
        // ダイアログカード（タップイベントを消費して親に伝播させない）
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 80.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(BackgroundCard)
                .clickable(enabled = false, onClick = {}) // 伝播防止
                .padding(16.dp)
        ) {
            // タイトル
            Text(
                text = "SELECT MODEL",
                color = TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // モデル一覧
            LlmModel.entries.forEach { model ->
                ModelSelectItem(
                    model = model,
                    isSelected = model == selectedModel,
                    onClick = { onModelSelected(model) }
                )
            }
        }
    }
}

@Composable
private fun ModelSelectItem(
    model: LlmModel,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isSelected) BackgroundCardDark else androidx.compose.ui.graphics.Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // モデルアイコン
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(PrimaryPurple.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = when (model) {
                    LlmModel.BONSAI_8B -> Icons.Default.Psychology
                    LlmModel.GEMMA_4_E4B -> Icons.Default.Memory
                },
                contentDescription = null,
                tint = AccentCyan,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // モデル名と説明
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = model.displayName,
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = model.description,
                color = TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        // 選択チェックマーク
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "選択中",
                tint = AccentCyan,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
