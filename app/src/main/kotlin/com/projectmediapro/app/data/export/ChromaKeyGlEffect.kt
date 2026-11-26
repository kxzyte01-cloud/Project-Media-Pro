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
import com.projectmediapro.core.model.ChromaKeyConfig

/**
 * Opt-in Media3 1.4.1 GLSL chroma key for video PiP overlays.
 * Smooth YCbCr chroma-distance gate + edge smoothness + luma despill; outputs clean alpha.
 */
@OptIn(UnstableApi::class)
class ChromaKeyGlEffect(
    private val config: ChromaKeyConfig,
) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
        return ChromaKeyShaderProgram(config.sanitized(), useHdr)
    }

    private class ChromaKeyShaderProgram(
        private val config: ChromaKeyConfig,
        useHighPrecisionColorComponents: Boolean,
    ) : BaseGlShaderProgram(useHighPrecisionColorComponents, /* texturePoolCapacity= */ 1) {

        private val keyChroma = config.keyChroma()

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

        override fun configure(inputWidth: Int, inputHeight: Int): Size = Size(inputWidth, inputHeight)

        override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
            try {
                glProgram.use()
                glProgram.setSamplerTexIdUniform("uInputTex", inputTexId, /* texUnitIndex= */ 0)
                glProgram.setFloatsUniform("uKeyChroma", floatArrayOf(keyChroma.first, keyChroma.second))
                glProgram.setFloatUniform("uSimilarity", config.similarity)
                glProgram.setFloatUniform("uSmoothness", config.smoothness)
                glProgram.setFloatUniform("uSpill", config.spillSuppression)
                glProgram.bindAttributesAndUniforms()
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, /* first= */ 0, /* count= */ 4)
                GlUtil.checkGlError()
            } catch (e: GlUtil.GlException) {
                throw VideoFrameProcessingException.from(e)
            }
        }

        override fun release() {
            try {
                glProgram.delete()
            } catch (e: GlUtil.GlException) {
                throw VideoFrameProcessingException.from(e)
            } finally {
                super.release()
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
uniform sampler2D uInputTex;
uniform vec2 uKeyChroma;
uniform float uSimilarity;
uniform float uSmoothness;
uniform float uSpill;
void main() {
  vec4 src = texture2D(uInputTex, vTexSamplingCoord);
  float y = dot(src.rgb, vec3(0.299, 0.587, 0.114));
  float cb = dot(src.rgb, vec3(-0.168736, -0.331264, 0.5)) + 0.5;
  float cr = dot(src.rgb, vec3(0.5, -0.418688, -0.081312)) + 0.5;
  float dist = distance(vec2(cb, cr), uKeyChroma);
  float alpha = smoothstep(uSimilarity, uSimilarity + max(uSmoothness, 0.0001), dist);
  float keyness = 1.0 - smoothstep(uSimilarity, uSimilarity + uSmoothness + 0.20, dist);
  float despill = uSpill * keyness * 0.85;
  vec3 rgb = mix(src.rgb, vec3(y), despill);
  gl_FragColor = vec4(rgb, src.a * alpha);
}
"""
        }
    }
}
