@file:Suppress("DEPRECATION")

package com.hp.ilo2.virtdevs

import com.hp.ilo2.remcons.Telnet
import java.applet.Applet
import java.awt.*
import java.awt.event.*
import java.awt.image.ImageObserver
import java.io.*
import java.lang.Exception
import java.lang.NumberFormatException
import java.net.URL
import java.util.*

/**
 * Main entry point
 *
 * Virtual devices
 */

// TODO get working
class VirtDevs : Applet(), ActionListener, ItemListener, Runnable {

    lateinit var cdSelected: String
    lateinit var roCbox: Checkbox
    private lateinit var cdActive: Canvas
    private lateinit var cdActiveImg: Image
    private lateinit var cdBrowse: Button
    private lateinit var cdCanvas: Canvas
    private lateinit var cdCboxDev: Checkbox
    private lateinit var cdCboxImg: Checkbox
    private lateinit var cdChooseFile: TextField
    private lateinit var cdDriveList: Choice
    private lateinit var cdIcons: Panel
    private lateinit var cdLabel: Label
    private lateinit var cdStartButton: Button
    private lateinit var cdStartImage: Image
    private lateinit var cdStopImage: Image
    private lateinit var cdch: ChiselBox
    private lateinit var ciStartImage: Image
    private lateinit var fdActive: Canvas
    private lateinit var fdActiveImg: Image
    private lateinit var fdBrowse: Button
    private lateinit var fdCanvas: Canvas
    private lateinit var fdCboxDev: Checkbox
    private lateinit var fdCboxImg: Checkbox
    private lateinit var fdChooseFile: TextField
    private lateinit var fdDriveList: Choice
    private lateinit var fdGroup: CheckboxGroup
    private lateinit var fdIcons: Panel
    private lateinit var fdSelected: String
    private lateinit var fdStartButton: Button
    private lateinit var fdStartImage: Image
    private lateinit var fdStopImage: Image
    private lateinit var fdch: ChiselBox
    private lateinit var fdcrImage: Button
    private lateinit var fiStartImage: Image
    private lateinit var floppyImage: Image
    private lateinit var grayDot: Image
    private lateinit var greenDot: Image
    private lateinit var img: Array<Image?>
    private lateinit var parent: Frame
    private lateinit var statLabel: Label
    private lateinit var statusBar: Panel
    private var base: String? = null
    private var cdCboxChecked = 0
    private var cdConnected = false
    private var cdGroup: CheckboxGroup? = null
    private var cdThread: Thread? = null
    private var configuration: String? = null
    private var connections = 0
    private var currentImage: Image? = null
    private var devAuto: String? = null
    private var devCdDevice = 0
    private var devCdrom: String? = null
    private var devFdDevice = 0
    private var devFloppy: String? = null
    private var fdCboxChecked = 0
    private var fdConnected = false
    private var fdConnection: Connection? = null
    private var fdThread: Thread? = null
    private var fdport = 17988
    private var forceConfig = false
    private var host: String? = null
    private var hostAddress: String? = null
    private var key = ByteArray(16)
    private var pre = ByteArray(16)
    private var running = false
    private var servername: String? = null
    private var stopFlag = false
    private var threadInit = false
    private var unqFeature = 0
    var cdConnection: Connection? = null

    private fun getImg(str: String): Image {
        val url = VirtDevs::class.java.classLoader.getResource(str)
        println("Image URL: $url")
        return getImage(url) // TODO: verify
    }

    override fun init() {
        if (UID == 0) {
            UID = hashCode()
        }

        img = arrayOf()
        img[0] = getImg("cdstart.gif")
        img[1] = getImg("cdstop.gif")
        img[2] = getImg("active.gif")
        img[3] = getImg("inactive.gif")
        img[4] = null
        img[5] = getImg("fdstart.gif")
        img[6] = getImg("fdstop.gif")
        img[7] = getImg("fistart.gif")

        val documentBase = documentBase

        host = getParameter("hostAddress")
        if (host == null) {
            host = documentBase.host
        }

        this.base = StringBuffer().append(documentBase.protocol).append("://").append(documentBase.host).toString()
        if (documentBase.port != -1) {
            this.base = StringBuffer().append(this.base).append(":").append(documentBase.port).toString()
        }
        this.base = StringBuffer().append(this.base).append("/").toString()

        val parameter = getParameter("INFO0")
        if (parameter != null) {
            for (i in 0..15) {
                try {
                    pre[i] = parameter.substring(2 * i, 2 * i + 2).toInt(16).toByte()
                    key[i] = 0
                } catch (e: NumberFormatException) {
                    D.println(0, StringBuffer().append("Couldn't parse INFO0: ").append(e).toString())
                }
            }
        }

        try {
            fdport = getParameter("INFO1").toInt()
        } catch (e2: NumberFormatException) {
            D.println(0, StringBuffer().append("Couldn't parse INFO1: ").append(e2).toString())
        }

        configuration = getParameter("INFO2")
        if (configuration == null) {
            configuration = "auto"
        }

        servername = getParameter("INFO3")
        devFloppy = getParameter("floppy")
        devCdrom = getParameter("cdrom")
        devAuto = getParameter("device")

        val parameter2 = getParameter("config")
        if (parameter2 != null) {
            configuration = parameter2
            forceConfig = true
        }

        val parameter3 = getParameter("UNIQUE_FEATURES")
        if (parameter3 != null) {
            try {
                unqFeature = parameter3.toInt()
            } catch (e3: NumberFormatException) {
                D.println(0, StringBuffer().append("Couldn't parse UNIQUE_FEATURES: ").append(e3).toString())
            }
        }

        this.parent = Frame()
    }

