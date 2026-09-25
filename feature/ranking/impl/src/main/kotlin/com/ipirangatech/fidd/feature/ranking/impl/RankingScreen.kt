package com.ipirangatech.fidd.feature.ranking.impl

import android.Manifest
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Leaderboard
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.ipirangatech.fidd.core.model.CityRanking
import com.ipirangatech.fidd.core.model.CityRankingSortBy
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun RankingScreen(
    viewModel: RankingViewModel,
    modifier: Modifier = Modifier,
    // Banner de anúncio montado pelo app (null = sem anúncio: comprou "Remover anúncios" ou a
    // detecção está ativa). A feature só reserva o lugar; não depende do SDK de anúncios.
    adBanner: (@Composable () -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val locationPermissionState = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)
    var pendingMyCityRequest by remember { mutableStateOf(false) }

    LaunchedEffect(locationPermissionState.status.isGranted) {
        if (pendingMyCityRequest && locationPermissionState.status.isGranted) {
            pendingMyCityRequest = false
            viewModel.findMyCity()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.uiEffect.collect { effect ->
            when (effect) {
                is RankingUiEffect.ShowMessage ->
                    Toast.makeText(
                        context,
                        context.getString(effect.messageRes),
                        Toast.LENGTH_SHORT,
                    ).show()
            }
        }
    }

    val hasContent = uiState.top.isNotEmpty()
    val metricLabel = uiState.sortBy.metricLabel()
    val topTitle = stringResource(R.string.ranking_top_header_format, metricLabel)
    val bottomTitle = stringResource(R.string.ranking_bottom_header_format, metricLabel)
    val showMyCityCard = !uiState.loadFailed
    val adItems = if (adBanner != null && hasContent) 1 else 0

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.ranking_title), fontWeight = FontWeight.Bold)
                        if (uiState.totalCities > 0) {
                            Text(
                                pluralStringResource(
                                    R.plurals.ranking_subtitle_format,
                                    uiState.totalCities,
                                    uiState.totalCities,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        // Capped width so rows and bars stay readable on tablets/landscape instead of stretching.
        Column(
            modifier =
                Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .wrapContentWidth()
                    .widthIn(max = 640.dp),
        ) {
            SortSelector(
                selected = uiState.sortBy,
                onSelected = viewModel::onSortByChanged,
            )

            PullToRefreshBox(
                // The initial load shows its own centered spinner; the pull indicator is only for
                // refreshing a ranking that's already on screen.
                isRefreshing = uiState.isLoading && hasContent,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                ) {
                    if (showMyCityCard) {
                        item(key = "my-city") {
                            MyCityCard(
                                myCity = uiState.myCity,
                                totalCities = uiState.totalCities,
                                metric = uiState.sortBy,
                                isLocating = uiState.isLocatingMyCity,
                                canScrollToRow =
                                    uiState.myCity?.let { city ->
                                        (uiState.top + uiState.bottom).any { it.ibgeCode == city.ibgeCode }
                                    } ?: false,
                                onFindMyCity = {
                                    if (locationPermissionState.status.isGranted) {
                                        viewModel.findMyCity()
                                    } else {
                                        pendingMyCityRequest = true
                                        locationPermissionState.launchPermissionRequest()
                                    }
                                },
                                onScrollToRow = {
                                    val city = uiState.myCity ?: return@MyCityCard
                                    // Mirrors the item order below: [my-city], top header, top rows,
                                    // [ad], bottom header, bottom rows.
                                    val topIndex = uiState.top.indexOfFirst { it.ibgeCode == city.ibgeCode }
                                    val bottomIndex = uiState.bottom.indexOfFirst { it.ibgeCode == city.ibgeCode }
                                    val targetIndex =
                                        when {
                                            topIndex >= 0 -> 2 + topIndex
                                            bottomIndex >= 0 -> 2 + uiState.top.size + adItems + 1 + bottomIndex
                                            else -> null
                                        }
                                    if (targetIndex != null) {
                                        scope.launch { listState.animateScrollToItem(targetIndex) }
                                    }
                                },
                            )
                        }
                    }

                    when {
                        hasContent -> {
                            rankingSection(
                                key = "top",
                                title = topTitle,
                                icon = Icons.AutoMirrored.Rounded.TrendingUp,
                                isGoodSection = uiState.sortBy.isPositive,
                                cities = uiState.top,
                                metric = uiState.sortBy,
                                myCityCode = uiState.myCity?.ibgeCode,
                            )
                            // Entre o topo e o fim do ranking: uma pausa natural na leitura.
                            if (adBanner != null) {
                                item(key = "ad") {
                                    Box(Modifier.padding(vertical = 8.dp)) { adBanner() }
                                }
                            }
                            if (uiState.bottom.isNotEmpty()) {
                                rankingSection(
                                    key = "bottom",
                                    title = bottomTitle,
                                    icon = Icons.AutoMirrored.Rounded.TrendingDown,
                                    isGoodSection = !uiState.sortBy.isPositive,
                                    cities = uiState.bottom,
                                    metric = uiState.sortBy,
                                    myCityCode = uiState.myCity?.ibgeCode,
                                )
                            }
                        }

                        uiState.isLoading ->
                            item(key = "loading") {
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 48.dp),
                                    contentAlignment = Alignment.Center,
                                ) { CircularProgressIndicator() }
                            }

                        uiState.loadFailed ->
                            item(key = "error") {
                                MessageState(
                                    icon = Icons.Rounded.CloudOff,
                                    title = stringResource(R.string.ranking_error_title),
                                    body = stringResource(R.string.ranking_error_body),
                                    action = {
                                        Button(onClick = { viewModel.refresh() }) {
                                            Text(stringResource(R.string.ranking_retry_button))
                                        }
                                    },
                                )
                            }

                        else ->
                            item(key = "empty") {
                                MessageState(
                                    icon = Icons.Rounded.Leaderboard,
                                    title = stringResource(R.string.ranking_empty_title),
                                    body = stringResource(R.string.ranking_empty),
                                )
                            }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortSelector(
    selected: CityRankingSortBy,
    onSelected: (CityRankingSortBy) -> Unit,
) {
    val options = CityRankingSortBy.entries
    SingleChoiceSegmentedButtonRow(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        options.forEachIndexed { index, sortBy ->
            SegmentedButton(
                selected = sortBy == selected,
                onClick = { onSelected(sortBy) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                // No checkmark: "Reincidências" needs the full segment width on narrow phones.
                icon = {},
                // The theme doesn't define secondaryContainer, so the default would be M3 lavender.
                colors =
                    SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
            ) {
                Text(sortBy.chipLabel(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun MyCityCard(
    myCity: CityRanking?,
    totalCities: Int,
    metric: CityRankingSortBy,
    isLocating: Boolean,
    canScrollToRow: Boolean,
    onFindMyCity: () -> Unit,
    onScrollToRow: () -> Unit,
) {
    val onContainer = MaterialTheme.colorScheme.onPrimaryContainer
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .animateContentSize(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = onContainer,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(
                    stringResource(R.string.ranking_my_city_title).uppercase(),
                    modifier =
                        Modifier
                            .padding(start = 6.dp)
                            .weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (myCity != null && !isLocating) {
                    IconButton(onClick = onFindMyCity, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.ranking_refresh_button),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            when {
                isLocating ->
                    Row(
                        modifier = Modifier.padding(top = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = onContainer,
                        )
                        Text(stringResource(R.string.ranking_locating), modifier = Modifier.padding(start = 12.dp))
                    }

                myCity == null -> {
                    Text(
                        stringResource(R.string.ranking_my_city_prompt),
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Button(onClick = onFindMyCity, modifier = Modifier.padding(top = 12.dp)) {
                        Icon(Icons.Rounded.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.ranking_view_my_city_button))
                    }
                }

                else -> {
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                myCity.name,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                myCity.state,
                                style = MaterialTheme.typography.bodyMedium,
                                color = onContainer.copy(alpha = 0.75f),
                            )
                        }
                        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 12.dp)) {
                            Text(
                                myCity.rank?.let { stringResource(R.string.ranking_ordinal_format, it) }
                                    ?: stringResource(R.string.ranking_rank_placeholder),
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Black,
                            )
                            Text(
                                if (myCity.rank != null) {
                                    pluralStringResource(R.plurals.ranking_of_total_format, totalCities, totalCities)
                                } else {
                                    stringResource(R.string.ranking_unranked)
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = onContainer.copy(alpha = 0.75f),
                            )
                        }
                    }

                    if (myCity.rank == null) {
                        Text(
                            stringResource(R.string.ranking_no_potholes_metric),
                            modifier = Modifier.padding(top = 8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = onContainer.copy(alpha = 0.75f),
                        )
                    }

                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp)
                                .clip(RoundedCornerShape(16.dp))
                                // Neutral inset so the green/red stat values stay legible on the orange card.
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(vertical = 12.dp),
                    ) {
                        CityRankingSortBy.entries.forEach { stat ->
                            StatItem(
                                value = myCity.valueFor(stat),
                                label = stat.chipLabel(),
                                tone = stat.toneColor(),
                                emphasized = stat == metric,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    if (canScrollToRow) {
                        TextButton(
                            onClick = onScrollToRow,
                            modifier = Modifier.padding(top = 4.dp),
                        ) { Text(stringResource(R.string.ranking_view_in_list_button), color = onContainer) }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatItem(
    value: Long,
    label: String,
    tone: Color,
    emphasized: Boolean,
    modifier: Modifier = Modifier,
) {
    val labelColor = MaterialTheme.colorScheme.onSurface
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value.toString(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = if (emphasized) FontWeight.Black else FontWeight.SemiBold,
            color = if (emphasized) tone else tone.copy(alpha = 0.7f),
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (emphasized) FontWeight.Bold else null,
            color = if (emphasized) labelColor else labelColor.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MessageState(
    icon: ImageVector,
    title: String,
    body: String,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp),
            )
        }
        Text(
            title,
            modifier = Modifier.padding(top = 16.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            body,
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}
