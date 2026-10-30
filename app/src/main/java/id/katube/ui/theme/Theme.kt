package id.katube.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val KATubeDarkColorScheme = darkColorScheme(
    primary          = Gold,
    onPrimary        = OnGold,
    secondary        = GoldDark,
    onSecondary      = OnGold,
    background       = BackgroundDark,
    onBackground     = OnBackgroundDark,
    surface          = SurfaceDark,
    onSurface        = OnBackgroundDark,
    surfaceVariant   = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    error            = ErrorDark,
    outline          = DividerDark,
)

private val KATubeLightColorScheme = lightColorScheme(
    primary          = GoldAccessible,
    onPrimary        = BackgroundLight,
    secondary        = GoldDark,
    onSecondary      = BackgroundLight,
    background       = BackgroundLight,
    onBackground     = OnBackgroundLight,
    surface          = SurfaceLight,
    onSurface        = OnBackgroundLight,
    surfaceVariant   = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    error            = ErrorLight,
    outline          = DividerLight,
)

/**
 * Tema utama KATube.
 * - Default: gelap (#0F0F0F + emas)
 * - Mendukung mode terang dan gelap (ikut sistem atau pengaturan pengguna)
 * - Dynamic Color dinonaktifkan agar aksen emas KATube selalu konsisten
 */
@Composable
fun KATubeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) KATubeDarkColorScheme else KATubeLightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = KATubeTypography,
        shapes      = KATubeShapes,
        content     = content
    )
}
