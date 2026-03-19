package com.github.axondragonscale.variantx.state

import com.github.axondragonscale.variantx.model.FavoriteVariant
import com.github.axondragonscale.variantx.model.VariantSelection
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/**
 * Persists the last used variant selection and favorites per project.
 * Stored in `.idea/variantx.xml`.
 */
@State(
    name = "VariantXState",
    storages = [Storage("variantx.xml")],
)
@Service(Service.Level.PROJECT)
class VariantXStateService : PersistentStateComponent<VariantXStateService.VariantXState> {

    companion object {
        const val MAX_FAVORITES = 10
    }

    data class VariantXState(
        var lastModule: String = "",
        var lastFlavors: MutableMap<String, String> = mutableMapOf(),
        var lastBuildType: String = "debug",
        var favorites: MutableList<FavoriteVariant> = mutableListOf(),
    )

    private var myState = VariantXState()

    override fun getState(): VariantXState = myState

    override fun loadState(state: VariantXState) {
        myState = state
    }

    // ── Last Selection ──

    fun saveSelection(selection: VariantSelection) {
        myState.lastModule = selection.selectedModuleGradlePath
        myState.lastFlavors = selection.flavorSelections.toMutableMap()
        myState.lastBuildType = selection.selectedBuildType
    }

    fun loadSelection(): VariantSelection = VariantSelection(
        selectedModuleGradlePath = myState.lastModule,
        flavorSelections = myState.lastFlavors.toMutableMap(),
        selectedBuildType = myState.lastBuildType,
    )

    // ── Favorites Management ──

    fun getFavorites(): List<FavoriteVariant> =
        myState.favorites.sortedByDescending { it.pinnedAt }

    fun addFavorite(favorite: FavoriteVariant): Boolean {
        if (myState.favorites.size >= MAX_FAVORITES) return false
        if (myState.favorites.any { it.matches(favorite.toVariantSelection()) }) return false
        myState.favorites.add(favorite)
        return true
    }

    fun removeFavorite(favorite: FavoriteVariant) {
        myState.favorites.removeAll { it.matches(favorite.toVariantSelection()) }
    }

    fun isFavorite(selection: VariantSelection): Boolean =
        myState.favorites.any { it.matches(selection) }

    fun canAddFavorite(): Boolean =
        myState.favorites.size < MAX_FAVORITES
}

