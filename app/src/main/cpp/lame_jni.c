#include <jni.h>
#include <stdlib.h>
#include <stdint.h>
#include <string.h>
#include "lame.h"

static void throw_illegal_state(JNIEnv *env, const char *message) {
    jclass cls = (*env)->FindClass(env, "java/lang/IllegalStateException");
    if (cls != NULL) {
        (*env)->ThrowNew(env, cls, message);
    }
}

static void throw_illegal_arg(JNIEnv *env, const char *message) {
    jclass cls = (*env)->FindClass(env, "java/lang/IllegalArgumentException");
    if (cls != NULL) {
        (*env)->ThrowNew(env, cls, message);
    }
}

JNIEXPORT jlong JNICALL
Java_com_axvyn_app_features_audiotomp3_LameMp3Encoder_nativeCreate(
        JNIEnv *env,
        jobject thiz,
        jint sample_rate,
        jint channels,
        jint bitrate_kbps) {
    (void) thiz;
    if (sample_rate <= 0 || (channels != 1 && channels != 2) || bitrate_kbps <= 0) {
        throw_illegal_arg(env, "Invalid LAME encoder parameters");
        return 0;
    }

    lame_global_flags *gfp = lame_init();
    if (gfp == NULL) {
        throw_illegal_state(env, "lame_init failed");
        return 0;
    }

    lame_set_in_samplerate(gfp, sample_rate);
    lame_set_num_channels(gfp, channels);
    lame_set_brate(gfp, bitrate_kbps);
    lame_set_mode(gfp, channels == 1 ? MONO : STEREO);
    lame_set_VBR(gfp, vbr_off);
    lame_set_quality(gfp, 2);

    if (lame_init_params(gfp) < 0) {
        lame_close(gfp);
        throw_illegal_state(env, "lame_init_params failed");
        return 0;
    }

    return (jlong) (intptr_t) gfp;
}

JNIEXPORT jint JNICALL
Java_com_axvyn_app_features_audiotomp3_LameMp3Encoder_nativeEncode(
        JNIEnv *env,
        jobject thiz,
        jlong handle,
        jbyteArray pcm_data,
        jint bytes_read,
        jbyteArray mp3_out) {
    (void) thiz;
    lame_global_flags *gfp = (lame_global_flags *) (intptr_t) handle;
    if (gfp == NULL) {
        throw_illegal_state(env, "LAME encoder is not initialized");
        return -1;
    }
    if (pcm_data == NULL || mp3_out == NULL || bytes_read < 0) {
        throw_illegal_arg(env, "Invalid encode buffers");
        return -1;
    }

    jsize pcm_len = (*env)->GetArrayLength(env, pcm_data);
    if (bytes_read > pcm_len) bytes_read = pcm_len;
    if ((bytes_read % 2) != 0) bytes_read -= 1;
    if (bytes_read <= 0) return 0;

    jbyte *pcm_bytes = (*env)->GetByteArrayElements(env, pcm_data, NULL);
    jbyte *mp3_bytes = (*env)->GetByteArrayElements(env, mp3_out, NULL);
    if (pcm_bytes == NULL || mp3_bytes == NULL) {
        if (pcm_bytes != NULL) (*env)->ReleaseByteArrayElements(env, pcm_data, pcm_bytes, JNI_ABORT);
        if (mp3_bytes != NULL) (*env)->ReleaseByteArrayElements(env, mp3_out, mp3_bytes, JNI_ABORT);
        throw_illegal_state(env, "Could not access JNI buffers");
        return -1;
    }

    int channels = lame_get_num_channels(gfp);
    int samples_per_channel = (bytes_read / 2) / (channels > 0 ? channels : 1);
    jsize mp3_capacity = (*env)->GetArrayLength(env, mp3_out);

    int encoded = lame_encode_buffer_interleaved(
            gfp,
            (short *) pcm_bytes,
            samples_per_channel,
            (unsigned char *) mp3_bytes,
            mp3_capacity);

    (*env)->ReleaseByteArrayElements(env, pcm_data, pcm_bytes, JNI_ABORT);
    (*env)->ReleaseByteArrayElements(env, mp3_out, mp3_bytes, 0);

    if (encoded < 0) {
        throw_illegal_state(env, "LAME encoding failed");
        return -1;
    }
    return encoded;
}

JNIEXPORT jint JNICALL
Java_com_axvyn_app_features_audiotomp3_LameMp3Encoder_nativeFlush(
        JNIEnv *env,
        jobject thiz,
        jlong handle,
        jbyteArray mp3_out) {
    (void) thiz;
    lame_global_flags *gfp = (lame_global_flags *) (intptr_t) handle;
    if (gfp == NULL) {
        throw_illegal_state(env, "LAME encoder is not initialized");
        return -1;
    }
    if (mp3_out == NULL) {
        throw_illegal_arg(env, "Invalid MP3 output buffer");
        return -1;
    }

    jbyte *mp3_bytes = (*env)->GetByteArrayElements(env, mp3_out, NULL);
    if (mp3_bytes == NULL) {
        throw_illegal_state(env, "Could not access JNI buffer");
        return -1;
    }

    jsize mp3_capacity = (*env)->GetArrayLength(env, mp3_out);
    int encoded = lame_encode_flush(gfp, (unsigned char *) mp3_bytes, mp3_capacity);

    (*env)->ReleaseByteArrayElements(env, mp3_out, mp3_bytes, 0);

    if (encoded < 0) {
        throw_illegal_state(env, "LAME flush failed");
        return -1;
    }
    return encoded;
}

JNIEXPORT void JNICALL
Java_com_axvyn_app_features_audiotomp3_LameMp3Encoder_nativeClose(
        JNIEnv *env,
        jobject thiz,
        jlong handle) {
    (void) env;
    (void) thiz;
    lame_global_flags *gfp = (lame_global_flags *) (intptr_t) handle;
    if (gfp != NULL) {
        lame_close(gfp);
    }
}
