import be.tarsos.dsp.AudioEvent
import be.tarsos.dsp.mfcc.MFCC
import be.tarsos.dsp.io.TarsosDSPAudioFormat

class MFCCProcessor(
    private val sampleRate: Int = 16000,
    private val bufferSize: Int = 512,
    private val mfccCount: Int = 13
) {
    private val mfcc = MFCC(bufferSize, sampleRate.toFloat(), mfccCount, 30, 133.3334f, sampleRate / 2.0f)

    fun extractMFCC(audioData: FloatArray): Array<FloatArray> {
        val result = mutableListOf<FloatArray>()
        val format = TarsosDSPAudioFormat(
            sampleRate.toFloat(),   // sample rate
            16,                     // sample size in bits
            1,                      // mono
            true,                   // signed
            false                   // little endian
        )

        var start = 0
        while (start + bufferSize <= audioData.size) {
            val frame = audioData.sliceArray(start until start + bufferSize)

            val event = AudioEvent(format) // ✅ 수정된 생성자
            event.setFloatBuffer(frame)   // ✅ 직접 설정

            mfcc.process(event)
            result.add(mfcc.mfcc)
            start += bufferSize / 2  // 50% overlap
        }
        return result.toTypedArray()
    }
}