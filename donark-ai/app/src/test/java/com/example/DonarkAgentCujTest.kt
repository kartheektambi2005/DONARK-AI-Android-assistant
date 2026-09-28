package com.example

import com.example.ai.DonarkAiBrain
import com.example.model.PendingMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * End-to-end and unit tests verifying the critical agent user journeys (CUJs):
 * 1. Spotify Kesariya playback & media interruption
 * 2. WhatsApp Amma chat, message composition, conversational correction ("Change it to 11 AM"), and send
 * 3. YouTube search & first result selection
 * 4. Telugu-English natural voice commands
 * 5. Screen reader and awareness commands
 */
class DonarkAgentCujTest {

    private lateinit var brain: DonarkAiBrain

    @Before
    fun setUp() {
        brain = DonarkAiBrain()
    }

    @Test
    fun testCuj1_SpotifyKesariyaAndMediaControls() {
        // Step 1: "Open Spotify, search Kesariya, and play it"
        val plan = brain.planWithLocalEngine("Open Spotify, search Kesariya, and play it", null, null)
        assertTrue("Plan should have steps", plan.steps.isNotEmpty())
        assertEquals("openApp", plan.steps[0].toolName)
        assertEquals("Spotify", plan.steps[0].parameters["app"])
        assertEquals("searchCurrentApp", plan.steps[1].toolName)
        assertEquals("Kesariya", plan.steps[1].parameters["query"])
        assertEquals("tapElement", plan.steps[2].toolName)
        assertEquals("Kesariya", plan.steps[2].parameters["element"])
        assertEquals("playMedia", plan.steps[3].toolName)

        // Step 2: "Stop the song"
        val stopPlan = brain.planWithLocalEngine("Stop the song", null, "com.spotify.music")
        assertEquals(1, stopPlan.steps.size)
        assertEquals("stopMedia", stopPlan.steps[0].toolName)

        // Step 3: "Play it again"
        val resumePlan = brain.planWithLocalEngine("Play it again", null, "com.spotify.music")
        assertEquals(1, resumePlan.steps.size)
        assertEquals("playMedia", resumePlan.steps[0].toolName)
    }

    @Test
    fun testCuj2_WhatsAppWorkflowAndCorrection() {
        // Step 1: "Open Amma chat"
        val chatPlan = brain.planWithLocalEngine("Open Amma chat", null, null)
        assertEquals("openChat", chatPlan.steps[0].toolName)
        assertEquals("Amma", chatPlan.steps[0].parameters["contact"])

        // Step 2: User drafts message: "Type I will reach tomorrow at 10 AM"
        val draftPlan = brain.planWithLocalEngine(
            "Type I will reach tomorrow at 10 AM",
            null,
            "com.whatsapp"
        )
        assertEquals("typeMessage", draftPlan.steps[0].toolName)
        assertEquals("I will reach tomorrow at 10 AM", draftPlan.steps[0].parameters["message"])

        // Step 3: Conversational correction: "Change it to 11 AM"
        val pending = PendingMessage(
            targetApp = "WhatsApp",
            contactName = "Amma",
            messageText = "I will reach tomorrow at 10 AM"
        )
        val correctionPlan = brain.planWithLocalEngine(
            "Change it to 11 AM",
            pending,
            "com.whatsapp"
        )
        // Verified: The system modifies the message to 11 AM
        val updatedText = correctionPlan.contextData["updatedMessage"]
            ?: correctionPlan.steps.firstOrNull()?.parameters?.get("message")
        assertNotNull("Should contain updated text", updatedText)
        assertTrue("Should reflect 11 AM", updatedText!!.contains("11 AM"))

        // Step 4: User confirms "Send it"
        val sendPlan = brain.planWithLocalEngine("Send it", pending, "com.whatsapp")
        assertEquals("sendPreparedMessage", sendPlan.steps[0].toolName)
    }

    @Test
    fun testCuj3_YouTubeSearchAndFirstResult() {
        // "Open YouTube, search funny dog videos, and open the first result"
        val plan = brain.planWithLocalEngine(
            "Open YouTube, search funny dog videos, and open the first result",
            null,
            null
        )
        assertTrue(plan.steps.size >= 3)
        assertEquals("openApp", plan.steps[0].toolName)
        assertEquals("YouTube", plan.steps[0].parameters["app"])
        assertEquals("searchCurrentApp", plan.steps[1].toolName)
        assertTrue(plan.steps[1].parameters["query"]!!.contains("funny dog"))
        assertEquals("tapElement", plan.steps[2].toolName)
        assertEquals("first_result", plan.steps[2].parameters["element"])
    }

    @Test
    fun testTeluguVoiceCommands() {
        // "YouTube lo funny videos search cheyyi"
        val ytTelugu = brain.planWithLocalEngine("YouTube lo funny videos search cheyyi", null, null)
        assertEquals("openApp", ytTelugu.steps[0].toolName)
        assertEquals("searchCurrentApp", ytTelugu.steps[1].toolName)
        assertTrue(ytTelugu.steps[1].parameters["query"]!!.contains("funny videos"))

        // "Song stop cheyyi"
        val stopTelugu = brain.planWithLocalEngine("Song stop cheyyi", null, null)
        assertEquals("stopMedia", stopTelugu.steps[0].toolName)

        // "WhatsApp lo Amma chat open cheyyi"
        val waTelugu = brain.planWithLocalEngine("WhatsApp lo Amma chat open cheyyi", null, null)
        assertEquals("openChat", waTelugu.steps[0].toolName)
        assertEquals("Amma", waTelugu.steps[0].parameters["contact"])
    }

    @Test
    fun testScreenReaderAndAwareness() {
        val screenPlan = brain.planWithLocalEngine("What is on the screen?", null, null)
        assertEquals("readScreen", screenPlan.steps[0].toolName)

        val messagesPlan = brain.planWithLocalEngine("Read visible messages", null, "com.whatsapp")
        assertEquals("readVisibleMessages", messagesPlan.steps[0].toolName)

        val photosPlan = brain.planWithLocalEngine("Show my screenshots", null, null)
        assertEquals("findImages", photosPlan.steps[0].toolName)
    }
}
