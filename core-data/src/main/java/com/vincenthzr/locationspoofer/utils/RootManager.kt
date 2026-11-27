package com.vincenthzr.locationspoofer.utils

import android.util.Log
import com.vincenthzr.locationspoofer.data.model.RootSetupTestResult
import com.vincenthzr.locationspoofer.data.model.RootSolution
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RootManager {

    companion object {
        private const val TAG = "LocationSpoofer"
        private const val COMMAND_TIMEOUT_SECONDS = 15L
        private const val MAX_OUTPUT_CHARS = 64 * 1024
        private val PACKAGE_NAME_PATTERN = Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")

        private fun isValidPackageName(packageName: String): Boolean =
            packageName.length <= 255 && PACKAGE_NAME_PATTERN.matches(packageName)
    }

    suspend fun checkRootAccess(solution: RootSolution = RootSolution.AUTO): Boolean =
        withContext(Dispatchers.IO) {
            val hasRoot = executeCommand("id").contains("uid=0(root)")
            if (hasRoot) applyRootBackgroundExemptions()
            hasRoot
        }

    suspend fun applyRootBackgroundExemptions(packageName: String = "com.vincenthzr.locationspoofer"): Boolean =
        withContext(Dispatchers.IO) {
            if (!isValidPackageName(packageName)) {
                Log.w(TAG, "Rejected invalid package name for root exemption")
                return@withContext false
            }
            val cmds = """
                dumpsys deviceidle whitelist +$packageName 2>/dev/null || true
                cmd appops set $packageName RUN_IN_BACKGROUND allow 2>/dev/null || true
                cmd appops set $packageName RUN_ANY_IN_BACKGROUND allow 2>/dev/null || true
                cmd appops set $packageName WAKE_LOCK allow 2>/dev/null || true
                cmd appops set $packageName AUTO_REVOKE_PERMISSIONS_IF_UNUSED ignore 2>/dev/null || true
                am set-standby-bucket $packageName active 2>/dev/null || true
            """.trimIndent()
            executeCommand(cmds) != "ERROR"
        }

    /** Read-only diagnostics: no SELinux patches, probe files, or root scripts are needed. */
    suspend fun testRootSetup(solution: RootSolution = RootSolution.AUTO): RootSetupTestResult =
        withContext(Dispatchers.IO) {
            val idOutput = executeCommand("id")
            val service = XposedModuleStatus.mService
            var accessible = false
            var published = false
            var detail = "Framework disconnected"
            if (service != null) {
                try {
                    val snapshot = service.getRemotePreferences(FrameworkConfigChannel.GROUP)
                        .getString(FrameworkConfigChannel.SNAPSHOT_KEY, null)
                    accessible = true
                    published = snapshot != null
                    detail = if (published) "Configuration published; check Hook status for receiver confirmation"
                        else "Framework ready; no configuration published yet"
                } catch (error: Exception) {
                    detail = "${error.javaClass.simpleName}: ${error.message}"
                }
            }
            RootSetupTestResult(
                hasRoot = idOutput.contains("uid=0(root)"),
                idOutput = idOutput,
                solution = solution,
                frameworkConnected = service != null,
                remoteConfigAccessible = accessible,
                configPublished = published,
                frameworkDetail = detail
            )
        }

    /** Force-stops selected apps so their next launch loads the current module and configuration. */
    suspend fun forceStopApps(packages: List<String>): List<Pair<String, Boolean>> =
        withContext(Dispatchers.IO) {
            packages.map { pkg ->
                pkg to (isValidPackageName(pkg) && executeCommand("am force-stop $pkg") != "ERROR")
            }
        }

    suspend fun grantMockLocation(): Boolean = withContext(Dispatchers.IO) {
        executeCommand("appops set com.vincenthzr.locationspoofer android:mock_location allow") != "ERROR"
    }

    suspend fun revokeMockLocation(): Boolean = withContext(Dispatchers.IO) {
        executeCommand("appops set com.vincenthzr.locationspoofer android:mock_location default") != "ERROR"
    }

    /**
     * Execute an internal, application-authored shell command.
     * Never concatenate untrusted input into [command]; validate dynamic arguments before this call.
     */
    fun executeCommand(command: String): String = runCommand(command, null)

    fun executeCommandWithInput(command: String, input: String): String = runCommand(command, input)

    private fun runCommand(command: String, input: String?): String {
        var process: Process? = null
        var reader: Thread? = null
        var writer: Thread? = null
        val output = StringBuilder()
        return try {
            process = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
            val activeProcess = process

            // Always drain stdout/stderr; retain only a bounded prefix to avoid memory exhaustion.
            reader = Thread({
                try {
                    activeProcess.inputStream.use { stream ->
                        val buffer = ByteArray(4096)
                        while (true) {
                            val count = stream.read(buffer)
                            if (count < 0) break
                            synchronized(output) {
                                val remaining = MAX_OUTPUT_CHARS - output.length
                                if (remaining > 0) {
                                    output.append(String(buffer, 0, minOf(count, remaining), Charsets.UTF_8))
                                }
                            }
                        }
                    }
                } catch (_: IOException) {
                    // Process termination closes the stream; this is expected on timeout.
                }
            }, "LocationSp-root-output").apply {
                isDaemon = true
                start()
            }

            writer = Thread({
                try {
                    activeProcess.outputStream.bufferedWriter(Charsets.UTF_8).use { stream ->
                        if (input != null) stream.write(input)
                    }
                } catch (_: IOException) {
                    // The command may exit before consuming stdin.
                }
            }, "LocationSp-root-input").apply {
                isDaemon = true
                start()
            }

            val completed = activeProcess.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!completed) {
                Log.e(TAG, "Root command timed out after ${COMMAND_TIMEOUT_SECONDS}s")
                activeProcess.destroy()
                if (!activeProcess.waitFor(500, TimeUnit.MILLISECONDS)) activeProcess.destroyForcibly()
                return "ERROR"
            }

            writer.join(1000)
            reader.join(2000)
            val result = synchronized(output) { output.toString() }
            if (activeProcess.exitValue() == 0) result.ifEmpty { "SUCCESS" } else {
                Log.e(TAG, "Root command failed (${activeProcess.exitValue()}): ${result.take(2000)}")
                "ERROR"
            }
        } catch (error: Exception) {
            if (error is InterruptedException) Thread.currentThread().interrupt()
            Log.e(TAG, "Root command failed", error)
            "ERROR"
        } finally {
            try {
                process?.outputStream?.close()
                process?.inputStream?.close()
            } catch (_: IOException) {
                // Best-effort stream cleanup.
            }
            process?.destroy()
            reader?.join(250)
            writer?.join(250)
        }
    }
}
