package com.ironlog.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The app as a new user meets it: the introduction, creating an account,
 * arriving on Home, and reading the privacy page.
 *
 * Runs against the real graph -- the encrypted database, the real
 * repositories -- with nothing faked, because what is being checked is that
 * the pieces fit, not any one of them alone. Each run signs up a fresh
 * account so the test does not depend on what an earlier run left behind.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AppFlowTest {

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    private val email = "flow.${System.currentTimeMillis()}@example.com"
    private val password = "Correct-Horse-9"

    @Before
    fun inject() = hilt.inject()

    @Test
    fun aNewUserGetsFromTheIntroductionToHome() {
        skipIntroductionIfShown()
        createAccount()
        compose.waitUntil(30_000) { compose.onAllNodes(hasText("Start training")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Start training").assertIsDisplayed()
    }

    @Test
    fun thePrivacyPageIsReachableFromSettings() {
        skipIntroductionIfShown()
        createAccount()
        compose.waitUntil(30_000) { compose.onAllNodes(hasText("Profile")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Profile").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Settings")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Settings").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Privacy & your data")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Privacy & your data").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("What leaves the phone")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("What leaves the phone").assertIsDisplayed()
    }

    /**
     * Gets to the sign-in screen from wherever the device is: the introduction
     * (first launch), sign-in itself, or Home with someone already signed in,
     * who is signed out first. A test that assumed a clean device would only
     * pass once.
     */
    private fun skipIntroductionIfShown() {
        compose.waitUntil(20_000) {
            compose.onAllNodes(hasText("Skip")).fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodes(hasText("Sign in")).fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodes(hasText("Profile")).fetchSemanticsNodes().isNotEmpty()
        }
        if (compose.onAllNodes(hasText("Skip")).fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText("Skip").performClick()
        }
        if (compose.onAllNodes(hasText("Profile")).fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText("Profile").performClick()
            compose.waitUntil(10_000) { compose.onAllNodes(hasText("Sign out")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Sign out").performScrollTo().performClick()
            // The confirmation dialog carries the same label on its button.
            compose.waitUntil(10_000) { compose.onAllNodes(hasText("Sign out?")).fetchSemanticsNodes().isNotEmpty() }
            compose.onAllNodes(hasText("Sign out")).fetchSemanticsNodes()
            compose.onAllNodesWithText("Sign out").onLast().performClick()
            compose.waitUntil(30_000) { compose.onAllNodes(hasText("Sign in")).fetchSemanticsNodes().isNotEmpty() }
        }
    }

    private fun createAccount() {
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("No account yet? Create one")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("No account yet? Create one").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Create account")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Name").performTextInput("Flow Tester")
        compose.onNodeWithText("Email").performTextInput(email)
        compose.onNodeWithText("Password").performTextInput(password)
        compose.onNodeWithText("Confirm password").performTextInput(password)
        compose.onNodeWithText("Create account").performClick()
    }
}
