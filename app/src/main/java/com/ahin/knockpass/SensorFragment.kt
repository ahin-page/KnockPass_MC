package com.ahin.knockpass

import com.ahin.knockpass.utils.saveMFCCToCSV
import MFCCProcessor
import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.hardware.*
import android.icu.text.SimpleDateFormat
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import java.io.File
import java.io.FileWriter
import java.util.*
import androidx.appcompat.app.AlertDialog

data class SensorLog(
    val timestamp: Long,
    val type: String,
    val x: Float,
    val y: Float = 0f,
    val z: Float = 0f
)

class SensorFragment : Fragment(), SensorEventListener {

    private lateinit var prefs: SharedPreferences
    private var currentObject = ""
    private var currentKnockNumber = 0
    private lateinit var sensorManager: SensorManager
    private lateinit var micRecorder: MicRecorder
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null
    private val sensorDataList = mutableListOf<SensorLog>()
    private var isRecording = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_sensor, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requestPermissions()
        prefs = requireContext().getSharedPreferences("sensor_prefs", Context.MODE_PRIVATE)
        sensorManager = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        micRecorder = MicRecorder(onRawAudioData = { floatArray ->
            if (isRecording) {
                val energy = floatArray.map { it * it }.average().let { Math.sqrt(it).toFloat() }
                val timestamp = System.currentTimeMillis()
                sensorDataList.add(SensorLog(timestamp, "Microphone", energy))
            }
        })
        view.findViewById<Button>(R.id.btnStart).setOnClickListener {
            showFilenameDialog()
        }

        view.findViewById<Button>(R.id.btnStop).setOnClickListener {
            isRecording = false
            stopSensors()
            micRecorder.stop()
            val filename = generateFilename()

            // 1. 센서 데이터 CSV 저장
            saveToCSV(requireContext(), filename)

            // 2. MFCC 저장 - 오디오 데이터를 MFCC로 변환 후 CSV 저장
            val processor = MFCCProcessor()
            val audioFloatArray = micRecorder.getRawAudio()
            val mfccFeatures = processor.extractMFCC(audioFloatArray)
            saveMFCCToCSV(requireContext(), mfccFeatures, "${filename}_mfcc")

            Toast.makeText(requireContext(), "저장 완료", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestPermissions() {
        val perms = arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        if (perms.any { ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED }) {
            ActivityCompat.requestPermissions(requireActivity(), perms, 1001)
        }
    }
    private fun showFilenameDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_filename, null, false)

        val objectInput = dialogView.findViewById<AutoCompleteTextView>(R.id.autoObject)
        val knockInput = dialogView.findViewById<EditText>(R.id.etKnockNumber)

        // Load history
        val history = prefs.getStringSet("object_history", emptySet())?.toList() ?: emptyList()
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, history)
        objectInput.setAdapter(adapter)

        AlertDialog.Builder(requireContext())
            .setTitle("파일 정보 입력")
            .setView(dialogView)
            .setPositiveButton("시작") { _, _ ->
                currentObject = objectInput.text.toString().ifBlank { "UnknownObject" }
                currentKnockNumber = knockInput.text.toString().toIntOrNull() ?: 0

                // Save history
                prefs.edit().putStringSet(
                    "object_history",
                    history.plus(currentObject).toSet()
                ).apply()

                isRecording = true
                startSensors()
                micRecorder.start(requireContext())
                Toast.makeText(requireContext(), "수집 시작: $currentObject #$currentKnockNumber", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun generateFilename(): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return "${stamp}_${currentObject}_$currentKnockNumber"
    }


    private fun startSensors() {
        accelerometer?.also { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        gyroscope?.also { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    private fun stopSensors() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (!isRecording || event == null) return
        val ts = System.currentTimeMillis()
        val type = when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> "Accelerometer"
            Sensor.TYPE_GYROSCOPE -> "Gyroscope"
            else -> return
        }
        sensorDataList.add(SensorLog(ts, type, event.values[0], event.values[1], event.values[2]))
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun saveToCSV(context: Context, filename: String) {
        val fn = "$filename.csv"
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)?.apply { if (!exists()) mkdirs() }
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
