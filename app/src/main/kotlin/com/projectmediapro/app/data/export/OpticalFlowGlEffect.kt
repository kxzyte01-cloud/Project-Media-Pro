package com.projectmediapro.app.data.export

import android.content.Context
import android.opengl.GLES20
import androidx.annotation.OptIn
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram

/**
 * Pure math helpers for Optical Flow export smoothing, kept JVM-testable.
 */
object OpticalFlowMath {
    const val MAX_CONTINUOUS_GAP_US: Long = 250_000L
    const val STANDARD_60FPS_INTERVAL_US: Long = 16_667L
    const val STANDARD_30FPS_INTERVAL_US: Long = 33_333L

    /**
     * Returns an adaptive optical-flow motion-blend strength in [0f, 0.42f] based on the
     * timestamp gap between consecutive decoded frames:
     * - Returns 0f on first frame, non-monotonic timestamps, or scene cuts (> 250ms).
     * - Applies subtle silky motion flow (~0.18) at 60 FPS, medium (~0.28) at 30 FPS,
     *   and stronger motion-compensated flow (up to 0.42) on slowed-down clips.
     */
    fun blendStrengthForDeltaUs(deltaUs: Long): Float {
        if (deltaUs <= 0L || deltaUs > MAX_CONTINUOUS_GAP_US) return 0f
        return when {
            deltaUs <= STANDARD_60FPS_INTERVAL_US + 2_000L -> 0.18f
            deltaUs <= STANDARD_30FPS_INTERVAL_US + 4_000L -> 0.28f
            else -> {
                val extra = ((deltaUs - STANDARD_30FPS_INTERVAL_US).toFloat() / 66_667f).coerceIn(0f, 1f)
                (0.28f + extra * 0.14f).coerceIn(0f, 0.42f)
            }
        }
    }
}

/**
 * Hardware-accelerated OpenGL ES 2.0 Optical Flow motion-smoothing [GlEffect] for Media3 exports.
 *
 * Uses Lucas-Kanade spatial-temporal gradients between the previous frame and current frame to
 * estimate local 2D motion vectors, warps both frames along the flow vector, and blends them with
 * automatic scene-cut rejection so hard cuts remain crisp while motion & slow-mo stay silky.
 */