    override fun start() {
        Thread(this).start()

        try {
            Thread.sleep(1000L)
        } catch (e: InterruptedException) {
            println(StringBuffer().append("Exception: ").append(e).toString())
        }

        hostAddress = host

        if (uiInit(this.base, img)) {
            setconfig(configuration)

            if (forceConfig) {
                updateconfig()
            }

            show()

            if (devFloppy != null) {
                doFloppy(devFloppy!!)
            }

            if (devCdrom != null) {
                doCdrom(devCdrom!!)
            }
        }
    }

    override fun stop() {
        D.println(3, StringBuffer().append("Stop ").append(this).toString())

        if (fdConnection != null) {
            try {
                fdConnection?.close()
                fdThread = null
            } catch (e: IOException) {
                D.println(3, e.toString())
            }
        }

        if (cdConnection != null) {
            try {
                cdConnection?.close()
                cdThread = null
            } catch (e2: IOException) {
                D.println(3, e2.toString())
            }
        }
    }

    override fun destroy() {
        Thread(this).start()

        try {
            Thread.sleep(1000L)
        } catch (e: InterruptedException) {
            println(StringBuffer().append("Exception: ").append(e).toString())
        }
    }

    @Synchronized
    override fun run() {
        if (!threadInit) {
            prop = Properties()

            try {
                prop?.load(
                    FileInputStream(
                        StringBuffer()
                            .append(System.getProperty("user.home"))
                            .append(System.getProperty("file.separator"))
                            .append(".java")
                            .append(System.getProperty("file.separator"))
                            .append("hp.properties")
                            .toString()
                    )
                )
            } catch (e: Exception) {
                println(StringBuffer().append("Exception: ").append(e).toString())
            }

            cdimg_support = prop?.getProperty("com.hp.ilo2.virtdevs.cdimage", "true").toBoolean()

            MediaAccess().setupDirectIO()

            threadInit = true

            return
        }

        MediaAccess.cleanup()

        threadInit = false
    }

