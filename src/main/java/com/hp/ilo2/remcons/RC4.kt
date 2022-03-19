package com.hp.ilo2.remcons

import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import kotlin.Throws

/**
 * Rivest Cipher 4
 */
class RC4 internal constructor(key: ByteArray) {

    private val key = byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
    private val keyBox = ByteArray(0x100)
    private val pre = ByteArray(16)
    private val sBox = ByteArray(0x100)
    private var i = 0
    private var j = 0

    init {
        System.arraycopy(key, 0, pre, 0, 16)

        try {
            updateKey()
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
        }
    }

    @Throws(NoSuchAlgorithmException::class)
    fun updateKey() {
        val md = MessageDigest.getInstance("MD5")
        md.update(pre)
        md.update(key)

        val digest = md.digest()
        System.arraycopy(digest, 0, key, 0, key.size)

        for (k in 0..255) {
            sBox[k] = (k and 0xFF).toByte()
            keyBox[k] = key[k % 16]
        }

        j = 0
        i = 0

        while (i < 256) {
            j = (j and 0xFF) + (sBox[i].toInt() and 0xFF) + (keyBox[i].toInt() and 0xFF) and 0xFF
            val m = sBox[i].toInt()
            sBox[i] = sBox[j]
            sBox[j] = m.toByte()
            i += 1
        }

        i = 0
        j = 0
    }

    fun randomValue(): Int {
        i = (i and 0xFF) + 1 and 0xFF
        j = (j and 0xFF) + (sBox[i].toInt() and 0xFF) and 0xFF

        val k = sBox[i].toInt()

        sBox[i] = sBox[j]
        sBox[j] = k.toByte()

        val m: Int = (sBox[i].toInt() and 0xFF) + (sBox[j].toInt() and 0xFF) and 0xFF

        return sBox[m].toInt()
    }
}
