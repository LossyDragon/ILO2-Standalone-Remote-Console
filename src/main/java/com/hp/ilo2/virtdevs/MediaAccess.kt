package com.hp.ilo2.virtdevs

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.util.*
import kotlin.Throws
import kotlin.jvm.JvmOverloads

class MediaAccess {

    private var dev = false
    private var file: File? = null
    private var raf: RandomAccessFile? = null
    private var readonly = false
    private var zeroOffset = 0
    var dio: DirectIO? = null

    @Throws(IOException::class)
    fun open(str: String, i: Int): Int {
        dev = i and 1 == 1

        val z = i and 2 == 2

        zeroOffset = 0

        return if (!dev) {
            readonly = false
            file = File(str)

            if (!file!!.exists() && !z) {
                throw IOException(StringBuffer().append("File ").append(str).append(" does not exist").toString())
            } else if (file!!.isDirectory) {
                throw IOException(StringBuffer().append("File ").append(str).append(" is a directory").toString())
            } else {
                try {
                    raf = RandomAccessFile(str, "rw")
                } catch (e: IOException) {
                    if (!z) {
                        raf = RandomAccessFile(str, "r")
                        readonly = true
                    } else {
                        throw e
                    }
                }

                val bArr = ByteArray(512)
                read(0L, 512, bArr)

                if (bArr[0].toInt() == 67 &&
                    bArr[1].toInt() == 80 &&
                    bArr[2].toInt() == 81 &&
                    bArr[3].toInt() == 82 &&
                    bArr[4].toInt() == 70 &&
                    bArr[5].toInt() == 66 &&
                    bArr[6].toInt() == 76 &&
                    bArr[7].toInt() == 79
                ) {
                    zeroOffset = bArr[14].toInt() or (bArr[15].toInt() shl 8)
                }

                0
            }
        } else if (dio_setup != 0) {
            throw IOException(StringBuffer().append("DirectIO not possible (").append(dio_setup).append(")").toString())
        } else {
            if (dio == null) {
                dio = DirectIO()
            }

            dio!!.open(str)
        }
    }

    @Throws(IOException::class)
    fun close(): Int {
        if (dev) {
            return dio!!.close()
        }

        raf!!.close()

        return 0
    }

    @Throws(IOException::class)
    fun read(j: Long, i: Int, bArr: ByteArray?) {
        val j2 = j + zeroOffset

        if (dev) {
            val read = dio!!.read(j2, i, bArr)

            if (read != 0) {
                throw IOException(
                    StringBuffer().append("DirectIO read error (").append(dio!!.sysError(-read)).append(")").toString()
                )
            }

            return
        }

        raf!!.seek(j2)
        raf!!.read(bArr, 0, i)
    }

    @Throws(IOException::class)
    fun write(j: Long, i: Int, bArr: ByteArray) {
        val j2 = j + zeroOffset

        if (dev) {
            val write = dio!!.write(j2, i, bArr)

            if (write != 0) {
                throw IOException(
                    StringBuffer()
                        .append("DirectIO write error (")
                        .append(dio!!.sysError(-write))
                        .append(")")
                        .toString()
                )
            }

            return
        }
        raf!!.seek(j2)
        raf!!.write(bArr, 0, i)
    }

    @Throws(IOException::class)
    fun size(): Long {
        return if (dev) dio!!.size() else raf!!.length() - zeroOffset
    }

    @Throws(IOException::class)
    fun format(i: Int, i2: Int, i3: Int, i4: Int, i5: Int): Int {
        if (!dev) {
            return 0
        }

        dio!!.mediaType = i
        dio!!.startCylinder = i2
        dio!!.endCylinder = i3
        dio!!.startHead = i4
        dio!!.endHead = i5

        return dio!!.format()
    }

    fun devices(): Array<String?>? {
        if (dio_setup != 0) {
            return null
        }

        if (dio == null) {
            dio = DirectIO()
        }

        return dio!!.devices()
    }

    fun devtype(str: String?): Int {
        if (dio_setup != 0) {
            return 0
        }

        if (dio == null) {
            dio = DirectIO()
        }

        return dio!!.devtype(str)
    }

    @JvmOverloads
    fun scsi(bArr: ByteArray?, i: Int, i2: Int, bArr2: ByteArray?, bArr3: ByteArray?, i3: Int = 0): Int {
        return if (dev) dio!!.scsi(bArr, i, i2, bArr2, bArr3, i3) else -1
    }

    fun wp(): Boolean {
        return if (dev) dio!!.wp == 1 else readonly
    }

