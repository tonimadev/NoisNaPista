package digital.tonima.noisnapista.feature.ranking.impl

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import digital.tonima.noisnapista.core.data.CityRepository
import digital.tonima.noisnapista.core.location.LocationProvider
import digital.tonima.noisnapista.core.model.CityRanking
import digital.tonima.noisnapista.core.model.CityRankingSortBy
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
    /** The user's own city, once located — null before the user asks, or if it couldn't be
     * resolved. Its `rank` is specific to [sortBy], re-fetched whenever that changes. */
    val myCity: CityRanking? = null,
    val isLocatingMyCity: Boolean = false
)

sealed interface RankingUiEffect {
    data class ShowMessage(val message: String) : RankingUiEffect
}

@HiltViewModel
class RankingViewModel @Inject constructor(
    private val cityRepository: CityRepository,
    private val locationProvider: LocationProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(RankingUiState())
    val uiState: StateFlow<RankingUiState> = _uiState.asStateFlow()

    private val _uiEffect = Channel<RankingUiEffect>()
    val uiEffect = _uiEffect.receiveAsFlow()

    // Kept outside UiState (not something the screen renders directly) so switching the sort
    // metric can silently re-fetch "minha cidade"'s rank for the new metric without asking the
    // OS for a fresh GPS fix every time.
    private var lastLat: Double? = null
    private var lastLon: Double? = null

    init {
        // Silent on the automatic first load — an unreachable backend shouldn't greet the user
        // with an error the moment they open the screen.
        refresh(notifyOnFailure = false)
    }

    fun onSortByChanged(sortBy: CityRankingSortBy) {
        if (sortBy == _uiState.value.sortBy) return
        _uiState.update { it.copy(sortBy = sortBy) }
        refresh(notifyOnFailure = true)
        val lat = lastLat
        val lon = lastLon
        if (lat != null && lon != null) refreshMyCity(lat, lon, notifyOnFailure = false)
    }

    fun refresh(notifyOnFailure: Boolean = true) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val sortBy = _uiState.value.sortBy
            cityRepository.fetchRanking(sortBy)
                .onSuccess { list ->
                    _uiState.update { it.copy(totalCities = list.totalCities, top = list.top, bottom = list.bottom) }
                }
                .onFailure { error ->
                    Log.e(TAG, "Failed to fetch city ranking", error)
                    if (notifyOnFailure) {
                        _uiEffect.send(RankingUiEffect.ShowMessage("Não foi possível carregar o ranking de cidades"))
                    }
                }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    /** Finds the user's current location, then resolves and shows their city's ranking row. */
    fun findMyCity() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLocatingMyCity = true) }
            val location = locationProvider.getCurrentLocation()
            if (location == null) {
                _uiEffect.send(RankingUiEffect.ShowMessage("Não foi possível obter sua localização"))
                _uiState.update { it.copy(isLocatingMyCity = false) }
                return@launch
            }
            lastLat = location.latitude
            lastLon = location.longitude
            refreshMyCity(location.latitude, location.longitude, notifyOnFailure = true)
        }
    }

    private fun refreshMyCity(lat: Double, lon: Double, notifyOnFailure: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLocatingMyCity = true) }
            cityRepository.fetchNearestCity(lat, lon, _uiState.value.sortBy)
                .onSuccess { city -> _uiState.update { it.copy(myCity = city) } }
                .onFailure { error ->
                    Log.e(TAG, "Failed to resolve the user's city", error)
                    if (notifyOnFailure) {
                        _uiEffect.send(RankingUiEffect.ShowMessage("Não foi possível identificar sua cidade"))
                    }
                }
            _uiState.update { it.copy(isLocatingMyCity = false) }
        }
    }

    private companion object {
        const val TAG = "RankingViewModel"
    }
}
