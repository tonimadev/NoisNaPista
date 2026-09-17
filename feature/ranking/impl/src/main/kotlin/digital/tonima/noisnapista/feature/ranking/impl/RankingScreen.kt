package digital.tonima.noisnapista.feature.ranking.impl

import android.Manifest
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import digital.tonima.noisnapista.core.model.CityRanking
import digital.tonima.noisnapista.core.model.CityRankingSortBy
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun RankingScreen(
    viewModel: RankingViewModel,
    modifier: Modifier = Modifier
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
                is RankingUiEffect.ShowMessage -> Toast.makeText(context, context.getString(effect.messageRes), Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.ranking_title), fontWeight = FontWeight.Bold) })
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            SortSelector(
                selected = uiState.sortBy,
                onSelected = viewModel::onSortByChanged
            )

            MyCityCard(
                myCity = uiState.myCity,
                totalCities = uiState.totalCities,
                isLocating = uiState.isLocatingMyCity,
                canScrollToRow = uiState.myCity?.let { city ->
                    uiState.top.any { it.ibgeCode == city.ibgeCode } || uiState.bottom.any { it.ibgeCode == city.ibgeCode }
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
                    val topIndex = uiState.top.indexOfFirst { it.ibgeCode == city.ibgeCode }
                    val bottomIndex = uiState.bottom.indexOfFirst { it.ibgeCode == city.ibgeCode }
                    val targetIndex = when {
                        topIndex >= 0 -> 1 + topIndex
                        bottomIndex >= 0 -> 1 + uiState.top.size + 1 + bottomIndex
                        else -> null
                    }
                    if (targetIndex != null) {
                        scope.launch { listState.animateScrollToItem(targetIndex) }
                    }
                }
            )

            val metricLabel = uiState.sortBy.metricLabel()
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(key = "top-header") {
                    SectionLabel(stringResource(R.string.ranking_top_header_format, metricLabel))
                }
                items(uiState.top, key = { "top-${it.ibgeCode}" }) { city ->
                    CityRankingRow(city = city, metric = uiState.sortBy, isMyCity = city.ibgeCode == uiState.myCity?.ibgeCode)
                }

                if (uiState.bottom.isNotEmpty()) {
                    item(key = "bottom-header") {
                        SectionLabel(stringResource(R.string.ranking_bottom_header_format, metricLabel))
                    }
                    items(uiState.bottom, key = { "bottom-${it.ibgeCode}" }) { city ->
                        CityRankingRow(city = city, metric = uiState.sortBy, isMyCity = city.ibgeCode == uiState.myCity?.ibgeCode)
                    }
                }

                if (uiState.top.isEmpty() && !uiState.isLoading) {
                    item(key = "empty") {
                        Text(
                            stringResource(R.string.ranking_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SortSelector(selected: CityRankingSortBy, onSelected: (CityRankingSortBy) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CityRankingSortBy.entries.forEach { sortBy ->
            FilterChip(
                selected = sortBy == selected,
                onClick = { onSelected(sortBy) },
                label = { Text(sortBy.chipLabel()) }
            )
        }
    }
}

@Composable
private fun MyCityCard(
    myCity: CityRanking?,
    totalCities: Int,
    isLocating: Boolean,
    canScrollToRow: Boolean,
    onFindMyCity: () -> Unit,
    onScrollToRow: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.MyLocation,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    stringResource(R.string.ranking_my_city_title),
                    modifier = Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            when {
                isLocating -> Row(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.ranking_locating), color = MaterialTheme.colorScheme.onPrimaryContainer)
                }

                myCity == null -> TextButton(onClick = onFindMyCity) {
                    Text(stringResource(R.string.ranking_view_my_city_button))
                }

                else -> {
                    Text(
                        stringResource(R.string.ranking_city_name_state_format, myCity.name, myCity.state),
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = myCity.rank?.let { stringResource(R.string.ranking_my_city_rank_format, it, totalCities) }
                            ?: stringResource(R.string.ranking_no_potholes_metric),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = myCity.summaryLine(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Row(modifier = Modifier.padding(top = 4.dp)) {
                        if (canScrollToRow) {
                            TextButton(onClick = onScrollToRow) { Text(stringResource(R.string.ranking_view_in_list_button)) }
                        }
                        TextButton(onClick = onFindMyCity) { Text(stringResource(R.string.ranking_refresh_button)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun CityRankingRow(city: CityRanking, metric: CityRankingSortBy, isMyCity: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isMyCity) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = city.rank?.let { stringResource(R.string.ranking_rank_format, it) } ?: stringResource(R.string.ranking_rank_placeholder),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(end = 12.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.ranking_city_name_state_format, city.name, city.state), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text(city.summaryLine(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                text = city.highlightedValue(metric).toString(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun CityRanking.summaryLine(): String =
    stringResource(R.string.ranking_summary_format, totalPotholes, fixedPotholes, recurrenceCount)

private fun CityRanking.highlightedValue(metric: CityRankingSortBy): Long = when (metric) {
    CityRankingSortBy.POTHOLES -> totalPotholes
    CityRankingSortBy.FIXED -> fixedPotholes
    CityRankingSortBy.RECURRENCE -> recurrenceCount
}

@Composable
private fun CityRankingSortBy.chipLabel(): String = when (this) {
    CityRankingSortBy.POTHOLES -> stringResource(R.string.ranking_metric_potholes)
    CityRankingSortBy.FIXED -> stringResource(R.string.ranking_metric_fixed)
    CityRankingSortBy.RECURRENCE -> stringResource(R.string.ranking_metric_recurrence)
}

@Composable
private fun CityRankingSortBy.metricLabel(): String = when (this) {
    CityRankingSortBy.POTHOLES -> stringResource(R.string.ranking_metric_potholes_lower)
    CityRankingSortBy.FIXED -> stringResource(R.string.ranking_metric_fixed_lower)
    CityRankingSortBy.RECURRENCE -> stringResource(R.string.ranking_metric_recurrence_lower)
}
