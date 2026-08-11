package dev.breenottshook.api

class CharacterCache(
    private val ttlMs: Long = 24 * 60 * 60 * 1_000L,
    private val clock: () -> Long = System::currentTimeMillis,
    private val loader: suspend (String) -> CharacterCatalog
) {
    private data class Entry(val catalog: CharacterCatalog, val loadedAt: Long)
    private val entries = mutableMapOf<String, Entry>()

    suspend fun getOrFetch(baseUrl: String, forceRefresh: Boolean): CatalogState {
        val cached = entries[baseUrl]
        if (!forceRefresh && cached != null && clock() - cached.loadedAt <= ttlMs) {
            return CatalogState.Fresh(cached.catalog)
        }
        return runCatching { loader(baseUrl) }
            .fold(
                onSuccess = { catalog ->
                    entries[baseUrl] = Entry(catalog, clock())
                    CatalogState.Fresh(catalog)
                },
                onFailure = { error ->
                    cached?.let {
                        CatalogState.Stale(it.catalog, error.message ?: error::class.java.simpleName)
                    } ?: CatalogState.Failed(error.message ?: error::class.java.simpleName)
                }
            )
    }
}
