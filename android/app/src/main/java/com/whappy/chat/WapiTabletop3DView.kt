package com.whappy.chat

import android.content.Context
import android.graphics.BitmapFactory
import android.opengl.GLES20
import android.opengl.GLUtils
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Shared native tabletop renderer for WAPI Play.
 *
 * The view renders actual OpenGL geometry (board, frame and pieces), keeps the
 * scene synchronized with the rule engine and ray-casts taps back to a board
 * square. A drag orbits the camera; a tap selects or moves a piece.
 */
internal class WapiTabletop3DView(context: Context) : GLSurfaceView(context) {
    enum class Scene { CHECKERS, CHESS, LUDO, POOL }

    private val tabletopRenderer = TabletopRenderer(context.applicationContext)
    private var downX = 0f
    private var downY = 0f
    private var previousX = 0f
    private var previousY = 0f
    var onSquareTapped: ((Int) -> Unit)? = null
    var onPoolGesture: ((Float, Float, Boolean) -> Unit)? = null
    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            tabletopRenderer.zoomBy(detector.scaleFactor)
            return true
        }
    })
    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(event: MotionEvent): Boolean = true

        override fun onDoubleTap(event: MotionEvent): Boolean {
            tabletopRenderer.resetCamera()
            return true
        }

        override fun onFling(first: MotionEvent?, last: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            if (tabletopRenderer.scene != Scene.POOL) tabletopRenderer.fling(velocityX, velocityY)
            return true
        }
    })

    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 24, 0)
        setRenderer(tabletopRenderer)
        renderMode = RENDERMODE_CONTINUOUSLY
        preserveEGLContextOnPause = true
    }

    fun setStrategyScene(scene: Scene, board: List<String>, selected: Int, legalTargets: Set<Int>) {
        tabletopRenderer.scene = scene
        tabletopRenderer.updateStrategyBoard(board)
        tabletopRenderer.selected = selected
        tabletopRenderer.legalTargets = legalTargets.toSet()
    }

    fun setLudoScene(positions: List<Int>, activePlayer: Int, dieOne: Int, dieTwo: Int, rolling: Boolean) {
        tabletopRenderer.scene = Scene.LUDO
        tabletopRenderer.updateLudoPositions(positions)
        tabletopRenderer.activePlayer = activePlayer
        tabletopRenderer.dieOne = dieOne.coerceIn(1, 6)
        tabletopRenderer.dieTwo = dieTwo.coerceIn(1, 6)
        tabletopRenderer.dieRolling = rolling
    }

    fun setPoolScene(balls: List<FloatArray>, aimAngle: Float, power: Int, moving: Boolean) {
        tabletopRenderer.scene = Scene.POOL
        tabletopRenderer.poolBalls = balls.map(FloatArray::copyOf)
        tabletopRenderer.poolAim = aimAngle
        tabletopRenderer.poolPower = power.coerceIn(1, 4)
        tabletopRenderer.poolMoving = moving
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        if (event.pointerCount > 1 || scaleDetector.isInProgress) return true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                previousX = event.x
                previousY = event.y
                tabletopRenderer.beginOrbit()
                if (tabletopRenderer.scene == Scene.POOL) dispatchPoolGesture(event, released = false)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (tabletopRenderer.scene == Scene.POOL) {
                    dispatchPoolGesture(event, released = false)
                    return true
                }
                val dx = event.x - previousX
                val dy = event.y - previousY
                previousX = event.x
                previousY = event.y
                if (abs(event.x - downX) + abs(event.y - downY) > 12f) tabletopRenderer.orbit(dx, dy)
                return true
            }
            MotionEvent.ACTION_UP -> {
                performClick()
                tabletopRenderer.endOrbit()
                if (tabletopRenderer.scene == Scene.POOL) {
                    dispatchPoolGesture(event, released = true)
                } else if (abs(event.x - downX) + abs(event.y - downY) < 24f && tabletopRenderer.scene != Scene.LUDO) {
                    tabletopRenderer.pickSquare(event.x, event.y)?.let { square ->
                        post { onSquareTapped?.invoke(square) }
                    }
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                tabletopRenderer.endOrbit()
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun dispatchPoolGesture(event: MotionEvent, released: Boolean) {
        tabletopRenderer.pickPoolPoint(event.x, event.y)?.let { point ->
            onPoolGesture?.invoke(point[0], point[1], released)
        }
    }

    private data class Mesh(val vertices: FloatBuffer, val count: Int)
    private data class StrategyMove(val from: Int, val to: Int, val startedAtNanos: Long)
    private data class LudoMove(
        val player: Int,
        val pawn: Int,
        val from: Int,
        val to: Int,
        val startedAtNanos: Long,
    )

    private class TabletopRenderer(private val context: Context) : Renderer {
        @Volatile var scene: Scene = Scene.CHECKERS
        @Volatile var board: List<String> = emptyList()
        @Volatile var selected: Int = -1
        @Volatile var legalTargets: Set<Int> = emptySet()
        @Volatile var ludoPositions: List<Int> = emptyList()
        @Volatile var activePlayer: Int = 0
        @Volatile var dieOne: Int = 1
        @Volatile var dieTwo: Int = 1
        @Volatile var dieRolling: Boolean = false
        @Volatile var poolBalls: List<FloatArray> = emptyList()
        @Volatile var poolAim: Float = 0f
        @Volatile var poolPower: Int = 2
        @Volatile var poolMoving: Boolean = false
        @Volatile private var strategyMove: StrategyMove? = null
        @Volatile private var ludoMove: LudoMove? = null

        private var width = 1
        private var height = 1
        @Volatile private var strategyDimension = 8
        private var yaw = 0f
        private var pitch = 49f
        @Volatile private var zoom = 1f
        @Volatile private var orbiting = false
        @Volatile private var yawVelocity = 0f
        @Volatile private var pitchVelocity = 0f
        private var program = 0
        private var positionHandle = 0
        private var normalHandle = 0
        private var mvpHandle = 0
        private var modelHandle = 0
        private var colorHandle = 0
        private var glossHandle = 0
        private var materialHandle = 0
        private var textureHandle = 0
        private var eyeHandle = 0
        private var eyeX = 0f
        private var eyeY = 8f
        private var eyeZ = 8f
        private var woodTexture = 0
        private var feltTexture = 0
        private val projection = FloatArray(16)
        private val view = FloatArray(16)
        private val vp = FloatArray(16)
        private val model = FloatArray(16)
        private val viewModel = FloatArray(16)
        private val mvp = FloatArray(16)
        private val inverseVp = FloatArray(16)
        private val cube = cubeMesh()
        private val cylinder = cylinderMesh(48)
        private val cone = coneMesh(48)
        private val sphere = sphereMesh(40, 24)
        private val torus = torusMesh(48, 14)
        private val startedAt = System.nanoTime()

        fun updateStrategyBoard(nextBoard: List<String>) {
            val previous = board
            val next = nextBoard.toList()
            if (previous.size == next.size && previous.isNotEmpty() && previous != next) {
                val to = next.indices.filter { next[it].isNotBlank() && previous[it] != next[it] }
                val destination = to.singleOrNull()
                val from = if (destination != null) previous.indices.filter {
                    previous[it].isNotBlank() && next[it].isBlank() &&
                        WapiGameRules.isWhite(previous[it]) == WapiGameRules.isWhite(next[destination])
                } else emptyList()
                if (from.size == 1 && destination != null) {
                    strategyMove = StrategyMove(from.first(), destination, System.nanoTime())
                }
            }
            board = next
        }

        /**
         * Keep the Ludo board authoritative while giving a moved pawn a short
         * physical trajectory. Compose can publish the same state many times;
         * comparing the previous snapshot prevents the animation from
         * restarting during recomposition.
         */
        fun updateLudoPositions(nextPositions: List<Int>) {
            val previous = ludoPositions
            val next = nextPositions.toList()
            if (previous.size == next.size && previous.isNotEmpty() && previous != next) {
                val changed = next.indices.filter { previous[it] != next[it] }
                val moved = changed.firstOrNull { index ->
                    val old = previous[index]
                    val value = next[index]
                    value >= 0 && (old < 0 || value > old)
                }
                if (moved != null) {
                    ludoMove = LudoMove(
                        player = moved / 4,
                        pawn = moved % 4,
                        from = previous[moved],
                        to = next[moved],
                        startedAtNanos = System.nanoTime(),
                    )
                }
            }
            ludoPositions = next
        }

        fun orbit(dx: Float, dy: Float) {
            yaw = (yaw + dx * .18f).coerceIn(-27f, 27f)
            pitch = (pitch - dy * .14f).coerceIn(37f, 67f)
            yawVelocity = dx * .030f
            pitchVelocity = -dy * .023f
        }

        fun beginOrbit() { orbiting = true; yawVelocity = 0f; pitchVelocity = 0f }
        fun endOrbit() { orbiting = false }
        fun zoomBy(scaleFactor: Float) { zoom = (zoom / scaleFactor).coerceIn(.72f, 1.38f) }
        fun fling(velocityX: Float, velocityY: Float) {
            orbiting = false
            yawVelocity = (velocityX / 8_500f).coerceIn(-1.15f, 1.15f)
            pitchVelocity = (-velocityY / 10_500f).coerceIn(-.72f, .72f)
        }
        fun resetCamera() {
            yaw = 0f
            pitch = 49f
            zoom = 1f
            yawVelocity = 0f
            pitchVelocity = 0f
        }

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            GLES20.glClearColor(.035f, .045f, .065f, 1f)
            GLES20.glEnable(GLES20.GL_DEPTH_TEST)
            GLES20.glEnable(GLES20.GL_CULL_FACE)
            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
            GLES20.glCullFace(GLES20.GL_BACK)
            program = createProgram(
                """
                attribute vec3 aPosition;
                attribute vec3 aNormal;
                uniform mat4 uMvp;
                uniform mat4 uModel;
                varying vec3 vNormal;
                varying vec3 vWorld;
                void main() {
                    vec4 world = uModel * vec4(aPosition, 1.0);
                    vWorld = world.xyz;
                    vNormal = normalize(mat3(uModel) * aNormal);
                    gl_Position = uMvp * vec4(aPosition, 1.0);
                }
                """.trimIndent(),
                """
                precision mediump float;
                uniform vec4 uColor;
                uniform float uGloss;
                uniform float uMaterial;
                uniform sampler2D uTexture;
                uniform vec3 uEye;
                varying vec3 vNormal;
                varying vec3 vWorld;
                float hash(vec2 p) {
                    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
                }
                void main() {
                    vec3 n = normalize(vNormal);
                    vec3 albedo = uColor.rgb;
                    if (uMaterial > 0.5 && uMaterial < 1.5) {
                        vec2 woodUv = fract(vWorld.xz * 0.19);
                        vec3 scan = texture2D(uTexture, woodUv).rgb;
                        float scanLuma = dot(scan, vec3(0.299, 0.587, 0.114));
                        float sampleX = dot(texture2D(uTexture, fract(woodUv + vec2(0.004, 0.0))).rgb, vec3(0.299, 0.587, 0.114));
                        float sampleZ = dot(texture2D(uTexture, fract(woodUv + vec2(0.0, 0.004))).rgb, vec3(0.299, 0.587, 0.114));
                        float longGrain = sin(vWorld.x * 10.0 + sin(vWorld.z * 2.7) * 3.2);
                        float fineGrain = sin(vWorld.x * 41.0 + vWorld.z * 1.8) * 0.35;
                        float pore = (hash(vWorld.xz * 46.0) - 0.5);
                        albedo *= 0.76 + scanLuma * 0.92 + longGrain * 0.038 + fineGrain * 0.014 + pore * 0.012;
                        // A small texture-derived normal keeps the varnished
                        // walnut tactile instead of looking like a flat image.
                        float facing = smoothstep(0.12, 0.74, abs(n.y));
                        n = normalize(n + vec3((scanLuma - sampleX) * 2.6, 0.0, (scanLuma - sampleZ) * 2.6) * facing);
                    } else if (uMaterial > 1.5 && uMaterial < 2.5) {
                        vec2 feltUv = fract(vWorld.xz * 0.52);
                        vec3 scan = texture2D(uTexture, feltUv).rgb;
                        float scanLuma = dot(scan, vec3(0.299, 0.587, 0.114));
                        float sampleX = dot(texture2D(uTexture, fract(feltUv + vec2(0.006, 0.0))).rgb, vec3(0.299, 0.587, 0.114));
                        float sampleZ = dot(texture2D(uTexture, fract(feltUv + vec2(0.0, 0.006))).rgb, vec3(0.299, 0.587, 0.114));
                        float weave = sin(vWorld.x * 118.0) * sin(vWorld.z * 118.0);
                        float fibre = (hash(vWorld.xz * 125.0) - 0.5) * 0.045;
                        albedo *= 0.68 + scanLuma * 0.78 + weave * 0.012 + fibre * 0.55;
                        float facing = smoothstep(0.12, 0.74, abs(n.y));
                        n = normalize(n + vec3((scanLuma - sampleX) * 1.15, 0.0, (scanLuma - sampleZ) * 1.15) * facing);
                    } else if (uMaterial > 2.5 && uMaterial < 3.5) {
                        float vein = sin((vWorld.x + vWorld.z) * 5.4 + sin(vWorld.x * 8.7) * 1.4);
                        float marble = smoothstep(0.72, 1.0, abs(vein)) * 0.075;
                        albedo += vec3(marble);
                    } else if (uMaterial > 3.5) {
                        float lacquer = sin(vWorld.y * 23.0 + vWorld.x * 2.0) * 0.014;
                        albedo *= 0.985 + lacquer;
                    }
                    vec3 light = normalize(vec3(-0.42, 0.88, 0.34));
                    vec3 fillLight = normalize(vec3(0.68, 0.42, -0.58));
                    float diffuse = max(dot(n, light), 0.0);
                    float fill = max(dot(n, fillLight), 0.0) * 0.31;
                    // Specular highlights follow the real orbiting camera.
                    // A fixed eye position made glossy pieces look painted on
                    // as soon as the player rotated the board.
                    vec3 viewDir = normalize(uEye - vWorld);
                    vec3 reflected = reflect(-light, n);
                    float specular = pow(max(dot(viewDir, reflected), 0.0), mix(12.0, 72.0, uGloss)) * uGloss;
                    float rim = pow(1.0 - max(dot(viewDir, n), 0.0), 2.4) * 0.10;
                    float groundOcclusion = mix(0.82, 1.0, smoothstep(0.0, 0.65, vWorld.y));
                    vec3 rgb = albedo * (0.38 + diffuse * 0.62 + fill) * groundOcclusion + vec3(specular + rim);
                    rgb = pow(max(rgb, vec3(0.0)), vec3(0.92));
                    gl_FragColor = vec4(rgb, uColor.a);
                }
                """.trimIndent(),
            )
            positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
            normalHandle = GLES20.glGetAttribLocation(program, "aNormal")
            mvpHandle = GLES20.glGetUniformLocation(program, "uMvp")
            modelHandle = GLES20.glGetUniformLocation(program, "uModel")
            colorHandle = GLES20.glGetUniformLocation(program, "uColor")
            glossHandle = GLES20.glGetUniformLocation(program, "uGloss")
            materialHandle = GLES20.glGetUniformLocation(program, "uMaterial")
            textureHandle = GLES20.glGetUniformLocation(program, "uTexture")
            eyeHandle = GLES20.glGetUniformLocation(program, "uEye")
            woodTexture = loadTexture(R.drawable.wapi_game_walnut_texture)
            feltTexture = loadTexture(R.drawable.wapi_game_felt_texture)
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            this.width = width.coerceAtLeast(1)
            this.height = height.coerceAtLeast(1)
            GLES20.glViewport(0, 0, this.width, this.height)
            Matrix.perspectiveM(projection, 0, 38f, this.width.toFloat() / this.height, .1f, 60f)
        }

        override fun onDrawFrame(gl: GL10?) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
            if (!orbiting) {
                yaw = (yaw + yawVelocity).coerceIn(-27f, 27f)
                pitch = (pitch + pitchVelocity).coerceIn(37f, 67f)
                yawVelocity *= .944f
                pitchVelocity *= .944f
            }
            val seconds = (System.nanoTime() - startedAt) / 1_000_000_000f
            val radius = (when (scene) { Scene.LUDO -> 13.8f; Scene.POOL -> 12.5f; else -> 13.0f }) * zoom
            val yawRadians = Math.toRadians(yaw.toDouble())
            val pitchRadians = Math.toRadians(pitch.toDouble())
            eyeX = (sin(yawRadians) * cos(pitchRadians) * radius).toFloat()
            eyeY = (sin(pitchRadians) * radius).toFloat()
            eyeZ = (cos(yawRadians) * cos(pitchRadians) * radius).toFloat()
            Matrix.setLookAtM(view, 0, eyeX, eyeY, eyeZ, 0f, 0f, 0f, 0f, 1f, 0f)
            Matrix.multiplyMM(vp, 0, projection, 0, view, 0)
            Matrix.invertM(inverseVp, 0, vp, 0)
            when (scene) {
                Scene.LUDO -> drawLudo(seconds)
                Scene.POOL -> drawPool(seconds)
                else -> drawStrategy(scene == Scene.CHECKERS, seconds)
            }
        }

        fun pickSquare(screenX: Float, screenY: Float): Int? {
            if (width <= 1 || height <= 1) return null
            val x = screenX / width * 2f - 1f
            val y = 1f - screenY / height * 2f
            val near = unproject(x, y, -1f)
            val far = unproject(x, y, 1f)
            val dy = far[1] - near[1]
            if (abs(dy) < .0001f) return null
            val t = (.20f - near[1]) / dy
            if (t !in 0f..1f) return null
            val worldX = near[0] + (far[0] - near[0]) * t
            val worldZ = near[2] + (far[2] - near[2]) * t
            val dimension = strategyDimension
            val cell = 8f / dimension
            val column = kotlin.math.floor((worldX + 4f) / cell).toInt()
            val row = kotlin.math.floor((worldZ + 4f) / cell).toInt()
            return if (row in 0 until dimension && column in 0 until dimension) row * dimension + column else null
        }

        /** Ray-cast the finger onto the physical cloth instead of treating the
         * perspective screen as a flat normalized table. This keeps cue aiming
         * accurate on wide phones and foldables. */
        fun pickPoolPoint(screenX: Float, screenY: Float): FloatArray? {
            if (width <= 1 || height <= 1) return null
            val x = screenX / width * 2f - 1f
            val y = 1f - screenY / height * 2f
            val near = unproject(x, y, -1f)
            val far = unproject(x, y, 1f)
            val dy = far[1] - near[1]
            if (abs(dy) < .0001f) return null
            val t = (.18f - near[1]) / dy
            if (t !in 0f..1f) return null
            val worldX = near[0] + (far[0] - near[0]) * t
            val worldZ = near[2] + (far[2] - near[2]) * t
            return floatArrayOf(
                (worldX / 10.2f + .5f).coerceIn(.055f, .945f),
                (worldZ / 6.05f + .5f).coerceIn(.075f, .925f),
            )
        }

        private fun unproject(x: Float, y: Float, z: Float): FloatArray {
            val input = floatArrayOf(x, y, z, 1f)
            val output = FloatArray(4)
            Matrix.multiplyMV(output, 0, inverseVp, 0, input, 0)
            val w = output[3].takeIf { abs(it) > .00001f } ?: 1f
            return floatArrayOf(output[0] / w, output[1] / w, output[2] / w)
        }

        private fun drawStrategy(checkers: Boolean, seconds: Float) {
            val woodDark = floatArrayOf(.20f, .075f, .026f, 1f)
            val woodEdge = floatArrayOf(.34f, .14f, .055f, 1f)
            draw(cube, 0f, -.62f, 0f, 7.8f, .05f, 6.4f, floatArrayOf(.025f, .032f, .045f, 1f), .08f, material = 2f)
            draw(cube, 0f, -.20f, 0f, 4.72f, .28f, 4.72f, woodDark, .24f, material = 1f)
            draw(cube, 0f, -.03f, -4.38f, 4.55f, .16f, .28f, woodEdge, .30f, material = 1f)
            draw(cube, 0f, -.03f, 4.38f, 4.55f, .16f, .28f, woodEdge, .30f, material = 1f)
            draw(cube, -4.38f, -.03f, 0f, .28f, .16f, 4.55f, woodEdge, .30f, material = 1f)
            draw(cube, 4.38f, -.03f, 0f, .28f, .16f, 4.55f, woodEdge, .30f, material = 1f)
            val currentBoard = board
            val dimension = if (checkers && currentBoard.size == 100) 10 else 8
            strategyDimension = dimension
            val cell = 8f / dimension
            val pieceScale = cell
            val activeMove = strategyMove
            val moveProgress = activeMove?.let {
                ((System.nanoTime() - it.startedAtNanos) / 280_000_000f).coerceIn(0f, 1f)
            } ?: 1f
            if (activeMove != null && moveProgress >= 1f) strategyMove = null
            for (row in 0 until dimension) for (column in 0 until dimension) {
                val index = row * dimension + column
                val base = if ((row + column) % 2 == 0) floatArrayOf(.84f, .64f, .37f, 1f) else floatArrayOf(.17f, .065f, .026f, 1f)
                val color = when {
                    index == selected -> floatArrayOf(.08f, .58f, 1f, 1f)
                    index in legalTargets -> floatArrayOf(.13f, .72f, .40f, 1f)
                    else -> base
                }
                draw(cube, -4f + cell * .5f + column * cell, .08f, -4f + cell * .5f + row * cell, cell * .495f, .09f, cell * .495f, color, .18f, material = 1f)
            }
            if (currentBoard.size != dimension * dimension) return
            currentBoard.forEachIndexed { index, piece ->
                if (piece.isBlank()) return@forEachIndexed
                val row = index / dimension
                val column = index % dimension
                var x = -4f + cell * .5f + column * cell
                var z = -4f + cell * .5f + row * cell
                var movementLift = 0f
                if (activeMove != null && index == activeMove.to && moveProgress < 1f) {
                    val fromRow = activeMove.from / dimension
                    val fromColumn = activeMove.from % dimension
                    val fromX = -4f + cell * .5f + fromColumn * cell
                    val fromZ = -4f + cell * .5f + fromRow * cell
                    val eased = moveProgress * moveProgress * (3f - 2f * moveProgress)
                    x = fromX + (x - fromX) * eased
                    z = fromZ + (z - fromZ) * eased
                    movementLift = sin(moveProgress * Math.PI).toFloat() * .34f * pieceScale
                }
                val white = WapiGameRules.isWhite(piece)
                val color = if (white) floatArrayOf(.91f, .88f, .76f, 1f) else floatArrayOf(.070f, .082f, .105f, 1f)
                val accent = if (white) floatArrayOf(.98f, .95f, .84f, 1f) else floatArrayOf(.19f, .22f, .28f, 1f)
                drawSoftShadow(x, z, .38f * pieceScale)
                if (checkers) drawChecker(x, z, piece, color, accent, index == selected, seconds, pieceScale, movementLift)
                else drawChessPiece(x, z, piece, color, accent, index == selected, pieceScale, movementLift)
            }
        }

        private fun drawChecker(x: Float, z: Float, piece: String, color: FloatArray, accent: FloatArray, selected: Boolean, seconds: Float, scale: Float, movementLift: Float) {
            val lift = (if (selected) (.11f + sin(seconds * 5f) * .035f) * scale else 0f) + movementLift
            val radius = .405f * scale
            draw(cylinder, x, .27f + lift, z, radius, .105f * scale, radius, color, .84f, material = 4f)
            draw(torus, x, .27f + .10f * scale + lift, z, radius * 1.02f, .085f * scale, radius * 1.02f, accent, .94f, material = 4f)
            draw(cylinder, x, .27f + .12f * scale + lift, z, .34f * scale, .035f * scale, .34f * scale, accent, .91f, material = 4f)
            // Concentric moulded grooves catch the light like a polished
            // tournament checker instead of a flat coloured cylinder.
            draw(torus, x, .27f + .158f * scale + lift, z, .275f * scale, .025f * scale, .275f * scale, color, .96f, material = 4f)
            draw(torus, x, .27f + .166f * scale + lift, z, .185f * scale, .018f * scale, .185f * scale, accent, .97f, material = 4f)
            if (piece == "W" || piece == "B") {
                val gold = floatArrayOf(1f, .67f, .08f, 1f)
                // A king is represented by a second stacked checker, the
                // convention used by physical draughts sets, with a gold inlay.
                draw(cylinder, x, .27f + .285f * scale + lift, z, radius * .96f, .095f * scale, radius * .96f, color, .88f, material = 4f)
                draw(torus, x, .27f + .375f * scale + lift, z, radius * .98f, .075f * scale, radius * .98f, accent, .96f, material = 4f)
                draw(cylinder, x, .27f + .395f * scale + lift, z, .27f * scale, .024f * scale, .27f * scale, gold, .91f, material = 3f)
                draw(torus, x, .27f + .425f * scale + lift, z, .18f * scale, .022f * scale, .18f * scale, gold, .97f, material = 3f)
            }
        }

        private fun drawChessPiece(x: Float, z: Float, piece: String, color: FloatArray, accent: FloatArray, selected: Boolean, scale: Float, movementLift: Float) {
            val lift = (if (selected) .09f * scale else 0f) + movementLift
            val key = piece.lowercase()
            val pieceMaterial = if (WapiGameRules.isWhite(piece)) 3f else 4f
            draw(cylinder, x, .28f + lift, z, .39f * scale, .075f * scale, .39f * scale, color, .90f, material = pieceMaterial)
            draw(torus, x, .35f + lift, z, .39f * scale, .11f * scale, .39f * scale, accent, .94f, material = pieceMaterial)
            draw(cylinder, x, .38f + lift, z, .31f * scale, .055f * scale, .31f * scale, accent, .88f, material = pieceMaterial)
            when (key) {
                "♙", "♟" -> {
                    draw(cone, x, .62f + lift, z, .20f, .25f, .20f, color, .84f)
                    draw(sphere, x, .89f + lift, z, .18f, .18f, .18f, accent, .94f)
                }
                "♖", "♜" -> {
                    draw(cylinder, x, .67f + lift, z, .21f, .30f, .21f, color, .88f)
                    draw(cylinder, x, .98f + lift, z, .31f, .10f, .31f, accent, .90f)
                    repeat(4) { n ->
                        val angle = n * Math.PI / 2.0
                        draw(cube, x + cos(angle).toFloat() * .22f, 1.10f + lift, z + sin(angle).toFloat() * .22f, .10f, .12f, .10f, color, .76f)
                    }
                }
                "♘", "♞" -> {
                    draw(cone, x, .68f + lift, z, .24f, .32f, .24f, color, .87f, rotationX = -12f)
                    draw(sphere, x, .98f + lift, z - .07f, .22f, .29f, .18f, accent, .94f)
                    draw(cone, x, 1.18f + lift, z - .09f, .08f, .18f, .08f, color, .90f, rotationX = -20f)
                }
                "♗", "♝" -> {
                    draw(cone, x, .70f + lift, z, .24f, .34f, .24f, color, .88f)
                    draw(sphere, x, 1.02f + lift, z, .20f, .25f, .20f, accent, .94f)
                    draw(cone, x, 1.25f + lift, z, .07f, .18f, .07f, color, .91f)
                }
                "♕", "♛" -> {
                    draw(cone, x, .72f + lift, z, .26f, .36f, .26f, color, .90f)
                    draw(cylinder, x, 1.03f + lift, z, .27f, .08f, .27f, accent, .92f)
                    repeat(6) { n ->
                        val angle = n * Math.PI / 3.0
                        draw(sphere, x + cos(angle).toFloat() * .20f, 1.22f + lift, z + sin(angle).toFloat() * .20f, .075f, .11f, .075f, color, .96f)
                    }
                    draw(sphere, x, 1.30f + lift, z, .09f, .12f, .09f, accent, .98f)
                }
                else -> {
                    draw(cone, x, .74f + lift, z, .27f, .38f, .27f, color, .91f)
                    draw(sphere, x, 1.12f + lift, z, .16f, .20f, .16f, accent, .96f)
                    draw(cube, x, 1.38f + lift, z, .055f, .18f, .055f, color, .90f)
                    draw(cube, x, 1.45f + lift, z, .15f, .055f, .055f, color, .90f)
                }
            }
        }

        private fun drawLudo(seconds: Float) {
            val colors = listOf(
                floatArrayOf(.88f, .08f, .10f, 1f),
                floatArrayOf(.10f, .60f, .96f, 1f),
                floatArrayOf(.18f, .72f, .20f, 1f),
                floatArrayOf(1f, .72f, .02f, 1f),
            )
            draw(cube, 0f, -.65f, 0f, 7.8f, .05f, 6.4f, floatArrayOf(.025f, .032f, .045f, 1f), .08f, material = 2f)
            draw(cube, 0f, -.22f, 0f, 5.1f, .30f, 5.1f, floatArrayOf(.13f, .055f, .02f, 1f), .24f, material = 1f)
            val path = ludoPath
            val safeSquares = setOf(0, 8, 13, 21, 26, 34, 39, 47)
            val bases = listOf(2 to 2, 2 to 12, 12 to 12, 12 to 2)
            val baseSlots = listOf(
                listOf(1 to 1, 1 to 3, 3 to 1, 3 to 3),
                listOf(1 to 11, 1 to 13, 3 to 11, 3 to 13),
                listOf(11 to 11, 11 to 13, 13 to 11, 13 to 13),
                listOf(11 to 1, 11 to 3, 13 to 1, 13 to 3),
            )
            for (row in 0 until 15) for (column in 0 until 15) {
                val pathIndex = path.indexOf(row to column)
                val baseIndex = bases.indexOf(row to column)
                val zone = when {
                    row < 6 && column < 6 -> 0
                    row < 6 && column > 8 -> 1
                    row > 8 && column > 8 -> 2
                    row > 8 && column < 6 -> 3
                    else -> -1
                }
                val color = when {
                    baseIndex >= 0 -> colors[baseIndex]
                    row in 6..8 && column in 6..8 -> floatArrayOf(.94f, .75f, .08f, 1f)
                    row == 7 && column in 1..6 -> colors[0]
                    column == 7 && row in 1..6 -> colors[1]
                    row == 7 && column in 8..13 -> colors[2]
                    column == 7 && row in 8..13 -> colors[3]
                    pathIndex >= 0 -> floatArrayOf(.88f, .90f, .92f, 1f)
                    zone >= 0 -> colors[zone].copyOf().also { it[3] = 1f }.mapIndexed { i, v -> if (i < 3) v * .76f + .16f else v }.toFloatArray()
                    else -> floatArrayOf(.94f, .94f, .91f, 1f)
                }
                draw(cube, -3.92f + column * .56f, .07f, -3.92f + row * .56f, .274f, .075f, .274f, color, .35f, material = 3f)
            }
            // Safe squares and home slots are physical inlays, not flat UI
            // markers. They make the board state readable while the camera
            // is orbiting and give the tabletop a manufactured finish.
            safeSquares.forEach { absolute ->
                val coordinate = path[absolute]
                val x = -3.92f + coordinate.second * .56f
                val z = -3.92f + coordinate.first * .56f
                draw(torus, x, .17f, z, .16f, .025f, .16f, floatArrayOf(.96f, .74f, .18f, 1f), .72f, material = 3f)
                draw(sphere, x, .20f, z, .035f, .018f, .035f, floatArrayOf(1f, .88f, .40f, 1f), .82f, material = 3f)
            }
            baseSlots.forEachIndexed { player, slots ->
                slots.forEach { (row, column) ->
                    val x = -3.92f + column * .56f
                    val z = -3.92f + row * .56f
                    draw(torus, x, .16f, z, .245f, .025f, .245f, colors[player], .70f, material = 4f)
                }
            }
            val starts = listOf(0, 13, 26, 39)
            val arrivalLanes = listOf(
                (1..6).map { column -> 7 to column },
                (1..6).map { row -> row to 7 },
                (13 downTo 8).map { column -> 7 to column },
                (13 downTo 8).map { row -> row to 7 },
            )
            fun worldCoordinate(player: Int, pawn: Int, progress: Int): Pair<Float, Float> {
                val coordinate = when {
                    progress < 0 -> baseSlots[player][pawn]
                    progress >= 52 -> arrivalLanes[player][(progress - 52).coerceIn(0, 5)]
                    else -> path[(starts[player] + progress) % 52]
                }
                return (-3.92f + coordinate.second * .56f) to (-3.92f + coordinate.first * .56f)
            }
            val activeMove = ludoMove
            val moveProgress = activeMove?.let {
                ((System.nanoTime() - it.startedAtNanos) / 620_000_000f).coerceIn(0f, 1f)
            } ?: 1f
            if (activeMove != null && moveProgress >= 1f) ludoMove = null
            for (player in 0 until 4) {
                val playerPositions = when (ludoPositions.size) {
                    16 -> ludoPositions.subList(player * 4, player * 4 + 4)
                    else -> listOf(ludoPositions.getOrElse(player) { -1 }, -1, -1, -1)
                }
                playerPositions.forEachIndexed { pawn, progress ->
                    val moving = activeMove != null && activeMove.player == player && activeMove.pawn == pawn && moveProgress < 1f
                    val eased = moveProgress * moveProgress * (3f - 2f * moveProgress)
                    val destination = worldCoordinate(player, pawn, progress)
                    val position = if (moving) {
                        val origin = worldCoordinate(player, pawn, activeMove.from)
                        (origin.first + (destination.first - origin.first) * eased) to
                            (origin.second + (destination.second - origin.second) * eased)
                    } else destination
                    val lift = if (moving) sin(moveProgress * Math.PI).toFloat() * .34f else 0f
                    drawPawn(
                        position.first + (pawn % 2) * .035f,
                        position.second + (pawn / 2) * .035f,
                        colors[player],
                        activePlayer == player,
                        seconds + pawn * .22f,
                        lift,
                    )
                }
            }
            // Two correctly proportioned dice: separate bodies, independent
            // values and a shared physical roll above the centre lane.
            val spin = if (dieRolling) seconds * 470f else 0f
            val dieY = .88f + if (dieRolling) abs(sin(seconds * 9f)) * .52f else 0f
            listOf(-.40f to dieOne, .40f to dieTwo).forEachIndexed { index, (dieX, value) ->
                val localSpin = spin * if (index == 0) 1f else -.83f
                if (!dieRolling) drawSoftShadow(dieX, 0f, .28f)
                draw(cube, dieX, dieY, 0f, .30f, .30f, .30f, floatArrayOf(.78f, .84f, .91f, 1f), .55f, rotationX = localSpin, rotationY = localSpin * .73f)
                draw(cube, dieX, dieY, 0f, .278f, .292f, .278f, floatArrayOf(.995f, .998f, 1f, 1f), .92f, rotationX = localSpin, rotationY = localSpin * .73f)
                if (!dieRolling) drawDiePips(value, dieX, 0f, dieY + .298f, .62f)
            }
        }

        private fun drawPool(seconds: Float) {
            val wood = floatArrayOf(.28f, .095f, .026f, 1f)
            val woodLight = floatArrayOf(.48f, .20f, .055f, 1f)
            val felt = floatArrayOf(.015f, .39f, .24f, 1f)
            val pocketLeather = floatArrayOf(.014f, .016f, .019f, 1f)
            val brass = floatArrayOf(.73f, .49f, .14f, 1f)
            draw(cube, 0f, -.72f, 0f, 7.8f, .05f, 5.1f, floatArrayOf(.022f, .028f, .039f, 1f), .08f, material = 2f)
            draw(cube, 0f, -.24f, 0f, 5.15f, .30f, 3.25f, wood, .38f, material = 1f)
            draw(cube, 0f, .02f, 0f, 4.65f, .11f, 2.67f, felt, .20f, material = 2f)
            draw(cube, 0f, .22f, -2.92f, 4.82f, .22f, .25f, woodLight, .60f, material = 1f)
            draw(cube, 0f, .22f, 2.92f, 4.82f, .22f, .25f, woodLight, .60f, material = 1f)
            draw(cube, -4.91f, .22f, 0f, .25f, .22f, 2.70f, woodLight, .60f, material = 1f)
            draw(cube, 4.91f, .22f, 0f, .25f, .22f, 2.70f, woodLight, .60f, material = 1f)
            listOf(-4.60f to -2.55f, 0f to -2.62f, 4.60f to -2.55f, -4.60f to 2.55f, 0f to 2.62f, 4.60f to 2.55f).forEach { (x, z) ->
                draw(cylinder, x, .17f, z, .27f, .05f, .27f, pocketLeather, .04f)
                draw(torus, x, .20f, z, .285f, .042f, .285f, brass, .78f, material = 3f)
            }
            // Small inlaid rail sights make the table readable from every
            // camera angle and ground it as a physical object.
            listOf(-2.55f, 2.55f).forEach { x ->
                draw(cylinder, x, .47f, -2.93f, .045f, .014f, .045f, brass, .82f, material = 3f)
                draw(cylinder, x, .47f, 2.93f, .045f, .014f, .045f, brass, .82f, material = 3f)
            }
            val colors = listOf(
                floatArrayOf(1f, .78f, .04f, 1f),
                floatArrayOf(.04f, .24f, .88f, 1f),
                floatArrayOf(.88f, .04f, .06f, 1f),
                floatArrayOf(.45f, .08f, .64f, 1f),
                floatArrayOf(1f, .31f, .02f, 1f),
                floatArrayOf(.04f, .55f, .20f, 1f),
                floatArrayOf(.46f, .03f, .06f, 1f),
                floatArrayOf(.008f, .012f, .018f, 1f),
            )
            poolBalls.forEach { ball ->
                if (ball.size < 3 || ball[2] < 0f) return@forEach
                val id = ball[2].toInt()
                val x = (ball[0] - .5f) * 10.2f
                val z = (ball[1] - .5f) * 6.05f
                val color = if (id == 0) floatArrayOf(.97f, .98f, 1f, 1f) else colors[(id - 1).mod(colors.size)]
                val velocityX = ball.getOrElse(3) { 0f }
                val velocityY = ball.getOrElse(4) { 0f }
                val speed = sqrt(velocityX * velocityX + velocityY * velocityY)
                // The stripe and number plate rotate with the actual physics
                // velocity passed by the pool engine, rather than a generic
                // animation unrelated to the shot.
                val roll = if (poolMoving && speed > .002f) seconds * speed * 760f + id * 17f else id * 17f
                val rotationX = if (speed > .002f) -velocityY / speed * roll else 0f
                val rotationY = if (speed > .002f) velocityX / speed * roll else roll
                drawSoftShadow(x, z, .24f)
                if (id >= 9) {
                    draw(sphere, x, .38f, z, .255f, .255f, .255f, floatArrayOf(.97f, .975f, .98f, 1f), .95f, rotationX = rotationX, rotationY = rotationY, material = 3f)
                    draw(cylinder, x, .38f, z, .258f, .095f, .258f, color, .91f, rotationY = rotationY, material = 4f)
                } else {
                    draw(sphere, x, .38f, z, .255f, .255f, .255f, color, .96f, rotationX = rotationX, rotationY = rotationY, material = if (id == 0) 3f else 4f)
                }
                if (id != 0) {
                    // Numbered cap: a raised white plate and dark numeral dot
                    // catch the light like a real billiard ball.
                    draw(cylinder, x, .635f, z, .075f, .008f, .075f, floatArrayOf(.98f, .985f, 1f, 1f), .74f, material = 3f)
                    draw(cylinder, x, .646f, z, .024f, .005f, .024f, floatArrayOf(.012f, .016f, .024f, 1f), .58f)
                }
                draw(sphere, x - .07f, .48f, z - .07f, .055f, .035f, .055f, floatArrayOf(1f, 1f, 1f, .82f), .98f)
            }
            if (!poolMoving) {
                val cue = poolBalls.firstOrNull { it.size >= 3 && it[2].toInt() == 0 } ?: return
                val cueX = (cue[0] - .5f) * 10.2f
                val cueZ = (cue[1] - .5f) * 6.05f
                val directionX = cos(poolAim)
                val directionZ = sin(poolAim)
                val distance = 1.8f + poolPower * .22f
                val centerX = cueX - directionX * distance
                val centerZ = cueZ - directionZ * distance
                // Round maple shaft, leather cue tip and weighted butt.
                draw(cylinder, centerX, .46f, centerZ, .045f, distance, .045f, floatArrayOf(.78f, .43f, .15f, 1f), .66f, rotationX = 90f, rotationY = -Math.toDegrees(poolAim.toDouble()).toFloat(), material = 1f)
                draw(cylinder, centerX - directionX * distance * .72f, .46f, centerZ - directionZ * distance * .72f, .082f, .15f, .082f, floatArrayOf(.12f, .045f, .014f, 1f), .58f, rotationX = 90f, rotationY = -Math.toDegrees(poolAim.toDouble()).toFloat(), material = 1f)
                draw(cylinder, cueX - directionX * .32f, .46f, cueZ - directionZ * .32f, .055f, .12f, .055f, floatArrayOf(.94f, .82f, .61f, 1f), .72f, rotationX = 90f, rotationY = -Math.toDegrees(poolAim.toDouble()).toFloat())
                draw(cylinder, cueX - directionX * .19f, .46f, cueZ - directionZ * .19f, .062f, .018f, .062f, floatArrayOf(.035f, .23f, .42f, 1f), .62f, rotationX = 90f, rotationY = -Math.toDegrees(poolAim.toDouble()).toFloat())
            }
        }

        private fun drawPawn(x: Float, z: Float, color: FloatArray, active: Boolean, seconds: Float, moveLift: Float = 0f) {
            val lift = moveLift + if (active) .05f + abs(sin(seconds * 4f)) * .07f else 0f
            drawSoftShadow(x, z, .25f + moveLift * .25f)
            draw(cylinder, x, .25f + lift, z, .26f, .065f, .26f, color, .85f, material = 4f)
            draw(torus, x, .32f + lift, z, .26f, .082f, .26f, color, .96f, material = 4f)
            draw(cone, x, .49f + lift, z, .19f, .25f, .19f, color, .90f, material = 4f)
            draw(sphere, x, .77f + lift, z, .16f, .16f, .16f, color, .96f, material = 4f)
        }

        private fun drawDiePips(value: Int, centerX: Float, centerZ: Float, topY: Float, scale: Float = 1f) {
            val pip = floatArrayOf(.018f, .024f, .040f, 1f)
            val points = when (value) {
                1 -> listOf(0f to 0f)
                2 -> listOf(-.14f to -.14f, .14f to .14f)
                3 -> listOf(-.14f to -.14f, 0f to 0f, .14f to .14f)
                4 -> listOf(-.14f to -.14f, .14f to -.14f, -.14f to .14f, .14f to .14f)
                5 -> listOf(-.14f to -.14f, .14f to -.14f, 0f to 0f, -.14f to .14f, .14f to .14f)
                else -> listOf(-.14f to -.16f, .14f to -.16f, -.14f to 0f, .14f to 0f, -.14f to .16f, .14f to .16f)
            }
            points.forEach { (x, z) ->
                draw(sphere, centerX + x * scale, topY, centerZ + z * scale, .054f * scale, .025f * scale, .054f * scale, pip, .88f, material = 3f)
            }
        }

        private fun drawSoftShadow(x: Float, z: Float, radius: Float) {
            draw(cylinder, x + .08f, .205f, z + .10f, radius, .010f, radius, floatArrayOf(.005f, .007f, .010f, .30f), .01f)
            draw(cylinder, x + .13f, .202f, z + .16f, radius * 1.16f, .007f, radius * 1.16f, floatArrayOf(.005f, .007f, .010f, .13f), .01f)
        }

        private fun draw(
            mesh: Mesh,
            x: Float,
            y: Float,
            z: Float,
            sx: Float,
            sy: Float,
            sz: Float,
            color: FloatArray,
            gloss: Float,
            rotationX: Float = 0f,
            rotationY: Float = 0f,
            material: Float = 0f,
        ) {
            Matrix.setIdentityM(model, 0)
            Matrix.translateM(model, 0, x, y, z)
            if (rotationX != 0f) Matrix.rotateM(model, 0, rotationX, 1f, 0f, 0f)
            if (rotationY != 0f) Matrix.rotateM(model, 0, rotationY, 0f, 1f, 0f)
            Matrix.scaleM(model, 0, sx, sy, sz)
            Matrix.multiplyMM(viewModel, 0, view, 0, model, 0)
            Matrix.multiplyMM(mvp, 0, projection, 0, viewModel, 0)
            GLES20.glUseProgram(program)
            GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvp, 0)
            GLES20.glUniformMatrix4fv(modelHandle, 1, false, model, 0)
            GLES20.glUniform4fv(colorHandle, 1, color, 0)
            GLES20.glUniform1f(glossHandle, gloss)
            GLES20.glUniform1f(materialHandle, material)
            GLES20.glUniform3f(eyeHandle, eyeX, eyeY, eyeZ)
            val boundTexture = when {
                material > .5f && material < 1.5f -> woodTexture
                material > 1.5f && material < 2.5f -> feltTexture
                else -> 0
            }
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, boundTexture)
            GLES20.glUniform1i(textureHandle, 0)
            mesh.vertices.position(0)
            GLES20.glEnableVertexAttribArray(positionHandle)
            GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 24, mesh.vertices)
            mesh.vertices.position(3)
            GLES20.glEnableVertexAttribArray(normalHandle)
            GLES20.glVertexAttribPointer(normalHandle, 3, GLES20.GL_FLOAT, false, 24, mesh.vertices)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, mesh.count)
            GLES20.glDisableVertexAttribArray(positionHandle)
            GLES20.glDisableVertexAttribArray(normalHandle)
        }

        private fun loadTexture(resourceId: Int): Int {
            val ids = IntArray(1)
            GLES20.glGenTextures(1, ids, 0)
            val texture = ids[0]
            val bitmap = BitmapFactory.decodeResource(context.resources, resourceId) ?: return 0
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
            GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
            bitmap.recycle()
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
            return texture
        }

        private fun createProgram(vertexSource: String, fragmentSource: String): Int {
            val vertex = compile(GLES20.GL_VERTEX_SHADER, vertexSource)
            val fragment = compile(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
            return GLES20.glCreateProgram().also {
                GLES20.glAttachShader(it, vertex)
                GLES20.glAttachShader(it, fragment)
                GLES20.glLinkProgram(it)
            }
        }

        private fun compile(type: Int, source: String): Int = GLES20.glCreateShader(type).also {
            GLES20.glShaderSource(it, source)
            GLES20.glCompileShader(it)
        }

        private companion object {
            val ludoPath = listOf(
                6 to 0, 6 to 1, 6 to 2, 6 to 3, 6 to 4, 6 to 5, 5 to 6, 4 to 6, 3 to 6, 2 to 6, 1 to 6, 0 to 6, 0 to 7,
                0 to 8, 1 to 8, 2 to 8, 3 to 8, 4 to 8, 5 to 8, 6 to 9, 6 to 10, 6 to 11, 6 to 12, 6 to 13, 6 to 14, 7 to 14,
                8 to 14, 8 to 13, 8 to 12, 8 to 11, 8 to 10, 8 to 9, 9 to 8, 10 to 8, 11 to 8, 12 to 8, 13 to 8, 14 to 8, 14 to 7,
                14 to 6, 13 to 6, 12 to 6, 11 to 6, 10 to 6, 9 to 6, 8 to 5, 8 to 4, 8 to 3, 8 to 2, 8 to 1, 8 to 0, 7 to 0,
            )

            fun buffer(values: List<Float>): FloatBuffer = ByteBuffer.allocateDirect(values.size * Float.SIZE_BYTES)
                .order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(values.toFloatArray()); position(0) }

            fun cubeMesh(): Mesh {
                val data = mutableListOf<Float>()
                fun face(normal: FloatArray, a: FloatArray, b: FloatArray, c: FloatArray, d: FloatArray) {
                    listOf(a, b, c, a, c, d).forEach { point -> data += point.toList(); data += normal.toList() }
                }
                face(floatArrayOf(0f,0f,1f), floatArrayOf(-1f,-1f,1f), floatArrayOf(1f,-1f,1f), floatArrayOf(1f,1f,1f), floatArrayOf(-1f,1f,1f))
                face(floatArrayOf(0f,0f,-1f), floatArrayOf(1f,-1f,-1f), floatArrayOf(-1f,-1f,-1f), floatArrayOf(-1f,1f,-1f), floatArrayOf(1f,1f,-1f))
                face(floatArrayOf(-1f,0f,0f), floatArrayOf(-1f,-1f,-1f), floatArrayOf(-1f,-1f,1f), floatArrayOf(-1f,1f,1f), floatArrayOf(-1f,1f,-1f))
                face(floatArrayOf(1f,0f,0f), floatArrayOf(1f,-1f,1f), floatArrayOf(1f,-1f,-1f), floatArrayOf(1f,1f,-1f), floatArrayOf(1f,1f,1f))
                face(floatArrayOf(0f,1f,0f), floatArrayOf(-1f,1f,1f), floatArrayOf(1f,1f,1f), floatArrayOf(1f,1f,-1f), floatArrayOf(-1f,1f,-1f))
                face(floatArrayOf(0f,-1f,0f), floatArrayOf(-1f,-1f,-1f), floatArrayOf(1f,-1f,-1f), floatArrayOf(1f,-1f,1f), floatArrayOf(-1f,-1f,1f))
                return Mesh(buffer(data), data.size / 6)
            }

            fun cylinderMesh(segments: Int): Mesh {
                val data = mutableListOf<Float>()
                fun vertex(x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float) { data += listOf(x,y,z,nx,ny,nz) }
                repeat(segments) { index ->
                    val a0 = index * Math.PI * 2.0 / segments
                    val a1 = (index + 1) * Math.PI * 2.0 / segments
                    val x0 = cos(a0).toFloat(); val z0 = sin(a0).toFloat(); val x1 = cos(a1).toFloat(); val z1 = sin(a1).toFloat()
                    vertex(x0,-1f,z0,x0,0f,z0); vertex(x1,-1f,z1,x1,0f,z1); vertex(x1,1f,z1,x1,0f,z1)
                    vertex(x0,-1f,z0,x0,0f,z0); vertex(x1,1f,z1,x1,0f,z1); vertex(x0,1f,z0,x0,0f,z0)
                    vertex(0f,1f,0f,0f,1f,0f); vertex(x0,1f,z0,0f,1f,0f); vertex(x1,1f,z1,0f,1f,0f)
                    vertex(0f,-1f,0f,0f,-1f,0f); vertex(x1,-1f,z1,0f,-1f,0f); vertex(x0,-1f,z0,0f,-1f,0f)
                }
                return Mesh(buffer(data), data.size / 6)
            }

            fun coneMesh(segments: Int): Mesh {
                val data = mutableListOf<Float>()
                fun vertex(x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float) { data += listOf(x,y,z,nx,ny,nz) }
                repeat(segments) { index ->
                    val a0 = index * Math.PI * 2.0 / segments
                    val a1 = (index + 1) * Math.PI * 2.0 / segments
                    val x0 = cos(a0).toFloat(); val z0 = sin(a0).toFloat(); val x1 = cos(a1).toFloat(); val z1 = sin(a1).toFloat()
                    val mid = (a0 + a1) * .5
                    val nx = cos(mid).toFloat(); val nz = sin(mid).toFloat()
                    vertex(x0,-1f,z0,nx,.48f,nz); vertex(x1,-1f,z1,nx,.48f,nz); vertex(0f,1f,0f,nx,.48f,nz)
                    vertex(0f,-1f,0f,0f,-1f,0f); vertex(x1,-1f,z1,0f,-1f,0f); vertex(x0,-1f,z0,0f,-1f,0f)
                }
                return Mesh(buffer(data), data.size / 6)
            }

            fun sphereMesh(segments: Int, rings: Int): Mesh {
                val data = mutableListOf<Float>()
                fun vertex(phi: Double, theta: Double) {
                    val x = (sin(phi) * cos(theta)).toFloat(); val y = cos(phi).toFloat(); val z = (sin(phi) * sin(theta)).toFloat()
                    data += listOf(x,y,z,x,y,z)
                }
                repeat(rings) { ring ->
                    val p0 = Math.PI * ring / rings; val p1 = Math.PI * (ring + 1) / rings
                    repeat(segments) { segment ->
                        val t0 = Math.PI * 2 * segment / segments; val t1 = Math.PI * 2 * (segment + 1) / segments
                        vertex(p0,t0); vertex(p1,t0); vertex(p1,t1)
                        vertex(p0,t0); vertex(p1,t1); vertex(p0,t1)
                    }
                }
                return Mesh(buffer(data), data.size / 6)
            }

            fun torusMesh(majorSegments: Int, minorSegments: Int): Mesh {
                val data = mutableListOf<Float>()
                fun vertex(major: Double, minor: Double) {
                    val majorRadius = .78
                    val tubeRadius = .22
                    val ring = majorRadius + tubeRadius * cos(minor)
                    val x = (ring * cos(major)).toFloat()
                    val y = (tubeRadius * sin(minor)).toFloat()
                    val z = (ring * sin(major)).toFloat()
                    val nx = (cos(minor) * cos(major)).toFloat()
                    val ny = sin(minor).toFloat()
                    val nz = (cos(minor) * sin(major)).toFloat()
                    data += listOf(x, y, z, nx, ny, nz)
                }
                repeat(majorSegments) { majorIndex ->
                    val a0 = Math.PI * 2.0 * majorIndex / majorSegments
                    val a1 = Math.PI * 2.0 * (majorIndex + 1) / majorSegments
                    repeat(minorSegments) { minorIndex ->
                        val b0 = Math.PI * 2.0 * minorIndex / minorSegments
                        val b1 = Math.PI * 2.0 * (minorIndex + 1) / minorSegments
                        vertex(a0, b0); vertex(a1, b0); vertex(a1, b1)
                        vertex(a0, b0); vertex(a1, b1); vertex(a0, b1)
                    }
                }
                return Mesh(buffer(data), data.size / 6)
            }
        }
    }
}
