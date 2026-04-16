package com.example.onbitllm.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.onbitllm.ui.theme.AccentCyan
import com.example.onbitllm.ui.theme.BackgroundCard
import com.example.onbitllm.ui.theme.OverlayBackground
import com.example.onbitllm.ui.theme.PrimaryPurple
import com.example.onbitllm.ui.theme.TextMuted
import com.example.onbitllm.ui.theme.TextPrimary
import com.example.onbitllm.ui.theme.TextSecondary

/**
 * 画像ソース選択のボトムシート
 * Sprint 2: UI実装のみ。実際のカメラ/ギャラリー連携はSprint 3で実装。
 */
@Composable
fun ImageSourceBottomSheet(
    onCameraSelected: () -> Unit,
    onGallerySelected: () -> Unit,
    onDismiss: () -> Unit
) {
    // オーバーレイ背景（タップで閉じる）
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OverlayBackground)
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter
    ) {
        // BottomSheet 本体（タップイベントを消費して親に伝播させない）
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(BackgroundCard)
                .clickable(enabled = false, onClick = {})
                .padding(bottom = 24.dp)
        ) {
            // ハンドルバー
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .align(Alignment.CenterHorizontally)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(TextMuted.copy(alpha = 0.4f))
            )

            Text(
                text = "画像を追加",
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )

            HorizontalDivider(color = TextMuted.copy(alpha = 0.15f))

            // カメラで撮影
            ImageSourceItem(
                icon = {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(22.dp)
                    )
                },
                title = "カメラで撮影",
                subtitle = "新しい写真を撮影して送信",
                onClick = {
                    onDismiss()
                    onCameraSelected()
                }
            )

            HorizontalDivider(
                color = TextMuted.copy(alpha = 0.08f),
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            // ギャラリーから選択
            ImageSourceItem(
                icon = {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = null,
                        tint = PrimaryPurple,
                        modifier = Modifier.size(22.dp)
                    )
                },
                title = "ギャラリーから選択",
                subtitle = "端末内の写真を選択して送信",
                onClick = {
                    onDismiss()
                    onGallerySelected()
                }
            )
        }
    }
}

@Composable
private fun ImageSourceItem(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(PrimaryPurple.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                color = TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
