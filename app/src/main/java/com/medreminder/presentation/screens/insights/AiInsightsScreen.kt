package com.medreminder.presentation.screens.insights

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.medreminder.domain.repository.MedicationRepository
import com.medreminder.util.DateUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AiInsightsViewModel @Inject constructor(
    private val repository: MedicationRepository,
    private val dailyAnalysisUseCase: DailyAnalysisUseCase,
    private val weeklyReportUseCase: WeeklyReportUseCase
) : ViewModel() {

    private val _dailyReport = MutableStateFlow<AnalysisResult?>(null)
    val dailyReport: StateFlow<AnalysisResult?> = _dailyReport.asStateFlow()

    private val _isDailyAnalyzing = MutableStateFlow(false)
    val isDailyAnalyzing: StateFlow<Boolean> = _isDailyAnalyzing.asStateFlow()

    private val _dailyError = MutableStateFlow<String?>(null)
    val dailyError: StateFlow<String?> = _dailyError.asStateFlow()

    private val _weeklyReport = MutableStateFlow<AnalysisResult?>(null)
    val weeklyReport: StateFlow<AnalysisResult?> = _weeklyReport.asStateFlow()

    private val _isWeeklyAnalyzing = MutableStateFlow(false)
    val isWeeklyAnalyzing: StateFlow<Boolean> = _isWeeklyAnalyzing.asStateFlow()

    private val _weeklyError = MutableStateFlow<String?>(null)
    val weeklyError: StateFlow<String?> = _weeklyError.asStateFlow()

    private val _todayStats = MutableStateFlow(AdherenceStats())
    val todayStats: StateFlow<AdherenceStats> = _todayStats.asStateFlow()

    private val _weekStats = MutableStateFlow(AdherenceStats())
    val weekStats: StateFlow<AdherenceStats> = _weekStats.asStateFlow()

    init {
        loadStats()
    }

    fun runDailyAnalysis() {
        viewModelScope.launch {
            _isDailyAnalyzing.value = true
            _dailyError.value = null
            try {
                _dailyReport.value = dailyAnalysisUseCase.analyze()
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
                _weeklyReport.value = weeklyReportUseCase.analyze()
            } catch (e: Exception) {
                _weeklyError.value = e.message ?: "Weekly analysis failed"
            }
            _isWeeklyAnalyzing.value = false
        }
    }

    private fun loadStats() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            _todayStats.value = repository.getAdherenceStats(DateUtils.getStartOfDay(), now)
            _weekStats.value = repository.getAdherenceStats(DateUtils.daysAgo(7), now)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiInsightsScreen(
    onNavigateBack: () -> Unit,
    viewModel: AiInsightsViewModel = hiltViewModel()
) {
    val dailyReport by viewModel.dailyReport.collectAsStateWithLifecycle()
    val isDailyAnalyzing by viewModel.isDailyAnalyzing.collectAsStateWithLifecycle()
    val dailyError by viewModel.dailyError.collectAsStateWithLifecycle()
    val weeklyReport by viewModel.weeklyReport.collectAsStateWithLifecycle()
    val isWeeklyAnalyzing by viewModel.isWeeklyAnalyzing.collectAsStateWithLifecycle()
    val weeklyError by viewModel.weeklyError.collectAsStateWithLifecycle()
    val todayStats by viewModel.todayStats.collectAsStateWithLifecycle()
    val weekStats by viewModel.weekStats.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Psychology,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            stringResource(R.string.ai_insights_title),
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Quick stats overview
            QuickStatsRow(todayStats = todayStats, weekStats = weekStats)

            // Privacy note
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        stringResource(R.string.ai_insights_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Daily Insight Section
            InsightSection(
                title = stringResource(R.string.ai_daily_insight),
                subtitle = stringResource(R.string.daily_insight_subtitle),
                icon = Icons.Default.Today,
                iconTint = Color(0xFF4A90D9),
                analysis = dailyReport,
                isAnalyzing = isDailyAnalyzing,
                error = dailyError,
                onRunAnalysis = { viewModel.runDailyAnalysis() }
            )

            // Weekly Insight Section
            InsightSection(
                title = stringResource(R.string.ai_weekly_insight),
                subtitle = stringResource(R.string.weekly_insight_subtitle),
                icon = Icons.Default.DateRange,
                iconTint = Color(0xFF9B59B6),
                analysis = weeklyReport,
                isAnalyzing = isWeeklyAnalyzing,
                error = weeklyError,
                onRunAnalysis = { viewModel.runWeeklyAnalysis() }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun QuickStatsRow(todayStats: AdherenceStats, weekStats: AdherenceStats) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        QuickStatCard(
            label = stringResource(R.string.today_adherence),
            value = "${todayStats.adherenceRate.toInt()}%",
            color = when {
                todayStats.adherenceRate >= 90 -> Color(0xFF2ECC71)
                todayStats.adherenceRate >= 70 -> Color(0xFFF39C12)
                else -> Color(0xFFE74C3C)
            },
            modifier = Modifier.weight(1f)
        )
        QuickStatCard(
            label = stringResource(R.string.week_adherence),
            value = "${weekStats.adherenceRate.toInt()}%",
            color = when {
                weekStats.adherenceRate >= 90 -> Color(0xFF2ECC71)
                weekStats.adherenceRate >= 70 -> Color(0xFFF39C12)
                else -> Color(0xFFE74C3C)
            },
            modifier = Modifier.weight(1f)
        )
        QuickStatCard(
            label = stringResource(R.string.current_streak),
            value = "${weekStats.currentStreak}",
            color = Color(0xFF4A90D9),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun QuickStatCard(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun InsightSection(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    analysis: AnalysisResult?,
    isAnalyzing: Boolean,
    error: String?,
    onRunAnalysis: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Section header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(iconTint.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (analysis != null) {
                    val providerLabel = when (analysis.providerUsed) {
                        AiProviderType.CLOUD -> stringResource(R.string.provider_cloud)
                        AiProviderType.SYSTEM_AI -> stringResource(R.string.provider_on_device)
                        AiProviderType.CUSTOM_LOCAL -> stringResource(R.string.provider_local)
                        AiProviderType.AUTO -> stringResource(R.string.provider_auto)
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            providerLabel,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            if (isAnalyzing) {
                // Loading state
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 3.dp,
                        color = iconTint
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.analyzing),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (error != null) {
                // Error state
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
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
                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.refresh_analysis))
                }
            } else if (analysis != null) {
                // Risk level badge
                val riskColor = when (analysis.riskLevel) {
                    RiskLevel.LOW -> Color(0xFF2ECC71)
                    RiskLevel.MODERATE -> Color(0xFFF39C12)
                    RiskLevel.HIGH -> MaterialTheme.colorScheme.error
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = riskColor.copy(alpha = 0.12f)
                ) {
                    val riskLabel = when (analysis.riskLevel) {
                        RiskLevel.LOW -> stringResource(R.string.risk_low)
                        RiskLevel.MODERATE -> stringResource(R.string.risk_moderate)
                        RiskLevel.HIGH -> stringResource(R.string.risk_high)
                    }
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            when (analysis.riskLevel) {
                                RiskLevel.LOW -> Icons.Default.CheckCircle
                                RiskLevel.MODERATE -> Icons.Default.Warning
                                RiskLevel.HIGH -> Icons.Default.Error
                            },
                            contentDescription = null,
                            tint = riskColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "${stringResource(R.string.med_risk_assessment)}: $riskLabel",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = riskColor
                        )
                    }
                }

                // Cloud error warning
                if (!analysis.cloudError.isNullOrEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFFFF3E0)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = Color(0xFFF57C00),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                analysis.cloudError,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFE65100)
                            )
                        }
                    }
                }

                // Summary
                Text(
                    analysis.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 22.sp
                )

                // Insights
                if (analysis.insights.isNotEmpty()) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        stringResource(R.string.insights_header),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    analysis.insights.forEach { insight ->
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Icon(
                                Icons.Default.Insights,
                                null,
                                modifier = Modifier
                                    .size(16.dp)
                                    .padding(top = 2.dp),
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                insight,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }

                // Recommendations
                if (analysis.recommendations.isNotEmpty()) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        stringResource(R.string.recommendations_header),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFF39C12)
                    )
                    analysis.recommendations.forEach { rec ->
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Icon(
                                Icons.Default.Lightbulb,
                                null,
                                modifier = Modifier
                                    .size(16.dp)
                                    .padding(top = 2.dp),
                                tint = Color(0xFFF39C12)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                rec,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 20.sp
                            )
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
                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.refresh_analysis))
                }
            } else {
                // Initial state - prompt to run analysis
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(
                        onClick = onRunAnalysis,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = iconTint
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.get_ai_insight))
                    }
                }
            }
        }
    }
}
