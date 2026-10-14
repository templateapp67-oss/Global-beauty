package com.nexora.companion

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.nexora.companion.data.Consent
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Runs the real Activity and Firebase initialization on Android, without
 * sending email, enabling diagnostics or submitting real account operations. */
@RunWith(AndroidJUnit4::class)
class CompanionDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun installedAppStartsAndOpensNativeTemplateDetails() {
        compose.onNodeWithText("Explore 14 Studioflow designs").assertIsDisplayed().performClick()
        compose.onNodeWithText("All 14 templates from Studioflow.").assertIsDisplayed()
        compose.onNodeWithText("Search templates").performTextInput("Barber")
        compose.onNodeWithText("1 templates").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Barber Shop / Men's Grooming").performScrollTo().performClick()
        compose.onNodeWithText("Sample service menu").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Use in Studioflow").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Search templates").assertExists()
    }

    @Test fun nativeAuthValidationBlocksInvalidInputWithoutSubmittingAccounts() {
        compose.onNodeWithText("Profile").performClick()
        compose.onNodeWithText("Welcome back").assertIsDisplayed()
        compose.onNodeWithText("Email").performTextInput("invalid-address")
        compose.onNodeWithText("Password").performTextInput("123456")
        compose.onNodeWithText("Sign in").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Email").performTextReplacement("qa@example.test")
        compose.onNodeWithText("Sign in").performScrollTo().assertIsEnabled()
        compose.onNodeWithText("Create an account").performScrollTo().performClick()
        compose.onNodeWithText("Create account").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Password").performTextReplacement("12345678")
        compose.onNodeWithText("Create account").performScrollTo().assertIsEnabled()
    }

    @Test fun firebaseAndroidIdentityLoadsWithOptionalCollectionDisabled() {
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Privacy & preferences").assertIsDisplayed()
        compose.onNodeWithText("Usage analytics").assertIsDisplayed()
        compose.onAllNodes(isToggleable()).onFirst().assertIsOff()
        compose.runOnIdle {
            val app=compose.activity.application as NexoraApplication
            assertEquals("nexora-beauty",FirebaseApp.getInstance().options.projectId)
            assertTrue(FirebaseApp.getInstance().options.applicationId.contains(":android:"))
            assertTrue(app.container.telemetry.available)
            assertEquals(Consent(),app.container.preferences.consent.value)
            assertFalse(FirebaseMessaging.getInstance().isAutoInitEnabled)
            assertNull(app.container.repository.session.value)
        }
    }
}
