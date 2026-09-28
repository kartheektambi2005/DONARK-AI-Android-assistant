package com.example.ai

import android.util.Log
import com.example.BuildConfig
import com.example.model.AgentPlan
import com.example.model.AgentStep
import com.example.model.PendingMessage
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * DONARK AI Brain powered by Gemini 3.5 Flash with fallback local neural planner.
 */
class DonarkAiBrain {
    companion object {
        private const val TAG = "DonarkAiBrain"
        const val MODEL_NAME = "gemini-3.5-flash"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/"
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    /**
     * Parse natural language command into an executable multi-step AgentPlan.
     */
    suspend fun planCommand(
        command: String,
        currentPendingMessage: PendingMessage? = null,
        activeApp: String? = null
    ): AgentPlan = withContext(Dispatchers.IO) {
        val trimmed = command.trim()

        // 1. Check conversational message modifications first (e.g. "Change it to 11 AM")
        if (currentPendingMessage != null && isCorrectionCommand(trimmed)) {
            val updatedText = extractCorrectionText(trimmed, currentPendingMessage.messageText)
            return@withContext AgentPlan(
                originalCommand = trimmed,
                steps = listOf(
                    AgentStep(
                        toolName = "prepareMessage",
                        parameters = mapOf(
                            "contact" to currentPendingMessage.contactName,
                            "message" to updatedText
                        ),
                        description = "Update pending message to: \"$updatedText\""
                    )
                ),
                contextData = mapOf("updatedMessage" to updatedText)
            )
        }

        // 2. Try Gemini API if key is available
        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (_: Throwable) {
            ""
        }

        if (apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY") {
            try {
                val plan = planWithGemini(trimmed, apiKey, currentPendingMessage, activeApp)
                if (plan != null && plan.steps.isNotEmpty()) {
                    return@withContext plan
                }
            } catch (e: Exception) {
                Log.w(TAG, "Gemini planning call failed or unparseable: ${e.message}. Using semantic engine.")
            }
        }

        // 3. Robust local semantic parser (English + Telugu-English)
        planWithLocalEngine(trimmed, currentPendingMessage, activeApp)
    }

    private fun isCorrectionCommand(text: String): Boolean {
        val lower = text.lowercase()
        return lower.startsWith("change it to") ||
                lower.startsWith("change to") ||
                lower.startsWith("make it") ||
                lower.startsWith("edit to") ||
                lower.contains("change cheyyi") ||
                lower.contains("marchu")
    }

    private fun extractCorrectionText(command: String, originalText: String): String {
        val lower = command.lowercase()
        val prefix = when {
            lower.startsWith("change it to") -> "change it to"
            lower.startsWith("change to") -> "change to"
            lower.startsWith("make it") -> "make it"
            lower.startsWith("edit to") -> "edit to"
            else -> ""
        }
        if (prefix.isNotEmpty()) {
            val remainder = command.substring(prefix.length).trim()
            if (remainder.isNotBlank()) {
                // If it's a time adjustment like "11 AM" and original was "I will reach tomorrow at 10 AM",
                // replace the target time or return the updated sentence
                return if (originalText.contains("at", ignoreCase = true) && !remainder.contains("will", ignoreCase = true)) {
                    val atIndex = originalText.lastIndexOf("at", ignoreCase = true)
                    originalText.substring(0, atIndex + 2) + " " + remainder
                } else {
                    remainder
                }
            }
        }
        return command
    }

    /**
     * Local semantic planner handling English and Telugu-English voice commands.
     */
    fun planWithLocalEngine(
        command: String,
        pendingMessage: PendingMessage?,
        activeApp: String?
    ): AgentPlan {
        val lower = command.lowercase().trim()
        val steps = mutableListOf<AgentStep>()

        // Continuous voice mode controls
        if (lower == "voice mode on" || lower == "start voice mode" || lower == "voice on") {
            // Handled directly by orchestrator
            return AgentPlan(originalCommand = command, steps = emptyList(), contextData = mapOf("action" to "VOICE_MODE_ON"))
        }
        if (lower == "voice mode off" || lower == "stop listening" || lower == "voice off") {
            return AgentPlan(originalCommand = command, steps = emptyList(), contextData = mapOf("action" to "VOICE_MODE_OFF"))
        }

        // Send Command ("Send it", "Send", "Send cheyyi")
        if (lower == "send it" || lower == "send" || lower == "send cheyyi" || lower == "send chey") {
            if (pendingMessage != null) {
                steps.add(
                    AgentStep(
                        toolName = "sendPreparedMessage",
                        parameters = emptyMap(),
                        description = "Send message to ${pendingMessage.contactName}",
                        verificationType = "verifyMessageSent"
                    )
                )
                return AgentPlan(originalCommand = command, steps = steps, contextData = mapOf("action" to "CONFIRM_SEND"))
            } else {
                steps.add(
                    AgentStep(
                        toolName = "sendPreparedMessage",
                        parameters = emptyMap(),
                        description = "Send currently visible message",
                        verificationType = "verifyMessageSent"
                    )
                )
                return AgentPlan(originalCommand = command, steps = steps)
            }
        }

        // Media controls ("Stop the song", "Stop playing", "Song stop cheyyi", "Pause", "Resume", "Play it again")
        if (lower.contains("stop the song") || lower.contains("stop song") || lower.contains("song stop cheyyi") ||
            lower == "stop playing" || lower == "stop music") {
            steps.add(
                AgentStep(
                    toolName = "stopMedia",
                    parameters = emptyMap(),
                    description = "Stop media playback",
                    verificationType = "verifyPlaybackStopped"
                )
            )
            return AgentPlan(originalCommand = command, steps = steps, contextData = mapOf("mediaAction" to "STOP"))
        }

        if (lower == "pause" || lower == "pause the song" || lower == "pause it") {
            steps.add(
                AgentStep(
                    toolName = "pauseMedia",
                    parameters = emptyMap(),
                    description = "Pause media playback",
                    verificationType = "verifyPlaybackStopped"
                )
            )
            return AgentPlan(originalCommand = command, steps = steps)
        }

        if (lower == "resume" || lower == "play again" || lower == "play it again" || lower == "play it" || lower == "play the song" || lower == "play") {
            steps.add(
                AgentStep(
                    toolName = "playMedia",
                    parameters = emptyMap(),
                    description = "Play/Resume media playback",
                    verificationType = "verifyPlaybackStarted"
                )
            )
            return AgentPlan(originalCommand = command, steps = steps)
        }

        if (lower.contains("next song") || lower.contains("next track")) {
            steps.add(AgentStep("nextTrack", emptyMap(), "Skip to next song"))
            return AgentPlan(originalCommand = command, steps = steps)
        }

        if (lower.contains("previous song") || lower.contains("prev song")) {
            steps.add(AgentStep("previousTrack", emptyMap(), "Play previous song"))
            return AgentPlan(originalCommand = command, steps = steps)
        }

        // Screen Reading ("What is on the screen?", "Read this screen", "Read visible messages")
        if (lower.contains("what is on the screen") || lower.contains("what's on the screen") ||
            lower.contains("read this screen") || lower.contains("read screen")) {
            steps.add(AgentStep("readScreen", emptyMap(), "Read visible screen content"))
            return AgentPlan(originalCommand = command, steps = steps)
        }
        if (lower.contains("read visible messages") || lower.contains("read messages") || lower.contains("read chat")) {
            steps.add(AgentStep("readVisibleMessages", emptyMap(), "Read visible chat messages"))
            return AgentPlan(originalCommand = command, steps = steps)
        }

        // Files & Photos ("Show my images", "Show my screenshots", "Find my photos", "Open my files")
        if (lower.contains("show my screenshots") || lower.contains("screenshots")) {
            steps.add(AgentStep("findImages", mapOf("type" to "screenshots"), "Show screenshots"))
            return AgentPlan(originalCommand = command, steps = steps)
        }
        if (lower.contains("show my images") || lower.contains("show images") || lower.contains("find my photos") || lower.contains("photos")) {
            steps.add(AgentStep("findImages", mapOf("type" to "images"), "Show images"))
            return AgentPlan(originalCommand = command, steps = steps)
        }
        if (lower.contains("open my files") || lower.contains("find files") || lower.contains("open files")) {
            steps.add(AgentStep("findFiles", emptyMap(), "Open file explorer"))
            return AgentPlan(originalCommand = command, steps = steps)
        }

        // Navigation ("Go back", "Go home", "Scroll down", "Scroll up", "Kindaki scroll cheyyi")
        if (lower == "back" || lower == "go back" || lower == "venakki vellu") {
            steps.add(AgentStep("goBack", emptyMap(), "Navigate back"))
            return AgentPlan(originalCommand = command, steps = steps)
        }
        if (lower == "home" || lower == "go home") {
            steps.add(AgentStep("goHome", emptyMap(), "Navigate home"))
            return AgentPlan(originalCommand = command, steps = steps)
        }
        if (lower.contains("scroll down") || lower.contains("kindaki scroll cheyyi") || lower.contains("scroll a little")) {
            steps.add(AgentStep("scroll", mapOf("direction" to "down"), "Scroll down"))
            return AgentPlan(originalCommand = command, steps = steps)
        }
        if (lower.contains("scroll up") || lower.contains("paiki scroll cheyyi")) {
            steps.add(AgentStep("scroll", mapOf("direction" to "up"), "Scroll up"))
            return AgentPlan(originalCommand = command, steps = steps)
        }

        // WhatsApp Workflows
        // E.g. "Open WhatsApp", "Open Amma's chat", "Open Rahul chat", "WhatsApp lo Amma chat open cheyyi"
        if (lower.contains("whatsapp") || lower.contains("chat")) {
            val contact = extractContactName(command)
            if (contact != null) {
                steps.add(AgentStep("openChat", mapOf("contact" to contact), "Open $contact chat in WhatsApp", "verifyChatOpened"))
                return AgentPlan(originalCommand = command, steps = steps, contextData = mapOf("targetChat" to contact))
            } else if (lower.contains("open whatsapp") || lower.contains("whatsapp open cheyyi")) {
                steps.add(AgentStep("openApp", mapOf("app" to "WhatsApp"), "Open WhatsApp", "verifyAppOpen"))
                return AgentPlan(originalCommand = command, steps = steps)
            }
        }

        // Direct typing ("Type I will reach tomorrow at 10 AM", "Ee message type cheyyi ...", "I will reach tomorrow at 10 AM")
        if (lower.startsWith("type ") || lower.contains("type cheyyi") ||
            (activeApp?.contains("whatsapp", ignoreCase = true) == true && !lower.startsWith("open"))) {
            val textToType = when {
                lower.startsWith("type ") -> command.substring(5).trim()
                lower.contains("type cheyyi") -> command.replace("type cheyyi", "", ignoreCase = true).trim()
                else -> command.trim()
            }
            steps.add(AgentStep("typeMessage", mapOf("message" to textToType), "Type \"$textToType\"", "verifyTextEntered"))
            return AgentPlan(originalCommand = command, steps = steps, contextData = mapOf("pendingMessageText" to textToType))
        }

        // Spotify Multi-step Workflows
        // E.g. "Open Spotify, search Kesariya, and play it" or "Open Spotify and search for Arijit Singh"
        if (lower.contains("spotify")) {
            steps.add(AgentStep("openApp", mapOf("app" to "Spotify"), "Open Spotify", "verifyAppOpen"))

            val query = extractSearchQuery(command, "spotify")
            if (query.isNotBlank()) {
                steps.add(AgentStep("searchCurrentApp", mapOf("query" to query), "Search Spotify for \"$query\"", "verifySearchResults"))
                if (lower.contains("play") || lower.contains("play it") || lower.contains("play cheyyi")) {
                    steps.add(AgentStep("tapElement", mapOf("element" to query), "Select \"$query\""))
                    steps.add(AgentStep("playMedia", emptyMap(), "Start playback", "verifyPlaybackStarted"))
                }
            }
            return AgentPlan(originalCommand = command, steps = steps)
        }

        // YouTube Multi-step Workflows
        // E.g. "Open YouTube, search funny dog videos, and open the first result" or "Search YouTube for funny dog videos"
        if (lower.contains("youtube")) {
            steps.add(AgentStep("openApp", mapOf("app" to "YouTube"), "Open YouTube", "verifyAppOpen"))
            val query = extractSearchQuery(command, "youtube")
            if (query.isNotBlank()) {
                steps.add(AgentStep("searchCurrentApp", mapOf("query" to query), "Search YouTube for \"$query\"", "verifySearchResults"))
                if (lower.contains("first result") || lower.contains("first video") || lower.contains("first")) {
                    steps.add(AgentStep("tapElement", mapOf("element" to "first_result"), "Open first video result"))
                }
            }
            return AgentPlan(originalCommand = command, steps = steps)
        }

        // Chrome / Web Search
        if (lower.contains("chrome")) {
            steps.add(AgentStep("openApp", mapOf("app" to "Chrome"), "Open Chrome", "verifyAppOpen"))
            val query = extractSearchQuery(command, "chrome")
            if (query.isNotBlank()) {
                steps.add(AgentStep("searchWeb", mapOf("query" to query), "Search Chrome for \"$query\""))
            }
            return AgentPlan(originalCommand = command, steps = steps)
        }

        // Generic search ("Search Amazon for headphones")
        if (lower.startsWith("search ") || lower.contains("search cheyyi")) {
            val app = when {
                lower.contains("amazon") -> "Amazon"
                lower.contains("flipkart") -> "Flipkart"
                else -> null
            }
            if (app != null) {
                val query = extractSearchQuery(command, app.lowercase())
                steps.add(AgentStep("openApp", mapOf("app" to app), "Open $app", "verifyAppOpen"))
                steps.add(AgentStep("searchCurrentApp", mapOf("query" to query), "Search $app for \"$query\"", "verifySearchResults"))
                return AgentPlan(originalCommand = command, steps = steps)
            } else {
                val query = command.replace("search", "", ignoreCase = true).trim()
                steps.add(AgentStep("searchWeb", mapOf("query" to query), "Search web for \"$query\""))
                return AgentPlan(originalCommand = command, steps = steps)
            }
        }

        // Generic Open App ("Open [App]", "[App] open cheyyi")
        val appToOpen = extractAppToOpen(command)
        if (appToOpen != null) {
            steps.add(AgentStep("openApp", mapOf("app" to appToOpen), "Open $appToOpen", "verifyAppOpen"))
            return AgentPlan(originalCommand = command, steps = steps)
        }

        // Default: Search web for the command or read screen
        steps.add(AgentStep("searchWeb", mapOf("query" to command), "Search for \"$command\""))
        return AgentPlan(originalCommand = command, steps = steps)
    }

    private fun extractContactName(command: String): String? {
        val lower = command.lowercase()
        // E.g. "open amma's chat", "open amma chat", "open rahul chat", "whatsapp lo amma chat open cheyyi"
        val regex = Regex("""(?:open\s+)?([a-zA-Z]+)(?:'s)?\s+chat|whatsapp\s+lo\s+([a-zA-Z]+)\s+chat""", RegexOption.IGNORE_CASE)
        val match = regex.find(command)
        if (match != null) {
            val name = match.groupValues[1].ifBlank { match.groupValues[2] }
            if (name.isNotBlank() && !name.equals("open", ignoreCase = true) && !name.equals("whatsapp", ignoreCase = true)) {
                return name
            }
        }

        if (lower.contains("amma")) return "Amma"
        if (lower.contains("rahul")) return "Rahul"
        if (lower.contains("dad")) return "Dad"
        if (lower.contains("karthik")) return "Karthik"
        if (lower.contains("john")) return "John"

        return null
    }

    private fun extractSearchQuery(command: String, appName: String): String {
        val lower = command.lowercase().trim()
        var str: String

        // 1. Check Telugu pattern: "[App] lo [query] search chesi / search cheyyi"
        if (lower.contains("search chesi") || lower.contains("search cheyyi")) {
            val searchIdx = lower.indexOf("search che")
            str = if (searchIdx != -1) {
                command.substring(0, searchIdx)
            } else {
                command
            }
        } else {
            // 2. English pattern: "search [app] for [query]" or "search [query]"
            val searchForIndex = lower.indexOf("search $appName for ")
            if (searchForIndex != -1) {
                str = command.substring(searchForIndex + "search $appName for ".length)
            } else {
                val searchIndex = lower.indexOf("search ")
                str = if (searchIndex != -1) {
                    command.substring(searchIndex + "search ".length)
                } else {
                    command
                }
            }
        }

        // Cut off trailing action phrases (longer substrings first)
        val cutoffs = listOf(", and play", " and play", ", and open", " and open", " and", " search chesi", " search cheyyi", " play cheyyi")
        for (cutoff in cutoffs) {
            val idx = str.indexOf(cutoff, ignoreCase = true)
            if (idx != -1) {
                str = str.substring(0, idx)
            }
        }

        // Clean up app names, particles, commas, and punctuation
        str = str.replace(appName, "", ignoreCase = true)
            .replace("for ", "", ignoreCase = true)
            .replace("lo ", "", ignoreCase = true)
            .replace("open ", "", ignoreCase = true)
            .trim(',', '.', ' ', ';')

        return str
    }

    private fun extractAppToOpen(command: String): String? {
        val lower = command.lowercase()
        val knownApps = listOf("youtube", "whatsapp", "spotify", "chrome", "amazon", "flipkart", "photos", "files", "camera", "settings")
        for (app in knownApps) {
            if (lower.contains(app)) {
                return app.replaceFirstChar { it.uppercase() }
            }
        }
        return null
    }

    /**
     * Gemini 3.5 Flash direct REST planning implementation.
     */
    private suspend fun planWithGemini(
        command: String,
        apiKey: String,
        pendingMessage: PendingMessage?,
        activeApp: String?
    ): AgentPlan? = withContext(Dispatchers.IO) {
        val systemInstruction = """
            You are DONARK AI's planning brain. You break user commands into structured tool steps.
            Available tools:
            - openApp(app)
            - searchCurrentApp(query)
            - searchWeb(query)
            - typeMessage(message)
            - prepareMessage(contact, message)
            - sendPreparedMessage()
            - openChat(contact)
            - tapElement(element)
            - playMedia()
            - pauseMedia()
            - stopMedia()
            - nextTrack()
            - previousTrack()
            - readScreen()
            - readVisibleMessages()
            - findImages(type)
            - findFiles()
            - scroll(direction)
            - goBack()
            - goHome()

            Return ONLY valid JSON matching this schema:
            {
              "steps": [
                {
                  "toolName": "string",
                  "parameters": {"key": "value"},
                  "description": "string",
                  "verificationType": "string"
                }
              ],
              "contextData": {"key": "value"}
            }
            Do NOT include markdown formatting or backticks.
        """.trimIndent()

        val userPrompt = "User command: \"$command\". Active app: \"$activeApp\". Pending message: \"${pendingMessage?.messageText}\"."

        val jsonBody = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", userPrompt))
                    })
                })
            })
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().put("text", systemInstruction))
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.1)
                put("responseMimeType", "application/json")
            })
        }

        val url = "$BASE_URL$MODEL_NAME:generateContent?key=$apiKey"
        val request = Request.Builder()
            .url(url)
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = okHttpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            Log.w(TAG, "Gemini REST response not successful: ${response.code}")
            return@withContext null
        }

        val responseString = response.body?.string() ?: return@withContext null
        val rootJson = JSONObject(responseString)
        val text = rootJson.getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")

        val planJson = JSONObject(text)
        val stepsJson = planJson.getJSONArray("steps")
        val steps = mutableListOf<AgentStep>()

        for (i in 0 until stepsJson.length()) {
            val stepObj = stepsJson.getJSONObject(i)
            val toolName = stepObj.getString("toolName")
            val desc = stepObj.optString("description", toolName)
            val verType = stepObj.optString("verificationType", "")
            val paramsObj = stepObj.optJSONObject("parameters")
            val params = mutableMapOf<String, String>()
            if (paramsObj != null) {
                val keys = paramsObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    params[k] = paramsObj.getString(k)
                }
            }
            steps.add(AgentStep(toolName, params, desc, verType))
        }

        return@withContext AgentPlan(
            originalCommand = command,
            steps = steps
        )
    }
}