    @Suppress("UNUSED_PARAMETER")
    private fun uiInit(str: String?, imageArr: Array<Image?>): Boolean {
        val devtype: Int
        val devtype2: Int
        var i = 0
        var i2 = 0
        val mediaAccess = MediaAccess()
        val gridBagLayout = GridBagLayout()
        val gridBagConstraints = GridBagConstraints()

        layout = gridBagLayout
        background = Color.lightGray
        statusBar = Panel(FlowLayout(0, 5, 5))

        val panel = Panel(FlowLayout(1, 5, 5))

        if (servername == null || servername == "host is unnamed") {
            servername = host
        }

        val label = Label(StringBuffer().append("Virtual Media: ").append(servername).toString())
        label.font = Font("Arial", 1, 12)

        panel.add(label)
        panel.foreground = Color.white
        panel.background = Color(0, 102, 153)

        if (cdimg_support) {
            cdGroup = CheckboxGroup()
            cdCboxDev = Checkbox("Local Media Drive:", cdGroup, true)
            cdCboxDev.addItemListener(this)
            cdCboxImg = Checkbox("Local Image File:", cdGroup, false)
            cdCboxImg.addItemListener(this)
        } else {
            cdLabel = Label("Local Media Drive:")
        }

        fdCboxChecked = 0
        cdChooseFile = TextField(15)
        cdChooseFile.isEditable = false
        cdChooseFile.addActionListener(this)
        cdDriveList = Choice()
        cdDriveList.add("None")
        fdDriveList = Choice()
        fdDriveList.add("None")

        val mediaCd = mediaAccess.devtype(devCdrom)
        devtype2 = mediaCd

        if (!(devCdrom == null || mediaCd == 1 || devtype2 == 5)) {
            VErrorDialog(
                this.parent,
                StringBuffer().append("Device '").append(devCdrom).append("' is not a CD/DVD-ROM").toString()
            )
            devCdrom = null
        }

        val mediaFloppy = mediaAccess.devtype(devFloppy)
        devtype = mediaFloppy

        if (!(devFloppy == null || mediaFloppy == 1 || devtype == 2)) {
            VErrorDialog(
                this.parent,
                StringBuffer().append("Device '").append(devFloppy).append("' is not a floppy/USBkey").toString()
            )
            devFloppy = null
        }

        if (devAuto != null) {
            when (mediaAccess.devtype(devAuto)) {
                5 -> devCdrom = devAuto
                2 -> devFloppy = devAuto
                else -> {
                    VErrorDialog(
                        this.parent,
                        StringBuffer().append("Device '").append(devAuto)
                            .append("' is neither a floppy/USBkey nor a CD/DVD-ROM").toString()
                    )
                }
            }
        }

        val devices = mediaAccess.devices()
        var i3 = 0

        while (devices != null && i3 < devices.size) {
            D.println(3, StringBuffer().append("init:deviceName = ").append(devices[i3]).toString())
            val devtype4 = mediaAccess.devtype(devices[i3])

            if (devtype4 == 5) {
                cdDriveList.add(devices[i3])
                i2++
                if (devices[i3] == devCdrom) {
                    devCdDevice = i2
                }
            }

            if (devtype4 == 2) {
                fdDriveList.add(devices[i3])
                i++
                if (devices[i3] == devFloppy) {
                    devFdDevice = i
                }
            }
            i3++
        }

        cdDriveList.select(devCdDevice)
        cdDriveList.addItemListener(this)
        fdDriveList.select(devFdDevice)
        fdDriveList.addItemListener(this)
        cdStartButton = Button("")
        cdStartButton.label = "   Connect   "
        cdStartButton.addActionListener(this)
        cdBrowse = Button("Browse")
        cdBrowse.isEnabled = false
        cdBrowse.addActionListener(this)
        cdIcons = Panel(FlowLayout(0, 5, 5))
        greenDot = imageArr[2]!!

        prepareImage(greenDot, cdIcons)

        grayDot = imageArr[3]!!

        prepareImage(grayDot, cdIcons)

        cdStartImage = imageArr[0]!!

        prepareImage(cdStartImage, cdIcons)

        cdStopImage = imageArr[1]!!

        prepareImage(cdStopImage, cdIcons)

        ciStartImage = imageArr[7]!!

        prepareImage(ciStartImage, cdIcons)

        currentImage = cdStopImage
        cdActiveImg = grayDot

        val panel2 = cdIcons
        val canvas: Canvas = object : Canvas() {
            override fun paint(graphics: Graphics) {
                super@VirtDevs.paint(graphics)
                if (currentImage != null) {
                    waitImage(currentImage, this)
                    graphics.color = Color.lightGray
                    graphics.fillRect(2, 2, 36, 36)
                    graphics.drawImage(currentImage, 4, 4, null as ImageObserver?)
                }
            }
        }

        cdCanvas = canvas
        panel2.add(canvas)

        cdCanvas.background = Color.red
        cdCanvas.setSize(40, 40)
        cdCanvas.isVisible = true

        val panel3 = cdIcons
        val canvas2: Canvas = object : Canvas() {
            override fun paint(graphics: Graphics) {
                super@VirtDevs.paint(graphics)
                // if (cdActiveImg != null) {
                waitImage(cdActiveImg, this)
                graphics.drawImage(cdActiveImg, 10, 10, null as ImageObserver?)
                // }
            }
        }

        cdActive = canvas2
        panel3.add(canvas2)

        cdActive.background = Color.lightGray
        cdActive.setSize(40, 40)
        cdActive.isVisible = true

        fdGroup = CheckboxGroup()

        fdCboxDev = Checkbox("Local Media Drive:", fdGroup, true)
        fdCboxDev.addItemListener(this)

        fdCboxImg = Checkbox("Local Image File:", fdGroup, false)
        fdCboxImg.addItemListener(this)

        fdCboxChecked = 0

        fdStartButton = Button("")
        fdStartButton.label = "   Connect   "

        fdBrowse = Button("Browse")
        fdBrowse.addActionListener(this)
        fdBrowse.isEnabled = false

        fdStartButton.addActionListener(this)

        fdIcons = Panel(FlowLayout(0, 5, 5))

        fdStartImage = imageArr[5]!!
        prepareImage(fdStartImage, fdIcons)

        fdStopImage = imageArr[6]!!
        prepareImage(fdStopImage, fdIcons)

        fiStartImage = imageArr[7]!!
        prepareImage(fiStartImage, fdIcons)

        floppyImage = fdStopImage
        fdActiveImg = grayDot

        val panel4 = fdIcons
        val canvas3: Canvas = object : Canvas() {
            override fun paint(graphics: Graphics) {
                super@VirtDevs.paint(graphics)
                // if (floppyImage != null) {
                waitImage(floppyImage, this)
                graphics.color = Color.lightGray
                graphics.fillRect(2, 2, 36, 36)
                graphics.drawImage(floppyImage, 4, 4, null as ImageObserver?)
                // }
            }
        }

        fdCanvas = canvas3
        panel4.add(canvas3)

        fdCanvas.background = Color.red
        fdCanvas.setSize(40, 40)
        fdCanvas.isVisible = true

        val panel5 = fdIcons
        val canvas4: Canvas = object : Canvas() {
            override fun paint(graphics: Graphics) {
                super@VirtDevs.paint(graphics)
                // if (fdActiveImg != null) {
                waitImage(fdActiveImg, this)
                graphics.drawImage(fdActiveImg, 10, 10, null as ImageObserver?)
                // }
            }
        }

        fdActive = canvas4

        panel5.add(canvas4)
        fdActive.background = Color.lightGray
        fdActive.setSize(40, 40)
        fdActive.isVisible = true

        fdChooseFile = TextField(15)
        fdChooseFile.isEditable = false
        fdChooseFile.addActionListener(this)
        if (devFdDevice == 0 && devFloppy != null) {
            fdChooseFile.text = devFloppy
        }

        roCbox = Checkbox("Force read-only access", false)
        roCbox.addItemListener(this)

        gridBagConstraints.anchor = 11
        gridBagConstraints.fill = 2
        gridBagConstraints.weightx = 0.0
        gridBagConstraints.weighty = 0.0

        add(panel, gridBagConstraints, 0, 0, 4, 1)

        cdch = ChiselBox("Virtual CD/DVD-ROM")
        cdch.content.layout = GridBagLayout()

        gridBagConstraints.anchor = 18
        gridBagConstraints.fill = 1
        gridBagConstraints.weightx = 100.0
        gridBagConstraints.weighty = 60.0

        add(cdch, gridBagConstraints, 0, 3, 3, 1)

        gridBagConstraints.fill = 2
        gridBagConstraints.anchor = 17

        if (cdimg_support) {
            cdch.cadd(cdCboxDev, gridBagConstraints, 0, 0, 1, 1)
        } else {
            cdch.cadd(cdLabel, gridBagConstraints, 0, 0, 1, 1)
        }

        gridBagConstraints.fill = 2
        gridBagConstraints.anchor = 17

        cdch.cadd(cdDriveList, gridBagConstraints, 1, 0, 1, 1)

        gridBagConstraints.fill = 2
        gridBagConstraints.anchor = 17

        cdch.cadd(cdStartButton, gridBagConstraints, 2, 0, 1, 1)

        gridBagConstraints.fill = 0
        gridBagConstraints.anchor = 13

        cdch.cadd(cdIcons, gridBagConstraints, 3, 0, 1, 2)

        if (cdimg_support) {
            gridBagConstraints.fill = 2
            gridBagConstraints.anchor = 17

            cdch.cadd(cdCboxImg, gridBagConstraints, 0, 0 + 1, 1, 1)

            gridBagConstraints.anchor = 17
            gridBagConstraints.fill = 2

            cdch.cadd(cdChooseFile, gridBagConstraints, 1, 0 + 1, 1, 1)

            if (devCdDevice == 0 && devCdrom != null) {
                cdChooseFile.text = devCdrom
            }

            gridBagConstraints.fill = 0
            gridBagConstraints.anchor = 17

            cdch.cadd(cdBrowse, gridBagConstraints, 2, 0 + 1, 1, 1)
        }

        fdch = ChiselBox("Virtual Floppy/USBKey")
        fdch.content.layout = GridBagLayout()

        gridBagConstraints.anchor = 18
        gridBagConstraints.fill = 1
        gridBagConstraints.weightx = 100.0
        gridBagConstraints.weighty = 60.0
        add(fdch, gridBagConstraints, 0, 2, 3, 1)

        gridBagConstraints.fill = 2
        gridBagConstraints.anchor = 17

        fdch.cadd(fdCboxDev, gridBagConstraints, 0, 0, 1, 1)

        gridBagConstraints.anchor = 17
        gridBagConstraints.fill = 2

        fdch.cadd(fdDriveList, gridBagConstraints, 1, 0, 1, 1)

        gridBagConstraints.fill = 0
        gridBagConstraints.anchor = 17

        fdch.cadd(fdStartButton, gridBagConstraints, 2, 0, 1, 1)

        gridBagConstraints.fill = 0
        gridBagConstraints.anchor = 13

        fdch.cadd(fdIcons, gridBagConstraints, 3, 0, 1, 2)

        gridBagConstraints.fill = 2
        gridBagConstraints.anchor = 17

        fdch.cadd(fdCboxImg, gridBagConstraints, 0, 0 + 1, 1, 1)

        gridBagConstraints.anchor = 17
        gridBagConstraints.fill = 2

        fdch.cadd(fdChooseFile, gridBagConstraints, 1, 0 + 1, 1, 1)

        gridBagConstraints.fill = 0
        gridBagConstraints.anchor = 17

        fdch.cadd(fdBrowse, gridBagConstraints, 2, 0 + 1, 1, 1)

        gridBagConstraints.anchor = 17

        fdch.cadd(roCbox, gridBagConstraints, 0, 0 + 2, 1, 1)

        statLabel = Label("Select a local drive from the list")
        statLabel.font = Font("Arial", 1, 12)

        statusBar.add(statLabel)

        gridBagConstraints.anchor = 17
        gridBagConstraints.fill = 2
        gridBagConstraints.weightx = 120.0
        gridBagConstraints.weighty = 0.0

        add(statusBar, gridBagConstraints, 0, 4, 1, 1)

        fdcrImage = Button("Create Disk Image")
        fdcrImage.addActionListener(this)

        gridBagConstraints.anchor = 13
        gridBagConstraints.fill = 0
        gridBagConstraints.weightx = 8.0

        add(fdcrImage, gridBagConstraints, 2, 4, 1, 1)

        val mouseAdapter: MouseAdapter = object : MouseAdapter() {
            override fun mouseClicked(mouseEvent: MouseEvent) {
                if (mouseEvent.modifiers and 2 != 0) {
                    D.debug++
                    println(StringBuffer().append("Debug set to ").append(D.debug).toString())
                }
                if (mouseEvent.modifiers and 8 != 0) {
                    D.debug--
                    println(StringBuffer().append("Debug set to ").append(D.debug).toString())
                }
            }
        }

        addMouseListener(mouseAdapter)
        label.addMouseListener(mouseAdapter)
        statusBar.addMouseListener(mouseAdapter)

        if (unqFeature and 1 != 1) {
            return true
        }

        fdch.hide()

        return true
    }

