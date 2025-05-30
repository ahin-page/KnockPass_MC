package com.ahin.knockpass

import android.hardware.*
import android.os.Bundle
import android.view.*
import android.widget.Button
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

class SettingFragment : Fragment(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private val knockPattern = mutableListOf<Float>()
    private var isRecording = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_setting, container, false)

        sensorManager = requireContext().getSystemService(SensorManager::class.java)
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        val btnStart = view.findViewById<Button>(R.id.btnStart)
        val btnStop = view.findViewById<Button>(R.id.btnStop)

        btnStart.setOnClickListener {
            knockPattern.clear()
            isRecording = true
            accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
            Toast.makeText(requireContext(), "녹음 시작", Toast.LENGTH_SHORT).show()
        }

        btnStop.setOnClickListener {
            isRecording = false
            sensorManager.unregisterListener(this)
           // KnockPatternManager.savePattern(requireContext(), knockPattern)
            Toast.makeText(requireContext(), "암호가 설정되었습니다. Test 탭에서 잠금 해제를 시도하세요.", Toast.LENGTH_LONG).show()
        }

        return view
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (!isRecording || event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return
        val magnitude = event.values.map { it * it }.sum().let { Math.sqrt(it.toDouble()).toFloat() }
        knockPattern.add(magnitude)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}