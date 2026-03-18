package com.medreminder.presentation.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.medreminder.R
import com.medreminder.ai.BodyRegion
import com.medreminder.domain.model.DoseStatus
import com.medreminder.domain.model.MedicationForm

/** Returns a translated display name for MedicationForm using string resources. */
@Composable
fun MedicationForm.translatedName(): String = when (this) {
    MedicationForm.PILL -> stringResource(R.string.form_pill)
    MedicationForm.CAPSULE -> stringResource(R.string.form_capsule)
    MedicationForm.LIQUID -> stringResource(R.string.form_liquid)
    MedicationForm.INJECTION -> stringResource(R.string.form_injection)
    MedicationForm.PATCH -> stringResource(R.string.form_patch)
    MedicationForm.SPRAY -> stringResource(R.string.form_spray)
    MedicationForm.DROPS -> stringResource(R.string.form_drops)
    MedicationForm.INHALER -> stringResource(R.string.form_inhaler)
    MedicationForm.CREAM -> stringResource(R.string.form_cream)
    MedicationForm.OTHER -> stringResource(R.string.form_other)
}

/** Returns a translated display name for DoseStatus using string resources. */
@Composable
fun DoseStatus.translatedName(): String = when (this) {
    DoseStatus.PENDING -> stringResource(R.string.status_pending)
    DoseStatus.TAKEN -> stringResource(R.string.status_taken)
    DoseStatus.SKIPPED -> stringResource(R.string.status_skipped)
    DoseStatus.SNOOZED -> stringResource(R.string.status_snoozed)
    DoseStatus.MISSED -> stringResource(R.string.status_missed)
}

/** Returns a translated display name for BodyRegion using string resources. */
@Composable
fun BodyRegion.translatedName(): String = when (this) {
    BodyRegion.HEAD -> stringResource(R.string.body_head)
    BodyRegion.EYES -> stringResource(R.string.body_eyes)
    BodyRegion.EARS -> stringResource(R.string.body_ears)
    BodyRegion.THROAT -> stringResource(R.string.body_throat)
    BodyRegion.HEART -> stringResource(R.string.body_heart)
    BodyRegion.LUNGS -> stringResource(R.string.body_lungs)
    BodyRegion.STOMACH -> stringResource(R.string.body_stomach)
    BodyRegion.LIVER -> stringResource(R.string.body_liver)
    BodyRegion.KIDNEYS -> stringResource(R.string.body_kidneys)
    BodyRegion.JOINTS -> stringResource(R.string.body_joints)
    BodyRegion.SKIN -> stringResource(R.string.body_skin)
    BodyRegion.BLOOD -> stringResource(R.string.body_blood)
    BodyRegion.IMMUNE -> stringResource(R.string.body_immune)
    BodyRegion.HORMONES -> stringResource(R.string.body_hormones)
    BodyRegion.NERVOUS_SYSTEM -> stringResource(R.string.body_nervous_system)
    BodyRegion.BONES -> stringResource(R.string.body_bones)
    BodyRegion.FULL_BODY -> stringResource(R.string.body_full_body)
}
