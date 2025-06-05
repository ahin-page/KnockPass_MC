package com.ahin.knockpass

import MFCCProcessor
import android.Manifest
import android.content.pm.PackageManager
import android.media.*
import android.os.*
import android.view.*
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.ahin.knockpass.utils.MFCCUtils
import com.ahin.knockpass.utils.reshapeMFCC
import java.io.*

class HomeFragment : Fragment() {

    private lateinit var tvIntro: TextView
    private lateinit var tvResult: TextView
    private lateinit var btnTry: Button
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button

    private val sampleRate = 16000
    private val bufferSize = AudioRecord.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )

    private lateinit var audioRecorder: AudioRecord
    private lateinit var audioBuffer: ShortArray
    private var isRecording = false

    private val audioDataList = mutableListOf<Float>()


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_home, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvIntro = view.findViewById(R.id.tvIntro)
        tvResult = view.findViewById(R.id.tvUnlockResult)
        btnTry = view.findViewById(R.id.btnTryUnlock)

        btnStart = view.findViewById(R.id.btnStartRecording)  // 새로 추가된 버튼
        btnStop = view.findViewById(R.id.btnStopRecording)    // 새로 추가된 버튼

        tvIntro.text = "앱을 사용하려면 설정 탭에서 암호 패턴을 먼저 설정하세요."
        KnockUnlockModule.initModel(requireContext())

        btnTry.setOnClickListener {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    requireActivity(),
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                    2001
                )
                return@setOnClickListener
            }

            btnStart.visibility = View.VISIBLE
            btnStop.visibility = View.VISIBLE
            tvResult.text = "시작 버튼을 누르면 녹음이 시작됩니다."   //"시작 버튼을 누르면 녹음이 시작됩니다."
        }

        btnStart.setOnClickListener {
            startRecording()
            tvResult.text = "녹음 중입니다... 끝내기 버튼을 눌러주세요."
        }

        btnStop.setOnClickListener {
            stopRecordingAndUnlock()
        }
    }

    private fun startRecording() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(requireContext(), "마이크 권한이 필요합니다", Toast.LENGTH_SHORT).show()
            return
        }
        audioRecorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize * 5
        )
        audioBuffer = ShortArray(bufferSize * 5)

        isRecording = true
        audioDataList.clear()

        Thread {
            audioRecorder.startRecording()
            val shortBuffer = ShortArray(bufferSize)
            while (isRecording) {
                val readCount = audioRecorder.read(shortBuffer, 0, bufferSize)
                if (readCount > 0) {
                    val floatFrame = FloatArray(readCount) { i -> shortBuffer[i] / 32768.0f }
                    synchronized(audioDataList) {
                        audioDataList.addAll(floatFrame.toList())
                    }
                }
            }
        }.start()

    }

    private fun stopRecordingAndUnlock() {
        isRecording = false
        tvResult.text = "분석 중..."

        Thread {
            try {
                // Thread가 녹음을 마칠 수 있도록 잠깐 대기
                Thread.sleep(100)  // 또는 join 처리 가능

                audioRecorder.stop()
                audioRecorder.release()

                val floatData: FloatArray = synchronized(audioDataList) {
                    audioDataList.toFloatArray()
                }

                if (floatData.isEmpty()) {
                    showToast("녹음된 데이터가 없습니다.")
                    return@Thread
                }

                val mfcc = MFCCProcessor().extractMFCC(floatData)

                val docsDir = requireContext().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                val refFiles = docsDir
                    ?.listFiles { _, name -> name.endsWith("_lock_audio_pattern_mfcc.csv") }
                    ?.sortedBy { it.lastModified() }

                if (refFiles.isNullOrEmpty()) {
                    showText("Setting으로 이동해서 lock pattern을 설정하세요.")
                    return@Thread
                }

                val refEmbeddings = refFiles.mapNotNull { file ->
                    val input = KnockUnlockModule.readMFCCfromCSV(file)
                    if (input.isEmpty()) {
                        println("kipping ${file.name}: no peak window extracted.")
                        null
                    } else {
                        KnockUnlockModule.getEmbedding(input)
                    }
                }

                if (refEmbeddings.isEmpty()) {
                    showText("기준 벡터 생성 실패")
                    return@Thread
                }
                //println(mfcc.joinToString(separator = "\n") { it.joinToString(prefix = "[", postfix = "]") })

                val mfcc2 = mfcc?.let { MFCCUtils.extractPeakWindowMFCC(it) }
                if (mfcc2 == null) {
                    showText("인증 실패 (유효한 peak 없음)")
                    return@Thread
                }

                val (reference, threshold)= KnockUnlockModule.computeReferenceVector(refEmbeddings)
                println(reference)

                val current = KnockUnlockModule.getEmbedding(reshapeMFCC(mfcc2))
                val unlocked = KnockUnlockModule.shouldUnlock(current, reference,threshold)

                showText(if (unlocked) "잠금 해제 성공!" else "인증 실패")
            } catch (e: Exception) {
                e.printStackTrace()
                showText("에러 발생: ${e.localizedMessage}")
            }
        }.start()
    }


    private fun showText(msg: String) {
        requireActivity().runOnUiThread {
            tvResult.text = msg
        }
    }

    private fun showToast(msg: String) {
        requireActivity().runOnUiThread {
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }
    }
}