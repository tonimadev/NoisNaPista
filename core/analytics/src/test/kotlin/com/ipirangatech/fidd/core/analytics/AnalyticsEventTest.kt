package com.ipirangatech.fidd.core.analytics

import com.ipirangatech.fidd.core.model.CityRankingSortBy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyticsEventTest {
    // Limites do Firebase: nome de evento/parâmetro até 40 caracteres, só letras, dígitos e "_",
    // começando por letra; valores de texto até 100 caracteres. Fora disso o evento é descartado
    // em silêncio.
    private val validName = Regex("^[a-z][a-z0-9_]{0,39}$")

    private val everyEvent: List<AnalyticsEvent> =
        AnalyticsEvent.Screen.entries.flatMap {
            listOf(AnalyticsEvent.ScreenView(it), AnalyticsEvent.CommunityRefreshed(it))
        } +
            AnalyticsEvent.PermissionOutcome.entries.map { AnalyticsEvent.LocationPermissionAnswered(it) } +
            AnalyticsEvent.VoteResult.entries.map { AnalyticsEvent.FixVoteCast(it) } +
            AnalyticsEvent.StopSource.entries.map { AnalyticsEvent.DetectionStopped(it, durationMinutes = 3) } +
            CityRankingSortBy.entries.map { AnalyticsEvent.RankingSortChanged(it) } +
            listOf(
                AnalyticsEvent.OnboardingFinished(skipped = false),
                AnalyticsEvent.DetectionBlockedByPermission,
                AnalyticsEvent.DetectionStarted,
                AnalyticsEvent.PotholeDetected,
                AnalyticsEvent.FalseAlarmMarked,
                AnalyticsEvent.PotholeDeleted,
                AnalyticsEvent.MapRecentered,
                AnalyticsEvent.RankingMyCityRequested,
                AnalyticsEvent.RemoveAdsClicked,
                AnalyticsEvent.RemoveAdsPurchased,
                AnalyticsEvent.RemoveAdsFailed,
            )

    @Test
    fun `every event and parameter name is accepted by Firebase`() {
        everyEvent.forEach { event ->
            assertTrue(event.name, validName.matches(event.name))
            event.params.forEach { (key, value) ->
                assertTrue("${event.name}.$key", validName.matches(key))
                assertTrue("${event.name}.$key", value is Long || (value is String && value.length <= 100))
            }
        }
    }

    @Test
    fun `parameters are the enum values, never free text`() {
        assertEquals(
            mapOf("sort_by" to "recurrence"),
            AnalyticsEvent.RankingSortChanged(CityRankingSortBy.RECURRENCE).params,
        )
        assertEquals(mapOf("skipped" to 0L), AnalyticsEvent.OnboardingFinished(skipped = false).params)
        assertEquals(
            mapOf("outcome" to "approximate_only"),
            AnalyticsEvent.LocationPermissionAnswered(AnalyticsEvent.PermissionOutcome.APPROXIMATE_ONLY).params,
        )
        assertEquals(
            mapOf("result" to "marked_fixed"),
            AnalyticsEvent.FixVoteCast(AnalyticsEvent.VoteResult.MARKED_FIXED).params,
        )
    }
}
