package com.hp.ilo2.remcons

import java.awt.Cursor
import java.awt.Image
import java.awt.Point
import java.awt.Toolkit
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.awt.image.MemoryImageSource
import java.io.IOException
import java.lang.Exception
import java.lang.StringBuilder
import java.security.NoSuchAlgorithmException
import kotlin.jvm.Synchronized

/**
 * Common/Computer Information Model
 */
class Cim : telnet(), MouseSyncListener {

    private val colorRemapTable = IntArray(0x1000)
    private val encryptKey = ByteArray(16)
    private val mouseSync = MouseSync(this)
    private var altlock = false
    private var currentCursor: Cursor
    private var disableKbd = false
    private var encryptionActive = false
    private var ignoreNextKey = false
    private var keyIndex1 = 0
    private var mouseProtocol = 0
    private var rc4encrypter: RC4? = null
    private var scaleX = 1
    private var scaleY = 1
    private var screenX = 1
    private var screenY = 1
    private var sendingEncryptCommand = false
    var uiDirty = false

    init {
        dvc_reversal[0xff] = 0
        currentCursor = Cursor.getDefaultCursor()
        screen.addMouseListener(mouseSync)
        screen.addMouseMotionListener(mouseSync)
        mouseSync.setListener(this)
    }

    fun setupEncryption(key: ByteArray?, keyIndex: Int) {
        System.arraycopy(key!!, 0, encryptKey, 0, 16)
        rc4encrypter = RC4(key)
        keyIndex1 = keyIndex
    }

    override fun reinit_vars() {
        super.reinit_vars()
        disableKbd = false
        altlock = false
        dvc_reversal[0xff] = 0
        scaleX = 1
        scaleY = 1
        mouseSync.restart()
        dvc_process_inhibit = false
    }

    override fun enable_debug() {
        debug_msgs = true
        super.enable_debug()
        mouseSync.enableDebug()
    }

    override fun disable_debug() {
        debug_msgs = false
        super.disable_debug()
        mouseSync.disableDebug()
    }

    fun syncStart() {
        mouseSync.sync()
    }

    // clientX and clientY are probably just 16 bit
    override fun serverMove(paramInt1: Int, paramInt2: Int, clientX: Int, clientY: Int) {
        var paramInt1 = paramInt1
        var paramInt2 = paramInt2
        var clientX = clientX
        var clientY = clientY

        if (paramInt1 < -128) {
            paramInt1 = -128
        } else if (paramInt1 > 127) {
            paramInt1 = 127
        }

        if (paramInt2 < -128) {
            paramInt2 = -128
        } else if (paramInt2 > 127) {
            paramInt2 = 127
        }

        uiDirty = true

        if (screenX > 0 && screenY > 0) {
            clientX = 3000 * clientX / screenX
            clientY = 3000 * clientY / screenY
        } else {
            clientX *= 3000
            clientY *= 3000
        }

        val c1 = (clientX / 256).toByte()
        val c2 = (clientX % 256).toByte()
        val c3 = (clientY / 256).toByte()
        val c4 = (clientY % 256).toByte()

        if (mouseProtocol == 0) {
            transmit(byteArrayOf(TELNET_IAC, CMD_MOUSE_MOVE, paramInt1.toByte(), paramInt2.toByte()))
        } else {
            transmit(byteArrayOf(TELNET_IAC, CMD_MOUSE_MOVE, paramInt1.toByte(), paramInt2.toByte(), c1, c2, c3, c4))
        }
    }

    fun mouseModeChange(absolute: Boolean) {
        val mode = (if (absolute) MOUSE_USBABS else MOUSE_USBREL).code.toByte()
        transmit(byteArrayOf(TELNET_IAC, CMD_SET_MODE, mode))
    }

    override fun mouseEntered(event: MouseEvent) {
        uiDirty = true
        cursor = currentCursor
        super.mouseEntered(event)
    }

    override fun serverPress(button: Int) {
        uiDirty = true
        sendMousePress(button)
    }

    override fun serverRelease(button: Int) {
        uiDirty = true
        sendMouseRelease(button)
    }

    override fun serverClick(button: Int, paramInt2: Int) {
        uiDirty = true
        sendMouseClick(button, paramInt2)
    }

    @Synchronized
    override fun mouseExited(paramMouseEvent: MouseEvent) {
        super.mouseExited(paramMouseEvent)
        cursor = Cursor.getDefaultCursor()
    }

    fun disableKeyboard() {
        disableKbd = true
    }

    fun enableKeyboard() {
        disableKbd = false
    }

    fun disableAltlock() {
        altlock = false
    }

    fun enableAltlock() {
        altlock = true
    }

    @Synchronized
    override fun connect(paramString1: String?, paramString2: String?, paramInt1: Int, paramInt2: Int, paramInt3: Int) {
        var paramString2 = paramString2
        val arrayOfChar = charArrayOf('ÿ', 'À')

        if (encryption_enabled) {
            encryptionActive = true
            paramString2 = "" + arrayOfChar[0] + "" + arrayOfChar[1] + "    " + paramString2
            sendingEncryptCommand = true
        }

        super.connect(paramString1, paramString2, paramInt1, paramInt2, paramInt3)
    }

