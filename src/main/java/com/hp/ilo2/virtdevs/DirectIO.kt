package com.hp.ilo2.virtdevs

import java.util.*
import kotlin.Array
import kotlin.ByteArray
import kotlin.Int
import kotlin.Long
import kotlin.String

class DirectIO {

    companion object {
        private var keydrive = 0

        init {
            keydrive = 1
            val stringBuffer: String = StringBuffer().append("cpqma-").append(Integer.toHexString(VirtDevs.UID)).append(MediaAccess.dllext).toString()
            val property = System.getProperty("file.separator")
            var property2: String? = System.getProperty("java.io.tmpdir")
            val lowerCase = System.getProperty("os.name").lowercase(Locale.getDefault())

            if (property2 == null) {
                property2 = if (lowerCase.startsWith("windows")) "C:\\TEMP" else "/tmp"
            }

            if (!property2.endsWith(property)) {
                property2 = StringBuffer().append(property2).append(property).toString()
            }

            keydrive = if (VirtDevs.prop!!.getProperty("keydrive", "true").toBoolean()) 1 else 0

            var stringBuffer2: String? = StringBuffer().append(property2).append(stringBuffer).toString()
            val property3: String? = VirtDevs.prop!!.getProperty("dll")
            if (property3 != null) {
                stringBuffer2 = property3
            }

            println(StringBuffer().append("Loading ").append(stringBuffer2).toString())
            System.load(stringBuffer2)
        }
    }

    var auxHandle = -1
    var bufferaddr: Long = 0
    var bytesPerSec = 0
    var cylinders = 0
    var endCylinder = 0
    var endHead = 0
    var filehandle = -1
    var mediaSize = 0
    var mediaType = 0
    var misc0 = 0
    var physicalDevice = 0
    var secPerTrack = 0
    var startCylinder = 0
    var startHead = 0
    var tracksPerCyl = 0
    var wp = 0

    external fun close(): Int
    external fun devices(): Array<String?>?
    external fun devtype(str: String?): Int
    external fun format(): Int
    external fun open(str: String?): Int
    external fun read(j: Long, i: Int, bArr: ByteArray?): Int
    external fun scsi(bArr: ByteArray?, i: Int, i2: Int, bArr2: ByteArray?, bArr3: ByteArray?, i3: Int): Int
    external fun size(): Long
    external fun sysError(i: Int): String?
    external fun write(j: Long, i: Int, bArr: ByteArray?): Int

    protected fun finalize() {
        if (filehandle != -1) {
            close()
        }
    }
}
