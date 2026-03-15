package com.medreminder.presentation.screens.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.medreminder.domain.model.DoseLog
import com.medreminder.domain.model.DoseStatus
import com.medreminder.domain.repository.MedicationRepository
import com.medreminder.util.DateUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: MedicationRepository
) : ViewModel() {

    private val _logs = MutableStateFlow<List<DoseLog>>(emptyList())
    val logs: StateFlow<List<DoseLog>> = _logs.asStateFlow()

    private val _daysBack = MutableStateFlow(7)
    val daysBack: StateFlow<Int> = _daysBack.asStateFlow()

    init { loadHistory() }

    fun setDaysBack(days: Int) {
        _daysBack.value = days
        loadHistory()
    }

    private fun loadHistory() {
        viewModelScope.launch {
            val start = DateUtils.daysAgo(_daysBack.value)
            val end = DateUtils.getEndOfDay()
            repository.getDoseLogsForDateRange(start, end).collect { list ->
                _logs.value = list.sortedByDescending { it.scheduledTime }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: HistoryViewModel = hiltViewModel()) {
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val daysBack by viewModel.daysBack.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Column(modifier = Modifier.padding(16.dp)) {
            Text("History", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(7 to "7 days", 14 to "14 days", 30 to "30 days").forEach { (days, label) ->
                    FilterChip(
                        selected = daysBack == days,
                        onClick = { viewModel.setDaysBack(days) },
                        label = { Text(label) },
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }
        }

        // Group by date
        val grouped = logs.groupBy {
            SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date(it.scheduledTime))
        }

        if (logs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📋", style = MaterialTheme.typography.displayLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No history yet", style = MaterialTheme.typography.titleMedium)
                    Text("Your medication history will appear here",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                grouped.forEach { (date, dayLogs) ->
                    item {
                        Text(
                            text = if (DateUtils.isToday(dayLogs.first().scheduledTime)) "Today" else date,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    items(dayLogs, key = { it.id }) { log ->
                        HistoryItem(log)
                    }
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
fun HistoryItem(log: DoseLog) {
    val (icon, iconColor) = when (log.status) {
        DoseStatus.TAKEN -> Icons.Default.CheckCircle to Color(0xFF2ECC71)
        DoseStatus.MISSED -> Icons.Default.Cancel to MaterialTheme.colorScheme.error
        DoseStatus.SKIPPED -> Icons.Default.RemoveCircle to Color(0xFFF39C12)
        DoseStatus.SNOOZED -> Icons.Default.Snooze to Color(0xFFF39C12)
        DoseStatus.PENDING -> Icons.Default.Schedule to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = iconColor, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(log.medicationName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(log.medicationDosage, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(DateUtils.formatTimeOnly(log.scheduledTime),
                style = MaterialTheme.typography.bodyMedium)
            Text(log.status.displayName,
                style = MaterialTheme.typography.labelSmall,
                color = iconColor)
        }
    }
}