    @Synchronized
    override fun transmit(data: String) {
        if (out == null) {
            return
        }

        if (data.isEmpty()) {
            return
        }

        val arrayOfByte = ByteArray(data.length)
        if (encryptionActive) {
            if (sendingEncryptCommand) {
                arrayOfByte[0] = data[0].code.toByte()
                arrayOfByte[1] = data[1].code.toByte()
                arrayOfByte[2] = (keyIndex1 and -0x1000000 ushr 24).toByte()
                arrayOfByte[3] = (keyIndex1 and 0xFF0000 ushr 16).toByte()
                arrayOfByte[4] = (keyIndex1 and 0xFF00 ushr 8).toByte()
                arrayOfByte[5] = (keyIndex1 and 0xFF ushr 0).toByte()

                for (i in 6 until data.length) {
                    arrayOfByte[i] = (data[i].code xor rc4encrypter!!.randomValue()).toByte()
                }

                sendingEncryptCommand = false
            } else {
                for (i in data.indices) {
                    arrayOfByte[i] = (data[i].code xor rc4encrypter!!.randomValue()).toByte()
                }
            }
        } else {
            for (i in data.indices) {
                arrayOfByte[i] = data[i].code.toByte()
            }
        }

        try {
            out.write(arrayOfByte, 0, arrayOfByte.size)
        } catch (ignored: IOException) {
        }
    }

    @Synchronized
    override fun transmit(data: ByteArray) {
        if (out == null) {
            return
        }

        if (data.isEmpty()) {
            return
        }

        val arrayOfByte = ByteArray(data.size)
        if (encryptionActive) {
            if (sendingEncryptCommand) {
                arrayOfByte[0] = data[0]
                arrayOfByte[1] = data[1]
                arrayOfByte[2] = (keyIndex1 and -0x1000000 ushr 24).toByte()
                arrayOfByte[3] = (keyIndex1 and 0xFF0000 ushr 16).toByte()
                arrayOfByte[4] = (keyIndex1 and 0xFF00 ushr 8).toByte()
                arrayOfByte[5] = (keyIndex1 and 0xFF ushr 0).toByte()
                for (i in 6 until data.size) {
                    arrayOfByte[i] = (data[i].toInt() xor rc4encrypter!!.randomValue()).toByte()
                }
                sendingEncryptCommand = false
            } else {
                for (i in data.indices) {
                    arrayOfByte[i] = (data[i].toInt() xor rc4encrypter!!.randomValue()).toByte()
                }
            }
        } else {
            System.arraycopy(data, 0, arrayOfByte, 0, data.size)
        }
        try {
            out.write(arrayOfByte, 0, arrayOfByte.size)
        } catch (ignored: IOException) {
        }
    }

    override fun translate_key(keyEvent: KeyEvent): String {
        var str = ""
        val i = keyEvent.keyChar
        var j = 0
        var k = 1

        if (disableKbd) {
            return ""
        }

        if (ignoreNextKey) {
            ignoreNextKey = false
            return ""
        }

        uiDirty = true

        if (keyEvent.isShiftDown) {
            j = 1
        } else if (keyEvent.isControlDown) {
            j = 2
        } else if (altlock || keyEvent.isAltDown) {
            j = 3
            if (keyEvent.isAltDown) {
                keyEvent.consume()
            }
        }

        when (i.code) {
            0x1b -> k = 0
            0xa,
            0xd -> {
                when (j) {
                    0 -> str = "\r"
                    1 -> str = "\u001b[3\r"
                    2 -> str = "\n"
                    3 -> str = "\u001b[1\r"
                }
                k = 0
            }
            8 -> {
                when (j) {
                    0 -> str = "\b"
                    1 -> str = "\u001b[3\b"
                    2 -> str = ""
                    3 -> str = "\u001b[1\b"
                }
                k = 0
            }
            else -> str = super.translate_key(keyEvent)
        }

        if (k == 1 && str.isNotEmpty() && j == 3) {
            str = "\u001b[1$str"
        }

        return str
    }

