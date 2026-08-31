package com.whappy.chat

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat

/**
 * Debug-only visual harness. It never ships in release builds and lets QA
 * inspect the complete pool surface without weakening WAPI authentication.
 */
class PoolVisualTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (intent.getStringExtra("game") !in listOf("chess", "checkers")) {
            androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
        setContent {
            WhappyTheme {
                if (intent.getStringExtra("game") in listOf("chess", "checkers")) StrategyBoardGame(
                    checkers = intent.getStringExtra("game") == "checkers", onXp = {}, onWin = {},
                ) else Billiards3D(
                    mode = intent.getStringExtra("mode") ?: "training",
                    aiDifficulty = "ultra",
                    onXp = {},
                    onWin = {},
                )
            }
        }
    }
}
