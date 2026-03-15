package com.medreminder.util

import com.medreminder.domain.model.Medication

data class DrugInteraction(
    val drug1: String,
    val drug2: String,
    val severity: InteractionSeverity,
    val description: String
)

enum class InteractionSeverity(val label: String, val color: String) {
    MAJOR("Major", "#E74C3C"),
    MODERATE("Moderate", "#F39C12"),
    MINOR("Minor", "#3498DB")
}

object DrugInteractionChecker {

    // Common known interactions database (expandable)
    private val knownInteractions = listOf(
        DrugInteraction("warfarin", "aspirin", InteractionSeverity.MAJOR,
            "Increased risk of bleeding. Consult your doctor immediately."),
        DrugInteraction("warfarin", "ibuprofen", InteractionSeverity.MAJOR,
            "Increased risk of bleeding. Avoid combining without medical guidance."),
        DrugInteraction("lisinopril", "potassium", InteractionSeverity.MAJOR,
            "Risk of hyperkalemia (high potassium). Monitor potassium levels."),
        DrugInteraction("metformin", "alcohol", InteractionSeverity.MAJOR,
            "Risk of lactic acidosis. Avoid excessive alcohol."),
        DrugInteraction("simvastatin", "grapefruit", InteractionSeverity.MODERATE,
            "Grapefruit increases statin levels. Avoid grapefruit juice."),
        DrugInteraction("levothyroxine", "calcium", InteractionSeverity.MODERATE,
            "Calcium reduces thyroid hormone absorption. Take 4 hours apart."),
        DrugInteraction("levothyroxine", "iron", InteractionSeverity.MODERATE,
            "Iron reduces thyroid hormone absorption. Take 4 hours apart."),
        DrugInteraction("ciprofloxacin", "antacid", InteractionSeverity.MODERATE,
            "Antacids reduce absorption. Take ciprofloxacin 2 hours before or 6 hours after."),
        DrugInteraction("methotrexate", "ibuprofen", InteractionSeverity.MAJOR,
            "NSAIDs reduce methotrexate clearance. Risk of toxicity."),
        DrugInteraction("ssri", "maoi", InteractionSeverity.MAJOR,
            "Risk of serotonin syndrome. Do not combine."),
        DrugInteraction("fluoxetine", "tramadol", InteractionSeverity.MAJOR,
            "Risk of serotonin syndrome and seizures."),
        DrugInteraction("amlodipine", "simvastatin", InteractionSeverity.MODERATE,
            "Amlodipine increases simvastatin levels. Max simvastatin dose is 20mg."),
        DrugInteraction("omeprazole", "clopidogrel", InteractionSeverity.MAJOR,
            "Omeprazole reduces clopidogrel effectiveness. Consider pantoprazole instead."),
        DrugInteraction("metformin", "contrast dye", InteractionSeverity.MAJOR,
            "Stop metformin before contrast procedures. Risk of lactic acidosis."),
        DrugInteraction("ace inhibitor", "potassium", InteractionSeverity.MODERATE,
            "Risk of elevated potassium. Monitor levels regularly."),
        DrugInteraction("digoxin", "amiodarone", InteractionSeverity.MAJOR,
            "Amiodarone increases digoxin levels. Reduce digoxin dose by 50%."),
        DrugInteraction("lithium", "ibuprofen", InteractionSeverity.MAJOR,
            "NSAIDs increase lithium levels. Risk of toxicity."),
        DrugInteraction("warfarin", "vitamin k", InteractionSeverity.MODERATE,
            "Vitamin K reduces warfarin effectiveness. Keep vitamin K intake consistent."),
    )

    // SSRI names for group matching
    private val ssriNames = listOf("fluoxetine", "sertraline", "paroxetine", "citalopram",
        "escitalopram", "fluvoxamine", "prozac", "zoloft", "paxil", "lexapro")
    private val aceInhibitors = listOf("lisinopril", "enalapril", "ramipril", "captopril",
        "benazepril", "fosinopril", "quinapril", "perindopril")

    fun checkInteractions(medications: List<Medication>): List<DrugInteraction> {
        val interactions = mutableListOf<DrugInteraction>()
        val names = medications.filter { it.isActive }.map { it.name.lowercase().trim() }

        for (i in names.indices) {
            for (j in i + 1 until names.size) {
                val found = findInteraction(names[i], names[j])
                interactions.addAll(found)
            }
        }

        return interactions.distinctBy { "${it.drug1}-${it.drug2}" }
    }

    private fun findInteraction(name1: String, name2: String): List<DrugInteraction> {
        val results = mutableListOf<DrugInteraction>()

        // Expand group names
        val expanded1 = expandGroupNames(name1)
        val expanded2 = expandGroupNames(name2)

        for (known in knownInteractions) {
            val d1 = known.drug1.lowercase()
            val d2 = known.drug2.lowercase()

            val match = (expanded1.any { it.contains(d1) || d1.contains(it) } &&
                    expanded2.any { it.contains(d2) || d2.contains(it) }) ||
                    (expanded1.any { it.contains(d2) || d2.contains(it) } &&
                            expanded2.any { it.contains(d1) || d1.contains(it) })

            if (match) {
                results.add(known.copy(
                    drug1 = name1.replaceFirstChar { it.uppercase() },
                    drug2 = name2.replaceFirstChar { it.uppercase() }
                ))
            }
        }

        return results
    }

    private fun expandGroupNames(name: String): List<String> {
        val list = mutableListOf(name)
        if (ssriNames.any { name.contains(it) }) list.add("ssri")
        if (aceInhibitors.any { name.contains(it) }) list.add("ace inhibitor")
        return list
    }
}
