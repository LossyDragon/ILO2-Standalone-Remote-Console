package com.hp.ilo2.virtdevs

import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.io.BufferedOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.Socket
import java.net.UnknownHostException
import javax.swing.Timer

class Connection(
    private var host: String?,
    private var port: Int,
    private var device: Int,
    private var target: String,
    private var i3: Int,
    private var pre: ByteArray,
    private var key: ByteArray,
    private var v: VirtDevs
) : Runnable, ActionListener {

    private var changingDisks = false
    private var digest: VMD5
    private var inputStream: InputStream? = null
    private var out: BufferedOutputStream? = null
    private var scsi: SCSI? = null
    private var socket: Socket? = null
    private var targetIsDevice = 0
    private var writeprot = false

    init {
        val mediaAccess = MediaAccess()
        val devtype = mediaAccess.devtype(target)

        if (devtype == 2 || devtype == 5) {
            targetIsDevice = 1
            D.println(0, "Got CD or removable connection\n")
        } else {
            targetIsDevice = 0
            D.println(0, "Got NO CD or removable connection\n")
        }

        mediaAccess.open(target, targetIsDevice)
        val size = mediaAccess.size()
        mediaAccess.close()

        if (device == 1 && size > 2949120) {
            device = 3
        }

        digest = VMD5()
    }

    @Throws(UnknownHostException::class, IOException::class)
    fun connect(): Int {
        val bArr = ByteArray(18)
        bArr[0] = 16
        bArr[1] = 0
        bArr[2] = 0
        bArr[3] = 0
        bArr[4] = 0
        bArr[5] = 0
        bArr[6] = 0
        bArr[7] = 0
        bArr[8] = 0
        bArr[9] = 0
        bArr[10] = 0
        bArr[11] = 0
        bArr[12] = 0
        bArr[13] = 0
        bArr[14] = 0
        bArr[15] = 0
        bArr[16] = 0
        bArr[17] = 0

        socket = Socket(host, port)
        socket!!.tcpNoDelay = true
        inputStream = socket!!.getInputStream()
        out = BufferedOutputStream(socket!!.getOutputStream())

        digest.reset()
        digest.update(pre)
        digest.update(key)

        val digest = digest.digest()
        System.arraycopy(digest, 0, bArr, 2, key.size)
        System.arraycopy(digest, 0, key, 0, key.size)

        bArr[1] = device.toByte()

        if (targetIsDevice == 0) {
            bArr[1] = (bArr[1].toInt() or Byte.MIN_VALUE.toInt()).toByte()
        }

        out!!.write(bArr)
        out!!.flush()
        inputStream!!.read(bArr, 0, 4)

        D.println(3, StringBuffer().append("Hello response0: ").append(D.hex(bArr[0], 2)).toString())
        D.println(3, StringBuffer().append("Hello response1: ").append(D.hex(bArr[1], 2)).toString())

        if (bArr[0].toInt() == 32 && bArr[1].toInt() == 0) {
            D.println(
                1,
                StringBuffer()
                    .append("Connected.  Protocol version = ")
                    .append(bArr[3].toInt() and 255)
                    .append(".")
                    .append(bArr[2].toInt() and 255)
                    .toString()
            )
            return 0
        }

        D.println(0, "Unexpected Hello Response!")
        socket!!.close()
        socket = null
        inputStream = null
        out = null

        return bArr[0].toInt()
    }

    @Throws(IOException::class)
    fun close() {
        if (scsi != null) {
            try {
                val timer = Timer(2000, this)
                timer.isRepeats = false
                timer.start()
                scsi!!.changeDisk()
                timer.stop()
            } catch (e: Exception) {
                scsi!!.changeDisk()
            }
        } else {
            internalClose()
        }
    }

    override fun actionPerformed(actionEvent: ActionEvent) {
        try {
            internalClose()
        } catch (e: Exception) {
            /* no-op */
        }
    }

    @Throws(IOException::class)
    fun internalClose() {
        if (socket != null) {
            socket!!.close()
        }
        socket = null
        inputStream = null
        out = null
    }

    fun setWriteProt(z: Boolean) {
        writeprot = z
        scsi?.writeprot = writeprot
    }

    @Throws(IOException::class)
    fun changeDisk(str: String) {
        val mediaAccess = MediaAccess()
        val devtype = mediaAccess.devtype(str)

        val i: Int = if (devtype == 2 || devtype == 5) 1 else 0
        if (i == 0) {
            mediaAccess.open(str, 0)
            mediaAccess.close()
        }

        target = str
        targetIsDevice = i
        changingDisks = true

        scsi!!.changeDisk()
    }

    // java.lang.Runnable
    override fun run() {
        do {
            changingDisks = false

            try {
                scsi = if (device == 1 || device == 3) {
                    SCSIFloppy(socket, inputStream, out, target, targetIsDevice)
                } else if (device != 2) {
                    D.println(0, StringBuffer().append("Unsupported virtual device ").append(device).toString())
                    return
                } else if (targetIsDevice == 1) {
                    SCSICDRom(socket, inputStream, out, target, 1)
                } else {
                    SCSICDImage(socket, inputStream, out, target, 0, v)
                }
            } catch (e: Exception) {
                D.println(
                    0,
                    StringBuffer().append("Exception while opening ").append(target).append("(").append(e).append(")")
                        .toString()
                )
            }

            scsi!!.writeprot = writeprot

            while (true) {
                if ((device == 1 || device == 3) && scsi!!.writeprot) {
                    v.roCbox.state = true
                    v.roCbox.isEnabled = false
                    v.roCbox.repaint()
                }

                try {
                    scsi!!.process()
                } catch (e2: IOException) {
                    D.println(1, StringBuffer().append("Exception in Connection::run() ").append(e2).toString())
                    e2.printStackTrace()
                    D.println(3, "Closing scsi and socket")

                    try {
                        scsi!!.close()
                        if (!changingDisks) {
                            internalClose()
                        }
                    } catch (e3: IOException) {
                        D.println(0, StringBuffer().append("Exception closing connection ").append(e3).toString())
                    }

                    scsi = null

                    if (device == 1 || device == 3) {
                        v.roCbox.isEnabled = true
                        v.roCbox.repaint()
                    }

                    if (!changingDisks) {
                        @Suppress("ControlFlowWithEmptyBody")
                        if (device != 1) {
                            /* no-op */
                        }

                        v.fdDisconnect()
                    }
                }
            }
        } while (!changingDisks)

        if (device != 1 || device == 3) {
            v.fdDisconnect()
        } else if (device == 2) {
            v.cdDisconnect()
        }
    }

    companion object {
        const val FLOPPY = 1
        const val CDROM = 2
        const val USBKEY = 3
    }
}
