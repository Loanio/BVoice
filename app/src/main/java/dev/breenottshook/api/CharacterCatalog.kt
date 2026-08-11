package dev.breenottshook.api

data class CharacterCatalog(
    val characters: Map<String, List<String>>
)

sealed interface CatalogState {
    data class Fresh(val catalog: CharacterCatalog) : CatalogState
    data class Stale(val catalog: CharacterCatalog, val reason: String) : CatalogState
    data class Failed(val reason: String) : CatalogState
}
