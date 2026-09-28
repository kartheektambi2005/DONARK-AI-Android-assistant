package com.example.accessibility

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Thread-safe bridge connecting the Android AccessibilityService with the
 * DONARK Agent Orchestrator.
 */
object DonarkAccessibilityBridge {
    private const val TAG = "DonarkAccessBridge"

    private var activeService: AccessibilityService? = null

    private val _isServiceConnected = MutableStateFlow(false)
    val isServiceConnected: StateFlow<Boolean> = _isServiceConnected.asStateFlow()

    private val _currentPackage = MutableStateFlow<String?>(null)
    val currentPackage: StateFlow<String?> = _currentPackage.asStateFlow()

    private val _lastEventDescription = MutableStateFlow("")
    val lastEventDescription: StateFlow<String> = _lastEventDescription.asStateFlow()

    fun onServiceConnected(service: AccessibilityService) {
        activeService = service
        _isServiceConnected.value = true
        Log.i(TAG, "DONARK Accessibility Service connected.")
    }

    fun onServiceDisconnected() {
        activeService = null
        _isServiceConnected.value = false
        _currentPackage.value = null
        Log.i(TAG, "DONARK Accessibility Service disconnected.")
    }

    fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString()
        if (!pkg.isNullOrEmpty() && !pkg.contains("accessibility") && !pkg.contains("systemui")) {
            _currentPackage.value = pkg
        }

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            _lastEventDescription.value = "Window changed: $pkg (${event.className})"
        }
    }

    fun getCurrentPackage(): String? = _currentPackage.value

    fun getRootNode(): AccessibilityNodeInfo? {
        val service = activeService ?: return null
        return try {
            service.rootInActiveWindow
        } catch (e: Exception) {
            Log.e(TAG, "Error getting root node: ${e.message}")
            null
        }
    }

    /**
     * Find node containing target text (case-insensitive)
     */
    fun findNodeByText(text: String, exact: Boolean = false): AccessibilityNodeInfo? {
        val root = getRootNode() ?: return null
        return findNodeRecursive(root) { node ->
            val nodeText = node.text?.toString() ?: ""
            val nodeDesc = node.contentDescription?.toString() ?: ""
            if (exact) {
                nodeText.equals(text, ignoreCase = true) || nodeDesc.equals(text, ignoreCase = true)
            } else {
                nodeText.contains(text, ignoreCase = true) || nodeDesc.contains(text, ignoreCase = true)
            }
        }
    }

    /**
     * Find node by content description
     */
    fun findNodeByDescription(description: String): AccessibilityNodeInfo? {
        val root = getRootNode() ?: return null
        return findNodeRecursive(root) { node ->
            val desc = node.contentDescription?.toString() ?: ""
            desc.contains(description, ignoreCase = true)
        }
    }

    /**
     * Find search field in current app dynamically.
     * Checks for EditTexts with hint/text containing search, or viewIds containing 'search', 'query', etc.
     */
    fun findSearchInputNode(): AccessibilityNodeInfo? {
        val root = getRootNode() ?: return null
        // 1. Look for EditText with search hint/contentDescription or viewId
        val specific = findNodeRecursive(root) { node ->
            val isEditable = node.isEditable || node.className?.contains("EditText", ignoreCase = true) == true
            if (isEditable) {
                val text = (node.text?.toString() ?: "").lowercase()
                val desc = (node.contentDescription?.toString() ?: "").lowercase()
                val viewId = (node.viewIdResourceName ?: "").lowercase()
                text.contains("search") || desc.contains("search") ||
                        viewId.contains("search") || viewId.contains("query") ||
                        text.contains("find") || desc.contains("find")
            } else false
        }
        if (specific != null) return specific

        // 2. Look for any visible EditText
        val anyEditText = findNodeRecursive(root) { node ->
            node.isEditable || node.className?.contains("EditText", ignoreCase = true) == true
        }
        if (anyEditText != null) return anyEditText

        // 3. Look for a Search button/icon that can be tapped to reveal the search input
        return findNodeRecursive(root) { node ->
            val desc = (node.contentDescription?.toString() ?: "").lowercase()
            val text = (node.text?.toString() ?: "").lowercase()
            val viewId = (node.viewIdResourceName ?: "").lowercase()
            (desc.contains("search") || text.contains("search") || viewId.contains("search_button")) &&
                    (node.isClickable || node.parent?.isClickable == true)
        }
    }

    /**
     * Find message composer input (e.g. in WhatsApp, Telegram, Messages)
     */
    fun findMessageInputNode(): AccessibilityNodeInfo? {
        val root = getRootNode() ?: return null
        return findNodeRecursive(root) { node ->
            val isEditable = node.isEditable || node.className?.contains("EditText", ignoreCase = true) == true
            if (isEditable) {
                val text = (node.text?.toString() ?: "").lowercase()
                val desc = (node.contentDescription?.toString() ?: "").lowercase()
                val viewId = (node.viewIdResourceName ?: "").lowercase()
                text.contains("message") || desc.contains("message") ||
                        viewId.contains("entry") || viewId.contains("message") ||
                        viewId.contains("input") || isEditable
            } else false
        }
    }

    /**
     * Find send button (e.g., in WhatsApp)
     */
    fun findSendButtonNode(): AccessibilityNodeInfo? {
        val root = getRootNode() ?: return null
        return findNodeRecursive(root) { node ->
            val desc = (node.contentDescription?.toString() ?: "").lowercase()
            val text = (node.text?.toString() ?: "").lowercase()
            val viewId = (node.viewIdResourceName ?: "").lowercase()
            (desc.contains("send") || text.contains("send") || viewId.contains("send")) &&
                    (node.isClickable || node.parent?.isClickable == true)
        }
    }

    /**
     * Type text into an accessibility node.
     */
    fun typeTextIntoNode(node: AccessibilityNodeInfo, text: String): Boolean {
        // Focus the node first
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val success = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        Log.d(TAG, "typeTextIntoNode result=$success for text: $text")
        return success
    }

    /**
     * Perform search submission dynamically.
     * Priority:
     * 1. ACTION_IME_ACTION (if supported on Android 11+)
     * 2. Search button adjacent to input
     * 3. Accessible Search button in window
     * 4. Clickable keyboard/submit action
     */
    fun submitSearch(node: AccessibilityNodeInfo?): Boolean {
        if (node != null) {
            // Try standard IME search action if available (API 30+)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                try {
                    val imeSuccess = node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
                    if (imeSuccess) {
                        Log.d(TAG, "Submitted search via ACTION_IME_ENTER")
                        return true
                    }
                } catch (_: Throwable) {}
            }
        }

        // Search for an on-screen submit or search button
        val root = getRootNode() ?: return false
        val searchBtn = findNodeRecursive(root) { child ->
            val desc = (child.contentDescription?.toString() ?: "").lowercase()
            val text = (child.text?.toString() ?: "").lowercase()
            val viewId = (child.viewIdResourceName ?: "").lowercase()
            (desc == "search" || text == "search" || desc == "go" || text == "go" ||
                    viewId.contains("search_btn") || viewId.contains("submit")) &&
                    (child.isClickable || child.parent?.isClickable == true)
        }

        if (searchBtn != null) {
            return clickNode(searchBtn)
        }

        return false
    }

    /**
     * Click a node or its nearest clickable parent
     */
    fun clickNode(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable) {
                val clicked = current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Log.d(TAG, "clickNode success=$clicked on ${current.className}")
                return clicked
            }
            current = current.parent
        }
        // Fallback: try click directly even if isClickable is false
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    fun performScroll(forward: Boolean): Boolean {
        val root = getRootNode() ?: return false
        val scrollable = findNodeRecursive(root) { node -> node.isScrollable }
        return if (scrollable != null) {
            val action = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            scrollable.performAction(action)
        } else {
            false
        }
    }

    fun performGlobal(action: Int): Boolean {
        val service = activeService ?: return false
        return service.performGlobalAction(action)
    }

    /**
     * Inspect screen and summarize all visible accessible text.
     */
    fun getVisibleScreenText(maxLines: Int = 40): String {
        val root = getRootNode() ?: return "Screen is not accessible or empty."
        val collected = mutableListOf<String>()
        collectTextRecursive(root, collected, maxLines)
        return if (collected.isEmpty()) {
            "No text elements visible on screen."
        } else {
            collected.joinToString("\n")
        }
    }

    /**
     * Read visible messages specifically for chat apps (WhatsApp, etc.)
     */
    fun getVisibleChatMessages(): List<String> {
        val root = getRootNode() ?: return emptyList()
        val messages = mutableListOf<String>()
        collectTextRecursive(root, messages, 30)
        return messages.filter { it.isNotBlank() && !it.equals("WhatsApp", ignoreCase = true) }
    }

    private fun collectTextRecursive(node: AccessibilityNodeInfo, list: MutableList<String>, max: Int) {
        if (list.size >= max) return
        val text = node.text?.toString()?.trim()
        val desc = node.contentDescription?.toString()?.trim()

        if (!text.isNullOrEmpty() && !list.contains(text)) {
            list.add(text)
        } else if (!desc.isNullOrEmpty() && !list.contains(desc)) {
            list.add("[$desc]")
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectTextRecursive(child, list, max)
        }
    }

    private fun findNodeRecursive(
        node: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        if (predicate(node)) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNodeRecursive(child, predicate)
            if (found != null) return found
        }
        return null
    }
}
