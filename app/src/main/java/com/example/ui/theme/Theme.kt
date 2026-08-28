package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Cyber-Trading Cockpit Shapes: Cartões com cantos arredondados (12.dp)
val CyberShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp), // Cartões com cantos arredondados (12.dp)
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp)
)

private val DarkColorScheme = darkColorScheme(
    primary = CyberTradingGreen,
    onPrimary = Color(0xFF022C22),
    primaryContainer = CyberTradingGreenContainer,
    onPrimaryContainer = CyberTextPrimary,
    
    secondary = CyberSkyBlue,
    onSecondary = Color(0xFF082F49),
    secondaryContainer = CyberSkyBlueContainer,
    onSecondaryContainer = CyberTextPrimary,
    
    background = ObsidianDarkBg, // Fundo Escuro Profundo (#090D16)
    onBackground = CyberTextPrimary,
    
    surface = DarkSlateSurface, // Dark Slate (#0F172A)
    onSurface = CyberTextPrimary,
    
    surfaceVariant = Slate800SurfaceVariant, // Camadas semitransparentes
    onSurfaceVariant = CyberTextSecondary,
    
    outline = CyberLuminousBorder, // Bordas finas luminosas de 1.dp
    outlineVariant = CyberBorderSubtle,
    
    error = Color(0xFFEF4444),
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Forced Cyber-Trading Dark Mode
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        shapes = CyberShapes,
        content = content
    )
}

