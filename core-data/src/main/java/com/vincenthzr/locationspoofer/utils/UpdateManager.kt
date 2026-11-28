package com.vincenthzr.locationspoofer.utils

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

class UpdateManager(private val context: Context) {
    fun downloadApk(url: String, fileName: String): Long {
        val uri = Uri.parse(url)
        require(uri.scheme == "https" && uri.host == "github.com") { "Only HTTPS GitHub release assets are allowed" }
        require(fileName.matches(Regex("[A-Za-z0-9._-]+\\.apk"))) { "Invalid APK file name" }
        val request = DownloadManager.Request(uri)
            .setTitle("LocationSpoofer $fileName")
            .setDescription("Downloading update...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = dm.enqueue(request)
        check(downloadId > 0L) {
            "Android DownloadManager rejected the APK download request"
        }
        return downloadId
    }

    fun getDownloadProgress(downloadId: Long): Int {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(downloadId)
        dm.query(query)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                if (total > 0) return (downloaded * 100L / total).toInt()
            }
        }
        return 0
    }

    fun getDownloadStatus(downloadId: Long): Int {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(downloadId)
        dm.query(query)?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
        }
        return DownloadManager.STATUS_FAILED
    }

    /** Verify the published SHA-256, APK package id and installed signing certificate before launching Package Installer. */
    fun installApk(downloadId: Long, expectedSha256: String) {
        val normalized = expectedSha256.removePrefix("sha256:").lowercase()
        require(normalized.matches(Regex("[a-f0-9]{64}"))) { "Missing or invalid trusted SHA-256 metadata" }
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val uri = dm.getUriForDownloadedFile(downloadId) ?: error("Downloaded APK is unavailable")
        val temp = File.createTempFile("locationspoofer-update-", ".apk", context.cacheDir)
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0L
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(temp).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= MAX_APK_BYTES) { "APK exceeds the configured size limit" }
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                }
            } ?: error("Cannot read downloaded APK")
            val actualSha256 = digest.digest().joinToString("") { "%02x".format(it) }
            require(actualSha256 == normalized) { "APK SHA-256 mismatch; installation blocked" }

            val packageInfoFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_SIGNATURES
            }
            val archive = context.packageManager.getPackageArchiveInfo(temp.absolutePath, packageInfoFlags)
                ?: error("Downloaded file is not a valid APK")
            require(archive.packageName == context.packageName) { "APK package name does not match installed app" }
            val installed = context.packageManager.getPackageInfo(context.packageName, packageInfoFlags)
            val archiveSigners = signerDigests(archive)
            val installedSigners = signerDigests(installed)
            require(archiveSigners.isNotEmpty() && archiveSigners == installedSigners) {
                "APK signing certificate differs from the installed application or is unavailable"
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                this.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
        } finally {
            temp.delete()
        }
    }

    @Suppress("DEPRECATION")
    private fun signerDigests(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners
        } else {
            info.signatures
        } ?: emptyArray()
        return signatures.map { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }.toSet()
    }

    fun cancelDownload(downloadId: Long) {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.remove(downloadId)
    }

    private companion object {
        const val MAX_APK_BYTES = 200L * 1024L * 1024L
    }
}
