package com.hp.ilo2.remcons

import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.WindowEvent
import java.awt.event.WindowListener

@Suppress("unused")
class OkCancelDialog : Dialog, ActionListener, WindowListener {

    private lateinit var cancel: Button
    private lateinit var ok: Button
    private lateinit var txt: TextArea
    private var rc = false

    constructor(owner: Frame?, message: String) : super(owner, "Notice!", true) {
        uiInit(message)
    }

    constructor(message: String, isModal: Boolean) : super(Frame(), "Notice!", isModal) {
        uiInit(message)
    }

    private fun uiInit(message: String) {
        txt = TextArea(message, 5, 40, 1)
        txt.isEditable = false

        ok = Button("    Ok    ")
        ok.addActionListener(this)

        cancel = Button("Cancel")
        cancel.addActionListener(this)

        background = Color.lightGray

        setSize(360, 160)

        val gridBagLayout = GridBagLayout()
        layout = gridBagLayout

        val constraints = GridBagConstraints().apply {
            fill = 2
            anchor = 17
            weightx = 100.0
            weighty = 100.0
            gridx = 0
            gridy = 0
            gridwidth = 1
            gridheight = 1
        }

        add(txt, constraints)

        val buttonsPanel = Panel().apply {
            layout = FlowLayout(FlowLayout.RIGHT)
            add(ok)
            add(cancel)
        }

        constraints.fill = 0
        constraints.anchor = 13
        constraints.gridx = 0
        constraints.gridy = 1
        constraints.gridwidth = 1

        add(buttonsPanel, constraints)
        addWindowListener(this)

        isVisible = true
        isResizable = false
    }

    override fun actionPerformed(event: ActionEvent) {
        if (event.source == ok) {
            dispose()
            rc = true
        } else if (event.source == cancel) {
            dispose()
            rc = false
        }
    }

    fun result(): Boolean {
        return rc
    }

    fun append(message: String?) {
        txt.append(message)
        txt.repaint()
    }

    override fun windowClosing(event: WindowEvent) {
        dispose()
        rc = false
    }

    override fun windowOpened(event: WindowEvent) {}

    override fun windowDeiconified(event: WindowEvent) {}

    override fun windowIconified(event: WindowEvent) {}

    override fun windowActivated(event: WindowEvent) {}

    override fun windowClosed(event: WindowEvent) {}

    override fun windowDeactivated(event: WindowEvent) {}
}
