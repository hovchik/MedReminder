package med.reminder.com.billing

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.android.billingclient.api.Purchase
import med.reminder.com.domain.model.SubscriptionPlans
import med.reminder.com.domain.model.SubscriptionTier
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
        private val KEY_TRIAL_START = longPreferencesKey("free_trial_start_ms")
        private const val TRIAL_DURATION_MS = 3L * 24 * 60 * 60 * 1000 // 3 days
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

    // --- Free trial ---

    /** Flow that emits the trial start timestamp, or 0 if never started. */
    val trialStartMs: Flow<Long> = context.subscriptionDataStore.data.map { prefs ->
        prefs[KEY_TRIAL_START] ?: 0L
    }

    /** Whether the free trial is currently active. */
    val isTrialActive: Flow<Boolean> = trialStartMs.map { start ->
        start > 0L && System.currentTimeMillis() - start < TRIAL_DURATION_MS
    }

    /** Whether the trial has been used (started) regardless of expiry. */
    val hasTrialBeenUsed: Flow<Boolean> = trialStartMs.map { it > 0L }

    /** Remaining trial time in milliseconds, or 0 if expired / not started. */
    val trialRemainingMs: Flow<Long> = trialStartMs.map { start ->
        if (start <= 0L) 0L
        else (TRIAL_DURATION_MS - (System.currentTimeMillis() - start)).coerceAtLeast(0L)
    }

    /** Start the 3-day free trial. No-op if already used. */
    suspend fun startFreeTrial() {
        context.subscriptionDataStore.edit { prefs ->
            if ((prefs[KEY_TRIAL_START] ?: 0L) == 0L) {
                prefs[KEY_TRIAL_START] = System.currentTimeMillis()
                Log.d(TAG, "Free trial started")
            } else {
                Log.d(TAG, "Free trial already used — ignoring")
            }
        }
    }

    // --- Effective tier (includes trial) ---

    private val baseTier: Flow<SubscriptionTier> = context.subscriptionDataStore.data.map { prefs ->
        val tierName = prefs[KEY_TIER] ?: SubscriptionTier.FREE.name
        try {
            SubscriptionTier.valueOf(tierName)
        } catch (_: Exception) {
            SubscriptionTier.FREE
        }
    }

    /**
     * The effective subscription tier, accounting for free trial.
     * During an active trial, FREE users are upgraded to PRO.
     */
    val currentTier: Flow<SubscriptionTier> = combine(baseTier, isTrialActive) { tier, trial ->
        if (tier == SubscriptionTier.FREE && trial) SubscriptionTier.PRO else tier
    }

    suspend fun getCurrentTierOnce(): SubscriptionTier = currentTier.first()

    private fun resolveTierFromPurchases(purchases: List<Purchase>): SubscriptionTier {
        // Find the highest active subscription tier.
        // Defensive copies protect against the billing library mutating its
        // internal lists on a background thread while we iterate.
        var highestTier = SubscriptionTier.FREE
        for (purchase in purchases.toList()) {
            if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) continue
            for (productId in purchase.products.toList()) {
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
