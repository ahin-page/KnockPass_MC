package com.ahin.knockpass

import android.hardware.*
import android.os.Bundle
import android.view.*
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment

class SettingFragment : Fragment(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var isRecording = false
    private val allPatterns = mutableListOf<List<Float>>()  // 여러 번 기록된 패턴 저장
    private var currentPattern = mutableListOf<Float>()     // 한 번의 패턴 기록
    private lateinit var tvProgress: TextView

    private val REQUIRED_ATTEMPTS = 5

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_setting, container, false)

        sensorManager = requireContext().getSystemService(SensorManager::class.java)
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        val btnStart = view.findViewById<Button>(R.id.btnStart)
        val btnStop = view.findViewById<Button>(R.id.btnStop)
        tvProgress = view.findViewById(R.id.tvProgress)

        tvProgress.text = "0 / $REQUIRED_ATTEMPTS"

        btnStart.setOnClickListener {
            if (allPatterns.size >= REQUIRED_ATTEMPTS) {
                Toast.makeText(requireContext(), "이미 5회 완료되었습니다", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            currentPattern.clear()
            isRecording = true
            accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
            Toast.makeText(requireContext(), "녹음 시작 (${allPatterns.size + 1}/$REQUIRED_ATTEMPTS)", Toast.LENGTH_SHORT).show()
        }

        btnStop.setOnClickListener {
            if (!isRecording) return@setOnClickListener
            isRecording = false
            sensorManager.unregisterListener(this)

            if (currentPattern.isNotEmpty()) {
                allPatterns.add(currentPattern.toList())
                tvProgress.text = "${allPatterns.size} / $REQUIRED_ATTEMPTS"
                Toast.makeText(requireContext(), "녹음 완료 (${allPatterns.size}/$REQUIRED_ATTEMPTS)", Toast.LENGTH_SHORT).show()
            }

            if (allPatterns.size == REQUIRED_ATTEMPTS) {
                // 평균값 계산 후 저장
                val averagePattern = computeAveragePattern(allPatterns)
                KnockPatternManager.savePattern(requireContext(), averagePattern)
                Toast.makeText(requireContext(), "암호가 설정되었습니다.\nTest 탭에서 잠금 해제를 시도하세요.", Toast.LENGTH_LONG).show()
            }
        }

        return view
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (!isRecording || event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return
        val magnitude = event.values.map { it * it }.sum().let { Math.sqrt(it.toDouble()).toFloat() }
        currentPattern.add(magnitude)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun computeAveragePattern(patterns: List<List<Float>>): List<Float> {
        val maxLength = patterns.maxOfOrNull { it.size } ?: return emptyList()

        return List(maxLength) { i ->
            val valuesAtI = patterns.mapNotNull { it.getOrNull(i) }
            if (valuesAtI.isNotEmpty()) valuesAtI.average().toFloat() else 0f
        }
    }
}