    @Suppress("SameParameterValue")
    private fun add(component: Component, gridBagConstraints: GridBagConstraints, i: Int, i2: Int, i3: Int, i4: Int) {
        gridBagConstraints.gridx = i
        gridBagConstraints.gridy = i2
        gridBagConstraints.gridwidth = i3
        gridBagConstraints.gridheight = i4
        add(component, gridBagConstraints)
    }

    override fun itemStateChanged(itemEvent: ItemEvent) {
        if (itemEvent.itemSelectable == cdDriveList) {
            if (cdDriveList.selectedItem == "None") {
                statLabel.text = "Select a local drive from the list"
            } else {
                statLabel.text = "Press Connect to start"
            }
        }
        if (itemEvent.itemSelectable == fdDriveList) {
            if (fdDriveList.selectedItem == "None") {
                statLabel.text = "Select a local drive from the list"
            } else {
                statLabel.text = "Press Connect to start"
            }
        }
        if (itemEvent.source == fdCboxDev) {
            if (!fdConnected) {
                fdChooseFile.isEditable = false
                fdBrowse.isEnabled = false
                fdDriveList.isEnabled = true
                fdStartButton.isEnabled = true
                fdCboxChecked = 0
            }
        } else if (itemEvent.source == fdCboxImg && !fdConnected) {
            fdChooseFile.isEditable = true
            fdBrowse.isEnabled = true
            fdDriveList.isEnabled = false
            fdStartButton.isEnabled = true
            fdCboxChecked = 1
        }
        if (itemEvent.source == cdCboxDev) {
            if (!cdConnected) {
                cdChooseFile.isEditable = false
                cdBrowse.isEnabled = false
                cdDriveList.isEnabled = true
                cdStartButton.isEnabled = true
                cdCboxChecked = 0
            }
        } else if (itemEvent.source == cdCboxImg && !cdConnected) {
            cdChooseFile.isEditable = true
            cdBrowse.isEnabled = true
            cdDriveList.isEnabled = false
            cdStartButton.isEnabled = true
            cdCboxChecked = 1
        }
        if (itemEvent.source == roCbox) {
            D.println(3, StringBuffer().append("Read only = ").append(roCbox.state).toString())
            if (fdConnection != null) {
                fdConnection?.setWriteProt(roCbox.state)
            }
        }
    }

