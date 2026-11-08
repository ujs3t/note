package com.midad.app.glass

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.roundToInt
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.midad.app.store.GlassSettings

/*
 * ============================================================
 *  محرك الزجاج السائل الأصلي لأندرويد — منقول من مهارة الويب
 *  نفس الرياضيات حرفيًا: انكسار سنيل + سطح convexSquircle +
 *  ظل داخلي حاد (زاوية 55، شدة 0.36) — لكن بتظليل AGSL
 *  يعمل بالكامل على GPU: خفيف جدًا وسلس مثل iOS.
 *  (الويب: SVG filter + backdrop-filter / الأندرويد: AGSL
 *   + RenderEffect + GraphicsLayer — نفس الناتج البصري)
 * ============================================================
 */

/* نفس ثوابت المهارة الافتراضية: ior 1.5، سماكة 40، حواف 50، ظل 1/55/0.36 */
const val GLASS_IOR = 1.5f
const val GLASS_THICKNESS_DP = 40f
const val GLASS_BEZEL_DP = 50f
const val GLASS_SHADOW_THICKNESS_DP = 1f
const val GLASS_SHADOW_ANGLE = 55f
const val GLASS_SHADOW_INTENSITY = 0.36f

/**
 * ظل AGSL — ترجمة حرفية لدوال المهارة:
 * convexSquircle(x) = pow(1-(1-x)^4, 1/4)
 * dispAt: انكسار سنيل مع ميل تحليلي رقمي (d=0.001)
 * paintShadow: الحزام الداخلي مع edgeAA والقرون الحادة k^1.8
 * إزاحة أخذ العينة = -n*disp (مطابقة لإشارة feDisplacementMap)
 */
private const val GLASS_AGSL = """
uniform shader uContent;
uniform float2 uSize;
uniform float uRadius;
uniform float uBezel;
uniform float uThickness;
uniform float uIor;
uniform float uShadowThickness;
uniform float uShadowAngleDeg;
uniform float uShadowIntensity;

half4 main(float2 xy) {
    float hw = uSize.x * 0.5;
    float hh = uSize.y * 0.5;
    float r = min(uRadius, min(hw, hh));
    float2 p = xy - float2(hw, hh);
    float sx = p.x < 0.0 ? -1.0 : 1.0;
    float sy = p.y < 0.0 ? -1.0 : 1.0;
    float2 q = abs(p) - float2(hw - r, hh - r);
    float dd = length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - r;

    float2 n;
    if (q.x > 0.0 && q.y > 0.0) {
        float l = length(q) + 0.0001;
        n = float2(sx * q.x / l, sy * q.y / l);
    } else {
        n = (q.x > q.y) ? float2(sx, 0.0) : float2(0.0, sy);
    }

    float2 uv = xy;
    if (dd < 0.0 && uBezel > 0.5 && uThickness > 0.5) {
        float x = clamp(min(-dd, uBezel) / uBezel, 0.0, 1.0);
        float h = pow(max(0.0, 1.0 - pow(1.0 - x, 4.0)), 0.25) * uThickness;
        float d0 = 0.001;
        float x1 = max(0.0, x - d0);
        float x2 = min(1.0, x + d0);
        float s1 = pow(max(0.0, 1.0 - pow(1.0 - x1, 4.0)), 0.25);
        float s2 = pow(max(0.0, 1.0 - pow(1.0 - x2, 4.0)), 0.25);
        float slope = ((s2 - s1) / (x2 - x1)) * uThickness / uBezel;
        float2 N = normalize(float2(-slope, 1.0));
        float2 I = float2(0.0, -1.0);
        float eta = 1.0 / uIor;
        float cosI = -dot(I, N);
        float sin2T = eta * eta * (1.0 - cosI * cosI);
        if (sin2T <= 1.0) {
            float cosT = sqrt(1.0 - sin2T);
            float2 T = eta * I + (eta * cosI - cosT) * N;
            if (T.y != 0.0) {
                float disp = (-h / T.y) * T.x;
                uv = xy - n * disp;
            }
        }
    }

    half4 c = uContent.eval(uv);

    float feather = max(0.9, uShadowThickness * 0.15);
    float a = 0.0;
    if (dd <= feather && -dd <= uShadowThickness + feather) {
        float edge = -dd;
        float band = clamp((uShadowThickness - edge) / max(uShadowThickness, 0.0001), 0.0, 1.0);
        float sharp = pow(band, 0.6);
        float edgeAA = clamp((feather - dd) / (2.0 * feather), 0.0, 1.0);
        float rad = uShadowAngleDeg * 0.0174532925;
        float2 sdir = float2(cos(rad), sin(rad));
        float k = abs(dot(n, sdir));
        a = sharp * edgeAA * pow(k, 1.8) * uShadowIntensity;
    }

    return half4(mix(c.rgb, half3(1.0, 1.0, 1.0), half(a)), c.a);
}
"""

