package med.reminder.com.presentation.screens.legal

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.ui.graphics.vector.ImageVector
import med.reminder.com.R

enum class LegalDocumentType(
    val titleRes: Int,
    val subtitleRes: Int,
    val bodyRes: Int,
    val icon: ImageVector
) {
    TERMS_OF_USE(
        titleRes = R.string.legal_terms_of_use,
        subtitleRes = R.string.legal_terms_subtitle,
        bodyRes = R.string.terms_of_use_body,
        icon = Icons.Default.Description
    ),
    PRIVACY_POLICY(
        titleRes = R.string.legal_privacy_policy,
        subtitleRes = R.string.legal_privacy_subtitle,
        bodyRes = R.string.privacy_policy_body,
        icon = Icons.Default.PrivacyTip
    ),
    MEDICAL_DISCLAIMER(
        titleRes = R.string.legal_medical_disclaimer,
        subtitleRes = R.string.legal_disclaimer_subtitle,
        bodyRes = R.string.medical_disclaimer_body,
        icon = Icons.Default.LocalHospital
    )
}