    override fun actionPerformed(actionEvent: ActionEvent) {
        D.println(
            3,
            StringBuffer()
                .append("ActonPerformed ")
                .append(actionEvent)
                .append(" ")
                .append(actionEvent.source)
                .toString()
        )

        if (cdDriveList.selectedItem == "None" && cdChooseFile.text.isEmpty()) {
            statLabel.text = "Select a local drive from the list"
        } else if (actionEvent.source == cdStartButton) {
            cdSelected = if (!cdimg_support) {
                cdDriveList.selectedItem
            } else if (cdCboxDev.state) {
                cdDriveList.selectedItem
            } else {
                cdChooseFile.text
            }

            doCdrom(cdSelected)
        }

        if (fdCboxDev.state && fdDriveList.selectedItem == "None" || fdCboxImg.state && fdChooseFile.text.isEmpty()) {
            statLabel.text = "Select a local drive or Image"
        } else if (actionEvent.source == fdStartButton) {
            fdSelected = if (fdCboxDev.state) fdDriveList.selectedItem else fdChooseFile.text
            doFloppy(fdSelected)
        }

        if (actionEvent.source == fdBrowse) {
            D.println(3, StringBuffer().append("actionPerformed:fdSelected = ").append(fdSelected).toString())

            val string = VFileDialog("Choose Disk Image File").string

            if (string != null) {
                fdChooseFile.text = string
            }

            fdSelected = fdChooseFile.text

            D.println(3, StringBuffer().append("FDIO.actionPerformed:fdSelected = ").append(fdSelected).toString())

            if (fdThread != null) {
                changeDisk(fdConnection, fdSelected)
            }
        } else if (actionEvent.source == fdChooseFile) {
            fdSelected = fdChooseFile.text

            if (fdThread != null) {
                changeDisk(fdConnection, fdSelected)
            }

            D.println(3, StringBuffer().append("actionPerformed(2):fdSelected = ").append(fdSelected).toString())
        } else if (actionEvent.source == cdBrowse) {
            D.println(3, StringBuffer().append("actionPerformed:cdSelected = ").append(cdSelected).toString())

            val string2 = VFileDialog("Choose CD/DVD-ROM Image File").string

            if (string2 != null) {
                cdChooseFile.text = string2
            }

            cdSelected = cdChooseFile.text

            D.println(3, StringBuffer().append("FDIO.actionPerformed:cdSelected = ").append(cdSelected).toString())

            if (cdThread != null) {
                changeDisk(cdConnection, cdSelected)
            }
        } else if (actionEvent.source == cdChooseFile) {
            cdSelected = fdChooseFile.text

            D.println(3, StringBuffer().append("actionPerformed(2):cdSelected = ").append(cdSelected).toString())

            if (cdThread != null) {
                changeDisk(cdConnection, cdSelected)
            }
        } else if (actionEvent.source == fdcrImage) {
            CreateImage(this.parent)
        }
    }

