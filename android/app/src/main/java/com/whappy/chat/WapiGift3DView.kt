package com.whappy.chat

import android.content.Context
import android.graphics.PixelFormat
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/** Lightweight transparent OpenGL stage for real 3D Live gift geometry. */
internal class WapiGift3DView(context: Context) : GLSurfaceView(context) {
    private val giftRenderer = GiftRenderer()

    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        holder.setFormat(PixelFormat.TRANSLUCENT)
        setZOrderOnTop(true)
        setRenderer(giftRenderer)
        renderMode = RENDERMODE_CONTINUOUSLY
        preserveEGLContextOnPause = true
    }

    fun setGift(giftId: String) = giftRenderer.setGift(giftId)

    private class GiftRenderer : Renderer {
        private val vertices = floatArrayOf(
            -1f,-1f, 1f,  1f,-1f, 1f,  1f, 1f, 1f,  -1f,-1f, 1f,  1f, 1f, 1f, -1f, 1f, 1f,
             1f,-1f,-1f, -1f,-1f,-1f, -1f, 1f,-1f,  1f,-1f,-1f, -1f, 1f,-1f,  1f, 1f,-1f,
            -1f,-1f,-1f, -1f,-1f, 1f, -1f, 1f, 1f, -1f,-1f,-1f, -1f, 1f, 1f, -1f, 1f,-1f,
             1f,-1f, 1f,  1f,-1f,-1f,  1f, 1f,-1f,  1f,-1f, 1f,  1f, 1f,-1f,  1f, 1f, 1f,
            -1f, 1f, 1f,  1f, 1f, 1f,  1f, 1f,-1f, -1f, 1f, 1f,  1f, 1f,-1f, -1f, 1f,-1f,
            -1f,-1f,-1f,  1f,-1f,-1f,  1f,-1f, 1f, -1f,-1f,-1f,  1f,-1f, 1f, -1f,-1f, 1f,
        )
        private val vertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(vertices); position(0) }
        private val projection = FloatArray(16)
        private val view = FloatArray(16)
        private val model = FloatArray(16)
        private val viewModel = FloatArray(16)
        private val mvp = FloatArray(16)
        private var program = 0
        private var giftId = "trophy"
        private val startedAt = System.nanoTime()

        fun setGift(value: String) { giftId = value }

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            GLES20.glClearColor(0f, 0f, 0f, 0f)
            GLES20.glEnable(GLES20.GL_DEPTH_TEST)
            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
            program = createProgram(
                "attribute vec4 aPosition; uniform mat4 uMvp; varying float vLight; void main(){ gl_Position=uMvp*aPosition; vLight=.55+.45*max(0.0,aPosition.z); }",
                "precision mediump float; uniform vec4 uColor; varying float vLight; void main(){ gl_FragColor=vec4(uColor.rgb*vLight,uColor.a); }",
            )
            Matrix.setLookAtM(view, 0, 0f, 0f, 6f, 0f, 0f, 0f, 0f, 1f, 0f)
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            GLES20.glViewport(0, 0, width, height)
            Matrix.perspectiveM(projection, 0, 42f, width.toFloat() / height.coerceAtLeast(1), 1f, 20f)
        }

        override fun onDrawFrame(gl: GL10?) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
            val seconds = (System.nanoTime() - startedAt) / 1_000_000_000f
            Matrix.setIdentityM(model, 0)
            Matrix.rotateM(model, 0, seconds * 48f, .35f, 1f, .15f)
            val scale = when (giftId) { "glasses" -> floatArrayOf(1.45f, .48f, .35f); "crown" -> floatArrayOf(1.05f, .78f, 1.05f); "halo" -> floatArrayOf(1.35f, .20f, 1.35f); else -> floatArrayOf(.88f, 1.10f, .88f) }
            Matrix.scaleM(model, 0, scale[0], scale[1], scale[2])
            Matrix.multiplyMM(viewModel, 0, view, 0, model, 0)
            Matrix.multiplyMM(mvp, 0, projection, 0, viewModel, 0)
            GLES20.glUseProgram(program)
            val position = GLES20.glGetAttribLocation(program, "aPosition")
            val matrix = GLES20.glGetUniformLocation(program, "uMvp")
            val color = GLES20.glGetUniformLocation(program, "uColor")
            val rgba = when (giftId) { "glasses" -> floatArrayOf(.05f, .68f, 1f, .92f); "halo" -> floatArrayOf(.58f, .30f, 1f, .88f); "trophy" -> floatArrayOf(1f, .50f, .06f, .94f); else -> floatArrayOf(1f, .76f, .05f, .95f) }
            GLES20.glUniformMatrix4fv(matrix, 1, false, mvp, 0)
            GLES20.glUniform4fv(color, 1, rgba, 0)
            GLES20.glEnableVertexAttribArray(position)
            GLES20.glVertexAttribPointer(position, 3, GLES20.GL_FLOAT, false, 12, vertexBuffer)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertices.size / 3)
            GLES20.glDisableVertexAttribArray(position)
        }

        private fun createProgram(vertex: String, fragment: String): Int {
            val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertex)
            val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragment)
            return GLES20.glCreateProgram().also { value -> GLES20.glAttachShader(value, vertexShader); GLES20.glAttachShader(value, fragmentShader); GLES20.glLinkProgram(value) }
        }

        private fun compileShader(type: Int, source: String): Int = GLES20.glCreateShader(type).also { shader -> GLES20.glShaderSource(shader, source); GLES20.glCompileShader(shader) }
    }
}
