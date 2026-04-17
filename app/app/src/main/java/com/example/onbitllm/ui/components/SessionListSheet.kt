package com.example.onbitllm.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.onbitllm.model.ChatSession
import com.example.onbitllm.ui.theme.AccentCyan
import com.example.onbitllm.ui.theme.BackgroundCard
import com.example.onbitllm.ui.theme.BackgroundDark
import com.example.onbitllm.ui.theme.HeaderBackground
import com.example.onbitllm.ui.theme.TextMuted
import com.example.onbitllm.ui.theme.TextPrimary
import com.example.onbitllm.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * セッション一覧のサイドシート（フルスクリーンダイアログ）
 *
 * Sprint 7: セッション一覧・切り替え・削除・新規作成
 */
@Composable
fun SessionListSheet(
    sessions: List<ChatSession>,
    currentSessionId: String?,
    onSessionClick: (String) -> Unit,
    onSessionDelete: (String) -> Unit,
    onNewSession: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // 右側: タップで閉じる透明エリア
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onDismiss() }
            )

            // 左側: セッション一覧シート
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(300.dp)
                    .align(Alignment.CenterStart)
                    .background(BackgroundDark)
            ) {
                // ヘッダー
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(HeaderBackground)
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "会話履歴",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "閉じる",
                            tint = TextSecondary
                        )
                    }
                }

                // 新しい会話ボタン
                Button(
                    onClick = {
                        onNewSession()
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentCyan.copy(alpha = 0.8f),
                        contentColor = BackgroundDark
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("新しい会話", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }

                HorizontalDivider(color = Color(0xFF2A2A55), thickness = 1.dp)

                if (sessions.isEmpty()) {
                    // セッションがない場合の表示
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "会話履歴がありません",
                            color = TextMuted,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f)
                    ) {
                        items(
                            items = sessions,
                            key = { it.id }
                        ) { session ->
                            SessionItem(
                                session = session,
                                isSelected = session.id == currentSessionId,
                                onSelect = {
                                    onSessionClick(session.id)
                                    onDismiss()
                                },
                                onDelete = { onSessionDelete(session.id) }
                            )
                            HorizontalDivider(color = Color(0xFF1A1A35), thickness = 0.5.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionItem(
    session: ChatSession,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    val bgColor = if (isSelected) BackgroundCard.copy(alpha = 0.8f) else Color.Transparent
    val titleColor = if (isSelected) AccentCyan else TextPrimary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .clickable(onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = session.title,
                color = titleColor,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatDate(session.updatedAt),
                    color = TextMuted,
                    fontSize = 11.sp
                )
                Text(
                    text = "|",
                    color = TextMuted,
                    fontSize = 11.sp
                )
                Text(
                    text = session.modelName,
                    color = TextMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // 削除ボタン
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "削除",
                tint = Color(0xFFEF5350),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * タイムスタンプを人間が読めるフォーマットに変換
 */
private fun formatDate(timestampMs: Long): String {
    val sdf = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
    return sdf.format(Date(timestampMs))
}
