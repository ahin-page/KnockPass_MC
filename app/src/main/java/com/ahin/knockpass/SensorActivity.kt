package com.ahin.knockpass

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType.Companion.Text
import androidx.compose.ui.unit.dp
import com.ahin.knockpass.ui.theme.KnockpassTheme
import java.io.File

//data class SensorSample(
//    val timestamp: Long,
//    val ax: Float, val ay: Float, val az: Float,
//    val gx: Float, val gy: Float, val gz: Float
//)

class SensorActivity : ComponentActivity(), SensorEventListener {


    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null
    private lateinit var audio: MicRecorder  // 녹음 클래스 (이전 MicRecorder)

    data class SensorSample(
        val timestamp: Long,
        val ax: Float, val ay: Float, val az: Float,
        val gx: Float, val gy: Float, val gz: Float
    )
    // 센서 측정값 저장용
    private var ax = 0f
    private var ay = 0f
    private var az = 0f

    private var gx = 0f
    private var gy = 0f
    private var gz = 0f

    // 마지막 가속도 저장
    private var lastAccel = floatArrayOf(0f, 0f, 0f)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        audio = MicRecorder()

        setContent {
            var label by remember { mutableStateOf("") }
            var isRecording by remember { mutableStateOf(false) }

            KnockpassTheme {
                SensorControlUI(
                    label = label,
                    isRecording = isRecording,
                    onLabelChange = { label = it },
                    onToggleRecording = {
                        isRecording = !isRecording
                        if (isRecording) {
                            startSensors()
                            audio.startRecording()
                        } else {
                            stopSensors()
                            audio.stopRecording()
                            saveSensorDataToFile(label)
                        }
                    }
                )
            }
        }
    }


    override fun onResume() {
        super.onResume()
        accelerometer?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        gyroscope?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        audio.startRecording()
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        audio.stopRecording()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event != null) {
            val type = event.sensor.type
            val values = event.values

            if (type == Sensor.TYPE_ACCELEROMETER) {
                val ax = values[0]
                val ay = values[1]
                val az = values[2]
                Log.d("Accelerometer", "X:$ax, Y:$ay, Z:$az")
            } else if (type == Sensor.TYPE_GYROSCOPE) {
                val gx = values[0]
                val gy = values[1]
                val gz = values[2]
                Log.d("Gyroscope", "X:$gx, Y:$gy, Z:$gz")
            }
        }
    }
    private val sensorDataList = mutableListOf<SensorSample>()

    fun saveSensorDataToFile(label: String) {
        val timestamp = System.currentTimeMillis()
        val fileName = "object_${label}_${timestamp}.csv"
        val file = File(filesDir, fileName)

        file.bufferedWriter().use { writer ->
            writer.write("label,timestamp,ax,ay,az,gx,gy,gz\n")
            for (sample in sensorDataList) {
                writer.write(
                    "$label,${sample.timestamp},${sample.ax},${sample.ay},${sample.az},${sample.gx},${sample.gy},${sample.gz}\n"
                )
            }
        }

        Log.d("FileSave", "Saved ${sensorDataList.size} samples to ${file.absolutePath}")
        Toast.makeText(this, "Saved to $fileName", Toast.LENGTH_SHORT).show()

        // 저장 후 초기화
        sensorDataList.clear()
    }

    private fun startSensors() {
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    private fun stopSensors() {
        sensorManager.unregisterListener(this)
    }



    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
@Composable
fun SensorControlUI(
    label: String,
    isRecording: Boolean,
    onLabelChange: (String) -> Unit,
    onToggleRecording: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Enter Object Label")
        TextField(
            value = label,
            onValueChange = onLabelChange,
            label = { Text("Label") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onToggleRecording,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isRecording) "Stop & Save" else "Start Recording")
        }
    }
}
