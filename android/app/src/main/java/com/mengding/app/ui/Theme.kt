package com.mengding.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** 主色薄荷绿，辅色奶油、樱花粉（需求 U3），带深色模式 */
data class Palette(
    val mint: Color, val mintDeep: Color, val mintSoft: Color,
    val pink: Color, val pinkSoft: Color, val cream: Color,
    val ink: Color, val sub: Color, val line: Color, val card: Color,
    val gold: Color, val blue: Color, val purple: Color, val danger: Color,
)

val LightPalette = Palette(
    mint = Color(0xFF2FB38C), mintDeep = Color(0xFF1E8C6C), mintSoft = Color(0xFFD6F7EA),
    pink = Color(0xFFFF8FA8), pinkSoft = Color(0xFFFFE3EA), cream = Color(0xFFFFFBF4),
    ink = Color(0xFF3A2C36), sub = Color(0xFF8C7A86), line = Color(0xFFF0E6DC), card = Color.White,
    gold = Color(0xFFF2B43C), blue = Color(0xFF6EB8F0), purple = Color(0xFFA48BE0), danger = Color(0xFFE5606F),
)

val DarkPalette = Palette(
    mint = Color(0xFF6FDDB8), mintDeep = Color(0xFF9BEBCF), mintSoft = Color(0xFF214238),
    pink = Color(0xFFFF9DB4), pinkSoft = Color(0xFF4A2A35), cream = Color(0xFF1B2422),
    ink = Color(0xFFF1E8EE), sub = Color(0xFFA9A0A6), line = Color(0xFF34403C), card = Color(0xFF243230),
    gold = Color(0xFFF5C25C), blue = Color(0xFF8CC8F5), purple = Color(0xFFBCA6F0), danger = Color(0xFFF07B88),
)

val LocalPalette = staticCompositionLocalOf { LightPalette }
val LocalUiTheme = staticCompositionLocalOf { Themes.classic }

@Composable
fun MengDingTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val cfg by com.mengding.app.data.Store.config.collectAsState()
    val ui = Themes.of(cfg.settings.uiTheme)
    // 界面主题替换主色：按钮、选中态、进度条等所有用 mint 的地方
    val base = if (dark) DarkPalette else LightPalette
    val p = if (dark) base.copy(mint = ui.darkPrimary, mintDeep = ui.darkPrimary, mintSoft = ui.primaryDeep.copy(alpha = 0.35f))
    else base.copy(mint = ui.primary, mintDeep = ui.primaryDeep, mintSoft = ui.primarySoft)
    val scheme = if (dark) darkColorScheme(
        primary = p.mint, onPrimary = Color(0xFF0E2A22), primaryContainer = p.mintSoft,
        secondary = p.pink, background = p.cream, surface = p.card, onSurface = p.ink, onBackground = p.ink,
        surfaceVariant = p.line, outline = p.line,
    ) else lightColorScheme(
        primary = p.mint, onPrimary = Color.White, primaryContainer = p.mintSoft,
        secondary = p.pink, background = p.cream, surface = p.card, onSurface = p.ink, onBackground = p.ink,
        surfaceVariant = p.line, outline = p.line,
    )
    val base0 = Typography(
        headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = p.ink),
        titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = p.ink),
        bodyMedium = TextStyle(fontSize = 15.sp, color = p.ink),
        bodySmall = TextStyle(fontSize = 13.sp, color = p.sub),
        labelMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
    )
    // 自定义字体（我的 → 显示字体）：所有文字样式都换成它
    val ctx = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(cfg.settings.fontKey) { AppFonts.load(ctx) }
    val ff by AppFonts.family.collectAsState()
    val type = ff?.let { f ->
        fun TextStyle.withFont() = copy(fontFamily = f)
        base0.copy(
            displayLarge = base0.displayLarge.withFont(), displayMedium = base0.displayMedium.withFont(),
            displaySmall = base0.displaySmall.withFont(), headlineLarge = base0.headlineLarge.withFont(),
            headlineMedium = base0.headlineMedium.withFont(), headlineSmall = base0.headlineSmall.withFont(),
            titleLarge = base0.titleLarge.withFont(), titleMedium = base0.titleMedium.withFont(),
            titleSmall = base0.titleSmall.withFont(), bodyLarge = base0.bodyLarge.withFont(),
            bodyMedium = base0.bodyMedium.withFont(), bodySmall = base0.bodySmall.withFont(),
            labelLarge = base0.labelLarge.withFont(), labelMedium = base0.labelMedium.withFont(),
            labelSmall = base0.labelSmall.withFont(),
        )
    } ?: base0
    androidx.compose.runtime.CompositionLocalProvider(LocalPalette provides p, LocalUiTheme provides ui) {
        MaterialTheme(colorScheme = scheme, typography = type, content = content)
    }
}
