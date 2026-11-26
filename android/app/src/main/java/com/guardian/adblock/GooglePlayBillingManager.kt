package com.guardian.adblock

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

internal fun isSupportedGuardianSubscriptionRecurrenceMode(mode: Int): Boolean =
    mode == ProductDetails.RecurrenceMode.INFINITE_RECURRING ||
        mode == ProductDetails.RecurrenceMode.FINITE_RECURRING

internal fun shouldLaunchGuardianBillingFlow(managerClosed: Boolean, activityFinishing: Boolean, activityDestroyed: Boolean): Boolean =
    !managerClosed && !activityFinishing && !activityDestroyed

internal fun parseGuardianPlayProductIds(value: String): List<String> {
    val values = value.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    require(values.size <= 10) { "Google Play 구독 상품은 최대 10개까지 설정할 수 있습니다." }
    require(values.all { it.matches(Regex("[A-Za-z0-9_.-]{1,120}")) }) {
        "Google Play 구독 상품 ID 설정에 허용되지 않은 문자가 있습니다."
    }
    require(values.distinct().size == values.size) { "Google Play 구독 상품 ID가 중복되었습니다." }
    return values
}

internal data class GuardianPlayProduct(
    val productId: String,
    val title: String,
    val description: String,
    val formattedPrice: String,
    internal val details: ProductDetails,
    internal val offerToken: String
)

internal data class GuardianBillingOutcome(
    val success: Boolean,
    val message: String
)

/**
 * One BillingClient per Activity lifetime. All entitlement decisions are made
 * by the Guardian server after querying Google Play Developer API; client-side
 * purchase state is used only to decide whether verification may be attempted.
 */
