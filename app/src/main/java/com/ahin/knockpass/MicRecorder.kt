package com.ahin.knockpass

import android.media.AudioFormat
import android.media.AudioRecord
import android.util.Log
import android.media.MediaRecorder


class MicRecorder {

    private val sampleRate = 44100
    private val bufferSize = AudioRecord.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )

    private val recorder = AudioRecord(
        MediaRecorder.AudioSource.MIC,
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
        bufferSize
    )

    private var isRecording = false

    fun startRecording() {
        recorder.startRecording()
        isRecording = true

        Thread {
            val buffer = ShortArray(bufferSize)
            while (isRecording) {
                val read = recorder.read(buffer, 0, buffer.size)
                if (read > 0) {
                    // 오디오 데이터를 처리하거나 저장
                    Log.d("Microphone", "Audio data collected: $read samples")
                }
            }
        }.start()
    }

    fun stopRecording() {
        isRecording = false
        recorder.stop()
        recorder.release()
    }
}
