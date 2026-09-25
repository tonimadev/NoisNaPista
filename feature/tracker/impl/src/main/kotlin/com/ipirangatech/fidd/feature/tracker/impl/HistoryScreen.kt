package com.ipirangatech.fidd.feature.tracker.impl

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Report
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ipirangatech.fidd.core.model.Pothole
import com.ipirangatech.fidd.core.ui.PotholeMarker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    modifier: Modifier = Modifier,
    // Banner de anúncio montado pelo app (null = sem anúncio: comprou "Remover anúncios" ou a
    // detecção está ativa). A feature só reserva o lugar; não depende do SDK de anúncios.
    adBanner: (@Composable () -> Unit)? = null
) {
    val potholes by viewModel.potholes.collectAsStateWithLifecycle()
    val communityPotholes by viewModel.communityPotholes.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Read once per screen (parses the manifest) instead of once per list item.
    val mapsApiKey = remember(context) { getMapsApiKey(context) }

    LaunchedEffect(Unit) {
        viewModel.uiEffect.collect { effect ->
            when (effect) {
                is HistoryUiEffect.ShowMessage -> Toast.makeText(context, context.getString(effect.messageRes), Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title), fontWeight = FontWeight.Bold) }
            )
        }
    ) { innerPadding ->
        // The "Atualizar" retry action must stay reachable even when the community list is
        // empty because the last fetch failed — nesting it inside an isNotEmpty() check would
        // strand the user with no way to retry (this was a real bug: the button only appeared
        // once there was already data, exactly when it was least needed).
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (potholes.isEmpty()) {
                item(key = "local-empty") {
                    Text(
                        stringResource(R.string.common_no_pothole_detected),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(potholes, key = { "local-${it.id}" }) { pothole ->
                    HistoryItem(
                        pothole = pothole,
                        mapsApiKey = mapsApiKey,
                        onMarkFalseAlarm = { viewModel.onIntent(HistoryUiIntent.MarkFalseAlarm(pothole)) },
                        onDelete = { viewModel.onIntent(HistoryUiIntent.Delete(pothole)) }
                    )
                }
            }

            // Entre as duas listas: separa "meus" de "comunidade" sem ficar colado nos botões de
            // cada cartão (alarme falso/apagar/votar).
            if (adBanner != null) {
                item(key = "ad") { adBanner() }
            }

            item(key = "community-header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.history_community_header), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { viewModel.onIntent(HistoryUiIntent.RefreshCommunity) }) {
                        Text(stringResource(R.string.common_refresh))
                    }
                }
            }
            if (communityPotholes.isEmpty()) {
                item(key = "community-empty") {
                    Text(
                        stringResource(R.string.history_community_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(communityPotholes, key = { "community-${it.serverId}" }) { pothole ->
                    CommunityHistoryItem(
                        pothole = pothole,
                        onVoteFixed = { viewModel.onIntent(HistoryUiIntent.VoteFixed(pothole)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryItem(
    pothole: Pothole,
    mapsApiKey: String?,
    onMarkFalseAlarm: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("pt-BR")) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (pothole.isFalseAlarm) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Warning,
                    contentDescription = null,
                    tint = if (pothole.isFalseAlarm) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
                Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
                    Text(
                        text = stringResource(if (pothole.isFalseAlarm) R.string.history_false_alarm_label else R.string.history_pothole_detected_label),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        textDecoration = if (pothole.isFalseAlarm) TextDecoration.LineThrough else null
                    )
                    Text(
                        text = dateFormat.format(Date(pothole.timestamp)),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = stringResource(R.string.tracker_lat_lon_format, pothole.location.latitude, pothole.location.longitude),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = stringResource(if (pothole.serverId != null) R.string.history_synced else R.string.history_pending_sync),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!pothole.isFalseAlarm) {
                    IconButton(onClick = onMarkFalseAlarm) {
                        Icon(
                            imageVector = Icons.Rounded.Report,
                            contentDescription = stringResource(R.string.history_mark_false_alarm_cd),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = stringResource(R.string.history_delete_cd),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            // A visual anchor to help recall "what was at this spot" when reviewing/labeling a
            // week's worth of detections later — tap to open the exact point in Google Maps.
            if (mapsApiKey != null) {
                val noMapsAppFoundMessage = stringResource(R.string.common_no_maps_app_found)
                // O ponto do buraco é sempre o centro da imagem (o Crop corta por igual dos dois lados),
                // então a cratera é sobreposta ali em vez de vir desenhada pelo Static Maps.
                Box(contentAlignment = Alignment.Center) {
                    AsyncImage(
                        model = staticMapUrl(mapsApiKey, pothole.location.latitude, pothole.location.longitude, withMarker = false),
                        contentDescription = stringResource(R.string.history_map_thumbnail_cd),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                            .clickable {
                                val uri = Uri.parse(
                                    "geo:${pothole.location.latitude},${pothole.location.longitude}" +
                                        "?q=${pothole.location.latitude},${pothole.location.longitude}"
                                )
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                } catch (e: ActivityNotFoundException) {
                                    Toast.makeText(context, noMapsAppFoundMessage, Toast.LENGTH_SHORT).show()
                                }
                            }
                    )
                    val craterColor = PotholeMarker.severityColor(pothole.severity)
                    val crater = remember(craterColor) { PotholeMarker.bitmap(context, craterColor).asImageBitmap() }
                    Image(bitmap = crater, contentDescription = null)
                }
            }
        }
    }
}

@Composable
private fun CommunityHistoryItem(
    pothole: Pothole,
    onVoteFixed: () -> Unit
) {
    val isFixed = pothole.status == "FIXED"

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary
            )
            Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
                Text(
                    text = stringResource(R.string.history_community_status_format, pothole.status ?: stringResource(R.string.history_status_pending_fallback)),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.history_report_count_format, pothole.distinctReporterCount),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = stringResource(R.string.tracker_lat_lon_format, pothole.location.latitude, pothole.location.longitude),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (!isFixed) {
                IconButton(onClick = onVoteFixed) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = stringResource(R.string.history_vote_fixed_cd),
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }
    }
}
