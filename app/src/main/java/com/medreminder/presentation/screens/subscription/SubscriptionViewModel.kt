package com.medreminder.presentation.screens.subscription

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medreminder.billing.BillingManager
import com.medreminder.billing.SubscriptionRepository
import com.medreminder.domain.model.SubscriptionPlans
import com.medreminder.domain.model.SubscriptionTier
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SubscriptionUiState(
    val currentTier: SubscriptionTier = SubscriptionTier.FREE,
    val selectedTier: SubscriptionTier = SubscriptionTier.PRO,
    val prices: Map<SubscriptionTier, String> = emptyMap(),
    val isBillingReady: Boolean = false,
    val purchaseError: String? = null
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
    }

    private fun updatePrices() {
        val prices = mutableMapOf<SubscriptionTier, String>()
        SubscriptionTier.entries.forEach { tier ->
            billingManager.getFormattedPrice(tier)?.let { price ->
                prices[tier] = price
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

    fun dismissError() {
        _uiState.update { it.copy(purchaseError = null) }
    }
}
