package med.reminder.com.presentation.screens.legal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import med.reminder.com.data.preferences.UserPreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LegalAcceptanceViewModel @Inject constructor(
    private val userPreferencesManager: UserPreferencesManager
) : ViewModel() {
    fun acceptAll(onDone: (onboardingAlreadyDone: Boolean) -> Unit) {
        viewModelScope.launch {
            userPreferencesManager.acceptLegal()
            val onboardingDone = userPreferencesManager.isOnboardingDone()
            onDone(onboardingDone)
        }
    }
}
