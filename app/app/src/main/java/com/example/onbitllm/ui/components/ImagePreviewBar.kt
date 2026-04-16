package com.example.onbitllm.ui.components

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.example.onbitllm.ui.theme.AccentCyan
import com.example.onbitllm.ui.theme.BackgroundDeepDark
import com.example.onbitllm.ui.theme.InputBackground
import com.example.onbitllm.ui.theme.InputBorder
import com.example.onbitllm.ui.theme.TextMuted
import com.example.onbitllm.ui.theme.TextPrimary

/**
 * 添付画像のプレビューバー
 * 入力欄の上部に表示。サムネイル + ✕削除ボタン
 *
 * Sprint 3: Coil で実際の画像サムネイルを表示
 */
@Composable
fun ImagePreviewBar(
    imageUri: Uri?,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = imageUri != null,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BackgroundDeepDark)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 画像サムネイル（Coil: 実画像 / fallback: アイコン）
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(InputBackground)
                    .border(1.dp, InputBorder, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (imageUri != null) {
                    SubcomposeAsyncImage(
                        model = imageUri,
                        contentDescription = "添付画像プレビュー",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                    ) {
                        val state = painter.state
                        if (state is coil.compose.AsyncImagePainter.State.Loading ||
                            state is coil.compose.AsyncImagePainter.State.Error) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = "添付画像",
                                tint = AccentCyan,
                                modifier = Modifier.size(28.dp)
                            )
                        } else {
                            SubcomposeAsyncImageContent()
                        }
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = "添付画像",
                        tint = AccentCyan,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Text(
                text = "画像を添付済み",
                color = TextMuted,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f)
            )

            // ✕削除ボタン
            IconButton(
                onClick = onClear,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(InputBackground)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "添付解除",
                    tint = TextPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
