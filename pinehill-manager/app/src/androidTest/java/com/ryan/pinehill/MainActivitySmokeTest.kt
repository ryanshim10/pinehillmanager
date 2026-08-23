package com.ryan.pinehill

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun mainTabs_launchWithoutCrash() {
        // fetchSemanticsNode() throws if the node does not exist, so this works
        // across the Compose test library version used by this project.
        composeRule.onNodeWithText("월세 입금 확인").fetchSemanticsNode()

        composeRule.onNodeWithText("계약").performClick()
        composeRule.onNodeWithText("세입자 · 계약 이력").fetchSemanticsNode()

        composeRule.onNodeWithText("백업").performClick()
        composeRule.onNodeWithText("백업 · 복원").fetchSemanticsNode()

        composeRule.onNodeWithText("월세").performClick()
        composeRule.onNodeWithText("월세 입금 확인").fetchSemanticsNode()
    }
}
