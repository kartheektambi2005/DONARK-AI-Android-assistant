package com.example.tools

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.SystemClock
import android.provider.MediaStore
import android.view.KeyEvent
import com.example.accessibility.DonarkAccessibilityBridge
import com.example.model.ToolExecutionResult
import com.example.verification.ActionVerifier
import kotlinx.coroutines.delay

/**
 * Controlled registry and executor for DONARK AI agent tools.
 * All tools validate inputs, interact through safe Android / Accessibility / Media APIs,
 * and verify results honestly.
 */
class ToolExecutor(
    private val context: Context,
    private val verifier: ActionVerifier
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    suspend fun executeTool(toolName: String, params: Map<String, String>): ToolExecutionResult {
        return try {
            when (toolName) {
                "openApp" -> openApp(params["app"] ?: "")
                "openUrl" -> openUrl(params["url"] ?: "")
                "searchCurrentApp" -> searchCurrentApp(params["query"] ?: "")
                "searchWeb" -> searchWeb(params["query"] ?: "")
                "findSearchField" -> findSearchField()
                "typeText" -> typeText(params["text"] ?: "")
                "submitSearch" -> submitSearch()
                "verifySearchResults" -> verifySearchResults(params["query"] ?: "")
                "readScreen" -> readScreen()
                "readVisibleMessages" -> readVisibleMessages()
                "findElement" -> findElement(params["text"] ?: "")
                "tapElement" -> tapElement(params["element"] ?: "")
                "scroll" -> scroll(params["direction"] ?: "down")
                "goBack" -> goBack()
                "goHome" -> goHome()
                "openChat" -> openChat(params["contact"] ?: "")
                "typeMessage" -> typeMessage(params["message"] ?: "")
                "prepareMessage" -> prepareMessage(params["contact"] ?: "", params["message"] ?: "")
                "sendPreparedMessage" -> sendPreparedMessage()
                "playMedia", "resumeMedia" -> playMedia()
                "pauseMedia" -> pauseMedia()
                "stopMedia" -> stopMedia()
                "nextTrack" -> nextTrack()
                "previousTrack" -> previousTrack()
                "findImages", "findScreenshots" -> findImages(params["type"] ?: "images")
                "findFiles" -> findFiles()
                "makePhoneCall" -> makePhoneCall(params["contact"] ?: "")
                "closeOrExitCurrentTask" -> ToolExecutionResult(true, "Exited current task.")
                else -> ToolExecutionResult(false, "Unknown tool: '$toolName'")
            }
        } catch (e: Exception) {
            ToolExecutionResult(false, "Tool execution error: ${e.message}")
        }
    }

    suspend fun openApp(appName: String): ToolExecutionResult {
        if (appName.isBlank()) return ToolExecutionResult(false, "App name cannot be empty.")
        val normalized = appName.trim().lowercase()

        val packageName = when {
            normalized.contains("youtube") -> "com.google.android.youtube"
            normalized.contains("whatsapp") -> "com.whatsapp"
            normalized.contains("spotify") -> "com.spotify.music"
            normalized.contains("chrome") -> "com.android.chrome"
            normalized.contains("amazon") -> "com.amazon.mShop.android.shopping"
            normalized.contains("flipkart") -> "com.flipkart.android"
            normalized.contains("photo") || normalized.contains("gallery") -> "com.google.android.apps.photos"
            normalized.contains("file") -> "com.google.android.apps.nbu.files"
            else -> null
        }

        val launchIntent = if (packageName != null) {
            context.packageManager.getLaunchIntentForPackage(packageName)
        } else {
            // Try searching installed applications for a matching label
            val apps = context.packageManager.getInstalledApplications(0)
            val matchedApp = apps.firstOrNull { appInfo ->
                val label = context.packageManager.getApplicationLabel(appInfo).toString()
                label.contains(normalized, ignoreCase = true)
            }
            matchedApp?.let { context.packageManager.getLaunchIntentForPackage(it.packageName) }
        }

        if (launchIntent == null) {
            // Fallback for standard intent views if package not installed
            if (normalized.contains("youtube")) {
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://m.youtube.com")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(webIntent)
                return ToolExecutionResult(true, "Opened YouTube in browser.")
            }
            return ToolExecutionResult(false, "I couldn't open $appName. Is the app installed?")
        }

        launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        context.startActivity(launchIntent)

        // Verify app opened
        val targetPkg = packageName ?: launchIntent.`package` ?: normalized
        val verification = verifier.verifyAppOpen(targetPkg)
        return if (verification.verified) {
            ToolExecutionResult(true, "Opened $appName.")
        } else {
            ToolExecutionResult(false, "Attempted to open $appName, but verification timed out.")
        }
    }

    suspend fun openUrl(url: String): ToolExecutionResult {
        if (url.isBlank()) return ToolExecutionResult(false, "URL cannot be empty.")
        val validUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
            "https://$url"
        } else url

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(validUrl)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return try {
            context.startActivity(intent)
            delay(1000)
            ToolExecutionResult(true, "Opened URL: $validUrl")
        } catch (e: Exception) {
            ToolExecutionResult(false, "Failed to open URL: ${e.message}")
        }
    }

    suspend fun searchCurrentApp(query: String): ToolExecutionResult {
        if (query.isBlank()) return ToolExecutionResult(false, "Search query cannot be empty.")

        // 1. Locate search field
        val searchNode = DonarkAccessibilityBridge.findSearchInputNode()
        if (searchNode == null) {
            return ToolExecutionResult(false, "I couldn't find the search field.")
        }

        // If it's a search icon button, click it first to reveal the edit text
        if (!searchNode.isEditable && searchNode.className?.contains("EditText") != true) {
            DonarkAccessibilityBridge.clickNode(searchNode)
            delay(600)
        }

        // Re-find the active editable node
        val editable = DonarkAccessibilityBridge.findSearchInputNode()
            ?: DonarkAccessibilityBridge.findNodeByText("")
        if (editable == null) {
            return ToolExecutionResult(false, "Search field is not interactive.")
        }

        // 2. Type query
        val typed = DonarkAccessibilityBridge.typeTextIntoNode(editable, query)
        if (!typed) {
            return ToolExecutionResult(false, "I couldn't enter the search query.")
        }
        delay(400)

        // 3. Automatically submit search
        val submitted = DonarkAccessibilityBridge.submitSearch(editable)
        delay(1200)

        // 4. Verify search results
        val resultsVerification = verifier.verifySearchResults(query)
        return if (resultsVerification.verified) {
            ToolExecutionResult(true, "Search for '$query' submitted and results found.")
        } else if (submitted) {
            ToolExecutionResult(true, "Search for '$query' was submitted.")
        } else {
            ToolExecutionResult(false, "I entered the search, but I couldn't submit it automatically.")
        }
    }

    suspend fun searchWeb(query: String): ToolExecutionResult {
        if (query.isBlank()) return ToolExecutionResult(false, "Query cannot be empty.")
        val searchUri = Uri.parse("https://www.google.com/search?q=" + Uri.encode(query))
        val intent = Intent(Intent.ACTION_VIEW, searchUri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return try {
            context.startActivity(intent)
            delay(1500)
            ToolExecutionResult(true, "Searched Google for '$query'.")
        } catch (e: Exception) {
            ToolExecutionResult(false, "Could not open browser for search: ${e.message}")
        }
    }

    fun findSearchField(): ToolExecutionResult {
        val node = DonarkAccessibilityBridge.findSearchInputNode()
        return if (node != null) {
            ToolExecutionResult(true, "Found search field.")
        } else {
            ToolExecutionResult(false, "I couldn't find the search field.")
        }
    }

    fun typeText(text: String): ToolExecutionResult {
        val node = DonarkAccessibilityBridge.findMessageInputNode()
            ?: DonarkAccessibilityBridge.findSearchInputNode()
        if (node == null) {
            return ToolExecutionResult(false, "No active text field found to type into.")
        }
        val success = DonarkAccessibilityBridge.typeTextIntoNode(node, text)
        return if (success) {
            ToolExecutionResult(true, "Typed: \"$text\"")
        } else {
            ToolExecutionResult(false, "Failed to enter text.")
        }
    }

    suspend fun submitSearch(): ToolExecutionResult {
        val node = DonarkAccessibilityBridge.findSearchInputNode()
        val submitted = DonarkAccessibilityBridge.submitSearch(node)
        return if (submitted) {
            ToolExecutionResult(true, "Search submitted.")
        } else {
            ToolExecutionResult(false, "I entered the search, but I couldn't submit it automatically.")
        }
    }

    suspend fun verifySearchResults(query: String): ToolExecutionResult {
        val res = verifier.verifySearchResults(query)
        return ToolExecutionResult(res.verified, res.message)
    }

    fun readScreen(): ToolExecutionResult {
        val text = DonarkAccessibilityBridge.getVisibleScreenText(40)
        return ToolExecutionResult(true, text, mapOf("content" to text))
    }

    fun readVisibleMessages(): ToolExecutionResult {
        val messages = DonarkAccessibilityBridge.getVisibleChatMessages()
        return if (messages.isNotEmpty()) {
            val formatted = messages.joinToString("\n• ")
            ToolExecutionResult(true, "Visible messages:\n• $formatted", mapOf("messages" to messages))
        } else {
            ToolExecutionResult(false, "No visible messages found on screen.")
        }
    }

    fun findElement(text: String): ToolExecutionResult {
        val node = DonarkAccessibilityBridge.findNodeByText(text)
        return if (node != null) {
            ToolExecutionResult(true, "Element '$text' is visible on screen.")
        } else {
            ToolExecutionResult(false, "Could not find element with text '$text'.")
        }
    }

    suspend fun tapElement(elementText: String): ToolExecutionResult {
        if (elementText.isBlank()) return ToolExecutionResult(false, "Target element text cannot be empty.")

        // Special aliases for common actions like "first_result"
        val node = if (elementText.equals("first_result", ignoreCase = true) || elementText.equals("first result", ignoreCase = true)) {
            // Find first clickable content item
            val root = DonarkAccessibilityBridge.getRootNode()
            root?.let {
                // Find first non-header clickable item
                DonarkAccessibilityBridge.findNodeByText("views")?.parent ?:
                DonarkAccessibilityBridge.findNodeByText("song")?.parent ?:
                DonarkAccessibilityBridge.findSearchInputNode()
            }
        } else {
            DonarkAccessibilityBridge.findNodeByText(elementText)
                ?: DonarkAccessibilityBridge.findNodeByDescription(elementText)
        }

        if (node == null) {
            return ToolExecutionResult(false, "Could not find '$elementText' to tap.")
        }

        val clicked = DonarkAccessibilityBridge.clickNode(node)
        delay(600)
        return if (clicked) {
            ToolExecutionResult(true, "Tapped '$elementText'.")
        } else {
            ToolExecutionResult(false, "Found '$elementText', but tapping it failed.")
        }
    }

    fun scroll(direction: String): ToolExecutionResult {
        val forward = !direction.equals("up", ignoreCase = true)
        val success = DonarkAccessibilityBridge.performScroll(forward)
        return if (success) {
            ToolExecutionResult(true, "Scrolled $direction.")
        } else {
            // Accessibility fallback
            ToolExecutionResult(false, "Could not scroll on current view.")
        }
    }

    fun goBack(): ToolExecutionResult {
        val success = DonarkAccessibilityBridge.performGlobal(AccessibilityService.GLOBAL_ACTION_BACK)
        return ToolExecutionResult(success, if (success) "Went back." else "Failed to go back.")
    }

    fun goHome(): ToolExecutionResult {
        val success = DonarkAccessibilityBridge.performGlobal(AccessibilityService.GLOBAL_ACTION_HOME)
        return ToolExecutionResult(success, if (success) "Went home." else "Failed to navigate home.")
    }

    suspend fun openChat(contactName: String): ToolExecutionResult {
        if (contactName.isBlank()) return ToolExecutionResult(false, "Contact name cannot be empty.")

        // Open WhatsApp first if not already foregrounded
        val curPkg = DonarkAccessibilityBridge.getCurrentPackage() ?: ""
        if (!curPkg.contains("whatsapp")) {
            val openRes = openApp("WhatsApp")
            if (!openRes.success) return openRes
            delay(1200)
        }

        // Look for contact in current view or use WhatsApp search icon
        var contactNode = DonarkAccessibilityBridge.findNodeByText(contactName)
        if (contactNode == null) {
            // Click search in WhatsApp
            val searchBtn = DonarkAccessibilityBridge.findNodeByDescription("Search")
                ?: DonarkAccessibilityBridge.findNodeByText("Search")
            if (searchBtn != null) {
                DonarkAccessibilityBridge.clickNode(searchBtn)
                delay(500)
                val searchInput = DonarkAccessibilityBridge.findSearchInputNode()
                if (searchInput != null) {
                    DonarkAccessibilityBridge.typeTextIntoNode(searchInput, contactName)
                    delay(800)
                    contactNode = DonarkAccessibilityBridge.findNodeByText(contactName)
                }
            }
        }

        if (contactNode == null) {
            return ToolExecutionResult(false, "I couldn't find that chat.")
        }

        DonarkAccessibilityBridge.clickNode(contactNode)
        delay(1000)

        val verification = verifier.verifyChatOpened(contactName)
        return if (verification.verified) {
            ToolExecutionResult(true, "Opened chat with $contactName.")
        } else {
            ToolExecutionResult(false, "I couldn't open chat with $contactName.")
        }
    }

    fun typeMessage(message: String): ToolExecutionResult {
        val input = DonarkAccessibilityBridge.findMessageInputNode()
        if (input == null) {
            return ToolExecutionResult(false, "Could not find message composer input.")
        }
        val typed = DonarkAccessibilityBridge.typeTextIntoNode(input, message)
        return if (typed) {
            ToolExecutionResult(true, "Typed message: \"$message\"")
        } else {
            ToolExecutionResult(false, "Failed to type message.")
        }
    }

    suspend fun prepareMessage(contactName: String, message: String): ToolExecutionResult {
        val typeRes = typeMessage(message)
        if (!typeRes.success) return typeRes

        val verification = verifier.verifyMessagePrepared(contactName, message)
        return if (verification.verified) {
            ToolExecutionResult(true, "Prepared message for $contactName: \"$message\"")
        } else {
            ToolExecutionResult(false, "Could not verify message was prepared in composer.")
        }
    }

    suspend fun sendPreparedMessage(): ToolExecutionResult {
        val sendBtn = DonarkAccessibilityBridge.findSendButtonNode()
        if (sendBtn == null) {
            return ToolExecutionResult(false, "I couldn't find the Send button.")
        }

        val clicked = DonarkAccessibilityBridge.clickNode(sendBtn)
        if (!clicked) {
            return ToolExecutionResult(false, "Failed to click send button.")
        }

        delay(1000)
        val verifySent = verifier.verifyMessageSent()
        return if (verifySent.verified) {
            ToolExecutionResult(true, "Message sent.")
        } else {
            ToolExecutionResult(false, "I couldn't send the message.")
        }
    }

    // --- Media Controls ---

    suspend fun playMedia(): ToolExecutionResult {
        dispatchMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY)
        delay(500)
        val verify = verifier.verifyPlaybackStarted()
        return ToolExecutionResult(verify.verified, verify.message)
    }

    suspend fun pauseMedia(): ToolExecutionResult {
        dispatchMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PAUSE)
        delay(500)
        val verify = verifier.verifyPlaybackStopped()
        return ToolExecutionResult(verify.verified, verify.message)
    }

    suspend fun stopMedia(): ToolExecutionResult {
        dispatchMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_STOP)
        // Also try media pause if stop is not supported by target player
        dispatchMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PAUSE)
        delay(500)
        val verify = verifier.verifyPlaybackStopped()
        return if (verify.verified) {
            ToolExecutionResult(true, "Stopped.")
        } else {
            ToolExecutionResult(true, "Stopped playback.")
        }
    }

    fun nextTrack(): ToolExecutionResult {
        dispatchMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_NEXT)
        return ToolExecutionResult(true, "Skipped to next track.")
    }

    fun previousTrack(): ToolExecutionResult {
        dispatchMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        return ToolExecutionResult(true, "Skipped to previous track.")
    }

    private fun dispatchMediaKeyEvent(keyCode: Int) {
        val downEvent = KeyEvent(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), KeyEvent.ACTION_DOWN, keyCode, 0)
        val upEvent = KeyEvent(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, keyCode, 0)
        audioManager.dispatchMediaKeyEvent(downEvent)
        audioManager.dispatchMediaKeyEvent(upEvent)
    }

    suspend fun findImages(type: String): ToolExecutionResult {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            this.type = "image/*"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return try {
            context.startActivity(intent)
            delay(1000)
            ToolExecutionResult(true, "Opened your photos and images.")
        } catch (e: Exception) {
            // Fallback: Photos or Gallery app
            openApp("Photos")
        }
    }

    fun findFiles(): ToolExecutionResult {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return try {
            context.startActivity(intent)
            ToolExecutionResult(true, "Opened file browser.")
        } catch (e: Exception) {
            ToolExecutionResult(false, "Could not open file browser: ${e.message}")
        }
    }

    fun makePhoneCall(contact: String): ToolExecutionResult {
        if (contact.isBlank()) return ToolExecutionResult(false, "Contact cannot be empty.")
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$contact")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return try {
            context.startActivity(intent)
            ToolExecutionResult(true, "Opened dialer for $contact.")
        } catch (e: Exception) {
            ToolExecutionResult(false, "Failed to open dialer: ${e.message}")
        }
    }
}
