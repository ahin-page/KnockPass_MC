package com.ahin.knockpass.utils

import android.app.Activity
import android.content.Context
import android.os.Environment
import android.widget.Toast
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

fun saveMFCCToCSV(context: Context, mfccData: Array<FloatArray>, filename: String) {
    val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
    if (dir?.exists() == false) dir.mkdirs()

    val file = File(dir, "$filename.csv")

    try {
        FileWriter(file).use { writer ->
            val header = (1..(mfccData[0].size)).joinToString(",") { "c$it" }
            writer.write("$header\n")

            // Write each MFCC vector
            for (frame in mfccData) {
                writer.write(frame.joinToString(",") + "\n")
            }
        }
        (context as? Activity)?.runOnUiThread {
            Toast.makeText(context, "파일 저장 완료", Toast.LENGTH_SHORT).show()
        }

    } catch (e: Exception) {
        (context as? Activity)?.runOnUiThread {
            Toast.makeText(context, "MFCC 저장 실패: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
}

fun reshapeMFCC(input: Array<FloatArray>): Array<Array<Array<FloatArray>>> {
    val numFrames = input.size
    val numCoeffs = input[0].size

    return Array(1) { // batch size = 1
        Array(numFrames) { i ->
            Array(numCoeffs) { j ->
                FloatArray(1) { input[i][j] } // [coeff] → [coeff][1]
            }
        }
    }
}