    override fun translate_special_key(paramKeyEvent: KeyEvent): String {
        var str = ""
        var i = 1
        var j = 0

        if (disableKbd) {
            return ""
        }

        uiDirty = true

        if (paramKeyEvent.isShiftDown) {
            j = 1
        } else if (paramKeyEvent.isControlDown) {
            j = 2
        } else if (altlock || paramKeyEvent.isAltDown) {
            j = 3
        }

        when (paramKeyEvent.keyCode) {
            KeyEvent.VK_ESCAPE -> str = "\u001b"
            KeyEvent.VK_TAB -> {
                paramKeyEvent.consume()
                str = "\t"
            }
            KeyEvent.VK_DELETE -> {
                if (paramKeyEvent.isControlDown && (altlock || paramKeyEvent.isAltDown)) {
                    sendCtrlAltDel()
                    return ""
                }
                if (System.getProperty("java.version", "0") < "1.4.2") {
                    str = ""
                }
            }
            36 -> str = "\u001b[H"
            35 -> str = "\u001b[F"
            33 -> str = "\u001b[I"
            34 -> str = "\u001b[G"
            155 -> str = "\u001b[L"
            38 -> str = "\u001b[A"
            40 -> str = "\u001b[B"
            37 -> str = "\u001b[D"
            39 -> str = "\u001b[C"
            112 -> {
                when (j) {
                    0 -> str = "\u001b[M"
                    1 -> str = "\u001b[Y"
                    2 -> str = "\u001b[k"
                    3 -> str = "\u001b[w"
                }
                paramKeyEvent.consume()
                i = 0
            }
            113 -> {
                when (j) {
                    0 -> str = "\u001b[N"
                    1 -> str = "\u001b[Z"
                    2 -> str = "\u001b[l"
                    3 -> str = "\u001b[x"
                }
                paramKeyEvent.consume()
                i = 0
            }
            114 -> {
                when (j) {
                    0 -> str = "\u001b[O"
                    1 -> str = "\u001b[a"
                    2 -> str = "\u001b[m"
                    3 -> str = "\u001b[y"
                }
                paramKeyEvent.consume()
                i = 0
            }
            115 -> {
                when (j) {
                    0 -> str = "\u001b[P"
                    1 -> str = "\u001b[b"
                    2 -> str = "\u001b[n"
                    3 -> str = "\u001b[z"
                }
                paramKeyEvent.consume()
                i = 0
            }
            116 -> {
                when (j) {
                    0 -> str = "\u001b[Q"
                    1 -> str = "\u001b[c"
                    2 -> str = "\u001b[o"
                    3 -> str = "\u001b[@"
                }
                paramKeyEvent.consume()
                i = 0
            }
            117 -> {
                when (j) {
                    0 -> str = "\u001b[R"
                    1 -> str = "\u001b[d"
                    2 -> str = "\u001b[p"
                    3 -> str = "\u001b[["
                }
                paramKeyEvent.consume()
                i = 0
            }
            118 -> {
                when (j) {
                    0 -> str = "\u001b[S"
                    1 -> str = "\u001b[e"
                    2 -> str = "\u001b[q"
                    3 -> str = "\u001b[\\"
                }
                paramKeyEvent.consume()
                i = 0
            }
            119 -> {
                when (j) {
                    0 -> str = "\u001b[T"
                    1 -> str = "\u001b[f"
                    2 -> str = "\u001b[r"
                    3 -> str = "\u001b[]"
                }
                paramKeyEvent.consume()
                i = 0
            }
            120 -> {
                when (j) {
                    0 -> str = "\u001b[U"
                    1 -> str = "\u001b[g"
                    2 -> str = "\u001b[s"
                    3 -> str = "\u001b[^"
                }
                paramKeyEvent.consume()
                i = 0
            }
            121 -> {
                when (j) {
                    0 -> str = "\u001b[V"
                    1 -> str = "\u001b[h"
                    2 -> str = "\u001b[t"
                    3 -> str = "\u001b[_"
                }
                paramKeyEvent.consume()
                i = 0
            }
            122 -> {
                when (j) {
                    0 -> str = "\u001b[W"
                    1 -> str = "\u001b[i"
                    2 -> str = "\u001b[u"
                    3 -> str = "\u001b[`"
                }
                paramKeyEvent.consume()
                i = 0
            }
            123 -> {
                when (j) {
                    0 -> str = "\u001b[X"
                    1 -> str = "\u001b[j"
                    2 -> str = "\u001b[v"
                    3 -> str = "\u001b['"
                }
                paramKeyEvent.consume()
                i = 0
            }
            else -> {
                i = 0
                str = super.translate_special_key(paramKeyEvent)
            }
        }

        if (str.isNotEmpty()) {
            if (i == 1) {
                when (j) {
                    1 -> str = "\u001b[3$str"
                    2 -> str = "\u001b[2$str"
                    3 -> str = "\u001b[1$str"
                }
            }
        }

        return str
    }

    override fun translate_special_key_release(paramKeyEvent: KeyEvent): String {
        val str: String
        var i = 0

        if (paramKeyEvent.isShiftDown) {
            i = 1
        }

        if (altlock || paramKeyEvent.isAltDown) {
            i += 2
        }

        if (paramKeyEvent.isControlDown) {
            i += 4
        }

        when (paramKeyEvent.keyCode) {
            243,
            244,
            263 -> i += 128
            29 -> i += 136
            28,
            256,
            257 -> i += 144
            241,
            242,
            245 -> i += 152
        }

        str = if (i > 127) {
            "" + i.toChar()
        } else {
            ""
        }

        return str
    }

    fun sendCtrlAltDel() {
        transmit("\u001b[2\u001b[")
    }

    private fun sendMousePress(paramInt: Int) {
        transmit(byteArrayOf(TELNET_IAC, CMD_BUTTON_PRESS, paramInt.toByte()))
    }

    private fun sendMouseRelease(paramInt: Int) {
        transmit(byteArrayOf(TELNET_IAC, CMD_BUTTON_RELEASE, paramInt.toByte()))
    }

    private fun sendMouseClick(paramInt1: Int, paramInt2: Int) {
        transmit(byteArrayOf(TELNET_IAC, CMD_BUTTON_CLICK, paramInt1.toByte(), paramInt2.toByte()))
    }

    fun sendMouseByte(paramInt: Int) {
        transmit(byteArrayOf(TELNET_IAC, CMD_BYTE, paramInt.toByte()))
    }

    fun refreshScreen() {
        transmit("\u001b[~")
    }

    fun sendKeepAliveMsg() {
        transmit("\u001b[(")
    }

    @Synchronized
    private fun setFramerate(rate: Int) {
        framerate = rate
        screen.set_framerate(rate)
        set_status(3, "" + framerate)
    }

    private fun showError(message: String) {
        println("dvc:$message: state $dvc_decoder_state code $dvc_code")
        println("dvc:error at byte count $count_bytes")
    }

    private fun cacheReset() {
        dvc_cc_active = 0
    }

    private fun cacheLru(paramInt: Int): Int {
        var k = dvc_cc_active
        var j = 0
        var n = 0

        for (i in 0 until k) {
            if (paramInt == dvc_cc_color[i]) {
                j = i
                n = 1
                break
            }
            if (dvc_cc_usage[i] == k - 1) {
                j = i
            }
        }

        var m = dvc_cc_usage[j]
        if (n == 0) {
            if (k < 17) {
                j = k
                m = k
                k++

                dvc_cc_active = k
                dvc_pixcode = if (dvc_cc_active < 2) {
                    38
                } else if (dvc_cc_active == 2) {
                    4
                } else if (dvc_cc_active == 3) {
                    5
                } else if (dvc_cc_active < 6) {
                    6
                } else if (dvc_cc_active < 10) {
                    7
                } else {
                    32
                }

                next_1[31] = dvc_pixcode
            }

            dvc_cc_color[j] = paramInt
        }

        dvc_cc_block[j] = 1

        for (i in 0 until k) {
            if (dvc_cc_usage[i] < m) {
                dvc_cc_usage[i] += 1
            }
        }

        dvc_cc_usage[j] = 0

        return n
    }

