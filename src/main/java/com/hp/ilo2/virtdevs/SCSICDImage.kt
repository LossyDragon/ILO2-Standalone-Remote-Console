package com.hp.ilo2.virtdevs

import com.hp.ilo2.remcons.Telnet
import java.io.BufferedOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.Socket

class SCSICDImage(
    socket: Socket?,
    inputStream: InputStream?,
    bufferedOutputStream: BufferedOutputStream?,
    str: String,
    i: Int,
    private var cdi: VirtDevs
) : SCSI(socket, inputStream, bufferedOutputStream, str, i) {

    private var fddState = 0
    private var eventState = 0
    private var mediaSz: Long = 0

    override var writeProt: Boolean
        get() = super.writeProt
        set(z) {
            writeprot = z
        }

    init {
        D.println(
            1,
            StringBuffer()
                .append("Media open returns ")
                .append(media.open(str, 0))
                .append(" / ")
                .append(media.size())
                .append(" bytes").toString()
        )
    }

    @Throws(IOException::class)
    override fun process() {
        D.println(
            1,
            StringBuffer()
                .append("Device: ")
                .append(selectedDevice)
                .append(" (")
                .append(targetIsDevice)
                .append(")")
                .toString()
        )

        readCommand(req, 12)

        D.print(1, "SCSI Request: ")
        D.hexdump(1, req, 12)

        mediaSz = media.size()

        if (mediaSz == 0L) {
            reply.setmedia(0)
            fddState = 0
            eventState = 4
        } else {
            reply.setmedia(1)
            fddState++

            if (fddState > 2) {
                fddState = 2
            }

            if (eventState == 4) {
                eventState = 0
            }

            eventState++

            if (eventState > 2) {
                eventState = 2
            }
        }

        when (req[0].toInt() and 255) {
            0 -> {
                clientTestUnitReady()
                return
            }
            SCSI_START_STOP_UNIT -> {
                clientStartStopUnit(req)
                return
            }
            SCSI_SEND_DIAGNOSTIC -> {
                clientSendDiagnostic()
                return
            }
            SCSI_PA_MEDIA_REMOVAL -> {
                clientPaMediaRemoval(req)
                return
            }
            SCSI_READ_CAPACITY -> {
                clientReadCapacity()
                return
            }
            SCSI_READ_10,
            SCSI_READ_12 -> {
                clientRead(req)
                return
            }
            SCSI_READ_TOC -> {
                clientReadToc(req)
                return
            }
            SCSI_GET_EVENT_STATUS -> {
                clientGetEventStatus(req)
                return
            }
            SCSI_MODE_SENSE -> {
                clientModeSense(req)
                return
            }
            else -> {
                D.println(
                    0,
                    StringBuffer()
                        .append("Unknown request:cmd = ")
                        .append(Integer.toHexString(req[0].toInt()))
                        .toString()
                )

                reply[5, 36, 0] = 0
                reply.send(out)

                out!!.flush()

                return
            }
        }
    }

    @Throws(IOException::class)
    fun clientSendDiagnostic() {
        /* no-op */
    }

    @Throws(IOException::class)
    fun clientRead(bArr: ByteArray?) {
        val mkInt32: Long = (mkInt32(bArr, 2) * 2048).toLong()
        var mkInt322: Int = (if (bArr!![0].toInt() == 168) mkInt32(bArr, 6) else mkInt16(bArr, 7)) * 2048

        D.println(
            3,
            StringBuffer()
                .append("CDImage :Client read ")
                .append(mkInt32)
                .append(", len=")
                .append(mkInt322)
                .toString()
        )

        if (fddState == 0) {
            D.println(3, "media not present")

            reply[2, 58, 0] = 0
            mkInt322 = 0
        } else if (fddState == 1) {
            D.println(3, "media changed")

            reply[6, 40, 0] = 0
            mkInt322 = 0
            fddState = 2
        } else if (mkInt32 < 0 || mkInt32 >= mediaSz) {
            reply[5, 33, 0] = 0
            mkInt322 = 0
        } else {
            media.read(mkInt32, mkInt322, buffer)
            reply[0, 0, 0] = mkInt322
        }

        reply.send(out)

        if (mkInt322 != 0) {
            out!!.write(buffer, 0, mkInt322)
        }

        out!!.flush()
    }

    @Throws(IOException::class)
    fun clientPaMediaRemoval(bArr: ByteArray?) {
        if (bArr!![4].toInt() and 1 != 0) {
            D.println(3, "Media removal prevented")
        } else {
            D.println(3, "Media removal allowed")
        }

        reply[0, 0, 0] = 0
        reply.send(out)

        out!!.flush()
    }

    @Throws(IOException::class)
    fun clientStartStopUnit(bArr: ByteArray?) {
        val b: Byte = (bArr!![4].toInt() and 3).toByte()

        if (b.toInt() == 3) {
            if (cdi.cdConnection != null) {
                fddState = 1
                eventState = 2
            } else {
                fddState = 0
                eventState = 4
            }
        } else if (b.toInt() == 2) {
            fddState = 0
            eventState = 4

            if (cdi.cdConnection != null) {
                cdi.doCdrom(cdi.cdSelected)
            }

            D.println(3, "Media eject")
        }

        reply[0, 0, 0] = 0
        reply.send(out)

        out!!.flush()
    }

    @Throws(IOException::class)
    fun clientTestUnitReady() {
        when (fddState) {
            0 -> {
                D.println(3, "media not present")
                reply[2, 58, 0] = 0
            }
            1 -> {
                D.println(3, "media changed")
                reply[6, 40, 0] = 0
                fddState = 2
            }
            else -> {
                D.println(3, "device ready")
                reply[0, 0, 0] = 0
            }
        }

        reply.send(out)

        out!!.flush()
    }

    @Throws(IOException::class)
    fun clientReadCapacity() {
        val bArr = ByteArray(8)
        bArr[0] = 0
        bArr[1] = 0
        bArr[2] = 0
        bArr[3] = 0
        bArr[4] = 0
        bArr[5] = 0
        bArr[6] = 0
        bArr[7] = 0

        reply[0, 0, 0] = bArr.size

        when (fddState) {
            0 -> reply[2, 58, 0] = 0
            1 -> reply[6, 40, 0] = 0
            else -> {
                val size = (media.size() / 2048 - 1).toInt()
                bArr[0] = (size shr 24 and Telnet.TELNET_IAC.toInt()).toByte()
                bArr[1] = (size shr 16 and Telnet.TELNET_IAC.toInt()).toByte()
                bArr[2] = (size shr 8 and Telnet.TELNET_IAC.toInt()).toByte()
                bArr[3] = (size shr 0 and Telnet.TELNET_IAC.toInt()).toByte()
                bArr[6] = 8
            }
        }

        reply.send(out)

        if (fddState == 2) {
            out!!.write(bArr, 0, bArr.size)
        }

        out!!.flush()

        D.print(3, "client_read_capacity: ")
        D.hexdump(3, bArr, 8)
    }

    @Throws(IOException::class)
    fun clientReadToc(bArr: ByteArray?) {
        val z = bArr!![1].toInt() and 2 != 0
        val i: Int = bArr[9].toInt() and 192 shr 6
        val size = (media.size() / 2048).toInt()
        val d = size / 75.0 + 2.0
        val i2 = d.toInt() / 60
        val i3 = d.toInt() % 60
        val i4 = ((d - d.toInt()) * 75.0).toInt()
        val mkInt16: Int = mkInt16(bArr, 7)

        for (i5 in 0 until mkInt16) {
            buffer[i5] = 0
        }

        if (i == 0) {
            buffer[0] = 0
            buffer[1] = 18
            buffer[2] = 1
            buffer[3] = 1
            buffer[4] = 0
            buffer[5] = 20
            buffer[6] = 1
            buffer[7] = 0
            buffer[8] = 0
            buffer[9] = 0
            buffer[10] = if (z) 2.toByte() else 0.toByte()
            buffer[11] = 0
            buffer[12] = 0
            buffer[13] = 20
            buffer[14] = -86
            buffer[15] = 0
            buffer[16] = 0
            buffer[17] = if (z) i2.toByte() else (size shr 16 and Telnet.TELNET_IAC.toInt()).toByte()
            buffer[18] = if (z) i3.toByte() else (size shr 8 and Telnet.TELNET_IAC.toInt()).toByte()
            buffer[19] = if (z) i4.toByte() else (size and Telnet.TELNET_IAC.toInt()).toByte()
        }

        if (i == 1) {
            buffer[0] = 0
            buffer[1] = 10
            buffer[2] = 1
            buffer[3] = 1
            buffer[4] = 0
            buffer[5] = 20
            buffer[6] = 1
            buffer[7] = 0
            buffer[8] = 0
            buffer[9] = 0
            buffer[10] = if (z) 2.toByte() else 0.toByte()
            buffer[11] = 0
        }

        var i6 = 412

        if (mkInt16 < 412) {
            i6 = mkInt16
        }

        D.hexdump(3, buffer, i6)

        reply[0, 0, 0] = i6
        reply.send(out)

        out!!.write(buffer, 0, i6)
        out!!.flush()
    }

    @Suppress("UNUSED_PARAMETER")
    @Throws(IOException::class)
    fun clientModeSense(bArr: ByteArray?) {
        buffer[0] = 0
        buffer[1] = 8
        buffer[2] = 1
        buffer[3] = 0
        buffer[4] = 0
        buffer[5] = 0
        buffer[6] = 0
        buffer[7] = 0

        reply[0, 0, 0] = 8

        D.hexdump(3, buffer, 8)

        reply.setmedia(buffer[2].toInt())
        reply.send(out)

        out!!.write(buffer, 0, 8)
        out!!.flush()
    }

    @Throws(IOException::class)
    fun clientGetEventStatus(bArr: ByteArray?) {
        val b = bArr!![4]
        val mkInt16: Int = mkInt16(bArr, 7)

        for (i in 0 until mkInt16) {
            buffer[i] = 0
        }

        if (bArr[1].toInt() and 1 == 0) {
            reply[5, 36, 0] = 0
            reply.send(out)
            out!!.flush()
        }

        if (b.toInt() and 16 != 0) {
            buffer[0] = 0
            buffer[1] = 6
            buffer[2] = 4
            buffer[3] = 16

            if (eventState == 0) {
                buffer[4] = 0
                buffer[5] = 0
            } else if (eventState == 1) {
                buffer[4] = 4
                buffer[5] = 2

                if (mkInt16 > 4) {
                    eventState = 2
                }
            } else if (eventState == 4) {
                buffer[4] = 3
                buffer[5] = 0

                if (mkInt16 > 4) {
                    eventState = 0
                }
            } else {
                buffer[4] = 0
                buffer[5] = 2
            }

            D.hexdump(3, buffer, 8)

            reply[0, 0, 0] = if (mkInt16 < 8) mkInt16 else 8
            reply.send(out)

            out!!.write(buffer, 0, if (mkInt16 < 8) mkInt16 else 8)
            out!!.flush()

            return
        }

        buffer[0] = 0
        buffer[1] = 2
        buffer[2] = Byte.MIN_VALUE
        buffer[3] = 16

        D.hexdump(3, buffer, 4)

        reply[0, 0, 0] = if (mkInt16 < 4) mkInt16 else 4
        reply.send(out)

        out!!.write(buffer, 0, if (mkInt16 < 4) mkInt16 else 4)
        out!!.flush()
    }
}
