package com.whappy.chat

import android.content.Context
import android.content.SharedPreferences
import com.tencent.mmkv.MMKV

/** Fast C++/mmap persistence with one-time migration for existing installs. */
object WhappyFastStorage {
    private const val MIGRATED = "__whappy_mmkv_migrated_v1"
    private const val SECURITY_PREFERENCES = "__whappy_mmkv_security"
    @Volatile private var initialized = false

    fun initialize(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (!initialized) {
                MMKV.initialize(context.applicationContext)
                initialized = true
            }
        }
    }

    fun preferences(context: Context, name: String): SharedPreferences {
        initialize(context)
        val appContext = context.applicationContext
        val cryptKey = WhappyCryptoVault.mmkvCryptKey(appContext)
        val security = appContext.getSharedPreferences(SECURITY_PREFERENCES, Context.MODE_PRIVATE)
        val securityMarker = "encrypted_$name"
        val storage = if (security.getBoolean(securityMarker, false)) {
            MMKV.mmkvWithID(name, MMKV.SINGLE_PROCESS_MODE, cryptKey)
        } else {
            MMKV.mmkvWithID(name).also { legacy ->
                check(legacy.reKey(cryptKey)) { "Le stockage WHAPPY n'a pas pu être chiffré" }
                security.edit().putBoolean(securityMarker, true).commit()
            }
        }
        if (!storage.decodeBool(MIGRATED, false)) {
            storage.importFromSharedPreferences(appContext.getSharedPreferences(name, Context.MODE_PRIVATE))
            storage.encode(MIGRATED, true)
        }
        return storage
    }
}
