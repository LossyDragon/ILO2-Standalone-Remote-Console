package com.hp.ilo2.virtdevs

import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.ActionListener

class VErrorDialog : Dialog, ActionListener {

    private lateinit var ok: Button
    private lateinit var txt: TextArea
    private var disp = false

    constructor(frame: Frame?, str: String?) : super(frame, "Error", true) {
        uiInit(str)
    }

    constructor(str: String?, z: Boolean) : super(Frame(), "Error", z) {
        uiInit(str)
    }

    private fun uiInit(str: String?) {
        txt = TextArea(str, 5, 40, 1)
        txt.isEditable = false

        ok = Button("    Ok    ")
        ok.addActionListener(this)

        val gridBagLayout = GridBagLayout()
        val gridBagConstraints = GridBagConstraints()

        layout = gridBagLayout

        background = Color.lightGray

        setSize(300, 150)

        gridBagConstraints.fill = 0
        gridBagConstraints.anchor = 10
        gridBagConstraints.weightx = 100.0
        gridBagConstraints.weighty = 100.0
        gridBagConstraints.gridx = 0
        gridBagConstraints.gridy = 0
        gridBagConstraints.gridwidth = 1
        gridBagConstraints.gridheight = 1

        add(txt, gridBagConstraints)

        gridBagConstraints.gridy = 1

        add(ok, gridBagConstraints)

        isVisible = true
    }

    override fun actionPerformed(actionEvent: ActionEvent) {
        if (actionEvent.source == ok) {
            dispose()
            disp = true
        }
    }

    fun disposed(): Boolean {
        return disp
    }

    fun append(str: String?) {
        txt.append(str)
        txt.repaint()
    }
}
