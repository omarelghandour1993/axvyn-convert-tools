package com.axvyn.app.features.imageconverter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import com.axvyn.app.core.files.FilePicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

data class ConversionOutcome(
    val bytes: ByteArray,
    val width: Int,
    val height: Int,
    val originalBytes: Long,
)

/** Decodes WebP/AVIF on-device and encodes PNG, preserving dimensions and transparency. */
class ImageConversionEngine {

    fun inspect(context: Context, uri: Uri): SourceImageInfo {
        val metadata = FilePicker.metadata(context, uri)
        val displayName = metadata?.displayName ?: "image"
        val format = detectFormat(context.contentResolver.getType(uri), displayName)

        val bounds = decodeBounds(context, uri)
        return SourceImageInfo(
            displayName = displayName,
            sizeBytes = metadata?.sizeBytes ?: 0L,
            width = bounds.first.coerceAtLeast(1),
            height = bounds.second.coerceAtLeast(1),
            format = format,
        )
    }

    suspend fun convert(
        context: Context,
        uri: Uri,
        onProgress: (Float) -> Unit,
    ): ConversionOutcome = withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        val originalBytes = FilePicker.metadata(context, uri)?.sizeBytes ?: 0L
        onProgress(0.1f)

        val bitmap = decode(context, uri)
        try {
            onProgress(0.6f)
            currentCoroutineContext().ensureActive()
            val bytes = ByteArrayOutputStream().use { output ->
                val ok = bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                if (!ok) error("Could not encode the PNG output")
                output.toByteArray()
            }
            onProgress(1f)
            ConversionOutcome(
                bytes = bytes,
                width = bitmap.width,
                height = bitmap.height,
                originalBytes = originalBytes,
            )
        } finally {
            bitmap.recycle()
        }
    }

    private fun decode(context: Context, uri: Uri): Bitmap {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                return ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = false
                }
            } catch (error: Exception) {
                // Fall through to BitmapFactory for formats it can handle; AVIF may not be supported.
                if (isLikelyAvif(context, uri)) {
                    throw IllegalArgumentException(
                        "This AVIF image can't be decoded on this device. Try updating the device or using a WebP image.",
                        error,
                    )
                }
                throw IllegalArgumentException(
                    "This image can't be decoded on this device.",
                    error,
                )
            }
        }

        val decoded = context.contentResolver.openInputStream(uri).use { input ->
            BitmapFactory.decodeStream(input)
        } ?: error("Could not decode the selected image")
        return decoded.copy(Bitmap.Config.ARGB_8888, false).also { copy ->
            if (copy !== decoded) decoded.recycle()
        }
    }

    private fun decodeBounds(context: Context, uri: Uri): Pair<Int, Int> {
        val factoryBounds = decodeBoundsBitmapFactory(context, uri)
        if (factoryBounds.first > 0 && factoryBounds.second > 0) return factoryBounds

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return try {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                var size = 0 to 0
                val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    size = info.size.width to info.size.height
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
                bitmap.recycle()
                if (size.first > 0 && size.second > 0) size else bitmap.width to bitmap.height
            } catch (_: Exception) {
                0 to 0
            }
        }
        return 0 to 0
    }

    private fun decodeBoundsBitmapFactory(context: Context, uri: Uri): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri).use { input ->
            BitmapFactory.decodeStream(input, null, options)
        }
        return options.outWidth to options.outHeight
    }

    private fun detectFormat(mime: String?, fileName: String): SourceImageFormat = when {
        mime == "image/avif" || fileName.endsWith(".avif", ignoreCase = true) -> SourceImageFormat.AVIF
        mime == "image/webp" || fileName.endsWith(".webp", ignoreCase = true) -> SourceImageFormat.WEBP
        else -> SourceImageFormat.UNKNOWN
    }

    private fun isLikelyAvif(context: Context, uri: Uri): Boolean {
        val metadata = FilePicker.metadata(context, uri)
        val name = metadata?.displayName.orEmpty()
        val mime = context.contentResolver.getType(uri)
        return mime == "image/avif" || name.endsWith(".avif", ignoreCase = true)
    }

}
