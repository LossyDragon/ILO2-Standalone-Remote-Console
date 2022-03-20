package com.hp.ilo2.virtdevs

import com.hp.ilo2.remcons.Telnet
import java.io.BufferedOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.Socket
import java.util.*

class SCSIFloppy(
    socket: Socket?,
    inputStream: InputStream?,
    bufferedOutputStream: BufferedOutputStream?,
    str: String,
    i: Int
) : SCSI(socket, inputStream, bufferedOutputStream, str, i) {

    private var date = Date()
    private var fddState = 0
    private var mediaSz: Long = 0
    private var rcsResp = byteArrayOf(0, 0, 0, 16, 0, 0, 0, 0, 2, 0, 0, 0, 0, 0, 11, 64, 0, 0, 2, 0)

    override var writeProt: Boolean
        get() = super.writeProt
        set(z) {
            writeprot = z
            if (fddState == 2) {
                fddState = 0
            }
        }

    init {
        D.print(1, StringBuffer().append("open returns ").append(media.open(str, i)).toString())
    }

    @Throws(IOException::class)
    override fun process() {
        date.time = System.currentTimeMillis()

        D.println(1, StringBuffer().append("Date = ").append(date).toString())
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

        if (mediaSz < 0 || media.dio != null && media.dio!!.filehandle == -1) {
            D.println(1, "Disk change detected\n")
            media.close()
            media.open(selectedDevice, targetIsDevice)
            mediaSz = media.size()
            fddState = 0
        }

        D.println(
            1,
            StringBuffer()
                .append("retval=")
                .append(mediaSz)
                .append(" type=")
                .append(media.type())
                .append(" physdrive=")
                .append(if (media.dio != null) media.dio!!.physicalDevice else -1)
                .toString()
        )

        if (mediaSz <= 0) {
            reply.setmedia(0)
            fddState = 0
        } else {
            reply.setmedia(36)
            fddState++
            if (fddState > 2) {
                fddState = 2
            }
        }

        if (!writeprot && media.wp()) {
            writeprot = true
        }

        when (req[0].toInt() and 255) {
            0 -> {
                clientTestUnitReady()
                return
            }
            4 -> {
                clientFormatUnit(req)
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
            SCSI_READ_CAPACITIES -> {
                clientReadCapacities()
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
            SCSI_WRITE_10,
            SCSI_WRITE_VERIFY,
            SCSI_WRITE_12 -> {
                clientWrite(req)
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

                return
            }
        }
    }

    @Throws(IOException::class)
    fun clientReadCapacities() {
        if (fddState != 1) {
            reply[0, 0, 0] = rcsResp.size
        } else {
            reply[6, 40, 0] = rcsResp.size
            fddState = 2
        }

        if (media.type() == 0) {
            val bArr = rcsResp
            val bArr2 = rcsResp
            val bArr3 = rcsResp
            val bArr4 = rcsResp
            val bArr5 = rcsResp
            rcsResp[11] = 0
            bArr5[10] = 0
            bArr4[7] = 0
            bArr3[6] = 0
            bArr2[5] = 0
            bArr[4] = 0
        } else if (media.type() == 100) {
            val size = media.size() / 512
            rcsResp[4] = (size shr 24 and 255).toByte()
            rcsResp[5] = (size shr 16 and 255).toByte()
            rcsResp[6] = (size shr 8 and 255).toByte()
            rcsResp[7] = (size shr 0 and 255).toByte()
            rcsResp[10] = 2
            rcsResp[11] = 0
        } else {
            val size2 = media.size() / media.dio!!.bytesPerSec
            rcsResp[4] = (size2 shr 24 and 255).toByte()
            rcsResp[5] = (size2 shr 16 and 255).toByte()
            rcsResp[6] = (size2 shr 8 and 255).toByte()
            rcsResp[7] = (size2 shr 0 and 255).toByte()
            rcsResp[10] = (media.dio!!.bytesPerSec shr 8 and Telnet.TELNET_IAC.toInt()).toByte()
            rcsResp[11] = (media.dio!!.bytesPerSec and Telnet.TELNET_IAC.toInt()).toByte()
        }

        reply.setflags(writeprot)
        reply.send(out)

        out!!.write(rcsResp, 0, rcsResp.size)
        out!!.flush()
    }

    @Throws(IOException::class)
    fun clientSendDiagnostic() {
        fddState = 1
    }

    @Throws(IOException::class)
    fun clientRead(bArr: ByteArray?) {
        val mkInt32: Long = (mkInt32(bArr, 2) * 512).toLong()
        var mkInt322: Int = (if (bArr!![0].toInt() == 168) mkInt32(bArr, 6) else mkInt16(bArr, 7)) * 512

        D.println(
            3,
            StringBuffer()
                .append("FDIO.client_read:Client read ")
                .append(mkInt32).append(", len=")
                .append(mkInt322)
                .toString()
        )

        if (mkInt32 < 0 || mkInt32 >= mediaSz) {
            reply[5, 33, 0] = 0
            mkInt322 = 0
        } else {
            try {
                media.read(mkInt32, mkInt322, buffer)
                reply[0, 0, 0] = mkInt322
            } catch (e: IOException) {
                D.println(0, StringBuffer().append("Exception during read: ").append(e).toString())
                reply[3, 16, 0] = 0
                mkInt322 = 0
            }
        }

        reply.setflags(writeprot)
        reply.send(out)

        if (mkInt322 != 0) {
            out!!.write(buffer, 0, mkInt322)
        }

        out!!.flush()
    }

    @Throws(IOException::class)
    fun clientWrite(bArr: ByteArray?) {
        val z = bArr!![0].toInt() == 170
        val mkInt32: Long = (mkInt32(bArr, 2) * 512).toLong()
        val mkInt322: Int = (if (z) mkInt32(bArr, 6) else mkInt16(bArr, 7)) * 512

        D.println(
            3,
            StringBuffer()
                .append("FDIO.client_write:lba = ")
                .append(mkInt32)
                .append(", length = ")
                .append(mkInt322)
                .toString()
        )

        readComplete(buffer, mkInt322)

        if (writeprot) {
            reply[7, 39, 0] = 0
        } else if (mkInt32 < 0 || mkInt32 >= mediaSz) {
            reply[5, 33, 0] = 0
        } else {
            try {
                media.write(mkInt32, mkInt322, buffer)
                reply[0, 0, 0] = 0
            } catch (e: IOException) {
                D.println(0, StringBuffer().append("Exception during write: ").append(e).toString())
                reply[3, 16, 0] = 0
            }
        }

        reply.setflags(writeprot)
        reply.send(out)

        out!!.flush()
    }

    @Throws(IOException::class)
    fun clientPaMediaRemoval(bArr: ByteArray?) {
        if (bArr!![4].toInt() and 1 != 0) {
            reply[5, 36, 0] = 0
        } else {
            reply[0, 0, 0] = 0
        }

        reply.setflags(writeprot)
        reply.send(out)

        out!!.flush()
    }

    @Throws(IOException::class)
    fun clientStartStopUnit(bArr: ByteArray?) {
        if (bArr!![4].toInt() and 2 != 0) {
            reply[5, 36, 0] = 0
        } else {
            reply[0, 0, 0] = 0
        }

        reply.setflags(writeprot)
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

        reply.setflags(writeprot)
        reply.send(out)

        out!!.flush()
    }

    @Throws(IOException::class)
    fun clientFormatUnit(bArr: ByteArray?) {
        val i: Int
        val bArr2 = ByteArray(100)
        val mkInt16: Int = mkInt16(bArr, 7)

        readComplete(bArr2, mkInt16)

        D.print(3, "Format params: ")
        D.hexdump(3, bArr2, mkInt16)

        val i2: Int = bArr2[1].toInt() and 1

        i = if (mkInt32(bArr2, 4) == 2880 && mkInt24(bArr2, 9) == 512) {
            2
        } else if (mkInt32(bArr2, 4) == 1440 && mkInt24(bArr2, 9) == 512) {
            5
        } else {
            0
        }

        if (writeprot) {
            reply[7, 39, 0] = 0
        } else if (i != 0) {
            val i3: Int = bArr!![2].toInt() and 255
            media.format(i, i3, i3, i2, i2)
            D.println(3, "format")
            reply[0, 0, 0] = 0
        } else {
            reply[5, 38, 0] = 0
        }

        reply.setflags(writeprot)
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

        if (fddState == 0) {
            reply[2, 58, 0] = 0
        } else if (fddState == 1) {
            reply[6, 40, 0] = 0
        } else if (media.type() != 0) {
            if (media.type() == 100) {
                val size = media.size() / 512 - 1
                bArr[0] = (size shr 24 and 255).toByte()
                bArr[1] = (size shr 16 and 255).toByte()
                bArr[2] = (size shr 8 and 255).toByte()
                bArr[3] = (size shr 0 and 255).toByte()
                bArr[6] = 2
            } else {
                val size2 = media.size() / media.dio!!.bytesPerSec - 1
                bArr[0] = (size2 shr 24 and 255).toByte()
                bArr[1] = (size2 shr 16 and 255).toByte()
                bArr[2] = (size2 shr 8 and 255).toByte()
                bArr[3] = (size2 shr 0 and 255).toByte()
                bArr[6] = (media.dio!!.bytesPerSec shr 8 and Telnet.TELNET_IAC.toInt()).toByte()
                bArr[7] = (media.dio!!.bytesPerSec and Telnet.TELNET_IAC.toInt()).toByte()
            }
        }

        reply.setflags(writeprot)
        reply.send(out)

        if (fddState == 2) {
            out!!.write(bArr, 0, bArr.size)
        }

        out!!.flush()

        D.print(3, "FDIO.client_read_capacity: ")
        D.hexdump(3, bArr, 8)
    }
}
