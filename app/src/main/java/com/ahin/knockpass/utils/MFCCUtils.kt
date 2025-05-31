package com.ahin.knockpass.utils

import android.content.Context
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

object MFCCUtils {

    fun extractPeakWindowMFCC(file: File, preFrames: Int = 10, postFrames: Int = 30): Array<FloatArray>? {
        val mfccList = mutableListOf<FloatArray>()
        val c1List = mutableListOf<Float>()
        try {
            val reader = BufferedReader(file.reader())
            val header = reader.readLine()  // Skip header

            // Read CSV rows
            reader.forEachLine { line ->
                val values = line.split(",").map { it.toFloatOrNull() ?: 0f }
                if (values.isNotEmpty()) {
                    mfccList.add(values.toFloatArray())
                    c1List.add(values[0])
                }
            }

            // Find last peak index in c1
            val peakStarts = mutableListOf<Int>()
            var inPeak = false
            for (i in c1List.indices) {
                if (c1List[i] > 0f) {
                    if (!inPeak) {
                        peakStarts.add(i)
                        inPeak = true
                    }
                } else {
                    inPeak = false
                }
            }

//            if (peakStarts.isEmpty()) return null
            if (peakStarts.isEmpty()) {
                println("⚠️ No peak detected in: ${file.name}")
                return null
            }
            val lastPeak = peakStarts.last()
            val start = maxOf(0, lastPeak - preFrames)
            val end = minOf(mfccList.size, lastPeak + postFrames)

            val sliced = mfccList.subList(start, end).toMutableList()

            // Padding
            val totalLen = preFrames + postFrames
            while (sliced.size < totalLen) {
                sliced.add(FloatArray(mfccList[0].size))  // Zero padding
            }

            return sliced.toTypedArray()

        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}