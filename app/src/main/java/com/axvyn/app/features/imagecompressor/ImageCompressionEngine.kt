package com.axvyn.app.features.imagecompressor

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
import kotlin.math.roundToInt

data class CompressionSettings(
    val outputFormat: ImageFormat,
    val quality: Int,
    val resizeEnabled: Boolean,
    val targetWidth: Int?,
    val targetHeight: Int?,
    val targetSizeKb: Int?,
)

data class CompressionOutcome(
    val bytes: ByteArray,
    val width: Int,
    val height: Int,
    val originalBytes: Long,
    val note: String?,
)

/** On-device bitmap decoding/encoding. No network, one bitmap in memory at a time. */
class ImageCompressionEngine {

    fun inspect(context: Context, uri: Uri): SourceImageInfo {
        val metadata = FilePicker.metadata(context, uri)
        val displayName = metadata?.displayName ?: "image"
        val format = formatFromMime(context.contentResolver.getType(uri), displayName)

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri).use { input ->
            BitmapFactory.decodeStream(input, null, bounds)
        }

        return SourceImageInfo(
            displayName = displayName,
            sizeBytes = metadata?.sizeBytes ?: 0L,
            width = bounds.outWidth.coerceAtLeast(1),
            height = bounds.outHeight.coerceAtLeast(1),
            format = format,
        )
    }

    suspend fun compress(
        context: Context,
        uri: Uri,
        settings: CompressionSettings,
        onProgress: (Float) -> Unit,
    ): CompressionOutcome = withContext(Dispatchers.IO) {
        val source = inspect(context, uri)
        val (targetWidth, targetHeight) = targetDimensions(source, settings)
        val bitmap = decode(context, uri, targetWidth, targetHeight)
        try {
            onProgress(0.1f)
            val (bytes, note) = encodeWithTarget(bitmap, settings, onProgress)
            onProgress(1f)
            CompressionOutcome(
                bytes = bytes,
                width = bitmap.width,
                height = bitmap.height,
                originalBytes = source.sizeBytes,
                note = note,
            )
        } finally {
            bitmap.recycle()
        }
    }

    private fun targetDimensions(source: SourceImageInfo, settings: CompressionSettings): Pair<Int, Int> {
        if (!settings.resizeEnabled) return source.width to source.height
        val width = settings.targetWidth
        val height = settings.targetHeight
        return when {
            width != null && height != null -> width to height
            width != null -> width to (source.height.toFloat() * width / source.width)
                .roundToInt().coerceAtLeast(1)
            height != null -> (source.width.toFloat() * height / source.height)
                .roundToInt().coerceAtLeast(1) to height
            else -> source.width to source.height
        }
    }

    private fun decode(context: Context, uri: Uri, width: Int, height: Int): Bitmap {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            return ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.setTargetSize(width, height)
            }
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri).use { input ->
            BitmapFactory.decodeStream(input, null, bounds)
        }
        val sample = calculateSampleSize(bounds.outWidth, bounds.outHeight, width, height)
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = context.contentResolver.openInputStream(uri).use { input ->
            BitmapFactory.decodeStream(input, null, options)
        } ?: error("Could not decode the selected image")

        return if (decoded.width == width && decoded.height == height) {
            decoded
        } else {
            val scaled = Bitmap.createScaledBitmap(decoded, width, height, true)
            if (scaled !== decoded) decoded.recycle()
            scaled
        }
    }

    private fun calculateSampleSize(sourceWidth: Int, sourceHeight: Int, reqWidth: Int, reqHeight: Int): Int {
        var sample = 1
        val halfWidth = sourceWidth / 2
        val halfHeight = sourceHeight / 2
        while (halfWidth / sample >= reqWidth && halfHeight / sample >= reqHeight) {
            sample *= 2
        }
        return sample.coerceAtLeast(1)
    }

    private suspend fun encodeWithTarget(
        bitmap: Bitmap,
        settings: CompressionSettings,
        onProgress: (Float) -> Unit,
    ): Pair<ByteArray, String?> {
        val quality = settings.quality.coerceIn(1, 100)
        val target = settings.targetSizeKb

        if (target == null || settings.outputFormat == ImageFormat.PNG) {
            currentCoroutineContext().ensureActive()
            val note = if (target != null && settings.outputFormat == ImageFormat.PNG) {
                "PNG is lossless, so a target file size cannot be applied."
            } else {
                null
            }
            return encode(bitmap, settings.outputFormat, quality) to note
        }

        val targetBytes = target * 1_000L
        var low = 5
        var high = quality
        var best: ByteArray? = null
        var iterations = 0

        while (low <= high && iterations < 8) {
            currentCoroutineContext().ensureActive()
            val middle = (low + high) / 2
            val bytes = encode(bitmap, settings.outputFormat, middle)
            if (bytes.size <= targetBytes) {
                best = bytes
                high = middle - 1
            } else {
                low = middle + 1
            }
            iterations++
            onProgress(0.1f + (iterations / 8f) * 0.8f)
        }

        val result = best ?: encode(bitmap, settings.outputFormat, 5)
        val note = if (result.size > targetBytes) {
            "The target size could not be reached at minimum quality."
        } else {
            null
        }
        return result to note
    }

    private fun encode(bitmap: Bitmap, format: ImageFormat, quality: Int): ByteArray {
        val compressFormat = when (format) {
            ImageFormat.JPEG -> Bitmap.CompressFormat.JPEG
            ImageFormat.PNG -> Bitmap.CompressFormat.PNG
            ImageFormat.WEBP -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
        }
        return ByteArrayOutputStream().use { output ->
            bitmap.compress(compressFormat, quality, output)
            output.toByteArray()
        }
    }

    private fun formatFromMime(mime: String?, fileName: String): ImageFormat = when {
        mime == "image/png" || fileName.endsWith(".png", ignoreCase = true) -> ImageFormat.PNG
        mime == "image/webp" || fileName.endsWith(".webp", ignoreCase = true) -> ImageFormat.WEBP
        else -> ImageFormat.JPEG
    }
}
