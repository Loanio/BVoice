package dev.breenottshook.hook

internal class DeferredInstaller<T>(
    private val install: (T) -> Unit
) {
    private var installed = false

    fun start(
        current: T?,
        defer: (((T) -> Unit) -> Unit)
    ) {
        if (current != null) {
            installOnce(current)
        } else {
            defer(::installOnce)
        }
    }

    private fun installOnce(value: T) {
        if (installed) return
        installed = true
        install(value)
    }
}
