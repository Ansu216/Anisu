package com.ansu.anime.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ansu.anime.ui.theme.AnsuColors
import kotlin.math.roundToInt

/**
 * Pixel OS (Android 16 Material 3 Expressive) Frosted Glass Surface.
 *
 * Samples the shared blurred [LocalGlassBackground] lying directly behind this card's window coordinates,
 * then layers a matte tonal surface fill, optional top specular sheen, and 1dp hairline border driven
 * live by [GlassTuning].
 */
@Composable
fun FrostedGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    tintAlpha: Float = GlassTuning.cardTintAlpha,
    sampleBackground: Boolean = true,
    content: @Composable () -> Unit,
) {
    val glassBackground = LocalGlassBackground.current
    val place = remember { GlassCardPlace() }
    val effectiveTint = tintAlpha.coerceIn(0f, 1f)
    val sheenAlpha = GlassTuning.topSheenAlpha.coerceIn(0f, 0.5f)
    val rimAlpha = GlassTuning.borderAlpha.coerceIn(0f, 0.8f)

    Box(
        modifier = modifier
            .clip(shape)
            .onGloballyPositioned { place.origin = it.positionInWindow() }
            .drawBehind {
                // Sample the blurred background slice lying behind this card
                if (sampleBackground) drawGlassBackground(glassBackground, place)
            },
    ) {
        val backgroundModifier = Modifier
            .matchParentSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        AnsuColors.SurfaceGlassBase.copy(alpha = effectiveTint),
                        AnsuColors.SurfaceGlassBase.copy(alpha = (effectiveTint * 0.86f).coerceIn(0f, 1f)),
                    ),
                ),
            )
            .then(
                if (sheenAlpha > 0.001f) {
                    Modifier.background(
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = sheenAlpha),
                                Color.White.copy(alpha = sheenAlpha * 0.15f),
                            ),
                        ),
                    )
                } else {
                    Modifier
                },
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = rimAlpha),
                        Color.White.copy(alpha = rimAlpha * 0.35f),
                    ),
                ),
                shape = shape,
            )

        Box(modifier = backgroundModifier)
        content()
    }
}

/**
 * Bottom-anchored gradient so overlaid title/button text stays legible on any art.
 */
@Composable
fun BottomScrim(modifier: Modifier = Modifier, midAlpha: Float = 0.35f, midStop: Float = 0.55f) {
    Box(
        modifier = modifier.background(
            Brush.verticalGradient(
                0f to Color.Transparent,
                midStop to Color.Black.copy(alpha = midAlpha),
                1f to AnsuColors.Background,
            ),
        ),
    )
}

/**
 * Driven live by [GlassTuning.cardTintAlpha] so all [HeavyGlassCard] instances across the app
 * display the Pixel OS frosted blur instead of turning 100% solid opaque.
 */
val GLASS_MIN_TINT: Float
    get() = GlassTuning.cardTintAlpha

/** The least tint a card in a full-screen panel gets (see the `overlay` flag of [HeavyGlassCard]). */
const val OVERLAY_MIN_TINT: Float = 0.90f

/**
 * A [FrostedGlassCard] for content cards and buttons that now participates in the Pixel OS
 * frosted backdrop blur and reacts live to [GlassTuning].
 */
@Composable
fun HeavyGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    tintAlpha: Float = GlassTuning.cardTintAlpha,
    sampleBackground: Boolean = true,
    overlay: Boolean = false,
    content: @Composable () -> Unit,
) = FrostedGlassCard(
    modifier = modifier,
    shape = shape,
    // A card in a full-screen panel (welcome, error, links) sits over page content the shared glass copy never
    // contains, so it needs a firm tint of its own: at the card tint the sharp page showed straight through it.
    tintAlpha = if (overlay) tintAlpha.coerceAtLeast(OVERLAY_MIN_TINT) else tintAlpha.coerceAtMost(GlassTuning.cardTintAlpha),
    sampleBackground = sampleBackground,
    content = content,
)

/**
 * Interactive In-App Frosted Glass Live Preview + Fine-Tuning Sliders.
 * Place `GlassFineTuneCard()` inside `AppearanceScreen.kt` or `GlassSettings.kt` so users can
 * see a live preview card and drag sliders to customize the frosted blur across the whole app.
 */
