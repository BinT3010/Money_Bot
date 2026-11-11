package com.moneyset.app.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Đọc/lưu ảnh hóa đơn trên máy. */
@Singleton
class ImageStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun readBytes(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Không đọc được ảnh")
    }

    fun mimeType(uri: Uri): String = context.contentResolver.getType(uri) ?: "image/jpeg"

    suspend fun saveBitmap(bitmap: Bitmap): Uri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "receipts").apply { mkdirs() }
        val file = File(dir, "receipt_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        Uri.fromFile(file)
    }
}