    fun type(): Int {
        if (dev && dio != null) {
            return dio!!.mediaType
        }

        return if (raf != null) 100 else 0
    }

    private fun dllExtract(str: String, str2: String): Int {
        val classLoader = javaClass.classLoader
        val bArr = ByteArray(4096)

        D.println(1, StringBuffer().append("dllExtract trying ").append(str).toString())
        if (classLoader.getResource(str) == null) {
            return -1
        }

        D.println(
            1,
            StringBuffer()
                .append("Extracting")
                .append(classLoader.getResource(str).toExternalForm())
                .append(" to ")
                .append(str2)
                .toString()
        )

        try {
            val resourceAsStream = classLoader.getResourceAsStream(str)
            val fileOutputStream = FileOutputStream(str2)

            while (true) {
                val read = resourceAsStream.read(bArr, 0, 4096)
                if (read == -1) {
                    resourceAsStream.close()
                    fileOutputStream.close()

                    return 0
                }

                fileOutputStream.write(bArr, 0, read)
            }
        } catch (e: IOException) {
            D.println(0, StringBuffer().append("dllExtract: ").append(e).toString())

            return -2
        }
    }

    fun setupDirectIO(): Int {
        val property = System.getProperty("file.separator")
        var property2 = System.getProperty("java.io.tmpdir")
        val lowerCase = System.getProperty("os.name").lowercase(Locale.getDefault())
        val property3 = System.getProperty("java.vm.name")
        var str = "unknown"

        if (property2 == null) {
            property2 = if (lowerCase.startsWith("windows")) "C:\\TEMP" else "/tmp"
        }

        if (lowerCase.startsWith("windows")) {
            str = if (property3.indexOf("64") != -1) {
                println("virt: Detected win 64bit jvm")
                "x86-win64"
            } else {
                println("virt: Detected win 32bit jvm")
                "x86-win32"
            }

            dllext = ".dll"
        } else if (lowerCase.startsWith("linux")) {
            str = if (property3.indexOf("64") != -1) {
                println("virt: Detected 64bit linux jvm")
                "x86-linux64"
            } else {
                println("virt: Detected 32bit linux jvm")
                "x86-linux32"
            }
        }

        val file = File(property2)
        if (!file.exists()) {
            file.mkdir()
        }

        if (!property2.endsWith(property)) {
            property2 = StringBuffer().append(property2).append(property).toString()
        }

        val stringBuffer =
            StringBuffer()
                .append(property2)
                .append("cpqma-")
                .append(Integer.toHexString(VirtDevs.UID))
                .append(dllext)
                .toString()

        println(StringBuffer().append("Checking for ").append(stringBuffer).toString())
        if (File(stringBuffer).exists()) {
            println("DLL present")
            dio_setup = 0
            return 0
        }

        println("DLL not present")
        val dllname = StringBuffer().append("com/hp/ilo2/virtdevs/cpqma-").append(str).toString()
        val dllExtract = dllExtract(dllname, stringBuffer)

        dio_setup = dllExtract

        return dllExtract
    }

    @Suppress("unused")
    companion object {
        const val CDROM = 5
        const val F3_120M_512 = 13
        const val F3_1Pt44_512 = 2
        const val F3_20Pt88_512 = 4
        const val F3_2Pt88_512 = 3
        const val F3_720_512 = 5
        const val F5_160_512 = 10
        const val F5_180_512 = 9
        const val F5_1Pt2_512 = 1
        const val F5_320_1024 = 8
        const val F5_320_512 = 7
        const val F5_360_512 = 6
        const val Fixed = 3
        const val FixedMedia = 12
        const val ImageFile = 100
        const val NoRootDir = 1
        const val Ramdisk = 6
        const val Remote = 4
        const val Removable = 2
        const val RemovableMedia = 11
        const val Unknown = 0

        var dllext = ""
        var dio_setup = -1

        fun cleanup() {
            val property = System.getProperty("file.separator")
            var property2 = System.getProperty("java.io.tmpdir")
            val lowerCase = System.getProperty("os.name").lowercase(Locale.getDefault())

            if (property2 == null) {
                property2 = if (lowerCase.startsWith("windows")) "C:\\TEMP" else "/tmp"
            }

            val list = File(property2).list().orEmpty()

            if (!property2.endsWith(property)) {
                property2 = StringBuffer().append(property2).append(property).toString()
            }

            for (i in list.indices) {
                if (list[i].startsWith("cpqma-") && list[i].endsWith(dllext)) {
                    File(StringBuffer().append(property2).append(list[i]).toString()).delete()
                }
            }
        }
    }
}
