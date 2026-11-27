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
        if (closed || !binder.isBinderAlive) return
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
            entries.remove(binder)
            // The remote process may have died between isBinderAlive and linkToDeath.
            // Remove the caller's registration immediately instead of retaining a stale listener.
            runCatching { onDeath() }
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
