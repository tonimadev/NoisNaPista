package digital.tonima.noisnapista.feature.tracker.impl

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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import digital.tonima.noisnapista.core.model.DetectionDebugEntry
import digital.tonima.noisnapista.core.model.DetectionLabel
import digital.tonima.noisnapista.core.model.SensorWindowSample
import kotlinx.coroutines.delay
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
    modifier: Modifier = Modifier
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf<DetectionLabel?>(null) }
    val visibleEntries = remember(entries, filter) {
        filter?.let { f -> entries.filter { it.label == f } } ?: entries
    }
    val countsByLabel = remember(entries) { entries.groupingBy { it.label }.eachCount() }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Debug: Classificar Detecções", fontWeight = FontWeight.Bold) }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Text(
                text = "${entries.size} detecção(ões) capturada(s) • ${countsByLabel[DetectionLabel.UNLABELED] ?: 0} não classificada(s)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = filter == null,
                    onClick = { filter = null },
                    label = { Text("Todos (${entries.size})") }
                )
                DetectionLabel.entries.forEach { label ->
                    FilterChip(
                        selected = filter == label,
                        onClick = { filter = if (filter == label) null else label },
                        label = { Text("${label.displayName} (${countsByLabel[label] ?: 0})") }
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(top = 12.dp))

            if (visibleEntries.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Nenhuma detecção nesta categoria.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(visibleEntries, key = { it.pothole.id }) { entry ->
                        DebugListItem(entry = entry, onClick = { onOpenDetail(entry.pothole.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun DebugListItem(entry: DetectionDebugEntry, onClick: () -> Unit) {
    val context = LocalContext.current
    val mapsApiKey = remember(context) { getMapsApiKey(context) }
    val dateFormat = remember { SimpleDateFormat("dd/MM HH:mm:ss", Locale.forLanguageTag("pt-BR")) }

    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (mapsApiKey != null) {
                AsyncImage(
                    model = staticMapUrl(mapsApiKey, entry.pothole.location.latitude, entry.pothole.location.longitude, zoom = 16),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp))
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(dateFormat.format(Date(entry.pothole.timestamp)), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Severidade: %.1f m/s²  •  Pico: %s".format(
                        entry.pothole.severity,
                        entry.peakAbsZ?.let { "%.1f m/s²".format(it) } ?: "—"
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    entry.sensorWindow?.let { "${it.samples.size} amostras" } ?: "Aguardando captura...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.18f))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(label.displayName, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun labelColor(label: DetectionLabel): Color = when (label) {
    DetectionLabel.UNLABELED -> MaterialTheme.colorScheme.onSurfaceVariant
    DetectionLabel.POTHOLE -> MaterialTheme.colorScheme.error
    DetectionLabel.FALSE_POSITIVE, DetectionLabel.PHONE_HANDLING -> MaterialTheme.colorScheme.tertiary
    DetectionLabel.SPEED_BUMP, DetectionLabel.ROUGH_ROAD,
    DetectionLabel.JOINT_OR_MANHOLE, DetectionLabel.BRAKING_OR_MANEUVER -> MaterialTheme.colorScheme.primary
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugDetailScreen(
    viewModel: DebugViewModel,
    potholeId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val entry = entries.find { it.pothole.id == potholeId }
    val context = LocalContext.current
    val mapsApiKey = remember(context) { getMapsApiKey(context) }
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.forLanguageTag("pt-BR")) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Classificar Detecção", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (entry == null) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text("Detecção não encontrada (pode ter sido deletada).", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }

        var noteText by remember(entry.pothole.id) { mutableStateOf(entry.note) }
        LaunchedEffect(entry.pothole.id, noteText) {
            delay(500)
            if (noteText != entry.note) viewModel.updateNote(entry.pothole.id, noteText)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    if (mapsApiKey != null) {
                        AsyncImage(
                            model = staticMapUrl(mapsApiKey, entry.pothole.location.latitude, entry.pothole.location.longitude),
                            contentDescription = "Localização no mapa",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    val uri = Uri.parse(
                                        "geo:${entry.pothole.location.latitude},${entry.pothole.location.longitude}" +
                                            "?q=${entry.pothole.location.latitude},${entry.pothole.location.longitude}"
                                    )
                                    try {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                    } catch (e: ActivityNotFoundException) {
                                        Toast.makeText(context, "Nenhum app de mapas encontrado", Toast.LENGTH_SHORT).show()
                                    }
                                }
                        )
                    }
                }

                item { MetadataCard(entry = entry, dateText = dateFormat.format(Date(entry.pothole.timestamp))) }

                item {
                    Column {
                        Text(
                            "Janela do sensor (-2s a +1s do impacto)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        SensorWindowChart(samples = entry.sensorWindow?.samples.orEmpty())
                    }
                }

                item {
                    Column {
                        Text(
                            "Classificação",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DetectionLabel.entries.forEach { label ->
                                FilterChip(
                                    selected = entry.label == label,
                                    onClick = { viewModel.updateLabel(entry.pothole.id, label) },
                                    label = { Text(label.displayName) }
                                )
                            }
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        label = { Text("Observações (ex: \"buraco fundo depois da lombada\")") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                }
            }
        }
    }
}

@Composable
private fun MetadataCard(entry: DetectionDebugEntry, dateText: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp)) {
            MetadataRow("Data/hora", dateText)
            MetadataRow("Localização", "%.6f, %.6f".format(entry.pothole.location.latitude, entry.pothole.location.longitude))
            MetadataRow("Precisão GPS", "±%.1f m".format(entry.pothole.location.accuracy))
            MetadataRow("Severidade no gatilho", "%.2f m/s²".format(entry.pothole.severity))
            MetadataRow("Pico na janela", entry.peakAbsZ?.let { "%.2f m/s²".format(it) } ?: "—")
            MetadataRow("Amostras capturadas", entry.sensorWindow?.samples?.size?.toString() ?: "aguardando...")
            MetadataRow("Sincronização", if (entry.pothole.serverId != null) "Sincronizado" else "Aguardando sincronização")
            if (entry.pothole.status != null) {
                MetadataRow("Status no backend", "${entry.pothole.status} (${entry.pothole.distinctReporterCount} relato(s))")
            }
        }
    }
}

@Composable
private fun MetadataRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
    }
}

/** Plots raw x/y/z acceleration against time-since-impact so the user can visually correlate the
 * shape of the reading with what they remember of the road (a sharp single spike vs. a longer
 * rumble, for instance) — the kind of detail a single severity number can't convey. */
@Composable
private fun SensorWindowChart(samples: List<SensorWindowSample>, modifier: Modifier = Modifier) {
    if (samples.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text("Sem dados de sensor capturados", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            ChartLegendDot(xColor, "X")
            Spacer(modifier = Modifier.width(12.dp))
            ChartLegendDot(yColor, "Y")
            Spacer(modifier = Modifier.width(12.dp))
            ChartLegendDot(zColor, "Z (eixo do impacto)")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(1.dp)
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
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                )
            }

            fun drawSeries(color: Color, valueOf: (SensorWindowSample) -> Float) {
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
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("${minOffset.toInt()} ms", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("impacto", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("+${maxOffset.toInt()} ms", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ChartLegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
