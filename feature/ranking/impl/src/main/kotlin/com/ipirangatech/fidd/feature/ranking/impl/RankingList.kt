package com.ipirangatech.fidd.feature.ranking.impl

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ipirangatech.fidd.core.model.CityRanking
import com.ipirangatech.fidd.core.model.CityRankingSortBy

/** A section header followed by its rows, drawn as one grouped list: rows sit 2dp apart and
 * only the group's outer corners are fully rounded. */
internal fun LazyListScope.rankingSection(
    key: String,
    title: String,
    icon: ImageVector,
    isGoodSection: Boolean,
    cities: List<CityRanking>,
    metric: CityRankingSortBy,
    myCityCode: Int?,
) {
    // Bars are relative to the section's largest value, so each section reads on its own scale.
    val maxValue = cities.maxOfOrNull { it.valueFor(metric) } ?: 0L
    item(key = "$key-header") {
        SectionHeader(title = title, icon = icon, isGoodSection = isGoodSection)
    }
    itemsIndexed(cities, key = { _, city -> "$key-${city.ibgeCode}" }) { index, city ->
        CityRankingRow(
            city = city,
            metric = metric,
            fraction = if (maxValue > 0) city.valueFor(metric).toFloat() / maxValue else 0f,
            isMyCity = city.ibgeCode == myCityCode,
            shape = groupedShape(index, cities.size),
            modifier = Modifier.padding(bottom = 2.dp),
        )
    }
}

private fun groupedShape(
    index: Int,
    count: Int,
): RoundedCornerShape {
    val outer = 20.dp
    val inner = 6.dp
    return RoundedCornerShape(
        topStart = if (index == 0) outer else inner,
        topEnd = if (index == 0) outer else inner,
        bottomStart = if (index == count - 1) outer else inner,
        bottomEnd = if (index == count - 1) outer else inner,
    )
}

@Composable
private fun SectionHeader(
    title: String,
    icon: ImageVector,
    isGoodSection: Boolean,
) {
    // Tone reflects whether being in this list is good news ("Mais corrigidos", "Menos buracos")
    // or bad news ("Mais buracos", "Menos corrigidos").
    val tone = rankingTone(positive = isGoodSection)
    Row(
        modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(tone.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tone, modifier = Modifier.size(18.dp))
        }
        Text(
            title,
            modifier = Modifier.padding(start = 8.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun CityRankingRow(
    city: CityRanking,
    metric: CityRankingSortBy,
    fraction: Float,
    isMyCity: Boolean,
    shape: RoundedCornerShape,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val tone = metric.toneColor()
    val animatedFraction by animateFloatAsState(fraction, label = "rankBar")
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = colors.surfaceVariant,
        border = if (isMyCity) BorderStroke(1.5.dp, colors.primary) else null,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RankBadge(rank = city.rank, highlighted = isMyCity)

            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
            ) {
                Text(
                    city.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (isMyCity) "${city.state} · ${stringResource(R.string.ranking_your_city_tag)}" else city.state,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isMyCity) colors.primary else colors.onSurfaceVariant.copy(alpha = 0.8f),
                )
                Box(
                    modifier =
                        Modifier
                            .padding(top = 8.dp)
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(tone.copy(alpha = 0.15f)),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth(animatedFraction)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(tone),
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    city.valueFor(metric).toString(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = tone,
                )
                Text(
                    metric.metricLabel(),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@Composable
private fun RankBadge(
    rank: Int?,
    highlighted: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier =
            Modifier
                .defaultMinSize(minWidth = 40.dp, minHeight = 40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (highlighted) colors.primary else colors.onSurfaceVariant.copy(alpha = 0.1f))
                .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            rank?.let {
                stringResource(
                    R.string.ranking_ordinal_format,
                    it,
                )
            } ?: stringResource(R.string.ranking_rank_placeholder),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (highlighted) colors.onPrimary else colors.onSurface,
        )
    }
}

@Composable
internal fun CityRankingSortBy.toneColor(): Color = rankingTone(positive = isPositive)

/** Green (tertiary) for good news, red (error) for bad news. The brand green/red are tuned for the
 * dark theme, so they're darkened on light backgrounds to keep text contrast readable. */
@Composable
private fun rankingTone(positive: Boolean): Color {
    val colors = MaterialTheme.colorScheme
    val base = if (positive) colors.tertiary else colors.error
    return if (colors.background.luminance() > 0.5f) lerp(base, Color.Black, 0.3f) else base
}

internal fun CityRanking.valueFor(metric: CityRankingSortBy): Long =
    when (metric) {
        CityRankingSortBy.POTHOLES -> totalPotholes
        CityRankingSortBy.FIXED -> fixedPotholes
        CityRankingSortBy.RECURRENCE -> recurrenceCount
    }

@Composable
internal fun CityRankingSortBy.chipLabel(): String =
    when (this) {
        CityRankingSortBy.POTHOLES -> stringResource(R.string.ranking_metric_potholes)
        CityRankingSortBy.FIXED -> stringResource(R.string.ranking_metric_fixed)
        CityRankingSortBy.RECURRENCE -> stringResource(R.string.ranking_metric_recurrence)
    }

@Composable
internal fun CityRankingSortBy.metricLabel(): String =
    when (this) {
        CityRankingSortBy.POTHOLES -> stringResource(R.string.ranking_metric_potholes_lower)
        CityRankingSortBy.FIXED -> stringResource(R.string.ranking_metric_fixed_lower)
        CityRankingSortBy.RECURRENCE -> stringResource(R.string.ranking_metric_recurrence_lower)
    }

/** Fixed potholes are good news; reported potholes and recurrences are bad news. */
internal val CityRankingSortBy.isPositive: Boolean
    get() = this == CityRankingSortBy.FIXED
