package com.ipirangatech.fidd.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import com.ipirangatech.fidd.core.data.OnboardingPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val preferences: OnboardingPreferences
) : ViewModel() {

    // null = ainda lendo o DataStore; evita piscar a tela de onboarding por um frame antes da
    // primeira leitura resolver, para quem já passou por ela em uma sessão anterior.
    private val _hasCompletedOnboarding = MutableStateFlow<Boolean?>(null)
    val hasCompletedOnboarding: StateFlow<Boolean?> = _hasCompletedOnboarding.asStateFlow()

    init {
        viewModelScope.launch {
            preferences.hasCompletedOnboarding.collect { completed ->
                _hasCompletedOnboarding.value = completed
            }
        }
    }

    fun onOnboardingFinished() {
        viewModelScope.launch { preferences.markCompleted() }
    }
}
