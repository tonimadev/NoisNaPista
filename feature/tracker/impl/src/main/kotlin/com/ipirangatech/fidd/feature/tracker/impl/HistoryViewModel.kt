package com.ipirangatech.fidd.feature.tracker.impl

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ipirangatech.fidd.core.data.PotholeRepository
import com.ipirangatech.fidd.core.location.LocationProvider
import com.ipirangatech.fidd.core.model.Pothole
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

sealed interface HistoryUiIntent {
    data class Delete(val pothole: Pothole) : HistoryUiIntent

    data class MarkFalseAlarm(val pothole: Pothole) : HistoryUiIntent

    data class VoteFixed(val pothole: Pothole) : HistoryUiIntent

    data object RefreshCommunity : HistoryUiIntent
}

sealed interface HistoryUiEffect {
    data class ShowMessage(
        @StringRes val messageRes: Int,
    ) : HistoryUiEffect
}

@HiltViewModel
class HistoryViewModel
    @Inject
    constructor(
        private val repository: PotholeRepository,
        private val locationProvider: LocationProvider,
    ) : ViewModel() {
        val potholes: StateFlow<List<Pothole>> =
            repository.getPotholes()
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = emptyList(),
                )

        private val _communityPotholes = MutableStateFlow<List<Pothole>>(emptyList())
        val communityPotholes: StateFlow<List<Pothole>> = _communityPotholes.asStateFlow()

        private val _uiEffect = Channel<HistoryUiEffect>()
        val uiEffect = _uiEffect.receiveAsFlow()

        init {
            // Silent on the automatic first load — see MapViewModel for the same reasoning.
            refreshCommunity(notifyOnFailure = false)
        }

        fun onIntent(intent: HistoryUiIntent) {
            when (intent) {
                is HistoryUiIntent.Delete -> viewModelScope.launch { repository.delete(intent.pothole) }
                is HistoryUiIntent.MarkFalseAlarm -> viewModelScope.launch { repository.markFalseAlarm(intent.pothole) }
                is HistoryUiIntent.VoteFixed -> voteFixed(intent.pothole)
                HistoryUiIntent.RefreshCommunity -> refreshCommunity(notifyOnFailure = true)
            }
        }

        private fun voteFixed(pothole: Pothole) {
            val serverId = pothole.serverId ?: return
            viewModelScope.launch {
                repository.castFixVote(serverId)
                    .onSuccess { updated ->
                        replaceInCommunityList(updated)
                        _uiEffect.send(
                            HistoryUiEffect.ShowMessage(
                                if (updated.status == "FIXED") {
                                    R.string.history_vote_marked_fixed
                                } else {
                                    R.string.history_vote_registered
                                },
                            ),
                        )
                    }
                    .onFailure {
                        _uiEffect.send(HistoryUiEffect.ShowMessage(R.string.history_vote_failed))
                    }
            }
        }

        private fun refreshCommunity(notifyOnFailure: Boolean) {
            viewModelScope.launch {
                // Only the potholes around the user (server-side radius + limit, nearest first) —
                // never the whole country.
                val location = locationProvider.getCurrentLocation()
                if (location == null) {
                    if (notifyOnFailure) {
                        _uiEffect.send(HistoryUiEffect.ShowMessage(R.string.history_community_location_unavailable))
                    }
                    return@launch
                }
                repository.fetchNearbyCommunityPotholes(location)
                    .onSuccess { _communityPotholes.value = it }
                    .onFailure {
                        if (notifyOnFailure) {
                            _uiEffect.send(HistoryUiEffect.ShowMessage(R.string.history_community_refresh_failed))
                        }
                    }
            }
        }

        private fun replaceInCommunityList(updated: Pothole) {
            _communityPotholes.value =
                _communityPotholes.value.map {
                    if (it.serverId == updated.serverId) updated else it
                }
        }
    }
