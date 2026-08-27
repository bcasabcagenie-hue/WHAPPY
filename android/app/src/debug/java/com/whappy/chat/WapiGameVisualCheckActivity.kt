package com.whappy.chat

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/** Debug-only visual harness. It is never packaged in a release APK. */
class WapiGameVisualCheckActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        val scene = intent.getStringExtra("scene").orEmpty()
        val view = WapiTabletop3DView(this)
        when (scene) {
            "pool" -> view.setPoolScene(
                balls = buildList {
                    add(floatArrayOf(.23f, .50f, 0f))
                    var id = 1
                    for (row in 0..4) for (column in 0..row) {
                        add(floatArrayOf(.685f + row * .0445f, .50f + (column - row / 2f) * .0865f, id++.toFloat()))
                    }
                },
                aimAngle = 0f,
                power = 70,
                moving = false,
            )
            "ludo" -> view.setLudoScene(List(16) { -1 }, activePlayer = 0, dieOne = 6, dieTwo = 4, rolling = false)
            "chess" -> view.setStrategyScene(
                WapiTabletop3DView.Scene.CHESS,
                listOf("♜","♞","♝","♛","♚","♝","♞","♜") + List(8) { "♟" } + List(32) { "" } + List(8) { "♙" } + listOf("♖","♘","♗","♕","♔","♗","♘","♖"),
                selected = -1,
                legalTargets = emptySet(),
            )
            else -> view.setStrategyScene(
                WapiTabletop3DView.Scene.CHECKERS,
                List(100) { index ->
                    val row = index / 10
                    val column = index % 10
                    if ((row + column) % 2 == 1 && row < 4) "b"
                    else if ((row + column) % 2 == 1 && row >= 6) "w"
                    else ""
                },
                selected = -1,
                legalTargets = emptySet(),
            )
        }
        setContentView(view)
    }
}
