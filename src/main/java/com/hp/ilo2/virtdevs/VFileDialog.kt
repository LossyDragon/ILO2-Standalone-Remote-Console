package com.hp.ilo2.virtdevs

import java.awt.FileDialog
import java.awt.Frame
import java.awt.event.WindowEvent
import java.awt.event.WindowListener

class VFileDialog(str: String?) : Frame(str), WindowListener {

    private var fd: FileDialog

    init {
        addWindowListener(this)
        fd = FileDialog(this, str)
        fd.isVisible = true
    }

    val string: String?
        get() {
            var str: String? = null
            if (!(fd.directory == null || fd.file == null)) {
                str = StringBuffer().append(fd.directory).append(fd.file).toString()
            }
            return str
        }

    override fun windowClosing(windowEvent: WindowEvent) {
        isVisible = false
    }

    override fun windowActivated(windowEvent: WindowEvent) {}

    override fun windowClosed(windowEvent: WindowEvent) {}

    override fun windowDeactivated(windowEvent: WindowEvent) {}

    override fun windowDeiconified(windowEvent: WindowEvent) {}

    override fun windowIconified(windowEvent: WindowEvent) {}

    override fun windowOpened(windowEvent: WindowEvent) {}
}
