package com.stpplay.android

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StressTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun testNavigationStress() {
        // Simular navegação rápida entre abas
        val tabs = listOf("Home", "Live TV", "Filmes", "Séries", "Definições")
        
        repeat(10) {
            tabs.forEach { tab ->
                try {
                    composeTestRule.onNodeWithText(tab, ignoreCase = true).performClick()
                    Thread.sleep(200) // 200ms entre cliques
                } catch (e: Exception) {
                    // Ignora se o componente não estiver visível no momento
                }
            }
        }
    }

    @Test
    fun testScrollStress() {
        // Tenta fazer scroll rápido na Home
        repeat(5) {
            composeTestRule.onRoot().performTouchInput {
                swipeUp(durationMillis = 300)
            }
            Thread.sleep(100)
            composeTestRule.onRoot().performTouchInput {
                swipeDown(durationMillis = 300)
            }
        }
    }
}
