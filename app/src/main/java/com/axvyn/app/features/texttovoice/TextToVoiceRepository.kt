package com.axvyn.app.features.texttovoice

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.axvyn.app.core.files.FileSaver
import com.axvyn.app.core.storage.LocalStorageManager
import com.axvyn.app.features.texttovoice.engine.AndroidSystemTtsEngine
import com.axvyn.app.features.texttovoice.engine.TtsEngine
import com.axvyn.app.features.texttovoice.engine.TtsSynthesisRequest
import com.axvyn.app.features.texttovoice.engine.TtsVoiceOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TextToVoiceRepository(
    context: Context,
    private val engine: TtsEngine = AndroidSystemTtsEngine(context.applicationContext),
) {
    private val app = context.applicationContext
    private val storage = LocalStorageManager(app)

    suspend fun initialize(): Boolean = engine.initialize()

    fun availableVoices(): List<TtsVoiceOption> = engine.availableVoices()

    fun availableLanguages(voices: List<TtsVoiceOption>): List<String> = engine.availableLanguages(voices)

    suspend fun synthesize(
        request: TtsSynthesisRequest,
        onProgress: (Float) -> Unit,
    ): TextToVoiceResult = withContext(Dispatchers.IO) {
        val fileName = "axvyn-tts-${System.currentTimeMillis()}.mp3"
        val file = storage.workingFile(fileName)
        if (file.exists()) file.delete()
        try {
            val outcome = engine.synthesizeToMp3(
                request = request,
                outputFile = file,
                cacheDir = app.cacheDir,
                onProgress = onProgress,
            )
            TextToVoiceResult(
                fileName = fileName,
                charCount = request.text.trim().length,
                mp3Bytes = outcome.mp3Bytes,
                durationMs = outcome.durationMs,
                voiceLabel = request.voice.label,
                shareUri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file),
            )
        } catch (e: Exception) {
            file.delete()
            throw e
        }
    }

    fun save(fileName: String, destination: Uri) {
        FileSaver.writeBytes(app, destination, storage.workingFile(fileName).readBytes())
    }

    fun discard(fileName: String) {
        storage.workingFile(fileName).delete()
    }

    fun stop() {
        engine.stop()
    }

    fun shutdown() {
        engine.shutdown()
    }
}
