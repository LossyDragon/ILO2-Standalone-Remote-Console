package com.hp.ilo2.remcons

import java.awt.*
import java.awt.event.*
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InterruptedIOException
import java.net.Socket
import java.net.SocketException
import java.net.UnknownHostException
import java.security.NoSuchAlgorithmException
import java.util.*


open class Telnet : Panel(), Runnable, MouseListener, FocusListener, KeyListener {

    @Suppress("unused")
    companion object {
        const val TELNET_AO = 0xf5.toByte()
        const val TELNET_AYT = 0xf6.toByte()
        const val TELNET_BRK = 0xf3.toByte()
        const val TELNET_CHG_ENCRYPT_KEYS = 0xc1.toByte()
        const val TELNET_DM = 0xf2.toByte()
        const val TELNET_DO = 0xfd.toByte()
        const val TELNET_DONT = 0xfe.toByte()
        const val TELNET_EC = 0xf7.toByte()
        const val TELNET_EL = 0xf8.toByte()
        const val TELNET_ENCRYPT = 0xc0.toByte()
        const val TELNET_GA = 0xf9.toByte()
        const val TELNET_IAC = 0xff.toByte()
        const val TELNET_IP = 0xf4.toByte()
        const val TELNET_NOP = 0xf1.toByte()
        const val TELNET_SB = 0xfa.toByte()
        const val TELNET_SE = 0xf0.toByte()
        const val TELNET_WILL = 0xfb.toByte()
        const val TELNET_WONT = 0xfc.toByte()
        private const val CMD_TS_AVAIL = 0xc2.toByte()
        private const val CMD_TS_NOT_AVAIL = 0xc3.toByte()
        private const val CMD_TS_STARTED = 0xc4.toByte()
        private const val CMD_TS_STOPPED = 0xc5.toByte()
        private const val TELNET_PORT = 23
    }

    private val decryptKey = ByteArray(16)
    private val statusBox: TextField = TextField(60)
    private val statusFields = arrayOfNulls<String>(5)
    private val translator = LocaleTranslator()
    private var connected = 0
    private var dataInputStream: DataInputStream? = null
    private var decryptionActive = false
    private var dvcEncryption = false
    private var dvcMode = false
    private var enableTerminalServices = false
    private var host = ""
    private var login = ""
    private var port = TELNET_PORT
    private var rc4decrypter: RC4? = null
    private var rdpProc: Process? = null
    private var receiver: Thread? = null
    private var socket: Socket? = null
    private var seized = false
    private var terminalServicesPort = 3389
    protected var encryptionEnabled = false
    var out: DataOutputStream? = null
    var screen: dvcwin = dvcwin(1600, 1200)
    var tsType = 0

    init {
        initLayout() // Satisfies: Leaking 'this' in constructor of non-final class Telnet

        val isWindows = System.getProperty("os.name").lowercase(Locale.getDefault()).startsWith("windows")
        if (isWindows && !translator.windows) {
            translator.selectLocale("en_US")
        }
    }

    private fun initLayout() {
        screen = dvcwin(1600, 1200)
        statusBox.isEditable = false

        screen.addMouseListener(this)

        addFocusListener(this)
        screen.addFocusListener(this)
        screen.addKeyListener(this)

        focusTraversalKeysDisable(screen)
        focusTraversalKeysDisable(this)

        layout = BorderLayout()
        add("South", statusBox)
        add("North", screen)

        setStatus(1, "Offline")
        setStatus(2, "")
        setStatus(3, "")
        setStatus(4, "")
    }

    fun setLocale(paramString: String) {
        translator.selectLocale(paramString)
    }

    open fun enableDebug() {}

    open fun disableDebug() {}

