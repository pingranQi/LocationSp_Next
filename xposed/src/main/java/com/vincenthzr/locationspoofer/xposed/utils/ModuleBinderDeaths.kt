package com.vincenthzr.locationspoofer.xposed.utils

import android.os.IBinder

/** One death recipient per remote Binder; replace callbacks on re-registration and unlink on unregister. */
internal object ModuleBinderDeaths {
    private data class Entry(val recipient: IBinder.DeathRecipient, var onDeath: () -> Unit)
    private val entries = LinkedHashMap<IBinder, Entry>()
    private var closed = false

    @Synchronized
    fun watch(binder: IBinder, onDeath: () -> Unit) {
        if (closed || !binder.isBinderAlive) return
        entries[binder]?.let { it.onDeath = onDeath; return }
        lateinit var recipient: IBinder.DeathRecipient
        recipient = IBinder.DeathRecipient {
            val callback = synchronized(this@ModuleBinderDeaths) {
                val entry = entries.remove(binder)
                if (closed || entry?.recipient !== recipient) null else entry.onDeath
            }
            runCatching { callback?.invoke() }
        }
        entries[binder] = Entry(recipient, onDeath)
        try {
            binder.linkToDeath(recipient, 0)
        } catch (error: Throwable) {
            entries.remove(binder)
            throw error
        }
    }

    @Synchronized
    fun unwatch(binder: IBinder) {
        val entry = entries.remove(binder) ?: return
        runCatching { binder.unlinkToDeath(entry.recipient, 0) }
    }

    @Synchronized
    fun close() {
        closed = true
        entries.forEach { (binder, entry) -> runCatching { binder.unlinkToDeath(entry.recipient, 0) } }
        entries.clear()
    }
}
