package com.vincenthzr.locationspoofer.xposed.utils

import android.os.IBinder

/** One death recipient per remote Binder; replace callbacks on re-registration and unlink on unregister. */
internal object ModuleBinderDeaths {
    private data class Entry(
        val recipient: IBinder.DeathRecipient,
        val callbacks: LinkedHashMap<String, () -> Unit>
    )
    private val entries = LinkedHashMap<IBinder, Entry>()
    private var closed = false

    @Synchronized
    fun watch(binder: IBinder, registrationKey: String = "default", onDeath: () -> Unit) {
        if (closed || !binder.isBinderAlive) {
            runCatching { onDeath() }
            return
        }
        entries[binder]?.let { it.callbacks[registrationKey] = onDeath; return }
        lateinit var recipient: IBinder.DeathRecipient
        recipient = IBinder.DeathRecipient {
            val callback = synchronized(this@ModuleBinderDeaths) {
                val entry = entries.remove(binder)
                if (closed || entry?.recipient !== recipient) emptyList() else entry.callbacks.values.toList()
            }
            callback.forEach { runCatching { it() } }
        }
        entries[binder] = Entry(recipient, linkedMapOf(registrationKey to onDeath))
        try {
            binder.linkToDeath(recipient, 0)
        } catch (error: Throwable) {
            val failed = entries.remove(binder)
            // The remote process may have died between isBinderAlive and linkToDeath.
            // Clear every registration attached to this Binder, not only the latest one.
            failed?.callbacks?.values?.forEach { runCatching { it() } }
            throw error
        }
    }

    @Synchronized
    fun unwatch(binder: IBinder, registrationKey: String = "default") {
        val entry = entries[binder] ?: return
        entry.callbacks.remove(registrationKey)
        if (entry.callbacks.isEmpty()) {
            entries.remove(binder)
            runCatching { binder.unlinkToDeath(entry.recipient, 0) }
        }
    }

    /**
     * Close registrations for the retiring hook generation. This singleton is reused after
     * LSPosed hot reload, so close must not permanently disable future watch() calls.
     */
    @Synchronized
    fun close() {
        closed = true
        val snapshot = entries.toList()
        entries.clear()
        try {
            snapshot.forEach { (binder, entry) ->
                runCatching { binder.unlinkToDeath(entry.recipient, 0) }
            }
        } finally {
            closed = false
        }
    }
}
