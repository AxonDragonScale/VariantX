package com.github.axondragonscale.variantx.ui

import com.github.axondragonscale.variantx.VariantXBundle
import com.github.axondragonscale.variantx.model.AndroidModuleInfo
import com.github.axondragonscale.variantx.model.FavoriteVariant
import com.intellij.icons.AllIcons
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import icons.GradleIcons
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu

/**
 * Displays a list of favorite/pinned variant combinations.
 * Clicking the variant name row loads its values into the dialog.
 * Each row has icon-only toolbar-style Sync, Build, Run, and Unpin buttons.
 * Stale favorites show grayed-out text with disabled action buttons.
 */
class FavoritesPanel(
    private var favorites: List<FavoriteVariant>,
    private val moduleInfoMap: Map<String, AndroidModuleInfo>,
    private val onSelect: (FavoriteVariant) -> Unit,
    private val onSet: (FavoriteVariant) -> Unit,
    private val onBuild: (FavoriteVariant) -> Unit,
    private val onRun: (FavoriteVariant) -> Unit,
    private val onRemove: (FavoriteVariant) -> Unit,
) : JPanel() {

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        border = JBUI.Borders.empty(0, 0, 4, 0)
        buildContent()
    }

    fun refresh(newFavorites: List<FavoriteVariant>) {
        favorites = newFavorites
        buildContent()
    }

    private fun buildContent() {
        removeAll()
        for (fav in favorites) {
            val isValid = fav.isValid(moduleInfoMap[fav.moduleGradlePath])
            add(createFavoriteRow(fav, isValid))
        }
        revalidate()
        repaint()
    }

    private fun createFavoriteRow(fav: FavoriteVariant, isValid: Boolean): JPanel {
        // hgap=100 provides the gap between the name label and the button panel
        val row = JPanel(BorderLayout(JBUI.scale(100), 0))
        row.isOpaque = false
        row.border = JBUI.Borders.empty(2, 4)

        // Left: variant name — clicking loads the favorite's values into the dialog
        val displayName = if (moduleInfoMap.size > 1) {
            val moduleName = moduleInfoMap[fav.moduleGradlePath]?.name ?: fav.moduleGradlePath
            "$moduleName: ${fav.variantName}"
        } else {
            fav.variantName
        }
        val nameLabel = object : JBLabel(displayName) {
            private var hovered = false
            private var pressed = false

            override fun paintComponent(g: Graphics) {
                val g2 = g.create() as Graphics2D
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                    when {
                        pressed -> {
                            g2.color = JBUI.CurrentTheme.ActionButton.pressedBackground()
                            g2.fillRoundRect(0, 0, width, height, JBUI.scale(8), JBUI.scale(8))
                        }
                        hovered -> {
                            g2.color = JBUI.CurrentTheme.ActionButton.hoverBackground()
                            g2.fillRoundRect(0, 0, width, height, JBUI.scale(8), JBUI.scale(8))
                        }
                    }
                } finally {
                    g2.dispose()
                }
                super.paintComponent(g)
            }

            init {
                font = UIUtil.getLabelFont()
                cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                border = JBUI.Borders.empty(1, 4)
                if (!isValid) {
                    foreground = JBColor.GRAY
                    toolTipText = VariantXBundle.message("favorites.stale")
                }
                addMouseListener(object : MouseAdapter() {
                    override fun mouseEntered(e: MouseEvent) { hovered = true; repaint() }
                    override fun mouseExited(e: MouseEvent) { hovered = false; pressed = false; repaint() }
                    override fun mousePressed(e: MouseEvent) { pressed = true; repaint() }
                    override fun mouseReleased(e: MouseEvent) { pressed = false; repaint() }
                    override fun mouseClicked(e: MouseEvent) {
                        if (e.button == MouseEvent.BUTTON1 && !e.isPopupTrigger) onSelect(fav)
                    }
                })
            }
        }
        row.add(nameLabel, BorderLayout.CENTER)

        // Right: icon-only toolbar-sized buttons
        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(4), 0))
        buttonPanel.isOpaque = false

        buttonPanel.add(toolbarButton(GradleIcons.GradleLoadChanges, VariantXBundle.message("dialog.set"), isValid) { onSet(fav) })
        buttonPanel.add(toolbarButton(AllIcons.Actions.Compile, VariantXBundle.message("dialog.build"), isValid) { onBuild(fav) })
        buttonPanel.add(toolbarButton(AllIcons.Actions.Execute, VariantXBundle.message("dialog.run"), isValid) { onRun(fav) })
        buttonPanel.add(toolbarButton(AllIcons.Actions.PinTab, VariantXBundle.message("dialog.unpin")) { onRemove(fav) })

        row.add(buttonPanel, BorderLayout.EAST)

        // Right-click context menu (secondary removal path)
        row.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) = maybeShowPopup(e)
            override fun mouseReleased(e: MouseEvent) = maybeShowPopup(e)

            private fun maybeShowPopup(e: MouseEvent) {
                if (e.isPopupTrigger) {
                    val popup = JPopupMenu()
                    val removeItem = JMenuItem(VariantXBundle.message("favorites.remove"))
                    removeItem.addActionListener { onRemove(fav) }
                    popup.add(removeItem)
                    popup.show(e.component, e.x, e.y)
                }
            }
        })

        return row
    }

    /** Creates an icon-only button with a custom rounded hover/pressed background. */
    private fun toolbarButton(
        icon: Icon,
        tooltip: String,
        enabled: Boolean = true,
        action: () -> Unit,
    ): JButton {
        val size = Dimension(JBUI.scale(28), JBUI.scale(28))
        return object : JButton(icon) {
            private var hovered = false
            private var pressed = false

            override fun paintComponent(g: Graphics) {
                val g2 = g.create() as Graphics2D
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                    when {
                        isEnabled && pressed -> {
                            g2.color = JBUI.CurrentTheme.ActionButton.pressedBackground()
                            g2.fillRoundRect(0, 0, width, height, JBUI.scale(8), JBUI.scale(8))
                        }
                        isEnabled && hovered -> {
                            g2.color = JBUI.CurrentTheme.ActionButton.hoverBackground()
                            g2.fillRoundRect(0, 0, width, height, JBUI.scale(8), JBUI.scale(8))
                        }
                    }
                } finally {
                    g2.dispose()
                }
                super.paintComponent(g)
            }

            init {
                toolTipText = tooltip
                isEnabled = enabled
                isFocusable = false
                isContentAreaFilled = false
                isBorderPainted = false
                isOpaque = false
                margin = JBUI.emptyInsets()
                preferredSize = size
                minimumSize = size
                maximumSize = size
                addMouseListener(object : MouseAdapter() {
                    override fun mouseEntered(e: MouseEvent) { hovered = true; repaint() }
                    override fun mouseExited(e: MouseEvent) { hovered = false; pressed = false; repaint() }
                    override fun mousePressed(e: MouseEvent) { if (isEnabled) { pressed = true; repaint() } }
                    override fun mouseReleased(e: MouseEvent) { pressed = false; repaint() }
                })
                addActionListener { action() }
            }
        }
    }
}
