package com.medreminder.presentation.screens.adherence

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.medreminder.R
import com.medreminder.ai.AiProviderType
import com.medreminder.ai.AnalysisResult
import com.medreminder.ai.RiskLevel
import com.medreminder.ai.local.DailyAnalysisUseCase
import com.medreminder.ai.local.WeeklyReportUseCase
import com.medreminder.domain.model.AdherenceStats
import com.medreminder.domain.model.DayAdherence
import com.medreminder.domain.repository.MedicationRepository
import com.medreminder.util.DateUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AdherenceViewModel @Inject constructor(
    private val repository: MedicationRepository,
    private val dailyAnalysisUseCase: DailyAnalysisUseCase,
    private val weeklyReportUseCase: WeeklyReportUseCase
) : ViewModel() {

    private val _stats = MutableStateFlow(AdherenceStats())
    val stats: StateFlow<AdherenceStats> = _stats.asStateFlow()

    private val _period = MutableStateFlow("week")
    val period: StateFlow<String> = _period.asStateFlow()

    private val _dailyAiReport = MutableStateFlow<AnalysisResult?>(null)
    val dailyAiReport: StateFlow<AnalysisResult?> = _dailyAiReport.asStateFlow()

    private val _isDailyAnalyzing = MutableStateFlow(false)
    val isDailyAnalyzing: StateFlow<Boolean> = _isDailyAnalyzing.asStateFlow()

    private val _weeklyAiReport = MutableStateFlow<AnalysisResult?>(null)
    val weeklyAiReport: StateFlow<AnalysisResult?> = _weeklyAiReport.asStateFlow()

    private val _isWeeklyAnalyzing = MutableStateFlow(false)
    val isWeeklyAnalyzing: StateFlow<Boolean> = _isWeeklyAnalyzing.asStateFlow()

    private val _dailyError = MutableStateFlow<String?>(null)
    val dailyError: StateFlow<String?> = _dailyError.asStateFlow()

    private val _weeklyError = MutableStateFlow<String?>(null)
    val weeklyError: StateFlow<String?> = _weeklyError.asStateFlow()

    init {
        loadStats()
        // Auto-trigger AI analyses on screen load
        runDailyAnalysis()
        runWeeklyAnalysis()
    }

    fun setPeriod(p: String) {
        _period.value = p
        loadStats()
    }

    fun runDailyAnalysis() {
        viewModelScope.launch {
            _isDailyAnalyzing.value = true
            _dailyError.value = null
            try {
                _dailyAiReport.value = dailyAnalysisUseCase.analyze()
            } catch (e: Exception) {
                _dailyError.value = e.message ?: "Daily analysis failed"
            }
            _isDailyAnalyzing.value = false
        }
    }

    fun runWeeklyAnalysis() {
        viewModelScope.launch {
            _isWeeklyAnalyzing.value = true
            _weeklyError.value = null
            try {
                _weeklyAiReport.value = weeklyReportUseCase.analyze()
            } catch (e: Exception) {
                _weeklyError.value = e.message ?: "Weekly analysis failed"
            }
            _isWeeklyAnalyzing.value = false
        }
    }

    private fun loadStats() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val start = when (_period.value) {
                "week" -> DateUtils.daysAgo(7)
                "month" -> DateUtils.daysAgo(30)
                "year" -> DateUtils.daysAgo(365)
                else -> DateUtils.daysAgo(7)
            }
            _stats.value = repository.getAdherenceStats(start, now)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdherenceScreen(viewModel: AdherenceViewModel = hiltViewModel()) {
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val period by viewModel.period.collectAsStateWithLifecycle()
    val dailyReport by viewModel.dailyAiReport.collectAsStateWithLifecycle()
    val isDailyAnalyzing by viewModel.isDailyAnalyzing.collectAsStateWithLifecycle()
    val weeklyReport by viewModel.weeklyAiReport.collectAsStateWithLifecycle()
    val isWeeklyAnalyzing by viewModel.isWeeklyAnalyzing.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(stringResource(R.string.adherence), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        // Period selector
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                "week" to stringResource(R.string.week),
                "month" to stringResource(R.string.month),
                "year" to stringResource(R.string.year)
            ).forEach { (key, label) ->
                FilterChip(
                    selected = period == key,
                    onClick = { viewModel.setPeriod(key) },
                    label = { Text(label) },
                    shape = RoundedCornerShape(10.dp)
                )
            }
        }

        // Rate card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = when {
                    stats.adherenceRate >= 90 -> Color(0xFF2ECC71).copy(alpha = 0.12f)
                    stats.adherenceRate >= 70 -> Color(0xFFF39C12).copy(alpha = 0.12f)
                    else -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                }
            ),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "${stats.adherenceRate.toInt()}%",
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        stats.adherenceRate >= 90 -> Color(0xFF2ECC71)
                        stats.adherenceRate >= 70 -> Color(0xFFF39C12)
                        else -> MaterialTheme.colorScheme.error
                    }
                )
                Text(stringResource(R.string.adherence_rate), style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Stats grid
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(stringResource(R.string.status_taken), "${stats.takenDoses}", Icons.Default.Check,
                Color(0xFF2ECC71), Modifier.weight(1f))
            StatCard(stringResource(R.string.status_missed), "${stats.missedDoses}", Icons.Default.Close,
                MaterialTheme.colorScheme.error, Modifier.weight(1f))
            StatCard(stringResource(R.string.status_skipped), "${stats.skippedDoses}", Icons.Default.SkipNext,
                Color(0xFFF39C12), Modifier.weight(1f))
        }

        // Streak
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("\uD83D\uDD25", fontSize = 32.sp)
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(stringResource(R.string.current_streak), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                    Text(
                        if (stats.currentStreak != 1) stringResource(R.string.days_streak, stats.currentStreak)
                        else stringResource(R.string.day_streak, stats.currentStreak),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }

        // AI Daily Insight
        val dailyError by viewModel.dailyError.collectAsStateWithLifecycle()
        AiInsightCard(
            title = stringResource(R.string.ai_daily_insight),
            analysis = dailyReport,
            isAnalyzing = isDailyAnalyzing,
            error = dailyError,
            onRunAnalysis = { viewModel.runDailyAnalysis() }
        )

        // AI Weekly Report
        val weeklyError by viewModel.weeklyError.collectAsStateWithLifecycle()
        AiInsightCard(
            title = stringResource(R.string.ai_weekly_insight),
            analysis = weeklyReport,
            isAnalyzing = isWeeklyAnalyzing,
            error = weeklyError,
            onRunAnalysis = { viewModel.runWeeklyAnalysis() }
        )

        // Weekly bar chart
        if (stats.weeklyData.isNotEmpty()) {
            Text(stringResource(R.string.daily_breakdown), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        stats.weeklyData.take(7).forEach { day ->
                            DayBar(day)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun StatCard(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = color)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun AiInsightCard(
    title: String,
    analysis: AnalysisResult?,
    isAnalyzing: Boolean,
    error: String? = null,
    onRunAnalysis: () -> Unit
) {
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
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.Psychology,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                if (analysis != null) {
                    val providerLabel = when (analysis.providerUsed) {
                        AiProviderType.CLOUD -> stringResource(R.string.provider_cloud)
                        AiProviderType.SYSTEM_AI -> stringResource(R.string.provider_on_device)
                        AiProviderType.CUSTOM_LOCAL -> stringResource(R.string.provider_local)
                        AiProviderType.AUTO -> stringResource(R.string.provider_auto)
                    }
                    Text(
                        providerLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isAnalyzing) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.analyzing),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (error != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                TextButton(
                    onClick = onRunAnalysis,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.refresh_analysis), style = MaterialTheme.typography.labelSmall)
                }
            } else if (analysis != null) {
                // Risk level badge
                val riskColor = when (analysis.riskLevel) {
                    RiskLevel.LOW -> Color(0xFF2ECC71)
                    RiskLevel.MODERATE -> Color(0xFFF39C12)
                    RiskLevel.HIGH -> MaterialTheme.colorScheme.error
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = riskColor.copy(alpha = 0.15f)
                ) {
                    val riskLabel = when (analysis.riskLevel) {
                        RiskLevel.LOW -> stringResource(R.string.risk_low)
                        RiskLevel.MODERATE -> stringResource(R.string.risk_moderate)
                        RiskLevel.HIGH -> stringResource(R.string.risk_high)
                    }
                    Text(
                        "${stringResource(R.string.med_risk_assessment)}: $riskLabel",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = riskColor
                    )
                }

                // Cloud error warning (fell back to local)
                if (!analysis.cloudError.isNullOrEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFFFF3E0)
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = Color(0xFFF57C00),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                analysis.cloudError,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFE65100)
                            )
                        }
                    }
                }

                // Summary
                Text(analysis.summary, style = MaterialTheme.typography.bodyMedium)

                // All insights
                if (analysis.insights.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        stringResource(R.string.insights_header),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    analysis.insights.forEach { insight ->
                        Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(top = 2.dp)) {
                            Icon(Icons.Default.Insights, null, modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.tertiary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(insight, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // All recommendations
                if (analysis.recommendations.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        stringResource(R.string.recommendations_header),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFF39C12)
                    )
                    analysis.recommendations.forEach { rec ->
                        Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(top = 2.dp)) {
                            Icon(Icons.Default.Lightbulb, null, modifier = Modifier.size(14.dp),
                                tint = Color(0xFFF39C12))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(rec, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // Latency info
                if (analysis.latencyMs > 0) {
                    Text(
                        stringResource(R.string.analysis_time, analysis.latencyMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }

                // Refresh button
                TextButton(
                    onClick = onRunAnalysis,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.refresh_analysis), style = MaterialTheme.typography.labelSmall)
                }
            } else {
                OutlinedButton(
                    onClick = onRunAnalysis,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.get_ai_insight))
                }
            }
        }
    }
}

@Composable
fun DayBar(day: DayAdherence) {
    val rate = if (day.total > 0) day.taken.toFloat() / day.total else 0f
    val animatedHeight by animateFloatAsState(
        targetValue = rate, animationSpec = tween(600), label = "bar"
    )
    val barColor = when {
        rate >= 1f -> Color(0xFF2ECC71)
        rate >= 0.5f -> Color(0xFFF39C12)
        rate > 0f -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .width(28.dp)
                .height(120.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(fraction = animatedHeight.coerceIn(0f, 1f))
                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                    .background(barColor)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(day.dayName, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("${day.taken}/${day.total}", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
    }
}
