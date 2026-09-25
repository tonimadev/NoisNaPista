package com.ipirangatech.fidd.feature.tracker.impl

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ipirangatech.fidd.core.model.LocationPoint

/**
 * Raw GPS/accelerometer readouts, collapsed by default: useful to see the sensors reacting (and for
 * whoever is tuning the detector), but not something a driver needs to read to use the app.
 */
@Composable
internal fun SensorDetailsSection(
    isTracking: Boolean,
    currentLocation: LocationPoint?,
    x: Float,
    y: Float,
    z: Float,
    verticalIntensity: Float,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column {
        TextButton(onClick = { expanded = !expanded }) {
            Text(stringResource(R.string.tracker_sensor_details_toggle))
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = null,
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(horizontal = 4.dp)) {
                StatusRow(isTracking = isTracking)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.tracker_current_location_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text =
                        currentLocation?.let {
                            stringResource(R.string.tracker_lat_lon_format, it.latitude, it.longitude)
                        } ?: stringResource(R.string.tracker_waiting_gps),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(12.dp))
                SensorReadingPanel(
                    x = x,
                    y = y,
                    z = z,
                    verticalIntensity = verticalIntensity,
                    isTracking = isTracking,
                )
            }
        }
    }
}

@Composable
private fun StatusRow(isTracking: Boolean) {
    val statusColor =
        if (isTracking) {
            MaterialTheme.colorScheme.tertiary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }

    Row {
        Text(
            text = stringResource(R.string.tracker_gps_label),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(if (isTracking) R.string.tracker_gps_active else R.string.tracker_gps_inactive),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = statusColor,
        )
        Text(
            text = stringResource(R.string.tracker_sensors_label),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(if (isTracking) R.string.tracker_sensors_on else R.string.tracker_sensors_off),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = statusColor,
        )
    }
}

@Composable
private fun SensorReadingPanel(
    x: Float,
    y: Float,
    z: Float,
    verticalIntensity: Float,
    isTracking: Boolean,
) {
    val intensityColor by animateColorAsState(
        targetValue =
            when {
                !isTracking -> MaterialTheme.colorScheme.onSurfaceVariant
                verticalIntensity >= IMPACT_THRESHOLD -> MaterialTheme.colorScheme.error
                verticalIntensity >= WARNING_THRESHOLD -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.tertiary
            },
        label = "sensorIntensityColor",
    )

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.tracker_sensor_reading_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text =
                    when {
                        !isTracking -> stringResource(R.string.common_placeholder_dash)
                        verticalIntensity >= IMPACT_THRESHOLD -> stringResource(R.string.tracker_impact_label)
                        else -> stringResource(R.string.tracker_sensor_value_format, verticalIntensity)
                    },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = intensityColor,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            AxisReadingChip(stringResource(R.string.tracker_axis_x), x, AXIS_X_COLOR)
            AxisReadingChip(stringResource(R.string.tracker_axis_y), y, AXIS_Y_COLOR)
            AxisReadingChip(stringResource(R.string.tracker_axis_z), z, AXIS_Z_COLOR)
        }

        Spacer(modifier = Modifier.height(4.dp))

        SensorAxisGizmo(x = x, y = y, z = z)

        Text(
            text = stringResource(R.string.tracker_z_only_detection_caption),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun AxisReadingChip(
    axisLabel: String,
    value: Float,
    color: Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(color))
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = stringResource(R.string.tracker_axis_value_format, axisLabel, value),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Draws raw X/Y/Z as three spikes from a shared origin along fixed isometric directions — a live
 * "3D" readout of the raw vector, instead of the three axes overlapping as line traces over time
 * (compare the debug/classification screen's post-hoc SensorWindowChart, which plots history and
 * so needs that line-chart shape; this widget only ever shows the current instant). Context only:
 * the value that actually decides "this is a pothole" is shown, dynamically colored, in the
 * header above — see SensorReadingPanel. */
@Composable
private fun SensorAxisGizmo(
    x: Float,
    y: Float,
    z: Float,
    modifier: Modifier = Modifier,
) {
    val guideColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    val originColor = MaterialTheme.colorScheme.onSurfaceVariant

    Canvas(modifier = modifier.fillMaxWidth().height(150.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val guideLength = size.minDimension / 2.1f
        val scale = guideLength / MAX_SENSOR_SCALE

        fun drawGuide(direction: Offset) {
            drawLine(
                color = guideColor,
                start = center,
                end = center + direction * guideLength,
                strokeWidth = 2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
            )
        }
        drawGuide(AXIS_X_DIR)
        drawGuide(AXIS_Y_DIR)
        drawGuide(AXIS_Z_DIR)

        fun drawReading(
            direction: Offset,
            value: Float,
            color: Color,
        ) {
            val length = value.coerceIn(-MAX_SENSOR_SCALE, MAX_SENSOR_SCALE) * scale
            val tip = center + direction * length
            drawLine(color = color, start = center, end = tip, strokeWidth = 6.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(color = color, radius = 5.dp.toPx(), center = tip)
        }
        drawReading(AXIS_X_DIR, x, AXIS_X_COLOR)
        drawReading(AXIS_Y_DIR, y, AXIS_Y_COLOR)
        drawReading(AXIS_Z_DIR, z, AXIS_Z_COLOR)

        drawCircle(color = originColor, radius = 4.dp.toPx(), center = center)
    }
}

// Same 15 m/s² line PotholeDetector uses to decide "this is a pothole" — the header status text
// turns red at exactly the value that would trigger a detection. That value is the
// gravity-corrected vertical acceleration (see PotholeDetector.computeVerticalAcceleration), not
// simply abs(z) — a bump can land mostly on the raw X or Y axis if the phone isn't mounted flat,
// so the X/Y/Z chips and gizmo below are shown as fixed-color raw context only.
private const val IMPACT_THRESHOLD = 15f
private const val WARNING_THRESHOLD = 10f
private const val MAX_SENSOR_SCALE = 30f

private val AXIS_X_COLOR = Color(0xFF4FC3F7)
private val AXIS_Y_COLOR = Color(0xFF81C784)
private val AXIS_Z_COLOR = Color(0xFFBA68C8)

// Fixed isometric projection directions for a simple 3-axis "gizmo": Z straight up, X/Y splayed
// 30° down to either side — the classic isometric-cube look, cheap to draw with plain lines
// instead of pulling in a 3D rendering library for three numbers.
private val AXIS_X_DIR = Offset(0.866f, 0.5f)
private val AXIS_Y_DIR = Offset(-0.866f, 0.5f)
private val AXIS_Z_DIR = Offset(0f, -1f)
