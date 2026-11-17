package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Base64
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

object MediaUtils {
    private val bitmapCache = object : LruCache<String, Bitmap>(32) {}

    fun createTempCameraUri(context: Context): Uri {
        val cameraDir = File(context.cacheDir, "camera").apply { mkdirs() }
        val photoFile = File(cameraDir, "bps_cam_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            photoFile
        )
    }

    suspend fun compressImageUriToBase64(
        context: Context,
        uri: Uri,
        maxDimension: Int = 960,
        maxBytes: Int = 135_000
    ): String? = withContext(Dispatchers.IO) {
        try {
            val sourceBitmap = decodeScaledBitmap(context, uri, maxDimension) ?: return@withContext null
            val resized = scaleBitmapIfNeeded(sourceBitmap, maxDimension)
            if (resized !== sourceBitmap) {
                sourceBitmap.recycle()
            }

            val baos = ByteArrayOutputStream()
            var quality = 82
            resized.compress(Bitmap.CompressFormat.JPEG, quality, baos)
            while (baos.size() > maxBytes && quality > 28) {
                baos.reset()
                quality -= 14
                resized.compress(Bitmap.CompressFormat.JPEG, quality, baos)
            }
            if (baos.size() > maxBytes) {
                // Shrink further if still above budget
                val smaller = scaleBitmapIfNeeded(resized, (maxDimension * 0.65f).toInt())
                baos.reset()
                smaller.compress(Bitmap.CompressFormat.JPEG, 40, baos)
                if (smaller !== resized) smaller.recycle()
            }
            resized.recycle()
            Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeScaledBitmap(context: Context, uri: Uri, maxDimension: Int): Bitmap? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val w = info.size.width
                val h = info.size.height
                val maxSide = maxOf(w, h)
                if (maxSide > maxDimension) {
                    val ratio = maxDimension.toFloat() / maxSide.toFloat()
                    decoder.setTargetSize(
                        (w * ratio).toInt().coerceAtLeast(1),
                        (h * ratio).toInt().coerceAtLeast(1)
                    )
                }
            }
        } else {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, boundsOpts)
            }
            val maxSide = maxOf(boundsOpts.outWidth, boundsOpts.outHeight)
            var sampleSize = 1
            while (maxSide / sampleSize > maxDimension * 2) {
                sampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOpts)
            }
        }
    }

    private fun scaleBitmapIfNeeded(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val maxSide = maxOf(w, h)
        if (maxSide <= maxDimension) return bitmap
        val ratio = maxDimension.toFloat() / maxSide.toFloat()
        val targetW = (w * ratio).toInt().coerceAtLeast(1)
        val targetH = (h * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
    }

    fun decodeBase64ToBitmap(base64: String?): Bitmap? {
        if (base64.isNullOrBlank()) return null
        val cacheKey = "${base64.hashCode()}_${base64.length}"
        bitmapCache.get(cacheKey)?.let { return it }
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (bmp != null) {
                bitmapCache.put(cacheKey, bmp)
            }
            bmp
        } catch (_: Exception) {
            null
        }
    }

    fun decodeBase64ToImageBitmap(base64: String?): ImageBitmap? {
        return decodeBase64ToBitmap(base64)?.asImageBitmap()
    }
}
