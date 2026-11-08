package com.midad.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.midad.app.R

/*
 * لوحة ألوان مِداد — نفس متغيرات :root في الويب حرفيًا
 */
@Immutable
data class MidadColors(
    val bg: Color = Color(0xFF151310),
    val bgDeep: Color = Color(0xFF0C0B09),
    val card: Color = Color(0xFF1E1B17),
    val card2: Color = Color(0xFF272219),
    val well: Color = Color(0x47000000),
    val ink: Color = Color(0xFFEAE3D0),
    val inkSoft: Color = Color(0xFFA19882),
    val inkFaint: Color = Color(0xFF6F6757),
    val line: Color = Color(0xFF342E25),
    val lineSoft: Color = Color(0xFF282218),
    val acc: Color = Color(0xFFF2C14E),
    val accInk: Color = Color(0xFF211804),
    val accDim: Color = Color(0x29F2C14E),
    val ok: Color = Color(0xFF9BB77F),
    val red: Color = Color(0xFFE06C55),
    val codeBg: Color = Color(0xFF0B0A08),
    val codeLine: Color = Color(0xFF241F17),
    val codeInk: Color = Color(0xFFCFC7B2),
    // ألوان تلوين السكربت (tk-* في الويب)
    val tkK: Color = Color(0xFFF2C14E),
    val tkS: Color = Color(0xFFB0D88A),
    val tkN: Color = Color(0xFFE8A46D),
    val tkC: Color = Color(0xFF6E675A),
    val tkF: Color = Color(0xFF82C7E0),
    val tkP: Color = Color(0xFFD4A5E0)
)

val LocalColors = staticCompositionLocalOf { MidadColors() }

@Immutable
data class MidadFonts(
    val sans: FontFamily = FontFamily(
        Font(R.font.plexarabic_regular, FontWeight.Normal),
        Font(R.font.plexarabic_medium, FontWeight.Medium),
        Font(R.font.plexarabic_semibold, FontWeight.SemiBold),
        Font(R.font.plexarabic_bold, FontWeight.Bold)
    ),
    val mono: FontFamily = FontFamily(
        Font(R.font.plexmono_regular, FontWeight.Normal),
        Font(R.font.plexmono_medium, FontWeight.Medium)
    )
)

val LocalFonts = staticCompositionLocalOf { MidadFonts() }

@Composable
fun MidadTheme(content: @Composable () -> Unit) {
    val colors = MidadColors()
    val fonts = MidadFonts()
    CompositionLocalProvider(LocalColors provides colors, LocalFonts provides fonts) {
        content()
    }
}

/* نص أساسي خفيف فوق BasicText — بلا تبعيات Material */
@Composable
fun MText(
    text: String,
    color: Color = LocalColors.current.ink,
    fontSize: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    lineHeight: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    softWrap: Boolean = true,
    modifier: Modifier = Modifier,
    letterSpacing: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    overflow: androidx.compose.ui.text.style.TextOverflow = androidx.compose.ui.text.style.TextOverflow.Clip,
    textAlign: androidx.compose.ui.text.style.TextAlign? = null,
    textDecoration: androidx.compose.ui.text.style.TextDecoration? = null,
    style: androidx.compose.ui.text.TextStyle? = null,
    onTextLayout: ((androidx.compose.ui.text.TextLayoutResult) -> Unit)? = null
) {
    // Compose 1.8: البناء عبر SpanStyle + ParagraphStyle
    val span = androidx.compose.ui.text.SpanStyle(
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration
    )
    val para = androidx.compose.ui.text.ParagraphStyle(
        textAlign = textAlign ?: androidx.compose.ui.text.style.TextAlign.Unspecified,
        lineHeight = lineHeight
    )
    val defaultStyle = androidx.compose.ui.text.TextStyle.Default
        .merge(span)
        .merge(para)
    val base = style?.merge(defaultStyle) ?: defaultStyle
    androidx.compose.foundation.text.BasicText(
        text = text,
        modifier = modifier,
        maxLines = maxLines,
        softWrap = softWrap,
        overflow = overflow,
        style = base,
        onTextLayout = onTextLayout
    )
}

