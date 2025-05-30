package com.ahin.knockpass

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat

class MicRecorder(
    private val onRawAudioData: (FloatArray) -> Unit
) {
    private val sampleRate = 16000
    private val bufferSize = AudioRecord.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )

    private var recorder: AudioRecord? = null
    private var isRecording = false
    private val recordedData = mutableListOf<Float>()  // ✅ 전체 raw audio 저장

    fun start(context: Context) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("MicRecorder", "RECORD_AUDIO permission not granted")
            return
        }

        recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        if (recorder?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e("MicRecorder", "AudioRecord init failed")
            return
        }

        recorder?.startRecording()
        isRecording = true
        recordedData.clear()  // ✅ 시작할 때 초기화

        Thread {
            val shortBuffer = ShortArray(bufferSize)
            while (isRecording) {
                val read = recorder?.read(shortBuffer, 0, shortBuffer.size) ?: 0
                if (read > 0) {
                    val floatData = FloatArray(read) { i -> shortBuffer[i] / 32768.0f }
                    recordedData.addAll(floatData.toList())  // ✅ 전체 저장
                    onRawAudioData(floatData)
                }
            }
        }.start()
    }

    fun stop() {
        isRecording = false
        recorder?.stop()
        recorder?.release()
        recorder = null
    }

    fun getRawAudio(): FloatArray {
        return recordedData.toFloatArray()
    }
}