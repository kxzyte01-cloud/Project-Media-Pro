package com.projectmediapro.app.editor

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import com.projectmediapro.app.data.export.SoftClipAudioProcessor
import com.projectmediapro.core.model.AudioEnhanceConfig
import com.projectmediapro.core.model.AudioEqPreset
import com.projectmediapro.core.model.Clip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class Step31LiveAudioPreviewTest {

    @Test
    fun liveConfigSanitizesOutOfRangeAndNonFiniteBoost() {
        val clean = LiveAudioConfig.from(Float.NaN, null)
        assertTrue(clean.isPassThrough)
        assertEquals(Clip.DEFAULT_AUDIO_BOOST, clean.gainBoost, 0.0001f)

        val clamped = LiveAudioConfig.from(
            audioBoost = 5.0f,
            enhance = AudioEnhanceConfig(eqPreset = AudioEqPreset.VOCAL_CLARITY, noiseGateEnabled = true),
        )
        assertFalse(clamped.isPassThrough)
        assertEquals(Clip.MAX_AUDIO_BOOST, clamped.gainBoost, 0.0001f)
        assertEquals(AudioEqPreset.VOCAL_CLARITY, clamped.eqPreset)
        assertTrue(clamped.noiseGateEnabled)
    }

    @Test
    fun liveProcessorMatchesExportProcessorBitForBitAndResetsOnPassThrough() {
        val format = AudioProcessor.AudioFormat(48_000, 1, C.ENCODING_PCM_16BIT)
        val enhance = AudioEnhanceConfig(eqPreset = AudioEqPreset.VOCAL_CLARITY, noiseGateEnabled = true)
        val boost = 1.6f

        val live = LiveAudioEnhanceProcessor()
        assertEquals(format, live.configure(format))
        live.flush()

        val export = SoftClipAudioProcessor(gain = boost, audioEnhance = enhance)
        assertEquals(format, export.configure(format))
        export.flush()

        val sampleCount = 1_024
        val inputLive = ByteBuffer.allocateDirect(sampleCount * 2).order(ByteOrder.nativeOrder())
        val inputExport = ByteBuffer.allocateDirect(sampleCount * 2).order(ByteOrder.nativeOrder())
        for (i in 0 until sampleCount) {
            val s = (sin(2.0 * PI * 3_200.0 * i / 48_000.0) * 8_000.0).roundToInt().toShort()
            inputLive.putShort(s)
            inputExport.putShort(s)
        }
        inputLive.flip()
        inputExport.flip()

        live.update(audioBoost = boost, enhance = enhance)
        live.queueInput(inputLive)
        export.queueInput(inputExport)

        val liveOut = live.output
        val exportOut = export.output
        assertEquals(sampleCount * 2, liveOut.remaining())
        assertEquals(sampleCount * 2, exportOut.remaining())

        var processedPeak = 0
        for (i in 0 until sampleCount) {
            val l = liveOut.short
            val e = exportOut.short
            assertEquals("Sample $i must match export bit-for-bit", e, l)
            processedPeak = maxOf(processedPeak, abs(l.toInt()))
        }
        assertTrue("Vocal Clarity + 1.6x boost should amplify 3.2 kHz", processedPeak > 12_000)

        live.update(Clip.DEFAULT_AUDIO_BOOST, null)
        inputLive.rewind()
        live.queueInput(inputLive)
        val flatOut = live.output
        assertEquals(sampleCount * 2, flatOut.remaining())
        var flatPeak = 0
        while (flatOut.hasRemaining()) {
            flatPeak = maxOf(flatPeak, abs(flatOut.short.toInt()))
        }
        assertTrue("Pass-through should restore original 8000 peak", flatPeak in 7_500..8_500)
    }
}
