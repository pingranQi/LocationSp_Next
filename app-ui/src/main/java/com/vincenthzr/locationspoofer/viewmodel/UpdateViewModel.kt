package com.vincenthzr.locationspoofer.viewmodel

import android.app.DownloadManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vincenthzr.locationspoofer.data.model.GithubRelease
import com.vincenthzr.locationspoofer.ui.BuildConfig
import com.vincenthzr.locationspoofer.utils.UpdateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

data class UpdateUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val releases: List<GithubRelease> = emptyList(),
    val activeDownloadId: Long? = null,
    val activeDownloadUrl: String? = null,
    val activeDownloadSha256: String? = null,
    val downloadProgress: Int = 0,
    val downloadStatus: Int = DownloadManager.STATUS_PENDING
)

class UpdateViewModel(private val context: Context) : ViewModel() {
    private val _uiState = MutableStateFlow(UpdateUiState())
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    private val updateManager = UpdateManager(context)
    private val okHttpClient = OkHttpClient()

    fun fetchReleases() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("https://api.github.com/repos/ZenQing-Tao/LocationSp_Next/releases")
                    .header("Accept", "application/vnd.github.v3+json")
                    .build()

                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    throw Exception("Failed to fetch updates: ${response.code}")
                }

                val jsonStr = response.body?.string() ?: "[]"
                val jsonArray = JSONArray(jsonStr)
                val releaseList = mutableListOf<GithubRelease>()

                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val tagName = obj.optString("tag_name", "Unknown")
                    val body = obj.optString("body", "")
                    val publishedAt = obj.optString("published_at", "")
                    val isPrerelease = obj.optBoolean("prerelease", false)

                    var downloadUrl: String? = null
                    var downloadUrl32Bit: String? = null
                    var downloadSha256: String? = null
                    var download32Sha256: String? = null
                    val assets = obj.optJSONArray("assets")
                    if (assets != null) {
                        for (j in 0 until assets.length()) {
                            val asset = assets.getJSONObject(j)
                            val assetName = asset.optString("name", "")
                            if (assetName.endsWith(".apk") && isAssetForCurrentScheme(assetName)) {
                                val url = asset.optString("browser_download_url")
                                val digest = asset.optString("digest").removePrefix("sha256:")
                                    .takeIf { it.matches(Regex("[A-Fa-f0-9]{64}")) }
                                if (assetName.contains("armeabi-v7a") || assetName.contains("32")) {
                                    downloadUrl32Bit = url
                                    download32Sha256 = digest
                                } else if (assetName.contains("arm64-v8a")) {
                                    downloadUrl = url
                                    downloadSha256 = digest
                                } else if (downloadUrl == null) {
                                    // 通用 APK 仅作为主链接；必须有 GitHub 提供的 asset digest 才允许安装。
                                    downloadUrl = url
                                    downloadSha256 = digest
                                }
                            }
                        }
                    }

                    if (downloadUrl == null && downloadUrl32Bit != null) {
                        downloadUrl = downloadUrl32Bit
                        downloadSha256 = download32Sha256
                    }

                    releaseList.add(
                        GithubRelease(
                            tagName,
                            body,
                            downloadUrl,
                            downloadUrl32Bit,
                            publishedAt,
                            isPrerelease,
                            downloadSha256,
                            download32Sha256
                        )
                    )
                }

                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isLoading = false, releases = releaseList) }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
            }
        }
    }

    fun startDownload(url: String, versionName: String, expectedSha256: String? = null) {
        val digest = expectedSha256?.removePrefix("sha256:")?.lowercase()
        if (digest == null || !digest.matches(Regex("[a-f0-9]{64}"))) {
            _uiState.update { it.copy(error = "Release 未提供有效 SHA-256，已拒绝安装该 APK。") }
            return
        }
        val fileName = "LocationSpoofer_${versionName.replace(Regex("[^A-Za-z0-9._-]"), "_")}.apk"
        val downloadId = updateManager.downloadApk(url, fileName)
        _uiState.update {
            it.copy(
                activeDownloadId = downloadId,
                activeDownloadUrl = url,
                activeDownloadSha256 = digest,
                error = null,
                downloadProgress = 0,
                downloadStatus = DownloadManager.STATUS_PENDING
            )
        }
        monitorDownload(downloadId)
    }

    private fun monitorDownload(downloadId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                val status = updateManager.getDownloadStatus(downloadId)
                val progress = updateManager.getDownloadProgress(downloadId)

                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            downloadStatus = status,
                            downloadProgress = progress
                        )
                    }
                }

                if (status == DownloadManager.STATUS_SUCCESSFUL || status == DownloadManager.STATUS_FAILED) {
                    break
                }
                delay(500)
            }
        }
    }

    fun installApk() {
        val state = _uiState.value
        val downloadId = state.activeDownloadId ?: return
        val digest = state.activeDownloadSha256 ?: run {
            _uiState.update { it.copy(error = "缺少 APK SHA-256，拒绝安装。") }
            return
        }
        try {
            updateManager.installApk(downloadId, digest)
        } catch (error: Exception) {
            _uiState.update { it.copy(error = error.message ?: "APK 完整性验证失败，已拒绝安装。") }
        }
    }

    fun cancelDownload() {
        _uiState.value.activeDownloadId?.let { downloadId ->
            updateManager.cancelDownload(downloadId)
            _uiState.update {
                it.copy(
                    activeDownloadId = null,
                    activeDownloadUrl = null,
                    activeDownloadSha256 = null,
                    downloadProgress = 0,
                    downloadStatus = DownloadManager.STATUS_PENDING
                )
            }
        }
    }
}

/**
 * 同一个 Release 里同时挂着两个模拟方案的 APK，文件名形如 LocationSpoofer-<scheme>-<abi>-<tag>.apk
 * （见 .github/workflows/release.yml），只挑和当前安装的方案一致的那一个。
 * 引入 scheme 变体之前的旧 Release 文件名里没有方案标记，那时发布的都是非全局（scoped）方案。
 */
private fun isAssetForCurrentScheme(assetName: String): Boolean {
    val markedGlobal = assetName.contains("-global-")
    return if (BuildConfig.GLOBAL_SCHEME) markedGlobal else !markedGlobal
}