    private fun cacheFind(paramInt: Int): Int {
        val i = dvc_cc_active
        var j = 0

        while (j < i) {
            if (paramInt == dvc_cc_usage[j]) {
                val m = dvc_cc_color[j]
                val k = j
                j = 0

                while (j < i) {
                    if (dvc_cc_usage[j] < paramInt) {
                        dvc_cc_usage[j] += 1
                    }
                    j++
                }

                dvc_cc_usage[k] = 0
                dvc_cc_block[k] = 1

                return m
            }

            j++
        }

        return -1
    }

    private fun cachePrune() {
        var j = dvc_cc_active
        var i = 0

        while (i < j) {
            val k = dvc_cc_block[i]
            if (k == 0) {
                j--
                dvc_cc_block[i] = dvc_cc_block[j]
                dvc_cc_color[i] = dvc_cc_color[j]
                dvc_cc_usage[i] = dvc_cc_usage[j]
            } else {
                dvc_cc_block[i] -= 1
                i++
            }
        }

        dvc_cc_active = j
        dvc_pixcode = if (dvc_cc_active < 2) {
            38
        } else if (dvc_cc_active == 2) {
            4
        } else if (dvc_cc_active == 3) {
            5
        } else if (dvc_cc_active < 6) {
            6
        } else if (dvc_cc_active < 10) {
            7
        } else {
            32
        }

        next_1[31] = dvc_pixcode
    }

    private fun nextBlock(paramInt: Int) {
        var paramInt = paramInt
        var k = 1

        if (!video_detected) {
            k = 0
        }

        if (dvc_pixel_count != 0) {
            if (dvc_y_clipped > 0 && dvc_lasty == dvc_size_y) {
                val m = colorRemapTable[0]
                for (j in dvc_y_clipped..255) {
                    block[j] = m
                }
            }
        }

        dvc_pixel_count = 0
        dvc_next_state = 1
        var i = dvc_lastx * 16
        val j = dvc_lasty * 16

        while (paramInt != 0) {
            if (k != 0) {
                screen.paste_array(block, i, j, 16)
            }

            dvc_lastx += 1
            i += 16

            if (dvc_lastx >= dvc_size_x) break

            paramInt--
        }
    }

    private fun initReversal() {
        for (i in 0..0xff) {
            var i1 = 8
            var n = 8
            var k = i
            var m = 0

            for (j in 0..7) {
                m = m shl 1
                if (k and 0x1 == 1) {
                    if (i1 > j) i1 = j
                    m = m or 0x1
                    n = 7 - j
                }
                k = k shr 1
            }

            dvc_reversal[i] = m
            dvc_right[i] = i1
            dvc_left[i] = n
        }
    }

    private fun addBits(paramChar: Char): Int {
        dvc_zero_count += dvc_right[paramChar.code]
        val i = paramChar.code
        dvc_ib_acc = dvc_ib_acc or (i shl dvc_ib_bcnt)
        dvc_ib_bcnt += 8

        if (dvc_zero_count > 30) {
            if (debug_msgs) {
                if (dvc_decoder_state == 38 && fatal_count < 40 && fatal_count > 0) {
                    println("reset caused a false alarm")
                } else {
                    println("Reset sequence detected at $count_bytes")
                }
            }
            dvc_next_state = 43
            dvc_decoder_state = 43
            return 4
        }

        if (paramChar.code != 0) {
            dvc_zero_count = dvc_left[paramChar.code]
        }

        return 0
    }

    private fun getBits(paramInt: Int): Int {
        if (paramInt == 1) {
            dvc_code = dvc_ib_acc and 0x1
            dvc_ib_acc = dvc_ib_acc shr 1
            dvc_ib_bcnt -= 1

            return 0
        }

        if (paramInt == 0) {
            return 0
        }

        var i = dvc_ib_acc and dvc_getmask[paramInt]
        dvc_ib_bcnt -= paramInt
        dvc_ib_acc = dvc_ib_acc shr paramInt
        i = dvc_reversal[i]
        i = i shr 8 - paramInt
        dvc_code = i

        return 0
    }

