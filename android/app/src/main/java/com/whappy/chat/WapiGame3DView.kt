package com.whappy.chat

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.view.MotionEvent
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.sin

/**
 * Native OpenGL ES 2.0 stage used by WAPI Play.
 *
 * This is real 3D geometry with a perspective camera, depth testing, lighting
 * by material color and touch orbit controls. Game rules remain in the
 * Compose game modules; this stage is their shared 3D presentation layer.
 */
internal class WapiGame3DView(context: Context) : GLSurfaceView(context) {
    private val gameRenderer = GameRenderer()
    private var previousX = 0f
    private var previousY = 0f

    init {
        setEGLContextClientVersion(2)
        setRenderer(gameRenderer)
        renderMode = RENDERMODE_CONTINUOUSLY
        preserveEGLContextOnPause = true
    }

    fun setScene(scene: String) {
        gameRenderer.scene = scene
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                previousX = event.x
                previousY = event.y
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - previousX
                val dy = event.y - previousY
                previousX = event.x
                previousY = event.y
                gameRenderer.orbit(dx, dy)
                return true
            }
            MotionEvent.ACTION_UP -> {
                performClick()
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private class GameRenderer : Renderer {
        @Volatile var scene: String = "ludo"
        private var yaw = -18f
        private var pitch = 28f
        private var program = 0
        private var positionHandle = 0
        private var matrixHandle = 0
        private var colorHandle = 0
        private val projection = FloatArray(16)
        private val view = FloatArray(16)
        private val model = FloatArray(16)
        private val viewModel = FloatArray(16)
        private val mvp = FloatArray(16)
        private val cube = cubeBuffer()
        private val sphere = sphereBuffer()
        private val startedAt = System.nanoTime()

        fun orbit(dx: Float, dy: Float) {
            yaw = (yaw + dx * .35f) % 360f
            pitch = (pitch - dy * .25f).coerceIn(8f, 58f)
        }

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            GLES20.glClearColor(.018f, .027f, .055f, 1f)
            GLES20.glEnable(GLES20.GL_DEPTH_TEST)
            GLES20.glEnable(GLES20.GL_CULL_FACE)
            GLES20.glCullFace(GLES20.GL_BACK)
            program = createProgram(
                "attribute vec4 aPosition; uniform mat4 uMvp; void main(){ gl_Position=uMvp*aPosition; }",
                "precision mediump float; uniform vec4 uColor; void main(){ gl_FragColor=uColor; }",
            )
            positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
            matrixHandle = GLES20.glGetUniformLocation(program, "uMvp")
            colorHandle = GLES20.glGetUniformLocation(program, "uColor")
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            GLES20.glViewport(0, 0, width, height)
            Matrix.perspectiveM(projection, 0, 42f, width.toFloat() / height.coerceAtLeast(1), .1f, 80f)
        }

        override fun onDrawFrame(gl: GL10?) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
            val seconds = (System.nanoTime() - startedAt) / 1_000_000_000f
            val orbitRadius = 10.2f
            val yawRadians = Math.toRadians(yaw.toDouble())
            val pitchRadians = Math.toRadians(pitch.toDouble())
            val eyeX = (sin(yawRadians) * cos(pitchRadians) * orbitRadius).toFloat()
            val eyeY = (sin(pitchRadians) * orbitRadius).toFloat()
            val eyeZ = (cos(yawRadians) * cos(pitchRadians) * orbitRadius).toFloat()
            Matrix.setLookAtM(view, 0, eyeX, eyeY, eyeZ, 0f, .15f, 0f, 0f, 1f, 0f)
            Matrix.setIdentityM(model, 0)
            when (scene) {
                "pool" -> drawPool(seconds)
                "chess" -> drawBoard(false, seconds)
                "checkers" -> drawBoard(true, seconds)
                "cards", "poker" -> drawCards(scene == "poker", seconds)
                "sky" -> drawSky(seconds)
                else -> drawLudo(seconds)
            }
        }

