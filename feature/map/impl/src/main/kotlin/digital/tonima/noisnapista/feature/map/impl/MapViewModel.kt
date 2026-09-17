package digital.tonima.noisnapista.feature.map.impl

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import digital.tonima.noisnapista.core.data.PotholeRepository
import digital.tonima.noisnapista.core.model.Pothole
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface MapUiEffect {
    data class ShowMessage(val message: String) : MapUiEffect
}

@HiltViewModel
class MapViewModel @Inject constructor(
    private val repository: PotholeRepository
) : ViewModel() {
    val potholes: StateFlow<List<Pothole>> = repository.getActivePotholes()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _communityPotholes = MutableStateFlow<List<Pothole>>(emptyList())
    val communityPotholes: StateFlow<List<Pothole>> = _communityPotholes.asStateFlow()

    private val _uiEffect = Channel<MapUiEffect>()
    val uiEffect = _uiEffect.receiveAsFlow()

    init {
        // Silent on the automatic first load — an unreachable backend shouldn't greet the user
        // with an error the moment they open the map; the device's own pins still render fine.
        refreshCommunityPotholes(notifyOnFailure = false)
    }

    fun refreshCommunityPotholes(notifyOnFailure: Boolean = true) {
        viewModelScope.launch {
            repository.fetchCommunityPotholes()
                .onSuccess { _communityPotholes.value = it }
                .onFailure { error ->
                    Log.e(TAG, "Failed to fetch community potholes", error)
                    if (notifyOnFailure) {
                        _uiEffect.send(MapUiEffect.ShowMessage("Não foi possível atualizar os buracos da comunidade"))
                    }
                }
            // Keeps the previous list on failure — the device's own locally-detected potholes
            // still render regardless.
        }
    }

    private companion object {
        const val TAG = "MapViewModel"
    }
}