    private fun processBits(paramChar: Char): Int {
        var m = 0
        addBits(paramChar)
        dvc_new_bits = paramChar
        count_bytes += 1L
        var k: Int

        label2353@ while (m == 0) {
            k = bits_to_read[dvc_decoder_state]
            if (k > dvc_ib_bcnt) {
                m = 0
                break
            }

            var i = getBits(k)

            dvc_next_state = if (dvc_code == 0) {
                next_0[dvc_decoder_state]
            } else {
                next_1[dvc_decoder_state]
            }

            var j: Int

            when (dvc_decoder_state) {
                3,
                4,
                5,
                6,
                7,
                32 -> {
                    if (dvc_cc_active == 1) {
                        dvc_code = dvc_cc_usage[0]
                    } else if (dvc_decoder_state == 4) {
                        dvc_code = 0
                    } else if (dvc_decoder_state == 3) {
                        dvc_code = 1
                    } else if (dvc_code != 0) {
                        dvc_code += 1
                    }

                    dvc_color = cacheFind(dvc_code)

                    if (dvc_color == -1) {
                        showError("could not find color for LRU $dvc_code, cache has $dvc_cc_active colors")
                        dvc_next_state = 38
                    } else {
                        dvc_last_color = colorRemapTable[dvc_color]

                        if (dvc_pixel_count < 256) {
                            block[dvc_pixel_count] = dvc_last_color
                        } else {
                            println("dvc:too many block0")
                            dvc_next_state = 38

                            break@label2353
                        }

                        dvc_pixel_count += 1
                    }
                }
                12 -> {
                    if (dvc_code == 7) {
                        dvc_next_state = 14
                    } else if (dvc_code == 6) {
                        dvc_next_state = 13
                    } else {
                        dvc_code += 2

                        j = 0

                        while (j < dvc_code) {
                            if (dvc_pixel_count < 256) {
                                block[dvc_pixel_count] = dvc_last_color
                            } else {
                                showError("too many pixels in a block2")
                                dvc_next_state = 38
                                break
                            }

                            dvc_pixel_count += 1
                            j++
                        }
                    }
                }
                13 -> {
                    dvc_code += 8

                    if (dvc_decoder_state == 14 && dvc_code < 16) {
                        if (debug_msgs) {
                            println("dvc:non-std repeat misused")
                        }
                    }

                    j = 0

                    while (j < dvc_code) {
                        if (dvc_pixel_count < 256) {
                            block[dvc_pixel_count] = dvc_last_color
                        } else {
                            showError("too many pixels in a block3")
                            dvc_next_state = 38
                            break
                        }

                        dvc_pixel_count += 1
                        j++
                    }
                }
                14 -> {
                    if (dvc_decoder_state == 14 && dvc_code < 16) {
                        if (debug_msgs) {
                            println("dvc:non-std repeat misused")
                        }
                    }

                    j = 0

                    while (j < dvc_code) {
                        if (dvc_pixel_count < 256) {
                            block[dvc_pixel_count] = dvc_last_color
                        } else {
                            showError("too many pixels in a block3")
                            dvc_next_state = 38
                            break
                        }

                        dvc_pixel_count += 1
                        j++
                    }
                }
                33 -> {
                    if (dvc_pixel_count < 256) {
                        block[dvc_pixel_count] = dvc_last_color
                    } else {
                        showError("too many pixels in a block4")
                        dvc_next_state = 38

                        break@label2353
                    }

                    dvc_pixel_count += 1
                }
                1,
                2,
                10,
                11,
                22,
                28,
                31,
                36 -> {
                }
                35 -> dvc_next_state = dvc_pixcode
                9 -> dvc_red = dvc_code shl 8
                41 -> dvc_green = dvc_code shl 4
                8 -> {
                    dvc_red = dvc_code shl 8
                    dvc_green = dvc_code shl 4
                    dvc_blue = dvc_code
                    dvc_color = dvc_red or dvc_green or dvc_blue
                    i = cacheLru(dvc_color)

                    if (i != 0) {
                        if (debug_msgs) {
                            if (count_bytes > 6L) {
                                showError("unexpected hit: color " + intToHex4(dvc_color))
                            } else {
                                showError("possible reset underway: color " + intToHex4(dvc_color))
                            }
                        }

                        dvc_next_state = 38
                    } else {
                        dvc_last_color = colorRemapTable[dvc_color]

                        if (dvc_pixel_count < 256) {
                            block[dvc_pixel_count] = dvc_last_color
                        } else {
                            println("dvc:too many block1")
                            dvc_next_state = 38
                            break@label2353
                        }

                        dvc_pixel_count += 1
                    }
                }
                42 -> {
                    dvc_blue = dvc_code
                    dvc_color = dvc_red or dvc_green or dvc_blue
                    i = cacheLru(dvc_color)

                    if (i != 0) {
                        if (debug_msgs) {
                            if (count_bytes > 6L) {
                                showError("unexpected hit: color " + intToHex4(dvc_color))
                            } else {
                                showError("possible reset underway: color " + intToHex4(dvc_color))
                            }
                        }

                        dvc_next_state = 38
                    } else {
                        dvc_last_color = colorRemapTable[dvc_color]

                        if (dvc_pixel_count < 256) {
                            block[dvc_pixel_count] = dvc_last_color
                        } else {
                            println("dvc:too many block1")
                            dvc_next_state = 38
                            break@label2353
                        }

                        dvc_pixel_count += 1
                    }
                }
                17,
                26 -> {
                    dvc_newx = dvc_code

                    if (dvc_decoder_state == 17 && dvc_newx > dvc_size_x) {
                        if (debug_msgs) {
                            print("dvc:movexy moves x beyond screen $dvc_newx")
                            println(" byte count $count_bytes")
                        }

                        dvc_newx = 0
                    }
                }
                39 -> {
                    dvc_newy = dvc_code and 0x7F
                    dvc_lastx = dvc_newx
                    dvc_lasty = dvc_newy

                    if (dvc_lasty > dvc_size_y) {
                        if (debug_msgs) {
                            print("dvc:movexy moves y beyond screen $dvc_lasty")
                            println(" byte count $count_bytes")
                        }
                        dvc_lasty = 0
                    }

                    screen.repaint_it(true)
                }
                20 -> {
                    dvc_code += dvc_lastx + 1

                    if (dvc_code > dvc_size_x) {
                        if (debug_msgs) {
                            print("dvc:short x moves beyond screen $dvc_code lastx $dvc_lastx")
                            println(" byte count $count_bytes")
                        }
                    }

                    dvc_lastx = dvc_code and 0x7F

                    if (dvc_lastx > dvc_size_x) {
                        if (debug_msgs) {
                            print("dvc:long x moves beyond screen $dvc_lastx")
                            println(" byte count $count_bytes")
                        }

                        dvc_lastx = 0
                    }
                }
                21 -> {
                    dvc_lastx = dvc_code and 0x7F

                    if (dvc_lastx > dvc_size_x) {
                        if (debug_msgs) {
                            print("dvc:long x moves beyond screen $dvc_lastx")
                            println(" byte count $count_bytes")
                        }

                        dvc_lastx = 0
                    }
                }
                27 -> {
                    if (timeout_count == count_bytes - 1L) {
                        showError("double timeout at " + count_bytes + ", remaining bits " + (dvc_ib_bcnt and 0x7))
                        dvc_next_state = 38
                    }

                    if (dvc_ib_bcnt and 0x7 != 0) getBits(dvc_ib_bcnt and 0x7)

                    timeout_count = count_bytes
                    screen.repaint_it(true)
                }
                24 -> {
                    if (cmd_p_count != 0) cmd_p_buff[cmd_p_count - 1] = cmd_last

                    cmd_p_count += 1
                    cmd_last = dvc_code
                }
                46 -> {
                    if (dvc_code == 0) {
                        when (cmd_last) {
                            1 -> dvc_next_state = 37
                            2 -> dvc_next_state = 44
                            3 -> {
                                if (cmd_p_count != 0) {
                                    setFramerate(cmd_p_buff[0])
                                } else {
                                    setFramerate(0)
                                }
                            }
                            4,
                            5 -> {
                            }
                            6 -> {
                                screen.show_text("Video suspended")
                                set_status(2, "Video_suspended")
                                screenX = 640
                                screenY = 100
                            }
                            7 -> {
                                ts_type = cmd_p_buff[0]
                                startRdp()
                            }
                            8 -> stop_rdp()
                            9 -> {
                                if (dvc_ib_bcnt and 0x7 != 0) {
                                    getBits(dvc_ib_bcnt and 0x7)
                                }
                                change_key()
                            }
                            10 -> seize()
                            else -> println("dvc: unknown firmware command $cmd_last")
                        }

                        cmd_p_count = 0
                    }
                }
                44 -> {
                    printchan = dvc_code
                    printstring = ""
                }
                45 -> {
                    if (dvc_code != 0) {
                        printstring += dvc_code.toChar()
                    } else {
                        when (printchan) {
                            1, 2 -> set_status(2 + printchan, printstring)
                            3 -> println(printstring)
                            4 -> screen.show_text(printstring)
                        }
                        dvc_next_state = 1
                    }
                }
                15,
                16,
                18,
                19,
                23,
                25 -> {
                }
                0 -> {
                    cacheReset()
                    dvc_pixel_count = 0
                    dvc_lastx = 0
                    dvc_lasty = 0
                    dvc_red = 0
                    dvc_green = 0
                    dvc_blue = 0
                    fatal_count = 0
                    timeout_count = -1L
                    cmd_p_count = 0
                }
                38 -> {
                    if (fatal_count == 0) {
                        debug_lastx = dvc_lastx
                        debug_lasty = dvc_lasty
                        debug_show_block = 1
                    }

                    if (fatal_count == 40) {
                        print("Latched: byte count $count_bytes")
                        println(" current block at $dvc_lastx $dvc_lasty")
                    }

                    if (fatal_count == 11680) {
                        refreshScreen()
                    }

                    fatal_count += 1
                    if (fatal_count == 120000) {
                        println("Requesting refresh1")
                        refreshScreen()
                    }

                    if (fatal_count == 12000000) {
                        println("Requesting refresh2")
                        refreshScreen()
                        fatal_count = 41
                    }
                }
                34 -> nextBlock(1)
                29 -> {
                    dvc_code += 2
                    nextBlock(dvc_code)
                }
                30 -> nextBlock(dvc_code)
                40 -> {
                    dvc_size_x = dvc_newx
                    dvc_size_y = dvc_code
                }
                47 -> {
                    dvc_lastx = 0
                    dvc_lasty = 0
                    dvc_pixel_count = 0
                    cacheReset()
                    scaleX = 1
                    scaleY = 1
                    screenX = dvc_size_x * 16
                    screenY = dvc_size_y * 16 + dvc_code
                    video_detected = !(screenX == 0 || screenY == 0)
                    dvc_y_clipped = if (dvc_code > 0) 256 - 16 * dvc_code else 0

                    if (!video_detected) {
                        screen.show_text("No Video")
                        set_status(2, "No Video")
                        screenX = 640
                        screenY = 100
                    } else {
                        screen.set_abs_dimensions(screenX, screenY)
                        mouseSync.serverScreen(screenX, screenY)
                        set_status(2, " Video:" + screenX + "x" + screenY)
                    }
                }
                43 -> {
                    if (dvc_next_state != dvc_decoder_state) {
                        dvc_ib_bcnt = 0
                        dvc_ib_acc = 0
                        dvc_zero_count = 0
                        count_bytes = 0L
                    }
                }
                37 -> return 1
            }

            if (dvc_next_state == 2 && dvc_pixel_count == 256) {
                nextBlock(1)
                cachePrune()
            }

            if (dvc_decoder_state == dvc_next_state && dvc_decoder_state != 45 && dvc_decoder_state != 38 && dvc_decoder_state != 43) {
                println("Machine hung in state $dvc_decoder_state")
                m = 6
            } else {
                dvc_decoder_state = dvc_next_state
            }
        }

        return m
    }

