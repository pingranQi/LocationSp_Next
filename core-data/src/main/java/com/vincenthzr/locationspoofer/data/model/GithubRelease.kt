package com.vincenthzr.locationspoofer.data.model

data class GithubRelease(
    val versionName: String,
    val body: String,
    val downloadUrl: String?,
    val downloadUrl32Bit: String?,
    val publishedAt: String,
    val isPrerelease: Boolean,
    val downloadSha256: String? = null,
    val download32Sha256: String? = null
)