@OptIn(UnstableApi::class)
class OpticalFlowGlEffect : GlEffect {

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
        return OpticalFlowShaderProgram(useHdr)
    }

    private class OpticalFlowShaderProgram(
        useHighPrecisionColorComponents: Boolean,
    ) : BaseGlShaderProgram(useHighPrecisionColorComponents, /* texturePoolCapacity= */ 1) {

        private val glProgram: GlProgram = try {
            GlProgram(VERTEX_SHADER, FRAGMENT_SHADER).apply {
                setBufferAttribute(
                    "aFramePosition",
                    GlUtil.getNormalizedCoordinateBounds(),
                    GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE,
                )
                setFloatsUniform("uTransformationMatrix", GlUtil.create4x4IdentityMatrix())
                setFloatsUniform("uTexTransformationMatrix", GlUtil.create4x4IdentityMatrix())
            }
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException.from(e)
        }

        private var width: Int = 0
        private var height: Int = 0
        private var prevTexId: Int = 0
        private var prevFboId: Int = 0
        private var hasPrevFrame: Boolean = false
        private var prevPresentationTimeUs: Long = Long.MIN_VALUE
        private val boundFboScratch = IntArray(1)

        override fun configure(inputWidth: Int, inputHeight: Int): Size {
            if (inputWidth != width || inputHeight != height || prevTexId == 0) {
                releaseHistoryBuffers()
                width = inputWidth
                height = inputHeight
                allocateHistoryBuffers(inputWidth, inputHeight)
            }
            hasPrevFrame = false
            prevPresentationTimeUs = Long.MIN_VALUE
            return Size(inputWidth, inputHeight)
        }

        override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
            try {
                GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, boundFboScratch, 0)
                val outputFboId = boundFboScratch[0]

                val deltaUs = if (hasPrevFrame && prevPresentationTimeUs != Long.MIN_VALUE) {
                    presentationTimeUs - prevPresentationTimeUs
                } else {
                    0L
                }
                val blendStrength = OpticalFlowMath.blendStrengthForDeltaUs(deltaUs)

                glProgram.use()
                glProgram.setSamplerTexIdUniform("uCurrTex", inputTexId, /* texUnitIndex= */ 0)
                glProgram.setSamplerTexIdUniform(
                    "uPrevTex",
                    if (hasPrevFrame && prevTexId != 0) prevTexId else inputTexId,
                    /* texUnitIndex= */ 1,
                )
                glProgram.setFloatsUniform(
                    "uTexelSize",
                    floatArrayOf(1f / width.coerceAtLeast(1), 1f / height.coerceAtLeast(1)),
                )
                glProgram.setFloatUniform("uBlendStrength", if (hasPrevFrame) blendStrength else 0f)
                glProgram.bindAttributesAndUniforms()
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, /* first= */ 0, /* count= */ 4)
                GlUtil.checkGlError()

                // Copy raw inputTexId into prevTexId for the next frame pair
                if (prevFboId != 0 && prevTexId != 0) {
                    val blendWasEnabled = GLES20.glIsEnabled(GLES20.GL_BLEND)
                    if (blendWasEnabled) GLES20.glDisable(GLES20.GL_BLEND)
                    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, prevFboId)
                    GLES20.glViewport(0, 0, width, height)
                    glProgram.use()
                    glProgram.setSamplerTexIdUniform("uCurrTex", inputTexId, /* texUnitIndex= */ 0)
                    glProgram.setSamplerTexIdUniform("uPrevTex", inputTexId, /* texUnitIndex= */ 1)
                    glProgram.setFloatUniform("uBlendStrength", 0f)
                    glProgram.bindAttributesAndUniforms()
                    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, /* first= */ 0, /* count= */ 4)
                    GlUtil.checkGlError()
                    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, outputFboId)
                    GLES20.glViewport(0, 0, width, height)
                    if (blendWasEnabled) GLES20.glEnable(GLES20.GL_BLEND)
                    hasPrevFrame = true
                    prevPresentationTimeUs = presentationTimeUs
                }
            } catch (e: GlUtil.GlException) {
                throw VideoFrameProcessingException.from(e)
            }
        }

        override fun flush() {
            hasPrevFrame = false
            prevPresentationTimeUs = Long.MIN_VALUE
            super.flush()
        }

        override fun signalEndOfCurrentInputStream() {
            hasPrevFrame = false
            prevPresentationTimeUs = Long.MIN_VALUE
            super.signalEndOfCurrentInputStream()
        }

        override fun release() {
            hasPrevFrame = false
            prevPresentationTimeUs = Long.MIN_VALUE
            releaseHistoryBuffers()
            try {
                glProgram.delete()
            } catch (e: GlUtil.GlException) {
                throw VideoFrameProcessingException.from(e)
            } finally {
                super.release()
            }
        }

        private fun allocateHistoryBuffers(w: Int, h: Int) {
            GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, boundFboScratch, 0)
            val savedFboId = boundFboScratch[0]

            val textures = IntArray(1)
            GLES20.glGenTextures(1, textures, 0)
            prevTexId = textures[0]
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, prevTexId)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D,
                0,
                GLES20.GL_RGBA,
                w,
                h,
                0,
                GLES20.GL_RGBA,
                GLES20.GL_UNSIGNED_BYTE,
                null,
            )

            val fbos = IntArray(1)
            GLES20.glGenFramebuffers(1, fbos, 0)
            prevFboId = fbos[0]
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, prevFboId)
            GLES20.glFramebufferTexture2D(
                GLES20.GL_FRAMEBUFFER,
                GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D,
                prevTexId,
                0,
            )
            val status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, savedFboId)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
            if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
                releaseHistoryBuffers()
                return
            }
            try {
                GlUtil.checkGlError()
            } catch (e: GlUtil.GlException) {
                releaseHistoryBuffers()
                throw VideoFrameProcessingException.from(e)
            }
        }

        private fun releaseHistoryBuffers() {
            if (prevFboId != 0) {
                GLES20.glDeleteFramebuffers(1, intArrayOf(prevFboId), 0)
                prevFboId = 0
            }
            if (prevTexId != 0) {
                GLES20.glDeleteTextures(1, intArrayOf(prevTexId), 0)
                prevTexId = 0
            }
        }

        companion object {
            private const val VERTEX_SHADER = """
                attribute vec4 aFramePosition;
                uniform mat4 uTransformationMatrix;
                uniform mat4 uTexTransformationMatrix;
                varying vec2 vTexSamplingCoord;
                void main() {
                    gl_Position = uTransformationMatrix * aFramePosition;
                    vec4 texturePosition = vec4(aFramePosition.x * 0.5 + 0.5, aFramePosition.y * 0.5 + 0.5, 0.0, 1.0);
                    vTexSamplingCoord = (uTexTransformationMatrix * texturePosition).xy;
                }
            """

            private const val FRAGMENT_SHADER = """
                precision highp float;
                varying vec2 vTexSamplingCoord;
                uniform sampler2D uCurrTex;
                uniform sampler2D uPrevTex;
                uniform vec2 uTexelSize;
                uniform float uBlendStrength;

                float luma(vec3 c) {
                    return dot(c, vec3(0.299, 0.587, 0.114));
                }

                void main() {
                    vec4 curr = texture2D(uCurrTex, vTexSamplingCoord);
                    if (uBlendStrength <= 0.001) {
                        gl_FragColor = curr;
                        return;
                    }

                    vec4 prev = texture2D(uPrevTex, vTexSamplingCoord);
                    float diff = abs(luma(curr.rgb) - luma(prev.rgb));
                    // Scene-cut rejection: keep hard cuts crisp without ghosting
                    float cutGuard = 1.0 - smoothstep(0.22, 0.42, diff);
                    float strength = uBlendStrength * cutGuard;
                    if (strength <= 0.001) {
                        gl_FragColor = curr;
                        return;
                    }

                    // Lucas-Kanade spatial-temporal gradient optical flow estimate
                    vec2 dx = vec2(uTexelSize.x * 2.0, 0.0);
                    vec2 dy = vec2(0.0, uTexelSize.y * 2.0);
                    float gx = (luma(texture2D(uCurrTex, clamp(vTexSamplingCoord + dx, 0.0, 1.0)).rgb) -
                                luma(texture2D(uCurrTex, clamp(vTexSamplingCoord - dx, 0.0, 1.0)).rgb)) * 0.5;
                    float gy = (luma(texture2D(uCurrTex, clamp(vTexSamplingCoord + dy, 0.0, 1.0)).rgb) -
                                luma(texture2D(uCurrTex, clamp(vTexSamplingCoord - dy, 0.0, 1.0)).rgb)) * 0.5;
                    float gt = luma(curr.rgb) - luma(prev.rgb);

                    float denom = gx * gx + gy * gy + 0.015;
                    vec2 flow = clamp(vec2(-gt * gx, -gt * gy) / denom, vec2(-3.0), vec2(3.0)) * uTexelSize;

                    vec4 warpedPrev = texture2D(uPrevTex, clamp(vTexSamplingCoord + flow * 0.5, 0.0, 1.0));
                    vec4 warpedCurr = texture2D(uCurrTex, clamp(vTexSamplingCoord - flow * 0.5, 0.0, 1.0));
                    vec4 flowMid = mix(warpedPrev, warpedCurr, 0.5);

                    gl_FragColor = vec4(mix(curr.rgb, flowMid.rgb, strength), curr.a);
                }
            """
        }
    }
}