    public override fun process_dvc(paramChar: Char): Boolean {
        if (dvc_reversal[0xff] == 0) {
            println(" Version 20050808154652 ")
            initReversal()
            cacheReset()
            dvc_decoder_state = 0
            dvc_next_state = 0
            dvc_zero_count = 0
            dvc_ib_acc = 0
            dvc_ib_bcnt = 0
            for (j in 0..4095) {
                colorRemapTable[j] = (j and 0xF00) * 0x1100 + (j and 0xF0) * 0x110 + (j and 0xF) * 0x11
            }
        }

        val i: Int = if (!dvc_process_inhibit) {
            processBits(paramChar)
        } else 0

        val bool: Boolean
        if (i == 0) {
            bool = true
        } else {
            println("Exit from DVC mode status =$i")
            println("Current block at $dvc_lastx $dvc_lasty")
            println("Byte count $count_bytes")
            bool = true
            dvc_decoder_state = LATCHED
            dvc_next_state = LATCHED
            fatal_count = 0
            refreshScreen()
        }

        return bool
    }

    fun setSigColors(paramArrayOfInt: IntArray?) {}

    override fun change_key() {
        try {
            rc4encrypter!!.update_key()
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
        }

        super.change_key()
    }

    fun setMouseProtocol(paramInt: Int) {
        mouseProtocol = paramInt
    }

