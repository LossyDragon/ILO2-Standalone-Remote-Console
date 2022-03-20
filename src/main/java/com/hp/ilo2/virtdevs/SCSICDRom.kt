package com.hp.ilo2.virtdevs

import java.io.BufferedOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.Socket
import kotlin.Throws

class SCSICDRom(
    socket: Socket?,
    inputStream: InputStream?,
    bufferedOutputStream: BufferedOutputStream?,
    str: String,
    i: Int
) : SCSI(socket, inputStream, bufferedOutputStream, str, i) {

    private var dlg: VErrorDialog? = null
    private var doSplitReads = false
    private var retrycount: Int = VirtDevs.prop!!.getProperty("com.hp.ilo2.virtdevs.retrycount", "10").toInt()
    private var sense = ByteArray(3)

    private fun mediaErr(bArr: ByteArray?, bArr2: ByteArray) {
        val stringBuffer = StringBuffer()
            .append("The CDROM drive reports a media error:\nCommand: ")
            .append(D.hex(bArr!![0], 2))
            .append(" ")
            .append(D.hex(bArr[1], 2))
            .append(" ")
            .append(D.hex(bArr[2], 2))
            .append(" ")
            .append(D.hex(bArr[3], 2))
            .append(" ")
            .append(D.hex(bArr[4], 2))
            .append(" ")
            .append(D.hex(bArr[5], 2))
            .append(" ")
            .append(D.hex(bArr[6], 2))
            .append(" ")
            .append(D.hex(bArr[7], 2))
            .append(" ")
            .append(D.hex(bArr[8], 2))
            .append(" ")
            .append(D.hex(bArr[9], 2))
            .append(" ")
            .append(D.hex(bArr[10], 2))
            .append(" ")
            .append(D.hex(bArr[11], 2))
            .append("\n")
            .append("Sense Code: ")
            .append(D.hex(bArr2[0], 2))
            .append("/")
            .append(D.hex(bArr2[1], 2))
            .append("/")
            .append(D.hex(bArr2[2], 2))
            .append("\n\n")
            .toString()

        if (dlg == null || dlg!!.disposed()) {
            dlg = VErrorDialog(stringBuffer, false)
        } else {
            dlg!!.append(stringBuffer)
        }
    }

    init {
        D.println(
            1,
            StringBuffer().append("Media opening ").append(str).append("(").append(i or 2).append(")").toString()
        )
        D.println(1, StringBuffer().append("Media open returns ").append(media.open(str, i)).toString())
    }

    @Throws(IOException::class)
    override fun close() {
        req[0] = 30

        val bArr = req
        val bArr2 = req
        val bArr3 = req
        val bArr4 = req
        val bArr5 = req
        val bArr6 = req
        val bArr7 = req
        val bArr8 = req
        val bArr9 = req
        val bArr10 = req

        req[11] = 0

        bArr10[10] = 0
        bArr9[9] = 0
        bArr8[8] = 0
        bArr7[7] = 0
        bArr6[7] = 0
        bArr5[5] = 0
        bArr4[4] = 0
        bArr3[3] = 0
        bArr2[2] = 0
        bArr[1] = 0

        media.scsi(req, 2, 0, buffer, null)

        super.close()
    }

    private fun scsiLength(i: Int, bArr: ByteArray?): Int {
        var i2 = 0
        val i3 = i + 1

        when (commands[i3] and 8323072) {
            0 -> i2 = commands[i3] and 65535
            B08 -> i2 = bArr!![commands[i3] and 65535].toInt() and 255
            B16 -> i2 = mkInt16(bArr, commands[i3] and 65535)
            B24 -> i2 = mkInt24(bArr, commands[i3] and 65535)
            B32 -> i2 = mkInt32(bArr, commands[i3] and 65535)
            else -> D.println(0, "Unknown Size!")
        }

        if (commands[i3] and BLKS == 8388608) {
            i2 *= 2048
        }

        return i2
    }

    fun startStopUnit() {
        val bArr = ByteArray(3)
        D.println(
            3,
            StringBuffer()
                .append("Start/Stop unit = ")
                .append(media.scsi(byteArrayOf(27, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0), 2, 0, buffer, bArr))
                .append(" ")
                .append(bArr[0].toInt())
                .append("/")
                .append(bArr[1].toInt())
                .append("/")
                .append(bArr[2].toInt())
                .toString()
        )
    }

    private fun within75(bArr: ByteArray?): Boolean {
        val bArr2 = ByteArray(8)
        val bArr3 = byteArrayOf(37, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        val z = bArr!![0].toInt() == 168
        val mkInt32: Int = mkInt32(bArr, 2)
        val mkInt322: Int = if (z) mkInt32(bArr, 6) else mkInt16(bArr, 7)

        media.scsi(bArr3, 1, 8, bArr2, null)

        val mkInt323: Int = mkInt32(bArr2, 0)

        return mkInt32 > mkInt323 - 75 || mkInt32 + mkInt322 > mkInt323 - 75
    }

    private fun splitRead(): Int {
        val z = req[0].toInt() == 168
        val mkInt32: Int = mkInt32(req, 2)
        val mkInt322: Int = if (z) mkInt32(req, 6) else mkInt16(req, 7)
        val i = if (mkInt322 > 32) 32 else mkInt322

        req[2] = (mkInt32 shr 24).toByte()
        req[3] = (mkInt32 shr 16).toByte()
        req[4] = (mkInt32 shr 8).toByte()
        req[5] = mkInt32.toByte()

        if (z) {
            req[6] = (i shr 24).toByte()
            req[7] = (i shr 16).toByte()
            req[8] = (i shr 8).toByte()
            req[9] = i.toByte()
        } else {
            req[7] = (i shr 8).toByte()
            req[8] = i.toByte()
        }

        val scsi = media.scsi(req, 1, i * 2048, buffer, sense)
        if (scsi < 0) {
            return scsi
        }

        val i2 = mkInt322 - i
        if (i2 <= 0) {
            return scsi
        }

        val i3 = mkInt32 + i
        req[2] = (i3 shr 24).toByte()
        req[3] = (i3 shr 16).toByte()
        req[4] = (i3 shr 8).toByte()
        req[5] = i3.toByte()

        if (z) {
            req[6] = (i2 shr 24).toByte()
            req[7] = (i2 shr 16).toByte()
            req[8] = (i2 shr 8).toByte()
            req[9] = i2.toByte()
        } else {
            req[7] = (i2 shr 8).toByte()
            req[8] = i2.toByte()
        }

        val scsi2 = media.scsi(req, 1, i2 * 2048, buffer, sense, B08)

        return if (scsi2 < 0) scsi2 else scsi + scsi2
    }

    @Throws(IOException::class)
    override fun process() {
        var i: Int
        var i2: Int
        var open: Int

        readCommand(req, 12)

        D.println(1, "SCSI Request:")
        D.hexdump(1, req, 12)

        val mediaOpen = media.open(selectedDevice, targetIsDevice)
        open = mediaOpen

        if (media.dio!!.filehandle != -1 || mediaOpen >= 0) {
            var i3 = 0
            while (i3 < commands.size && req[0] != commands[i3].toByte()) {
                i3 += 2
            }

            if (i3 != commands.size) {
                val scsiLength = scsiLength(i3, req)
                val i4 = commands[i3 + 1] shr 24
                val i5: Int = req[0].toInt() and 255

                if (i4 == 0) {
                    readComplete(buffer, scsiLength)
                }

                D.println(
                    1,
                    StringBuffer().append("SCSI dir=").append(i4).append(" len=").append(scsiLength).toString()
                )

                var i6 = 0

                do {
                    val currentTimeMillis = System.currentTimeMillis()

                    i2 = if ((i5 == 40 || i5 == 168) && doSplitReads) {
                        splitRead()
                    } else {
                        media.scsi(req, i4, scsiLength, buffer, sense)
                    }

                    D.println(
                        1,
                        StringBuffer()
                            .append("ret=")
                            .append(i2)
                            .append(" sense=")
                            .append(D.hex(sense[0], 2))
                            .append(" ")
                            .append(
                                D.hex(sense[1], 2)
                            ).append(" ")
                            .append(D.hex(sense[2], 2))
                            .append(" Time=")
                            .append(System.currentTimeMillis() - currentTimeMillis)
                            .toString()
                    )

                    if (i5 == 90) {
                        D.println(1, StringBuffer().append("media type: ").append(D.hex(buffer[3], 2)).toString())
                        reply.setmedia(buffer[3].toInt())
                    }

                    if (i5 == 67) {
                        D.hexdump(3, buffer, scsiLength)
                    }

                    if (i5 == 27) {
                        i2 = 0
                    }

                    if (i5 == 40 || i5 == 168) {
                        if (sense[1].toInt() == 41) {
                            i2 = -1
                        } else if (i2 < 0 && within75(req)) {
                            sense[0] = 5
                            sense[1] = 33
                            sense[2] = 0
                            i2 = 0
                        } else if (i2 < 0) {
                            doSplitReads = true
                        }
                    }

                    if (sense[0].toInt() == 3 || sense[0].toInt() == 4) {
                        mediaErr(req, sense)
                        i2 = -1
                    }

                    if (i2 >= 0) {
                        break
                    }

                    i6++
                } while (i6 < retrycount)

                i = i2

                if (i < 0 || i > B16) {
                    D.println(
                        0,
                        StringBuffer()
                            .append("AIEE! len out of bounds: ")
                            .append(i)
                            .append(", cmd: ")
                            .append(D.hex(i5, 2))
                            .append("\n")
                            .toString()
                    )

                    i = 0

                    reply[5, 32, 0] = 0
                } else {
                    reply[sense[0].toInt(), sense[1].toInt(), sense[2].toInt()] = i
                }
            } else {
                D.println(
                    0,
                    StringBuffer().append("AIEE! Unhandled command").append(D.hex(req[0], 2)).append("\n").toString()
                )

                reply[5, 32, 0] = 0

                i = 0
            }

            reply.send(out)

            if (i != 0) {
                out!!.write(buffer, 0, i)
            }

            out!!.flush()

            return
        }

        VErrorDialog(
            StringBuffer().append("Could not open CDROM (").append(media.dio!!.sysError(-open)).append(")").toString(),
            false
        )

        throw IOException(StringBuffer().append("Couldn't open cdrom ").append(open).toString())
    }

    companion object {
        private const val CONST = 0
        private const val SCSI_IOCTL_DATA_IN = 1
        private const val SCSI_IOCTL_DATA_OUT = 0
        private const val SCSI_IOCTL_DATA_UNSPECIFIED = 2
        private const val WRITE = 0

        private const val B08 = 65536
        private const val B16 = 131072
        private const val B24 = 196608
        private const val B32 = 262144
        private const val BLKS = 8388608
        private const val NONE = 33554432
        private const val READ = 16777216

        private val commands = intArrayOf(
            30, NONE, 37, 16777224, 29, NONE, 0, NONE, 40, 25296903,
            SCSI_READ_12, 25427974, 27, NONE, SCSI_READ_CD, 25362438, SCSI_READ_CD_MSF, READ, 68, 16777224,
            66, 16908295, 67, 16908295, 78, NONE, SCSI_MECHANISM_STATUS, 16908296, 90, 16908295,
            74, 16908295
        )
    }
}
