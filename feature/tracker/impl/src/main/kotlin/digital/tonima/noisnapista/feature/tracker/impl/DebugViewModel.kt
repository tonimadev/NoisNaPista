package digital.tonima.noisnapista.feature.tracker.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import digital.tonima.noisnapista.core.data.PotholeRepository
import digital.tonima.noisnapista.core.model.DetectionDebugEntry
import digital.tonima.noisnapista.core.model.DetectionLabel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs both the debug detection list and its detail/labeling view — debug-build-only, see
 * MainActivity's BuildConfig.DEBUG gate. A single class is enough for both screens since each
 * NavEntry gets its own instance (rememberViewModelStoreNavEntryDecorator) and the detail screen
 * simply looks its target up by id from the same repository-backed flow.
 */
@HiltViewModel
class DebugViewModel @Inject constructor(
    private val repository: PotholeRepository
) : ViewModel() {

    val entries: StateFlow<List<DetectionDebugEntry>> = repository.getDebugEntries()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun updateLabel(potholeId: String, label: DetectionLabel) {
        viewModelScope.launch { repository.updateDetectionLabel(potholeId, label) }
    }

    fun updateNote(potholeId: String, note: String) {
        viewModelScope.launch { repository.updateDetectionNote(potholeId, note) }
    }
}