@Composable
fun GlassFineTuneCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        GlassTuning.load(context)
    }

    val previewGlassBg = rememberGlassBackground()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 1. Live Interactive Preview Sandbox
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(148.dp)
                .clip(RoundedCornerShape(24.dp)),
        ) {
            // Vivid color-bloom canvas recorded as the backdrop source for the preview card
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .recordGlassBackground(previewGlassBg, AnsuColors.Background),
            ) {
                drawRect(AnsuColors.Background)
                drawCircle(
                    color = Color(0xFF7C4DFF).copy(alpha = 0.75f),
                    radius = size.minDimension * 0.55f,
                    center = Offset(size.width * 0.25f, size.height * 0.35f),
                )
                drawCircle(
                    color = Color(0xFFFF4081).copy(alpha = 0.65f),
                    radius = size.minDimension * 0.50f,
                    center = Offset(size.width * 0.75f, size.height * 0.65f),
                )
                drawCircle(
                    color = Color(0xFF00E5FF).copy(alpha = 0.60f),
                    radius = size.minDimension * 0.45f,
                    center = Offset(size.width * 0.55f, size.height * 0.25f),
                )
            }

            // Live Frosted Card floating over the recorded preview canvas
            CompositionLocalProvider(LocalGlassBackground provides previewGlassBg) {
                FrostedGlassCard(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth(0.88f),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "Pixel OS Frosted Glass Live Preview",
                            color = AnsuColors.TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Blur: ${GlassTuning.blurRadiusDp.roundToInt()}dp • Card: ${(GlassTuning.cardTintAlpha * 100).roundToInt()}% • Menu: ${(GlassTuning.popupTintAlpha * 100).roundToInt()}%",
                            color = AnsuColors.TextPrimary.copy(alpha = 0.80f),
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }

        // 2. Sliders Control Card
        FrostedGlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Fine-Tune Frosted Blur",
                        color = AnsuColors.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Reset to Pixel OS",
                        color = AnsuColors.TextPrimary.copy(alpha = 0.80f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { GlassTuning.resetToPixelOsDefaults(context) }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }

                GlassTuningSliderRow(
                    label = "Backdrop Blur Radius",
                    valueText = "${GlassTuning.blurRadiusDp.roundToInt()} dp",
                    value = GlassTuning.blurRadiusDp,
                    valueRange = 0f..60f,
                    onValueChange = {
                        GlassTuning.blurRadiusDp = it
                        GlassTuning.save(context)
                    },
                )

                GlassTuningSliderRow(
                    label = "Card Frosted Opacity",
                    valueText = "${(GlassTuning.cardTintAlpha * 100).roundToInt()}%",
                    value = GlassTuning.cardTintAlpha,
                    valueRange = 0.15f..1.0f,
                    onValueChange = {
                        GlassTuning.cardTintAlpha = it
                        GlassTuning.save(context)
                    },
                )

                GlassTuningSliderRow(
                    label = "Menu & Sheet Opacity",
                    valueText = "${(GlassTuning.popupTintAlpha * 100).roundToInt()}%",
                    value = GlassTuning.popupTintAlpha,
                    valueRange = 0.25f..1.0f,
                    onValueChange = {
                        GlassTuning.popupTintAlpha = it
                        GlassTuning.save(context)
                    },
                )

                GlassTuningSliderRow(
                    label = "Window Blur Behind Popups",
                    valueText = "${GlassTuning.windowBlurDp.roundToInt()} dp",
                    value = GlassTuning.windowBlurDp,
                    valueRange = 0f..80f,
                    onValueChange = {
                        GlassTuning.windowBlurDp = it
                        GlassTuning.save(context)
                    },
                )

                GlassTuningSliderRow(
                    label = "Top Specular Sheen",
                    valueText = "${(GlassTuning.topSheenAlpha * 100).roundToInt()}%",
                    value = GlassTuning.topSheenAlpha,
                    valueRange = 0f..0.30f,
                    onValueChange = {
                        GlassTuning.topSheenAlpha = it
                        GlassTuning.save(context)
                    },
                )

                GlassTuningSliderRow(
                    label = "Border Rim Opacity",
                    valueText = "${(GlassTuning.borderAlpha * 100).roundToInt()}%",
                    value = GlassTuning.borderAlpha,
                    valueRange = 0f..0.50f,
                    onValueChange = {
                        GlassTuning.borderAlpha = it
                        GlassTuning.save(context)
                    },
                )
            }
        }
    }
}

@Composable
private fun GlassTuningSliderRow(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = label, color = AnsuColors.TextPrimary, fontSize = 12.sp)
            Text(
                text = valueText,
                color = AnsuColors.TextPrimary.copy(alpha = 0.85f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
        )
    }
}