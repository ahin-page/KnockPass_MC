package com.ahin.knockpass.utils

import android.content.Context
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
object MFCCUtils {

    // 1️⃣ CSV 파일로부터 MFCC 읽기
    fun readMFCCFromCSV(file: File): Array<FloatArray>? {
        val mfccList = mutableListOf<FloatArray>()
        try {
            val reader = BufferedReader(file.reader())
            reader.readLine() // Skip header

            reader.forEachLine { line ->
                val values = line.split(",").map { it.toFloatOrNull() ?: 0f }
                if (values.isNotEmpty()) {
                    mfccList.add(values.toFloatArray())
                }
            }
            return mfccList.toTypedArray()
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    // 2️⃣ 핵심 피크 추출 로직
    fun extractPeakWindow(
        mfccData: Array<FloatArray>,
        preFrames: Int = 10,
        postFrames: Int = 30
    ): Array<FloatArray>? {
        if (mfccData.isEmpty() || mfccData[0].isEmpty()) return null

        val c1List = mfccData.map { it[0] }
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
        println(c1List)

        if (peakStarts.isEmpty()) {
            println("No peak")
            return null
        }

        val lastPeak = peakStarts.last()
        val start = maxOf(0, lastPeak - preFrames)
        val end = minOf(mfccData.size, lastPeak + postFrames)

        val sliced = mfccData.slice(start until end).toMutableList()

        val totalLen = preFrames + postFrames
        while (sliced.size < totalLen) {
            sliced.add(FloatArray(mfccData[0].size)) // Zero padding
        }

        return sliced.toTypedArray()
    }

    // 3️⃣ 파일 기반: CSV 파일을 읽고 → extract
    fun extractPeakWindowMFCC(file: File, preFrames: Int = 10, postFrames: Int = 30): Array<FloatArray>? {
        val raw = readMFCCFromCSV(file) ?: return null
        return extractPeakWindow(raw, preFrames, postFrames)
    }

    // 4️⃣ MFCC 배열 기반 직접 호출
    fun extractPeakWindowMFCC(mfccData: Array<FloatArray>, preFrames: Int = 10, postFrames: Int = 30): Array<FloatArray>? {
        return extractPeakWindow(mfccData, preFrames, postFrames)
    }
}
