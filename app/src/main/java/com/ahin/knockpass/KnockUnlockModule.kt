package com.ahin.knockpass

import android.content.Context
import android.content.res.AssetFileDescriptor
import androidx.core.content.ContentProviderCompat.requireContext
import com.ahin.knockpass.utils.MFCCUtils
import org.tensorflow.lite.Interpreter
import java.io.*
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.sqrt

object KnockUnlockModule {
    private lateinit var tflite: Interpreter

    fun initModel(context: Context, modelName: String = "model.tflite") {
        tflite = Interpreter(loadModelFile(context, modelName))
    }

    private fun loadModelFile(context: Context, modelName: String): MappedByteBuffer {
        val fileDescriptor: AssetFileDescriptor = context.assets.openFd(modelName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }
    fun readMFCCfromCSV(file: File): Array<Array<Array<FloatArray>>> {
            val sliced: Array<FloatArray> = MFCCUtils.extractPeakWindowMFCC(file) ?: return emptyArray()

            val numFrames = 40
            val numCoeffs = 13

            // MFCC dimension 맞추기 (프레임 수는 extract에서 이미 보장됨)
            val processed = sliced.map { frame ->
                if (frame.size >= numCoeffs) frame.take(numCoeffs).toFloatArray()
                else FloatArray(numCoeffs) { i -> if (i < frame.size) frame[i] else 0f }
            }
            println("MFCC size: ${sliced.size}")
            // [1, 40, 13, 1] 구조로 reshape
            return Array(1) {
                Array(numFrames) { i ->
                    Array(numCoeffs) { j ->
                        FloatArray(1) { processed[i][j] }
                    }
                }
            }
    }
    fun getEmbedding(mfccInput: Array<Array<Array<FloatArray>>>): FloatArray {
        val output = Array(1) { FloatArray(128) }
        tflite.run(mfccInput, output)
        return output[0]
    }

    fun computeReferenceVector(embeddings: List<FloatArray>): FloatArray {
        val dim = embeddings[0].size
        val avg = FloatArray(dim) { i -> embeddings.map { it[i] }.average().toFloat() }

        val variances = embeddings.map {
            it.zip(avg) { a, b -> (a - b) * (a - b) }.sum()
        }

        val top2Indices = variances.withIndex().sortedByDescending { it.value }.take(2).map { it.index }
        val filtered = embeddings.filterIndexed { i, _ -> i !in top2Indices }

        return FloatArray(dim) { i -> filtered.map { it[i] }.average().toFloat() }
    }

    fun cosineSimilarity(v1: FloatArray, v2: FloatArray): Float {
        val dot = v1.zip(v2) { a, b -> a * b }.sum()
        val norm1 = sqrt(v1.map { it * it }.sum())
        val norm2 = sqrt(v2.map { it * it }.sum())
        return dot / (norm1 * norm2 + 1e-6f)
    }


    fun shouldUnlock(current: FloatArray, reference: FloatArray, threshold: Float = 0.90f): Boolean {
        return cosineSimilarity(current, reference) >= threshold
    }

    fun saveReferenceVector(context: Context, vector: FloatArray) {
        val file = File(context.filesDir, "reference_vector.csv")
        file.writeText(vector.joinToString(","))
    }

    fun loadReferenceVector(context: Context): FloatArray? {
        val file = File(context.filesDir, "reference_vector.csv")
        if (!file.exists()) return null

        return try {
            file.readText().split(",").map { it.toFloat() }.toFloatArray()
        } catch (e: Exception) {
            null
        }
    }
}
