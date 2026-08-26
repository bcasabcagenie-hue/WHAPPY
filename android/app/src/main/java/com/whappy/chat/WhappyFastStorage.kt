package com.whappy.chat

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import com.tencent.mmkv.MMKV

/**
 * Fast local persistence with a compatibility fallback.
 *
 * MMKV 2.4 ships native code for 64-bit Android. Some supported WAPI phones
 * still run a 32-bit OS, even when their processor itself is 64-bit. Loading
 * MMKV unconditionally on those devices throws UnsatisfiedLinkError before the
 * first screen appears. They use Android's private SharedPreferences instead;
 * Firestore remains the cross-device source of truth.
 */
object WhappyFastStorage {
    private const val MIGRATED = "__whappy_mmkv_migrated_v1"
    private const val SECURITY_PREFERENCES = "__whappy_mmkv_security"
    private const val TAG = "WapiFastStorage"
    @Volatile private var initialized = false
    @Volatile private var nativeStorageAvailable = false

    fun initialize(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (!initialized) {
                nativeStorageAvailable = supportsPackagedMmkv(Build.SUPPORTED_ABIS) && runCatching {
                    MMKV.initialize(context.applicationContext)
                    true
                }.getOrElse { error ->
                    Log.w(TAG, "MMKV unavailable; using Android private storage", error)
                    false
                }
                initialized = true
            }
        }
    }

    fun preferences(context: Context, name: String): SharedPreferences {
        initialize(context)
        val appContext = context.applicationContext
        val fallback = appContext.getSharedPreferences(name, Context.MODE_PRIVATE)
        if (!nativeStorageAvailable) return fallback

        return runCatching {
            val cryptKey = WhappyCryptoVault.mmkvCryptKey(appContext)
            val security = appContext.getSharedPreferences(SECURITY_PREFERENCES, Context.MODE_PRIVATE)
            val securityMarker = "encrypted_$name"
            val storage = if (security.getBoolean(securityMarker, false)) {
                MMKV.mmkvWithID(name, MMKV.SINGLE_PROCESS_MODE, cryptKey)
            } else {
                MMKV.mmkvWithID(name).also { legacy ->
                    check(legacy.reKey(cryptKey)) { "Le stockage WAPI n'a pas pu être chiffré" }
                    security.edit().putBoolean(securityMarker, true).commit()
                }
            }
            if (!storage.decodeBool(MIGRATED, false)) {
                storage.importFromSharedPreferences(fallback)
                storage.encode(MIGRATED, true)
            }
            storage as SharedPreferences
        }.getOrElse { error ->
            // Manufacturer-specific Keystore or mmap failures must never make
            // the entire application unusable. Retry MMKV on the next process.
            nativeStorageAvailable = false
            Log.e(TAG, "Native storage failed; continuing with private Android storage", error)
            fallback
        }
    }

    internal fun supportsPackagedMmkv(abis: Array<String>): Boolean = abis.any {
        it.equals("arm64-v8a", ignoreCase = true) || it.equals("x86_64", ignoreCase = true)
    }
}
