package com.vincenthzr.locationspoofer.data.model

import kotlinx.serialization.Serializable

@Serializable
data class RoutePoint(
    val lat: Double,
    val lng: Double,
    val waitSec: Double = 0.0,
    val elevationMeters: Double? = null,
    val timestampEpochMillis: Long? = null
)