    fun startRdp() {
        if (rdpProc == null) {
            val localRuntime = Runtime.getRuntime()

            val str1: String = when (tsType) {
                0 -> "mstsc"
                1 -> "vnc"
                else -> "type$tsType"
            }

            var str2 = Remcons.prop!!.getProperty("$str1.program")
            println("$str1 = $str2")

            if (str2 != null) {
                str2 = percentSub(str2)
                println("exec: $str2")

                try {
                    rdpProc = localRuntime.exec(str2)
                    transmit(byteArrayOf(TELNET_IAC, CMD_TS_STARTED))
                } catch (e: SecurityException) {
                    println("SecurityException: " + e.message + ":: Attempting to launch " + str2)
                } catch (e: IOException) {
                    println("IOException: " + e.message + ":: " + str2)
                }

                return
            }

            var i = false

            try {
                println("Executing mstsc. Port is $terminalServicesPort")
                rdpProc = localRuntime.exec("mstsc /f /console /v:$host:$terminalServicesPort")
                transmit(byteArrayOf(TELNET_IAC, CMD_TS_STARTED))
            } catch (e: SecurityException) {
                println("SecurityException: " + e.message + ":: Attempting to launch mstsc.")
            } catch (e: IOException) {
                println("IOException: " + e.message + ":: mstsc not found in system directory. Looking in \\Program Files\\Remote Desktop.")
                i = true
            }

            var arrayOfString: Array<String>
            if (i) {
                i = false
                arrayOfString =
                    arrayOf("\\Program Files\\Remote Desktop\\mstsc /f /console /v:$host:$terminalServicesPort")
                try {
                    rdpProc = localRuntime.exec(arrayOfString)
                    transmit(byteArrayOf(TELNET_IAC, CMD_TS_STARTED))
                } catch (e: SecurityException) {
                    println("SecurityException: " + e.message + ":: Attempting to launch mstsc.")
                } catch (e: IOException) {
                    println("IOException: " + e.message + ":: Unable to find mstsc. Verify that Terminal Services client is installed.")
                    i = true
                }
            }

            if (i) {
                arrayOfString = arrayOf("\\Program Files\\Terminal Services Client\\mstsc")

                try {
                    rdpProc = localRuntime.exec(arrayOfString)
                    transmit(byteArrayOf(TELNET_IAC, CMD_TS_STARTED))
                } catch (e: SecurityException) {
                    println("SecurityException: " + e.message + ":: Attempting to launch mstsc.")
                } catch (e: IOException) {
                    println("IOException: " + e.message + ":: Unable to find mstsc. Verify that Terminal Services client is installed.")
                }
            }
        }
    }

    override fun keyTyped(event: KeyEvent) {
        transmit(translateKey(event))
    }

    override fun keyPressed(event: KeyEvent) {
        transmit(translateSpecialKey(event))
    }

    override fun keyReleased(event: KeyEvent) {
        transmit(translateSpecialKeyRelease(event))
    }

    fun sendAutoAliveMsg() {
        transmit("\u001b[&")
    }

    @Synchronized
    override fun focusGained(paramFocusEvent: FocusEvent) {
        if (paramFocusEvent.component !== screen) {
            screen.requestFocus()
        }
    }

    @Synchronized
    override fun focusLost(paramFocusEvent: FocusEvent) {
        @Suppress("ControlFlowWithEmptyBody")
        if (paramFocusEvent.component === screen) {
            /* no-op */
        }
    }

    @Synchronized
    override fun mouseClicked(paramMouseEvent: MouseEvent) {
        super.requestFocus()
    }

    @Synchronized
    override fun mousePressed(paramMouseEvent: MouseEvent) {
    }

    @Synchronized
    override fun mouseReleased(paramMouseEvent: MouseEvent) {
    }

    @Synchronized
    override fun mouseEntered(paramMouseEvent: MouseEvent) {
    }

    @Synchronized
    override fun mouseExited(paramMouseEvent: MouseEvent) {
    }

    @Synchronized
    override fun addNotify() {
        super.addNotify()
    }

    @Synchronized
    fun setStatus(fieldIndex: Int, message: String?) {
        statusFields[fieldIndex] = message
        statusBox.text = statusFields[0] + " " + statusFields[1] + " " + statusFields[2] + " " + statusFields[3]
    }

    open fun reinitVars() {}

    fun setupDecryption(paramArrayOfByte: ByteArray?) {
        if (paramArrayOfByte == null)
            throw NullPointerException("setupDecryption(paramArrayOfByte: ByteArray?) is null")

        System.arraycopy(paramArrayOfByte, 0, decryptKey, 0, 16)

        rc4decrypter = RC4(paramArrayOfByte)
        encryptionEnabled = true
    }

