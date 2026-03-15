package com.medreminder.presentation.screens.ocr

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Looks up medication info from barcodes using the OpenFDA API.
 * Supports UPC-A, EAN-13, and NDC (National Drug Code) formats.
 *
 * The OpenFDA API is free, requires no API key, and provides
 * drug labeling data from the FDA database.
 */
object BarcodeLookupService {

    private const val TAG = "BarcodeLookup"
    private const val OPENFDA_BASE = "https://api.fda.gov/drug/ndc.json"

    data class MedicationInfo(
        val name: String,
        val dosage: String,
        val form: String = ""
    )

    /**
     * Looks up medication information from a barcode value.
     *
     * UPC-A barcodes on US medications typically encode the NDC
     * (National Drug Code) in their digits. This method tries
     * multiple NDC format derivations from the barcode.
     *
     * @return medication info if found, null otherwise
     */
    suspend fun lookup(barcodeValue: String): MedicationInfo? = withContext(Dispatchers.IO) {
        val cleaned = barcodeValue.replace(Regex("[^0-9]"), "")
        if (cleaned.length < 10) return@withContext null

        // Try different NDC derivations from the barcode
        val ndcCandidates = deriveNdcCandidates(cleaned)

        for (ndc in ndcCandidates) {
            try {
                val result = queryOpenFda(ndc)
                if (result != null) return@withContext result
            } catch (e: Exception) {
                Log.d(TAG, "NDC lookup failed for $ndc: ${e.message}")
            }
        }

        // Fallback: search by the raw barcode number as a product code
        try {
            val result = queryOpenFdaByPackageNdc(cleaned)
            if (result != null) return@withContext result
        } catch (e: Exception) {
            Log.d(TAG, "Package NDC lookup failed: ${e.message}")
        }

        null
    }

    /**
     * Derives possible NDC codes from a UPC-A or EAN-13 barcode.
     *
     * UPC-A (12 digits): digits 2-11 contain the NDC in one of three formats:
     *   - 4-4-2: labeler(4)-product(4)-package(2)
     *   - 5-3-2: labeler(5)-product(3)-package(2)
     *   - 5-4-1: labeler(5)-product(4)-package(1)
     *
     * EAN-13 (13 digits): first digit is typically 0 for US products,
     * remaining 12 digits follow UPC-A format.
     */
    private fun deriveNdcCandidates(barcode: String): List<String> {
        val digits = when {
            barcode.length == 13 && barcode.startsWith("0") -> barcode.substring(1)
            barcode.length == 12 -> barcode
            barcode.length == 11 -> barcode // Already an NDC
            barcode.length == 10 -> barcode // Already an NDC without check digit
            else -> return listOf(barcode)
        }

        // For 12-digit UPC-A, NDC is in digits 1-10 (skip first and last)
        val ndcDigits = if (digits.length == 12) digits.substring(1, 11) else digits.take(10)

        if (ndcDigits.length < 10) return listOf(ndcDigits)

        // Three standard NDC formats from 10 digits
        return listOf(
            "${ndcDigits.substring(0, 4)}-${ndcDigits.substring(4, 8)}-${ndcDigits.substring(8, 10)}",
            "${ndcDigits.substring(0, 5)}-${ndcDigits.substring(5, 8)}-${ndcDigits.substring(8, 10)}",
            "${ndcDigits.substring(0, 5)}-${ndcDigits.substring(5, 9)}-${ndcDigits.substring(9, 10)}",
            ndcDigits  // raw 10-digit form
        )
    }

    private fun queryOpenFda(ndc: String): MedicationInfo? {
        val url = URL("$OPENFDA_BASE?search=product_ndc:\"$ndc\"&limit=1")
        return fetchAndParse(url)
    }

    private fun queryOpenFdaByPackageNdc(barcode: String): MedicationInfo? {
        val url = URL("$OPENFDA_BASE?search=package_ndc:\"$barcode\"&limit=1")
        return fetchAndParse(url)
    }

    private fun fetchAndParse(url: URL): MedicationInfo? {
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.setRequestProperty("Accept", "application/json")

        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return null
            }

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val results = json.optJSONArray("results") ?: return null
            if (results.length() == 0) return null

            val product = results.getJSONObject(0)

            // Extract brand/generic name
            val brandName = product.optString("brand_name", "")
            val genericName = product.optString("generic_name", "")
            val name = brandName.ifBlank { genericName }
            if (name.isBlank()) return null

            // Extract dosage from active_ingredients
            var dosage = ""
            val ingredients = product.optJSONArray("active_ingredients")
            if (ingredients != null && ingredients.length() > 0) {
                val firstIngredient = ingredients.getJSONObject(0)
                val strength = firstIngredient.optString("strength", "")
                if (strength.isNotBlank()) {
                    dosage = strength
                }
            }

            // Extract dosage form
            val dosageForm = product.optString("dosage_form", "")

            val displayName = if (brandName.isNotBlank() && genericName.isNotBlank()
                && brandName.lowercase() != genericName.lowercase()) {
                "$brandName ($genericName)"
            } else {
                name
            }

            Log.d(TAG, "Found medication: $displayName, dosage: $dosage, form: $dosageForm")
            return MedicationInfo(
                name = displayName.lowercase()
                    .replaceFirstChar { it.uppercase() },
                dosage = dosage,
                form = dosageForm
            )
        } finally {
            connection.disconnect()
        }
    }
}
