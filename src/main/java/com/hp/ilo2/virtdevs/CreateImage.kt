package com.hp.ilo2.virtdevs

import com.hp.ilo2.remcons.Telnet
import java.awt.*
import java.awt.event.*
import java.io.IOException

class CreateImage(
    frame: Frame?
) : Dialog(frame, "Create Media Image"), ActionListener, WindowListener, TextListener, ItemListener, Runnable {

    private val statLabel: Label
    private var imgFile: TextField
    private var browse: Button
    private var cancel: Button
    private var canceled = false
    private var create: Button
    private var defaultRemovable = false
    private var dev: Array<String?>?
    private var devt: IntArray
    private var dimg: Button
    private var diskimage = true
    private var fdDrive = Choice()
    private var frame: Frame?
    private var iscdrom = false
    private var progress: VProgressBar
    private var retrycount = 10

    init {
        var z = true
        var z2 = false
        this.frame = frame

        val panel = Panel(FlowLayout(2, 10, 5))

        setSize(400, SCSI.SCSI_READ_CD_MSF)

        isResizable = false
        isModal = false

        addWindowListener(this)

        val gridBagConstraints = GridBagConstraints()

        layout = GridBagLayout()
        background = Color.lightGray

        val chiselBox = ChiselBox("Create Disk Image")
        chiselBox.content.layout = GridBagLayout()

        gridBagConstraints.anchor = 11
        gridBagConstraints.fill = 1
        gridBagConstraints.weightx = 100.0
        gridBagConstraints.weighty = 100.0

        add(chiselBox, gridBagConstraints, 0, 0, 1, 1)

        gridBagConstraints.fill = 0
        gridBagConstraints.weightx = 100.0
        gridBagConstraints.weighty = 100.0
        gridBagConstraints.anchor = 13

        chiselBox.cadd(Label("Drive"), gridBagConstraints, 0, 0, 1, 1)
        chiselBox.cadd(Label("Image File"), gridBagConstraints, 0, 1, 1, 1)

        gridBagConstraints.fill = 2
        gridBagConstraints.anchor = 17

        val mediaAccess = MediaAccess()
        dev = mediaAccess.devices()
        devt = IntArray(dev!!.size)

        for (i in dev!!.indices) {
            devt[i] = mediaAccess.devtype(dev!![i])

            if (devt[i] == 2) {
                fdDrive.add(dev!![i])
                z = false
                defaultRemovable = true
            }

            if (devt[i] == 5 && VirtDevs.cdimg_support) {
                fdDrive.add(
                    dev!![i]
                )
                if (i == 0) {
                    iscdrom = true
                } else if (!defaultRemovable) {
                    iscdrom = true
                    z2 = true
                }

                z = false
            }
        }

        if (z) {
            fdDrive.add("None")
        }

        fdDrive.addItemListener(this)

        chiselBox.cadd(fdDrive, gridBagConstraints, 1, 0, 1, 1)

        imgFile = TextField()
        imgFile.addTextListener(this)

        chiselBox.cadd(imgFile, gridBagConstraints, 1, 1, 1, 1)

        progress = VProgressBar(350, 25, Color.lightGray, Color.blue, Color.white)

        gridBagConstraints.anchor = 10

        chiselBox.cadd(progress, gridBagConstraints, 0, 2, 3, 1)

        gridBagConstraints.fill = 2

        dimg = Button("Disk >> Image")

        chiselBox.cadd(dimg, gridBagConstraints, 2, 0, 1, 1)

        dimg.addActionListener(this)

        statLabel = Label("                                                                                 ") // ok...
        statLabel.font = Font("Arial", 1, 12)

        panel.add(statLabel)

        browse = Button("Browse")

        chiselBox.cadd(browse, gridBagConstraints, 2, 1, 1, 1)

        browse.addActionListener(this)

        create = Button("Create")
        create.isEnabled = false

        panel.add(create)

        create.addActionListener(this)

        cancel = Button("Cancel")

        panel.add(cancel)

        cancel.addActionListener(this)

        gridBagConstraints.fill = 2
        gridBagConstraints.weighty = 0.0

        add(panel, gridBagConstraints, 0, 1, 1, 1)

        isVisible = true

        if (z2) {
            dimg.label = "Disk >> Image"
            diskimage = true
            dimg.isEnabled = false
        } else {
            dimg.isEnabled = true
        }

        dimg.repaint()
    }

    private fun add(component: Component, gridBagConstraints: GridBagConstraints, i: Int, i2: Int, i3: Int, i4: Int) {
        gridBagConstraints.gridx = i
        gridBagConstraints.gridy = i2
        gridBagConstraints.gridwidth = i3
        gridBagConstraints.gridheight = i4
        add(component, gridBagConstraints)
    }

    override fun actionPerformed(actionEvent: ActionEvent) {
        val source = actionEvent.source
        if (source == browse) {
            statLabel.text = " "
            progress.updateBar(0.0f)
            val string = VFileDialog("Create Disk Image").string

            if (string != null) {
                imgFile.text = string

                if (fdDrive.selectedItem != "None") {
                    create.isEnabled = true
                }
            }
        }

        if (source == create) {
            create.isEnabled = false
            browse.isEnabled = false
            fdDrive.isEnabled = false
            imgFile.isEnabled = false

            dimg.isEnabled = false
            if (diskimage) {
                statLabel.text = "Creating image file, please wait..."
            } else {
                statLabel.text = "Creating disk, please wait..."
            }

            Thread(this).start()
        }

        if (source == dimg) {
            statLabel.text = " "
            progress.updateBar(0.0f)
            diskimage = !diskimage

            if (diskimage) {
                dimg.label = "Disk >> Image"
            } else {
                dimg.label = "Image >> Disk"
            }

            dimg.repaint()
        }

        if (source == cancel) {
            statLabel.text = " "
            progress.updateBar(0.0f)
            canceled = true

            dispose()
        }
    }

    override fun textValueChanged(textEvent: TextEvent) {
        if (textEvent.source == imgFile) {
            statLabel.text = " "
            progress.updateBar(0.0f)
            create.isEnabled = !(imgFile.text == "" || fdDrive.selectedItem == "None")
        }
    }

    override fun itemStateChanged(itemEvent: ItemEvent) {
        if (itemEvent.source == fdDrive) {
            statLabel.text = " "
            progress.updateBar(0.0f)

            val selectedItem = fdDrive.selectedItem
            var i = 0

            while (i < dev!!.size && selectedItem != dev!![i]) {
                i++
            }

            if (i < dev!!.size) {
                iscdrom = devt[i] == 5
            } else {
                iscdrom = false
                create.isEnabled = false
            }

            if (iscdrom) {
                dimg.label = "Disk >> Image"
                diskimage = true
                dimg.isEnabled = false
            } else {
                dimg.isEnabled = true
            }

            dimg.repaint()
        }
    }

    private fun cdromTestunitready(mediaAccess: MediaAccess): Int {
        val bArr = ByteArray(8)
        var scsi = mediaAccess.scsi(byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0), 1, 8, bArr, ByteArray(3))

        if (scsi >= 0) {
            scsi = SCSI.mkInt32(bArr, 0) * SCSI.mkInt32(bArr, 4)
        }

        return scsi
    }

    private fun cdromStartstopunit(mediaAccess: MediaAccess): Int {
        val bArr = ByteArray(8)
        var scsi = mediaAccess.scsi(byteArrayOf(27, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0), 1, 8, bArr, ByteArray(3))

        if (scsi >= 0) {
            scsi = SCSI.mkInt32(bArr, 0) * SCSI.mkInt32(bArr, 4)
        }

        return scsi
    }

    private fun cdromSize(mediaAccess: MediaAccess): Long {
        val bArr: ByteArray = byteArrayOf()
        var scsi = mediaAccess.scsi(byteArrayOf(37, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0), 1, 8, ByteArray(8), ByteArray(3))
            .toLong()

        if (scsi >= 0) {
            scsi = (SCSI.mkInt32(bArr, 0) * SCSI.mkInt32(bArr, 4)).toLong()
        }

        return scsi
    }

    @Throws(IOException::class)
    fun cdromRead(mediaAccess: MediaAccess, j: Long, i: Int, bArr: ByteArray?) {
        val bArr2 = ByteArray(3)
        val i2 = (j / 2048).toInt()
        if (mediaAccess.scsi(
                byteArrayOf(
                        40, 0, (i2 shr 24 and Telnet.TELNET_IAC.toInt()).toByte(),
                        (i2 shr 16 and Telnet.TELNET_IAC.toInt()).toByte(),
                        (i2 shr 8 and Telnet.TELNET_IAC.toInt()).toByte(),
                        (i2 shr 0 and Telnet.TELNET_IAC.toInt()).toByte(), 0,
                        (i / 2048 shr 8 and Telnet.TELNET_IAC.toInt()).toByte(),
                        (i / 2048 shr 0 and Telnet.TELNET_IAC.toInt()).toByte(), 0, 0, 0
                    ),
                1,
                i,
                bArr,
                bArr2
            ) == -1
        ) {
            throw IOException("Error reading CD-ROM.")
        } else if (bArr2[0].toInt() != 0) {
            throw IOException(
                StringBuffer()
                    .append("Error reading CD-ROM.  Sense data (")
                    .append(D.hex(bArr2[0], 1))
                    .append("/")
                    .append(D.hex(bArr2[1], 2))
                    .append("/")
                    .append(D.hex(bArr2[2], 2))
                    .append(")")
                    .toString()
            )
        }
    }

    @Throws(IOException::class)
    fun cdromReadRetry(mediaAccess: MediaAccess, j: Long, i: Int, bArr: ByteArray?) {
        val bArr2 = ByteArray(3)
        val bArr3 = ByteArray(12)
        var i2 = 0
        val i3 = (j / 2048).toInt()
        val bArr4 = byteArrayOf(
            40, 0, (i3 shr 24 and Telnet.TELNET_IAC.toInt()).toByte(),
            (i3 shr 16 and Telnet.TELNET_IAC.toInt()).toByte(),
            (i3 shr 8 and Telnet.TELNET_IAC.toInt()).toByte(),
            (i3 shr 0 and Telnet.TELNET_IAC.toInt()).toByte(), 0,
            (i / 2048 shr 8 and Telnet.TELNET_IAC.toInt()).toByte(),
            (i / 2048 shr 0 and Telnet.TELNET_IAC.toInt()).toByte(), 0, 0, 0
        )

        do {
            System.currentTimeMillis() // ???

            var scsi = mediaAccess.scsi(bArr4, 1, i, bArr, bArr2)

            System.currentTimeMillis() // ???

            if (scsi < 0) {
                cdromTestunitready(mediaAccess)
                cdromStartstopunit(mediaAccess)
                scsi = -1
            }

            if (bArr2[1].toInt() == 41) {
                scsi = -1
            }

            if (bArr2[0].toInt() == 3 || bArr2[0].toInt() == 4) {
                if (bArr2[1].toInt() == 2 && bArr2[2].toInt() == 0) {
                    bArr3[0] = 43
                    bArr3[1] = 0
                    bArr3[2] = bArr4[2]
                    bArr3[3] = bArr4[3]
                    bArr3[4] = bArr4[4]
                    bArr3[5] = bArr4[5]
                    bArr3[6] = 0
                    bArr3[7] = 0
                    bArr3[8] = 0
                    bArr3[9] = 0
                    bArr3[10] = 0
                    bArr3[11] = 0

                    mediaAccess.scsi(bArr3, 1, i, bArr, bArr2)

                    cdromTestunitready(mediaAccess)
                } else if (bArr2[1].toInt() == 17) {
                    cdromTestunitready(mediaAccess)
                    cdromStartstopunit(mediaAccess)
                } else {
                    cdromTestunitready(mediaAccess)
                }

                scsi = -1
            }

            if (scsi >= 0) {
                break
            }

            i2++
        } while (i2 < retrycount)

        if (i2 >= retrycount) {
            D.println(0, "RETRIES FAILED ! ")
        }
    }

    override fun run() {
        var i = 0
        var c = 0.toChar()
        val text = imgFile.text
        var z = false

        if (text == "") {
            browse.isEnabled = true
            fdDrive.isEnabled = true
            imgFile.isEnabled = true
            dimg.isEnabled = true
            return
        }

        val mediaAccess = MediaAccess()
        val mediaAccess2 = MediaAccess()

        try {
            if (iscdrom) {
                val open = mediaAccess.open(fdDrive.selectedItem, 1)
                if (open < 0) {
                    VErrorDialog(
                        StringBuffer()
                            .append("Could not open CDROM (")
                            .append(mediaAccess.dio!!.sysError(-open))
                            .append(")")
                            .toString(),
                        false
                    )
                    throw IOException(StringBuffer().append("Couldn't open cdrom ").append(open).toString())
                }

                cdromTestunitready(mediaAccess)

                c = Char(cdromSize(mediaAccess).toUShort())
                i = 65536
            } else {
                mediaAccess.open(fdDrive.selectedItem, 1)
                c = Char(mediaAccess.size().toUShort())
                i = mediaAccess.dio!!.bytesPerSec * mediaAccess.dio!!.secPerTrack
            }
        } catch (e: IOException) {
            /* no-op */
        }

        if (diskimage || !mediaAccess.wp()) {
            cursor = Cursor.getPredefinedCursor(3)

            var c2 = c

            if (i == 0 || c2.code == 0) {
                VErrorDialog(frame, "Unable to determine disk geometry. Make sure that a disk is in the drive.")
                z = true
                i = 0
                c2 = 0.toChar()
            } else {
                try {
                    mediaAccess2.open(text, if (diskimage) 2 else 0)
                } catch (e2: IOException) {
                    VErrorDialog(
                        frame,
                        StringBuffer().append("Unable to open file").append(text).append(".").toString()
                    )
                }
            }

            var c3 = 0.toChar()
            val bArr = ByteArray(i)

            while (c2.code > 0 && !canceled) {
                try {
                    val i2 = if (i.toLong() < c2.code.toLong()) i else c2.code

                    if (diskimage) {
                        if (iscdrom) {
                            cdromReadRetry(mediaAccess, c3.code.toLong(), i2, bArr)
                        } else {
                            mediaAccess.read(c3.code.toLong(), i2, bArr)
                        }

                        mediaAccess2.write(c3.code.toLong(), i2, bArr)
                    } else {
                        mediaAccess2.read(c3.code.toLong(), i2, bArr)
                        mediaAccess.write(c3.code.toLong(), i2, bArr)
                    }

                    c3 += i2.toChar().code
                    c2 -= i2.toChar().code

                    if (diskimage || c3.code / c.code < 0.95) {
                        progress.updateBar((c3.code / c.code).toFloat())
                    } else {
                        progress.updateBar(0.95f)
                    }
                } catch (e3: IOException) {
                    z = true
                    VErrorDialog(
                        frame,
                        StringBuffer()
                            .append("Error during ")
                            .append(if (diskimage) "image" else "diskette")
                            .append(" creation (")
                            .append(e3)
                            .append(")")
                            .toString()
                    )
                }
            }

            cursor = Cursor.getPredefinedCursor(0)

            if (!z) {
                try {
                    mediaAccess.close()
                    mediaAccess2.close()
                } catch (e4: IOException) {
                    D.println(0, StringBuffer().append("Closing: ").append(e4).toString())
                }

                progress.updateBar((c3.code / c.code).toFloat())
                statLabel.text = if (diskimage) {
                    "Image file was created successfully."
                } else {
                    "Disk was created successfully."
                }
            } else {
                statLabel.text = " "
            }

            create.isEnabled = true
            browse.isEnabled = true
            fdDrive.isEnabled = true
            imgFile.isEnabled = true
            dimg.isEnabled = !iscdrom
        } else {
            VErrorDialog(
                frame,
                StringBuffer()
                    .append("Diskette in drive ")
                    .append(fdDrive.selectedItem)
                    .append(" is write protected.")
                    .toString()
            )

            create.isEnabled = true
            browse.isEnabled = true
            fdDrive.isEnabled = true
            imgFile.isEnabled = true
            dimg.isEnabled = true

            try {
                mediaAccess.close()
            } catch (e5: IOException) {
                /* no-op */
            }
        }
    }

    override fun windowClosing(windowEvent: WindowEvent) {
        canceled = true
        dispose()
    }

    override fun windowActivated(windowEvent: WindowEvent) {}

    override fun windowClosed(windowEvent: WindowEvent) {}

    override fun windowDeactivated(windowEvent: WindowEvent) {}

    override fun windowDeiconified(windowEvent: WindowEvent) {}

    override fun windowIconified(windowEvent: WindowEvent) {}

    override fun windowOpened(windowEvent: WindowEvent) {}
}
