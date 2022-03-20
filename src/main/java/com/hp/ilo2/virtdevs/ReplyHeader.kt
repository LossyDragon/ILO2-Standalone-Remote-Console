package com.hp.ilo2.virtdevs

import com.hp.ilo2.remcons.Telnet
import java.io.IOException
import java.io.OutputStream

class ReplyHeader {

    @Suppress("unused")
    companion object {
        const val KEEPALIVE = 2
        const val WP = 1
        const val magic = 195936478
    }

    private var asc: Byte = 0
    private var ascq: Byte = 0
    private var data = ByteArray(16)
    private var flags = 0
    private var length = 0
    private var media: Byte = 0
    private var senseKey: Byte = 0

    operator fun set(i: Int, i2: Int, i3: Int, i4: Int) {
        senseKey = i.toByte()
        asc = i2.toByte()
        ascq = i3.toByte()
        length = i4
    }

    fun setmedia(i: Int) {
        media = i.toByte()
    }

    fun setflags(z: Boolean) {
        flags = if (z) flags or 1 else flags and -2
    }

    fun keepalive(z: Boolean) {
        flags = if (z) flags or 2 else flags and -3
    }

    @Throws(IOException::class)
    fun send(outputStream: OutputStream?) {
        data[0] = -34
        data[1] = -64
        data[2] = -83
        data[3] = 11
        data[4] = (flags and Telnet.TELNET_IAC.toInt()).toByte()
        data[5] = (flags shr 8 and Telnet.TELNET_IAC.toInt()).toByte()
        data[6] = (flags shr 16 and Telnet.TELNET_IAC.toInt()).toByte()
        data[7] = (flags shr 24 and Telnet.TELNET_IAC.toInt()).toByte()
        data[8] = media
        data[9] = senseKey
        data[10] = asc
        data[11] = ascq
        data[12] = (length and Telnet.TELNET_IAC.toInt()).toByte()
        data[13] = (length shr 8 and Telnet.TELNET_IAC.toInt()).toByte()
        data[14] = (length shr 16 and Telnet.TELNET_IAC.toInt()).toByte()
        data[15] = (length shr 24 and Telnet.TELNET_IAC.toInt()).toByte()

        outputStream!!.write(data, 0, 16)
    }

    @Throws(IOException::class)
    fun sendsynch(outputStream: OutputStream?, bArr: ByteArray) {
        data[0] = -34
        data[1] = -64
        data[2] = -83
        data[3] = 11
        data[4] = (flags and Telnet.TELNET_IAC.toInt()).toByte()
        data[5] = (flags shr 8 and Telnet.TELNET_IAC.toInt()).toByte()
        data[6] = (flags shr 16 and Telnet.TELNET_IAC.toInt()).toByte()
        data[7] = (flags shr 24 and Telnet.TELNET_IAC.toInt()).toByte()
        data[8] = bArr[4]
        data[9] = bArr[5]
        data[10] = bArr[6]
        data[11] = bArr[7]
        data[12] = bArr[8]
        data[13] = bArr[9]
        data[14] = bArr[10]
        data[15] = bArr[11]

        outputStream!!.write(data, 0, 16)
    }
}
