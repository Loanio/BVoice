package dev.breenottshook.ui.host

import kotlin.math.roundToInt

internal object HostToolbarDividerVisuals {
    private const val LIGHT_COLOR = 0x1f000000
    private const val DARK_COLOR = 0x33ffffff
    private const val SIDE_INSET_DP = 24f
    private const val EXPANSION_DISTANCE_DP = 50f

    data class State(val alpha: Float, val widthPx: Int)

    fun color(isNight: Boolean): Int = if (isNight) DARK_COLOR else LIGHT_COLOR

    fun state(scrollOffsetPx: Int, fullWidthPx: Int, density: Float): State {
        val safeDensity = density.coerceAtLeast(0f)
        val expansionDistance = (EXPANSION_DISTANCE_DP * safeDensity).coerceAtLeast(1f)
        val progress = (scrollOffsetPx.coerceAtLeast(0) / expansionDistance).coerceIn(0f, 1f)
        val totalInset = SIDE_INSET_DP * safeDensity * 2f
        val width = (fullWidthPx.coerceAtLeast(0) - totalInset * (1f - progress))
            .roundToInt()
            .coerceIn(0, fullWidthPx.coerceAtLeast(0))
        return State(alpha = progress, widthPx = width)
    }
}
