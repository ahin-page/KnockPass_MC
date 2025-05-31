package com.ahin.knockpass

import MFCCProcessor
import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.ahin.knockpass.utils.saveMFCCToCSV

class TestFragment : Fragment() {

    private lateinit var tvStatus: TextView
    private var recorder: AudioRecord? = null
    private var isRecording = false
    private val sampleRate = 16000
    private val bufferSize = AudioRecord.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_test, container, false)
        tvStatus = view.findViewById(R.id.tvTestStatus)

        view.findViewById<Button>(R.id.btnTestStart).setOnClickListener {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.RECORD_AUDIO), 1001)
                return@setOnClickListener
            }
            recordAndExtractMFCC()
        }

        return view
    }

    private fun recordAndExtractMFCC() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(requireContext(), "마이크 권한이 필요합니다", Toast.LENGTH_SHORT).show()
            return
        }

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            Toast.makeText(requireContext(), "녹음 초기화 실패", Toast.LENGTH_SHORT).show()
            return
        }

        val audioBuffer = ShortArray(bufferSize * 5)
        recorder.startRecording()
        isRecording = true
        tvStatus.text = "녹음 중..."

        Thread {
            val read = recorder.read(audioBuffer, 0, audioBuffer.size)
            recorder.stop()
            recorder.release()
            isRecording = false

            if (read > 0) {
                val floatData = FloatArray(read) { i -> audioBuffer[i] / 32768.0f }
                val mfccProcessor = MFCCProcessor()
                val mfcc = mfccProcessor.extractMFCC(floatData)

                requireActivity().runOnUiThread {
                    saveMFCCToCSV(requireContext(), mfcc, "test_mfcc")
                    tvStatus.text = "테스트 완료 및 저장됨"
                }
            } else {
                requireActivity().runOnUiThread {
                    tvStatus.text = "녹음 실패"
                }
            }
        }.start()
    }
}