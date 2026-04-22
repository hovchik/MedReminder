package com.medreminder.billing

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.android.billingclient.api.*
import com.medreminder.domain.model.SubscriptionPlans
import com.medreminder.domain.model.SubscriptionTier
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BillingManager @Inject constructor(
    @ApplicationContext private val context: Context
) : PurchasesUpdatedListener {

    companion object {
        private const val TAG = "BillingManager"
        private const val MAX_RECONNECT_ATTEMPTS = 4
        private const val BASE_RECONNECT_DELAY_MS = 2_000L
    }

    private var billingClient: BillingClient? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var reconnectAttempts = 0

    private val _productDetails = MutableStateFlow<Map<String, ProductDetails>>(emptyMap())
    val productDetails: StateFlow<Map<String, ProductDetails>> = _productDetails.asStateFlow()

    private val _currentPurchases = MutableStateFlow<List<Purchase>>(emptyList())
    val currentPurchases: StateFlow<List<Purchase>> = _currentPurchases.asStateFlow()

    private val _billingConnectionState = MutableStateFlow(false)
    val billingConnectionState: StateFlow<Boolean> = _billingConnectionState.asStateFlow()

    fun initialize() {
        if (billingClient?.isReady == true) return

        billingClient = BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases()
            .build()

        connectToGooglePlay()
    }

    private fun connectToGooglePlay() {
        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Billing client connected")
                    _billingConnectionState.value = true
                    reconnectAttempts = 0
                    queryProductDetails()
                    queryExistingPurchases()
                } else {
                    Log.e(TAG, "Billing setup failed: ${billingResult.debugMessage}")
                    _billingConnectionState.value = false
                    scheduleReconnect()
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "Billing service disconnected")
                _billingConnectionState.value = false
                scheduleReconnect()
            }
        })
    }

    private fun scheduleReconnect() {
        if (billingClient == null) return
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            Log.e(TAG, "Giving up on billing reconnection after $reconnectAttempts attempts")
            return
        }
        val delay = BASE_RECONNECT_DELAY_MS shl reconnectAttempts
        reconnectAttempts++
        mainHandler.postDelayed({
            if (billingClient?.isReady != true) connectToGooglePlay()
        }, delay)
    }

    private fun queryProductDetails() {
        val productList = SubscriptionPlans.allProductIds.map { productId ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient?.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val detailsMap = productDetailsList.associateBy { it.productId }
                _productDetails.value = detailsMap
                Log.d(TAG, "Loaded ${detailsMap.size} product details")
            } else {
                Log.e(TAG, "Failed to query product details: ${billingResult.debugMessage}")
            }
        }
    }

    fun queryExistingPurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        billingClient?.queryPurchasesAsync(params) { billingResult, purchasesList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                // Only treat PURCHASED purchases as active; ignore PENDING / UNSPECIFIED.
                val active = purchasesList.filter {
                    it.purchaseState == Purchase.PurchaseState.PURCHASED
                }
                _currentPurchases.value = active
                // Acknowledge any unacknowledged purchases
                active.forEach { purchase ->
                    if (!purchase.isAcknowledged) {
                        acknowledgePurchase(purchase)
                    }
                }
                Log.d(TAG, "Found ${active.size} active subscriptions (of ${purchasesList.size} total)")
            }
        }
    }

    fun launchPurchaseFlow(activity: Activity, tier: SubscriptionTier): Boolean {
        val productId = tier.productId ?: return false
        val details = _productDetails.value[productId] ?: return false

        val offerToken = details.subscriptionOfferDetails?.firstOrNull()?.offerToken
            ?: return false

        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .setOfferToken(offerToken)
            .build()

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))
            .build()

        val result = billingClient?.launchBillingFlow(activity, billingFlowParams)
        return result?.responseCode == BillingClient.BillingResponseCode.OK
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { purchase ->
                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        acknowledgePurchase(purchase)
                    }
                }
                queryExistingPurchases()
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                Log.d(TAG, "User canceled purchase")
            }
            else -> {
                Log.e(TAG, "Purchase failed: ${billingResult.debugMessage}")
            }
        }
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        if (purchase.isAcknowledged) return

        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        billingClient?.acknowledgePurchase(params) { billingResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                Log.d(TAG, "Purchase acknowledged")
                queryExistingPurchases()
            } else {
                Log.e(TAG, "Failed to acknowledge purchase: ${billingResult.debugMessage}")
            }
        }
    }

    /**
     * Get the formatted price string from Google Play for a given tier.
     */
    fun getFormattedPrice(tier: SubscriptionTier): String? {
        val productId = tier.productId ?: return null
        val details = _productDetails.value[productId] ?: return null
        return details.subscriptionOfferDetails?.firstOrNull()
            ?.pricingPhases?.pricingPhaseList?.firstOrNull()
            ?.formattedPrice
    }

    fun endConnection() {
        mainHandler.removeCallbacksAndMessages(null)
        billingClient?.endConnection()
        billingClient = null
        _billingConnectionState.value = false
        reconnectAttempts = 0
    }
}
