package com.medreminder.presentation.screens.medanalysis

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.medreminder.R
import com.medreminder.ai.*
import com.medreminder.ai.local.MedicationAnalysisUseCase
import com.medreminder.domain.model.Medication
import com.medreminder.domain.repository.MedicationRepository
import com.medreminder.presentation.util.translatedName
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MedicationAnalysisViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: MedicationRepository,
    private val analysisUseCase: MedicationAnalysisUseCase
) : ViewModel() {

    private val medicationId: Long = savedStateHandle.get<Long>("medicationId") ?: 0L

    private val _medication = MutableStateFlow<Medication?>(null)
    val medication: StateFlow<Medication?> = _medication.asStateFlow()

    private val _analysisResult = MutableStateFlow<MedicationAnalysisResult?>(null)
    val analysisResult: StateFlow<MedicationAnalysisResult?> = _analysisResult.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        loadMedication()
        runAnalysis()
    }

    private fun loadMedication() {
        viewModelScope.launch {
            repository.getMedicationById(medicationId)?.let {
                _medication.value = it
            }
        }
    }

    fun runAnalysis() {
        viewModelScope.launch {
            _isAnalyzing.value = true
            _error.value = null
            try {
                _analysisResult.value = analysisUseCase.analyze(medicationId)
            } catch (e: Exception) {
                _error.value = e.message ?: "Analysis failed"
            }
            _isAnalyzing.value = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationAnalysisScreen(
    medicationId: Long,
    onNavigateBack: () -> Unit,
    viewModel: MedicationAnalysisViewModel = hiltViewModel()
) {
    val medication by viewModel.medication.collectAsStateWithLifecycle()
    val analysis by viewModel.analysisResult.collectAsStateWithLifecycle()
    val isAnalyzing by viewModel.isAnalyzing.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        medication?.name ?: stringResource(R.string.med_analysis_title),
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    if (analysis != null) {
                        IconButton(onClick = { viewModel.runAnalysis() }) {
                            Icon(Icons.Default.Refresh, stringResource(R.string.refresh_analysis))
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Loading state
            if (isAnalyzing && analysis == null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(
                            stringResource(R.string.analyzing_medication),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Error state
            error?.let { errMsg ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(errMsg, color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            // Medication basic info header
            medication?.let { med ->
                MedicationHeaderCard(med)
            }

            analysis?.let { result ->
                // Anatomical Body View
                AnatomyCard(result.bodyRegions)

                // Medication Info Section
                MedicationInfoCard(result.medicationInfo)

                // Dosing Predictions Section
                DosingPredictionsCard(result.dosingPredictions)

                // Provider info
                if (result.latencyMs > 0) {
                    val providerLabel = when (result.providerUsed) {
                        AiProviderType.CLOUD -> stringResource(R.string.provider_cloud)
                        AiProviderType.SYSTEM_AI -> stringResource(R.string.provider_on_device)
                        AiProviderType.CUSTOM_LOCAL -> stringResource(R.string.provider_local)
                        AiProviderType.AUTO -> stringResource(R.string.provider_auto)
                    }
                    Text(
                        stringResource(R.string.med_analysis_provider_info, providerLabel, result.latencyMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun MedicationHeaderCard(med: Medication) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Medication,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        med.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${med.dosage} ${med.dosageUnit} - ${med.form.translatedName()}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (med.instructions.isNotBlank()) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        Icons.Default.Info,
                        null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        med.instructions,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (med.isEmergency) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                ) {
                    Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Icon(
                            Icons.Default.Warning,
                            null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            stringResource(R.string.emergency_medication),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Schedule info
            if (med.schedules.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Schedule,
                        null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        med.schedules.joinToString(", ") { it.timeFormatted },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Stock info
            if (med.currentStock > 0) {
                val stockColor = when {
                    med.refillReminder && med.currentStock <= med.refillThreshold -> MaterialTheme.colorScheme.error
                    med.currentStock <= med.refillThreshold * 2 -> Color(0xFFF39C12)
                    else -> Color(0xFF2ECC71)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Inventory,
                        null,
                        modifier = Modifier.size(16.dp),
                        tint = stockColor
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.med_stock_remaining, med.currentStock),
                        style = MaterialTheme.typography.bodySmall,
                        color = stockColor,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun AnatomyCard(bodyRegions: List<BodyRegion>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SectionHeader(
                icon = Icons.Default.Accessibility,
                title = stringResource(R.string.med_body_targets)
            )

            AnatomyBodyView(
                highlightedRegions = bodyRegions,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun MedicationInfoCard(info: MedicationInfoSection) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionHeader(
                icon = Icons.Default.LocalPharmacy,
                title = stringResource(R.string.med_info_title)
            )

            // Description
            Text(info.description, style = MaterialTheme.typography.bodyMedium)

            // Drug class badge
            if (info.drugClass.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                ) {
                    Text(
                        info.drugClass,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }

            // Common uses
            if (info.commonUses.isNotEmpty()) {
                InfoListSection(
                    title = stringResource(R.string.med_common_uses),
                    items = info.commonUses,
                    icon = Icons.Default.CheckCircle,
                    iconColor = Color(0xFF2ECC71)
                )
            }

            // Side effects
            if (info.sideEffects.isNotEmpty()) {
                InfoListSection(
                    title = stringResource(R.string.med_side_effects),
                    items = info.sideEffects,
                    icon = Icons.Default.ReportProblem,
                    iconColor = Color(0xFFF39C12)
                )
            }

            // Important warnings
            if (info.importantWarnings.isNotEmpty()) {
                InfoListSection(
                    title = stringResource(R.string.med_warnings),
                    items = info.importantWarnings,
                    icon = Icons.Default.Warning,
                    iconColor = MaterialTheme.colorScheme.error
                )
            }

            // Interactions
            if (info.interactions.isNotEmpty()) {
                InfoListSection(
                    title = stringResource(R.string.med_interactions),
                    items = info.interactions,
                    icon = Icons.Default.SwapHoriz,
                    iconColor = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun DosingPredictionsCard(predictions: DosingPredictionSection) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionHeader(
                icon = Icons.Default.TrendingUp,
                title = stringResource(R.string.med_dosing_predictions)
            )

            // Adherence forecast
            if (predictions.adherenceForecast.isNotBlank()) {
                PredictionItem(
                    icon = Icons.Default.Analytics,
                    title = stringResource(R.string.med_adherence_forecast),
                    content = predictions.adherenceForecast
                )
            }

            // Stock forecast
            if (predictions.stockForecast.isNotBlank()) {
                PredictionItem(
                    icon = Icons.Default.Inventory,
                    title = stringResource(R.string.med_stock_forecast),
                    content = predictions.stockForecast
                )
            }

            // Risk assessment
            if (predictions.riskAssessment.isNotBlank()) {
                PredictionItem(
                    icon = Icons.Default.Shield,
                    title = stringResource(R.string.med_risk_assessment),
                    content = predictions.riskAssessment
                )
            }

            // Optimization tips
            if (predictions.optimizationTips.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text(
                    stringResource(R.string.med_optimization_tips),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                predictions.optimizationTips.forEach { tip ->
                    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(top = 2.dp)) {
                        Icon(
                            Icons.Default.Lightbulb,
                            null,
                            modifier = Modifier.size(14.dp),
                            tint = Color(0xFFF39C12)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            tip,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun InfoListSection(
    title: String,
    items: List<String>,
    icon: ImageVector,
    iconColor: Color
) {
    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
    Text(
        title,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = iconColor
    )
    items.forEach { item ->
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(top = 2.dp)) {
            Icon(icon, null, modifier = Modifier.size(14.dp), tint = iconColor)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                item,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PredictionItem(icon: ImageVector, title: String, content: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            icon, null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                content,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
