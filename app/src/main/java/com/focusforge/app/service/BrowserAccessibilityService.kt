package com.focusforge.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.focusforge.app.BlockedAppActivity
import com.focusforge.app.data.model.DomainCategory
import com.focusforge.app.data.model.PolicySnapshot
import com.focusforge.app.data.repository.PolicyRepository
import com.focusforge.app.enforcement.OnDeviceContentInspector
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * On-Device Browser Content & Query Inspector using Android AccessibilityService.
 * Zero data leaves the device.
 *
 * Inspects:
 * 1. URL address bar text
 * 2. Search queries typed or submitted by the user
 * 3. Page title and main heading metadata
 *
 * High-performance optimizations:
 * - Scoped strictly to known browser package names (Chrome, Firefox, Edge, Brave, Opera, Samsung, UC, DuckDuckGo, etc.).
 * - Debounces text updates (300ms) to conserve CPU and battery.
 * - Respects the user's custom Allowlist immediately before any keyword or adult checks.
 * - When adult content is detected, instantly interrupts navigation and opens BlockedAppActivity.
 */
@AndroidEntryPoint
class BrowserAccessibilityService : AccessibilityService() {

    @Inject
    lateinit var contentInspector: OnDeviceContentInspector

    @Inject
    lateinit var policyRepository: PolicyRepository

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private val handler = Handler(Looper.getMainLooper())
    private var currentPolicy = PolicySnapshot()

    private var lastInspectedText = ""
    private var lastInspectionTime = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        _isAccessibilityRunning.value = true
        Log.i(TAG, "FocusForge BrowserAccessibilityService connected and ready")

        serviceScope.launch {
            policyRepository.policySnapshot.collect { snapshot ->
                currentPolicy = snapshot
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // 1. Check if adult content shield is enabled in policy
        if (!currentPolicy.enabledDomainCategories.contains(DomainCategory.ADULT)) {
            return
        }

        // 2. Battery Optimization: Filter strictly for browser package names
        val packageName = event.packageName?.toString() ?: return
        if (!SUPPORTED_BROWSER_PACKAGES.contains(packageName)) {
            return
        }

        // 3. Process relevant UI events: Window state changed, Window content changed, or View text changed
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                val now = System.currentTimeMillis()
                // Throttle / debounce (250ms) to avoid CPU churn during smooth scrolling or animations
                if (now - lastInspectionTime < 250) return
                lastInspectionTime = now

                val rootNode = rootInActiveWindow ?: return
                inspectNodeHierarchy(rootNode, packageName)
            }
        }
    }

    private fun inspectNodeHierarchy(rootNode: AccessibilityNodeInfo, packageName: String) {
        try {
            // Find address bar / search input node
            val urlOrSearchText = extractUrlOrSearchText(rootNode)
            val pageTitle = extractPageTitle(rootNode)

            val textToEvaluate = urlOrSearchText ?: pageTitle ?: return
            if (textToEvaluate == lastInspectedText) return
            lastInspectedText = textToEvaluate

            // On-Device analysis
            val result = contentInspector.inspectContent(
                rawText = textToEvaluate,
                pageTitle = pageTitle,
                customAllowedDomains = currentPolicy.customAllowedDomains,
                customBlockedDomains = currentPolicy.customBlockedDomains
            )

            if (result.isBlocked) {
                Log.w(TAG, "BLOCKED content in $packageName: '${result.matchedTerm}' (${result.reason})")

                // Asynchronously log block into Room
                serviceScope.launch {
                    policyRepository.logDnsBlock(
                        domain = result.matchedTerm ?: textToEvaluate.take(30),
                        category = "ADULT_CONTENT",
                        wasBlocked = true
                    )
                }

                // Immediately intercept browser by pressing BACK and launching BlockedAppActivity
                performGlobalAction(GLOBAL_ACTION_BACK)

                val blockIntent = Intent(this, BlockedAppActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra(BlockedAppActivity.EXTRA_BLOCKED_PACKAGE, packageName)
                    putExtra(BlockedAppActivity.EXTRA_REASON, "Adult content shield triggered: ${result.matchedTerm ?: ""}")
                }
                startActivity(blockIntent)
            }
        } catch (e: Exception) {
            Log.d(TAG, "Inspection error: ${e.message}")
        }
    }

    /**
     * Extracts URL or search query from address bar view IDs across popular browsers.
     */
    private fun extractUrlOrSearchText(rootNode: AccessibilityNodeInfo): String? {
        for (viewId in KNOWN_URL_BAR_IDS) {
            val nodes = rootNode.findAccessibilityNodeInfosByViewId(viewId)
            if (!nodes.isNullOrEmpty()) {
                val text = nodes[0].text?.toString()
                if (!text.isNullOrBlank()) return text
            }
        }
        return null
    }

    /**
     * Extracts page title or top heading metadata.
     */
    private fun extractPageTitle(rootNode: AccessibilityNodeInfo): String? {
        for (viewId in KNOWN_TITLE_VIEW_IDS) {
            val nodes = rootNode.findAccessibilityNodeInfosByViewId(viewId)
            if (!nodes.isNullOrEmpty()) {
                val text = nodes[0].text?.toString()
                if (!text.isNullOrBlank()) return text
            }
        }
        return null
    }

    override fun onInterrupt() {
        Log.i(TAG, "FocusForge BrowserAccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        _isAccessibilityRunning.value = false
        Log.i(TAG, "FocusForge BrowserAccessibilityService destroyed")
    }

    companion object {
        private const val TAG = "BrowserContentShield"

        private val _isAccessibilityRunning = MutableStateFlow(false)
        val isAccessibilityRunning: StateFlow<Boolean> = _isAccessibilityRunning.asStateFlow()

        // Supported browser package names
        val SUPPORTED_BROWSER_PACKAGES = setOf(
            "com.android.chrome",
            "org.mozilla.firefox",
            "com.microsoft.emmx",
            "com.brave.browser",
            "com.opera.browser",
            "com.opera.mini.native",
            "com.sec.android.app.sbrowser", // Samsung Internet
            "com.vivo.browser",             // Vivo Browser
            "com.heytap.browser",           // Oppo/Realme Browser
            "com.mi.globalbrowser",         // Xiaomi Mint/Mi Browser
            "com.duckduckgo.mobile.android",
            "com.UCMobile.intl"
        )

        // Known URL bar IDs across common browsers
        private val KNOWN_URL_BAR_IDS = listOf(
            "com.android.chrome:id/url_bar",
            "org.mozilla.firefox:id/toolbar_url",
            "org.mozilla.firefox:id/mozac_browser_toolbar_url_view",
            "com.microsoft.emmx:id/url_bar",
            "com.brave.browser:id/url_bar",
            "com.opera.browser:id/url_field",
            "com.sec.android.app.sbrowser:id/location_bar_edit_text",
            "com.vivo.browser:id/url_text",
            "com.duckduckgo.mobile.android:id/omnibarTextInput"
        )

        private val KNOWN_TITLE_VIEW_IDS = listOf(
            "com.android.chrome:id/title",
            "org.mozilla.firefox:id/mozac_browser_toolbar_title_view",
            "com.microsoft.emmx:id/title"
        )
    }
}
