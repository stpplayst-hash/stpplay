package com.stpplay.android.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * STP Play Premium Design System
 * Uma paleta de cores moderna com foco em alto contraste e legibilidade.
 */

// Fundos e Superfícies (Escala de Cinzas Profundos)
val StpBackground = Color(0xFF0D0D0D)      // Fundo ultra escuro
val StpSurface = Color(0xFF161616)         // Cards e menus
val StpSurfaceHigh = Color(0xFF1F1F1F)     // Inputs e detalhes
val StpSurfaceHighest = Color(0xFF2B2B2B)  // Hover e chips
val StpSurfaceVariant = Color(0xFF333333)  // Progress bars

// Cores de Vidro (Glassmorphism)
val GlassWhite10 = Color(0x1AFFFFFF)        // Branco 10%
val GlassWhite20 = Color(0x33FFFFFF)        // Branco 20%
val GlassBlack50 = Color(0x80000000)        // Preto 50%
val GlassBorder = Color(0x1AFFFFFF)         // Borda sutil de vidro

// Cores de Destaque (Premium Gold & Neon)
val DefaultPrimary = Color(0xFFFFD60A)          // Ouro vibrante (Apple-style)
val StpPrimary = DefaultPrimary // Alias para compatibilidade enquanto migramos
val StpPrimaryContainer = Color(0xFFFFE16D)
val StpSecondary = Color(0xFF32D74B)        // Verde vivo para "Live"
val StpRed = Color(0xFFFF453A)               // Vermelho sistema

// Tipografia e Iconografia
val StpOnSurface = Color(0xFFF2F2F7)         // Texto primário (quase branco)
val StpOnSurfaceVariant = Color(0xFF8E8E93)  // Texto secundário (cinza)
val StpOutline = Color(0xFF38383A)           // Bordas e divisores

// Cores Dinâmicas por Conteúdo
val TileLive = Color(0xFF30D158)
val TileMovies = Color(0xFFFFD60A)
val TileSeries = Color(0xFFFF375F)
val TileGuide = Color(0xFF30D158)
val TileFavorites = Color(0xFFFF2D55)

// Gradientes
val StpPremiumGradient = listOf(StpPrimary, Color(0xFFBF953F), StpPrimary)
