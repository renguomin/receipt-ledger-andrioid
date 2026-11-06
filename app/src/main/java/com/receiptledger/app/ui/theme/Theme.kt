package com.receiptledger.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.receiptledger.app.R

private val Colors = lightColorScheme(
    primary = Color(0xFF4C6EF5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE3FF),
    onPrimaryContainer = Color(0xFF1B2A6B),
    secondary = Color(0xFFE08A4C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE4CC),
    onSecondaryContainer = Color(0xFF5A3310),
    background = Color(0xFFF4F6FB),
    onBackground = Color(0xFF1C2434),
    surface = Color.White,
    onSurface = Color(0xFF1C2434),
    surfaceVariant = Color(0xFFE8ECF5),
    onSurfaceVariant = Color(0xFF5C6578),
    outline = Color(0xFFC5CBD8),
    error = Color(0xFFC23B3B),
)

private val Nunito = FontFamily(
    Font(R.font.nunito_medium, FontWeight.Medium),
    Font(R.font.nunito_semibold, FontWeight.SemiBold),
    Font(R.font.nunito_bold, FontWeight.Bold),
)

private val DefaultType = Typography()

private fun TextStyle.solid(weight: FontWeight = FontWeight.Medium): TextStyle =
    copy(fontFamily = Nunito, fontWeight = weight)

private val AppTypography = Typography(
    displayLarge = DefaultType.displayLarge.solid(FontWeight.Bold),
    displayMedium = DefaultType.displayMedium.solid(FontWeight.Bold),
    displaySmall = DefaultType.displaySmall.solid(FontWeight.SemiBold),
    headlineLarge = DefaultType.headlineLarge.solid(FontWeight.Bold),
    headlineMedium = DefaultType.headlineMedium.solid(FontWeight.SemiBold),
    headlineSmall = DefaultType.headlineSmall.solid(FontWeight.SemiBold),
    titleLarge = DefaultType.titleLarge.solid(FontWeight.SemiBold),
    titleMedium = DefaultType.titleMedium.solid(FontWeight.SemiBold),
    titleSmall = DefaultType.titleSmall.solid(FontWeight.SemiBold),
    bodyLarge = DefaultType.bodyLarge.solid(FontWeight.Medium),
    bodyMedium = DefaultType.bodyMedium.solid(FontWeight.Medium),
    bodySmall = DefaultType.bodySmall.solid(FontWeight.Medium),
    labelLarge = DefaultType.labelLarge.solid(FontWeight.SemiBold),
    labelMedium = DefaultType.labelMedium.solid(FontWeight.SemiBold),
    labelSmall = DefaultType.labelSmall.solid(FontWeight.SemiBold),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(16.dp),
    small = RoundedCornerShape(22.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun ReceiptLedgerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Colors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
