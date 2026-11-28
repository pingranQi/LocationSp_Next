package com.vincenthzr.locationspoofer.ui.screen.tabs.route

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vincenthzr.locationspoofer.data.model.RoutePoint
import com.vincenthzr.locationspoofer.ui.R

@Composable
fun RoutePointEditorDialog(
    points: List<RoutePoint>,
    onDismiss: () -> Unit,
    onMove: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit,
    onWaitChange: (Int, Double) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.route_edit_points)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.route_edit_points_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    itemsIndexed(points) { index, point ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = (index + 1).toString() + ".  " + String.format(java.util.Locale.US, "%.6f", point.lat) + ", " + String.format(java.util.Locale.US, "%.6f", point.lng),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                stringResource(R.string.route_waypoint_wait, point.waitSec.toInt()),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            IconButton(
                                onClick = { onWaitChange(index, (point.waitSec - 5.0).coerceAtLeast(0.0)) },
                                enabled = point.waitSec > 0.0
                            ) {
                                Icon(Icons.Rounded.Remove, contentDescription = stringResource(R.string.reduce_wait))
                            }
                            IconButton(
                                onClick = { onWaitChange(index, (point.waitSec + 5.0).coerceAtMost(86_400.0)) },
                                enabled = point.waitSec < 86_400.0
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.increase_wait))
                            }
                            IconButton(onClick = { if (index > 0) onMove(index, index - 1) }, enabled = index > 0) {
                                Icon(Icons.Rounded.ArrowUpward, contentDescription = stringResource(R.string.move_up))
                            }
                            IconButton(onClick = { if (index < points.lastIndex) onMove(index, index + 1) }, enabled = index < points.lastIndex) {
                                Icon(Icons.Rounded.ArrowDownward, contentDescription = stringResource(R.string.move_down))
                            }
                            IconButton(onClick = { onRemove(index) }) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = stringResource(R.string.remove_waypoint))
                            }
                        }
                    }
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
