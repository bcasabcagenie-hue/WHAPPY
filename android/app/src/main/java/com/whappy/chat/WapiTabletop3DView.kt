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
import kotlin.math.sign
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
    private val touchInput = WapiTabletopInput(android.view.ViewConfiguration.get(context).scaledTouchSlop.toFloat())
    /**
     * The old renderer drew sixty frames per second even while a board was
     * completely still.  Besides heating phones this made chess, dames and
     * ludo feel sluggish because they competed with the app UI for every
     * frame.  We now wake OpenGL only for a move, a roll, a cue stroke or
     * rolling balls.
     */
    private val frameTicker = object : Runnable {
        override fun run() {
            requestRender()
            if (tabletopRenderer.needsAnimationFrame()) postDelayed(this, 16L)
        }
    }
    var onSquareTapped: ((Int) -> Unit)? = null
    var onLudoPawnTapped: ((Int) -> Unit)? = null
    var onPoolGesture: ((Float, Float, Boolean) -> Unit)? = null
    /** Start/end points of an intentional cue pull, with a 10–100 power value. */
    var onPoolPullShot: ((Float, Float, Float, Float, Int) -> Unit)? = null
    private var poolPullOrigin: FloatArray? = null
    private var poolPullArmed = false
    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            if (tabletopRenderer.scene != Scene.POOL) tabletopRenderer.zoomBy(detector.scaleFactor)
            return true
        }
    })
    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(event: MotionEvent): Boolean = true

        override fun onDoubleTap(event: MotionEvent): Boolean {
            // A quick piece/destination pair is a move, not a camera reset.
            if (tabletopRenderer.selected >= 0 || tabletopRenderer.selectableLudoPawns.isNotEmpty()) return false
            touchInput.cancel()
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
        renderMode = RENDERMODE_WHEN_DIRTY
        preserveEGLContextOnPause = true
    }

    private fun requestGameFrame() {
        removeCallbacks(frameTicker)
        requestRender()
        if (tabletopRenderer.needsAnimationFrame()) postDelayed(frameTicker, 16L)
    }

    fun setStrategyScene(scene: Scene, board: List<String>, selected: Int, legalTargets: Set<Int>) {
        tabletopRenderer.scene = scene
        tabletopRenderer.updateStrategyBoard(board)
        tabletopRenderer.selected = selected
        tabletopRenderer.legalTargets = legalTargets.toSet()
        requestGameFrame()
    }

    fun setLudoScene(
        positions: List<Int>,
        activePlayer: Int,
        dieOne: Int,
        dieTwo: Int,
        rolling: Boolean,
        selectablePawns: Set<Int> = emptySet(),
    ) {
        tabletopRenderer.scene = Scene.LUDO
        tabletopRenderer.updateLudoPositions(positions)
        tabletopRenderer.activePlayer = activePlayer
        tabletopRenderer.dieOne = dieOne.coerceIn(1, 6)
        tabletopRenderer.dieTwo = dieTwo.coerceIn(1, 6)
        tabletopRenderer.dieRolling = rolling
        tabletopRenderer.selectableLudoPawns = selectablePawns.toSet()
        requestGameFrame()
    }

    fun setPoolScene(
        balls: List<FloatArray>,
        aimAngle: Float,
        power: Int,
        sideSpin: Float = 0f,
        followSpin: Float = 0f,
        aimBonus: Int = 0,
        moving: Boolean,
        cueStroke: Float = 0f,
        cueInHand: Boolean = false,
        tableTheme: String = "competitionBlue",
        cueStyle: String = "maple",
    ) {
        tabletopRenderer.enterPoolScene()
        tabletopRenderer.updatePoolBalls(balls)
        tabletopRenderer.poolAim = aimAngle
        tabletopRenderer.poolPower = power.coerceIn(1, 100)
        tabletopRenderer.poolSideSpin = sideSpin.coerceIn(-1f, 1f)
        tabletopRenderer.poolFollowSpin = followSpin.coerceIn(-1f, 1f)
        tabletopRenderer.poolAimBonus = aimBonus.coerceIn(0, 4)
        tabletopRenderer.poolMoving = moving
        tabletopRenderer.poolCueStroke = cueStroke.coerceIn(0f, 1f)
        tabletopRenderer.poolCueInHand = cueInHand
        tabletopRenderer.poolTableTheme = tableTheme
        tabletopRenderer.poolCueStyle = cueStyle
        requestGameFrame()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) touchInput.begin(event.x, event.y)
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        if (event.pointerCount > 1 || scaleDetector.isInProgress) {
            touchInput.cancel()
            tabletopRenderer.endOrbit()
            return true
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                previousX = event.x
                previousY = event.y
                tabletopRenderer.beginOrbit()
                if (tabletopRenderer.scene == Scene.POOL) {
                    // Aiming and striking are separate gestures.  Before this
                    // guard, any swipe on the cloth was treated as a shot,
                    // making it impossible to deliberately line up a ball.
                    poolPullArmed = tabletopRenderer.isPoolCuePullStart(event.x, event.y)
                    poolPullOrigin = if (poolPullArmed) {
                        tabletopRenderer.pickPoolPoint(event.x, event.y)
                    } else {
                        null
                    }
                    if (!poolPullArmed) dispatchPoolGesture(event, released = false)
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (touchInput.cancelled) return true
                touchInput.move(event.x, event.y)
                if (tabletopRenderer.scene == Scene.POOL) {
                    if (!poolPullArmed) dispatchPoolGesture(event, released = false)
                    requestGameFrame()
                    return true
                }
                val dx = event.x - previousX
                val dy = event.y - previousY
                previousX = event.x
                previousY = event.y
                if (touchInput.wasDragged) {
                    tabletopRenderer.orbit(dx, dy)
                    requestGameFrame()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                val tapped = touchInput.finish(event.x, event.y)
                performClick()
                tabletopRenderer.endOrbit()
                requestGameFrame()
                if (tabletopRenderer.scene == Scene.POOL) {
                    if (!touchInput.cancelled) {
                        if (poolPullArmed) dispatchPoolPullShot(event)
                        else dispatchPoolGesture(event, released = true)
                    }
                    poolPullOrigin = null
                    poolPullArmed = false
                } else if (tapped) {
                    if (tabletopRenderer.scene == Scene.LUDO) {
                        tabletopRenderer.pickLudoPawn(event.x, event.y)?.let { pawn ->
                            post { onLudoPawnTapped?.invoke(pawn) }
                        }
                    } else {
                        tabletopRenderer.pickSquare(event.x, event.y)?.let { square ->
                            post { onSquareTapped?.invoke(square) }
                        }
                    }
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                touchInput.cancel()
                tabletopRenderer.endOrbit()
                poolPullOrigin = null
                poolPullArmed = false
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

    /**
     * Pool uses the same physical gesture as a real cue: drag backwards then
     * release. A small tap is only an aiming gesture and can never fire a
     * shot. Keeping this detection beside the ray-caster makes it identical
     * for Compose and Flutter platform-view hosts.
     */
    private fun dispatchPoolPullShot(event: MotionEvent) {
        val start = poolPullOrigin ?: return
        val end = tabletopRenderer.pickPoolPoint(event.x, event.y) ?: return
        val dx = start[0] - end[0]
        val dy = start[1] - end[1]
        val distance = sqrt(dx * dx + dy * dy)
        if (distance < .045f) return
        val power = (distance * 260f + 12f).toInt().coerceIn(10, 100)
        post { onPoolPullShot?.invoke(start[0], start[1], end[0], end[1], power) }
    }

    private data class Mesh(val vertices: FloatBuffer, val count: Int)
    private data class StrategyMove(val from: Int, val to: Int, val startedAtNanos: Long)
    private data class PoolDrop(val id: Int, val x: Float, val z: Float, val startedAtNanos: Long)
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
        @Volatile var selectableLudoPawns: Set<Int> = emptySet()
        @Volatile var poolBalls: List<FloatArray> = emptyList()
        @Volatile private var poolDrops: List<PoolDrop> = emptyList()
        @Volatile var poolAim: Float = 0f
        @Volatile var poolPower: Int = 45
        @Volatile var poolSideSpin: Float = 0f
        @Volatile var poolFollowSpin: Float = 0f
        @Volatile var poolAimBonus: Int = 0
        @Volatile var poolMoving: Boolean = false
        @Volatile var poolCueStroke: Float = 0f
        @Volatile var poolCueInHand: Boolean = false
        @Volatile var poolTableTheme: String = "competitionBlue"
        @Volatile var poolCueStyle: String = "maple"
        @Volatile private var strategyMove: StrategyMove? = null
        @Volatile private var ludoMove: LudoMove? = null

        private var width = 1
        private var height = 1
        private var poolCameraRadius = WapiPoolPresentation.cameraDistance(1f)
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
        private var scaleHandle = 0
        private var cutoutHandle = 0
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
        @Volatile private var inputInverseVp = FloatArray(16)
        private val cube = cubeMesh()
        private val rail = railMesh()
        private val poolCushion = poolCushionMesh()
        private val cylinder = cylinderMesh(48)
        private val cone = coneMesh(48)
        private val chessStem = chessStemMesh(64)
        private val cueFrustum = frustumMesh(48, .43f)
        private val sphere = sphereMesh(40, 24)
        private val torus = torusMesh(48, 14)
        private val pocketRim = torusMesh(48, 10, .92, .08)
        private val flushPocketLip = torusMesh(64, 10, .96, .04)
        private val pocketLiner = pocketLinerMesh(48)
        private val ballRoll = mutableMapOf<Int, FloatArray>()
        private val startedAt = System.nanoTime()

        /** True only while visible state is actually changing. */
        fun needsAnimationFrame(): Boolean = when (scene) {
            Scene.POOL -> poolMoving || poolCueStroke > .001f || poolDrops.isNotEmpty()
            Scene.LUDO -> dieRolling || ludoMove != null
            Scene.CHECKERS, Scene.CHESS -> strategyMove != null || orbiting ||
                abs(yawVelocity) > .01f || abs(pitchVelocity) > .01f
        }

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

        fun updatePoolBalls(nextBalls: List<FloatArray>) {
            val previousById = poolBalls.filter { it.size >= 6 }.associateBy { it[2].toInt() }
            val now = System.nanoTime()
            val newlyDropped = nextBalls.mapNotNull { next ->
                if (next.size < 6 || next[5] < .5f) return@mapNotNull null
                val id = next[2].toInt()
                val previous = previousById[id] ?: return@mapNotNull null
                if (previous[5] >= .5f) null else PoolDrop(
                    id = id,
                    x = (previous[0] - .5f) * 10.2f,
                    z = (previous[1] - .5f) * 5.10f,
                    startedAtNanos = now,
                )
            }
            if (newlyDropped.isNotEmpty()) poolDrops = (poolDrops + newlyDropped).takeLast(8)
            poolBalls = nextBalls.map(FloatArray::copyOf)
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
            pitch = if (scene == Scene.POOL) 68f else 49f
            zoom = if (scene == Scene.POOL) .95f else 1f
            yawVelocity = 0f
            pitchVelocity = 0f
        }

        fun enterPoolScene() {
            if (scene == Scene.POOL) return
            scene = Scene.POOL
            yaw = 0f
            pitch = 68f
            zoom = .95f
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
                uniform mediump vec3 uScale;
                varying vec3 vNormal;
                varying vec3 vWorld;
                varying vec3 vLocal;
                void main() {
                    vec4 world = uModel * vec4(aPosition, 1.0);
                    vWorld = world.xyz;
                    vLocal = aPosition;
                    // Inverse-transpose for rotation + nonuniform scale.
                    highp vec3 normalScale = max(vec3(uScale), vec3(0.001));
                    vNormal = normalize(mat3(uModel) * (aNormal / (normalScale * normalScale)));
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
                uniform mediump vec3 uScale;
                uniform float uPoolCutout;
                varying vec3 vNormal;
                varying vec3 vWorld;
                varying vec3 vLocal;
                float hash(vec2 p) {
                    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
                }
                void main() {
                    if (uPoolCutout > 0.5) {
                        vec2 q = vec2(min(abs(vWorld.x), abs(abs(vWorld.x) - 4.539)), abs(abs(vWorld.z) - 2.193));
                        if (length(q) < ${WapiPoolPresentation.pocketOpeningRadius}) discard;
                    }
                    vec3 n = normalize(vNormal);
                    vec3 albedo = uColor.rgb;
                    if ((uMaterial > 0.5 && uMaterial < 1.5) || (uMaterial > 6.5 && uMaterial < 7.5)) {
                        // Grain follows each rail and the length of the shaft.
                        vec3 local = vLocal * uScale;
                        vec2 woodUv = uMaterial > 6.5 ? vec2(local.y * 0.22, vLocal.x * 0.28) : (uScale.x > uScale.z ? local.xz : local.zx) * vec2(0.16, 0.70);
                        woodUv = fract(woodUv);
                        vec3 scan = texture2D(uTexture, woodUv).rgb;
                        float scanLuma = dot(scan, vec3(0.299, 0.587, 0.114));
                        float sampleX = dot(texture2D(uTexture, fract(woodUv + vec2(0.004, 0.0))).rgb, vec3(0.299, 0.587, 0.114));
                        float sampleZ = dot(texture2D(uTexture, fract(woodUv + vec2(0.0, 0.004))).rgb, vec3(0.299, 0.587, 0.114));
                        float grain = sin(woodUv.y * 240.0 + sin(woodUv.x * 8.0) * 1.4) * 0.026;
                        albedo *= 0.58 + scanLuma * 1.65 + grain;
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
                        // Directional nap makes the cloth change very subtly
                        // with the camera/light, like brushed worsted rather
                        // than a flat blue painted plane.
                        vec2 napAxis = normalize(vec2(0.82, 0.57));
                        float nap = sin(dot(vWorld.xz, napAxis) * 410.0) * 0.010;
                        float railShade = smoothstep(1.56, 2.28, max(abs(vWorld.z), abs(vWorld.x) * 0.48));
                        albedo *= 0.68 + scanLuma * 0.78 + weave * 0.012 + fibre * 0.55 + nap;
                        albedo *= 1.0 - railShade * 0.075;
                        float facing = smoothstep(0.12, 0.74, abs(n.y));
                        n = normalize(n + vec3((scanLuma - sampleX) * 1.15, 0.0, (scanLuma - sampleZ) * 1.15) * facing);
                    } else if (uMaterial > 2.5 && uMaterial < 3.5) {
                        float vein = sin((vWorld.x + vWorld.z) * 5.4 + sin(vWorld.x * 8.7) * 1.4);
                        float marble = smoothstep(0.72, 1.0, abs(vein)) * 0.075;
                        albedo += vec3(marble);
                    } else if (uMaterial > 5.5 && uMaterial < 6.5) {
                        // The stripe is shaded on the sphere itself. Separate
                        // torus meshes made striped balls look hollow.
                        float stripe = 1.0 - smoothstep(0.40, 0.54, abs(vLocal.y));
                        albedo = mix(vec3(0.975, 0.98, 0.99), uColor.rgb, stripe);
                        float resin = 0.985 + sin((vWorld.x + vWorld.y + vWorld.z) * 17.0) * 0.006;
                        albedo *= resin;
                    } else if (uMaterial > 4.5 && uMaterial < 5.5) {
                        // Polished phenolic resin used by billiard balls. Keep
                        // the colour clean and let the clear coat carry the
                        // reflections instead of adding a painted white blob.
                        float resin = 0.985 + sin((vWorld.x + vWorld.y + vWorld.z) * 17.0) * 0.006;
                        albedo *= resin;
                    } else if (uMaterial > 8.5 && uMaterial < 9.5) {
                        // Fine, matte glove fabric — no marble veins or skin.
                        albedo *= 0.97 + hash(vWorld.xz * 180.0) * 0.03;
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
                    float billiard = step(4.5, uMaterial) * (1.0 - step(6.5, uMaterial));
                    float specular = pow(max(dot(viewDir, reflected), 0.0), mix(20.0, 96.0, max(uGloss, billiard))) * mix(uGloss * 0.48, 0.62, billiard);
                    vec3 secondLight = normalize(vec3(0.22, 0.96, -0.18));
                    float clearCoat = pow(max(dot(viewDir, reflect(-secondLight, n)), 0.0), 90.0) * billiard * 0.34;
                    float rim = pow(1.0 - max(dot(viewDir, n), 0.0), 2.4) * 0.10;
                    if ((uMaterial > 1.5 && uMaterial < 2.5) || uMaterial > 8.5) {
                        specular *= 0.10;
                        rim *= 0.10;
                    }
                    float groundOcclusion = mix(0.82, 1.0, smoothstep(0.0, 0.65, vWorld.y));
                    vec3 rgb = albedo * (0.38 + diffuse * 0.62 + fill) * groundOcclusion + vec3(specular + clearCoat + rim);
                    rgb = pow(max(rgb, vec3(0.0)), vec3(0.92));
                    // Deep pocket interiors must stay black, not catch the
                    // same white highlight as polished balls and metal.
                    if (uMaterial > 7.5 && uMaterial < 8.5) rgb = uColor.rgb * (0.25 + 0.65 * smoothstep(-0.40, 0.16, vWorld.y));
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
            scaleHandle = GLES20.glGetUniformLocation(program, "uScale")
            cutoutHandle = GLES20.glGetUniformLocation(program, "uPoolCutout")
            woodTexture = loadTexture(R.drawable.wapi_game_walnut_texture)
            feltTexture = loadTexture(R.drawable.wapi_game_felt_texture_competition)
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            this.width = width.coerceAtLeast(1)
            this.height = height.coerceAtLeast(1)
            poolCameraRadius = WapiPoolPresentation.cameraDistance(this.width.toFloat() / this.height)
            GLES20.glViewport(0, 0, this.width, this.height)
            Matrix.perspectiveM(projection, 0, 38f, this.width.toFloat() / this.height, .1f, 60f)
        }

        override fun onDrawFrame(gl: GL10?) {
            val seconds = (System.nanoTime() - startedAt) / 1_000_000_000f
            if (scene == Scene.POOL) {
                // A fixed room exposure avoids visible flashing on large
                // Android displays while preserving the table's contrast.
                GLES20.glClearColor(.004f, .018f, .046f, 1f)
            } else {
                GLES20.glClearColor(.035f, .045f, .065f, 1f)
            }
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
            Matrix.perspectiveM(projection, 0, if (scene == Scene.POOL) WapiPoolPresentation.fieldOfViewDegrees else 38f, width.toFloat() / height, .1f, 80f)
            if (scene == Scene.POOL) {
                yaw = 0f
                pitch = WapiPoolPresentation.pitchDegrees
                yawVelocity = 0f
                pitchVelocity = 0f
            } else if (!orbiting) {
                yaw = (yaw + yawVelocity).coerceIn(-27f, 27f)
                pitch = (pitch + pitchVelocity).coerceIn(37f, 67f)
                yawVelocity *= .944f
                pitchVelocity *= .944f
            }
            val radius = if (scene == Scene.POOL) poolCameraRadius
                else WapiPoolPresentation.strategyCameraDistance(width.toFloat() / height, pitch, yaw) * zoom
            val yawRadians = Math.toRadians(yaw.toDouble())
            val pitchRadians = Math.toRadians(pitch.toDouble())
            // The player's reference frame stays fixed during and after a shot.
            eyeX = (sin(yawRadians) * cos(pitchRadians) * radius).toFloat()
            eyeY = (sin(pitchRadians) * radius).toFloat()
            eyeZ = (cos(yawRadians) * cos(pitchRadians) * radius).toFloat()
            Matrix.setLookAtM(view, 0, eyeX, eyeY, eyeZ, 0f, 0f, 0f, 0f, 1f, 0f)
            Matrix.multiplyMM(vp, 0, projection, 0, view, 0)
            Matrix.invertM(inverseVp, 0, vp, 0)
            // Publish an immutable matrix to the UI thread: a tap must not
            // unproject through half of the previous and half of the next frame.
            inputInverseVp = inverseVp.copyOf()
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
            val camera = inputInverseVp
            val near = unproject(x, y, -1f, camera)
            val far = unproject(x, y, 1f, camera)
            val dimension = strategyDimension
            val cell = 8f / dimension
            val shapes = buildList {
                board.forEachIndexed { index, piece ->
                    if (piece.isBlank()) return@forEachIndexed
                    val px = -4f + (index % dimension + .5f) * cell
                    val pz = -4f + (index / dimension + .5f) * cell
                    val lift = if (index == selected) .09f else 0f
                    add(WapiPickSphere(index, px, .30f + lift, pz, .38f * cell))
                    if (scene == Scene.CHESS) {
                        add(WapiPickSphere(index, px, .65f + lift, pz, .22f))
                        val head = when (piece) {
                            "♙", "♟" -> .94f
                            "♖", "♜" -> 1.08f
                            "♗", "♝", "♘", "♞" -> 1.12f
                            else -> 1.28f
                        }
                        add(WapiPickSphere(index, px, head + lift, pz, .25f))
                    }
                }
            }
            pickWapiPiece(near, far, shapes)?.let { return it }
            val dy = far[1] - near[1]
            if (abs(dy) < .0001f) return null
            val t = (.20f - near[1]) / dy
            if (t !in 0f..1f) return null
            val worldX = near[0] + (far[0] - near[0]) * t
            val worldZ = near[2] + (far[2] - near[2]) * t
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
                (worldZ / 5.10f + .5f).coerceIn(.065f, .935f),
            )
        }

        /**
         * A shot can begin only on the visible cue, behind the white ball.
         * This preserves a natural two-stage interaction: point on the cloth
         * to aim, then pull the cue back and release to strike.
         */
        fun isPoolCuePullStart(screenX: Float, screenY: Float): Boolean {
            if (poolMoving || poolCueInHand) return false
            val point = pickPoolPoint(screenX, screenY) ?: return false
            val cue = poolBalls.firstOrNull { it.size >= 6 && it[2].toInt() == 0 && it[5] < .5f } ?: return false
            val cueX = (cue[0] - .5f) * WAPI_POOL_WORLD_WIDTH
            val cueZ = (cue[1] - .5f) * WAPI_POOL_WORLD_HEIGHT
            val pointX = (point[0] - .5f) * WAPI_POOL_WORLD_WIDTH
            val pointZ = (point[1] - .5f) * WAPI_POOL_WORLD_HEIGHT
            val directionX = cos(poolAim)
            val directionZ = sin(poolAim)
            val relativeX = pointX - cueX
            val relativeZ = pointZ - cueZ
            // Positive means behind the cue ball, in the rendered shaft area.
            val behind = -(relativeX * directionX + relativeZ * directionZ)
            val lateral = abs(relativeX * directionZ - relativeZ * directionX)
            return behind in .12f..3.55f && lateral <= .28f
        }

        /** Hit-tests only the pawns that the rule engine marked as legal. */
        fun pickLudoPawn(screenX: Float, screenY: Float): Int? {
            if (width <= 1 || height <= 1 || selectableLudoPawns.isEmpty()) return null
            val x = screenX / width * 2f - 1f
            val y = 1f - screenY / height * 2f
            val near = unproject(x, y, -1f)
            val far = unproject(x, y, 1f)
            val dy = far[1] - near[1]
            if (abs(dy) < .0001f) return null
            val t = (.24f - near[1]) / dy
            if (t !in 0f..1f) return null
            val worldX = near[0] + (far[0] - near[0]) * t
            val worldZ = near[2] + (far[2] - near[2]) * t
            return selectableLudoPawns
                .mapNotNull { index -> ludoPawnWorld(index)?.let { point -> index to point } }
                .minByOrNull { (_, point) ->
                    val dx = point.first - worldX
                    val dz = point.second - worldZ
                    dx * dx + dz * dz
                }
                ?.takeIf { (_, point) ->
                    val dx = point.first - worldX
                    val dz = point.second - worldZ
                    dx * dx + dz * dz <= .42f * .42f
                }
                ?.first
        }

        private fun ludoPawnWorld(index: Int): Pair<Float, Float>? {
            if (index !in ludoPositions.indices) return null
            val player = index / 4
            val pawn = index % 4
            val baseSlots = listOf(
                listOf(1 to 1, 1 to 3, 3 to 1, 3 to 3),
                listOf(1 to 11, 1 to 13, 3 to 11, 3 to 13),
                listOf(11 to 11, 11 to 13, 13 to 11, 13 to 13),
                listOf(11 to 1, 11 to 3, 13 to 1, 13 to 3),
            )
            val starts = listOf(0, 13, 26, 39)
            val arrivalLanes = listOf(
                (1..6).map { column -> 7 to column },
                (1..6).map { row -> row to 7 },
                (13 downTo 8).map { column -> 7 to column },
                (13 downTo 8).map { row -> row to 7 },
            )
            val progress = ludoPositions[index]
            val coordinate = when {
                progress < 0 -> baseSlots[player][pawn]
                progress >= 52 -> arrivalLanes[player][(progress - 52).coerceIn(0, 5)]
                else -> ludoPath[(starts[player] + progress) % 52]
            }
            return (-3.92f + coordinate.second * .56f + (pawn % 2) * .035f) to
                (-3.92f + coordinate.first * .56f + (pawn / 2) * .035f)
        }

        private fun unproject(x: Float, y: Float, z: Float, camera: FloatArray = inputInverseVp): FloatArray {
            val input = floatArrayOf(x, y, z, 1f)
            val output = FloatArray(4)
            Matrix.multiplyMV(output, 0, camera, 0, input, 0)
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
                val color = base
                draw(cube, -4f + cell * .5f + column * cell, .08f, -4f + cell * .5f + row * cell, cell * .495f, .09f, cell * .495f, color, .18f, material = 1f)
                val tileX = -4f + cell * .5f + column * cell
                val tileZ = -4f + cell * .5f + row * cell
                if (index == selected) draw(pocketRim, tileX, .181f, tileZ, .46f * cell, .025f, .46f * cell, floatArrayOf(.16f, .66f, 1f, 1f), .28f)
                if (index in legalTargets) draw(cylinder, tileX, .177f, tileZ, .10f * cell, .003f, .10f * cell, floatArrayOf(.24f, .69f, .43f, .8f), .18f)
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
                val shadowElevation = movementLift + if (index == selected) .11f * pieceScale else 0f
                drawSoftShadow(x, z, .38f * pieceScale, shadowElevation)
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
            val s = scale.coerceIn(.86f, 1.08f)
            draw(cylinder, x, .25f + lift, z, .405f * s, .070f * s, .405f * s, color, .58f, material = pieceMaterial)
            draw(torus, x, .31f + lift, z, .405f * s, .095f * s, .405f * s, accent, .58f, material = pieceMaterial)
            draw(cylinder, x, .37f + lift, z, .325f * s, .060f * s, .325f * s, accent, .58f, material = pieceMaterial)
            draw(torus, x, .42f + lift, z, .31f * s, .052f * s, .31f * s, color, .58f, material = pieceMaterial)
            when (key) {
                "♙", "♟" -> {
                    draw(chessStem, x, .62f + lift, z, .205f * s, .245f * s, .205f * s, color, .58f, material = pieceMaterial)
                    draw(torus, x, .81f + lift, z, .17f * s, .050f * s, .17f * s, accent, .58f, material = pieceMaterial)
                    draw(sphere, x, .94f + lift, z, .185f * s, .185f * s, .185f * s, accent, .58f, material = pieceMaterial)
                }
                "♖", "♜" -> {
                    draw(chessStem, x, .66f + lift, z, .235f * s, .27f * s, .235f * s, color, .58f, material = pieceMaterial)
                    draw(cylinder, x, .90f + lift, z, .255f * s, .18f * s, .255f * s, color, .58f, material = pieceMaterial)
                    draw(torus, x, 1.055f + lift, z, .305f * s, .070f * s, .305f * s, accent, .58f, material = pieceMaterial)
                    repeat(6) { n ->
                        val angle = n * Math.PI / 3.0
                        draw(cube, x + cos(angle).toFloat() * .22f * s, 1.15f + lift, z + sin(angle).toFloat() * .22f * s, .065f * s, .09f * s, .065f * s, color, .60f, rotationY = n * 60f, material = pieceMaterial)
                    }
                }
                "♘", "♞" -> {
                    draw(chessStem, x, .67f + lift, z + .05f, .245f * s, .29f * s, .245f * s, color, .58f, rotationX = -10f, material = pieceMaterial)
                    draw(sphere, x, .94f + lift, z - .08f, .235f * s, .285f * s, .19f * s, accent, .58f, material = pieceMaterial)
                    draw(sphere, x, 1.08f + lift, z - .25f, .19f * s, .16f * s, .25f * s, color, .58f, material = pieceMaterial)
                    draw(cone, x - .09f, 1.27f + lift, z - .13f, .052f * s, .15f * s, .052f * s, accent, .58f, rotationX = -10f, material = pieceMaterial)
                    draw(cone, x + .09f, 1.27f + lift, z - .13f, .052f * s, .15f * s, .052f * s, accent, .58f, rotationX = -10f, material = pieceMaterial)
                    // Inlaid eyes and carved mane distinguish the knight at play distance.
                    for (side in listOf(-1f, 1f)) draw(sphere, x + side * .205f * s, 1.095f + lift, z - .20f * s, .025f * s, .027f * s, .027f * s, if (WapiGameRules.isWhite(piece)) floatArrayOf(.07f,.05f,.03f,1f) else floatArrayOf(.6f,.48f,.3f,1f), .4f)
                }
                "♗", "♝" -> {
                    draw(chessStem, x, .70f + lift, z, .245f * s, .33f * s, .245f * s, color, .58f, material = pieceMaterial)
                    draw(torus, x, .93f + lift, z, .20f * s, .055f * s, .20f * s, accent, .58f, material = pieceMaterial)
                    draw(sphere, x, 1.09f + lift, z, .20f * s, .25f * s, .20f * s, accent, .58f, material = pieceMaterial)
                    draw(cone, x, 1.34f + lift, z, .065f * s, .17f * s, .065f * s, color, .58f, rotationX = -18f, material = pieceMaterial)
                }
                "♕", "♛" -> {
                    draw(chessStem, x, .73f + lift, z, .27f * s, .36f * s, .27f * s, color, .58f, material = pieceMaterial)
                    draw(torus, x, .99f + lift, z, .255f * s, .062f * s, .255f * s, accent, .58f, material = pieceMaterial)
                    repeat(6) { n ->
                        val angle = n * Math.PI / 3.0
                        draw(cone, x + cos(angle).toFloat() * .20f * s, 1.18f + lift, z + sin(angle).toFloat() * .20f * s, .060f * s, .16f * s, .060f * s, color, .58f, material = pieceMaterial)
                        draw(sphere, x + cos(angle).toFloat() * .20f * s, 1.34f + lift, z + sin(angle).toFloat() * .20f * s, .055f * s, .065f * s, .055f * s, accent, .58f, material = pieceMaterial)
                    }
                    draw(sphere, x, 1.28f + lift, z, .09f * s, .12f * s, .09f * s, accent, .58f, material = pieceMaterial)
                }
                else -> {
                    draw(chessStem, x, .75f + lift, z, .28f * s, .38f * s, .28f * s, color, .58f, material = pieceMaterial)
                    draw(torus, x, 1.00f + lift, z, .22f * s, .055f * s, .22f * s, accent, .58f, material = pieceMaterial)
                    draw(sphere, x, 1.14f + lift, z, .16f * s, .20f * s, .16f * s, accent, .58f, material = pieceMaterial)
                    draw(cube, x, 1.38f + lift, z, .050f * s, .17f * s, .050f * s, color, .58f, material = pieceMaterial)
                    draw(cube, x, 1.45f + lift, z, .14f * s, .048f * s, .048f * s, color, .58f, material = pieceMaterial)
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
                        player * 4 + pawn in selectableLudoPawns,
                        seconds + pawn * .22f,
                        lift,
                    )
                }
            }
            // Two correctly proportioned dice: separate bodies, independent
            // values and a shared physical roll above the centre lane.
            val spin = if (dieRolling) seconds * 470f else 0f
            val dieY = .88f + if (dieRolling) abs(sin(seconds * 9f)) * .52f else 0f
            listOf(-.30f to dieOne, .30f to dieTwo).forEachIndexed { index, (dieX, value) ->
                val localSpin = spin * if (index == 0) 1f else -.83f
                if (!dieRolling) drawSoftShadow(dieX, 0f, .21f)
                draw(cube, dieX, dieY, 0f, .225f, .225f, .225f, floatArrayOf(.78f, .84f, .91f, 1f), .55f, rotationX = localSpin, rotationY = localSpin * .73f)
                draw(cube, dieX, dieY, 0f, .209f, .219f, .209f, floatArrayOf(.995f, .998f, 1f, 1f), .92f, rotationX = localSpin, rotationY = localSpin * .73f)
                if (!dieRolling) drawDiePips(value, dieX, 0f, dieY + .224f, .46f)
            }
        }

        private fun drawPool(seconds: Float) {
            val wood = when (poolCueStyle) {
                "walnut" -> floatArrayOf(.16f, .055f, .020f, 1f)
                "carbon" -> floatArrayOf(.035f, .047f, .058f, 1f)
                "obsidian" -> floatArrayOf(.050f, .025f, .115f, 1f)
                else -> floatArrayOf(.22f, .105f, .045f, 1f)
            }
            val woodLight = when (poolCueStyle) {
                "walnut" -> floatArrayOf(.30f, .095f, .038f, 1f)
                "carbon" -> floatArrayOf(.10f, .13f, .16f, 1f)
                "obsidian" -> floatArrayOf(.22f, .10f, .42f, 1f)
                else -> floatArrayOf(.40f, .145f, .060f, 1f)
            }
            // The competition cloth follows the classic blue-table reference:
            // a deep blue bed inside a warm lacquered mahogany cabinet.  The
            // cue's material remains independent, so changing a queue never
            // turns the whole table into the same colour.
            val frameWood = if (poolTableTheme == "competitionBlue") {
                floatArrayOf(.34f, .026f, .012f, 1f)
            } else wood
            val frameHighlight = if (poolTableTheme == "competitionBlue") {
                floatArrayOf(.72f, .090f, .030f, 1f)
            } else woodLight
            val felt = when (poolTableTheme) {
                "navy" -> floatArrayOf(.018f, .17f, .43f, 1f)
                "emerald" -> floatArrayOf(.015f, .37f, .27f, 1f)
                // Competition blue: a real wool-cloth blue, deliberately
                // deeper than the old cyan which made the table look like a
                // generic mobile-game surface under bright Android displays.
                else -> floatArrayOf(.012f, .31f, .62f, 1f)
            }
            val cushion = when (poolTableTheme) {
                "emerald" -> floatArrayOf(.012f, .23f, .15f, 1f)
                else -> floatArrayOf(.008f, .20f, .39f, 1f)
            }
            val pocketLeather = floatArrayOf(.055f, .021f, .012f, 1f)
            val railSight = floatArrayOf(.90f, .94f, 1f, 1f)
            // Neutral lounge floor and a physical table plinth.  A real table
            // does not glow underneath or pulse while the player is aiming.
            draw(cube, 0f, -.72f, 0f, 7.8f, .05f, 4.45f, floatArrayOf(.006f, .012f, .026f, 1f), .08f)
            // The apron and legs give the table a real centre of gravity. The
            // old floating slab looked polished but had no believable weight
            // once the camera revealed the lower edge.
            val apron = floatArrayOf(.045f, .018f, .010f, 1f)
            val apronHighlight = floatArrayOf(.22f, .075f, .025f, 1f)
            draw(cube, 0f, -.55f, -3.02f, 5.18f, .34f, .16f, apron, .34f, material = 1f)
            draw(cube, 0f, -.55f, 3.02f, 5.18f, .34f, .16f, apron, .34f, material = 1f)
            draw(cube, -5.18f, -.55f, 0f, .16f, .34f, 2.84f, apron, .34f, material = 1f)
            draw(cube, 5.18f, -.55f, 0f, .16f, .34f, 2.84f, apron, .34f, material = 1f)
            draw(cube, 0f, -.34f, -3.12f, 4.48f, .025f, .018f, apronHighlight, .72f, material = 3f)
            draw(cube, 0f, -.34f, 3.12f, 4.48f, .025f, .018f, apronHighlight, .72f, material = 3f)
            listOf(-4.30f, 4.30f).forEach { x ->
                listOf(-2.28f, 2.28f).forEach { z ->
                    draw(cube, x, -1.02f, z, .33f, .74f, .33f, frameWood, .24f, material = 1f)
                    draw(cube, x, -1.43f, z, .48f, .08f, .48f, apron, .18f, material = 1f)
                    draw(cylinder, x, -.56f, z, .11f, .035f, .11f, apronHighlight, .82f, material = 3f)
                }
            }
            draw(cube, 0f, -.24f, 0f, 5.15f, .30f, 2.82f, frameWood, .38f, material = 1f, poolCutout = true)
            // A regulation playing surface is 2:1. The previous 1.7:1 table
            // looked squat and immediately read as a prototype.
            draw(cube, 0f, .02f, 0f, 4.65f, .11f, 2.25f, felt, .20f, material = 2f, poolCutout = true)
            // Rails are split around every pocket. Full rectangular bars used
            // to cover the openings and made the six pockets look decorative.
            listOf(-2.43f, 2.43f).forEach { x ->
                listOf(-2.55f, 2.55f).forEach { z ->
                    draw(rail, x, .24f, z, 2.03f, .24f, .25f, frameHighlight, .45f, material = 1f, poolCutout = true)
                    draw(poolCushion, x, .26f, z.sign * 2.31f, 1.98f, .15f, .11f, cushion, .08f, rotationY = if (z < 0) 0f else 180f, material = 2f)
                }
            }
            listOf(-4.91f, 4.91f).forEach { x ->
                draw(rail, x, .24f, 0f, 1.78f, .24f, .25f, frameHighlight, .45f, rotationY = 90f, material = 1f, poolCutout = true)
                draw(poolCushion, x.sign * 4.70f, .26f, 0f, 1.72f, .15f, .11f, cushion, .08f, rotationY = if (x < 0) 90f else -90f, material = 2f)
            }
            val pockets = WapiPoolPresentation.pockets
            pockets.forEach { (x, z) ->
                // Recessed black throat, leather liner and inner darkness are
                // placed below the cloth plane to read as a real opening.
                val opening = WapiPoolPresentation.pocketOpeningRadius
                val surround = WapiPoolPresentation.pocketLeatherRadius
                // No raised chrome torus: the mouth is flush with the cloth,
                // with a narrow stitched-leather lip and an unlit deep throat.
                draw(flushPocketLip, x, .133f, z, surround, .065f, surround, pocketLeather, .04f, material = 4f)
                draw(pocketLiner, x, .131f, z, opening, .55f, opening, floatArrayOf(.019f, .008f, .005f, 1f), 0f, material = 8f)
                draw(cylinder, x, -.42f, z, opening, .010f, opening, floatArrayOf(.001f, .001f, .002f, 1f), 0f, material = 8f)
                // Layered leather/net rings make the opening read as a deep
                // ball pocket rather than a black decal on the playing bed.
                draw(torus, x, -.04f, z, opening * .78f, .012f, opening * .78f, floatArrayOf(.075f, .027f, .012f, 1f), .05f, material = 8f)
                draw(torus, x, -.22f, z, opening * .54f, .010f, opening * .54f, floatArrayOf(.048f, .015f, .008f, 1f), .03f, material = 8f)
            }
            // Angled rubber jaws frame the pocket mouths instead of allowing
            // balls to visually pass through a square wooden corner.
            listOf(-1f, 1f).forEach { side ->
                listOf(-1f, 1f).forEach { end ->
                    draw(cube, side * 4.43f, .28f, end * 2.19f, .34f, .15f, .10f, cushion, .24f, rotationY = side * end * 32f, material = 2f, poolCutout = true)
                }
                draw(cube, side * .37f, .28f, -2.27f, .34f, .15f, .10f, cushion, .24f, rotationY = side * 28f, material = 2f, poolCutout = true)
                draw(cube, side * .37f, .28f, 2.27f, .34f, .15f, .10f, cushion, .24f, rotationY = -side * 28f, material = 2f, poolCutout = true)
            }
            // Small inlaid rail sights make the table readable from every
            // camera angle and ground it as a physical object.
            listOf(-3.45f, -2.30f, -1.15f, 1.15f, 2.30f, 3.45f).forEach { x ->
                draw(cylinder, x, .49f, -2.56f, .045f, .014f, .045f, railSight, .86f, material = 3f)
                draw(cylinder, x, .49f, 2.56f, .045f, .014f, .045f, railSight, .86f, material = 3f)
            }
            listOf(-1.12f, 0f, 1.12f).forEach { z ->
                draw(cylinder, -4.92f, .49f, z, .045f, .014f, .045f, railSight, .86f, material = 3f)
                draw(cylinder, 4.92f, .49f, z, .045f, .014f, .045f, railSight, .86f, material = 3f)
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
            // Compact mechanical return inspired by a real tournament table:
            // three nested polished lanes, sparse braces and one unmistakable
            // rounded bend on the right. It must read as a ball collector, not
            // as a long fence floating over the cloth.
            val returnWell = floatArrayOf(.006f, .012f, .022f, 1f)
            val returnRail = floatArrayOf(.30f, .35f, .42f, 1f)
            val returnHighlight = floatArrayOf(.73f, .80f, .88f, 1f)
            val returnJoint = floatArrayOf(.44f, .50f, .58f, 1f)
            val returnBaseZ = -3.14f
            val returnStartX = -2.72f
            val returnEndX = 2.56f
            val returnCenterX = (returnStartX + returnEndX) * .5f
            val returnHalfWidth = (returnEndX - returnStartX) * .5f
            draw(cube, returnCenterX + .18f, .31f, returnBaseZ - .22f, returnHalfWidth + .52f, .12f, .48f, returnWell, .05f, material = 8f)
            repeat(3) { index ->
                val y = .44f + index * .14f
                val z = returnBaseZ - index * .16f
                val tube = .031f
                draw(cylinder, returnCenterX, y, z, tube, returnHalfWidth, tube, returnRail, .86f, rotationZ = 90f, material = 3f)
                draw(cylinder, returnCenterX, y + .010f, z - .012f, .010f, returnHalfWidth - .08f, .010f, returnHighlight, .95f, rotationZ = 90f, material = 3f)
                draw(sphere, returnStartX, y, z, .041f, .041f, .041f, returnJoint, .82f, material = 3f)
                draw(sphere, returnEndX, y, z, .046f, .046f, .046f, returnJoint, .88f, material = 3f)
                val bendLength = .34f + index * .13f
                draw(cylinder, returnEndX, y, z + bendLength * .5f, tube, bendLength * .5f, tube, returnRail, .86f, rotationX = 90f, material = 3f)
                draw(sphere, returnEndX, y, z + bendLength, .041f, .041f, .041f, returnJoint, .84f, material = 3f)
            }
            repeat(9) { brace ->
                val x = returnStartX + .30f + brace * ((returnEndX - returnStartX - .60f) / 8f)
                draw(cylinder, x, .58f, returnBaseZ - .16f, .014f, .18f, .014f, returnJoint, .72f, material = 3f)
            }
            val returnedBalls = poolBalls.filter { it.size >= 6 && it[2].toInt() > 0 && it[5] >= .5f }
                .map { it[2].toInt() }.sorted().take(15)
            returnedBalls.forEachIndexed { index, id ->
                val lane = index / 5
                val slot = index % 5
                // Balls sit in the actual return wells, between the front and
                // rear rails, instead of floating on the decorative top bar.
                val x = -2.32f + slot * 1.14f
                val z = returnBaseZ - lane * .14f
                val color = colors[(id - 1).mod(colors.size)]
                drawSoftShadow(x, z, .105f, .065f)
                draw(sphere, x, .54f + lane * .14f, z, .115f, .115f, .115f, color, .92f, material = if (id >= 9) 6f else 5f)
                draw(cylinder, x, .64f + lane * .14f, z, .046f, .002f, .046f, floatArrayOf(.98f, .985f, 1f, 1f), .30f)
                drawPoolNumber(id, x, z, .657f + lane * .14f)
            }
            poolBalls.forEach { ball ->
                if (ball.size < 6 || ball[5] >= .5f) return@forEach
                val id = ball[2].toInt()
                val x = (ball[0] - .5f) * 10.2f
                val z = (ball[1] - .5f) * 5.10f
                val color = if (id == 0) floatArrayOf(.97f, .98f, 1f, 1f) else colors[(id - 1).mod(colors.size)]
                // The stripe and number plate rotate with the actual physics
                // velocity passed by the pool engine, rather than a generic
                // animation unrelated to the shot.
                val rolling = ballRoll.getOrPut(id) { floatArrayOf(x, z, id * 17f, 0f) }
                val dx = x - rolling[0]
                val dz = z - rolling[1]
                // Integrate travelled distance, not elapsed time × current
                // speed: slowing down must never make the stripe jump back.
                if (dx * dx + dz * dz < 2.25f) {
                    rolling[2] += Math.toDegrees((dz / WAPI_POOL_BALL_RADIUS).toDouble()).toFloat()
                    rolling[3] -= Math.toDegrees((dx / WAPI_POOL_BALL_RADIUS).toDouble()).toFloat()
                }
                rolling[0] = x; rolling[1] = z
                val rotationX = rolling[2] % 360f
                val rotationZ = rolling[3] % 360f
                drawSoftShadow(x, z, .14f, .10f)
                if (id >= 9) {
                    draw(sphere, x, .295f, z, .145f, .145f, .145f, color, .88f, rotationX = rotationX, rotationZ = rotationZ, material = 6f)
                } else {
                    draw(sphere, x, .295f, z, .145f, .145f, .145f, color, .88f, rotationX = rotationX, rotationZ = rotationZ, material = 5f)
                }
                // Tight studio-light reflections ground every ball in the
                // same overhead lighting as the polished rails.  The glint
                // stays fixed to the camera light, as real resin does while
                // the printed stripe rotates beneath it.
                draw(sphere, x - .047f, .393f, z - .052f, .026f, .015f, .026f, floatArrayOf(.96f, .99f, 1f, .64f), .96f, material = 3f)
                draw(sphere, x - .070f, .366f, z - .073f, .010f, .007f, .010f, floatArrayOf(.94f, .98f, 1f, .34f), .90f, material = 3f)
                if (id != 0) {
                    // Numbered cap: a raised white plate and an actual compact
                    // seven-segment numeral, readable from the game camera.
                    draw(cylinder, x, .438f, z, .062f, .002f, .062f, floatArrayOf(.98f, .985f, 1f, 1f), .32f)
                    drawPoolNumber(id, x, z)
                }
            }
            val liveDrops = poolDrops.filter { drop ->
                (System.nanoTime() - drop.startedAtNanos) < 620_000_000L
            }
            poolDrops = liveDrops
            liveDrops.forEach { drop ->
                val progress = ((System.nanoTime() - drop.startedAtNanos) / 620_000_000f).coerceIn(0f, 1f)
                val eased = progress * progress * (3f - 2f * progress)
                val scale = .145f * (1f - eased * .26f)
                val color = if (drop.id == 0) floatArrayOf(.97f, .98f, 1f, 1f) else colors[(drop.id - 1).mod(colors.size)]
                val pocket = WapiPoolPresentation.pockets.minByOrNull { (x, z) -> (x - drop.x) * (x - drop.x) + (z - drop.z) * (z - drop.z) }!!
                val entry = (progress * 3f).coerceAtMost(1f)
                val x = drop.x + (pocket.first - drop.x) * entry
                val z = drop.z + (pocket.second - drop.z) * entry
                draw(sphere, x, .295f - eased * .72f, z, scale, scale, scale, color, .88f, rotationX = eased * 260f, rotationY = eased * 180f, material = if (drop.id >= 9) 6f else 5f)
            }
            if (!poolMoving) {
                val cue = poolBalls.firstOrNull { it.size >= 6 && it[2].toInt() == 0 && it[5] < .5f } ?: return
                val cueX = (cue[0] - .5f) * 10.2f
                val cueZ = (cue[1] - .5f) * 5.10f
                if (poolCueInHand) { drawPlacementHand(cueX, cueZ); return }
                val directionX = cos(poolAim)
                val directionZ = sin(poolAim)
                val strength = poolPower / 100f
                val cueHalfLength = 2.68f
                val pullBack = (.10f + strength * .66f) * (1f - poolCueStroke)
                val tipDistance = .145f + pullBack
                val centerDistance = tipDistance + cueHalfLength
                val centerX = cueX - directionX * centerDistance
                val centerZ = cueZ - directionZ * centerDistance
                // Shaft meshes are authored on Y and first rotated onto Z.
                // The extra quarter-turn aligns the complete cue with the
                // physical shot vector instead of leaving only its tip visible.
                val aimDegrees = Math.toDegrees(poolAim.toDouble()).toFloat()
                val guideRotation = -aimDegrees
                val shaftRotation = aimDegrees - 90f
                // Ray-cast the predicted path against the real ball positions
                // and table limits. The guide now stops at the first physical
                // contact instead of displaying a decorative fixed line.
                val xLimit = if (directionX > .0001f) (4.70f - cueX) / directionX else if (directionX < -.0001f) (-4.70f - cueX) / directionX else 20f
                val zLimit = if (directionZ > .0001f) (2.16f - cueZ) / directionZ else if (directionZ < -.0001f) (-2.16f - cueZ) / directionZ else 20f
                val maximumGuide = 5.6f + poolAimBonus * .85f
                var guideDistance = xLimit.coerceAtMost(zLimit).coerceIn(.45f, maximumGuide)
                var guideTarget: FloatArray? = null
                poolBalls.forEach { candidate ->
                    if (candidate.size < 3 || candidate[2] <= 0f) return@forEach
                    val targetX = (candidate[0] - .5f) * 10.2f
                    val targetZ = (candidate[1] - .5f) * 5.10f
                    val relX = targetX - cueX
                    val relZ = targetZ - cueZ
                    val projection = relX * directionX + relZ * directionZ
                    val perpendicularSquared = relX * relX + relZ * relZ - projection * projection
                    val contactRadius = .292f
                    if (projection > .25f && perpendicularSquared in 0f..(contactRadius * contactRadius)) {
                        val hit = projection - sqrt((contactRadius * contactRadius - perpendicularSquared).coerceAtLeast(0f))
                        if (hit in .20f..<guideDistance) {
                            guideDistance = hit
                            guideTarget = candidate
                        }
                    }
                }
                val dashCount = (guideDistance / .25f).toInt().coerceIn(2, 34)
                repeat(dashCount) { index ->
                    val t = .42f + index * .25f
                    if (t < guideDistance) {
                        val alpha = (1f - index / 38f).coerceAtLeast(.22f)
                        draw(cube, cueX + directionX * t, .178f, cueZ + directionZ * t, .075f, .006f, .012f, floatArrayOf(.84f, .98f, .94f, alpha), .30f, rotationY = guideRotation)
                    }
                }
                val ghostX = cueX + directionX * guideDistance
                val ghostZ = cueZ + directionZ * guideDistance
                draw(torus, ghostX, .184f, ghostZ, .155f, .022f, .155f, floatArrayOf(.83f, .98f, .94f, 1f), .76f, material = 3f)
                if (guideTarget == null && guideDistance < 9.7f) {
                    // One-cushion preview: the line reflects on the physical
                    // rail instead of ending abruptly at the table edge.
                    val hitVerticalRail = xLimit <= zLimit
                    val reboundX = if (hitVerticalRail) -directionX else directionX
                    val reboundZ = if (hitVerticalRail) directionZ else -directionZ
                    val reboundRotation = -Math.toDegrees(kotlin.math.atan2(reboundZ, reboundX).toDouble()).toFloat()
                    repeat(9) { index ->
                        val t = .24f + index * .22f
                        draw(cube, ghostX + reboundX * t, .177f, ghostZ + reboundZ * t, .058f, .005f, .010f, floatArrayOf(.32f, .76f, 1f, .74f - index * .06f), .32f, rotationY = reboundRotation)
                    }
                }
                guideTarget?.let { target ->
                    val targetX = (target[0] - .5f) * 10.2f
                    val targetZ = (target[1] - .5f) * 5.10f
                    val normalX = targetX - ghostX
                    val normalZ = targetZ - ghostZ
                    val normalLength = sqrt(normalX * normalX + normalZ * normalZ).coerceAtLeast(.001f)
                    val outX = normalX / normalLength
                    val outZ = normalZ / normalLength
                    val outRotation = -Math.toDegrees(kotlin.math.atan2(outZ, outX).toDouble()).toFloat()
                    draw(torus, targetX, .184f, targetZ, .158f, .018f, .158f, floatArrayOf(1f, .68f, .12f, .92f), .72f, material = 3f)
                    repeat(6) { index ->
                        val t = .28f + index * .22f
                        draw(cube, targetX + outX * t, .176f, targetZ + outZ * t, .058f, .005f, .010f, floatArrayOf(1f, .78f, .26f, .74f - index * .08f), .30f, rotationY = outRotation)
                    }
                    // Tangent-line preview for the cue ball after impact. Draw
                    // and follow alter this vector, so the spin pad now has an
                    // immediate and understandable consequence on the table.
                    val projectionOnNormal = directionX * outX + directionZ * outZ
                    var cueOutX = directionX - outX * projectionOnNormal + outX * poolFollowSpin * .42f - outZ * poolSideSpin * .24f
                    var cueOutZ = directionZ - outZ * projectionOnNormal + outZ * poolFollowSpin * .42f + outX * poolSideSpin * .24f
                    val cueOutLength = sqrt(cueOutX * cueOutX + cueOutZ * cueOutZ)
                    if (cueOutLength > .025f) {
                        cueOutX /= cueOutLength
                        cueOutZ /= cueOutLength
                        val cueOutRotation = -Math.toDegrees(kotlin.math.atan2(cueOutZ, cueOutX).toDouble()).toFloat()
                        repeat(6) { index ->
                            val t = .24f + index * .19f
                            draw(cube, ghostX + cueOutX * t, .175f, ghostZ + cueOutZ * t, .050f, .004f, .009f, floatArrayOf(.20f, .78f, 1f, .68f - index * .075f), .32f, rotationY = cueOutRotation)
                        }
                    }
                }
                // Contact-point reticle on the cue ball: horizontal position
                // controls English, vertical position controls draw/follow.
                draw(torus, cueX + poolSideSpin * .080f, .443f, cueZ - poolFollowSpin * .080f, .026f, .008f, .026f, floatArrayOf(.96f, .24f, .18f, 1f), .88f, material = 3f)
                // Professional tapered cue: maple shaft, dark hardwood butt,
                // ivory ferrule and a separate chalked leather tip.
                val cueShaft = when (poolCueStyle) {
                    "walnut" -> floatArrayOf(.52f, .20f, .075f, 1f)
                    "carbon" -> floatArrayOf(.12f, .15f, .17f, 1f)
                    "obsidian" -> floatArrayOf(.25f, .10f, .50f, 1f)
                    else -> floatArrayOf(.96f, .76f, .43f, 1f)
                }
                val cueGrip = when (poolCueStyle) {
                    "carbon" -> floatArrayOf(.018f, .025f, .035f, 1f)
                    "obsidian" -> floatArrayOf(.035f, .012f, .085f, 1f)
                    else -> floatArrayOf(.095f, .028f, .010f, 1f)
                }
                draw(cueFrustum, centerX, .36f, centerZ, .061f, cueHalfLength, .061f, cueShaft, .38f, rotationX = 90f, rotationZ = shaftRotation, material = 7f)
                draw(cylinder, centerX - directionX * cueHalfLength * .72f, .36f, centerZ - directionZ * cueHalfLength * .72f, .057f, .31f, .057f, cueGrip, .70f, rotationX = 90f, rotationZ = shaftRotation, material = 1f)
                draw(torus, centerX - directionX * cueHalfLength * .93f, .36f, centerZ - directionZ * cueHalfLength * .93f, .059f, .024f, .059f, floatArrayOf(.76f, .60f, .28f, 1f), .86f, rotationX = 90f, rotationZ = shaftRotation, material = 3f)
                val ferruleX = cueX - directionX * (tipDistance + .085f)
                val ferruleZ = cueZ - directionZ * (tipDistance + .085f)
                draw(cylinder, ferruleX, .36f, ferruleZ, .031f, .075f, .031f, floatArrayOf(.95f, .88f, .70f, 1f), .80f, rotationX = 90f, rotationZ = shaftRotation, material = 3f)
                val tipX = cueX - directionX * tipDistance
                val tipZ = cueZ - directionZ * tipDistance
                draw(cylinder, tipX, .36f, tipZ, .033f, .018f, .033f, floatArrayOf(.025f, .20f, .38f, 1f), .68f, rotationX = 90f, rotationZ = shaftRotation, material = 4f)
            }
        }

        private fun drawPlacementHand(ballX: Float, z: Float) {
            // A fitted billiards glove sits beside the ball, with only the
            // fingertips entering the placement area.  It must guide the
            // gesture without hiding the cue ball or looking like a mascot.
            val x = ballX + .15f
            val glove = floatArrayOf(.84f, .855f, .86f, 1f)
            val seam = floatArrayOf(.56f, .62f, .66f, 1f)
            draw(sphere, x + .065f, .59f, z + .20f, .135f, .052f, .150f, glove, .08f, material = 9f)
            for (finger in 0..3) {
                val fx = x - .055f + finger * .055f
                val reach = if (finger == 3) .022f else 0f
                draw(sphere, fx, .57f, z + .060f + reach, .027f, .044f, .098f, glove, .08f, rotationX = -22f, material = 9f)
                draw(sphere, fx, .485f, z + .002f + reach, .026f, .069f, .032f, glove, .08f, rotationX = -18f, material = 9f)
                draw(sphere, fx, .616f, z + .110f, .022f, .011f, .027f, glove, .06f, material = 9f)
            }
            draw(sphere, x - .095f, .535f, z + .145f, .043f, .046f, .092f, glove, .08f, rotationY = -38f, material = 9f)
            draw(sphere, x - .135f, .46f, z + .085f, .036f, .058f, .039f, glove, .08f, material = 9f)
            for (stitch in -1..1) draw(sphere, x + .065f + stitch * .042f, .654f, z + .208f, .003f, .003f, .060f, seam, 0f, material = 9f)
            draw(sphere, x + .065f, .59f, z + .355f, .102f, .054f, .064f, glove, .05f, material = 9f)
            draw(sphere, x + .065f, .59f, z + .398f, .097f, .051f, .055f, floatArrayOf(.016f, .07f, .13f, 1f), .05f, material = 9f)
            for (rib in -3..3) draw(sphere, x + .065f + rib * .023f, .638f, z + .398f, .003f, .004f, .033f, seam, 0f, material = 9f)
        }

        private fun drawPoolNumber(number: Int, centerX: Float, centerZ: Float, centerY: Float = .448f) {
            val ink = floatArrayOf(.008f, .012f, .020f, 1f)
            val digits = number.coerceIn(1, 15).toString()
            val patterns = mapOf(
                '0' to setOf(0, 1, 2, 4, 5, 6), '1' to setOf(2, 5),
                '2' to setOf(0, 2, 3, 4, 6), '3' to setOf(0, 2, 3, 5, 6),
                '4' to setOf(1, 2, 3, 5), '5' to setOf(0, 1, 3, 5, 6),
                '6' to setOf(0, 1, 3, 4, 5, 6), '7' to setOf(0, 2, 5),
                '8' to setOf(0, 1, 2, 3, 4, 5, 6), '9' to setOf(0, 1, 2, 3, 5, 6),
            )
            val scale = if (digits.length == 1) 1.5f else 1.05f
            digits.forEachIndexed { index, digit ->
                val offsetX = if (digits.length == 1) 0f else (index * 2 - 1) * .020f
                val segments = patterns[digit].orEmpty()
                fun horizontal(z: Float) = draw(cube, centerX + offsetX, centerY, centerZ + z * scale, .012f * scale, .002f, .0028f * scale, ink, .42f)
                fun vertical(x: Float, z: Float) = draw(cube, centerX + offsetX + x * scale, centerY, centerZ + z * scale, .0028f * scale, .002f, .009f * scale, ink, .42f)
                if (0 in segments) horizontal(-.020f)
                if (1 in segments) vertical(-.010f, -.010f)
                if (2 in segments) vertical(.010f, -.010f)
                if (3 in segments) horizontal(0f)
                if (4 in segments) vertical(-.010f, .010f)
                if (5 in segments) vertical(.010f, .010f)
                if (6 in segments) horizontal(.020f)
            }
        }

        private fun drawPawn(x: Float, z: Float, color: FloatArray, active: Boolean, selectable: Boolean, seconds: Float, moveLift: Float = 0f) {
            val lift = moveLift + if (selectable) .08f + abs(sin(seconds * 4f)) * .10f else if (active) .025f else 0f
            drawSoftShadow(x, z, .25f + moveLift * .25f)
            if (selectable) draw(torus, x, .205f, z, .34f, .055f, .34f, floatArrayOf(1f, .84f, .24f, 1f), .98f, material = 3f)
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

        private fun drawSoftShadow(x: Float, z: Float, radius: Float, elevation: Float = 0f) {
            // Main light points from upper-left/front. Shadows therefore move
            // right/back, and the offset/softness increases with elevation.
            val offsetX = .075f + elevation * .16f
            val offsetZ = -.060f - elevation * .13f
            val softness = 1f + elevation * .18f
            val plane = when(scene) { Scene.POOL -> .147f; Scene.CHESS, Scene.CHECKERS -> .173f; else -> .198f }
            draw(cylinder, x + offsetX, plane, z + offsetZ, radius * softness, .004f, radius * softness, floatArrayOf(.004f, .006f, .009f, .25f), .01f)
            draw(cylinder, x + offsetX * 1.65f, plane - .002f, z + offsetZ * 1.65f, radius * 1.20f * softness, .003f, radius * 1.20f * softness, floatArrayOf(.004f, .006f, .009f, .10f), .01f)
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
            rotationZ: Float = 0f,
            material: Float = 0f,
            poolCutout: Boolean = false,
        ) {
            Matrix.setIdentityM(model, 0)
            Matrix.translateM(model, 0, x, y, z)
            if (rotationX != 0f) Matrix.rotateM(model, 0, rotationX, 1f, 0f, 0f)
            if (rotationY != 0f) Matrix.rotateM(model, 0, rotationY, 0f, 1f, 0f)
            if (rotationZ != 0f) Matrix.rotateM(model, 0, rotationZ, 0f, 0f, 1f)
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
            GLES20.glUniform3f(scaleHandle, sx, sy, sz)
            GLES20.glUniform1f(cutoutHandle, if (poolCutout) 1f else 0f)
            val boundTexture = when {
                (material > .5f && material < 1.5f) || (material > 6.5f && material < 7.5f) -> woodTexture
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
            return GLES20.glCreateProgram().also { programId ->
                GLES20.glAttachShader(programId, vertex)
                GLES20.glAttachShader(programId, fragment)
                GLES20.glLinkProgram(programId)
                val status = IntArray(1)
                GLES20.glGetProgramiv(programId, GLES20.GL_LINK_STATUS, status, 0)
                val log = GLES20.glGetProgramInfoLog(programId).orEmpty()
                GLES20.glDeleteShader(vertex)
                GLES20.glDeleteShader(fragment)
                check(status[0] == GLES20.GL_TRUE) { "WAPI tabletop shader link failed: $log" }
            }
        }

        private fun compile(type: Int, source: String): Int = GLES20.glCreateShader(type).also { shaderId ->
            GLES20.glShaderSource(shaderId, source)
            GLES20.glCompileShader(shaderId)
            val status = IntArray(1)
            GLES20.glGetShaderiv(shaderId, GLES20.GL_COMPILE_STATUS, status, 0)
            val log = GLES20.glGetShaderInfoLog(shaderId).orEmpty()
            check(status[0] == GLES20.GL_TRUE) { "WAPI tabletop shader compile failed: $log" }
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

            /** Chamfered hardwood rail: long edge highlights follow geometry. */
            fun railMesh(): Mesh {
                val section = listOf(1f to -.78f, .78f to -1f, -.78f to -1f, -1f to -.78f, -1f to .78f, -.78f to 1f, .78f to 1f, 1f to .78f)
                val data = mutableListOf<Float>()
                fun triangle(a: FloatArray, b: FloatArray, c: FloatArray, n: FloatArray) {
                    val u = FloatArray(3) { b[it] - a[it] }; val v = FloatArray(3) { c[it] - a[it] }
                    val dot = (u[1] * v[2] - u[2] * v[1]) * n[0] + (u[2] * v[0] - u[0] * v[2]) * n[1] + (u[0] * v[1] - u[1] * v[0]) * n[2]
                    (if (dot >= 0f) listOf(a, b, c) else listOf(a, c, b)).forEach { point -> data += point.toList(); data += n.toList() }
                }
                section.indices.forEach { index ->
                    val (y, z) = section[index]; val (nextY, nextZ) = section[(index + 1) % section.size]
                    val length = sqrt((y + nextY) * (y + nextY) + (z + nextZ) * (z + nextZ))
                    val normal = floatArrayOf(0f, (y + nextY) / length, (z + nextZ) / length)
                    val a = floatArrayOf(-1f, y, z); val b = floatArrayOf(1f, y, z)
                    val c = floatArrayOf(1f, nextY, nextZ); val d = floatArrayOf(-1f, nextY, nextZ)
                    triangle(a, b, c, normal); triangle(a, c, d, normal)
                    triangle(floatArrayOf(-1f, 0f, 0f), a, d, floatArrayOf(-1f, 0f, 0f))
                    triangle(floatArrayOf(1f, 0f, 0f), b, c, floatArrayOf(1f, 0f, 0f))
                }
                return Mesh(buffer(data), data.size / 6)
            }

            /** Cloth-covered wedge, tapered at the jaws; front is local +Z. */
            fun poolCushionMesh(): Mesh {
                val points = arrayOf(
                    floatArrayOf(-1f, -1f, -1f), floatArrayOf(1f, -1f, -1f),
                    floatArrayOf(.92f, -1f, 1f), floatArrayOf(-.92f, -1f, 1f),
                    floatArrayOf(-1f, 1f, -1f), floatArrayOf(1f, 1f, -1f),
                    floatArrayOf(.92f, .18f, 1f), floatArrayOf(-.92f, .18f, 1f),
                )
                val data = mutableListOf<Float>()
                for (face in arrayOf(intArrayOf(4,7,6,5), intArrayOf(0,1,2,3), intArrayOf(0,4,5,1), intArrayOf(3,2,6,7), intArrayOf(0,3,7,4), intArrayOf(1,5,6,2))) {
                    val a = points[face[0]]; val b = points[face[1]]; val c = points[face[2]]
                    val u = FloatArray(3) { b[it] - a[it] }; val v = FloatArray(3) { c[it] - a[it] }
                    val n = floatArrayOf(u[1]*v[2]-u[2]*v[1], u[2]*v[0]-u[0]*v[2], u[0]*v[1]-u[1]*v[0])
                    val length = sqrt(n.sumOf { (it*it).toDouble() }).toFloat()
                    for (i in n.indices) n[i] /= length
                    for (index in intArrayOf(0,1,2,0,2,3)) { data += points[face[index]].toList(); data += n.toList() }
                }
                return Mesh(buffer(data), data.size / 6)
            }

            /** Turned Staunton stem with a concave waist and smooth axial normals. */
            fun chessStemMesh(segments: Int): Mesh {
                val profile = listOf(-1f to .98f, -.82f to .91f, -.66f to .70f, -.43f to .52f, -.15f to .40f, .18f to .34f, .46f to .36f, .68f to .46f, .82f to .61f, 1f to .58f)
                val data = mutableListOf<Float>()
                fun vertex(ring: Int, angle: Double) {
                    val (y, radius) = profile[ring]
                    val before = profile[(ring - 1).coerceAtLeast(0)]; val after = profile[(ring + 1).coerceAtMost(profile.lastIndex)]
                    val slope = (after.second - before.second) / (after.first - before.first)
                    val norm = kotlin.math.sqrt(1f + slope * slope)
                    val x = cos(angle).toFloat(); val z = sin(angle).toFloat()
                    data += listOf(x * radius, y, z * radius, x / norm, -slope / norm, z / norm)
                }
                repeat(profile.lastIndex) { ring -> repeat(segments) { n ->
                    val a = n * 2.0 * Math.PI / segments; val b = (n + 1) * 2.0 * Math.PI / segments
                    vertex(ring, a); vertex(ring + 1, b); vertex(ring, b)
                    vertex(ring, a); vertex(ring + 1, a); vertex(ring + 1, b)
                } }
                return Mesh(buffer(data), data.size / 6)
            }

            fun cylinderMesh(segments: Int): Mesh {
                val data = mutableListOf<Float>()
                fun vertex(x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float) { data += listOf(x,y,z,nx,ny,nz) }
                repeat(segments) { index ->
                    val a0 = index * Math.PI * 2.0 / segments
                    val a1 = (index + 1) * Math.PI * 2.0 / segments
                    val x0 = cos(a0).toFloat(); val z0 = sin(a0).toFloat(); val x1 = cos(a1).toFloat(); val z1 = sin(a1).toFloat()
                    vertex(x0,-1f,z0,x0,0f,z0); vertex(x1,1f,z1,x1,0f,z1); vertex(x1,-1f,z1,x1,0f,z1)
                    vertex(x0,-1f,z0,x0,0f,z0); vertex(x0,1f,z0,x0,0f,z0); vertex(x1,1f,z1,x1,0f,z1)
                    vertex(0f,1f,0f,0f,1f,0f); vertex(x1,1f,z1,0f,1f,0f); vertex(x0,1f,z0,0f,1f,0f)
                    vertex(0f,-1f,0f,0f,-1f,0f); vertex(x0,-1f,z0,0f,-1f,0f); vertex(x1,-1f,z1,0f,-1f,0f)
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
                    vertex(x0,-1f,z0,nx,.48f,nz); vertex(0f,1f,0f,nx,.48f,nz); vertex(x1,-1f,z1,nx,.48f,nz)
                    vertex(0f,-1f,0f,0f,-1f,0f); vertex(x0,-1f,z0,0f,-1f,0f); vertex(x1,-1f,z1,0f,-1f,0f)
                }
                return Mesh(buffer(data), data.size / 6)
            }

            /** A cue is tapered, never a full cone. The upper radius keeps a
             * real shaft and ferrule silhouette instead of ending as a spike. */
            fun frustumMesh(segments: Int, upperRadius: Float): Mesh {
                val data = mutableListOf<Float>()
                fun vertex(x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float) {
                    data += listOf(x, y, z, nx, ny, nz)
                }
                repeat(segments) { index ->
                    val a0 = index * Math.PI * 2.0 / segments
                    val a1 = (index + 1) * Math.PI * 2.0 / segments
                    val c0 = cos(a0).toFloat(); val s0 = sin(a0).toFloat()
                    val c1 = cos(a1).toFloat(); val s1 = sin(a1).toFloat()
                    val mid = (a0 + a1) * .5
                    val nx = cos(mid).toFloat(); val nz = sin(mid).toFloat()
                    vertex(c0, -1f, s0, nx, .18f, nz)
                    vertex(c1 * upperRadius, 1f, s1 * upperRadius, nx, .18f, nz)
                    vertex(c1, -1f, s1, nx, .18f, nz)
                    vertex(c0, -1f, s0, nx, .18f, nz)
                    vertex(c0 * upperRadius, 1f, s0 * upperRadius, nx, .18f, nz)
                    vertex(c1 * upperRadius, 1f, s1 * upperRadius, nx, .18f, nz)
                    vertex(0f, -1f, 0f, 0f, -1f, 0f)
                    vertex(c0, -1f, s0, 0f, -1f, 0f)
                    vertex(c1, -1f, s1, 0f, -1f, 0f)
                    vertex(0f, 1f, 0f, 0f, 1f, 0f)
                    vertex(c1 * upperRadius, 1f, s1 * upperRadius, 0f, 1f, 0f)
                    vertex(c0 * upperRadius, 1f, s0 * upperRadius, 0f, 1f, 0f)
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
                        // Counter-clockwise winding as seen from outside. The
                        // reversed order exposed the back hemisphere through
                        // the centre and made every billiard ball look hollow.
                        vertex(p0,t0); vertex(p1,t1); vertex(p1,t0)
                        vertex(p0,t0); vertex(p0,t1); vertex(p1,t1)
                    }
                }
                return Mesh(buffer(data), data.size / 6)
            }

            fun pocketLinerMesh(segments: Int): Mesh {
                val data = mutableListOf<Float>()
                fun vertex(angle: Double, y: Float) {
                    val x = cos(angle).toFloat(); val z = sin(angle).toFloat()
                    data += listOf(x, y, z, -x, 0f, -z)
                }
                repeat(segments) { index ->
                    val a = index * Math.PI * 2 / segments
                    val b = (index + 1) * Math.PI * 2 / segments
                    // Inward-facing walls, deliberately no cap over the opening.
                    vertex(a, -1f); vertex(b, -1f); vertex(b, 0f)
                    vertex(a, -1f); vertex(b, 0f); vertex(a, 0f)
                }
                return Mesh(buffer(data), data.size / 6)
            }

            fun torusMesh(majorSegments: Int, minorSegments: Int, majorRadius: Double = .78, tubeRadius: Double = .22): Mesh {
                val data = mutableListOf<Float>()
                fun vertex(major: Double, minor: Double) {
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
                        vertex(a0, b0); vertex(a1, b1); vertex(a1, b0)
                        vertex(a0, b0); vertex(a0, b1); vertex(a1, b1)
                    }
                }
                return Mesh(buffer(data), data.size / 6)
            }
        }
    }
}
