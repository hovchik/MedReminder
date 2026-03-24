package com.medreminder.billing

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.android.billingclient.api.Purchase
import com.medreminder.domain.model.SubscriptionPlans
import com.medreminder.domain.model.SubscriptionTier
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

private val Context.subscriptionDataStore by preferencesDataStore(name = "subscription_prefs")

@Singleton
class SubscriptionRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val billingManager: BillingManager
) {
    companion object {
        private const val TAG = "SubscriptionRepo"
        private val KEY_TIER = stringPreferencesKey("subscription_tier")
        private val KEY_DEEP_ANALYSIS_COUNT = intPreferencesKey("deep_analysis_count_this_month")
        private val KEY_DEEP_ANALYSIS_MONTH = intPreferencesKey("deep_analysis_reset_month")
        private val KEY_DEEP_ANALYSIS_YEAR = intPreferencesKey("deep_analysis_reset_year")
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // Observe purchases from BillingManager and update local tier
        scope.launch {
            billingManager.currentPurchases.collect { purchases ->
                val tier = resolveTierFromPurchases(purchases)
                updateLocalTier(tier)
            }
        }
    }

    val currentTier: Flow<SubscriptionTier> = context.subscriptionDataStore.data.map { prefs ->
        val tierName = prefs[KEY_TIER] ?: SubscriptionTier.FREE.name
        try {
            SubscriptionTier.valueOf(tierName)
        } catch (_: Exception) {
            SubscriptionTier.FREE
        }
    }

    suspend fun getCurrentTierOnce(): SubscriptionTier = currentTier.first()

    private fun resolveTierFromPurchases(purchases: List<Purchase>): SubscriptionTier {
        // Find the highest active subscription tier
        var highestTier = SubscriptionTier.FREE
        for (purchase in purchases) {
            if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) continue
            for (productId in purchase.products) {
                val tier = SubscriptionTier.fromProductId(productId) ?: continue
                if (tier.ordinal > highestTier.ordinal) {
                    highestTier = tier
                }
            }
        }
        return highestTier
    }

    private suspend fun updateLocalTier(tier: SubscriptionTier) {
        context.subscriptionDataStore.edit { prefs ->
            prefs[KEY_TIER] = tier.name
        }
        Log.d(TAG, "Updated local tier to $tier")
    }

    // --- Deep analysis usage tracking ---

    val deepAnalysisCountThisMonth: Flow<Int> = context.subscriptionDataStore.data.map { prefs ->
        val savedMonth = prefs[KEY_DEEP_ANALYSIS_MONTH] ?: 0
        val savedYear = prefs[KEY_DEEP_ANALYSIS_YEAR] ?: 0
        val cal = Calendar.getInstance()
        val currentMonth = cal.get(Calendar.MONTH)
        val currentYear = cal.get(Calendar.YEAR)

        if (savedMonth != currentMonth || savedYear != currentYear) {
            0 // New month, reset count
        } else {
            prefs[KEY_DEEP_ANALYSIS_COUNT] ?: 0
        }
    }

    suspend fun incrementDeepAnalysisCount() {
        context.subscriptionDataStore.edit { prefs ->
            val cal = Calendar.getInstance()
            val currentMonth = cal.get(Calendar.MONTH)
            val currentYear = cal.get(Calendar.YEAR)
            val savedMonth = prefs[KEY_DEEP_ANALYSIS_MONTH] ?: -1
            val savedYear = prefs[KEY_DEEP_ANALYSIS_YEAR] ?: -1

            if (savedMonth != currentMonth || savedYear != currentYear) {
                // New month — reset
                prefs[KEY_DEEP_ANALYSIS_COUNT] = 1
                prefs[KEY_DEEP_ANALYSIS_MONTH] = currentMonth
                prefs[KEY_DEEP_ANALYSIS_YEAR] = currentYear
            } else {
                val current = prefs[KEY_DEEP_ANALYSIS_COUNT] ?: 0
                prefs[KEY_DEEP_ANALYSIS_COUNT] = current + 1
            }
        }
    }

    suspend fun canPerformDeepAnalysis(): Boolean {
        val tier = getCurrentTierOnce()
        val maxAnalyses = SubscriptionPlans.maxDeepAnalysesPerMonth(tier)
            ?: return true // null = unlimited
        if (maxAnalyses == 0) return false
        val currentCount = deepAnalysisCountThisMonth.first()
        return currentCount < maxAnalyses
    }

    fun hasCloudAiAccess(): Flow<Boolean> = currentTier.map { tier ->
        SubscriptionPlans.hasCloudAi(tier)
    }

    fun getMedicationLimit(): Flow<Int?> = currentTier.map { tier ->
        SubscriptionPlans.maxMedications(tier)
    }
}
