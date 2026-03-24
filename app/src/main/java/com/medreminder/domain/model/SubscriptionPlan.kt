package com.medreminder.domain.model

/**
 * Subscription tiers for MedReminder.
 *
 * Pricing rationale (based on Cloud AI token costs):
 * - Each daily analysis ≈ 1,700 tokens (1,200 input + 500 output)
 * - Each weekly report  ≈ 2,100 tokens (1,500 input + 600 output)
 * - Each medication deep analysis ≈ 2,000 tokens (1,200 input + 800 output)
 *
 * At typical Cloud AI rates (blended average across providers):
 * - Light user (~65K tokens/mo) costs ~$0.50/mo
 * - Heavy user (~200K tokens/mo) costs ~$1.50/mo
 * - Power user (~500K+ tokens/mo) costs ~$4.00/mo
 *
 * Cloud AI (Pro/Premium) is priced higher to cover API costs + margin.
 * All other features are kept affordable.
 */

enum class SubscriptionTier {
    FREE,
    BASIC,
    PRO,
    PREMIUM
}

data class SubscriptionPlan(
    val tier: SubscriptionTier,
    val name: String,
    val pricePerMonth: String,
    val description: String,
    val features: List<SubscriptionFeature>,
    val isPopular: Boolean = false
)

data class SubscriptionFeature(
    val title: String,
    val included: Boolean = true
)

object SubscriptionPlans {

    /** Estimated tokens per AI call type */
    const val TOKENS_PER_DAILY_ANALYSIS = 1_700
    const val TOKENS_PER_WEEKLY_REPORT = 2_100
    const val TOKENS_PER_MED_ANALYSIS = 2_000

    /** Monthly token budgets per tier */
    const val PRO_MONTHLY_TOKEN_BUDGET = 150_000L      // ~45 daily + 4 weekly + 5 med analyses
    const val PREMIUM_MONTHLY_TOKEN_BUDGET = 1_000_000L // unlimited practical use

    /** Approximate cost per 1K tokens (blended input/output across providers) */
    const val COST_PER_1K_TOKENS_USD = 0.008f // $0.008 per 1K tokens (blended avg)

    /** Pro tier: ~150K tokens × $0.008/1K = $1.20 cost → $4.99 price (margin for infra + profit) */
    /** Premium tier: ~1M tokens × $0.008/1K = $8.00 cost → $9.99 price */

    fun getAllPlans(): List<SubscriptionPlan> = listOf(
        SubscriptionPlan(
            tier = SubscriptionTier.FREE,
            name = "Free",
            pricePerMonth = "$0",
            description = "Essential medication tracking for everyone",
            features = listOf(
                SubscriptionFeature("Up to 10 medications"),
                SubscriptionFeature("Reminders & alarms"),
                SubscriptionFeature("Dose history tracking"),
                SubscriptionFeature("Basic adherence stats"),
                SubscriptionFeature("On-device AI (System/Local)"),
                SubscriptionFeature("1 caregiver contact"),
                SubscriptionFeature("OCR medication scanning"),
                SubscriptionFeature("Cloud AI insights", included = false),
                SubscriptionFeature("Medication deep analysis", included = false),
                SubscriptionFeature("Unlimited medications", included = false)
            )
        ),
        SubscriptionPlan(
            tier = SubscriptionTier.BASIC,
            name = "Basic",
            pricePerMonth = "$1.99",
            description = "Full tracking power without limits",
            features = listOf(
                SubscriptionFeature("Unlimited medications"),
                SubscriptionFeature("Reminders & alarms"),
                SubscriptionFeature("Full dose history"),
                SubscriptionFeature("Advanced adherence reports"),
                SubscriptionFeature("On-device AI (System/Local)"),
                SubscriptionFeature("Unlimited caregivers"),
                SubscriptionFeature("Family members support"),
                SubscriptionFeature("Drug interaction checker"),
                SubscriptionFeature("Data export"),
                SubscriptionFeature("Cloud AI insights", included = false),
                SubscriptionFeature("Medication deep analysis", included = false)
            )
        ),
        SubscriptionPlan(
            tier = SubscriptionTier.PRO,
            name = "Pro",
            pricePerMonth = "$4.99",
            description = "Smart AI insights powered by the cloud",
            isPopular = true,
            features = listOf(
                SubscriptionFeature("Everything in Basic"),
                SubscriptionFeature("Cloud AI daily insights"),
                SubscriptionFeature("Cloud AI weekly reports"),
                SubscriptionFeature("5 medication deep analyses/mo"),
                SubscriptionFeature("~150K tokens/month included"),
                SubscriptionFeature("Choose AI provider"),
                SubscriptionFeature("3D anatomy visualization"),
                SubscriptionFeature("Priority notifications"),
                SubscriptionFeature("Unlimited deep analyses", included = false)
            )
        ),
        SubscriptionPlan(
            tier = SubscriptionTier.PREMIUM,
            name = "Premium",
            pricePerMonth = "$9.99",
            description = "Maximum AI power for the whole family",
            features = listOf(
                SubscriptionFeature("Everything in Pro"),
                SubscriptionFeature("Unlimited Cloud AI analyses"),
                SubscriptionFeature("Unlimited medication deep analyses"),
                SubscriptionFeature("~1M tokens/month included"),
                SubscriptionFeature("All AI providers included"),
                SubscriptionFeature("Family plan (up to 5 members)"),
                SubscriptionFeature("Per-member AI insights"),
                SubscriptionFeature("Advanced drug interaction alerts"),
                SubscriptionFeature("Priority AI processing")
            )
        )
    )

    /**
     * Calculate estimated monthly token usage based on user behavior.
     * @param dailyAnalysesPerMonth how many daily AI analyses the user runs
     * @param weeklyReportsPerMonth how many weekly AI reports
     * @param medAnalysesPerMonth how many medication deep analyses
     * @return estimated total tokens consumed
     */
    fun estimateMonthlyTokens(
        dailyAnalysesPerMonth: Int = 30,
        weeklyReportsPerMonth: Int = 4,
        medAnalysesPerMonth: Int = 3
    ): Long {
        return (dailyAnalysesPerMonth.toLong() * TOKENS_PER_DAILY_ANALYSIS) +
                (weeklyReportsPerMonth.toLong() * TOKENS_PER_WEEKLY_REPORT) +
                (medAnalysesPerMonth.toLong() * TOKENS_PER_MED_ANALYSIS)
    }

    /**
     * Calculate raw API cost for a given token count.
     */
    fun estimateMonthlyCost(tokens: Long): Float {
        return (tokens / 1000f) * COST_PER_1K_TOKENS_USD
    }
}
