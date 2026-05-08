package net.rodakot.ngxhttpmonitoringclient.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = SignalGreen,
    secondary = SignalCyan,
    tertiary = SignalAmber,
    error = SignalRed,
    background = CommandBlack,
    surface = CommandPanel,
    surfaceVariant = Color(0xFF2A3340),
    surfaceContainer = CommandPanelRaised,
    onBackground = CommandText,
    onSurface = CommandText,
    onSurfaceVariant = CommandMuted,
    outline = Color(0xFF3A4555)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF08764D),
    secondary = Color(0xFF146B8D),
    tertiary = Color(0xFF8A5A00),
    error = Color(0xFFC73532),
    background = CommandLight,
    surface = Color.White,
    surfaceVariant = Color(0xFFE0E7EF),
    surfaceContainer = Color(0xFFEAF0F6),
    onBackground = Color(0xFF111820),
    onSurface = Color(0xFF111820),
    onSurfaceVariant = Color(0xFF566273),
    outline = Color(0xFFC6D0DC)

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

@Composable
fun NGXHttpMonitoringClientTheme(
    darkTheme: Boolean = true,
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
