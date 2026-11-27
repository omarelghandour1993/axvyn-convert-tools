package com.axvyn.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.axvyn.app.data.preferences.ThemePreferences
import com.axvyn.app.features.audiotomp3.AudioToMp3Screen
import com.axvyn.app.features.compresspdf.CompressPdfScreen
import com.axvyn.app.features.exceltopdf.ExcelToPdfScreen
import com.axvyn.app.features.imagecompressor.ImageCompressorScreen
import com.axvyn.app.features.imageconverter.ImageConverterScreen
import com.axvyn.app.features.imagetopdf.ImageToPdfScreen
import com.axvyn.app.features.mergepdf.MergePdfScreen
import com.axvyn.app.features.pdftoimage.PdfToImageScreen
import com.axvyn.app.features.powerpointtopdf.PowerPointToPdfScreen
import com.axvyn.app.features.wordtopdf.WordToPdfScreen
import com.axvyn.app.ui.history.HistoryScreen
import com.axvyn.app.ui.home.HomeScreen
import com.axvyn.app.ui.settings.SettingsScreen
import com.axvyn.app.ui.theme.AxvynTheme

@Composable
fun AxvynApp() {
    val context = LocalContext.current
    val preferences = remember(context) { ThemePreferences(context) }
    var themeMode by remember { mutableStateOf(preferences.themeMode) }

    AxvynTheme(themeMode = themeMode) {
        val navController = rememberNavController()
        AxvynNavHost(
            navController = navController,
            onThemeModeChange = { mode ->
                themeMode = mode
                preferences.themeMode = mode
            },
        )
    }
}

@Composable
private fun AxvynNavHost(
    navController: NavHostController,
    onThemeModeChange: (com.axvyn.app.data.preferences.ThemeMode) -> Unit,
) {
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onToolClick = { route -> navController.navigate(route) },
                onSettingsClick = { navController.navigate(Routes.SETTINGS) },
                onHistoryClick = { navController.navigate(Routes.HISTORY) },
            )
        }
        composable(Routes.HISTORY) {
            HistoryScreen(
                onHomeClick = { navController.navigate(Routes.HOME) },
                onSettingsClick = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onHomeClick = { navController.navigate(Routes.HOME) },
                onHistoryClick = { navController.navigate(Routes.HISTORY) },
                onThemeModeChange = onThemeModeChange,
            )
        }
        composable(Routes.WORD_TO_PDF) {
            WordToPdfScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.POWERPOINT_TO_PDF) {
            PowerPointToPdfScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.EXCEL_TO_PDF) {
            ExcelToPdfScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.IMAGE_TO_PDF) {
            ImageToPdfScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.COMPRESS_PDF) {
            CompressPdfScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.MERGE_PDF) {
            MergePdfScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.IMAGE_COMPRESSOR) {
            ImageCompressorScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.IMAGE_CONVERTER) {
            ImageConverterScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.PDF_TO_IMAGE) {
            PdfToImageScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.AUDIO_TO_MP3) {
            AudioToMp3Screen(onBack = { navController.popBackStack() })
        }
    }
}
