package com.github.axondragonscale.variantx.ui

import com.intellij.util.ui.JBUI
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent

/**
 * Tracks hover/press state and paints a rounded-rect background using the
 * platform's [JBUI.CurrentTheme.ActionButton] colors. Attach to any [JComponent]
 * to get consistent IntelliJ-style interactive highlighting.
 */
class HoverPaintHelper(private val component: JComponent) {

    var hovered: Boolean = false
        private set
    var pressed: Boolean = false
        private set

    /** Installs the mouse listeners that track hover/press state. */
    fun install() {
        component.addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) { hovered = true; component.repaint() }
            override fun mouseExited(e: MouseEvent) { hovered = false; pressed = false; component.repaint() }
            override fun mousePressed(e: MouseEvent) {
                if (component.isEnabled) { pressed = true; component.repaint() }
            }
            override fun mouseReleased(e: MouseEvent) { pressed = false; component.repaint() }
        })
    }

    /**
     * Paints the hover/pressed rounded-rect background.
     * Call this at the beginning of [JComponent.paintComponent].
     */
    fun paintBackground(g: Graphics2D, width: Int, height: Int, isEnabled: Boolean = true) {
        if (!isEnabled) return
        val color = when {
            pressed -> JBUI.CurrentTheme.ActionButton.pressedBackground()
            hovered -> JBUI.CurrentTheme.ActionButton.hoverBackground()
            else -> return
        }
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = color
        val arc = JBUI.scale(8)
        g.fillRoundRect(0, 0, width, height, arc, arc)
    }
}

