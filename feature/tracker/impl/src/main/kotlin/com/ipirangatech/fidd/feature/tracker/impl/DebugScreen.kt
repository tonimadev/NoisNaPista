package com.ipirangatech.fidd.feature.tracker.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ipirangatech.fidd.core.model.DetectionDebugEntry
import com.ipirangatech.fidd.core.model.DetectionLabel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Debug-build-only screen (see MainActivity's BuildConfig.DEBUG gate) listing every local
 * detection so the user can classify each one — "buraco real", "lombada", "alarme falso" etc —
 * after a week of driving with the app running. This is the labeled-dataset source for future
 * on-device/backend model training.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugListScreen(
    viewModel: DebugViewModel,
    onOpenDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf<DetectionLabel?>(null) }
    val visibleEntries =
        remember(entries, filter) {
            filter?.let { f -> entries.filter { it.label == f } } ?: entries
        }
    val countsByLabel = remember(entries) { entries.groupingBy { it.label }.eachCount() }
    // Trip numbers are assigned from the full (unfiltered) entry list, keyed by each session's
    // earliest detection, so "Viagem 3" doesn't shift around as the label filter changes.
    val tripNumbersBySession = remember(entries) { tripNumbersBySession(entries) }
    val sessionGroups =
        remember(visibleEntries, tripNumbersBySession) {
            visibleEntries.groupBy { it.pothole.sessionId }
                .toList()
                .sortedByDescending { (_, sessionEntries) -> sessionEntries.maxOf { it.pothole.timestamp } }
                .map { (sessionId, sessionEntries) ->
                    DebugSessionGroup(
                        sessionId = sessionId,
                        tripNumber = tripNumbersBySession[sessionId] ?: 0,
                        entries = sessionEntries,
                    )
                }
        }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.debug_list_title), fontWeight = FontWeight.Bold) },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Text(
                text =
                    stringResource(
                        R.string.debug_capture_summary_format,
                        entries.size,
                        countsByLabel[DetectionLabel.UNLABELED] ?: 0,
                        tripNumbersBySession.size,
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = filter == null,
                    onClick = { filter = null },
                    label = { Text(stringResource(R.string.debug_filter_all_format, entries.size)) },
                )
                DetectionLabel.entries.forEach { label ->
                    FilterChip(
                        selected = filter == label,
                        onClick = { filter = if (filter == label) null else label },
                        label = {
                            Text(
                                stringResource(
                                    R.string.debug_filter_label_format,
                                    label.displayName,
                                    countsByLabel[label] ?: 0,
                                ),
                            )
                        },
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(top = 12.dp))

            if (visibleEntries.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.debug_empty_category),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    sessionGroups.forEach { group ->
                        item(key = "session_${group.sessionId}") {
                            SessionHeader(group = group)
                        }
                        items(group.entries, key = { it.pothole.id }) { entry ->
                            DebugListItem(entry = entry, onClick = { onOpenDetail(entry.pothole.id) })
                        }
                    }
                }
            }
        }
    }
}

/** Assigns each session a stable "Viagem N" number from its earliest detection (oldest trip = 1),
 * independent of any label filter or sort order applied to the entries shown on screen. */
internal fun tripNumbersBySession(entries: List<DetectionDebugEntry>): Map<String?, Int> =
    entries.groupBy { it.pothole.sessionId }
        .mapValues { (_, sessionEntries) -> sessionEntries.minOf { it.pothole.timestamp } }
        .toList()
        .sortedBy { (_, earliestTimestamp) -> earliestTimestamp }
        .mapIndexed { index, (sessionId, _) -> sessionId to index + 1 }
        .toMap()

/** One tracking run ("viagem"): every detection sharing a PotholeDetector session id, newest
 * trip first — lets the debug list answer "which trip was this buraco from?" at a glance. */
private data class DebugSessionGroup(
    val sessionId: String?,
    val tripNumber: Int,
    val entries: List<DetectionDebugEntry>,
)

@Composable
private fun SessionHeader(group: DebugSessionGroup) {
    val dateFormat = remember { SimpleDateFormat("dd/MM HH:mm", Locale.forLanguageTag("pt-BR")) }
    val startedAt = remember(group) { group.entries.minOf { it.pothole.timestamp } }

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text =
                stringResource(
                    R.string.debug_session_header_format,
                    group.tripNumber,
                    dateFormat.format(Date(startedAt)),
                ),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.debug_session_count_format, group.entries.size),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DebugListItem(
    entry: DetectionDebugEntry,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val mapsApiKey = remember(context) { getMapsApiKey(context) }
    val dateFormat = remember { SimpleDateFormat("dd/MM HH:mm:ss", Locale.forLanguageTag("pt-BR")) }

    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (mapsApiKey != null) {
                AsyncImage(
                    model =
                        staticMapUrl(
                            mapsApiKey,
                            entry.pothole.location.latitude,
                            entry.pothole.location.longitude,
                            zoom = 16,
                        ),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)),
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    dateFormat.format(Date(entry.pothole.timestamp)),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(
                        R.string.debug_severity_peak_format,
                        entry.pothole.severity,
                        entry.peakAbsZ?.let {
                            stringResource(R.string.tracker_sensor_value_format, it)
                        } ?: stringResource(R.string.common_placeholder_dash),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    entry.sensorWindow?.let {
                        stringResource(R.string.debug_samples_count_format, it.samples.size)
                    } ?: stringResource(R.string.debug_awaiting_capture),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LabelChip(label = entry.label)
        }
    }
}

@Composable
private fun LabelChip(label: DetectionLabel) {
    val color = labelColor(label)
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(50))
                .background(color.copy(alpha = 0.18f))
                .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            label.displayName,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun labelColor(label: DetectionLabel): Color =
    when (label) {
        DetectionLabel.UNLABELED -> MaterialTheme.colorScheme.onSurfaceVariant
        DetectionLabel.POTHOLE -> MaterialTheme.colorScheme.error
        DetectionLabel.FALSE_POSITIVE, DetectionLabel.PHONE_HANDLING -> MaterialTheme.colorScheme.tertiary
        DetectionLabel.SPEED_BUMP, DetectionLabel.ROUGH_ROAD,
        DetectionLabel.JOINT_OR_MANHOLE, DetectionLabel.BRAKING_OR_MANEUVER,
        -> MaterialTheme.colorScheme.primary
    }
