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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.medreminder.R
import com.medreminder.domain.model.Caregiver
import com.medreminder.domain.model.FamilyMember
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

    val familyMembers = repository.getActiveFamilyMembers()
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

    fun addFamilyMember(member: FamilyMember) {
        viewModelScope.launch { repository.addFamilyMember(member) }
    }

    fun deleteFamilyMember(member: FamilyMember) {
        viewModelScope.launch { repository.deleteFamilyMember(member) }
    }

    fun updateFamilyMember(member: FamilyMember) {
        viewModelScope.launch { repository.updateFamilyMember(member) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaregiverScreen(viewModel: CaregiverViewModel = hiltViewModel()) {
    val caregivers by viewModel.caregivers.collectAsStateWithLifecycle()
    val familyMembers by viewModel.familyMembers.collectAsStateWithLifecycle()
    var showAddCaregiverDialog by remember { mutableStateOf(false) }
    var showAddFamilyMemberDialog by remember { mutableStateOf(false) }
    var editingCaregiver by remember { mutableStateOf<Caregiver?>(null) }
    var editingFamilyMember by remember { mutableStateOf<FamilyMember?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) }

    if (showAddCaregiverDialog) {
        AddCaregiverDialog(
            onDismiss = { showAddCaregiverDialog = false },
            onSave = { viewModel.addCaregiver(it); showAddCaregiverDialog = false }
        )
    }

    if (editingCaregiver != null) {
        AddCaregiverDialog(
            existing = editingCaregiver,
            onDismiss = { editingCaregiver = null },
            onSave = { viewModel.updateCaregiver(it); editingCaregiver = null }
        )
    }

    if (showAddFamilyMemberDialog) {
        AddFamilyMemberDialog(
            onDismiss = { showAddFamilyMemberDialog = false },
            onSave = { viewModel.addFamilyMember(it); showAddFamilyMemberDialog = false }
        )
    }

    if (editingFamilyMember != null) {
        AddFamilyMemberDialog(
            existing = editingFamilyMember,
            onDismiss = { editingFamilyMember = null },
            onSave = { viewModel.updateFamilyMember(it); editingFamilyMember = null }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                stringResource(R.string.family_caregivers),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }

        // Tab row
        item {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(stringResource(R.string.family)) },
                    icon = { Icon(Icons.Default.FamilyRestroom, null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(stringResource(R.string.caregivers)) },
                    icon = { Icon(Icons.Default.HealthAndSafety, null, modifier = Modifier.size(18.dp)) }
                )
            }
        }

        if (selectedTab == 0) {
            // Family tab
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    FilledTonalButton(onClick = { showAddFamilyMemberDialog = true }) {
                        Icon(Icons.Default.PersonAdd, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.add))
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
                            stringResource(R.string.family_info),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            if (familyMembers.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67\u200D\uD83D\uDC66", style = MaterialTheme.typography.displayLarge)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(stringResource(R.string.no_family_members_yet), style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(R.string.add_family_members_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            items(familyMembers, key = { it.id }) { member ->
                FamilyMemberCard(
                    member = member,
                    onEdit = { editingFamilyMember = member },
                    onDelete = { viewModel.deleteFamilyMember(member) }
                )
            }
        } else {
            // Caregivers tab
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    FilledTonalButton(onClick = { showAddCaregiverDialog = true }) {
                        Icon(Icons.Default.PersonAdd, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.add))
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
                            stringResource(R.string.caregiver_info),
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
                            Text("\uD83D\uDC69\u200D\u2695\uFE0F", style = MaterialTheme.typography.displayLarge)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(stringResource(R.string.no_caregivers_yet), style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(R.string.add_family_members),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            items(caregivers, key = { it.id }) { caregiver ->
                CaregiverCard(
                    caregiver = caregiver,
                    onEdit = { editingCaregiver = caregiver },
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
}

@Composable
fun FamilyMemberCard(
    member: FamilyMember,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            member.name.take(1).uppercase(),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(member.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(R.string.age_years, member.age),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (member.relation.isNotBlank()) {
                        Text(
                            member.relation,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, stringResource(R.string.edit_family_member), tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun CaregiverCard(
    caregiver: Caregiver,
    onEdit: () -> Unit,
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
                    if (caregiver.email.isNotBlank()) {
                        Text(caregiver.email, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, stringResource(R.string.edit_caregiver), tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error)
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.notify_on_missed), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(checked = caregiver.notifyOnMissed, onCheckedChange = { onToggleMissed() })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.notify_on_taken), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(checked = caregiver.notifyOnTaken, onCheckedChange = { onToggleTaken() })
            }
        }
    }
}

@Composable
fun AddCaregiverDialog(
    existing: Caregiver? = null,
    onDismiss: () -> Unit,
    onSave: (Caregiver) -> Unit
) {
    val isEdit = existing != null
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var phone by remember { mutableStateOf(existing?.phone ?: "") }
    var email by remember { mutableStateOf(existing?.email ?: "") }
    var relationship by remember { mutableStateOf(existing?.relationship ?: "") }

    val isValid = name.isNotBlank() && phone.isNotBlank() && email.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (isEdit) R.string.edit_caregiver else R.string.add_caregiver))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text(stringResource(R.string.name)) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = phone, onValueChange = { phone = it },
                    label = { Text(stringResource(R.string.phone)) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = email, onValueChange = { email = it },
                    label = { Text(stringResource(R.string.email)) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = relationship, onValueChange = { relationship = it },
                    label = { Text(stringResource(R.string.relationship_optional)) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isValid) {
                        val caregiver = if (isEdit) {
                            existing!!.copy(
                                name = name.trim(),
                                phone = phone.trim(),
                                email = email.trim(),
                                relationship = relationship.trim()
                            )
                        } else {
                            Caregiver(
                                name = name.trim(),
                                phone = phone.trim(),
                                email = email.trim(),
                                relationship = relationship.trim()
                            )
                        }
                        onSave(caregiver)
                    }
                },
                enabled = isValid
            ) { Text(stringResource(if (isEdit) R.string.save_changes else R.string.add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFamilyMemberDialog(
    existing: FamilyMember? = null,
    onDismiss: () -> Unit,
    onSave: (FamilyMember) -> Unit
) {
    val isEdit = existing != null
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var age by remember { mutableStateOf(existing?.age?.toString() ?: "") }
    var selectedRelation by remember { mutableStateOf(existing?.relation ?: "") }
    var relationExpanded by remember { mutableStateOf(false) }

    val relations = listOf("Parent", "Child", "Spouse", "Sibling", "Other")
    val isValid = name.isNotBlank() && age.isNotBlank() && (age.toIntOrNull() ?: 0) > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (isEdit) R.string.edit_family_member else R.string.add_family_member))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text(stringResource(R.string.member_name)) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = age, onValueChange = { age = it },
                    label = { Text(stringResource(R.string.member_age)) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
                )
                ExposedDropdownMenuBox(
                    expanded = relationExpanded,
                    onExpandedChange = { relationExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedRelation,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.relation)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(relationExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = relationExpanded,
                        onDismissRequest = { relationExpanded = false }
                    ) {
                        relations.forEach { relation ->
                            DropdownMenuItem(
                                text = { Text(relation) },
                                onClick = {
                                    selectedRelation = relation
                                    relationExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isValid) {
                        val member = if (isEdit) {
                            existing!!.copy(
                                name = name.trim(),
                                age = age.toIntOrNull() ?: 0,
                                relation = selectedRelation
                            )
                        } else {
                            FamilyMember(
                                name = name.trim(),
                                age = age.toIntOrNull() ?: 0,
                                relation = selectedRelation
                            )
                        }
                        onSave(member)
                    }
                },
                enabled = isValid
            ) { Text(stringResource(if (isEdit) R.string.save_changes else R.string.add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
