package com.vincenthzr.locationspoofer.ui.screen.tabs.route

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
    val distanceMeters = points.zipWithNext().sumOf { (a, b) ->
        GeoMath.distance(CoordinateUtils.LatLng(a.lat, a.lng), CoordinateUtils.LatLng(b.lat, b.lng))
    }
    val currentLat = uiState.latitudeInput.toDoubleOrNull()
    val currentLng = uiState.longitudeInput.toDoubleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.route_diagnostics)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.route_diagnostics_desc), style = MaterialTheme.typography.bodySmall)
                DiagnosticRow(stringResource(R.string.route_diag_simulation), if (uiState.isSpoofingActive) stringResource(R.string.route_diag_active) else stringResource(R.string.route_diag_inactive))
                DiagnosticRow(
                    stringResource(R.string.route_diag_motion),
                    motionState.mode.name + " · " + if (motionState.paused) stringResource(R.string.route_diag_paused) else stringResource(R.string.route_diag_playing)
                )
                DiagnosticRow(stringResource(R.string.route_diag_waypoints), points.size.toString())
                DiagnosticRow(stringResource(R.string.route_diag_distance), String.format(java.util.Locale.US, "%.2f km", distanceMeters / 1000.0))
                DiagnosticRow(stringResource(R.string.route_diag_speed), String.format(java.util.Locale.US, "%.2f m/s", motionState.speedMs))
                DiagnosticRow(
                    stringResource(R.string.route_diag_location),
                    if (currentLat != null && currentLng != null) String.format(java.util.Locale.US, "%.6f, %.6f", currentLat, currentLng) else "—"
                )
                DiagnosticRow(stringResource(R.string.route_diag_mock_wifi), (if (uiState.mockWifi) stringResource(R.string.route_diag_enabled) else stringResource(R.string.route_diag_disabled)) + " · " + (if (uiState.canMockWifi) stringResource(R.string.route_diag_available) else stringResource(R.string.route_diag_unavailable)))
                DiagnosticRow(stringResource(R.string.route_diag_mock_cell), if (uiState.mockCell) stringResource(R.string.status_enabled) else stringResource(R.string.status_disabled))
                DiagnosticRow(stringResource(R.string.route_diag_mock_bluetooth), if (uiState.mockBluetooth) stringResource(R.string.status_enabled) else stringResource(R.string.status_disabled))
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
private fun DiagnosticRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
