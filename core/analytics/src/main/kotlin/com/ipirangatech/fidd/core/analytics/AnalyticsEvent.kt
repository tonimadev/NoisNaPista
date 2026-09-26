package com.ipirangatech.fidd.core.analytics

import com.ipirangatech.fidd.core.model.CityRankingSortBy

/**
 * Tudo o que o app manda para o Analytics. Fechado de propósito: só comportamento dentro do app
 * (tela aberta, botão usado, resultado de uma ação), nunca dado pessoal — sem coordenada, sem id
 * de buraco, sem o token de reporter (ReporterIdentityProvider), sem texto digitado. Parâmetros são
 * enums ou números arredondados, para não virarem identificador por acidente.
 */
sealed class AnalyticsEvent(
    val name: String,
    val params: Map<String, Any> = emptyMap(),
) {
    data class ScreenView(val screen: Screen) : AnalyticsEvent(SCREEN_VIEW, mapOf(PARAM_SCREEN_NAME to screen.value))

    data class OnboardingFinished(val skipped: Boolean) :
        AnalyticsEvent("onboarding_finished", mapOf("skipped" to skipped.toParam()))

    data class LocationPermissionAnswered(val outcome: PermissionOutcome) :
        AnalyticsEvent("location_permission_answered", mapOf("outcome" to outcome.value))

    /** Tocou em "Iniciar" sem a permissão de localização precisa. */
    data object DetectionBlockedByPermission : AnalyticsEvent("detection_blocked_permission")

    data object DetectionStarted : AnalyticsEvent("detection_started")

    data class DetectionStopped(val source: StopSource, val durationMinutes: Long) :
        AnalyticsEvent(
            "detection_stopped",
            mapOf("source" to source.value, "duration_min" to durationMinutes),
        )

    data object PotholeDetected : AnalyticsEvent("pothole_detected")

    data object FalseAlarmMarked : AnalyticsEvent("false_alarm_marked")

    data object PotholeDeleted : AnalyticsEvent("pothole_deleted")

    data class FixVoteCast(val result: VoteResult) : AnalyticsEvent("fix_vote_cast", mapOf("result" to result.value))

    data class CommunityRefreshed(val screen: Screen) :
        AnalyticsEvent("community_refreshed", mapOf(PARAM_SCREEN_NAME to screen.value))

    data object MapRecentered : AnalyticsEvent("map_recentered")

    data class RankingSortChanged(val sortBy: CityRankingSortBy) :
        AnalyticsEvent("ranking_sort_changed", mapOf("sort_by" to sortBy.name.lowercase()))

    data object RankingMyCityRequested : AnalyticsEvent("ranking_my_city_requested")

    data object RemoveAdsClicked : AnalyticsEvent("remove_ads_clicked")

    data object RemoveAdsPurchased : AnalyticsEvent("remove_ads_purchased")

    data object RemoveAdsFailed : AnalyticsEvent("remove_ads_failed")

    enum class Screen(val value: String) {
        ONBOARDING("onboarding"),
        HOME("home"),
        MAP("map"),
        HISTORY("history"),
        RANKING("ranking"),
    }

    enum class PermissionOutcome(val value: String) {
        PRECISE("precise"),
        APPROXIMATE_ONLY("approximate_only"),
        DENIED("denied"),
        BLOCKED("blocked"),
    }

    enum class StopSource(val value: String) {
        APP("app"),
        NOTIFICATION("notification"),
    }

    enum class VoteResult(val value: String) {
        REGISTERED("registered"),
        MARKED_FIXED("marked_fixed"),
        FAILED("failed"),
    }

    companion object {
        // Mesmos nomes do evento/parâmetro padrão do Firebase ("screen_view"/"screen_name"): assim
        // as telas entram nos relatórios de "Páginas e telas" do console.
        const val SCREEN_VIEW = "screen_view"
        const val PARAM_SCREEN_NAME = "screen_name"

        // O Analytics não tem tipo booleano; 0/1 permite somar e filtrar no console.
        private fun Boolean.toParam(): Long = if (this) 1L else 0L
    }
}
