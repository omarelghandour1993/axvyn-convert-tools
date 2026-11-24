package com.axvyn.app.features.audiotomp3

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import com.axvyn.app.core.files.FilePicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class AudioConversionSettings(
    val bitrateKbps: Int,
)

data class AudioConversionOutcome(
    val file: File,
    val originalBytes: Long,
    val convertedBytes: Long,
    val durationMs: Long?,
)

/**
 * Decodes audio on-device using Android MediaExtractor/MediaCodec and encodes MP3 with
 * vendored LAME (LGPL v2). Only LAME encoding APIs are used; mpglib decoding is not compiled.
 */
class AudioConversionEngine {

    fun inspect(context: Context, uri: Uri): SourceAudioInfo {
        val metadata = FilePicker.metadata(context, uri)
        val durationMs = extractDurationMs(context, uri)
        return SourceAudioInfo(
            displayName = metadata?.displayName ?: "audio",
            sizeBytes = metadata?.sizeBytes ?: 0L,
            mimeType = context.contentResolver.getType(uri) ?: metadata?.mimeType,
            durationMs = durationMs,
        )
    }

    suspend fun convert(
        context: Context,
        uri: Uri,
        settings: AudioConversionSettings,
        outputFile: File,
        onProgress: (Float) -> Unit,
    ): AudioConversionOutcome = withContext(Dispatchers.IO) {
        val originalBytes = FilePicker.metadata(context, uri)?.sizeBytes ?: 0L
        val extractor = MediaExtractor()
        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            ?: error("Could not open the selected audio file")
        try {
            extractor.setDataSource(pfd.fileDescriptor)
            val trackIndex = selectAudioTrack(extractor)
                ?: throw IllegalArgumentException("No supported audio track was found in this file.")
            extractor.selectTrack(trackIndex)
            val inputFormat = extractor.getTrackFormat(trackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME)
                ?: throw IllegalArgumentException("The audio track format is missing a MIME type.")
            val durationUs = if (inputFormat.containsKey(MediaFormat.KEY_DURATION)) {
                inputFormat.getLong(MediaFormat.KEY_DURATION)
            } else null

            val codec = MediaCodec.createDecoderByType(mime)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                inputFormat.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            }
            codec.configure(inputFormat, null, null, 0)
                codec.start()
                try {
                    convertLoop(
                        extractor = extractor,
                        codec = codec,
                        outputFile = outputFile,
                        bitrateKbps = settings.bitrateKbps,
                        durationUs = durationUs,
                        onProgress = onProgress,
                    )
                } finally {
                    runCatching { codec.stop() }
                    codec.release()
                }
            } finally {
                // codec released in inner finally
            }