    @Synchronized
    open fun connect(paramString1: String, paramString2: String?, paramInt1: Int, paramInt2: Int, paramInt3: Int) {
        enableTerminalServices = paramInt2 and 0x1 == 1
        tsType = paramInt2 shr 8

        if (paramInt3 != 0) {
            terminalServicesPort = paramInt3
        }

        if (connected == 0) {
            screen.start_updates()
            connected = 1
            host = paramString1
            login = paramString2.orEmpty() // Eh?
            port = paramInt1

            requestFocus()

            try {
                setStatus(1, "Connecting")

                socket = Socket(host, port)

                try {
                    socket!!.setSoLinger(true, 0)
                } catch (e: SocketException) {
                    println("telnet.connect() linger SocketException: $e")
                }

                dataInputStream = DataInputStream(socket!!.getInputStream())
                out = DataOutputStream(socket!!.getOutputStream())

                setStatus(1, "Online")

                receiver = Thread(this)
                receiver!!.name = "telnet_rcvr"
                receiver!!.start()

                transmit(login)
            } catch (e: SocketException) {
                println("telnet.connect() SocketException: $e")
                setErrorStatus(e.toString())
            } catch (e: UnknownHostException) {
                println("telnet.connect() UnknownHostException: $e")
                setErrorStatus(e.toString())
            } catch (e: IOException) {
                println("telnet.connect() IOException: $e")
                setErrorStatus(e.toString())
            }
        } else {
            requestFocus()
        }
    }

    private fun setErrorStatus(status: String) {
        setStatus(1, status)

        socket = null
        dataInputStream = null
        out = null
        receiver = null
        connected = 0
    }

    fun connect(paramString1: String, paramString2: String, paramInt1: Int, paramInt2: Int) {
        connect(paramString1, paramString2, port, paramInt1, paramInt2)
    }

    fun connect(paramString: String, paramInt1: Int, paramInt2: Int) {
        connect(paramString, login, port, paramInt1, paramInt2)
    }

    @Synchronized
    fun disconnect() {
        if (connected == 1) {
            screen.stop_updates()
            connected = 0

            if (receiver != null && receiver!!.isAlive) {
                receiver!!.interrupt()
            }

            receiver = null

            if (socket != null) {
                try {
                    println("Closing socket")
                    socket!!.close()
                } catch (localIOException: IOException) {
                    println("telnet.disconnect() IOException: $localIOException")
                    setStatus(1, localIOException.toString())
                }
            }

            socket = null
            dataInputStream = null
            out = null

            setStatus(1, "Offline")
            reinitVars()

            decryptionActive = false
        }
    }

    // TAKE EXTRA CARE TO CONVERT INTEGERS TO BYTES PROPERLY WHEN USING THIS
    @Synchronized
    open fun transmit(paramString: String) {
        if (out == null) {
            return
        }

        if (paramString.isNotEmpty()) {
            val arrayOfByte = ByteArray(paramString.length)

            for (i in paramString.indices) {
                arrayOfByte[i] = paramString[i].code.toByte()
            }

            transmit(arrayOfByte)
        }
    }

    @Synchronized
    open fun transmit(data: ByteArray) {
        if (out == null) {
            return
        }

        if (data.isNotEmpty()) {
            try {
                out!!.write(data, 0, data.size)
            } catch (localIOException: IOException) {
                println("telnet.transmit() IOException: $localIOException")
            }
        }
    }

    @Synchronized
    protected open fun translateKey(keyEvent: KeyEvent): String {
        val str: String = when (val c = keyEvent.keyChar) {
            '\n', '\r' -> if (keyEvent.isShiftDown) "\n" else "\r"
            '\t' -> ""
            11.toChar(), '\u000c' -> translator.translate(c)
            else -> translator.translate(c)
        }
        return str
    }

    @Synchronized
    protected open fun translateSpecialKey(paramKeyEvent: KeyEvent): String {
        var str = ""
        if (paramKeyEvent.keyCode == '\t'.code) {
            paramKeyEvent.consume()
            str = "\t"
        }

        return str
    }

    @Synchronized
    protected open fun translateSpecialKeyRelease(paramKeyEvent: KeyEvent): String {
        return ""
    }