        private fun drawPool(seconds: Float) {
            drawCube(0f, -.12f, 0f, 5.2f, .22f, 3.2f, floatArrayOf(.02f, .08f, .12f, 1f))
            drawCube(0f, .08f, 0f, 4.75f, .16f, 2.75f, floatArrayOf(.02f, .38f, .25f, 1f))
            val rail = floatArrayOf(.24f, .12f, .05f, 1f)
            drawCube(0f, .23f, -2.93f, 4.95f, .32f, .22f, rail)
            drawCube(0f, .23f, 2.93f, 4.95f, .32f, .22f, rail)
            drawCube(-4.92f, .23f, 0f, .22f, .32f, 2.75f, rail)
            drawCube(4.92f, .23f, 0f, .22f, .32f, 2.75f, rail)
            val pockets = listOf(-4.65f to -2.62f, 0f to -2.62f, 4.65f to -2.62f, -4.65f to 2.62f, 0f to 2.62f, 4.65f to 2.62f)
            pockets.forEach { (x, z) -> drawSphere(x, .30f, z, .22f, floatArrayOf(.005f, .008f, .012f, 1f), 12f) }
            val balls = listOf(
                Triple(.24f, .34f, .50f) to floatArrayOf(1f, 1f, 1f, 1f),
                Triple(1.35f, .34f, .50f) to floatArrayOf(1f, .75f, .05f, 1f),
                Triple(1.62f, .34f, .35f) to floatArrayOf(.88f, .08f, .12f, 1f),
                Triple(1.62f, .34f, .65f) to floatArrayOf(.08f, .32f, .95f, 1f),
                Triple(1.90f, .34f, .20f) to floatArrayOf(.08f, .72f, .42f, 1f),
                Triple(1.90f, .34f, .50f) to floatArrayOf(.66f, .12f, .85f, 1f),
                Triple(1.90f, .34f, .80f) to floatArrayOf(1f, .35f, .06f, 1f),
            )
            balls.forEach { (point, color) -> drawSphere(point.first, point.second, point.third, .22f, color, seconds * 18f) }
            val cueAngle = seconds * .035f
            drawCube(-1.55f, .39f, .50f, 2.45f, .055f, .055f, floatArrayOf(.74f, .43f, .18f, 1f), rotationY = Math.toDegrees(cueAngle.toDouble()).toFloat())
        }

        private fun drawBoard(checkers: Boolean, seconds: Float) {
            drawCube(0f, -.10f, 0f, 4.55f, .25f, 4.55f, floatArrayOf(.08f, .035f, .018f, 1f))
            val light = floatArrayOf(.88f, .72f, .45f, 1f)
            val dark = floatArrayOf(.18f, .08f, .035f, 1f)
            for (row in 0 until 8) for (column in 0 until 8) {
                drawCube(-3.5f + column, .08f, -3.5f + row, .49f, .10f, .49f, if ((row + column) % 2 == 0) light else dark)
            }
            val pieceColor = if (checkers) floatArrayOf(.06f, .46f, .96f, 1f) else floatArrayOf(.80f, .87f, .93f, 1f)
            val rivalColor = if (checkers) floatArrayOf(.88f, .10f, .18f, 1f) else floatArrayOf(.08f, .11f, .16f, 1f)
            val rows = if (checkers) 3 else 2
            for (row in 0 until rows) for (column in 0 until 8) if ((row + column) % 2 == 1) {
                drawSphere(-3.5f + column, .30f, -3.5f + row, .30f, rivalColor, seconds * 8f)
                drawSphere(-3.5f + column, .30f, 3.5f - row, .30f, pieceColor, -seconds * 8f)
            }
            if (checkers) drawSphere(0f, .56f, 0f, .34f, floatArrayOf(1f, .78f, .08f, 1f), seconds * 12f)
        }

