package com.ahin.knockpass.utils

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
            // Header (optional)
            val header = (1..(mfccData[0].size)).joinToString(",") { "c$it" }
            writer.write("$header\n")

            // Write each MFCC vector
            for (frame in mfccData) {
                writer.write(frame.joinToString(",") + "\n")
            }
        }
        Toast.makeText(context, "MFCC 저장 완료: ${file.name}", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "MFCC 저장 실패: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
    }
}