    private fun doFloppy(str: String) {
        val str2: String
        if (!fdConnected) {
            try {
                fdConnection = Connection(hostAddress, fdport, 1, str, 0, pre, key, this)
                fdConnection!!.setWriteProt(roCbox.state)
                cursor = Cursor.getPredefinedCursor(3)

                try {
                    val connect = fdConnection!!.connect()
                    cursor = Cursor.getPredefinedCursor(0)

                    if (connect == 33) {
                        VErrorDialog(this.parent, "Another virtual media client is connected.")
                    } else if (connect == 34) {
                        str2 = if (rekey("vtdframe.htm")) "Invalid Login.  Try again." else "Invalid Login."
                        VErrorDialog(this.parent, str2)
                    } else if (connect == 35) {
                        VErrorDialog(this.parent, "iLO is not Licenced for Virtual Media.")
                    } else if (connect == 37) {
                        VErrorDialog(this.parent, "The Virtual Device is not configured as a floppy drive.")
                        configuration = "cdrom"
                        setconfig(configuration)
                    } else if (connect != 0) {
                        VErrorDialog(
                            this.parent,
                            StringBuffer()
                                .append("Unexpected HELLO response (")
                                .append(Integer.toHexString(connect))
                                .append(").  Connection Failed.")
                                .toString()
                        )
                    } else {
                        fdThread = Thread(fdConnection, "fdConnection")
                        fdThread!!.start()
                        floppyImage = if (fdCboxChecked == 0) fdStartImage else fiStartImage
                        fdActiveImg = greenDot
                        fdStartButton.label = "Disconnect"
                        fdcrImage.isEnabled = false
                        connections++
                        fdCboxDev.isEnabled = false
                        fdDriveList.isEnabled = false
                        fdCboxImg.isEnabled = false
                        statLabel.text = "Virtual Media Connected"
                        fdcrImage.isEnabled = false
                        fdCanvas.background = Color.green
                        fdCanvas.invalidate()
                        fdActive.repaint()

                        repaint()

                        fdConnected = true

                        if (configuration == "auto") {
                            setconfig("floppy")
                        }
                    }
                } catch (e: Exception) {
                    cursor = Cursor.getPredefinedCursor(0)
                    D.println(0, "Couldn't connect!\n")
                    println(e.message)
                    VErrorDialog(
                        this.parent,
                        "Could not connect Virtual Media. iLO Virtual Media service may be disabled."
                    )
                }
            } catch (e2: Exception) {
                VErrorDialog(this.parent, e2.message)
            }
        } else {
            fdStartButton.isEnabled = false
            try {
                fdConnection?.close()
            } catch (e3: Exception) {
                D.println(0, StringBuffer().append("Exception during close: ").append(e3).toString())
            }
        }
    }

    fun doCdrom(str: String) {
        val str2: String
        if (!cdConnected) {
            try {
                cdConnection = Connection(hostAddress, fdport, 2, str, 0, pre, key, this)
                cdConnection!!.setWriteProt(true)

                try {
                    val connect = cdConnection!!.connect()
                    if (connect == 33) {
                        VErrorDialog(this.parent, "Another virtual media client is connected.")
                    } else if (connect == 34) {
                        str2 = if (rekey("vtdframe.htm")) "Invalid Login.  Try again." else "Invalid Login."
                        VErrorDialog(this.parent, str2)
                    } else if (connect == 35) {
                        VErrorDialog(this.parent, "iLO is not Licenced for Virtual Media.")
                    } else if (connect == 37) {
                        VErrorDialog(this.parent, "The Virtual Device is not configured as a CD/DVD-ROM drive.")
                        configuration = "floppy"
                        setconfig(configuration)
                    } else if (connect != 0) {
                        VErrorDialog(
                            this.parent,
                            StringBuffer()
                                .append("Unexpected HELLO response (")
                                .append(Integer.toHexString(connect))
                                .append(").  Connection Failed.")
                                .toString()
                        )
                    } else {
                        cdThread = Thread(cdConnection, "cdConnection")
                        cdThread?.start()
                        fdcrImage.isEnabled = false
                        connections++
                        cdDriveList.isEnabled = false
                        cdStartButton.label = "Disconnect"
                        statLabel.text = "Virtual Media Connected"
                        fdcrImage.isEnabled = false
                        if (cdimg_support) {
                            cdCboxDev.isEnabled = false
                            cdCboxImg.isEnabled = false
                        }
                        currentImage = if (cdCboxChecked == 0) cdStartImage else ciStartImage
                        cdActiveImg = greenDot
                        cdCanvas.background = Color.green
                        cdCanvas.invalidate()
                        cdActive.repaint()

                        repaint()

                        cdConnected = true

                        if (configuration == "auto") {
                            setconfig("cdrom")
                        }
                    }
                } catch (e: Exception) {
                    D.println(0, "Couldn't connect!\n")
                    println(e.message)
                    VErrorDialog(
                        this.parent,
                        "Could not connect Virtual Media. iLO Virtual Media service may be disabled."
                    )
                }
            } catch (e2: Exception) {
                VErrorDialog(this.parent, e2.message)
            }
        } else {
            try {
                cdConnection?.close()
            } catch (e3: Exception) {
                D.println(0, StringBuffer().append("Exception during close: ").append(e3).toString())
            }
        }
    }

    override fun paint(graphics: Graphics) {
        super@VirtDevs.paint(graphics)
    }

    override fun update(graphics: Graphics) {
        paint(graphics)
    }

