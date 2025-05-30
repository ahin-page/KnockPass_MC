package com.ahin.knockpass

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.*
import android.icu.text.SimpleDateFormat
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
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
import androidx.core.content.FileProvider
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
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null
    private lateinit var micRecorder: MicRecorder

    private val sensorDataList = mutableListOf<SensorLog>()
    private var isRecording = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestPermissions()

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        micRecorder = MicRecorder { audioLog ->
            if (isRecording) {
                sensorDataList.add(audioLog)
            }
        }

        setContent {
            val context = LocalContext.current

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text("센서 수집", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(16.dp))

                Row {
                    Button(onClick = {
                        isRecording = true
                        startSensors()
                        micRecorder.start(context)
                        Toast.makeText(context, "수집 시작", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("시작")
                    }

                    Spacer(Modifier.width(16.dp))

                    Button(onClick = {
                        isRecording = false
                        stopSensors()
                        micRecorder.stop()
                        saveToCSV()
                        Toast.makeText(context, "저장 완료", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("끝내기")
                    }
                }
                Spacer(Modifier.height(24.dp))

                Button(onClick = {
                    sendEmailWithCSV(context)
                }) {
                    Text("CSV 이메일로 보내기")
                }
            }
        }
    }

    private fun requestPermissions() {
        val permissions = arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        if (permissions.any {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }) {
            ActivityCompat.requestPermissions(this, permissions, 1001)
        }
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

    override fun onSensorChanged(event: SensorEvent?) {
        if (!isRecording || event == null) return

        val timestamp = System.currentTimeMillis()
        val type = when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> "Accelerometer"
            Sensor.TYPE_GYROSCOPE -> "Gyroscope"
            else -> return
        }

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        sensorDataList.add(SensorLog(timestamp, type, x, y, z))
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun saveToCSV() {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "sensor_data_$timestamp.csv"
        val dir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        if (dir != null && !dir.exists()) dir.mkdirs()

        val file = File(dir, fileName)

        try {
            val writer = FileWriter(file)
            writer.write("timestamp,sensor_type,x,y,z\\n")
            for (entry in sensorDataList) {
                writer.write("\${entry.timestamp},\${entry.type},\${entry.x},\${entry.y},\${entry.z}\\n")
            }
            writer.close()
            sensorDataList.clear()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    private fun sendEmailWithCSV(context: Context) {
        val dir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        if (dir == null || !dir.exists()) {
            Toast.makeText(context, "CSV 파일 디렉토리를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val csvFiles = dir.listFiles { _, name ->
            name.endsWith(".csv")
        }

        if (csvFiles.isNullOrEmpty()) {
            Toast.makeText(context, "보낼 CSV 파일이 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val latestFile = csvFiles.maxByOrNull { it.lastModified() } ?: return
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            latestFile
        )

        val emailIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, "Sensor Data CSV")
            putExtra(Intent.EXTRA_TEXT, "첨부된 센서 데이터를 확인해주세요.")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(emailIntent, "Send email using:"))
    }
}


class MicRecorder(private val onAudioSample: (SensorLog) -> Unit) {

    private val sampleRate = 44100
    private val bufferSize = AudioRecord.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )

    private var recorder: AudioRecord? = null
    private var isRecording = false

    fun start(context: Context) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
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