    @Suppress("SameParameterValue")
    private fun customCursor(paramImage: Image, paramPoint: Point, paramString: String): Cursor? {
        var cursor: Cursor? = null

        try {
            val localClass = Toolkit::class.java
            val localMethod =
                localClass.getMethod("createCustomCursor", Image::class.java, Point::class.java, String::class.java)
            val localToolkit = Toolkit.getDefaultToolkit()
            cursor = localMethod.invoke(localToolkit, paramImage, paramPoint, paramString) as Cursor
        } catch (e: Exception) {
            println("This JVM cannot create custom cursors")
        }

        return cursor
    }

    private fun createCursor(cursorIndex: Int): Cursor {
        val javaVersion = System.getProperty("java.version", "0")
        val localToolkit = Toolkit.getDefaultToolkit()
        val localImage: Image
        val arrayOfInt: IntArray
        val localMemoryImageSource: MemoryImageSource

        when (cursorIndex) {
            0 -> return Cursor.getDefaultCursor()
            1 -> return Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR)
            2 -> localImage = localToolkit.createImage(cursor_none)
            3 -> {
                arrayOfInt = IntArray(21 * 12)
                arrayOfInt[0] = W.also { arrayOfInt[33] = it }.also { arrayOfInt[32] = it }.also { arrayOfInt[1] = it }
                localMemoryImageSource = MemoryImageSource(32, 32, arrayOfInt, 0, 32)
                localImage = createImage(localMemoryImageSource)
            }
            4 -> {
                arrayOfInt = IntArray(21 * 12)

                var row = 0
                while (row < 21) {
                    var col = 0
                    while (col < 12) {
                        arrayOfInt[col + row * 32] = cursor_outline[col + row * 12]
                        col++
                    }
                    row++
                }

                localMemoryImageSource = MemoryImageSource(32, 32, arrayOfInt, 0, 32)
                localImage = createImage(localMemoryImageSource)
            }
            else -> {
                println("createCursor: unknown cursor $cursorIndex")

                return Cursor.getDefaultCursor()
            }
        }

        var cursor: Cursor? = null
        if (javaVersion < "1.2") {
            println("This JVM cannot create custom cursors")
        } else {
            cursor = customCursor(localImage, Point(), "rcCursor")
        }

