package com.github.axondragonscale.variantx.ui

import com.github.axondragonscale.variantx.VariantXBundle
import com.github.axondragonscale.variantx.model.AndroidModuleInfo
import com.github.axondragonscale.variantx.model.FavoriteVariant
import com.github.axondragonscale.variantx.model.VariantSelection
import com.github.axondragonscale.variantx.service.AppRunnerService
import com.github.axondragonscale.variantx.service.VariantApplierService
import com.github.axondragonscale.variantx.state.VariantXStateService
import com.intellij.icons.AllIcons
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.JBColor
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import icons.GradleIcons
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Main VariantX dialog. Shows segmented controls for flavor dimensions
 * and build types, a favorites panel, and Set / Build / Pin actions.
 * Pin is placed on the left via [createLeftSideActions]; no Cancel button is shown.
 */
class VariantXDialog(
    private val project: Project,
    private val appModules: List<AndroidModuleInfo>,
) : DialogWrapper(project) {

    init {
        require(appModules.isNotEmpty()) { "VariantXDialog requires at least one app module" }
    }

    // Services
    private val stateService = project.service<VariantXStateService>()
    private val applierService = project.service<VariantApplierService>()
    private val runnerService = project.service<AppRunnerService>()

    // UI state
    private var selectedModule: AndroidModuleInfo = appModules.first()
    private val flavorSelections = mutableMapOf<String, String>()
    private var selectedBuildType: String = "debug"

    /** Cached current selection — updated on every UI change via [updatePreview]. */
    private var currentSelection: VariantSelection = VariantSelection()

    // UI components
    private val flavorSegmentedControls = mutableMapOf<String, SegmentedControl>()
    private var buildTypeSegmentedControl: SegmentedControl? = null
    private var variantPreviewLabel = JBLabel("")
    private var moduleSegmentedControl: SegmentedControl? = null
    private var favoritesPanel: FavoritesPanel? = null
    private var favoritesSeparator: TitledSeparator? = null
    private var favoritesSpacer: JPanel? = null
    private var contentPanel: JPanel? = null

    // Actions
    private lateinit var pinAction: Action
    private lateinit var setAction: Action
    private lateinit var buildAction: Action
    private lateinit var runAction: Action

    init {
        title = VariantXBundle.message("dialog.title")
        restoreFromState()
        init()
        updatePreview()
    }

    // ── Panel ──

    override fun createCenterPanel(): JComponent {
        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(8, 12)
        }

        // ── Favorites Section ──
        val favorites = stateService.getFavorites()
        if (favorites.isNotEmpty()) {
            favoritesSeparator = TitledSeparator(VariantXBundle.message("favorites.title"))
            panel.add(favoritesSeparator)

            favoritesPanel = FavoritesPanel(
                favorites = favorites,
                moduleInfoMap = appModules.associateBy { it.gradlePath },
                onSelect = { fav -> loadFromFavorite(fav) },
                onSet = { fav -> doSetFromFavorite(fav) },
                onBuild = { fav -> doBuildFromFavorite(fav) },
                onRun = { fav -> doRunFromFavorite(fav) },
                onRemove = { fav -> removeFavorite(fav) },
            )
            panel.add(favoritesPanel)
            favoritesSpacer = createVerticalSpacer()
            panel.add(favoritesSpacer)
        }

        // ── Module Selector (only if multiple app modules) ──
        if (appModules.size > 1) {
            panel.add(TitledSeparator(VariantXBundle.message("dialog.module")))
            val moduleRow = createRow("")
            moduleSegmentedControl = SegmentedControl(
                items = appModules.map { it.name },
                selectedItem = selectedModule.name,
            ) { newName ->
                val newModule = appModules.find { it.name == newName } ?: return@SegmentedControl
                selectedModule = newModule
                rebuildSegmentedControls()
                updatePreview()
            }
            moduleRow.add(moduleSegmentedControl, createFillConstraints())
            panel.add(moduleRow)
            panel.add(createVerticalSpacer())
        }

        // ── Flavors Section ──
        if (selectedModule.flavorDimensions.isNotEmpty()) {
            panel.add(TitledSeparator(VariantXBundle.message("dialog.flavors")))

            for (dimension in selectedModule.flavorDimensions) {
                val flavors = selectedModule.flavorsPerDimension[dimension] ?: continue
                val preSelected = flavorSelections[dimension]
                    ?: flavors.firstOrNull() ?: continue

                val row = createRow("$dimension:")
                val sc = SegmentedControl(flavors, preSelected) { chosen ->
                    flavorSelections[dimension] = chosen
                    updatePreview()
                }
                flavorSegmentedControls[dimension] = sc
                row.add(sc, createFillConstraints())
                panel.add(row)
            }
            panel.add(createVerticalSpacer())
        }

        // ── Build Type Section ──
        panel.add(TitledSeparator(VariantXBundle.message("dialog.buildType")))

        val btRow = createRow("")
        buildTypeSegmentedControl = SegmentedControl(
            selectedModule.buildTypes,
            selectedBuildType,
        ) { chosen ->
            selectedBuildType = chosen
            updatePreview()
        }
        btRow.add(buildTypeSegmentedControl, createFillConstraints())
        panel.add(btRow)

        // ── Variant Preview ──
        panel.add(createVerticalSpacer())
        val previewRow = createRow(VariantXBundle.message("dialog.variant"))
        variantPreviewLabel = JBLabel("").apply {
            font = UIUtil.getLabelFont().deriveFont(UIUtil.getLabelFont().size + 1f)
        }
        previewRow.add(variantPreviewLabel, createFillConstraints())
        panel.add(previewRow)

        contentPanel = panel
        return object : JPanel(java.awt.BorderLayout()) {
            override fun getPreferredSize(): java.awt.Dimension {
                val ps = super.getPreferredSize()
                return java.awt.Dimension(maxOf(ps.width, JBUI.scale(560)), ps.height)
            }
        }.apply {
            isOpaque = false
            add(panel, java.awt.BorderLayout.NORTH)
        }
    }

    // ── Actions ──

    /** Pin button sits on the far left, separated from the right-side action buttons. */
    override fun createLeftSideActions(): Array<Action> {
        pinAction = object : AbstractAction(getPinButtonText()) {
            init { putValue(Action.SMALL_ICON, AllIcons.Actions.PinTab) }
            override fun actionPerformed(e: ActionEvent) = doTogglePin()
        }
        return arrayOf(pinAction)
    }

    /** Set / Build / Run — no Cancel button. Dialog closes via Escape. */
    override fun createActions(): Array<Action> {
        setAction = object : AbstractAction(VariantXBundle.message("dialog.set")) {
            init { putValue(Action.SMALL_ICON, GradleIcons.GradleLoadChanges) }
            override fun actionPerformed(e: ActionEvent) = doSet()
        }
        buildAction = object : AbstractAction(VariantXBundle.message("dialog.build")) {
            init { putValue(Action.SMALL_ICON, AllIcons.Actions.Compile) }
            override fun actionPerformed(e: ActionEvent) = doBuild()
        }
        runAction = object : AbstractAction(VariantXBundle.message("dialog.run")) {
            init { putValue(Action.SMALL_ICON, AllIcons.Actions.Execute) }
            override fun actionPerformed(e: ActionEvent) = doRun()
        }
        return arrayOf(setAction, buildAction, runAction)
    }

    // ── State Restoration ──

    private fun restoreFromState() {
        val saved = stateService.loadSelection()

        // Restore module by gradle path
        val savedModule = appModules.find { it.gradlePath == saved.selectedModuleGradlePath }
        if (savedModule != null) selectedModule = savedModule

        // Restore flavor selections (validate against current module)
        for (dim in selectedModule.flavorDimensions) {
            val flavors = selectedModule.flavorsPerDimension[dim] ?: continue
            val savedFlavor = saved.flavorSelections[dim]
            flavorSelections[dim] = if (savedFlavor != null && savedFlavor in flavors) savedFlavor
                                    else flavors.first()
        }

        // Restore build type
        selectedBuildType = if (saved.selectedBuildType in selectedModule.buildTypes) {
            saved.selectedBuildType
        } else {
            selectedModule.buildTypes.firstOrNull() ?: "debug"
        }
    }

    // ── Preview & Validation ──

    private fun updatePreview() {
        currentSelection = buildSelection()
        val variantName = currentSelection.composeVariantName(selectedModule.flavorDimensions)
        val isValid = variantName in selectedModule.availableVariants ||
            selectedModule.availableVariants.isEmpty()

        if (isValid) {
            variantPreviewLabel.text = variantName
            variantPreviewLabel.foreground = UIUtil.getLabelForeground()
        } else {
            variantPreviewLabel.text = VariantXBundle.message("dialog.invalidVariant")
            variantPreviewLabel.foreground = JBColor.RED
        }

        if (::setAction.isInitialized) {
            setAction.isEnabled = isValid
            buildAction.isEnabled = isValid
            runAction.isEnabled = isValid
        }

        if (::pinAction.isInitialized) {
            pinAction.putValue(Action.NAME, getPinButtonText())
            val canPin = stateService.canAddFavorite() || stateService.isFavorite(currentSelection)
            pinAction.isEnabled = canPin
        }
    }

    private fun getPinButtonText(): String {
        return if (stateService.isFavorite(currentSelection)) VariantXBundle.message("dialog.unpin")
               else VariantXBundle.message("dialog.pin")
    }

    // ── Button Handlers ──

    private fun doSet() {
        stateService.saveSelection(currentSelection)
        applierService.applyVariant(currentSelection, selectedModule)
        close(OK_EXIT_CODE)
    }

    private fun doBuild() {
        stateService.saveSelection(currentSelection)
        runnerService.applyAndAssemble(currentSelection, selectedModule)
        close(OK_EXIT_CODE)
    }

    private fun doRun() {
        stateService.saveSelection(currentSelection)
        runnerService.applyAndRun(currentSelection, selectedModule)
        close(OK_EXIT_CODE)
    }

    private fun doTogglePin() {
        if (stateService.isFavorite(currentSelection)) {
            val fav = stateService.getFavorites().find { it.matches(currentSelection) }
            if (fav != null) stateService.removeFavorite(fav)
        } else {
            val variantName = currentSelection.composeVariantName(selectedModule.flavorDimensions)
            stateService.addFavorite(
                FavoriteVariant(
                    moduleGradlePath = currentSelection.selectedModuleGradlePath,
                    flavorSelections = currentSelection.flavorSelections.toMutableMap(),
                    buildType = currentSelection.selectedBuildType,
                    variantName = variantName,
                    pinnedAt = System.currentTimeMillis(),
                )
            )
        }
        favoritesPanel?.refresh(stateService.getFavorites())
        updatePreview()
    }

    /**
     * Resolves the module info for a favorite and executes [block] with it.
     * Handles saving the selection and closing the dialog.
     */
    private inline fun executeFavoriteAction(fav: FavoriteVariant, block: (AndroidModuleInfo, VariantSelection) -> Unit) {
        val moduleInfo = appModules.find { it.gradlePath == fav.moduleGradlePath } ?: return
        val selection = fav.toVariantSelection()
        stateService.saveSelection(selection)
        block(moduleInfo, selection)
        close(OK_EXIT_CODE)
    }

    private fun doSetFromFavorite(fav: FavoriteVariant) = executeFavoriteAction(fav) { moduleInfo, selection ->
        applierService.applyVariant(selection, moduleInfo)
    }

    private fun doBuildFromFavorite(fav: FavoriteVariant) = executeFavoriteAction(fav) { moduleInfo, selection ->
        runnerService.applyAndAssemble(selection, moduleInfo)
    }

    private fun doRunFromFavorite(fav: FavoriteVariant) = executeFavoriteAction(fav) { moduleInfo, selection ->
        runnerService.applyAndRun(selection, moduleInfo)
    }

    /** Loads a favorite's values into all dialog controls without closing the dialog. */
    private fun loadFromFavorite(fav: FavoriteVariant) {
        val moduleInfo = appModules.find { it.gradlePath == fav.moduleGradlePath } ?: return

        // Switch module
        selectedModule = moduleInfo
        moduleSegmentedControl?.setSelected(moduleInfo.name)

        // Seed the selection maps so rebuildSegmentedControls picks them up
        flavorSelections.clear()
        flavorSelections.putAll(fav.flavorSelections)
        selectedBuildType = fav.buildType

        // Update segmented controls to reflect the new module + selections
        rebuildSegmentedControls()
        updatePreview()
    }

    private fun removeFavorite(fav: FavoriteVariant) {
        stateService.removeFavorite(fav)
        val updated = stateService.getFavorites()
        if (updated.isEmpty()) {
            // Remove the entire favorites section including its spacer
            favoritesSeparator?.let { contentPanel?.remove(it) }
            favoritesPanel?.let { contentPanel?.remove(it) }
            favoritesSpacer?.let { contentPanel?.remove(it) }
            contentPanel?.revalidate()
            contentPanel?.repaint()
        } else {
            favoritesPanel?.refresh(updated)
        }
        updatePreview()
    }

    // ── Segmented Control Rebuilding ──

    private fun rebuildSegmentedControls() {
        val currentDimensions = selectedModule.flavorDimensions.toSet()

        // Remove stale dimension entries that don't exist in the new module
        flavorSelections.keys.retainAll(currentDimensions)
        flavorSegmentedControls.keys.retainAll(currentDimensions)

        for ((dim, sc) in flavorSegmentedControls) {
            val newFlavors = selectedModule.flavorsPerDimension[dim]
            if (newFlavors != null) {
                sc.updateItems(newFlavors, flavorSelections[dim])
                flavorSelections[dim] = sc.selectedValue
            }
        }
        for (dim in selectedModule.flavorDimensions) {
            if (dim !in flavorSegmentedControls) {
                val flavors = selectedModule.flavorsPerDimension[dim] ?: continue
                flavorSelections[dim] = flavors.first()
            }
        }
        buildTypeSegmentedControl?.updateItems(selectedModule.buildTypes, selectedBuildType)
        selectedBuildType = buildTypeSegmentedControl?.selectedValue ?: selectedBuildType
    }

    // ── Helpers ──

    private fun buildSelection(): VariantSelection = VariantSelection(
        selectedModuleGradlePath = selectedModule.gradlePath,
        flavorSelections = flavorSelections.toMutableMap(),
        selectedBuildType = selectedBuildType,
    )

    private fun createRow(label: String): JPanel {
        val panel = JPanel(GridBagLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.empty(2, 0)
        }
        if (label.isNotEmpty()) {
            val lbl = JBLabel(label).apply { border = JBUI.Borders.emptyRight(8) }
            panel.add(lbl, GridBagConstraints().apply {
                gridx = 0; gridy = 0
                anchor = GridBagConstraints.WEST
                fill = GridBagConstraints.NONE
            })
        }
        return panel
    }

    private fun createFillConstraints() = GridBagConstraints().apply {
        gridx = 1; gridy = 0
        weightx = 1.0
        anchor = GridBagConstraints.WEST
        fill = GridBagConstraints.HORIZONTAL
    }

    private fun createVerticalSpacer() = JPanel().apply {
        isOpaque = false
        preferredSize = java.awt.Dimension(0, JBUI.scale(8))
        maximumSize = java.awt.Dimension(Int.MAX_VALUE, JBUI.scale(8))
    }
}