    open fun processDvc(paramChar: Char): Boolean {
        return true
    }

    override fun run() {
        val i = 0
        var j = 0
        val k = 0
        val m = 0
        val arrayOfByte = ByteArray(1024)

        screen.show_text("Connecting")

        try {
            while (true) {
                if (rdpProc != null) {
                    try {
                        rdpProc!!.exitValue()
                        rdpProc!!.destroy()
                        rdpProc = null
                        transmit(byteArrayOf(TELNET_IAC, CMD_TS_STOPPED))
                    } catch (ignored: IllegalThreadStateException) {
                        /* no-op */
                    }
                }

                var n: Int
                try {
                    if (socket == null || dataInputStream == null) {
                        println("telnet.run() s or in is null")
                        break
                    }

                    socket!!.soTimeout = 1000
                    n = dataInputStream!!.read(arrayOfByte)
                } catch (e: InterruptedIOException) {
                    continue
                } catch (e: Exception) {
                    println("telnet.run().read Exception, class:" + e.javaClass + "  msg:" + e.message)
                    e.printStackTrace()

                    n = -1
                }

                if (n < 0) {
                    break
                }

                for (i1 in 0 until n) {
                    var c1 = Char(arrayOfByte[i1].toUShort())
                    c1 = (c1.code and 0xFF).toChar()

                    if (dvcMode) {
                        if (dvcEncryption) {
                            val c2 = (rc4decrypter!!.randomValue() and 0xFF).toChar()
                            c1 = (c1.code xor c2.code).toChar()
                            c1 = (c1.code and 0xFF).toChar()
                        }

                        dvcMode = processDvc(c1)

                        if (!dvcMode) {
                            println("DVC mode turned off")
                            setStatus(1, "DVC Mode off at run")
                        }
                    } else if (c1.code == 27) {
                        // this sequence has to happen before anything else - it gates the above if block
                        j = 1
                    } else if (j == 1 && c1 == '[') {
                        j = 2
                    } else if (j == 2 && c1 == 'R') {
                        dvcMode = true
                        dvcEncryption = true

                        setStatus(1, "DVC Mode (RC4-128 bit)")
                    } else if (j == 2 && c1 == 'r') {
                        dvcMode = true
                        dvcEncryption = false

                        setStatus(1, "DVC Mode (no encryption)")
                    } else {
                        j = 0
                    }
                }
            }
        } catch (e: Exception) {
            println("telnet.run() Exception, class:" + e.javaClass + "  msg:" + e.message)
            e.printStackTrace()
        } finally {
            if (!seized) {
                screen.show_text("Offline")
                setStatus(1, "Offline")
                setStatus(2, "")
                setStatus(3, "")
                setStatus(4, "")
                disconnect()
            }
        }
    }

    open fun changeKey() {
        try {
            rc4decrypter!!.update_key()
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
        }
    }

    private fun focusTraversalKeysDisable(paramObject: Component) {
        paramObject.focusTraversalKeysEnabled = false

        if (paramObject is Container)
            paramObject.isFocusCycleRoot = true
    }

    fun stopRdp() {
        if (rdpProc != null) {
            try {
                rdpProc!!.exitValue()
            } catch (e: IllegalThreadStateException) {
                println("IllegalThreadStateException thrown. Destroying TS.")

                rdpProc!!.destroy()
            }

            rdpProc = null
            transmit(byteArrayOf(TELNET_IAC, CMD_TS_STOPPED))
        }

        println("TS stop.")
    }

    fun seize() {
        seized = true
        screen.show_text("Session Acquired by another user.")
        setStatus(1, "Offline")
        setStatus(2, "")
        setStatus(3, "")
        setStatus(4, "")
        disconnect()
    }

    private fun percentSub(paramString: String): String {
        val builder = StringBuilder()
        var i = 0

        while (i < paramString.length) {
            var c = paramString[i]
            if (c == '%') {
                c = paramString[++i]
                when (c) {
                    'h' -> builder.append(host)
                    'p' -> builder.append(terminalServicesPort)
                    else -> builder.append(c)
                }
            } else {
                builder.append(c)
            }

            i++
        }

        return builder.toString()
    }
}
