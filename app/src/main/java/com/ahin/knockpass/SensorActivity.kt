package com.ahin.knockpass

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.*
import android.icu.text.SimpleDateFormat
import android.os.Bundle
import android.os.Environment
import android.widget.Button
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileWriter
import java.util.*

data class SensorLog(
    val timestamp: Long,
    val type: String,
    val x: Float,
    val y: Float = 0f,
    val z: Float = 0f
)

class SensorActivity : ComponentActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private lateinit var micRecorder: MicRecorder
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null
    private val sensorDataList = mutableListOf<SensorLog>()
    private var isRecording = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sensor)

        requestPermissions()

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        micRecorder = MicRecorder { if (isRecording) sensorDataList.add(it) }

        // 버튼 연결
        findViewById<Button>(R.id.btnStart).setOnClickListener {
            isRecording = true
            startSensors()
            micRecorder.start(this)
            Toast.makeText(this, "수집 시작", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnStop).setOnClickListener {
            isRecording = false
            stopSensors()
            micRecorder.stop()
            saveToCSV(this)
            Toast.makeText(this, "저장 완료", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestPermissions() {
        val perms = arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        if (perms.any { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }) {
            ActivityCompat.requestPermissions(this, perms, 1001)
        }
    }

    private fun startSensors() {
        accelerometer?.also { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        gyroscope?.also    { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    private fun stopSensors() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (!isRecording || event == null) return
        val ts = System.currentTimeMillis()
        val type = when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> "Accelerometer"
            Sensor.TYPE_GYROSCOPE     -> "Gyroscope"
            else                      -> return
        }
        sensorDataList.add(SensorLog(ts, type, event.values[0], event.values[1], event.values[2]))
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // --- CSV 저장 책임만 담당 ----------------------
    private fun saveToCSV(context: Context) {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fn = "sensor_data_$stamp.csv"
        val dir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)?.apply { if (!exists()) mkdirs() }
        val file = File(dir, fn)

        try {
            FileWriter(file).use { w ->
                w.write("timestamp,sensor_type,x,y,z\n")
                sensorDataList.forEach { e ->
                    w.write("${e.timestamp},${e.type},${e.x},${e.y},${e.z}\n")
                }
            }
            sensorDataList.clear()
        } catch (e: Exception) {
            Toast.makeText(context, "CSV 저장 실패: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
}