    fun waitImage(image: Image?, imageObserver: ImageObserver?) {
        var checkImage: Int
        val currentTimeMillis = System.currentTimeMillis()

        do {
            checkImage = checkImage(image, imageObserver)

            if (checkImage and Telnet.TELNET_ENCRYPT.toInt() == 0) {
                Thread.yield()
                if (System.currentTimeMillis() - currentTimeMillis > 2000) {
                    return
                }
            } else {
                return
            }
        } while (checkImage and ImageDone != ImageDone)
    }

    private fun setconfig(str: String?) {
        if (str == "floppy") {
            fdStartButton.isEnabled = true

            if (fdCboxChecked == 1) {
                fdCboxImg.isEnabled = true
                fdCboxDev.isEnabled = false
            } else {
                fdCboxDev.isEnabled = true
                fdCboxImg.isEnabled = false
            }

            fdDriveList.isEnabled = false
            fdch.checkEnabled = true
            roCbox.isEnabled = false
            cdStartButton.isEnabled = false
            cdDriveList.isEnabled = false
            cdch.checkEnabled = false

            if (cdimg_support) {
                cdChooseFile.isEditable = false
                cdBrowse.isEnabled = false
                cdCboxDev.isEnabled = false
                cdCboxImg.isEnabled = false
            }
        } else if (str == "cdrom") {
            fdStartButton.isEnabled = false
            fdCboxDev.isEnabled = false
            fdCboxImg.isEnabled = false
            fdDriveList.isEnabled = false
            fdChooseFile.isEditable = false
            fdBrowse.isEnabled = false
            roCbox.isEnabled = false
            fdch.checkEnabled = false

            cdStartButton.isEnabled = true

            if (cdCboxChecked == 1) {
                cdCboxImg.isEnabled = true
                cdCboxDev.isEnabled = false
            } else {
                cdCboxDev.isEnabled = true
                cdCboxImg.isEnabled = false
            }

            cdDriveList.isEnabled = false
            cdch.checkEnabled = true

            @Suppress("ControlFlowWithEmptyBody")
            if (cdimg_support) {
                /* no-op */
            }
        } else {
            fdStartButton.isEnabled = true
            fdCboxDev.isEnabled = true
            fdCboxImg.isEnabled = true
            fdChooseFile.isEditable = false
            roCbox.isEnabled = true
            fdch.checkEnabled = true
            cdStartButton.isEnabled = true
            cdch.checkEnabled = true

            if (cdimg_support) {
                cdCboxDev.isEnabled = true
                cdCboxImg.isEnabled = true
                cdChooseFile.isEditable = false
                cdBrowse.isEnabled = false
            }

            if (fdCboxChecked == 0) {
                fdDriveList.isEnabled = true
                fdChooseFile.isEditable = false
                fdBrowse.isEnabled = false
            } else {
                fdDriveList.isEnabled = false
                fdChooseFile.isEditable = true
                fdBrowse.isEnabled = true
            }

            if (cdCboxChecked == 0) {
                cdDriveList.isEnabled = true
                cdChooseFile.isEditable = false
                cdBrowse.isEnabled = false
                return
            }

            cdDriveList.isEnabled = false
            cdChooseFile.isEditable = true
            cdBrowse.isEnabled = true
        }
    }

    private fun updateconfig() {
        try {
            val url = URL(StringBuffer().append(this.base).append("modusb.cgi?usb=").append(configuration).toString())
            url.openConnection()

            val bufferedReader = BufferedReader(InputStreamReader(url.openStream()))
            while (true) {
                val readLine = bufferedReader.readLine()
                if (readLine == null) {
                    bufferedReader.close()

                    return
                }

                D.println(3, StringBuffer().append("updcfg: ").append(readLine).toString())
            }
        } catch (e: Exception) {
            VErrorDialog(
                this.parent,
                StringBuffer().append("Error updating device configuraiton (").append(e).append(")").toString()
            )

            e.printStackTrace()
        }
    }

    @Suppress("SameParameterValue")
    private fun rekey(str: String?): Boolean {
        var str2: String? = null

        return try {
            D.println(3, StringBuffer().append("Downloading new key: ").append(this.base).append(str).toString())

            val bufferedReader = BufferedReader(
                InputStreamReader(URL(StringBuffer().append(this.base).append(str).toString()).openStream())
            )

            while (true) {
                val readLine = bufferedReader.readLine() ?: break

                D.println(0, StringBuffer().append("rekey: ").append(readLine).toString())

                if (readLine.startsWith("info0=\"")) {
                    str2 = readLine.substring(7, ImageDone)

                    break
                }
            }

            bufferedReader.close()

            if (str2 == null) {
                VErrorDialog(this.parent, "Error retrieving new key")
                return false
            }

            for (i in 0..15) {
                try {
                    pre[i] = str2.substring(2 * i, 2 * i + 2).toInt(16).toByte()
                    key[i] = 0
                } catch (e: NumberFormatException) {
                    D.println(0, StringBuffer().append("Couldn't parse new key: ").append(e).toString())

                    VErrorDialog(this.parent, "Error parsing new key")

                    return false
                }
            }

            true
        } catch (e2: Exception) {
            D.println(0, StringBuffer().append("rekey: ").append(e2).toString())
            VErrorDialog(this.parent, "Error retrieving new key")

            false
        }
    }

