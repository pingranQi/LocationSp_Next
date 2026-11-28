package com.vincenthzr.locationspoofer.ui.screen.tabs.route

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vincenthzr.locationspoofer.data.model.AppState
import com.vincenthzr.locationspoofer.data.motion.MotionController
import com.vincenthzr.locationspoofer.ui.R
import com.vincenthzr.locationspoofer.utils.CoordinateUtils
import com.vincenthzr.locationspoofer.utils.GeoMath

@Composable
fun RouteDiagnosticsDialog(
    uiState: AppState,
    motionState: MotionController.State,
    onDismiss: () -> Unit
) {
    val points = uiState.routePoints
    val elevations = points.mapNotNull { it.elevationMeters }
    val distanceMeters = points.zipWithNext().sumOf { (a, b) ->
        GeoMath.distance(CoordinateUtils.LatLng(a.lat, a.lng), CoordinateUtils.LatLng(b.lat, b.lng))
    }
    val currentLat = uiState.latitudeInput.toDoubleOrNull()?.takeIf { it.isFinite() }
    val currentLng = uiState.longitudeInput.toDoubleOrNull()?.takeIf { it.isFinite() }
    val routeDeviation = if (currentLat != null && currentLng != null && motionState.lat.isFinite() && motionState.lng.isFinite()) {
        GeoMath.distance(CoordinateUtils.LatLng(currentLat, currentLng), CoordinateUtils.LatLng(motionState.lat, motionState.lng))
    } else null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.route_diagnostics)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.route_diagnostics_desc), style = MaterialTheme.typography.bodySmall)
                DiagnosticRow(stringResource(R.string.route_diag_simulation), if (uiState.isSpoofingActive) stringResource(R.string.route_diag_active) else stringResource(R.string.route_diag_inactive))
                DiagnosticRow(
                    stringResource(R.string.route_diag_motion),
                    motionState.mode.name + " · " + (if (motionState.paused) stringResource(R.string.route_diag_paused) else stringResource(R.string.route_diag_playing))
                )
                DiagnosticRow(stringResource(R.string.route_diag_waypoints), points.size.toString())
                if (elevations.size >= 2) {
                    Text(stringResource(R.string.route_diag_elevation_profile), style = MaterialTheme.typography.labelMedium)
                    RouteElevationProfile(elevations)
                    Text(
                        String.format(java.util.Locale.US, "%.1f–%.1f m", elevations.minOrNull() ?: 0.0, elevations.maxOrNull() ?: 0.0),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(stringResource(R.string.route_diag_no_elevation), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DiagnosticRow(stringResource(R.string.route_diag_distance), String.format(java.util.Locale.US, "%.2f km", distanceMeters / 1000.0))
                DiagnosticRow(stringResource(R.string.route_diag_speed), String.format(java.util.Locale.US, "%.2f m/s", motionState.speedMs))
                DiagnosticRow(stringResource(R.string.route_diag_update_interval), motionState.lastUpdateIntervalMs.toString() + " ms")
                DiagnosticRow(
                    stringResource(R.string.route_diag_route_deviation),
                    routeDeviation?.let { String.format(java.util.Locale.US, "%.2f m", it) } ?: "—"
                )
                DiagnosticRow(stringResource(R.string.route_diag_root_access), if (uiState.hasRootAccess) stringResource(R.string.route_diag_available) else stringResource(R.string.route_diag_unavailable))
                DiagnosticRow(stringResource(R.string.route_diag_xposed), if (uiState.isLSPosedActive) stringResource(R.string.route_diag_active) else stringResource(R.string.route_diag_inactive))
                DiagnosticRow(
                    stringResource(R.string.route_diag_location),
                    if (currentLat != null && currentLng != null) String.format(java.util.Locale.US, "%.6f, %.6f", currentLat, currentLng) else "—"
                )
                DiagnosticRow(stringResource(R.string.route_diag_mock_wifi), (if (uiState.mockWifi) stringResource(R.string.route_diag_enabled) else stringResource(R.string.route_diag_disabled)) + " · " + (if (uiState.canMockWifi) stringResource(R.string.route_diag_available) else stringResource(R.string.route_diag_unavailable)))
                DiagnosticRow(stringResource(R.string.route_diag_mock_cell), (if (uiState.mockCell) stringResource(R.string.route_diag_enabled) else stringResource(R.string.route_diag_disabled)) + " · " + (if (uiState.canMockCell) stringResource(R.string.route_diag_available) else stringResource(R.string.route_diag_unavailable)))
                DiagnosticRow(stringResource(R.string.route_diag_mock_bluetooth), (if (uiState.mockBluetooth) stringResource(R.string.route_diag_enabled) else stringResource(R.string.route_diag_disabled)) + " · " + (if (uiState.canMockBluetooth) stringResource(R.string.route_diag_available) else stringResource(R.string.route_diag_unavailable)))
                if (uiState.isFetchingRoute) {
                    Text(stringResource(R.string.route_diag_fetching), color = MaterialTheme.colorScheme.primary)
                }
                if (points.size < 2) {
                    Text(stringResource(R.string.route_diag_need_points), color = MaterialTheme.colorScheme.error)
                } else if (distanceMeters <= 0.0) {
                    Text(stringResource(R.string.route_diag_zero_length), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.done))
            }
        }
    )
}

@Composable
private fun RouteElevationProfile(elevations: List<Double>) {
    val lineColor = MaterialTheme.colorScheme.primary
    val minimum = elevations.minOrNull() ?: 0.0
    val maximum = elevations.maxOrNull() ?: minimum
    val range = (maximum - minimum).takeIf { it > 0.0 } ?: 1.0
    Canvas(modifier = Modifier.fillMaxWidth().height(72.dp)) {
        if (elevations.size < 2) return@Canvas
        val path = Path()
        elevations.forEachIndexed { index, elevation ->
            val x = size.width * index / (elevations.size - 1).toFloat()
            val y = size.height - ((elevation - minimum) / range).toFloat() * size.height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = lineColor, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
