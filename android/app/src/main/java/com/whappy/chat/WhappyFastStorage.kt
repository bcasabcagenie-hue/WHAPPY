package com.whappy.chat

import android.content.Context
import android.content.SharedPreferences
import com.tencent.mmkv.MMKV

/** Fast C++/mmap persistence with one-time migration for existing installs. */
object WhappyFastStorage {
    private const val MIGRATED = "__whappy_mmkv_migrated_v1"
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
        val storage = MMKV.mmkvWithID(name)
        if (!storage.decodeBool(MIGRATED, false)) {
            storage.importFromSharedPreferences(context.getSharedPreferences(name, Context.MODE_PRIVATE))
            storage.encode(MIGRATED, true)
        }
        return storage
    }
}
