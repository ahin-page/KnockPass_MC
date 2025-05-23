package com.ahin.knockpass

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity

class SensorActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null

    private lateinit var audio: MicRecorder  // 녹음 클래스 (이전 MicRecorder)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sensor)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        audio = MicRecorder()  // 오디오 초기화
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST)
        }
        gyroscope?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST)
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


    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
