package com.ipirangatech.fidd.feature.ranking.impl

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ipirangatech.fidd.core.data.CityRepository
import com.ipirangatech.fidd.core.data.RankingLocationPreferences
import com.ipirangatech.fidd.core.location.LocationProvider
import com.ipirangatech.fidd.core.model.CityRanking
import com.ipirangatech.fidd.core.model.CityRankingSortBy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RankingUiState(
    val sortBy: CityRankingSortBy = CityRankingSortBy.POTHOLES,
    val totalCities: Int = 0,
    val top: List<CityRanking> = emptyList(),
    val bottom: List<CityRanking> = emptyList(),
    val isLoading: Boolean = false,
    /** The last ranking fetch failed and there's nothing cached to show — drives the retry state. */
    val loadFailed: Boolean = false,
    /** The user's own city, once located — null before the user asks, or if it couldn't be
     * resolved. Its `rank` is specific to [sortBy], re-fetched whenever that changes. */
    val myCity: CityRanking? = null,
    val isLocatingMyCity: Boolean = false,
)

sealed interface RankingUiEffect {
    data class ShowMessage(
        @StringRes val messageRes: Int,
    ) : RankingUiEffect
}

@HiltViewModel
class RankingViewModel
    @Inject
    constructor(
        private val cityRepository: CityRepository,
        private val locationProvider: LocationProvider,
        private val locationPreferences: RankingLocationPreferences,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(RankingUiState())
        val uiState: StateFlow<RankingUiState> = _uiState.asStateFlow()

        private val _uiEffect = Channel<RankingUiEffect>()
        val uiEffect = _uiEffect.receiveAsFlow()

        // Kept outside UiState (not something the screen renders directly) so switching the sort
        // metric can silently re-fetch "minha cidade"'s rank for the new metric without asking the
        // OS for a fresh GPS fix every time. Also persisted (RankingLocationPreferences): this
        // ViewModel is recreated on every visit to the tab, and without it each visit fell back to
        // "Usar minha localização" — a new GPS fix, and a permission prompt for anyone who granted
        // location "só desta vez".
        private var lastLat: Double? = null
        private var lastLon: Double? = null

        init {
            // Silent on the automatic first load — an unreachable backend shouldn't greet the user
            // with an error the moment they open the screen.
            refreshRanking(notifyOnFailure = false)
            viewModelScope.launch {
                val (lat, lon) = locationPreferences.getLastLocation() ?: return@launch
                // The user may have already tapped "Usar minha localização" while this was loading.
                if (lastLat != null) return@launch
                lastLat = lat
                lastLon = lon
                refreshMyCity(lat, lon, notifyOnFailure = false)
            }
        }

        fun onSortByChanged(sortBy: CityRankingSortBy) {
            if (sortBy == _uiState.value.sortBy) return
            _uiState.update { it.copy(sortBy = sortBy) }
            refreshRanking(notifyOnFailure = true)
            val lat = lastLat
            val lon = lastLon
            if (lat != null && lon != null) refreshMyCity(lat, lon, notifyOnFailure = false)
        }

        /** Re-fetches the ranking and, if known, "minha cidade" from the saved location — never a new
         * GPS fix; that only happens when the user asks for it via [findMyCity]. */
        fun refresh() {
            refreshRanking(notifyOnFailure = true)
            val lat = lastLat
            val lon = lastLon
            if (lat != null && lon != null) refreshMyCity(lat, lon, notifyOnFailure = false)
        }

        private fun refreshRanking(notifyOnFailure: Boolean) {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true) }
                val sortBy = _uiState.value.sortBy
                cityRepository.fetchRanking(sortBy)
                    .onSuccess { list ->
                        _uiState.update {
                            it.copy(
                                totalCities = list.totalCities,
                                top = list.top,
                                bottom = list.bottom,
                                loadFailed = false,
                            )
                        }
                    }
                    .onFailure { error ->
                        Log.e(TAG, "Failed to fetch city ranking", error)
                        _uiState.update { it.copy(loadFailed = it.top.isEmpty()) }
                        if (notifyOnFailure) {
                            _uiEffect.send(RankingUiEffect.ShowMessage(R.string.ranking_load_failed))
                        }
                    }
                _uiState.update { it.copy(isLoading = false) }
            }
        }

        /** Takes a fresh location fix (first use, or the card's "atualizar localização" button), saves
         * it for later visits, then resolves and shows their city's ranking row. */
        fun findMyCity() {
            viewModelScope.launch {
                _uiState.update { it.copy(isLocatingMyCity = true) }
                val location = locationProvider.getCurrentLocation()
                if (location == null) {
                    _uiEffect.send(RankingUiEffect.ShowMessage(R.string.ranking_location_failed))
                    _uiState.update { it.copy(isLocatingMyCity = false) }
                    return@launch
                }
                lastLat = location.latitude
                lastLon = location.longitude
                locationPreferences.saveLastLocation(location.latitude, location.longitude)
                refreshMyCity(location.latitude, location.longitude, notifyOnFailure = true)
            }
        }

        private fun refreshMyCity(
            lat: Double,
            lon: Double,
            notifyOnFailure: Boolean,
        ) {
            viewModelScope.launch {
                _uiState.update { it.copy(isLocatingMyCity = true) }
                cityRepository.fetchNearestCity(lat, lon, _uiState.value.sortBy)
                    .onSuccess { city -> _uiState.update { it.copy(myCity = city) } }
                    .onFailure { error ->
                        Log.e(TAG, "Failed to resolve the user's city", error)
                        if (notifyOnFailure) {
                            _uiEffect.send(RankingUiEffect.ShowMessage(R.string.ranking_city_resolve_failed))
                        }
                    }
                _uiState.update { it.copy(isLocatingMyCity = false) }
            }
        }

        private companion object {
            const val TAG = "RankingViewModel"
        }
    }