        private fun drawCards(poker: Boolean, seconds: Float) {
            drawCube(0f, -.16f, 0f, 5.1f, .22f, 3.25f, floatArrayOf(.04f, .16f, .13f, 1f))
            drawCube(0f, .08f, 0f, 4.75f, .08f, 2.9f, floatArrayOf(.02f, .42f, .26f, 1f))
            val cardColor = floatArrayOf(.95f, .97f, 1f, 1f)
            val cardBack = floatArrayOf(.04f, .32f, .86f, 1f)
            val count = if (poker) 5 else 4
            for (index in 0 until count) {
                val x = (index - (count - 1) / 2f) * 1.15f
                drawCube(x, .28f, if (poker) .25f else 0f, .46f, .075f, .72f, cardColor, rotationY = sin(seconds + index) * 2.5f)
            }
            drawCube(3.45f, .33f, -1.45f, .52f, .24f, .72f, cardBack, rotationY = -12f)
            drawCube(3.52f, .37f, -1.35f, .52f, .24f, .72f, cardBack, rotationY = -8f)
        }

        private fun drawLudo(seconds: Float) {
            drawCube(0f, -.14f, 0f, 4.9f, .28f, 4.9f, floatArrayOf(.06f, .12f, .20f, 1f))
            val zones = listOf(
                -2.65f to -2.65f to floatArrayOf(.86f, .12f, .16f, 1f),
                2.65f to -2.65f to floatArrayOf(.10f, .34f, .94f, 1f),
                -2.65f to 2.65f to floatArrayOf(.08f, .70f, .40f, 1f),
                2.65f to 2.65f to floatArrayOf(.96f, .70f, .08f, 1f),
            )
            zones.forEach { (position, color) -> drawCube(position.first, .08f, position.second, 1.45f, .14f, 1.45f, color) }
            for (index in 0 until 12) {
                val angle = index * (Math.PI * 2.0 / 12.0) + seconds * .12
                drawSphere((cos(angle) * 1.65).toFloat(), .35f, (sin(angle) * 1.65).toFloat(), .24f, zones[index % 4].second, seconds * 15f)
            }
            drawSphere(0f, .72f, 0f, .35f, floatArrayOf(1f, 1f, 1f, 1f), seconds * 18f)
        }

        private fun drawSky(seconds: Float) {
            drawCube(0f, -.20f, 0f, 5.2f, .18f, 3.2f, floatArrayOf(.04f, .10f, .22f, 1f))
            for (index in 0 until 7) {
                val x = -3.8f + index * 1.25f
                val height = 1.0f + (index % 3) * .55f
                drawCube(x, height / 2f, (index % 2) * .8f - .4f, .42f, height, .42f, floatArrayOf(.05f, .63f, .96f, 1f), rotationY = seconds * 18f + index * 12f)
            }
            drawSphere(0f, .65f, 0f, .32f, floatArrayOf(1f, .78f, .05f, 1f), seconds * 60f)
        }

        private fun drawCube(x: Float, y: Float, z: Float, sx: Float, sy: Float, sz: Float, color: FloatArray, rotationY: Float = 0f) {
            Matrix.setIdentityM(model, 0)
            Matrix.translateM(model, 0, x, y, z)
            Matrix.rotateM(model, 0, rotationY, 0f, 1f, 0f)
            Matrix.scaleM(model, 0, sx, sy, sz)
            drawMesh(cube, color)
        }

        private fun drawSphere(x: Float, y: Float, z: Float, radius: Float, color: FloatArray, rotationY: Float) {
            Matrix.setIdentityM(model, 0)
            Matrix.translateM(model, 0, x, y, z)
            Matrix.rotateM(model, 0, rotationY, 0f, 1f, 0f)
            Matrix.scaleM(model, 0, radius, radius, radius)
            drawMesh(sphere, color)
        }

