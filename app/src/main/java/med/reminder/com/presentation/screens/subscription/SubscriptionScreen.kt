package med.reminder.com.presentation.screens.subscription

import android.app.Activity
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import med.reminder.com.R
import med.reminder.com.domain.model.SubscriptionFeature
import med.reminder.com.domain.model.SubscriptionPlan
import med.reminder.com.domain.model.SubscriptionPlans
import med.reminder.com.domain.model.SubscriptionTier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: SubscriptionViewModel = hiltViewModel()
) {
    val plans = remember { SubscriptionPlans.getAllPlans() }
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.subscription_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Header
            Text(
                text = stringResource(R.string.subscription_headline),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.subscription_subheadline),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Token cost breakdown card
            TokenCostBreakdown()

            Spacer(modifier = Modifier.height(16.dp))

            // Free trial card
            FreeTrialCard(
                isTrialActive = uiState.isTrialActive,
                hasTrialBeenUsed = uiState.hasTrialBeenUsed,
                trialRemainingMs = uiState.trialRemainingMs,
                onStartTrial = { viewModel.startFreeTrial() }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Plan cards
            plans.forEach { plan ->
                PlanCard(
                    plan = plan,
                    isSelected = uiState.selectedTier == plan.tier,
                    isCurrent = uiState.currentTier == plan.tier,
                    googlePrice = uiState.prices[plan.tier],
                    onSelect = { viewModel.selectTier(plan.tier) }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Subscribe button
            val selectedPlan = plans.first { it.tier == uiState.selectedTier }
            val isCurrentPlan = uiState.selectedTier == uiState.currentTier
            val displayPrice = uiState.prices[uiState.selectedTier] ?: selectedPlan.pricePerMonth

            Button(
                onClick = {
                    if (activity != null) {
                        viewModel.purchase(activity)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                enabled = !isCurrentPlan && uiState.selectedTier != SubscriptionTier.FREE && uiState.isBillingReady
            ) {
                Text(
                    text = when {
                        isCurrentPlan -> stringResource(R.string.subscription_current_plan)
                        uiState.selectedTier == SubscriptionTier.FREE -> stringResource(R.string.subscription_current_plan)
                        else -> stringResource(R.string.subscription_subscribe, displayPrice)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // Error message
            uiState.purchaseError?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Fine print
            Text(
                text = stringResource(R.string.subscription_fine_print),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TokenCostBreakdown() {
    val estimatedTokens = remember {
        SubscriptionPlans.estimateMonthlyTokens(
            dailyAnalysesPerMonth = 30,
            weeklyReportsPerMonth = 4,
            medAnalysesPerMonth = 3
        )
    }
    val estimatedCost = remember { SubscriptionPlans.estimateMonthlyCost(estimatedTokens) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Calculate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    stringResource(R.string.subscription_token_breakdown_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }

            TokenRow(
                label = stringResource(R.string.subscription_daily_analysis),
                tokens = "${SubscriptionPlans.TOKENS_PER_DAILY_ANALYSIS}",
                count = stringResource(R.string.subscription_per_call)
            )
            TokenRow(
                label = stringResource(R.string.subscription_weekly_report),
                tokens = "${SubscriptionPlans.TOKENS_PER_WEEKLY_REPORT}",
                count = stringResource(R.string.subscription_per_call)
            )
            TokenRow(
                label = stringResource(R.string.subscription_med_analysis),
                tokens = "${SubscriptionPlans.TOKENS_PER_MED_ANALYSIS}",
                count = stringResource(R.string.subscription_per_call)
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    stringResource(R.string.subscription_typical_monthly),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    stringResource(R.string.subscription_tokens_estimate, String.format("%,d", estimatedTokens)),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            Text(
                stringResource(R.string.subscription_api_cost, String.format("%.2f", estimatedCost)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TokenRow(label: String, tokens: String, count: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(
            "$tokens tokens $count",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PlanCard(
    plan: SubscriptionPlan,
    isSelected: Boolean,
    isCurrent: Boolean,
    googlePrice: String?,
    onSelect: () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outlineVariant,
        label = "borderColor"
    )

    Card(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = borderColor
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = isSelected,
                    onClick = onSelect
                )
                Spacer(modifier = Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            plan.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (plan.isPopular) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                Text(
                                    stringResource(R.string.subscription_popular),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        if (isCurrent) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.tertiary
                            ) {
                                Text(
                                    stringResource(R.string.subscription_current_plan),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Text(
                        plan.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        googlePrice ?: plan.pricePerMonth,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (plan.tier == SubscriptionTier.FREE)
                            MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.primary
                    )
                    if (plan.pricePerMonth != "$0") {
                        Text(
                            stringResource(R.string.subscription_per_month),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Feature list
            plan.features.forEach { feature ->
                FeatureRow(feature = feature)
            }
        }
    }
}

@Composable
private fun FeatureRow(feature: SubscriptionFeature) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (feature.included) Icons.Default.Check else Icons.Default.Close,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = if (feature.included) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = feature.title,
            style = MaterialTheme.typography.bodySmall,
            color = if (feature.included) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            textDecoration = if (!feature.included) TextDecoration.LineThrough else null
        )
    }
}

@Composable
private fun FreeTrialCard(
    isTrialActive: Boolean,
    hasTrialBeenUsed: Boolean,
    trialRemainingMs: Long,
    onStartTrial: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isTrialActive -> MaterialTheme.colorScheme.primaryContainer
                hasTrialBeenUsed -> MaterialTheme.colorScheme.surfaceVariant
                else -> MaterialTheme.colorScheme.secondaryContainer
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when {
                        isTrialActive -> Icons.Default.Stars
                        hasTrialBeenUsed -> Icons.Default.TimerOff
                        else -> Icons.Default.CardGiftcard
                    },
                    contentDescription = null,
                    tint = when {
                        isTrialActive -> MaterialTheme.colorScheme.primary
                        hasTrialBeenUsed -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.secondary
                    },
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when {
                        isTrialActive -> stringResource(R.string.subscription_trial_active)
                        hasTrialBeenUsed -> stringResource(R.string.subscription_trial_expired)
                        else -> stringResource(R.string.subscription_free_trial)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            when {
                isTrialActive -> {
                    val remaining = formatTrialRemaining(trialRemainingMs)
                    Text(
                        text = stringResource(R.string.subscription_trial_remaining, remaining),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                hasTrialBeenUsed -> {
                    Text(
                        text = stringResource(R.string.subscription_trial_expired),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> {
                    Text(
                        text = stringResource(R.string.subscription_trial_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = onStartTrial,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.subscription_free_trial),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun formatTrialRemaining(ms: Long): String {
    val totalMinutes = ms / (60 * 1000)
    val totalHours = totalMinutes / 60
    val days = totalHours / 24
    val hours = totalHours % 24
    val minutes = totalMinutes % 60

    return if (days > 0) {
        stringResource(R.string.subscription_trial_days_left, days, hours)
    } else {
        stringResource(R.string.subscription_trial_hours_left, hours, minutes)
    }
}

