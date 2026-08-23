package dev.breenottshook.ui.host

internal object HostPageVisuals {
    fun enterTranslation(width: Int, fraction: Float): Float =
        width.coerceAtLeast(0) * (1f - fraction.coerceIn(0f, 1f))

    fun exitTranslation(width: Int, fraction: Float): Float =
        width.coerceAtLeast(0) * fraction.coerceIn(0f, 1f)

    fun backgroundColor(resolvedHostColor: Int?, isNight: Boolean): Int =
        resolvedHostColor?.takeIf { it ushr 24 != 0 } ?:
            if (isNight) 0xff121212.toInt() else 0xfff7f7f7.toInt()
}
