package com.axvyn.app.features.audiotomp3

/**
 * Thin JNI wrapper around vendored LAME (LGPL v2). See docs/LEGAL-LAME.md.
 * Only encoding APIs are used; no mpglib decoding is compiled or linked.
 */
class LameMp3Encoder(
    sampleRate: Int,
    channels: Int,
    bitrateKbps: Int,
) : AutoCloseable {

    private var handle: Long = nativeCreate(sampleRate, channels, bitrateKbps)

    fun encode(pcmData: ByteArray, bytesRead: Int, mp3Out: ByteArray): Int {
        val h = handle
        check(h != 0L) { "LAME encoder is closed" }
        return nativeEncode(h, pcmData, bytesRead, mp3Out)
    }

    fun flush(mp3Out: ByteArray): Int {
        val h = handle
        check(h != 0L) { "LAME encoder is closed" }
        return nativeFlush(h, mp3Out)
    }

    override fun close() {
        val h = handle
        if (h != 0L) {
            handle = 0L
            nativeClose(h)
        }
    }

    private external fun nativeCreate(sampleRate: Int, channels: Int, bitrateKbps: Int): Long
    private external fun nativeEncode(handle: Long, pcmData: ByteArray, bytesRead: Int, mp3Out: ByteArray): Int
    private external fun nativeFlush(handle: Long, mp3Out: ByteArray): Int
    private external fun nativeClose(handle: Long)

    companion object {
        init {
            System.loadLibrary("axvyn_lame")
        }
    }
}
