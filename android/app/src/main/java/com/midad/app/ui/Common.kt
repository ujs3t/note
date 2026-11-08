package com.midad.app.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midad.app.glass.GlassSurface

/*
 * مكونات مشتركة — نفس سلوك وأبعاد الويب
 */

/** منحنى الويب المطاطي cubic-bezier(.34,1.56,.64,1) */
val JELLY = spring<Float>(dampingRatio = 0.48f, stiffness = Spring.StiffnessMedium)
val EASE_OUT_QUICK = tween<Float>(100)

/** زر أيقونة زجاجي دائري (أزرار رأس المحرر في الويب) — 40dp، زجاج نصف قطره 20dp */
@Composable
fun GlassIconButton(
    icon: String,
    tint: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    store: com.midad.app.store.AppStore? = null,
    sizeDp: Dp = 40.dp,
    onClick: () -> Unit = {}
) {
    var pressScale by remember { mutableStateOf(1f) }
    val scale by animateFloatAsState(pressScale, JELLY, label = "gib")
    GlassSurface(
        radius = 20.dp,
        tintAlpha = 0.045f,
        modifier = modifier
            .size(sizeDp)
            .scale(scale)
            .pointerInput(enabled) {
                if (enabled) detectTapGestures(
                    onPress = {
                        pressScale = 0.8f
                        try { awaitRelease() } finally {
                            pressScale = 1f
                            store?.vibrate()
                            onClick()
                        }
                    }
                )
            }
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            MidadIcon(icon, 20.dp, if (enabled) tint else tint.copy(alpha = 0.28f))
        }
    }
}

/** زر أيقونة عادي شفاف — icon-btn في الويب */
@Composable
fun IconBtn(
    icon: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = false,
    sizeDp: Dp = 40.dp,
    iconSize: Dp = 20.dp
) {
    val colors = LocalColors.current
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.8f else 1f, if (pressed) EASE_OUT_QUICK else JELLY, label = "ib")
    Box(
        modifier
            .size(sizeDp)
            .scale(scale)
            .clip(CircleShape)
            .background(
                when {
                    !enabled -> Color.Transparent
                    pressed && danger -> colors.red.copy(alpha = 0.22f)
                    pressed -> Color.White.copy(alpha = 0.09f)
                    else -> Color.Transparent
                }
            )
            .pointerInput(enabled) {
                if (enabled) detectTapGestures(
                    onPress = {
                        pressed = true
                        try { awaitRelease() } finally { pressed = false; onClick() }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        MidadIcon(icon, iconSize, if (enabled) tint else tint.copy(alpha = 0.28f))
    }
}

/** مفتاح التبديل — 46×27، مقبض 19، عنبري عند التفعيل (مطابق للويب) */
@Composable
fun MSwitch(
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalColors.current
    val anim by animateFloatAsState(if (checked) 1f else 0f, JELLY, label = "sw")
    Box(
        modifier
            .width(46.dp)
            .height(27.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (checked) colors.acc else Color.White.copy(alpha = 0.14f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onToggle() },
        contentAlignment = Alignment.CenterStart
    ) {
        // الويب: المقبض يبدأ من يمين المفتاح (RTL) وينزلق 19px نحو اليسار
        Box(
            Modifier
                .padding(start = 3.dp)
                .offset(x = 19.dp * anim)
                .size(19.dp)
                .clip(CircleShape)
                .background(if (checked) colors.accInk else colors.ink)
        )
    }
}

/** زر أساسي عنبري (primary-btn / tp-ok) — ضغط 0.96 مطاطي */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    corner: Dp = 14.dp,
    fontSize: Int = 15
) {
    val colors = LocalColors.current
    val fonts = LocalFonts.current
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, JELLY, label = "pb")
    Box(
        modifier
            .scale(scale)
            .clip(RoundedCornerShape(corner))
            .background(colors.acc)
            .clickable(enabled = enabled, indication = null, interactionSource = remember { MutableInteractionSource() }) {
                onClick()
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        MText(
            text = text,
            color = colors.accInk,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = fonts.sans
        )
    }
}