        private fun drawMesh(buffer: FloatBuffer, color: FloatArray) {
            Matrix.multiplyMM(viewModel, 0, view, 0, model, 0)
            Matrix.multiplyMM(mvp, 0, projection, 0, viewModel, 0)
            GLES20.glUseProgram(program)
            GLES20.glUniformMatrix4fv(matrixHandle, 1, false, mvp, 0)
            GLES20.glUniform4fv(colorHandle, 1, color, 0)
            buffer.position(0)
            GLES20.glEnableVertexAttribArray(positionHandle)
            GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 12, buffer)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, buffer.capacity() / 3)
            GLES20.glDisableVertexAttribArray(positionHandle)
        }

        private fun createProgram(vertexSource: String, fragmentSource: String): Int {
            val vertex = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource)
            val fragment = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
            return GLES20.glCreateProgram().also { programId ->
                GLES20.glAttachShader(programId, vertex)
                GLES20.glAttachShader(programId, fragment)
                GLES20.glLinkProgram(programId)
            }
        }

        private fun compileShader(type: Int, source: String): Int = GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)
        }

        private companion object {
            fun cubeBuffer(): FloatBuffer {
                val values = floatArrayOf(
                    -1f,-1f,1f, 1f,-1f,1f, 1f,1f,1f, -1f,-1f,1f, 1f,1f,1f, -1f,1f,1f,
                    1f,-1f,-1f, -1f,-1f,-1f, -1f,1f,-1f, 1f,-1f,-1f, -1f,1f,-1f, 1f,1f,-1f,
                    -1f,-1f,-1f, -1f,-1f,1f, -1f,1f,1f, -1f,-1f,-1f, -1f,1f,1f, -1f,1f,-1f,
                    1f,-1f,1f, 1f,-1f,-1f, 1f,1f,-1f, 1f,-1f,1f, 1f,1f,-1f, 1f,1f,1f,
                    -1f,1f,1f, 1f,1f,1f, 1f,1f,-1f, -1f,1f,1f, 1f,1f,-1f, -1f,1f,-1f,
                    -1f,-1f,-1f, 1f,-1f,-1f, 1f,-1f,1f, -1f,-1f,-1f, 1f,-1f,1f, -1f,-1f,1f,
                )
                return ByteBuffer.allocateDirect(values.size * Float.SIZE_BYTES)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer()
                    .apply { put(values); position(0) }
            }

            fun sphereBuffer(): FloatBuffer {
                val values = ArrayList<Float>()
                val segments = 18
                val rings = 10
                for (ring in 0 until rings) {
                    val v0 = ring.toFloat() / rings
                    val v1 = (ring + 1).toFloat() / rings
                    val phi0 = Math.PI * v0
                    val phi1 = Math.PI * v1
                    for (segment in 0 until segments) {
                        val u0 = segment.toFloat() / segments
                        val u1 = (segment + 1).toFloat() / segments
                        val p = floatArrayOf(
                            (sin(phi0) * cos(Math.PI * 2 * u0)).toFloat(), cos(phi0).toFloat(), (sin(phi0) * sin(Math.PI * 2 * u0)).toFloat(),
                            (sin(phi1) * cos(Math.PI * 2 * u0)).toFloat(), cos(phi1).toFloat(), (sin(phi1) * sin(Math.PI * 2 * u0)).toFloat(),
                            (sin(phi1) * cos(Math.PI * 2 * u1)).toFloat(), cos(phi1).toFloat(), (sin(phi1) * sin(Math.PI * 2 * u1)).toFloat(),
                            (sin(phi0) * cos(Math.PI * 2 * u0)).toFloat(), cos(phi0).toFloat(), (sin(phi0) * sin(Math.PI * 2 * u0)).toFloat(),
                            (sin(phi1) * cos(Math.PI * 2 * u1)).toFloat(), cos(phi1).toFloat(), (sin(phi1) * sin(Math.PI * 2 * u1)).toFloat(),
                            (sin(phi0) * cos(Math.PI * 2 * u1)).toFloat(), cos(phi0).toFloat(), (sin(phi0) * sin(Math.PI * 2 * u1)).toFloat(),
                        )
                        p.forEach(values::add)
                    }
                }
                return ByteBuffer.allocateDirect(values.size * Float.SIZE_BYTES)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer()
                    .apply { put(values.toFloatArray()); position(0) }
            }
        }
    }
}
