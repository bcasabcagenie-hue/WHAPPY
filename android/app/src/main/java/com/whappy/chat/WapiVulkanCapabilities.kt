package com.whappy.chat

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import org.json.JSONObject

internal data class WapiGraphicsCapabilities(
    val backend: String,
    val available: Boolean,
    val apiVersion: String,
    val deviceName: String,
    val deviceType: String,
    val reason: String,
)

/**
 * Hardware gate for the Vulkan renderer. WAPI never enables Vulkan from a
 * marketing flag: the loader, a graphics queue and swapchain support must all
 * be present on the actual phone.
 */
internal object WapiVulkanCapabilities {
    private val libraryLoaded: Boolean by lazy {
        runCatching { System.loadLibrary("wapi_vulkan") }.isSuccess
    }

    fun inspect(context: Context): WapiGraphicsCapabilities {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return unavailable("Android ${Build.VERSION.SDK_INT} sans Vulkan 1.0 garanti")
        }
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL)) {
            return unavailable("matériel Vulkan absent")
        }
        if (!libraryLoaded) return unavailable("moteur natif Vulkan non chargé")
        return runCatching {
            val json = JSONObject(nativeInspect())
            WapiGraphicsCapabilities(
                backend = if (json.optBoolean("available")) "Vulkan" else "OpenGL compatibilité",
                available = json.optBoolean("available"),
                apiVersion = json.optString("apiVersion", "0.0.0"),
                deviceName = json.optString("deviceName"),
                deviceType = json.optString("deviceType"),
                reason = json.optString("reason"),
            )
        }.getOrElse { unavailable(it.message ?: "inspection Vulkan impossible") }
    }

    private fun unavailable(reason: String) = WapiGraphicsCapabilities(
        backend = "OpenGL compatibilité",
        available = false,
        apiVersion = "0.0.0",
        deviceName = "",
        deviceType = "",
        reason = reason,
    )

    @JvmStatic private external fun nativeInspect(): String
}

