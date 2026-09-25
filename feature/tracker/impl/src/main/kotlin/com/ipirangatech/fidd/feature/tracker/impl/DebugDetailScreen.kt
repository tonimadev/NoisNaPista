package com.ipirangatech.fidd.feature.tracker.impl

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ipirangatech.fidd.core.model.DetectionDebugEntry
import com.ipirangatech.fidd.core.model.DetectionLabel
import com.ipirangatech.fidd.core.model.SensorWindowSample
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugDetailScreen(
    viewModel: DebugViewModel,
    potholeId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val entry = entries.find { it.pothole.id == potholeId }
    val context = LocalContext.current
    val mapsApiKey = remember(context) { getMapsApiKey(context) }
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.forLanguageTag("pt-BR")) }
    val tripNumbersBySession = remember(entries) { tripNumbersBySession(entries) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.debug_detail_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back_cd),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        if (entry == null) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.debug_detection_not_found),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }

        var noteText by remember(entry.pothole.id) { mutableStateOf(entry.note) }
        LaunchedEffect(entry.pothole.id, noteText) {
            delay(500)
            if (noteText != entry.note) viewModel.updateNote(entry.pothole.id, noteText)
        }

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
        ) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    if (mapsApiKey != null) {
                        val noMapsAppFoundMessage = stringResource(R.string.common_no_maps_app_found)
                        AsyncImage(
                            model =
                                staticMapUrl(
                                    mapsApiKey,
                                    entry.pothole.location.latitude,
                                    entry.pothole.location.longitude,
                                ),
                            contentDescription = stringResource(R.string.debug_map_location_cd),
                            contentScale = ContentScale.Crop,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(160.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        val latLon =
                                            "${entry.pothole.location.latitude},${entry.pothole.location.longitude}"
                                        val uri = Uri.parse("geo:$latLon?q=$latLon")
                                        try {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                        } catch (e: ActivityNotFoundException) {
                                            Toast.makeText(context, noMapsAppFoundMessage, Toast.LENGTH_SHORT).show()
                                        }
                                    },
                        )
                    }
                }

                item {
                    MetadataCard(
                        entry = entry,
                        dateText = dateFormat.format(Date(entry.pothole.timestamp)),
                        tripNumber = tripNumbersBySession[entry.pothole.sessionId] ?: 0,
                    )
                }

                item {
                    Column {
                        Text(
                            stringResource(R.string.debug_sensor_window_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        SensorWindowChart(samples = entry.sensorWindow?.samples.orEmpty())
                    }
                }

                item {
                    Column {
                        Text(
                            stringResource(R.string.debug_classification_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            DetectionLabel.entries.forEach { label ->
                                FilterChip(
                                    selected = entry.label == label,
                                    onClick = { viewModel.updateLabel(entry.pothole.id, label) },
                                    label = { Text(label.displayName) },
                                )
                            }
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        label = { Text(stringResource(R.string.debug_note_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                    )
                }
            }
        }
    }
}

@Composable
private fun MetadataCard(
    entry: DetectionDebugEntry,
    dateText: String,
    tripNumber: Int,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp)) {
            MetadataRow(
                stringResource(R.string.debug_metadata_trip),
                stringResource(R.string.debug_trip_number_format, tripNumber),
            )
            MetadataRow(stringResource(R.string.debug_metadata_datetime), dateText)
            MetadataRow(
                stringResource(R.string.debug_metadata_location),
                stringResource(
                    R.string.debug_lat_lon_precise_format,
                    entry.pothole.location.latitude,
                    entry.pothole.location.longitude,
                ),
            )
            MetadataRow(
                stringResource(R.string.debug_metadata_accuracy),
                stringResource(R.string.debug_accuracy_format, entry.pothole.location.accuracy),
            )
            MetadataRow(
                stringResource(R.string.debug_metadata_trigger_severity),
                stringResource(R.string.debug_severity_format, entry.pothole.severity),
            )
            MetadataRow(
                stringResource(R.string.debug_metadata_peak),
                entry.peakAbsZ?.let {
                    stringResource(R.string.debug_severity_format, it)
                } ?: stringResource(R.string.common_placeholder_dash),
            )
            MetadataRow(
                stringResource(R.string.debug_metadata_sample_count),
                entry.sensorWindow?.samples?.size?.toString() ?: stringResource(R.string.debug_awaiting_lowercase),
            )
            MetadataRow(
                stringResource(R.string.debug_metadata_sync),
                stringResource(
                    if (entry.pothole.serverId != null) R.string.history_synced else R.string.history_pending_sync,
                ),
            )
            val backendStatus = entry.pothole.status
            if (backendStatus != null) {
                MetadataRow(
                    stringResource(R.string.debug_metadata_backend_status),
                    stringResource(
                        R.string.debug_backend_status_format,
                        backendStatus,
                        entry.pothole.distinctReporterCount,
                    ),
                )
            }
        }
    }
}

