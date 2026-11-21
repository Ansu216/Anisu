package com.ansu.anime

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ansu.anime.data.prefs.SeasonalTheme
import com.ansu.anime.di.AppContainer
import com.ansu.anime.ui.about.AboutScreen
import com.ansu.anime.ui.about.CommitsScreen
import com.ansu.anime.ui.about.DeveloperOptionsScreen
import com.ansu.anime.ui.about.NotificationsScreen
import com.ansu.anime.ui.about.UpdateScreen
import com.ansu.anime.ui.about.UpdatesScreen
import com.ansu.anime.ui.components.LocalNavBarBackdropBlur
import com.ansu.anime.ui.components.rememberBackdropState
import com.ansu.anime.ui.playersettings.PlayerStreamingScreen
import com.ansu.anime.ui.search.SearchScreen
import com.ansu.anime.ui.theme.AnsuColors
import com.ansu.anime.ui.theme.AnsuTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Headless renders of the screens that only existed as code before. Robolectric rasterises them with
 * native graphics (no emulator), and Roborazzi writes each one as a PNG under `screenshots/` in the
 * project. The AnsuApp is deliberately not the test Application, so its background scheduling does
 * not run; the container is built here instead.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp-xhdpi")
class ScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val container by lazy {
        AppContainer(ApplicationProvider.getApplicationContext<Application>())
    }

    private fun shot(name: String) {
        val dir = File(System.getProperty("user.dir").orEmpty()).resolve("../screenshots").apply { mkdirs() }
        composeRule.onRoot().captureRoboImage(File(dir, name).absolutePath)
    }

    /** Every screen sits on the page colour here, because the nav host normally paints it. */
    private fun screen(content: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.setContent {
            AnsuTheme {
                Box(modifier = Modifier.fillMaxSize().background(AnsuColors.Background)) {
                    content()
                }
            }
        }
    }

    @Test
    fun updates() {
        screen {
            val nav = rememberNavController()
            UpdatesScreen(container = container, navController = nav)
        }
        shot("updates.png")
    }

    @Test
    fun notifications() {
        screen {
            val nav = rememberNavController()
            NotificationsScreen(container = container, navController = nav)
        }
        shot("notifications.png")
    }

    @Test
    fun commits() {
        screen {
            val nav = rememberNavController()
            CommitsScreen(container = container, navController = nav)
        }
        // The list comes from the GitHub API; let the request land so the PNG shows real commits.
        Thread.sleep(4000)
        composeRule.waitForIdle()
        shot("commits.png")
    }

    @Test
    fun about() {
        screen {
            val nav = rememberNavController()
            AboutScreen(container = container, navController = nav)
        }
        shot("about.png")
    }

    @Test
    fun aboutLinks() {
        composeRule.setContent {
            AnsuTheme {
                Box(modifier = Modifier.fillMaxSize().background(AnsuColors.Background)) {
                    val nav = rememberNavController()
                    AboutScreen(container = container, navController = nav)
                }
            }
        }
        composeRule.onNodeWithText("Anisu Community").performClick()
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(400)
        shot("about-links.png")
    }

    @Test
    fun aboutChristmas() {
        container.appearancePrefs.setSeasonalOverride(SeasonalTheme.CHRISTMAS)
        screen {
            val nav = rememberNavController()
            AboutScreen(container = container, navController = nav)
        }
        shot("about-christmas.png")
    }

    @Test
    fun aboutHalloween() {
        container.appearancePrefs.setSeasonalOverride(SeasonalTheme.HALLOWEEN)
        screen {
            val nav = rememberNavController()
            AboutScreen(container = container, navController = nav)
        }
        shot("about-halloween.png")
    }

    @Test
    fun aboutEaster() {
        container.appearancePrefs.setSeasonalOverride(SeasonalTheme.EASTER)
        screen {
            val nav = rememberNavController()
            AboutScreen(container = container, navController = nav)
        }
        shot("about-easter.png")
    }

    @Test
    fun updateScreen() {
        // The updater's hexagons turn forever, so the clock is driven by hand instead of waiting for idle.
        composeRule.mainClock.autoAdvance = false
        screen {
            val nav = rememberNavController()
            UpdateScreen(container = container, navController = nav)
        }
        composeRule.mainClock.advanceTimeBy(600)
        shot("update.png")
    }

    @Test
    fun developerOptions() {
        container.appearancePrefs.unlockDeveloperOptions()
        screen {
            val nav = rememberNavController()
            DeveloperOptionsScreen(container = container, navController = nav)
        }
        shot("developer-options.png")
    }

    @Test
    fun playback() {
        screen {
            val nav = rememberNavController()
            PlayerStreamingScreen(container = container, navController = nav)
        }
        shot("playback.png")
    }

    @Test
    fun searchBlurOn() {
        container.appearancePrefs.setNavBarBackdropBlur(true)
        screen {
            CompositionLocalProvider(LocalNavBarBackdropBlur provides true) {
                val nav = rememberNavController()
                SearchScreen(
                    container = container,
                    navController = nav,
                    onAnimeSelected = {},
                    backdrop = rememberBackdropState(),
                )
            }
        }
        shot("search-blur-on.png")
    }

    @Test
    fun searchBlurOff() {
        container.appearancePrefs.setNavBarBackdropBlur(false)
        screen {
            CompositionLocalProvider(LocalNavBarBackdropBlur provides false) {
                val nav = rememberNavController()
                SearchScreen(
                    container = container,
                    navController = nav,
                    onAnimeSelected = {},
                    backdrop = rememberBackdropState(),
                )
            }
        }
        shot("search-blur-off.png")
    }
}
