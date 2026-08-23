package dev.breenottshook.hook

object PreferenceTraversal {
    data class Located<T>(val node: T, val parent: T?)

    fun <T> find(
        root: T,
        children: (T) -> Iterable<T>,
        matches: (T) -> Boolean
    ): T? {
        if (matches(root)) return root
        return children(root).asSequence()
            .mapNotNull { find(it, children, matches) }
            .firstOrNull()
    }

    fun <T> findWithParent(
        root: T,
        children: (T) -> Iterable<T>,
        matches: (T) -> Boolean
    ): Located<T>? {
        if (matches(root)) return Located(root, null)
        children(root).forEach { child ->
            if (matches(child)) return Located(child, root)
            findWithParent(child, children, matches)?.let { return it }
        }
        return null
    }
}
