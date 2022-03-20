package com.hp.ilo2.virtdevs

import java.awt.*

class ChiselBox : Panel {

    private var bg: Color = Color.lightGray
    private var checkName: String = ""
    private var inx = 8
    private var iny = 8
    private var raised = false
    var checkEnabled = true
    var content = Panel()

    constructor() {
        add(content)
        background = bg
    }

    constructor(str: String) {
        add(content)
        this.checkName = str
        background = bg
    }

    override fun setEnabled(z: Boolean) {
        this.checkEnabled = z
        repaint()
    }

    fun cadd(component: Component, gridBagConstraints: GridBagConstraints, i: Int, i2: Int, i3: Int, i4: Int) {
        gridBagConstraints.gridx = i
        gridBagConstraints.gridy = i2
        gridBagConstraints.gridwidth = i3
        gridBagConstraints.gridheight = i4
        content.add(component, gridBagConstraints)
    }

    override fun paint(graphics: Graphics) {
        val size = size
        val brighter = if (raised) bg.brighter() else bg.darker()
        val darker = if (raised) bg.darker() else bg.brighter()
        val i = inx
        val i2 = iny
        val i3 = size.width - inx
        val i4 = size.height - iny

        graphics.color = brighter
        graphics.drawLine(i, i2, i3, i2)
        graphics.drawLine(i, i2, i, i4)
        graphics.drawLine(i + 1, i4 - 1, i3 - 1, i4 - 1)
        graphics.drawLine(i3 - 1, i2 + 1, i3 - 1, i4 - 1)
        graphics.color = darker
        graphics.drawLine(i + 1, i2 + 1, i3 - 2, i2 + 1)
        graphics.drawLine(i + 1, i2 + 1, i + 1, i4 - 2)
        graphics.drawLine(i, i4, i3, i4)
        graphics.drawLine(i3, i2, i3, i4)

        val fontMetrics = graphics.fontMetrics
        val stringWidth = fontMetrics.stringWidth(this.checkName)
        val height = fontMetrics.height - fontMetrics.descent

        graphics.color = bg
        graphics.fillRect(2 * inx, i2, stringWidth + 8, 2)

        if (this.checkEnabled) {
            graphics.color = Color.black
            graphics.drawString(this.checkName, 2 * inx + 4, iny + height / 2 - 1)
        } else {
            graphics.color = bg.brighter()
            graphics.drawString(this.checkName, 1 + 2 * inx + 4, 1 + iny + height / 2 - 1)
            graphics.color = bg.darker()
            graphics.drawString(this.checkName, 2 * inx + 4, iny + height / 2 - 1)
        }

        content.setBounds(2 * inx, iny + height, size.width - 4 * inx, size.height - 2 * (iny / 2 + height))
        content.paint(content.graphics)
    }
}
