package com.axvyn.app.features.texttovoice

import android.content.Context
import android.media.AudioFormat
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.axvyn.app.data.voicepacks.VoicePackCatalog
import com.axvyn.app.features.audiotomp3.LameMp3Encoder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.math.max

data class TtsSynthesisRequest(
    val text: String,
    val voice: TtsVoiceOption,
)

data class TtsSynthesisOutcome(
    val file: File,
    val mp3Bytes: Long,
    val durationMs: Long?,
)

class TextToVoiceEngine(private val context: Context) {

    private val engines = mutableMapOf<String, TextToSpeech>()
    private var defaultEnginePackage: String? = null

    suspend fun initialize(): Boolean = withContext(Dispatchers.Main) {
        if (engines.isNotEmpty()) return@withContext true
        val defaultTts = initEngine(null) ?: return@withContext false
        val defaultPkg = runCatching { defaultTts.defaultEngine }.getOrNull()
        defaultEnginePackage = defaultPkg
        val engineInfos = runCatching { defaultTts.engines }.getOrNull().orEmpty()
        val knownPkgs = listOf("com.samsung.SMT", "com.google.android.tts")
        val allPkgs = (engineInfos.map { it.name } + knownPkgs).distinct()
        // Initialized engines: $allPkgs (default=$defaultPkg)
        allPkgs.forEach { pkg ->
            if (engines.containsKey(pkg)) return@forEach
            if (pkg == defaultPkg) {
                engines[pkg] = defaultTts
            } else {
                initEngine(pkg)?.let { engines[pkg] = it }
            }
        }
        if (defaultPkg != null && !engines.containsKey(defaultPkg)) {
            engines[defaultPkg] = defaultTts
        }
        if (engines.isEmpty()) engines["default"] = defaultTts
        engines.isNotEmpty()
    }

    private suspend fun initEngine(enginePackage: String?): TextToSpeech? = withContext(Dispatchers.Main) {
        val ready = CompletableDeferred<Boolean>()
        val engine = if (enginePackage == null) {
            TextToSpeech(context.applicationContext) { status ->
                if (!ready.isCompleted) ready.complete(status == TextToSpeech.SUCCESS)
            }
        } else {
            TextToSpeech(context.applicationContext, { status ->
                if (!ready.isCompleted) ready.complete(status == TextToSpeech.SUCCESS)
            }, enginePackage)
        }
        val ok = withTimeoutOrNull(5_000) { ready.await() } == true
        if (!ok) {
            runCatching { engine.shutdown() }
            return@withContext null
        }
        runCatching { engine.setSpeechRate(1.0f) }
        runCatching { engine.setPitch(1.0f) }
        engine
    }

    fun stop() {
        engines.values.forEach { runCatching { it.stop() } }
    }

    fun shutdown() {
        val list = engines.values.toList()
        engines.clear()
        list.forEach { tts ->
            runCatching { tts.stop() }
            runCatching { tts.shutdown() }
        }
    }