/** لون الزجاج عند تعطّل المحرك — مطابق لـ #app.perf .glass في الويب rgba(30,27,23,.88) */
val GLASS_FALLBACK = Color(0xE01E1B17)

/** لون البحث في وضع التوفير — مطابق لـ #app.perf .search.glass rgba(39,34,25,.94) */
val GLASS_FALLBACK_SEARCH = Color(0xF0272219)

/** مصدر الخلفية المسجّل الذي يتغذى عليه الزجاج (طبقة GPU واحدة لكل شاشة) */
class BackdropSource(val layer: GraphicsLayer) {
    var origin by mutableStateOf(Offset.Zero)
}

/** يُنشئ مصدر خلفية لكل شاشة — تمرَّر إلى BackdropHost وكل الأسطح الزجاجية فيها */
@Composable
fun rememberBackdropSource(): BackdropSource {
    val layer = rememberGraphicsLayer()
    return remember { BackdropSource(layer) }
}

val LocalBackdrop = staticCompositionLocalOf<BackdropSource?> { null }
val LocalGlassSettings = staticCompositionLocalOf { GlassSettings() }

/** تزويد إعدادات الزجاج لكل الأسطح */
@Composable
fun CompositionLocalGlassSettings(settings: GlassSettings, content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(LocalGlassSettings provides settings) {
        content()
    }
}

/** يُسجّل محتوى الشاشة في GraphicsLayer ليلتقطه الزجاج خلفه — نفس فكرة backdrop-filter */
@Composable
fun BackdropHost(
    source: BackdropSource,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier
            .onGloballyPositioned { source.origin = it.positionInRoot() }
            .drawWithContent {
                source.layer.record(
                    this, layoutDirection,
                    androidx.compose.ui.unit.IntSize(size.width.roundToInt(), size.height.roundToInt())
                ) {
                    this@drawWithContent.drawContent()
                }
                drawContent()
            }
    ) {
        content()
    }
}

/** بناء تأثير GPU: تشبّع ← ضبابية ← انكسار+ظل داخلي (سلسلة RenderEffect) */
private fun buildGlassEffect(
    s: GlassSettings,
    size: IntSize,
    radiusPx: Float,
    bezelPx: Float,
    thicknessPx: Float,
    blurPx: Float,
    shadowThicknessPx: Float,
    density: Float
): android.graphics.RenderEffect? {
    if (size.width < 4 || size.height < 4) return null
    return try {
        if (Build.VERSION.SDK_INT >= 33) {
            val shader = RuntimeShader(GLASS_AGSL)
            shader.setFloatUniform("uSize", size.width.toFloat(), size.height.toFloat())
            shader.setFloatUniform("uRadius", radiusPx)
            shader.setFloatUniform("uBezel", bezelPx)
            shader.setFloatUniform("uThickness", thicknessPx)
            shader.setFloatUniform("uIor", GLASS_IOR)
            shader.setFloatUniform("uShadowThickness", shadowThicknessPx)
            shader.setFloatUniform("uShadowAngleDeg", GLASS_SHADOW_ANGLE)
            shader.setFloatUniform("uShadowIntensity", GLASS_SHADOW_INTENSITY)
            val shaderEffect = RenderEffect.createRuntimeShaderEffect(shader, "uContent")
            val sat = if (s.specularSaturation > 0.01f) {
                val cm = ColorMatrix().apply { setSaturation(1f + s.specularSaturation * 0.07f) }
                RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(cm))
            } else null
            val blur = if (blurPx > 0.5f) RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP) else null
            val inner = when {
                blur != null && sat != null -> RenderEffect.createChainEffect(sat, blur)
                blur != null -> blur
                sat != null -> sat
                else -> null
            }
            if (inner != null) RenderEffect.createChainEffect(shaderEffect, inner) else shaderEffect
        } else if (Build.VERSION.SDK_INT >= 31 && blurPx > 0.5f) {
            RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP)
        } else null
    } catch (t: Throwable) { null }
}

