package com.github.axondragonscale.variantx.ui

import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.ui.dsl.builder.bind
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.JPanel

/**
 * A segmented button control built on IntelliJ UI DSL v2's native [segmentedButton]
 * component for automatic IntelliJ theme consistency (light/dark), proper focus
 * rendering, and built-in ←/→ keyboard navigation — no custom painting required.
 *
 * @param items         The list of option labels.
 * @param selectedItem  The initially selected item (defaults to the first item).
 * @param onSelectionChanged Callback invoked whenever the selected segment changes.
 */
class SegmentedControl(
    items: List<String>,
    selectedItem: String? = null,
    private val onSelectionChanged: (String) -> Unit = {},
) : JPanel(BorderLayout()) {

    private var currentItems: List<String> = items
    private var _selectedValue: String = selectedItem ?: items.firstOrNull() ?: ""

    /** The currently selected segment label. */
    val selectedValue: String get() = _selectedValue

    init {
        isOpaque = false
        border = JBUI.Borders.empty()
        rebuild(items, _selectedValue)
    }

    /** Programmatically select a segment by label without triggering [onSelectionChanged]. */
    fun setSelected(item: String) {
        if (item in currentItems && item != _selectedValue) {
            _selectedValue = item
            rebuild(currentItems, item)
        }
    }

    /** Replace the items and optionally force a new selection. */
    fun updateItems(newItems: List<String>, selected: String? = null) {
        val newSelected = (if (selected != null && selected in newItems) selected
                           else newItems.firstOrNull()) ?: ""
        currentItems = newItems
        _selectedValue = newSelected
        rebuild(newItems, newSelected)
    }

    // ── Internal ──

    private fun rebuild(items: List<String>, selected: String) {
        removeAll()
        if (items.isEmpty()) { revalidate(); repaint(); return }

        // Fresh graph + property per rebuild so old bindings are cleanly released.
        val graph = PropertyGraph()
        val prop = graph.property(selected)
        prop.afterChange { newVal ->
            _selectedValue = newVal
            onSelectionChanged(newVal)
        }

        val content = panel {
            row {
                @Suppress("UnstableApiUsage")
                segmentedButton(items) { text = it }.bind(prop)
            }
        }.apply {
            isOpaque = false
            border = JBUI.Borders.empty()
        }

        add(content, BorderLayout.CENTER)
        revalidate()
        repaint()
    }
}
