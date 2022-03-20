package com.hp.ilo2.virtdevs

import java.io.FileOutputStream
import java.io.PrintStream

@Suppress("unused")
object D {

    const val NONE = -1
    const val FATAL = 0
    const val INFORM = 1
    const val WARNING = 2
    const val VERBOSE = 3

    private var out: PrintStream? = null
    var debug = 0

    init {
        debug = 0

        try {
            val property: String? = VirtDevs.prop?.getProperty("debugfile")
            out = if (property == null) {
                System.out
            } else {
                PrintStream(FileOutputStream(property))
            }
        } catch (e: Exception) {
            out = System.out
            out?.println(StringBuffer().append("Exception trying to open debug trace\n").append(e).toString())
        }

        val property2: String? = VirtDevs.prop?.getProperty("debug")
        if (property2 != null) {
            debug = Integer.valueOf(property2).toInt()
        }
    }

    fun println(i: Int, str: String?) {
        if (debug >= i) {
            out!!.println(str)
        }
    }

    fun print(i: Int, str: String?) {
        if (debug >= i) {
            out!!.println(str)
        }
    }

    fun hex(b: Byte, i: Int): String {
        return hex(b.toInt() and 255, i)
    }

    fun hex(s: Short, i: Int): String {
        return hex(s.toInt() and 65535, i)
    }

    fun hex(i: Int, i2: Int): String {
        var hexString = Integer.toHexString(i)

        while (hexString.length < i2) {
            hexString = StringBuffer().append("0").append(hexString).toString()
        }

        return hexString
    }

    fun hex(j: Long, i: Int): String {
        var hexString = java.lang.Long.toHexString(j)

        while (hexString.length < i) {
            hexString = StringBuffer().append("0").append(hexString).toString()
        }

        return hexString
    }

    fun hexdump(i: Int, bArr: ByteArray?, i2: Int) {
        var i2 = i2

        if (debug >= i) {
            if (i2 == 0) {
                i2 = bArr!!.size
            }

            for (i3 in 0 until i2) {
                if (i3 % 16 == 0) {
                    out!!.print("\n")
                }

                out!!.print(StringBuffer().append(hex(bArr!![i3], 2)).append(" ").toString())
            }

            out!!.print("\n")
        }
    }
}
