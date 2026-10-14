package com.nexora.companion

import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.espresso.Espresso.closeSoftKeyboard
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

    private fun inList(text: String): SemanticsNodeInteraction {
        // LazyColumn does not compose off-screen rows. Scroll the container
        // until the target exists, then interact with the actual UI node.
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex))
            .performScrollToNode(hasText(text))
        return compose.onNodeWithText(text)
    }

    @Test fun installedAppStartsAndOpensNativeTemplateDetails() {
        compose.onNodeWithText("Explore 14 Studioflow designs").assertIsDisplayed().performClick()
        compose.onNodeWithText("All 14 templates from Studioflow.").assertIsDisplayed()
        inList("Search templates").performTextInput("Barber")
        closeSoftKeyboard()
        inList("1 templates").assertIsDisplayed()
        inList("Barber Shop / Men's Grooming").performClick()
        inList("Sample service menu").assertIsDisplayed()
        inList("Use in Studioflow").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        inList("Search templates").assertIsDisplayed()
    }

    @Test fun nativeAuthValidationBlocksInvalidInputWithoutSubmittingAccounts() {
        compose.onNodeWithText("Profile").performClick()
        compose.onNodeWithText("Welcome back").assertIsDisplayed()
        compose.onNodeWithText("Email").performTextInput("invalid-address")
        inList("Password").performTextInput("123456")
        closeSoftKeyboard()
        inList("Sign in").assertIsNotEnabled()
        inList("Email").performTextReplacement("qa@example.test")
        closeSoftKeyboard()
        inList("Sign in").assertIsEnabled()
        inList("Create an account").performClick()
        inList("Create account").assertIsNotEnabled()
        inList("Password").performTextReplacement("12345678")
        closeSoftKeyboard()
        inList("Create account").assertIsEnabled()
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