    private fun changeDisk(connection: Connection?, str: String) {
        try {
            connection?.changeDisk(str)
        } catch (e: IOException) {
            VErrorDialog(this.parent, StringBuffer().append("Can't change disk (").append(e).append(")").toString())
        }
    }

    fun fdDisconnect() {
        fdThread = null

        if (fdCboxChecked == 0) {
            fdDriveList.isEnabled = true
            fdChooseFile.isEditable = false
            fdBrowse.isEnabled = false
        } else {
            fdDriveList.isEnabled = false
            fdChooseFile.isEditable = true
            fdBrowse.isEnabled = true
        }

        fdStartButton.label = "   Connect   "

        if (!cdConnected) {
            fdcrImage.isEnabled = true
        }

        fdCboxDev.isEnabled = true
        fdCboxImg.isEnabled = true
        statLabel.text = "Virtual Media Disconnected"
        floppyImage = fdStopImage
        fdActiveImg = grayDot
        fdCanvas.background = Color.red
        fdCanvas.invalidate()
        fdActive.repaint()

        repaint()

        fdConnected = false
        fdStartButton.isEnabled = true

        if (configuration == "auto") {
            setconfig(configuration)
        }

        val i = connections - 1
        connections = i

        if (i == 0) {
            fdcrImage.isEnabled = true
        }
    }

    fun cdDisconnect() {
        if (cdimg_support) {
            cdCboxDev.isEnabled = true
            cdCboxImg.isEnabled = true
        }

        cdThread = null

        if (cdCboxChecked == 0) {
            cdDriveList.isEnabled = true
            cdChooseFile.isEditable = false
            cdBrowse.isEnabled = false
        } else {
            cdDriveList.isEnabled = false
            cdChooseFile.isEditable = true
            cdBrowse.isEnabled = true
        }

        cdStartButton.label = "   Connect   "

        if (!fdConnected) {
            fdcrImage.isEnabled = true
        }

        statLabel.text = "Virtual Media Disconnected"
        currentImage = cdStopImage
        cdActiveImg = grayDot
        cdCanvas.background = Color.red
        cdCanvas.invalidate()
        cdActive.repaint()

        repaint()

        cdConnected = false
        cdStartButton.isEnabled = true

        if (configuration == "auto") {
            setconfig(configuration)
        }

        val i = connections - 1
        connections = i

        if (i == 0) {
            fdcrImage.isEnabled = true
        }
    }

    companion object {
        const val ImageDone = 39
        const val UNQF_HIDEFLP = 1
        var UID = 0
        var cdimg_support = true
        var classFileDescriptor: Class<*>? = null
        var classNetSocket: Class<*>? = null
        var classSocketImpl: Class<*>? = null
        var prop: Properties? = null

//        fun getSockFd(socket: Socket?): Int {
//            val cls: Class<*>?
//            val cls2: Class<*>?
//            val cls3: Class<*>?
//            var i = -1
//            var field: Field? = null
//            var field2: Field? = null
//
//            try {
//                if (classNetSocket == null) {
//                    cls = clazz("java.net.Socket")
//                    classNetSocket = cls
//                } else {
//                    cls = classNetSocket
//                }
//
//                val declaredFields = cls.declaredFields
//                var i2 = 0
//
//                while (true) {
//                    if (i2 >= declaredFields.size) {
//                        break
//                    } else if (declaredFields[i2].name == "impl") {
//                        field = declaredFields[i2]
//                        field.isAccessible = true
//                        break
//                    } else {
//                        i2++
//                    }
//                }
//
//                val socketImpl = field[socket] as SocketImpl
//
//                if (classSocketImpl == null) {
//                    cls2 = clazz("java.net.SocketImpl")
//                    classSocketImpl = cls2
//                } else {
//                    cls2 = classSocketImpl
//                }
//
//                val declaredFields2 = cls2.declaredFields
//                var i3 = 0
//
//                while (true) {
//                    if (i3 >= declaredFields2.size) {
//                        break
//                    } else if (declaredFields2[i3].name == "fd") {
//                        field2 = declaredFields2[i3]
//                        field2.isAccessible = true
//
//                        break
//                    } else {
//                        i3++
//                    }
//                }
//
//                val fileDescriptor = field2[socketImpl] as FileDescriptor
//
//                if (classFileDescriptor == null) {
//                    cls3 = clazz("java.io.FileDescriptor")
//                    classFileDescriptor = cls3
//                } else {
//                    cls3 = classFileDescriptor
//                }
//
//                val declaredFields3 = cls3.declaredFields
//                var i4 = 0
//
//                while (true) {
//                    if (i4 >= declaredFields3.size) {
//                        break
//                    } else if (declaredFields3[i4].name == "fd") {
//                        field2 = declaredFields3[i4]
//                        field2.isAccessible = true
//                        break
//                    } else {
//                        i4++
//                    }
//                }
//
//                i = field2.getInt(fileDescriptor)
//            } catch (e: Exception) {
//                println(StringBuffer().append("Ex: ").append(e).toString())
//            }
//
//            return i
//        }

//        private fun clazz(str: String?): Class<*> {
//            return try {
//                Class.forName(str)
//            } catch (e: ClassNotFoundException) {
//                throw NoClassDefFoundError(e.message)
//            }
//        }
    }
}
