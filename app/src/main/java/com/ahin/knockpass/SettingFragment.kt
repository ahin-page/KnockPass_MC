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
import java.io.File
import java.io.FileWriter

class SettingFragment : Fragment() {

    private lateinit var tvProgress: TextView
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button

    private val REQUIRED_ATTEMPTS = 5
    private var attemptCount = 0

    private val sampleRate = 16000
    private val bufferSize = AudioRecord.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )

    // 실제 AudioRecord 객체와 오디오 데이터를 담을 리스트
    private var recorder: AudioRecord? = null
    private var isRecording = false
    private val audioDataList = mutableListOf<Float>()
    private lateinit var btnReset: Button

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_setting, container, false)
        tvProgress = view.findViewById(R.id.tvProgress)
        btnStart   = view.findViewById(R.id.btnStart)
        btnStop    = view.findViewById(R.id.btnStop)
        btnReset = view.findViewById(R.id.btnReset)

        // 초기 상태: 0 / 5
        attemptCount = getRecordedAttemptCount()
        tvProgress.text = "$attemptCount / $REQUIRED_ATTEMPTS"

        btnStart.setOnClickListener {
            if (attemptCount >= REQUIRED_ATTEMPTS) {
                Toast.makeText(requireContext(),
                    "이미 $REQUIRED_ATTEMPTS 회 녹음이 완료되었습니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 녹음을 이미 진행 중이라면 재진입 막기
            if (isRecording) {
                Toast.makeText(requireContext(), "이미 녹음 중입니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 마이크 권한 검사
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    requireActivity(),
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                    1001
                )
                return@setOnClickListener
            }

            // 권한이 이미 허용된 상태라면 녹음 시작
            startRecording()
        }

        btnStop.setOnClickListener {
            if (!isRecording) {
                Toast.makeText(requireContext(), "먼저 녹음을 시작하세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            stopRecordingAndSave()
        }
        btnReset.setOnClickListener {
            val dir = requireContext().getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS)
            if (dir != null && dir.exists()) {
                val deletedCount = dir.listFiles { _, name ->
                    name.matches(Regex("\\d+_lock_audio_pattern(_mfcc)?\\.csv"))
                }?.count { it.delete() } ?: 0

                attemptCount = 0
                tvProgress.text = "$attemptCount / $REQUIRED_ATTEMPTS"
                Toast.makeText(requireContext(), "재설정합니다.", Toast.LENGTH_SHORT).show()
            }
        }
        return view
    }

    /**
     * “녹음 시작” 버튼을 누르면 호출됩니다.
     * - AudioRecord 를 초기화하고, 백그라운드 스레드에서 지속적으로
     *   마이크 입력을 읽어 audioDataList 에 담아 둡니다.
     * - UI 상에는 “녹음 중... (현재 시도+1/5)” 로 표시합니다.
     */
    private fun startRecording() {
        try {
            recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (recorder?.state != AudioRecord.STATE_INITIALIZED) {
                Toast.makeText(requireContext(),
                    "녹음 초기화에 실패했습니다.", Toast.LENGTH_SHORT).show()
                recorder?.release()
                recorder = null
                return
            }

            // 녹음 플래그 설정 및 기존 데이터 초기화
            isRecording = true
            audioDataList.clear()

            // UI 업데이트: “녹음 중...”
            tvProgress.text = "녹음 중... (${attemptCount + 1}/$REQUIRED_ATTEMPTS)"

            // 백그라운드 스레드에서 오디오 샘플을 읽어 오디오 리스트에 저장
            Thread {
                try {
                    recorder?.startRecording()
                    val shortBuffer = ShortArray(bufferSize)
                    while (isRecording) {
                        val readCount = recorder?.read(shortBuffer, 0, bufferSize) ?: 0
                        if (readCount > 0) {
                            // Short → Float 정규화 (−1.0 ~ +1.0)
                            val floatFrame = FloatArray(readCount) { i ->
                                shortBuffer[i] / 32768.0f
                            }
                            synchronized(audioDataList) {
                                audioDataList.addAll(floatFrame.toList())
                            }
                        }
                    }
                } catch (se: SecurityException) {
                    // 권한 문제 발생 시 UI 스레드에서 알림
                    requireActivity().runOnUiThread {
                        Toast.makeText(requireContext(),
                            "녹음 중 권한 문제가 발생했습니다.", Toast.LENGTH_LONG).show()
                        tvProgress.text = "$attemptCount / $REQUIRED_ATTEMPTS"
                    }
                }
            }.start()

        } catch (e: SecurityException) {
            // AudioRecord 객체 생성 중 예외 발생 시
            Toast.makeText(requireContext(),
                "AudioRecord 생성 중 권한 오류가 발생했습니다.", Toast.LENGTH_LONG).show()
            tvProgress.text = "$attemptCount / $REQUIRED_ATTEMPTS"
            isRecording = false
        }
    }

    /**
     * “녹음 끝내기” 버튼을 누르면 호출됩니다.
     * 1) 백그라운드 스레드에서 isRecording = false 로 표시하여 녹음 종료
     * 2) AudioRecord 를 정리(stop, release)
     * 3) audioDataList 에 모인 모든 Float 오디오 샘플을 MFCC 로 변환하여 CSV로 저장
     * 4) 마찬가지로 raw audio 값 자체를 CSV로 저장
     * 5) attemptCount 증가 → UI(진행 상황) 갱신 → 토스트 알림
     * 6) 5회 녹음이 모두 끝나면 별도 토스트로 안내
     */
    private fun stopRecordingAndSave() {
        // 녹음 중지
        isRecording = false
        try {
            recorder?.stop()
            recorder?.release()
        } catch (_: Exception) {
            // 예외 무시
        }
        recorder = null

        // 동기화하여 녹음 데이터를 로컬로 복사
        val floatData: FloatArray = synchronized(audioDataList) {
            audioDataList.toFloatArray()
        }

        if (floatData.isEmpty()) {
            Toast.makeText(requireContext(),
                "녹음된 데이터가 없습니다.", Toast.LENGTH_SHORT).show()
            tvProgress.text = "$attemptCount / $REQUIRED_ATTEMPTS"
            return
        }

        // (1) Raw audio 값을 CSV로 저장
        val rawFilename = "${attemptCount + 1}_lock_audio_pattern.csv"
        saveRawAudioToCSV(floatData, rawFilename)

        // (2) MFCC로 변환 후 CSV로 저장
        val processor = MFCCProcessor()
        val mfccResult = processor.extractMFCC(floatData)
        val mfccFilename = "${attemptCount + 1}_lock_audio_pattern_mfcc"
        saveMFCCToCSV(requireContext(), mfccResult, mfccFilename)

        // 시도 횟수 증가 및 UI 갱신
        attemptCount++
        tvProgress.text = "$attemptCount / $REQUIRED_ATTEMPTS"
        Toast.makeText(requireContext(),
            "녹음 완료 ($attemptCount/$REQUIRED_ATTEMPTS)", Toast.LENGTH_SHORT).show()

        if (attemptCount == REQUIRED_ATTEMPTS) {
            Toast.makeText(requireContext(),
                "암호 패턴 $REQUIRED_ATTEMPTS 회 모두 설정되었습니다.\nTest 탭으로 이동하세요.",
                Toast.LENGTH_LONG).show()
        }
    }

    /**
     * 원시 오디오(FloatArray)를 순차적으로 CSV에 저장하는 헬퍼 함수
     * 파일 경로: {앱 내부 Documents}/{filename}
     * CSV 헤더: value
     */
    private fun saveRawAudioToCSV(data: FloatArray, filename: String) {
        try {
            val dir = requireContext().getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS)
            if (dir?.exists() == false) dir.mkdirs()

            val file = File(dir, filename)
            FileWriter(file).use { writer ->
                writer.write("value\n")
                data.forEach { value -> writer.write("$value\n") }
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(),
                "원시 오디오 CSV 저장 실패: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
    private fun getRecordedAttemptCount(): Int {
        val dir = requireContext().getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS)
        return dir?.listFiles { _, name ->
            name.matches(Regex("\\d+_lock_audio_pattern_mfcc\\.csv"))
        }?.size ?: 0
    }

}