    fun availableVoices(): List<TtsVoiceOption> {
        val all = mutableListOf<TtsVoiceOption>()
        engines.forEach { (pkg, engine) ->
            val voices = runCatching { engine.voices }.getOrNull().orEmpty()
            // Enumerate offline, installed voices for supported languages.
            voices.asSequence()
                .filter { !it.isNetworkConnectionRequired }
                .filter { !it.features.contains("notInstalled") }
                .mapNotNull { it.toOption(pkg) }
                .filter { option ->
                    VoicePackCatalog.candidateLanguages.any { it.equals(option.languageDisplay, ignoreCase = true) }
                }
                .forEach { all += it }
        }
        // Fallback: some engines report language availability without exposing a Voice object.
        VoicePackCatalog.candidateLanguages.forEach { lang ->
            if (all.any { it.languageDisplay.equals(lang, ignoreCase = true) }) return@forEach
            val locale = localeForLanguage(lang) ?: return@forEach
            engines.forEach { (pkg, engine) ->
                val avail = runCatching { engine.isLanguageAvailable(locale) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
                if (avail >= TextToSpeech.LANG_AVAILABLE) {
                    all += TtsVoiceOption(
                        id = "lang-${locale.toLanguageTag()}-$pkg",
                        label = "$lang (Device voice)",
                        localeTag = locale.toLanguageTag(),
                        languageDisplay = lang,
                        enginePackage = pkg,
                        isOffline = true,
                        quality = 100,
                    )
                    // Fallback locale-only voice option for this engine.
                }
            }
        }

        val deduped = all
            .groupBy { "${it.languageDisplay}|${it.localeTag}|${it.label.lowercase()}" }
            .map { (_, group) ->
                group.minByOrNull { voiceRank(it) } ?: group.first()
            }
            .sortedWith(compareBy<TtsVoiceOption> { it.languageDisplay }.thenBy { voiceRank(it) }.thenBy { it.label })
        return deduped
    }

    private fun voiceRank(option: TtsVoiceOption): Int {
        val isLegacy = option.label.contains("legacy", ignoreCase = true) || option.id.contains("-language")
        val isDefaultEngine = option.enginePackage == defaultEnginePackage
        val qualityPenalty = when {
            option.quality >= 400 -> 0
            option.quality >= 300 -> 1
            option.quality >= 200 -> 2
            else -> 3
        }
        return (if (isLegacy) 20 else 0) + qualityPenalty + (if (isDefaultEngine) 0 else 1)
    }

    fun availableLanguages(voices: List<TtsVoiceOption>): List<String> {
        return VoicePackCatalog.candidateLanguages
    }

    suspend fun synthesizeToMp3(
        request: TtsSynthesisRequest,
        outputFile: File,
        cacheDir: File,
        onProgress: (Float) -> Unit,
    ): TtsSynthesisOutcome = withContext(Dispatchers.IO) {
        val enginePkg = request.voice.enginePackage ?: defaultEnginePackage
        val engine = enginePkg?.let { engines[it] } ?: engines.values.firstOrNull()
            ?: error("Text-to-speech engine is not ready.")
        val text = request.text.trim()
        if (text.isEmpty()) error("Enter some text first.")
        if (text.length > VoicePackCatalog.MAX_CHARACTERS) {
            error("Text exceeds the ${VoicePackCatalog.MAX_CHARACTERS} character limit.")
        }

        val voice = resolveVoice(engine, request.voice)
        val locale = localeForLanguage(request.voice.languageDisplay)
            ?: Locale.forLanguageTag(request.voice.localeTag)
        if (voice == null) {
            val avail = runCatching { engine.isLanguageAvailable(locale) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
            if (avail < TextToSpeech.LANG_AVAILABLE) {
                error("No offline voice is available for ${request.voice.languageDisplay} on this device.")
            }
        }
        val chunkLimit = 1800
        val chunks = splitText(text, chunkLimit)
        if (chunks.isEmpty()) error("Enter some text first.")

        val tempWavs = mutableListOf<File>()
        var encoder: LameMp3Encoder? = null
        var sampleRate = 22050
        var channels = 1
        var totalPcmBytes = 0L

        try {
            FileOutputStream(outputFile).use { mp3Out ->
                val mp3Buffer = ByteArray(8192 + 7200)
                chunks.forEachIndexed { index, chunk ->
                    currentCoroutineContext().ensureActive()
                    val wavFile = File(cacheDir, "axvyn-tts-${UUID.randomUUID()}.wav")
                    tempWavs += wavFile
                    synthesizeChunk(engine, voice, locale, chunk, wavFile)

                    currentCoroutineContext().ensureActive()
                    val pcm = readWavPcm16(wavFile)
                    if (encoder == null) {
                        sampleRate = pcm.sampleRate
                        channels = pcm.channels
                        encoder = LameMp3Encoder(sampleRate, channels, 128)
                    } else if (pcm.sampleRate != sampleRate || pcm.channels != channels) {
                        // Rare case: engine changes format between chunks. Convert naively by rejecting.
                        error("The TTS engine returned inconsistent audio format. Try a different voice.")
                    }

                    val data = pcm.pcmData
                    var offset = 0
                    val block = 8192
                    while (offset < data.size) {
                        currentCoroutineContext().ensureActive()
                        val len = minOf(block, data.size - offset)
                        val encoded = encoder!!.encode(data.copyOfRange(offset, offset + len), len, mp3Buffer)
                        if (encoded > 0) mp3Out.write(mp3Buffer, 0, encoded)
                        offset += len
                        totalPcmBytes += len
                    }

                    val progress = ((index + 1).toFloat() / chunks.size.toFloat()).coerceIn(0f, 0.98f)
                    onProgress(progress)
                }

                val enc = encoder ?: error("No audio was generated.")
                val flushed = enc.flush(mp3Buffer)
                if (flushed > 0) mp3Out.write(mp3Buffer, 0, flushed)
                mp3Out.flush()
            }

            onProgress(1f)
            val durationMs = if (sampleRate > 0 && channels > 0) {
                val bytesPerSecond = sampleRate.toLong() * channels.toLong() * 2L
                if (bytesPerSecond > 0) (totalPcmBytes * 1000L) / bytesPerSecond else null
            } else null

            TtsSynthesisOutcome(
                file = outputFile,
                mp3Bytes = outputFile.length(),
                durationMs = durationMs,
            )
        } catch (e: Exception) {
            outputFile.delete()
            throw e
        } finally {
            runCatching { encoder?.close() }
            tempWavs.forEach { it.delete() }
        }
    }

    private fun resolveVoice(engine: TextToSpeech, option: TtsVoiceOption): Voice? {
        val voices = runCatching { engine.voices }.getOrNull().orEmpty()
        voices.firstOrNull { it.name == option.id && !it.isNetworkConnectionRequired }?.let { return it }
        val locale = Locale.forLanguageTag(option.localeTag)
        return voices.firstOrNull {
            !it.isNetworkConnectionRequired && !it.features.contains("notInstalled") &&
                it.locale.language.equals(locale.language, ignoreCase = true) &&
                (locale.country.isBlank() || it.locale.country.equals(locale.country, ignoreCase = true))
        }
    }

    private suspend fun synthesizeChunk(
        engine: TextToSpeech,
        voice: Voice?,
        locale: Locale,
        text: String,
        outFile: File,
    ) = withContext(Dispatchers.Main) {
        val utteranceId = "axvyn-${UUID.randomUUID()}"
        val done = CompletableDeferred<Unit>()

        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(id: String?) {
                if (id == utteranceId && !done.isCompleted) done.complete(Unit)
            }

            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) {
                if (id == utteranceId && !done.isCompleted) {
                    done.completeExceptionally(IllegalStateException("TTS synthesis failed."))
                }
            }

            override fun onError(id: String?, errorCode: Int) {
                if (id == utteranceId && !done.isCompleted) {
                    done.completeExceptionally(IllegalStateException("TTS synthesis failed (code $errorCode)."))
                }
            }
        })

