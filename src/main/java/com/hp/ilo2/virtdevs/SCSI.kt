package com.hp.ilo2.virtdevs

import java.io.BufferedOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.net.Socket
import kotlin.Throws

abstract class SCSI(
    private var sock: Socket?,
    protected var `in`: InputStream?,
    protected var out: BufferedOutputStream?,
    var selectedDevice: String,
    i: Int
) {

    private var pleaseExit = false
    var buffer = ByteArray(131072)
    var media = MediaAccess()
    var reply = ReplyHeader()
    var req = ByteArray(12)
    var targetIsDevice = 0
    var writeprot = false

    open var writeProt: Boolean
        get() {
            D.println(3, StringBuffer().append("media.wp = ").append(media.wp()).toString())
            return media.wp()
        }
        set(z) {
            writeprot = z
        }

    init {
        targetIsDevice = i
    }

    @Throws(IOException::class)
    abstract fun process()

    @Throws(IOException::class)
    open fun close() {
        media.close()
    }

    @Throws(IOException::class)
    fun readComplete(bArr: ByteArray, i: Int): Int {
        var i = i
        var read = 0
        var i2 = 0

        while (i > 0) {
            try {
                sock!!.soTimeout = 1000
                read = `in`!!.read(bArr, i2, i)
            } catch (e: InterruptedIOException) {
                /* no-op */
            }

            if (read < 0) {
                break
            }

            i -= read
            i2 += read
        }

        return i2
    }

    @Throws(IOException::class)
    fun readCommand(bArr: ByteArray, i: Int): Int {
        var i2 = 0
        while (true) {
            try {
                sock!!.soTimeout = 1000
                i2 = `in`!!.read(bArr, 0, i)
            } catch (e: InterruptedIOException) {
                reply.keepalive(true)
                D.println(3, "Sending keepalive")
                reply.send(out)
                out!!.flush()
                reply.keepalive(false)

                if (pleaseExit) {
                    break
                }
            }

            if (bArr[0].toInt() and 255 != 254) {
                break
            }

            reply.sendsynch(out, bArr)
            out!!.flush()
        }

        return if (pleaseExit) {
            throw IOException("Asked to exit")
        } else if (i2 >= 0) {
            i2
        } else {
            throw IOException("Socket Closed")
        }
    }

    fun changeDisk() {
        pleaseExit = true
    }

    @Suppress("unused")
    companion object {
        const val SCSI_FORMAT_UNIT = 4
        const val SCSI_GET_EVENT_STATUS = 74
        const val SCSI_INQUIRY = 18
        const val SCSI_MECHANISM_STATUS = 189
        const val SCSI_MODE_SELECT = 85
        const val SCSI_MODE_SELECT_6 = 21
        const val SCSI_MODE_SENSE = 90
        const val SCSI_MODE_SENSE_6 = 26
        const val SCSI_PA_MEDIA_REMOVAL = 30
        const val SCSI_READ_10 = 40
        const val SCSI_READ_12 = 168
        const val SCSI_READ_CAPACITIES = 35
        const val SCSI_READ_CAPACITY = 37
        const val SCSI_READ_CD = 190
        const val SCSI_READ_CD_MSF = 185
        const val SCSI_READ_HEADER = 68
        const val SCSI_READ_SUBCHANNEL = 66
        const val SCSI_READ_TOC = 67
        const val SCSI_REQUEST_SENSE = 3
        const val SCSI_REZERO_UNIT = 1
        const val SCSI_SEEK = 43
        const val SCSI_SEND_DIAGNOSTIC = 29
        const val SCSI_START_STOP_UNIT = 27
        const val SCSI_STOP_PLAY_SCAN = 78
        const val SCSI_TEST_UNIT_READY = 0
        const val SCSI_VERIFY = 47
        const val SCSI_WRITE_10 = 42
        const val SCSI_WRITE_12 = 170
        const val SCSI_WRITE_VERIFY = 46

        fun mkInt32(bArr: ByteArray?, i: Int): Int {
            return bArr!![i + 0].toInt() and 255 shl 24 or (bArr[i + 1].toInt() and 255 shl 16) or
                (bArr[i + 2].toInt() and 255 shl 8) or (bArr[i + 3].toInt() and 255)
        }

        fun mkInt24(bArr: ByteArray?, i: Int): Int {
            return bArr!![i + 0].toInt() and 255 shl 16 or (bArr[i + 1].toInt() and 255 shl 8) or
                (bArr[i + 2].toInt() and 255)
        }

        fun mkInt16(bArr: ByteArray?, i: Int): Int {
            return bArr!![i + 0].toInt() and 255 shl 8 or (bArr[i + 1].toInt() and 255)
        }
    }
}
