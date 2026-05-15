package med.reminder.com.presentation.screens.caregiver

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import coil.request.ImageRequest
import android.net.Uri
import med.reminder.com.R
import med.reminder.com.domain.model.Caregiver
import med.reminder.com.domain.model.FamilyMember
import med.reminder.com.domain.model.toEntity
import med.reminder.com.domain.repository.MedicationRepository
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
    var deleteConfirmationTarget by remember { mutableStateOf<Any?>(null) }

    // Delete confirmation dialog
    if (deleteConfirmationTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteConfirmationTarget = null },
            icon = { Icon(Icons.Default.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.delete)) },
            text = {
                Text(
                    when (deleteConfirmationTarget) {
                        is FamilyMember -> stringResource(R.string.delete) + " ${(deleteConfirmationTarget as FamilyMember).name}?"
                        is Caregiver -> stringResource(R.string.delete) + " ${(deleteConfirmationTarget as Caregiver).name}?"
                        else -> ""
                    },
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        when (val target = deleteConfirmationTarget) {
                            is FamilyMember -> viewModel.deleteFamilyMember(target)
                            is Caregiver -> viewModel.deleteCaregiver(target)
                        }
                        deleteConfirmationTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmationTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

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

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header with gradient accent
            item {
                Column {
                    Text(
                        stringResource(R.string.family_caregivers),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        if (selectedTab == 0)
                            "${familyMembers.size} " + stringResource(R.string.family).lowercase()
                        else
                            "${caregivers.size} " + stringResource(R.string.caregivers).lowercase(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Segmented button row
            item {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        icon = {
                            SegmentedButtonDefaults.Icon(active = selectedTab == 0) {
                                Icon(Icons.Default.FamilyRestroom, null, modifier = Modifier.size(18.dp))
                            }
                        }
                    ) { Text(stringResource(R.string.family)) }
                    SegmentedButton(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        icon = {
                            SegmentedButtonDefaults.Icon(active = selectedTab == 1) {
                                Icon(Icons.Default.HealthAndSafety, null, modifier = Modifier.size(18.dp))
                            }
                        }
                    ) { Text(stringResource(R.string.caregivers)) }
                }
            }

            // Animated content for tabs
            item {
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        if (targetState > initialState) {
                            slideInHorizontally { it / 3 } + fadeIn() togetherWith
                                    slideOutHorizontally { -it / 3 } + fadeOut()
                        } else {
                            slideInHorizontally { -it / 3 } + fadeIn() togetherWith
                                    slideOutHorizontally { it / 3 } + fadeOut()
                        }.using(SizeTransform(clip = false))
                    },
                    label = "tab_content"
                ) { tab ->
                    if (tab == 0) {
                        // Info card for family
                        InfoBanner(
                            icon = Icons.Outlined.Info,
                            text = stringResource(R.string.family_info),
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    } else {
                        // Info card for caregivers
                        InfoBanner(
                            icon = Icons.Outlined.NotificationsActive,
                            text = stringResource(R.string.caregiver_info),
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            if (selectedTab == 0) {
                // Family tab
                if (familyMembers.isEmpty()) {
                    item {
                        EmptyStateCard(
                            emoji = "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67\u200D\uD83D\uDC66",
                            title = stringResource(R.string.no_family_members_yet),
                            subtitle = stringResource(R.string.add_family_members_hint),
                            buttonText = stringResource(R.string.add_family_member),
                            onButtonClick = { showAddFamilyMemberDialog = true }
                        )
                    }
                }

                items(familyMembers, key = { it.id }) { member ->
                    FamilyMemberCard(
                        member = member,
                        onEdit = { editingFamilyMember = member },
                        onDelete = { deleteConfirmationTarget = member }
                    )
                }
            } else {
                // Caregivers tab
                if (caregivers.isEmpty()) {
                    item {
                        EmptyStateCard(
                            emoji = "\uD83D\uDC69\u200D\u2695\uFE0F",
                            title = stringResource(R.string.no_caregivers_yet),
                            subtitle = stringResource(R.string.add_family_members),
                            buttonText = stringResource(R.string.add_caregiver),
                            onButtonClick = { showAddCaregiverDialog = true }
                        )
                    }
                }

                items(caregivers, key = { it.id }) { caregiver ->
                    CaregiverCard(
                        caregiver = caregiver,
                        onEdit = { editingCaregiver = caregiver },
                        onDelete = { deleteConfirmationTarget = caregiver },
                        onToggleMissed = {
                            viewModel.updateCaregiver(caregiver.copy(notifyOnMissed = !caregiver.notifyOnMissed))
                        },
                        onToggleTaken = {
                            viewModel.updateCaregiver(caregiver.copy(notifyOnTaken = !caregiver.notifyOnTaken))
                        },
                        onToggleCancelled = {
                            viewModel.updateCaregiver(caregiver.copy(notifyOnCancelled = !caregiver.notifyOnCancelled))
                        }
                    )
                }
            }
        }

        // Floating Action Button
        ExtendedFloatingActionButton(
            onClick = {
                if (selectedTab == 0) showAddFamilyMemberDialog = true
                else showAddCaregiverDialog = true
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            icon = { Icon(Icons.Default.PersonAdd, null) },
            text = {
                Text(
                    stringResource(if (selectedTab == 0) R.string.add_family_member else R.string.add_caregiver)
                )
            }
        )
    }
}

@Composable
private fun InfoBanner(
    icon: ImageVector,
    text: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, null, tint = contentColor, modifier = Modifier.size(20.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
private fun EmptyStateCard(
    emoji: String,
    title: String,
    subtitle: String,
    buttonText: String,
    onButtonClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 40.dp, horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(emoji, style = MaterialTheme.typography.displayLarge)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            FilledTonalButton(
                onClick = onButtonClick,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(buttonText)
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
    val avatarColors = remember(member.name) {
        val hash = member.name.hashCode()
        val palette = listOf(
            0xFF6C63FF to 0xFFB8B3FF,  // Purple
            0xFF00BFA5 to 0xFF80E8D6,  // Teal
            0xFFFF6B6B to 0xFFFFB3B3,  // Coral
            0xFFFFAB40 to 0xFFFFD699,  // Amber
            0xFF42A5F5 to 0xFF90CAF9,  // Blue
            0xFFEC407A to 0xFFF48FB1,  // Pink
            0xFF66BB6A to 0xFFA5D6A7,  // Green
            0xFF7E57C2 to 0xFFB39DDB   // Deep purple
        )
        val pick = palette[Math.abs(hash) % palette.size]
        androidx.compose.ui.graphics.Color(pick.first) to androidx.compose.ui.graphics.Color(pick.second)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar with photo or gradient fallback
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(avatarColors.first, avatarColors.second)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (member.photoUri != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(Uri.parse(member.photoUri))
                                .crossfade(true)
                                .build(),
                            contentDescription = member.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            member.name.take(1).uppercase(),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = androidx.compose.ui.graphics.Color.White
                        )
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        member.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (member.relation.isNotBlank()) {
                        Text(
                            member.relation,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Outlined.Edit,
                        stringResource(R.string.edit_family_member),
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        stringResource(R.string.delete),
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Info chips row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 86.dp, end = 16.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = avatarColors.second.copy(alpha = 0.25f),
                    tonalElevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Cake,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = avatarColors.first
                        )
                        Text(
                            stringResource(R.string.age_years, member.age),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = avatarColors.first
                        )
                    }
                }
                if (member.relation.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        tonalElevation = 0.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Outlined.People,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                member.relation,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
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
    onToggleTaken: () -> Unit,
    onToggleCancelled: () -> Unit
) {
    val avatarColors = remember(caregiver.name) {
        val hash = caregiver.name.hashCode()
        val palette = listOf(
            0xFF42A5F5 to 0xFF90CAF9,  // Blue
            0xFF26A69A to 0xFF80CBC4,  // Teal
            0xFFAB47BC to 0xFFCE93D8,  // Purple
            0xFFEF5350 to 0xFFEF9A9A,  // Red
            0xFFFFA726 to 0xFFFFCC80,  // Orange
            0xFF66BB6A to 0xFFA5D6A7,  // Green
            0xFFEC407A to 0xFFF48FB1,  // Pink
            0xFF5C6BC0 to 0xFF9FA8DA   // Indigo
        )
        val pick = palette[Math.abs(hash) % palette.size]
        androidx.compose.ui.graphics.Color(pick.first) to androidx.compose.ui.graphics.Color(pick.second)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column {
            // Top row: avatar + info + actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(avatarColors.first, avatarColors.second)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (caregiver.photoUri != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(Uri.parse(caregiver.photoUri))
                                .crossfade(true)
                                .build(),
                            contentDescription = caregiver.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            caregiver.name.take(1).uppercase(),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = androidx.compose.ui.graphics.Color.White
                        )
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        caregiver.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (caregiver.relationship.isNotBlank()) {
                        Text(
                            caregiver.relationship,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Outlined.Edit,
                        stringResource(R.string.edit_caregiver),
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        stringResource(R.string.delete),
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Contact info chips
            if (caregiver.phone.isNotBlank() || caregiver.email.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 86.dp, end = 16.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (caregiver.phone.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = avatarColors.second.copy(alpha = 0.25f),
                            tonalElevation = 0.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.Phone,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = avatarColors.first
                                )
                                Text(
                                    caregiver.phone,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = avatarColors.first
                                )
                            }
                        }
                    }
                    if (caregiver.email.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            tonalElevation = 0.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.Email,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    caregiver.email,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Divider
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )

            // Notification preferences section
            Row(
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    Icons.Outlined.Notifications,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Notifications",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            NotificationToggleRow(
                icon = Icons.Outlined.NotificationImportant,
                label = stringResource(R.string.notify_on_missed),
                checked = caregiver.notifyOnMissed,
                onCheckedChange = { onToggleMissed() },
                activeColor = MaterialTheme.colorScheme.error
            )
            NotificationToggleRow(
                icon = Icons.Outlined.CheckCircle,
                label = stringResource(R.string.notify_on_taken),
                checked = caregiver.notifyOnTaken,
                onCheckedChange = { onToggleTaken() },
                activeColor = MaterialTheme.colorScheme.primary
            )
            NotificationToggleRow(
                icon = Icons.Outlined.Cancel,
                label = stringResource(R.string.notify_on_cancelled),
                checked = caregiver.notifyOnCancelled,
                onCheckedChange = { onToggleCancelled() },
                activeColor = MaterialTheme.colorScheme.tertiary
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun NotificationToggleRow(
    icon: ImageVector,
    label: String,
    checked: Boolean,
    onCheckedChange: () -> Unit,
    activeColor: androidx.compose.ui.graphics.Color
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (checked) activeColor.copy(alpha = 0.08f)
        else androidx.compose.ui.graphics.Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon, null,
                modifier = Modifier.size(18.dp),
                tint = if (checked) activeColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                color = if (checked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            Switch(
                checked = checked,
                onCheckedChange = { onCheckedChange() },
                modifier = Modifier.height(24.dp),
                colors = SwitchDefaults.colors(
                    checkedTrackColor = activeColor.copy(alpha = 0.4f),
                    checkedThumbColor = activeColor
                )
            )
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
    var photoUri by remember { mutableStateOf(existing?.photoUri) }

    val context = LocalContext.current
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            // Take persistent permission so the URI survives reboots
            context.contentResolver.takePersistableUriPermission(
                it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            photoUri = it.toString()
        }
    }

    val isValid = name.isNotBlank() && (phone.isNotBlank() || email.isNotBlank())

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            // Tappable photo avatar as dialog icon
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                if (photoUri != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(Uri.parse(photoUri))
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Outlined.CameraAlt,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                // Small edit badge
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(22.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        },
        title = {
            Text(
                stringResource(if (isEdit) R.string.edit_caregiver else R.string.add_caregiver),
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text(stringResource(R.string.name)) },
                    leadingIcon = { Icon(Icons.Default.Person, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)
                )
                OutlinedTextField(
                    value = phone, onValueChange = { phone = it },
                    label = { Text(stringResource(R.string.phone)) },
                    leadingIcon = { Icon(Icons.Default.Phone, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)
                )
                OutlinedTextField(
                    value = email, onValueChange = { email = it },
                    label = { Text(stringResource(R.string.email)) },
                    leadingIcon = { Icon(Icons.Default.Email, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)
                )
                OutlinedTextField(
                    value = relationship, onValueChange = { relationship = it },
                    label = { Text(stringResource(R.string.relationship_optional)) },
                    leadingIcon = { Icon(Icons.Default.Group, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)
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
                                relationship = relationship.trim(),
                                photoUri = photoUri
                            )
                        } else {
                            Caregiver(
                                name = name.trim(),
                                phone = phone.trim(),
                                email = email.trim(),
                                relationship = relationship.trim(),
                                photoUri = photoUri
                            )
                        }
                        onSave(caregiver)
                    }
                },
                enabled = isValid,
                shape = RoundedCornerShape(12.dp)
            ) { Text(stringResource(if (isEdit) R.string.save_changes else R.string.add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
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
    var photoUri by remember { mutableStateOf(existing?.photoUri) }

    val context = LocalContext.current
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            photoUri = it.toString()
        }
    }

    val relations = listOf("Parent", "Child", "Spouse", "Sibling", "Other")
    val isValid = name.isNotBlank() && age.isNotBlank() && (age.toIntOrNull() ?: 0) > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            // Tappable photo avatar as dialog icon
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                if (photoUri != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(Uri.parse(photoUri))
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Outlined.CameraAlt,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                // Small edit badge
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(22.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        },
        title = {
            Text(
                stringResource(if (isEdit) R.string.edit_family_member else R.string.add_family_member),
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text(stringResource(R.string.member_name)) },
                    leadingIcon = { Icon(Icons.Default.Person, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)
                )
                OutlinedTextField(
                    value = age, onValueChange = { age = it },
                    label = { Text(stringResource(R.string.member_age)) },
                    leadingIcon = { Icon(Icons.Default.Cake, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)
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
                        leadingIcon = { Icon(Icons.Default.Group, null) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(relationExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        shape = RoundedCornerShape(14.dp)
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
                                },
                                leadingIcon = {
                                    Icon(
                                        when (relation) {
                                            "Parent" -> Icons.Default.Person
                                            "Child" -> Icons.Default.ChildCare
                                            "Spouse" -> Icons.Default.Favorite
                                            "Sibling" -> Icons.Default.People
                                            else -> Icons.Default.Group
                                        },
                                        null,
                                        modifier = Modifier.size(20.dp)
                                    )
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
                                relation = selectedRelation,
                                photoUri = photoUri
                            )
                        } else {
                            FamilyMember(
                                name = name.trim(),
                                age = age.toIntOrNull() ?: 0,
                                relation = selectedRelation,
                                photoUri = photoUri
                            )
                        }
                        onSave(member)
                    }
                },
                enabled = isValid,
                shape = RoundedCornerShape(12.dp)
            ) { Text(stringResource(if (isEdit) R.string.save_changes else R.string.add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
