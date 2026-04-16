package com.example.onbitllm.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * 画像URIを長辺1024px以下にリサイズして新しいURIを返す。
 * 既に1024px以下の場合はそのままのURIを返す。
 */
fun resizeImageIfNeeded(context: Context, uri: Uri, maxSide: Int = 1024): Uri {
    val inputStream = context.contentResolver.openInputStream(uri) ?: return uri
    val original = BitmapFactory.decodeStream(inputStream)
    inputStream.close()

    if (original == null) return uri

    val maxDim = maxOf(original.width, original.height)
    if (maxDim <= maxSide) {
        original.recycle()
        return uri
    }

    val scale = maxSide.toFloat() / maxDim
    val newWidth = (original.width * scale).toInt()
    val newHeight = (original.height * scale).toInt()
    val resized = Bitmap.createScaledBitmap(original, newWidth, newHeight, true)
    original.recycle()

    val file = File(context.cacheDir, "resized_${System.currentTimeMillis()}.jpg")
    FileOutputStream(file).use { out ->
        resized.compress(Bitmap.CompressFormat.JPEG, 85, out)
    }
    resized.recycle()

    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
}