@Composable
private fun MetadataRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
    }
}

/** Plots raw x/y/z acceleration against time-since-impact so the user can visually correlate the
 * shape of the reading with what they remember of the road (a sharp single spike vs. a longer
 * rumble, for instance) — the kind of detail a single severity number can't convey. */
@Composable
private fun SensorWindowChart(
    samples: List<SensorWindowSample>,
    modifier: Modifier = Modifier,
) {
    if (samples.isEmpty()) {
        Box(
            modifier =
                modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.debug_no_sensor_data), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val sorted = remember(samples) { samples.sortedBy { it.offsetMs } }
    val minOffset = sorted.first().offsetMs.toFloat()
    val maxOffset = sorted.last().offsetMs.toFloat()
    val offsetRange = (maxOffset - minOffset).coerceAtLeast(1f)
    val minValue = sorted.minOf { minOf(it.x, it.y, it.z) }
    val maxValue = sorted.maxOf { maxOf(it.x, it.y, it.z) }
    val valueRange = (maxValue - minValue).coerceAtLeast(0.1f)

    val xColor = Color(0xFF4FC3F7)
    val yColor = Color(0xFF81C784)
    val zColor = MaterialTheme.colorScheme.error
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)

    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChartLegendDot(xColor, stringResource(R.string.debug_axis_x))
            Spacer(modifier = Modifier.width(12.dp))
            ChartLegendDot(yColor, stringResource(R.string.debug_axis_y))
            Spacer(modifier = Modifier.width(12.dp))
            ChartLegendDot(zColor, stringResource(R.string.debug_axis_z))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Canvas(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(1.dp),
        ) {
            val strokeWidthPx = 2.5.dp.toPx()

            fun px(offsetMs: Long): Float = ((offsetMs - minOffset) / offsetRange) * size.width

            fun py(value: Float): Float = size.height - ((value - minValue) / valueRange) * size.height

            if (minOffset <= 0f && maxOffset >= 0f) {
                val x0 = px(0)
                drawLine(
                    color = gridColor,
                    start = Offset(x0, 0f),
                    end = Offset(x0, size.height),
                    strokeWidth = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)),
                )
            }

            fun drawSeries(
                color: Color,
                valueOf: (SensorWindowSample) -> Float,
            ) {
                val path = Path()
                sorted.forEachIndexed { index, sample ->
                    val x = px(sample.offsetMs)
                    val y = py(valueOf(sample))
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, color = color, style = Stroke(width = strokeWidthPx))
            }
            drawSeries(xColor) { it.x }
            drawSeries(yColor) { it.y }
            drawSeries(zColor) { it.z }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                stringResource(R.string.debug_chart_time_min_format, minOffset.toInt()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.debug_chart_impact_marker),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.debug_chart_time_max_format, maxOffset.toInt()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ChartLegendDot(
    color: Color,
    label: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