        return cursor ?: Cursor.getDefaultCursor()
    }

    fun setCursor(paramInt: Int) {
        currentCursor = createCursor(paramInt)
        cursor = currentCursor
    }

    companion object {
        const val MOUSE_BUTTON_CENTER = 2
        const val MOUSE_BUTTON_LEFT = 4
        const val MOUSE_BUTTON_RIGHT = 1
        private const val B = -0x1000000
        private const val BLKDUP = 34
        private const val BLKRPT = 22
        private const val BLKRPT1 = 28
        private const val BLKRPTNSTD = 30
        private const val BLKRPTSTD = 29
        private const val CMD = 15
        private const val CMD0 = 16
        private const val CMDX = 19
        private const val CMD_BUTTON_CLICK = 0xd3.toByte()
        private const val CMD_BUTTON_PRESS = 0xd1.toByte()
        private const val CMD_BUTTON_RELEASE = 0xd2.toByte()
        private const val CMD_BYTE = 0xd4.toByte()
        private const val CMD_ENCRYPT = 192
        private const val CMD_MOUSE_MOVE = 0xd0.toByte()
        private const val CMD_SET_MODE = 0xd5.toByte()
        private const val CORP = 46
        private const val EXIT = 37
        private const val EXTCMD = 18
        private const val EXTCMD1 = 23
        private const val EXTCMD2 = 25
        private const val FIRMWARE = 24
        private const val HUNT = 43
        private const val LATCHED = 38
        private const val MODE0 = 26
        private const val MODE1 = 40
        private const val MODE2 = 47
        private const val MOUSE_USBABS = '\u0001'
        private const val MOUSE_USBREL = '\u0002'
        private const val MOVELONGX = 21
        private const val MOVESHORTX = 20
        private const val MOVEXY0 = 17
        private const val MOVEXY1 = 39
        private const val PIXCODE = 35
        private const val PIXCODE1 = 5
        private const val PIXCODE2 = 6
        private const val PIXCODE3 = 7
        private const val PIXCODE4 = 32
        private const val PIXDUP = 33
        private const val PIXELS = 2
        private const val PIXFAN = 31
        private const val PIXGREY = 8
        private const val PIXLRU0 = 4
        private const val PIXLRU1 = 3
        private const val PIXRGBB = 42
        private const val PIXRGBG = 41
        private const val PIXRGBR = 9
        private const val PIXRPT = 10
        private const val PIXRPT1 = 11
        private const val PIXRPTNSTD = 14
        private const val PIXRPTSTD1 = 12
        private const val PIXRPTSTD2 = 13
        private const val PIXSPEC = 36
        private const val PRINT0 = 44
        private const val PRINT1 = 45
        private const val RESET = 0
        private const val SIZE_OF_ALL = 48
        private const val START = 1
        private const val TIMEOUT = 27
        private const val W = -0x7f7f80
        private const val block_height = 16
        private const val block_width = 16

        private val block = IntArray(0x100)
        private val cmd_p_buff = IntArray(0x100)
        private val cursor_none = byteArrayOf(0)
        private val dvc_cc_block = IntArray(17)
        private val dvc_cc_color = IntArray(17)
        private val dvc_cc_usage = IntArray(17)
        private val dvc_getmask = intArrayOf(0x0, 0x1, 0x3, 0x7, 0xf, 0x1f, 0x3f, 0x7f, 0xff)
        private val dvc_left = IntArray(0x100)
        private val dvc_lru_lengths = intArrayOf(0, 0, 0, 1, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4)
        private val dvc_reversal = IntArray(0x100)
        private val dvc_right = IntArray(0x100)
        private var cmd_last = 0
        private var cmd_p_count = 0
        private var count_bytes = 0L
        private var debug_lastx = 0
        private var debug_lasty = 0
        private var debug_msgs = false
        private var debug_show_block = 0
        private var dvc_blue = 0
        private var dvc_cc_active = 0
        private var dvc_code = 0
        private var dvc_color = 0
        private var dvc_decoder_state = 0
        private var dvc_green = 0
        private var dvc_ib_acc = 0
        private var dvc_ib_bcnt = 0
        private var dvc_last_color = 0
        private var dvc_lastx = 0
        private var dvc_lasty = 0
        private var dvc_new_bits = '\u0000'
        private var dvc_newx = 0
        private var dvc_newy = 0
        private var dvc_next_state = 0
        private var dvc_pixcode = 38
        private var dvc_pixel_count = 0
        private var dvc_process_inhibit = false
        private var dvc_red = 0
        private var dvc_size_x = 0
        private var dvc_size_y = 0
        private var dvc_y_clipped = 0
        private var dvc_zero_count = 0
        private var fatal_count = 0
        private var framerate = 30
        private var printchan = 0
        private var printstring = ""
        private var timeout_count = 0L
        private var video_detected = true

        private val cursor_outline = intArrayOf(
            W, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            W, W, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            W, 0, W, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            W, 0, 0, W, 0, 0, 0, 0, 0, 0, 0, 0,
            W, 0, 0, 0, W, 0, 0, 0, 0, 0, 0, 0,
            W, 0, 0, 0, 0, W, 0, 0, 0, 0, 0, 0,
            W, 0, 0, 0, 0, 0, W, 0, 0, 0, 0, 0,
            W, 0, 0, 0, 0, 0, 0, W, 0, 0, 0, 0,
            W, 0, 0, 0, 0, 0, 0, 0, W, 0, 0, 0,
            W, 0, 0, 0, 0, 0, 0, 0, 0, W, 0, 0,
            W, 0, 0, 0, 0, 0, 0, 0, 0, 0, W, 0,
            W, 0, 0, 0, 0, 0, 0, W, W, W, W, W,
            W, 0, 0, 0, 0, 0, 0, W, 0, 0, 0, 0,
            W, 0, 0, 0, W, 0, 0, W, 0, 0, 0, 0,
            W, 0, 0, W, W, W, 0, 0, W, 0, 0, 0,
            W, 0, W, 0, 0, W, 0, 0, W, 0, 0, 0,
            W, W, 0, 0, 0, 0, W, 0, 0, W, 0, 0,
            W, 0, 0, 0, 0, 0, W, 0, 0, W, 0, 0,
            0, 0, 0, 0, 0, 0, 0, W, 0, 0, W, 0,
            0, 0, 0, 0, 0, 0, 0, W, 0, 0, W, 0,
            0, 0, 0, 0, 0, 0, 0, 0, W, W, 0, 0
        )
        private val bits_to_read = intArrayOf(
            0, 1, 1, 1, 1, 1, 2, 3,
            4, 4, 1, 1, 3, 3, 8, 1,
            1, 7, 1, 1, 3, 7, 1, 1,
            8, 1, 7, 0, 1, 3, 7, 1,
            4, 0, 0, 0, 1, 0, 1, 7,
            7, 4, 4, 1, 8, 8, 1, 4
        )
        private val next_0 = intArrayOf(
            1, 2, 31, 2, 2, 10, 10, 10,
            10, 41, 2, 33, 2, 2, 2, 16,
            19, 39, 22, 20, 1, 1, 34, 25,
            46, 26, 40, 1, 29, 1, 1, 36,
            10, 2, 1, 35, 8, 37, 38, 1,
            47, 42, 10, 43, 45, 45, 1, 1
        )
        private val next_1 = intArrayOf(
            1, 15, 3, 11, 11, 10, 10, 10,
            10, 41, 11, 12, 2, 2, 2, 17,
            18, 39, 23, 21, 1, 1, 28, 24,
            46, 27, 40, 1, 30, 1, 1, 35,
            10, 2, 1, 35, 9, 37, 38, 1,
            47, 42, 10, 0, 45, 45, 24, 1
        )

        private fun byteToHex(paramByte: Byte): String {
            val localStringBuffer = StringBuilder()
            localStringBuffer.append(toHexChar(paramByte.toInt() ushr 4 and 0xF))
            localStringBuffer.append(toHexChar(paramByte.toInt() and 0xF))

            return localStringBuffer.toString()
        }

        private fun intToHex(paramInt: Int): String {
            val b = paramInt.toByte()

            return byteToHex(b)
        }

        private fun intToHex4(paramInt: Int): String {
            val localStringBuffer = StringBuilder()
            localStringBuffer.append(byteToHex((paramInt / 256).toByte()))
            localStringBuffer.append(byteToHex((paramInt and 0xFF).toByte()))

            return localStringBuffer.toString()
        }

        private fun charToHex(paramChar: Char): String {
            val b = paramChar.code.toByte()

            return byteToHex(b)
        }

        private fun toHexChar(paramInt: Int): Char {
            return if (paramInt in 0..9) {
                (48 + paramInt).toChar()
            } else {
                (65 + (paramInt - 10)).toChar()
            }
        }
    }
}
