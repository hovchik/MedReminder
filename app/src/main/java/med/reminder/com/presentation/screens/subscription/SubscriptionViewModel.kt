package med.reminder.com.presentation.screens.subscription

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import med.reminder.com.billing.BillingManager
import med.reminder.com.billing.SubscriptionRepository
import med.reminder.com.domain.model.SubscriptionPlans
import med.reminder.com.domain.model.SubscriptionTier
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SubscriptionUiState(
    val currentTier: SubscriptionTier = SubscriptionTier.FREE,
    val selectedTier: SubscriptionTier = SubscriptionTier.PRO,
    val prices: Map<SubscriptionTier, String> = emptyMap(),
    val isBillingReady: Boolean = false,
    val purchaseError: String? = null,
    val isTrialActive: Boolean = false,
    val hasTrialBeenUsed: Boolean = false,
    val trialRemainingMs: Long = 0L
)

@HiltViewModel
class SubscriptionViewModel @Inject constructor(
    private val billingManager: BillingManager,
    private val subscriptionRepository: SubscriptionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SubscriptionUiState())
    val uiState: StateFlow<SubscriptionUiState> = _uiState.asStateFlow()

    init {
        billingManager.initialize()

        viewModelScope.launch {
            subscriptionRepository.currentTier.collect { tier ->
                _uiState.update { it.copy(currentTier = tier) }
            }
        }

        viewModelScope.launch {
            billingManager.billingConnectionState.collect { connected ->
                _uiState.update { it.copy(isBillingReady = connected) }
                if (connected) {
                    updatePrices()
                }
            }
        }

        viewModelScope.launch {
            billingManager.productDetails.collect {
                updatePrices()
            }
        }

        viewModelScope.launch {
            subscriptionRepository.isTrialActive.collect { active ->
                _uiState.update { it.copy(isTrialActive = active) }
            }
        }

        viewModelScope.launch {
            subscriptionRepository.hasTrialBeenUsed.collect { used ->
                _uiState.update { it.copy(hasTrialBeenUsed = used) }
            }
        }

        viewModelScope.launch {
            subscriptionRepository.trialRemainingMs.collect { remaining ->
                _uiState.update { it.copy(trialRemainingMs = remaining) }
            }
        }
    }

    private fun updatePrices() {
        val prices = buildMap {
            SubscriptionTier.entries.forEach { tier ->
                billingManager.getFormattedPrice(tier)?.let { price ->
                    put(tier, price)
                }
            }
        }
        _uiState.update { it.copy(prices = prices) }
    }

    fun selectTier(tier: SubscriptionTier) {
        _uiState.update { it.copy(selectedTier = tier, purchaseError = null) }
    }

    fun purchase(activity: Activity) {
        val tier = _uiState.value.selectedTier
        if (tier == SubscriptionTier.FREE) return

        val launched = billingManager.launchPurchaseFlow(activity, tier)
        if (!launched) {
            _uiState.update {
                it.copy(purchaseError = "Could not start purchase. Please try again.")
            }
        }
    }

    fun startFreeTrial() {
        viewModelScope.launch {
            subscriptionRepository.startFreeTrial()
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(purchaseError = null) }
    }
}
