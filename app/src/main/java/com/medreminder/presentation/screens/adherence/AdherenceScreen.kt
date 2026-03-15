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
    private val repository: MedicationRepository
) : ViewModel() {

    private val _stats = MutableStateFlow(AdherenceStats())
    val stats: StateFlow<AdherenceStats> = _stats.asStateFlow()

    private val _period = MutableStateFlow("week")
    val period: StateFlow<String> = _period.asStateFlow()

    init { loadStats() }

    fun setPeriod(p: String) {
        _period.value = p
        loadStats()
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