            val durationMs = durationUs?.let { it / 1000 }
            AudioConversionOutcome(
                file = outputFile,
                originalBytes = originalBytes,
                convertedBytes = outputFile.length(),
                durationMs = durationMs,
            )
        } finally {
            runCatching { extractor.release() }
            runCatching { pfd.close() }
        }
    }

    private suspend fun convertLoop(
        extractor: MediaExtractor,
        codec: MediaCodec,
        outputFile: File,
        bitrateKbps: Int,
        durationUs: Long?,
        onProgress: (Float) -> Unit,
    ) {
        val bufferInfo = MediaCodec.BufferInfo()
        var sawInputEos = false
        var sawOutputEos = false
        var encoder: LameMp3Encoder? = null
        var sampleRate = 44100
        var channels = 2
        var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT
        val mp3Buffer = ByteArray(8192 + 7200)

        FileOutputStream(outputFile).use { output ->
            try {
                while (!sawOutputEos) {
                    currentCoroutineContext().ensureActive()

                    if (!sawInputEos) {
                        val inputIndex = codec.dequeueInputBuffer(10_000)
                        if (inputIndex >= 0) {
                            val inputBuffer = codec.getInputBuffer(inputIndex)
                            if (inputBuffer != null) {
                                val sampleSize = extractor.readSampleData(inputBuffer, 0)
                                if (sampleSize < 0) {
                                    codec.queueInputBuffer(
                                        inputIndex,
                                        0,
                                        0,
                                        0L,
                                        MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                                    )
                                    sawInputEos = true
                                } else {
                                    val presentationTimeUs = extractor.sampleTime
                                    codec.queueInputBuffer(inputIndex, 0, sampleSize, presentationTimeUs, 0)
                                    extractor.advance()
                                    if (durationUs != null && durationUs > 0 && presentationTimeUs >= 0) {
                                        val progress = (presentationTimeUs.toFloat() / durationUs.toFloat()).coerceIn(0f, 0.95f)
                                        onProgress(progress)
                                    }
                                }
                            } else {
                                codec.queueInputBuffer(inputIndex, 0, 0, 0L, 0)
                            }
                        }
                    }

                    val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                    when {
                        outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                        outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val outFormat = codec.outputFormat
                            sampleRate = outFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            channels = outFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            if (outFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                                pcmEncoding = outFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                            }
                            if (encoder == null) {
                                encoder = LameMp3Encoder(sampleRate, channels, bitrateKbps)
                            }
                        }
                        outputIndex >= 0 -> {
                            val outputBuffer = codec.getOutputBuffer(outputIndex)
                            val isEos = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                            if (bufferInfo.size > 0 && outputBuffer != null) {
                                if (encoder == null) {
                                    val outFormat = codec.outputFormat
                                    if (outFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                                        sampleRate = outFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                                    }
                                    if (outFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                                        channels = outFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                                    }
                                    if (outFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                                        pcmEncoding = outFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                                    }
                                    encoder = LameMp3Encoder(sampleRate, channels, bitrateKbps)
                                }
                                outputBuffer.position(bufferInfo.offset)
                                outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                                val pcmChunk = ByteArray(bufferInfo.size)
                                outputBuffer.get(pcmChunk)
                                outputBuffer.clear()

                                val pcm16 = if (pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT) {
                                    floatPcmToPcm16(pcmChunk)
                                } else {
                                    pcmChunk
                                }
                                val encoded = encoder!!.encode(pcm16, pcm16.size, mp3Buffer)
                                if (encoded > 0) output.write(mp3Buffer, 0, encoded)
                            }
                            codec.releaseOutputBuffer(outputIndex, false)
                            if (isEos) sawOutputEos = true
                        }
                    }
                }

                currentCoroutineContext().ensureActive()
                val enc = encoder ?: throw IllegalStateException("Audio decoder produced no PCM output.")
                val flushed = enc.flush(mp3Buffer)
                if (flushed > 0) output.write(mp3Buffer, 0, flushed)
                output.flush()
                onProgress(1f)
            } finally {
                encoder?.close()
            }
        }
    }

    private fun selectAudioTrack(extractor: MediaExtractor): Int? {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) return i
        }
        return null
    }

    private fun extractDurationMs(context: Context, uri: Uri): Long? {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(pfd.fileDescriptor)
            val trackIndex = selectAudioTrack(extractor) ?: return null
            val format = extractor.getTrackFormat(trackIndex)
            if (!format.containsKey(MediaFormat.KEY_DURATION)) null
            else format.getLong(MediaFormat.KEY_DURATION) / 1000
        } catch (_: Exception) {
            null
        } finally {
            runCatching { extractor.release() }
            runCatching { pfd.close() }
        }
    }

    private fun floatPcmToPcm16(floatBytes: ByteArray): ByteArray {
        val floats = ByteBuffer.wrap(floatBytes).order(ByteOrder.nativeOrder()).asFloatBuffer()
        val out = ByteBuffer.allocate(floats.remaining() * 2).order(ByteOrder.nativeOrder())
        while (floats.hasRemaining()) {
            val sample = (floats.get().coerceIn(-1f, 1f) * 32767f).toInt().toShort()
            out.putShort(sample)
        }
        return out.array()
    }
}