internal class GooglePlayBillingManager(
    context: Context,
    private val productIds: List<String>,
    private val onPurchaseOutcome: (GuardianBillingOutcome) -> Unit
) : Closeable {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val connectionLock = Any()
    private val connectionCallbacks = mutableListOf<(BillingResult?) -> Unit>()
    private val processingTokens = ConcurrentHashMap.newKeySet<String>()
    @Volatile private var connected = false
    @Volatile private var closed = false
    private var connecting = false

    private val billingClient = BillingClient.newBuilder(appContext)
        .setListener { result, purchases -> onPurchasesUpdated(result, purchases.orEmpty()) }
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .enableAutoServiceReconnection()
        .build()

    fun queryProducts(callback: (List<GuardianPlayProduct>, String?) -> Unit) {
        if (closed) return
        if (productIds.isEmpty()) {
            callback(emptyList(), "Google Play 구독 상품 ID가 빌드 설정에 등록되지 않았습니다.")
            return
        }
        ensureConnected { connectionError ->
            if (connectionError != null) {
                callback(emptyList(), "Google Play 결제 서비스에 연결할 수 없습니다. (${connectionError.responseCode})")
                return@ensureConnected
            }
            val products = productIds.map { productId ->
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(productId)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            }
            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(products)
                .build()
            billingClient.queryProductDetailsAsync(params) { result, detailsResult ->
                if (closed) return@queryProductDetailsAsync
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    callback(emptyList(), "Google Play 상품 정보를 불러오지 못했습니다. (${result.responseCode})")
                    return@queryProductDetailsAsync
                }
                val available = detailsResult.productDetailsList.mapNotNull { details ->
                    val offer = details.subscriptionOfferDetails?.firstOrNull { candidate ->
                        val recurrenceMode = candidate.pricingPhases.pricingPhaseList.lastOrNull()?.recurrenceMode
                        recurrenceMode != null && isSupportedGuardianSubscriptionRecurrenceMode(recurrenceMode)
                    } ?: return@mapNotNull null
                    val price = offer.pricingPhases.pricingPhaseList.lastOrNull()?.formattedPrice
                        ?: return@mapNotNull null
                    GuardianPlayProduct(
                        productId = details.productId,
                        title = details.title,
                        description = details.description,
                        formattedPrice = price,
                        details = details,
                        offerToken = offer.offerToken
                    )
                }
                callback(available, null)
            }
        }
    }

    /** Query current subscriptions on app resume/restore; never trusts cached local purchases. */
    fun restorePurchases(silentIfEmpty: Boolean = false) {
        if (closed) return
        ensureConnected { connectionError ->
            if (connectionError != null) {
                onPurchaseOutcome(GuardianBillingOutcome(false, "Google Play 구매 내역을 조회할 수 없습니다."))
                return@ensureConnected
            }
            val params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .includeSuspendedSubscriptions(true)
                .build()
            billingClient.queryPurchasesAsync(params) { result, purchases ->
                if (closed) return@queryPurchasesAsync
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    onPurchaseOutcome(GuardianBillingOutcome(false, "구매 복원에 실패했습니다. (${result.responseCode})"))
                    return@queryPurchasesAsync
                }
                if (purchases.isEmpty()) {
                    if (!silentIfEmpty) {
                        onPurchaseOutcome(GuardianBillingOutcome(false, "복원할 Google Play 구독이 없습니다."))
                    }
                } else {
                    processPurchases(purchases)
                }
            }
        }
    }

    fun launchPurchase(activity: Activity, productId: String) {
        require(productId in productIds) { "Guardian에서 허용하지 않은 구독 상품입니다." }
        // ProductDetails and offer tokens are deliberately queried immediately
        // before launching; Google advises against long-lived ProductDetails caches.
        if (!shouldLaunchGuardianBillingFlow(closed, activity.isFinishing, activity.isDestroyed)) return
        queryProducts { products, error ->
            if (!shouldLaunchGuardianBillingFlow(closed, activity.isFinishing, activity.isDestroyed)) return@queryProducts
            if (error != null) {
                onPurchaseOutcome(GuardianBillingOutcome(false, error))
                return@queryProducts
            }
            val product = products.firstOrNull { it.productId == productId }
            if (product == null) {
                onPurchaseOutcome(GuardianBillingOutcome(false, "선택한 Google Play 구독 상품을 사용할 수 없습니다."))
                return@queryProducts
            }
            val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(product.details)
                .setOfferToken(product.offerToken)
                .build()
            val flow = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(productParams))
                .build()
            val result = billingClient.launchBillingFlow(activity, flow)
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                onPurchaseOutcome(
                    GuardianBillingOutcome(false, "Google Play 결제 화면을 열지 못했습니다. (${result.responseCode})")
                )
            }
        }
    }

    private fun ensureConnected(callback: (BillingResult?) -> Unit) {
        var startConnection = false
        synchronized(connectionLock) {
            if (closed) return
            if (billingClient.isReady) {
                connected = true
            } else {
                connectionCallbacks += callback
                if (!connecting) {
                    connecting = true
                    startConnection = true
                }
            }
        }
        if (billingClient.isReady) {
            callback(null)
            return
        }
        if (!startConnection) return
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                val callbacks = synchronized(connectionLock) {
                    connecting = false
                    if (closed) {
                        connectionCallbacks.clear()
                        emptyList()
                    } else {
                        connected = result.responseCode == BillingClient.BillingResponseCode.OK
                        connectionCallbacks.toList().also { connectionCallbacks.clear() }
                    }
                }
                callbacks.forEach { it(if (connected) null else result) }
            }

            override fun onBillingServiceDisconnected() {
                if (!closed) connected = false
            }
        })
    }

    private fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>) {
        if (closed) return
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> processPurchases(purchases)
            BillingClient.BillingResponseCode.USER_CANCELED ->
                onPurchaseOutcome(GuardianBillingOutcome(false, "Google Play 결제를 취소했습니다."))
            else -> onPurchaseOutcome(
                GuardianBillingOutcome(false, "Google Play 결제 처리에 실패했습니다. (${result.responseCode})")
            )
        }
    }

    private fun processPurchases(purchases: List<Purchase>) {
        if (closed) return
        if (purchases.isEmpty()) {
            onPurchaseOutcome(GuardianBillingOutcome(false, "확인할 Google Play 구매 내역이 없습니다."))
            return
        }
        purchases.forEach { purchase ->
            if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
                onPurchaseOutcome(GuardianBillingOutcome(false, "결제가 보류 중입니다. 결제가 완료되면 자동으로 다시 확인합니다."))
                return@forEach
            }
            if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return@forEach
            val productId = purchase.products.firstOrNull { it in productIds }
            if (productId == null) {
                onPurchaseOutcome(GuardianBillingOutcome(false, "Guardian에서 지원하지 않는 구매 상품입니다."))
                return@forEach
            }
            verifyAndAcknowledge(purchase, productId)
        }
    }

    private fun verifyAndAcknowledge(purchase: Purchase, productId: String) {
        if (!processingTokens.add(purchase.purchaseToken)) return
        thread(name = "guardian-google-play-verify") {
            try {
                if (closed) return@thread
                val response = LicenseApiClient.verifyGooglePlayPurchase(
                    appContext,
                    purchase.purchaseToken,
                    productId,
                    BuildConfig.VERSION_NAME
                )
                if (!response.optBoolean("entitled", false)) {
                    mainHandler.post {
                        if (closed) return@post
                        onPurchaseOutcome(
                            GuardianBillingOutcome(false, "Google Play에서 활성 PRO 구독을 확인하지 못했습니다.")
                        )
                    }
                    return@thread
                }
                if (purchase.isAcknowledged) {
                    mainHandler.post {
                        if (closed) return@post
                        onPurchaseOutcome(GuardianBillingOutcome(true, "Google Play 구독 확인 및 PRO 활성화가 완료되었습니다."))
                    }
                } else {
                    mainHandler.post {
                        if (closed) return@post
                        billingClient.acknowledgePurchase(
                            AcknowledgePurchaseParams.newBuilder()
                                .setPurchaseToken(purchase.purchaseToken)
                                .build()
                        ) { result ->
                            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                                onPurchaseOutcome(
                                    GuardianBillingOutcome(true, "구독 검증·PRO 활성화·Google Play 구매 확인이 완료되었습니다.")
                                )
                            } else {
                                onPurchaseOutcome(
                                    GuardianBillingOutcome(
                                        true,
                                        "PRO 권한은 검증되었지만 Google Play 구매 확인이 지연되었습니다. 잠시 후 구매 복원을 실행하세요."
                                    )
                                )
                            }
                        }
                    }
                }
            } catch (error: Exception) {
                mainHandler.post {
                    if (closed) return@post
                    onPurchaseOutcome(
                        GuardianBillingOutcome(false, "구독 서버 검증에 실패했습니다: ${SafeFailureSummary.describe(error)}")
                    )
                }
            } finally {
                processingTokens.remove(purchase.purchaseToken)
            }
        }
    }

    override fun close() {
        synchronized(connectionLock) {
            closed = true
            connectionCallbacks.clear()
            connecting = false
        }
        billingClient.endConnection()
        connected = false
    }
}
