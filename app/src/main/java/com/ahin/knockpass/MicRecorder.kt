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
    private val onAudioSample: (SensorLog) -> Unit
) {
    private val sampleRate = 44100
    private val bufferSize = AudioRecord.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )

    private var recorder: AudioRecord? = null
    private var isRecording = false

    fun start(context: Context) {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
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

        Thread {
            val buffer = ShortArray(bufferSize)
            while (isRecording) {
                val read = recorder?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    val energy = Math.sqrt(buffer.map { it * it.toDouble() }.average()).toFloat()
                    val timestamp = System.currentTimeMillis()
                    onAudioSample(SensorLog(timestamp, "Microphone", energy))
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
}