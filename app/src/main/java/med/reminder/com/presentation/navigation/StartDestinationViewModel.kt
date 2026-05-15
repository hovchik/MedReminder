package med.reminder.com.presentation.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import med.reminder.com.data.preferences.UserPreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StartDestinationViewModel @Inject constructor(
    private val userPreferencesManager: UserPreferencesManager
) : ViewModel() {

    private val _startDestination = MutableStateFlow<String?>(null)
    val startDestination: StateFlow<String?> = _startDestination

    init {
        viewModelScope.launch {
            val legalAccepted = userPreferencesManager.isLegalAcceptedNow()
            val onboardingDone = userPreferencesManager.isOnboardingCompleted.first()
            _startDestination.value = when {
                !legalAccepted -> Screen.LegalAcceptance.route
                !onboardingDone -> Screen.Onboarding.route
                else -> Screen.Home.route
            }
        }
    }
}