/**
 * سطح زجاجي شفّال — انكسار حقيقي لما خلفه عند التمرير + لمعان + ظل داخلي.
 * يقابل class="glass" في الويب: الخلفية الشفافة = glassBgOpacity*0.15.
 */
@Composable
fun GlassSurface(
    radius: Dp,
    modifier: Modifier = Modifier,
    settings: GlassSettings = LocalGlassSettings.current,
    tintAlpha: Float? = null,
    tintColor: Color = Color.White,
    fallbackColor: Color = GLASS_FALLBACK,
    content: @Composable BoxScope.() -> Unit
) {
    val density = LocalDensity.current
    val backdrop = LocalBackdrop.current
    var sizePx by remember { mutableStateOf(IntSize.Zero) }
    var posInRoot by remember { mutableStateOf(Offset.Zero) }

    val radiusPx = with(density) { radius.toPx() }
    val shape = RoundedCornerShape(radius)

    val effect = remember(
        settings.on, settings.blur, settings.refractionLevel, settings.specularSaturation,
        sizePx, radiusPx, density.density
    ) {
        if (!settings.on) null else buildGlassEffect(
            s = settings,
            size = sizePx,
            radiusPx = radiusPx,
            bezelPx = with(density) { GLASS_BEZEL_DP.dp.toPx() },
            thicknessPx = with(density) { GLASS_THICKNESS_DP.dp.toPx() } * settings.refractionLevel,
            blurPx = with(density) { 14.dp.toPx() } * settings.blur,
            shadowThicknessPx = with(density) { GLASS_SHADOW_THICKNESS_DP.dp.toPx() },
            density = density.density
        )
    }

    // الزجاج نشط فقط مع محرّك GPU حقيقي — وإلا نستخدم البديل الداكن مثل #app.perf في الويب
    val active = settings.on && backdrop != null && effect != null

    Box(
        modifier.onGloballyPositioned {
            posInRoot = it.positionInRoot()
            sizePx = it.size
        }
    ) {
        if (active) {
            // 1) طبقة الانكسار: خلفية الشاشة المسجّلة + سلسلة التأثيرات (GPU فقط)
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        this.shape = shape
                        this.clip = true
                        if (effect != null) {
                            renderEffect = effect.asComposeRenderEffect()
                        } else {
                            // أجهزة أقدم من 31: زجاج ثابت بلا ضبابية
                            this.alpha = 0.99f
                        }
                    }
                    .drawWithContent {
                        if (effect == null) {
                            drawRect(GLASS_FALLBACK)
                        } else {
                            translate(
                                backdrop.origin.x - posInRoot.x,
                                backdrop.origin.y - posInRoot.y
                            ) {
                                drawLayer(backdrop.layer)
                            }
                        }
                    }
            )

            // 2) لمعان السطح (specular) — أعلى السطح كما في الويب
            Box(
                Modifier
                    .matchParentSize()
                    .clip(shape)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.30f * settings.specularOpacity),
                                Color.Transparent
                            ),
                            startY = 0f,
                            endY = sizePx.height * 0.55f
                        )
                    )
            )

            // 3) صبغة الزجاج — rgba(255,255,255, glassBgOpacity*0.15) مثل الويب
            Box(
                Modifier
                    .matchParentSize()
                    .clip(shape)
                    .background(tintColor.copy(alpha = tintAlpha ?: (settings.glassBgOpacity * 0.15f)))
            )
        } else {
            // وضع التوفير — مطابق لـ #app.perf .glass في الويب: زجاج داكن 88%
            Box(
                Modifier
                    .matchParentSize()
                    .clip(shape)
                    .background(fallbackColor)
            )
        }

        // المحتوى فوق الزجاج (z-index 3 في الويب) — يحدد حجم السطح
        Box(Modifier) { content() }
    }
}

/** مجرّد صغير: لفّ أي عنصر بزجاج سائل بأبعاد محددة */
@Immutable
data class GlassSpec(val radius: Dp, val tintAlpha: Float? = null)
