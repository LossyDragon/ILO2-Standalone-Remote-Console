package com.hp.ilo2.remcons

import java.applet.Applet
import java.applet.AppletStub
import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.ItemEvent
import java.awt.event.ItemListener
import java.io.File
import java.io.FileInputStream
import java.lang.Exception
import java.lang.NumberFormatException
import java.lang.Runnable
import java.lang.StringIndexOutOfBoundsException
import java.lang.Thread
import java.util.*

/**
 * Remote Console (Remcons)
 */
class Remcons(
    private var params: HashMap<String, String>
) : Applet(), ActionListener, ItemListener, TimerListener, Runnable, AppletStub {

    companion object {
        private const val SESSION_TIMEOUT_DEFAULT = 900
        private const val KEEP_ALIVE_INTERVAL = 30
        private const val INFINITE_TIMEOUT = Int.MAX_VALUE - 7
        private const val HP_MOUSE_WARNING = "The High Performance Mouse is supported natively on Microsoft Windows " +
            "Server 2000 SP3 or later and Windows 2003 or later. Linux users should enable the High-Performance " +
            "Mouse option once the HP iLO2 High-Performance Mouse for Linux driver is installed."

        var prop: Properties? = null

        private val base64 = charArrayOf(
            '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000',
            '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000',
            '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000',
            '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000',
            '\u0000', '\u0000', '\u0000', '>', '\u0000', '\u0000', '\u0000', '?', '4', '5',
            '6', '7', '8', '9', ':', ';', '<', '=', '\u0000', '\u0000',
            '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0001', '\u0002', '\u0003', '\u0004',
            '\u0005', '\u0006', '\u0007', '\b', '\t', '\n', '\u000b', '\u000c', '\r', '\u000e',
            '\u000f', '\u0010', '\u0011', '\u0012', '\u0013', '\u0014', '\u0015', '\u0016', '\u0017', '\u0018',
            '\u0019', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u001a', '\u001b', '\u001c',
            '\u001d', '\u001e', '\u001f', ' ', '!', '"', '#', '$', '%', '&',
            '\'', '(', ')', '*', '+', ',', '-', '.', '/', '0',
            '1', '2', '3', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000'
        )

        init {
            prop = Properties()

            try {
                val userHome = System.getProperty("user.home")
                val fis = FileInputStream(userHome + File.separator + ".java" + File.separator + "hp.properties")
                prop!!.load(fis)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // private val parentFrame: Frame? = null

    private lateinit var session: Cim
    private lateinit var toolBar: Panel
    private val lt = LocaleTranslator()
    private val translate = false
    private var altLock: Checkbox? = null
    private var altLockLabel: Label? = null
    private var debugMsg = false
    private var hpMouse: Checkbox? = null
    private var hpMouseLabel: Label? = null
    private var hpMouseOnce = false
    private var hpMouseState = false
    private var huust = ""
    private var initialized = 0
    private var kbdLocale: Choice? = null
    private var kbdLocaleLabel: Label? = null
    private var launchTerminalServices = false
    private var localCursor: Choice? = null
    private var localCursorLabel: Label? = null
    private var login: String? = null
    private var mouseMode = 0
    private var numCursors = 0
    private var portNum = 23
    private var refresh: Button? = null
    private var rndmNums = IntArray(12)
    private var sendCtrlAltDel: Button? = null
    private var sessionDecryptKey: ByteArray = ByteArray(16)
    private var sessionEncryptKey: ByteArray = ByteArray(16)
    private var sessionEncryptionEnabled = false
    private var sessionIp: String? = null
    private var sessionKeyIndex = 0
    private var sessionTimeout = SESSION_TIMEOUT_DEFAULT
    private var sessionWindow: Panel? = null
    private var termSvcs: Button? = null
    private var termSvcsLabel = "Terminal Svcs"
    private var terminalServicesPort = 3389
    private var timeoutCountdown = 0
    private var timer: Timer? = null
    private var tsParam = 0

    override fun init() {
        val localThread = Thread(this)
        localThread.start()

        background = Color.white

        initParams()

        refresh = Button("")
        refresh!!.addActionListener(this)

        sendCtrlAltDel = Button("")
        sendCtrlAltDel!!.addActionListener(this)

        termSvcs = Button("")
        termSvcs!!.addActionListener(this)

        if (tsParam and 0x1 == 0) {
            termSvcs!!.isEnabled = false
        }

        altLock = Checkbox("", null, false)
        altLock!!.addItemListener(this)
        altLock!!.background = Color.white

        altLockLabel = Label("", 2)

        hpMouse = Checkbox("", null, hpMouseState)
        hpMouse!!.addItemListener(this)
        hpMouse!!.background = Color.white

        hpMouseLabel = Label("", 2)

        localCursor = Choice()
        localCursor!!.add("Default")
        localCursor!!.add("Crosshairs")

        if (System.getProperty("java.version", "0") > "1.2") {
            localCursor!!.add("Hidden")
            localCursor!!.add("Dot")
            localCursor!!.add("Outline")
        }

        localCursor!!.addItemListener(this)

        localCursorLabel = Label("", 2)

        val str = lt.getSelected()
        if (lt.showgui) {
            kbdLocale = Choice()
            val locales = lt.getLocales()
            for (locale in locales) {
                kbdLocale!!.add(locale)
            }
            if (str != null) kbdLocale!!.select(str)
            kbdLocale!!.addItemListener(this)
        }

        kbdLocaleLabel = Label("", 2)
        session = Cim()

        if (sessionEncryptionEnabled) {
            session.setupEncryption(sessionEncryptKey, sessionKeyIndex)
            session.setupDecryption(sessionDecryptKey)
        }

        session.setMouseProtocol(mouseMode)

        for (i in 0..11) {
            rndmNums[i] = (Math.random() * 4.0).toInt() * 85
        }

        session.setSigColors(rndmNums)
        if (debugMsg) {
            session.enableDebug()
        } else {
            session.disableDebug()
        }

        toolBar = Panel(FlowLayout(1, 1, 7)).apply {
            add(refresh)
            add(termSvcs)
            add(sendCtrlAltDel)
            add(altLockLabel)
            add(altLock)
            add(hpMouseLabel)
            add(hpMouse)
            add(localCursorLabel)
            add(localCursor)
        }

        if (kbdLocale != null) {
            toolBar.add(kbdLocaleLabel)
            toolBar.add(kbdLocale)
        }

        session.enableKeyboard()

        updateStrings()

        sessionWindow = Panel()
        sessionWindow!!.layout = GridBagLayout()

        val localObject = GridBagConstraints().apply {
            anchor = 17
            fill = 0
            gridheight = 1
            gridwidth = 1
            gridx = 0
            gridy = 0
            weightx = 100.0
            weighty = 100.0
        }

        sessionWindow!!.add(toolBar, localObject)
        localObject.gridy = 1
        sessionWindow!!.add(session, localObject)

        layout = FlowLayout(0)
        add(sessionWindow)
        println("Applet initialized...")

        initialized = 1
    }

    override fun start() {
        println("Applet started...")

        updateStrings()
        timeoutCountdown = sessionTimeout
        startSession()

        if (sessionTimeout == INFINITE_TIMEOUT)
            println("Remote Console inactivity timeout = infinite.")
        else {
            println("Remote Console inactivity timeout = " + sessionTimeout / 60 + " minutes.")
        }
    }

    override fun stop() {
        stopSession()
        println("Applet stopped...")
    }

    override fun timeout(callbackInfo: Any?) {
        if (session.uiDirty) {
            session.uiDirty = false
            timeoutCountdown = sessionTimeout
            session.sendKeepAliveMsg()
        } else {
            session.sendAutoAliveMsg()
            timeoutCountdown -= KEEP_ALIVE_INTERVAL

            if (timeoutCountdown <= 0) {
                if (System.getProperty("java.version", "0") < "1.2") {
                    stopSession()
                }
            }
        }
    }

    private fun updateStrings() {
        if (!translate) {
            altLockLabel!!.text = "Alt Lock"
            localCursorLabel!!.text = "Local Cursor"
            kbdLocaleLabel!!.text = "Locale"
            hpMouseLabel!!.text = "High Performance Mouse"
            refresh!!.label = "Refresh"
            sendCtrlAltDel!!.label = "Ctrl-Alt-Del"
            termSvcs!!.label = termSvcsLabel
        } else {
            altLockLabel!!.text = "Altキーロック"
            refresh!!.label = "リフレッシュ"
            sendCtrlAltDel!!.label = "Ctrl-Alt-Del"
            termSvcs!!.label = termSvcsLabel
        }
    }

    override fun itemStateChanged(paramItemEvent: ItemEvent) {
        if (paramItemEvent.source == altLock) {
            if (altLock!!.state) {
                session.enableAltlock()
            } else {
                session.disableAltlock()
            }

            session.requestFocus()
        } else if (paramItemEvent.source == hpMouse) {
            var i = 1
            val bool = hpMouse!!.state

            if (!hpMouseOnce) {
                hpMouseOnce = true
                val localOkCancelDialog = OkCancelDialog(HP_MOUSE_WARNING, true)

                if (!localOkCancelDialog.result()) {
                    i = 0
                    hpMouse!!.state = hpMouseState
                }
            }

            if (i != 0) {
                hpMouseState = bool
                session.mouseModeChange(bool)
            }
        } else if (paramItemEvent.source == localCursor) {
            when (localCursor!!.selectedItem) {
                "Default" -> session.setCursor(0)
                "Crosshairs" -> session.setCursor(1)
                "Hidden" -> session.setCursor(2)
                "Dot" -> session.setCursor(3)
                "Outline" -> session.setCursor(4)
            }
        } else if (paramItemEvent.source == kbdLocale) {
            session.setLocale(kbdLocale!!.selectedItem)
        }
    }

    private fun startSession() {
        if (sessionIp == null) {
            session.connect(huust, login, portNum, tsParam, terminalServicesPort)
        } else {
            session.connect(sessionIp!!, login, portNum, tsParam, terminalServicesPort)
        }

        timer = Timer(30000, false, session)
        timer!!.setListener(this, null)
        timer!!.start()

        if (launchTerminalServices) {
            session.startRdp()
        }
    }

    private fun stopSession() {
        if (timer != null) {
            timer!!.stop()
            timer = null
        }

        session.disconnect()
    }

    fun setHost(hst: String) {
        huust = hst
    }

    override fun actionPerformed(paramActionEvent: ActionEvent) {
        when (paramActionEvent.source) {
            refresh -> {
                session.refreshScreen()
                session.requestFocus()
            }
            sendCtrlAltDel -> {
                session.sendCtrlAltDel()
                session.requestFocus()
            }
            termSvcs -> {
                session.startRdp()
            }
        }
    }

    override fun getParameter(name: String): String? {
        return params[name]
    }

    fun addParameter(name: String, value: String) {
        params[name] = value
    }

    private fun initParams() {
        login = parseLogin(getParameter("INFO0"))
        if (login!!.isNotEmpty()) {
            if (getParameter("INFO1") != null) {
                login = "\u001b[4$login"
            }
            login = "\u001b[7\u001b[9$login"
        }

        var str = getParameter("INFO6")
        if (str != null) {
            portNum = try {
                str.toInt()
            } catch (e: NumberFormatException) {
                23
            }
        }

        str = getParameter("INFOM")
        if (str != null) {
            mouseMode = try {
                str.toInt()
            } catch (e: NumberFormatException) {
                0
            }
        }

        str = getParameter("INFOMM")
        if (str != null) {
            hpMouseState = try {
                str.toInt() == 1
            } catch (e: NumberFormatException) {
                false
            }
        }

        str = getParameter("INFO7")
        if (str != null) {
            try {
                sessionTimeout = str.toInt()
                sessionTimeout *= 60
            } catch (e: NumberFormatException) {
                sessionTimeout = 900
            }
        } else {
            sessionTimeout = 900
        }

        str = getParameter("INFOA")
        var i: Int

        if (str != null) {
            i = try {
                str.toInt()
            } catch (e: NumberFormatException) {
                0
            }
            sessionEncryptionEnabled = i == 1
        } else {
            sessionEncryptionEnabled = false
        }

        if (sessionEncryptionEnabled) {
            str = getParameter("INFOB")
            if (str != null) {
                try {
                    i = 0
                    while (i < 16) {
                        sessionDecryptKey[i] = str.substring(2 * i, 2 * i + 2).toInt(16).toByte()
                        i++
                    }
                } catch (localNumberFormatException5: NumberFormatException) {
                    println("Couldn't parse INFOB: $localNumberFormatException5")
                }
            } else {
                sessionDecryptKey = byteArrayOf()
            }

            str = getParameter("INFOC")
            if (str != null) {
                try {
                    for (j in 0..15) {
                        sessionEncryptKey[j] = str.substring(2 * j, 2 * j + 2).toInt(16).toByte()
                    }
                } catch (localNumberFormatException6: NumberFormatException) {
                    println("Couldn't parse INFOC: $localNumberFormatException6")
                }
            } else {
                sessionEncryptKey = byteArrayOf()
            }

            str = getParameter("INFOD")
            sessionKeyIndex = if (str != null) {
                try {
                    str.toInt()
                } catch (localNumberFormatException7: NumberFormatException) {
                    0
                }
            } else {
                0
            }
        }

        str = getParameter("INFON")

        var k = 0
        if (str != null) {
            k = try {
                str.toInt()
            } catch (localNumberFormatException9: NumberFormatException) {
                0
            }
        }

        tsParam = k and 0xFF00
        getTerminalSvcsLabel(tsParam shr 8)
        k = k and 0xFF

        when (k) {
            0 -> {
                launchTerminalServices = false
                tsParam = tsParam or 0x1
            }
            1 -> {
                launchTerminalServices = false
            }
            else -> {
                launchTerminalServices = true
                tsParam = tsParam or 0x1
            }
        }

        str = getParameter("INFOO")
        if (str != null) {
            terminalServicesPort = try {
                str.toInt()
            } catch (localNumberFormatException10: NumberFormatException) {
                0
            }
        }

        str = getParameter("DEBUG")
        debugMsg = str != null && str.isNotEmpty()

        str = getParameter("IPADDR")
        if (str != null) {
            sessionIp = str
        }

        str = getParameter("cursors")
        if (str != null) {
            numCursors = str.toInt()
        }
    }

    private fun parseLogin(paramString: String?): String? {
        if (paramString.isNullOrEmpty()) {
            return null
        }

        if (paramString.startsWith("Compaq-RIB-Login=")) {
            var str = "\u001b[!"

            try {
                str += paramString.substring(17, 73)
                str += '\r'
                str += paramString.substring(74, 106)
                str += '\r'
            } catch (e: StringIndexOutOfBoundsException) {
                return null
            }

            return str
        }

        return base64Decode(paramString)
    }

    private fun base64Decode(paramString: String): String {
        var n = 0
        var i1 = 0
        var str = ""

        while (n + 3 < paramString.length && i1 == 0) {
            val i = base64[paramString[n].code and 0x7F].code
            val j = base64[paramString[n + 1].code and 0x7F].code
            val k = base64[paramString[n + 2].code and 0x7F].code
            val m = base64[paramString[n + 3].code and 0x7F].code

            var c1 = ((i shl 2) + (j shr 4)).toChar()
            var c2 = ((j shl 4) + (k shr 2)).toChar()
            var c3 = ((k shl 6) + m).toChar()
            c1 = (c1.code and 0xFF).toChar()
            c2 = (c2.code and 0xFF).toChar()
            c3 = (c3.code and 0xFF).toChar()

            if (c1 == ':') {
                c1 = '\r'
            }

            if (c2 == ':') {
                c2 = '\r'
            }

            if (c3 == ':') {
                c3 = '\r'
            }

            str += c1
            if (paramString[n + 2] == '=') {
                i1++
            } else {
                str += c2
            }

            if (paramString[n + 3] == '=') {
                i1++
            } else {
                str += c3
            }

            n += 4
        }

        if (str.isNotEmpty()) {
            str += '\r'
        }

        return str
    }

    override fun paint(paramGraphics: Graphics) {}

    override fun run() {
        if (System.getProperty("os.name").lowercase(Locale.getDefault()).startsWith("windows") &&
            !lt.windows
        ) {
            Locale.setDefault(Locale.US)
        }
    }

    private fun getTerminalSvcsLabel(paramInt: Int) {
        val str: String = when (paramInt) {
            0 -> "mstsc"
            1 -> "vnc"
            else -> "type$paramInt"
        }
        termSvcsLabel = prop!!.getProperty("$str.label", "Terminal Svcs")
    }

    override fun appletResize(width: Int, height: Int) {}
}
