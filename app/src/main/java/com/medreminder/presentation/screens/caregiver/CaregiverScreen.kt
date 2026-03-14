package com.medreminder.presentation.screens.caregiver

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.medreminder.domain.model.Caregiver
import com.medreminder.domain.model.toEntity
import com.medreminder.domain.repository.MedicationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CaregiverViewModel @Inject constructor(
    private val repository: MedicationRepository
) : ViewModel() {

    val caregivers = repository.getActiveCaregivers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addCaregiver(caregiver: Caregiver) {
        viewModelScope.launch { repository.addCaregiver(caregiver) }
    }

    fun deleteCaregiver(caregiver: Caregiver) {
        viewModelScope.launch { repository.deleteCaregiver(caregiver) }
    }

    fun updateCaregiver(caregiver: Caregiver) {
        viewModelScope.launch { repository.updateCaregiver(caregiver) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaregiverScreen(viewModel: CaregiverViewModel = hiltViewModel()) {
    val caregivers by viewModel.caregivers.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }

    if (showAddDialog) {
        AddCaregiverDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { viewModel.addCaregiver(it); showAddDialog = false }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Family & Caregivers", style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold)
                FilledTonalButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.PersonAdd, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add")
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "Caregivers will be notified when you miss a dose. They can also view your adherence reports.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        if (caregivers.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("👨‍👩‍👧‍👦", style = MaterialTheme.typography.displayLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No caregivers added yet", style = MaterialTheme.typography.titleMedium)
                        Text("Add family members who should be notified",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        items(caregivers, key = { it.id }) { caregiver ->
            CaregiverCard(
                caregiver = caregiver,
                onDelete = { viewModel.deleteCaregiver(caregiver) },
                onToggleMissed = {
                    viewModel.updateCaregiver(caregiver.copy(notifyOnMissed = !caregiver.notifyOnMissed))
                },
                onToggleTaken = {
                    viewModel.updateCaregiver(caregiver.copy(notifyOnTaken = !caregiver.notifyOnTaken))
                }
            )
        }
    }
}

@Composable
fun CaregiverCard(
    caregiver: Caregiver,
    onDelete: () -> Unit,
    onToggleMissed: () -> Unit,
    onToggleTaken: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            caregiver.name.take(1).uppercase(),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(caregiver.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (caregiver.relationship.isNotBlank()) {
                        Text(caregiver.relationship, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (caregiver.phone.isNotBlank()) {
                        Text(caregiver.phone, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Notify on missed", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(checked = caregiver.notifyOnMissed, onCheckedChange = { onToggleMissed() })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Notify on taken", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(checked = caregiver.notifyOnTaken, onCheckedChange = { onToggleTaken() })
            }
        }
    }
}

@Composable
fun AddCaregiverDialog(onDismiss: () -> Unit, onAdd: (Caregiver) -> Unit) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var relationship by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add caregiver") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Name") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = phone, onValueChange = { phone = it },
                    label = { Text("Phone (optional)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = relationship, onValueChange = { relationship = it },
                    label = { Text("Relationship (optional)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onAdd(Caregiver(name = name.trim(), phone = phone.trim(), relationship = relationship.trim()))
                    }
                },
                enabled = name.isNotBlank()
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