        if (voice != null) {
            runCatching { engine.voice = voice }
            runCatching { engine.language = voice.locale }
        } else {
            runCatching { engine.language = locale }
        }
        runCatching { engine.setSpeechRate(1.0f) }
        runCatching { engine.setPitch(1.0f) }

        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }
        val result = engine.synthesizeToFile(text, params, outFile, utteranceId)
        if (result != TextToSpeech.SUCCESS) {
            throw IllegalStateException("Could not start TTS synthesis.")
        }

        withTimeoutOrNull(120_000) { done.await() }
            ?: throw IllegalStateException("TTS synthesis timed out.")
        if (!outFile.exists() || outFile.length() <= 44L) {
            throw IllegalStateException("The TTS engine produced no audio for this text.")
        }
    }

    private fun Voice.toOption(enginePackage: String): TtsVoiceOption? {
        val loc = locale ?: return null
        val language = displayLanguage(loc) ?: return null
        val isLegacy = features.contains("legacySetLanguageVoice") || name?.contains("-language") == true
        val label = buildString {
            append(language)
            val country = loc.displayCountry.takeIf { it.isNotBlank() }
            if (!country.isNullOrBlank()) append(" ($country)")
            val voiceName = name
                ?.substringAfterLast('#')
                ?.replace('_', ' ')
                ?.replace('-', ' ')
                ?.trim()
            if (!voiceName.isNullOrBlank() && !voiceName.equals(language, ignoreCase = true) && !isLegacy) {
                append(" · ")
                append(voiceName.take(32))
            }
            if (isLegacy) append(" · Standard")
        }
        return TtsVoiceOption(
            id = name ?: "${loc.toLanguageTag()}-${UUID.randomUUID()}",
            label = label,
            localeTag = loc.toLanguageTag(),
            languageDisplay = language,
            enginePackage = enginePackage,
            isOffline = !isNetworkConnectionRequired,
            quality = quality,
        )
    }

    private fun displayLanguage(locale: Locale): String? {
        val tag = locale.toLanguageTag().lowercase(Locale.ROOT)
        val lang = locale.language.lowercase(Locale.ROOT)
        return when {
            lang == "ar" -> "Arabic"
            lang == "zh" -> "Chinese"
            lang == "nl" -> "Dutch"
            lang == "en" -> "English"
            lang == "fr" -> "French"
            lang == "de" -> "German"
            lang == "it" -> "Italian"
            lang == "ja" -> "Japanese"
            lang == "ko" -> "Korean"
            lang == "pl" -> "Polish"
            lang == "pt" -> "Portuguese"
            lang == "ru" -> "Russian"
            lang == "es" -> "Spanish"
            lang == "tr" -> "Turkish"
            tag.startsWith("cmn") || tag.startsWith("yue") -> "Chinese"
            else -> null
        }
    }

    private fun localeForLanguage(language: String): Locale? = when (language.lowercase(Locale.ROOT)) {
        "arabic" -> Locale.forLanguageTag("ar")
        "chinese" -> Locale.forLanguageTag("zh")
        "dutch" -> Locale.forLanguageTag("nl")
        "english" -> Locale.forLanguageTag("en-US")
        "french" -> Locale.forLanguageTag("fr")
        "german" -> Locale.forLanguageTag("de")
        "italian" -> Locale.forLanguageTag("it")
        "japanese" -> Locale.forLanguageTag("ja")
        "korean" -> Locale.forLanguageTag("ko")
        "polish" -> Locale.forLanguageTag("pl")
        "portuguese" -> Locale.forLanguageTag("pt")
        "russian" -> Locale.forLanguageTag("ru")
        "spanish" -> Locale.forLanguageTag("es")
        "turkish" -> Locale.forLanguageTag("tr")
        else -> null
    }

    private data class WavPcm(val sampleRate: Int, val channels: Int, val pcmData: ByteArray)

    private fun readWavPcm16(file: File): WavPcm = readWavPcm16RandomAccess(file)

    private fun readWavPcm16RandomAccess(file: File): WavPcm {
        val bytes = file.readBytes()
        if (bytes.size < 44) error("Invalid WAV file from TTS engine.")
        fun str(off: Int) = String(bytes, off, 4)
        if (str(0) != "RIFF" || str(8) != "WAVE") error("TTS did not produce a WAV file.")
        var pos = 12
        var audioFormat = 1
        var channels = 1
        var sampleRate = 22050
        var bitsPerSample = 16
        var dataOffset = -1
        var dataSize = -1
        while (pos + 8 <= bytes.size) {
            val id = str(pos)
            val size = (ByteBuffer.wrap(bytes, pos + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL).toInt()
            val chunkData = pos + 8
            if (size < 0 || chunkData > bytes.size) break
            if (id == "fmt " && chunkData + 16 <= bytes.size) {
                val fb = ByteBuffer.wrap(bytes, chunkData, minOf(size, bytes.size - chunkData)).order(ByteOrder.LITTLE_ENDIAN)
                audioFormat = fb.short.toInt() and 0xFFFF
                channels = fb.short.toInt() and 0xFFFF
                sampleRate = fb.int
                fb.int; fb.short
                bitsPerSample = fb.short.toInt() and 0xFFFF
            } else if (id == "data") {
                dataOffset = chunkData
                dataSize = size
                break
            }
            val next = chunkData.toLong() + size.toLong() + (size.toLong() % 2L)
            if (next <= pos) break
            pos = next.coerceAtMost(bytes.size.toLong()).toInt()
        }
        if (dataOffset < 0 || dataSize < 0) error("WAV data chunk not found.")
        val available = (bytes.size - dataOffset).coerceAtLeast(0)
        val len = minOf(dataSize, available)
        val raw = bytes.copyOfRange(dataOffset, dataOffset + len)

        val pcm16: ByteArray = when {
            audioFormat == 1 && bitsPerSample == 16 -> raw
            audioFormat == 1 && bitsPerSample == 8 -> {
                val out = ByteArray(raw.size * 2)
                for (i in raw.indices) {
                    val s = ((raw[i].toInt() and 0xFF) - 128) * 256
                    out[i * 2] = (s and 0xFF).toByte()
                    out[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
                }
                out
            }
            audioFormat == 3 && bitsPerSample == 32 -> {
                val fb = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
                val out = ByteBuffer.allocate(fb.remaining() * 2).order(ByteOrder.LITTLE_ENDIAN)
                while (fb.hasRemaining()) {
                    val s = (fb.get().coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                    out.putShort(s)
                }
                out.array()
            }
            else -> error("Unsupported TTS WAV format (format=$audioFormat, bits=$bitsPerSample).")
        }
        return WavPcm(sampleRate.coerceAtLeast(8000), channels.coerceIn(1, 2), pcm16)
    }

    private fun splitText(text: String, limit: Int): List<String> {
        val normalized = text.replace("\r\n", "\n").trim()
        if (normalized.length <= limit) return listOf(normalized)
        val chunks = mutableListOf<String>()
        var remaining = normalized
        while (remaining.isNotBlank()) {
            if (remaining.length <= limit) {
                chunks += remaining.trim()
                break
            }
            var cut = remaining.lastIndexOf("\n\n", limit)
            if (cut < limit / 3) cut = remaining.lastIndexOf('\n', limit)
            if (cut < limit / 3) cut = remaining.lastIndexOf(". ", limit)
            if (cut < limit / 3) cut = remaining.lastIndexOf("? ", limit)
            if (cut < limit / 3) cut = remaining.lastIndexOf("! ", limit)
            if (cut < limit / 3) cut = remaining.lastIndexOf(' ', limit)
            if (cut < limit / 3) cut = limit
            val part = remaining.substring(0, cut).trim()
            if (part.isNotEmpty()) chunks += part
            remaining = remaining.substring(cut).trimStart()
        }
        return chunks.filter { it.isNotBlank() }
    }
}
