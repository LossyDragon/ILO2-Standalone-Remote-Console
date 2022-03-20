package com.hp.ilo2.virtdevs

import com.hp.ilo2.remcons.Telnet

class VMD5() : Cloneable {

    @Suppress("unused")
    companion object {
        private const val S11 = 7
        private const val S12 = 12
        private const val S13 = 17
        private const val S14 = 22
        private const val S21 = 5
        private const val S22 = 9
        private const val S23 = 14
        private const val S24 = 20
        private const val S31 = 4
        private const val S32 = 11
        private const val S33 = 16
        private const val S34 = 23
        private const val S41 = 6
        private const val S42 = 10
        private const val S43 = 15
        private const val S44 = 21
    }

    private lateinit var buffer: ByteArray
    private lateinit var digestBits: ByteArray
    private lateinit var state: IntArray
    private lateinit var transformBuffer: IntArray
    private val algorithm: String? = null
    private var count: Long = 0

    init {
        init()
    }

    private constructor(vmd5: VMD5) : this() {
        state = IntArray(vmd5.state.size)
        System.arraycopy(vmd5.state, 0, state, 0, vmd5.state.size)
        transformBuffer = IntArray(vmd5.transformBuffer.size)
        System.arraycopy(vmd5.transformBuffer, 0, transformBuffer, 0, vmd5.transformBuffer.size)
        buffer = ByteArray(vmd5.buffer.size)
        System.arraycopy(vmd5.buffer, 0, buffer, 0, vmd5.buffer.size)
        digestBits = ByteArray(vmd5.digestBits.size)
        System.arraycopy(vmd5.digestBits, 0, digestBits, 0, vmd5.digestBits.size)
        count = vmd5.count
    }

    private fun F(i: Int, i2: Int, i3: Int): Int {
        return i and i2 or (i xor -1 and i3)
    }

    private fun G(i: Int, i2: Int, i3: Int): Int {
        return i and i3 or (i2 and (i3 xor -1))
    }

    private fun H(i: Int, i2: Int, i3: Int): Int {
        return i xor i2 xor i3
    }

    private fun I(i: Int, i2: Int, i3: Int): Int {
        return i2 xor (i or (i3 xor -1))
    }

    private fun rotateLeft(i: Int, i2: Int): Int {
        return i shl i2 or (i ushr 32 - i2)
    }

    private fun FF(i: Int, i2: Int, i3: Int, i4: Int, i5: Int, i6: Int, i7: Int): Int {
        return rotateLeft(i + F(i2, i3, i4) + i5 + i7, i6) + i2
    }

    private fun GG(i: Int, i2: Int, i3: Int, i4: Int, i5: Int, i6: Int, i7: Int): Int {
        return rotateLeft(i + G(i2, i3, i4) + i5 + i7, i6) + i2
    }

    private fun HH(i: Int, i2: Int, i3: Int, i4: Int, i5: Int, i6: Int, i7: Int): Int {
        return rotateLeft(i + H(i2, i3, i4) + i5 + i7, i6) + i2
    }

    private fun II(i: Int, i2: Int, i3: Int, i4: Int, i5: Int, i6: Int, i7: Int): Int {
        return rotateLeft(i + I(i2, i3, i4) + i5 + i7, i6) + i2
    }

    fun transform(bArr: ByteArray, i: Int) {
        val iArr = transformBuffer
        val i2 = state[0]
        val i3 = state[1]
        val i4 = state[2]
        val i5 = state[3]

        for (i6 in 0 until S33) {
            iArr[i6] = bArr[i6 * 4 + i].toInt() and Telnet.TELNET_IAC.toInt()
            for (i7 in 1..3) {
                iArr[i6] = iArr[i6] + (bArr[i6 * 4 + i7 + i].toInt() and Telnet.TELNET_IAC.toInt() shl i7 * 8)
            }
        }

        val FF = FF(i2, i3, i4, i5, iArr[0], 7, -680876936)
        val FF2 = FF(i5, FF, i3, i4, iArr[1], 12, -389564586)
        val FF3 = FF(i4, FF2, FF, i3, iArr[2], S13, 606105819)
        val FF4 = FF(i3, FF3, FF2, FF, iArr[3], S14, -1044525330)
        val FF5 = FF(FF, FF4, FF3, FF2, iArr[4], 7, -176418897)
        val FF6 = FF(FF2, FF5, FF4, FF3, iArr[5], 12, 1200080426)
        val FF7 = FF(FF3, FF6, FF5, FF4, iArr[6], S13, -1473231341)
        val FF8 = FF(FF4, FF7, FF6, FF5, iArr[7], S14, -45705983)
        val FF9 = FF(FF5, FF8, FF7, FF6, iArr[8], 7, 1770035416)
        val FF10 = FF(FF6, FF9, FF8, FF7, iArr[9], 12, -1958414417)
        val FF11 = FF(FF7, FF10, FF9, FF8, iArr[10], S13, -42063)
        val FF12 = FF(FF8, FF11, FF10, FF9, iArr[11], S14, -1990404162)
        val FF13 = FF(FF9, FF12, FF11, FF10, iArr[12], 7, 1804603682)
        val FF14 = FF(FF10, FF13, FF12, FF11, iArr[13], 12, -40341101)
        val FF15 = FF(FF11, FF14, FF13, FF12, iArr[S23], S13, -1502002290)
        val FF16 = FF(FF12, FF15, FF14, FF13, iArr[S43], S14, 1236535329)
        val GG = GG(FF13, FF16, FF15, FF14, iArr[1], 5, -165796510)
        val GG2 = GG(FF14, GG, FF16, FF15, iArr[6], 9, -1069501632)
        val GG3 = GG(FF15, GG2, GG, FF16, iArr[11], S23, 643717713)
        val GG4 = GG(FF16, GG3, GG2, GG, iArr[0], S24, -373897302)
        val GG5 = GG(GG, GG4, GG3, GG2, iArr[5], 5, -701558691)
        val GG6 = GG(GG2, GG5, GG4, GG3, iArr[10], 9, 38016083)
        val GG7 = GG(GG3, GG6, GG5, GG4, iArr[S43], S23, -660478335)
        val GG8 = GG(GG4, GG7, GG6, GG5, iArr[4], S24, -405537848)
        val GG9 = GG(GG5, GG8, GG7, GG6, iArr[9], 5, 568446438)
        val GG10 = GG(GG6, GG9, GG8, GG7, iArr[S23], 9, -1019803690)
        val GG11 = GG(GG7, GG10, GG9, GG8, iArr[3], S23, -187363961)
        val GG12 = GG(GG8, GG11, GG10, GG9, iArr[8], S24, 1163531501)
        val GG13 = GG(GG9, GG12, GG11, GG10, iArr[13], 5, -1444681467)
        val GG14 = GG(GG10, GG13, GG12, GG11, iArr[2], 9, -51403784)
        val GG15 = GG(GG11, GG14, GG13, GG12, iArr[7], S23, 1735328473)
        val GG16 = GG(GG12, GG15, GG14, GG13, iArr[12], S24, -1926607734)
        val HH = HH(GG13, GG16, GG15, GG14, iArr[5], 4, -378558)
        val HH2 = HH(GG14, HH, GG16, GG15, iArr[8], 11, -2022574463)
        val HH3 = HH(GG15, HH2, HH, GG16, iArr[11], S33, 1839030562)
        val HH4 = HH(GG16, HH3, HH2, HH, iArr[S23], 23, -35309556)
        val HH5 = HH(HH, HH4, HH3, HH2, iArr[1], 4, -1530992060)
        val HH6 = HH(HH2, HH5, HH4, HH3, iArr[4], 11, 1272893353)
        val HH7 = HH(HH3, HH6, HH5, HH4, iArr[7], S33, -155497632)
        val HH8 = HH(HH4, HH7, HH6, HH5, iArr[10], 23, -1094730640)
        val HH9 = HH(HH5, HH8, HH7, HH6, iArr[13], 4, 681279174)
        val HH10 = HH(HH6, HH9, HH8, HH7, iArr[0], 11, -358537222)
        val HH11 = HH(HH7, HH10, HH9, HH8, iArr[3], S33, -722521979)
        val HH12 = HH(HH8, HH11, HH10, HH9, iArr[6], 23, 76029189)
        val HH13 = HH(HH9, HH12, HH11, HH10, iArr[9], 4, -640364487)
        val HH14 = HH(HH10, HH13, HH12, HH11, iArr[12], 11, -421815835)
        val HH15 = HH(HH11, HH14, HH13, HH12, iArr[S43], S33, 530742520)
        val HH16 = HH(HH12, HH15, HH14, HH13, iArr[2], 23, -995338651)
        val II = II(HH13, HH16, HH15, HH14, iArr[0], 6, -198630844)
        val II2 = II(HH14, II, HH16, HH15, iArr[7], 10, 1126891415)
        val II3 = II(HH15, II2, II, HH16, iArr[S23], S43, -1416354905)
        val II4 = II(HH16, II3, II2, II, iArr[5], 21, -57434055)
        val II5 = II(II, II4, II3, II2, iArr[12], 6, 1700485571)
        val II6 = II(II2, II5, II4, II3, iArr[3], 10, -1894986606)
        val II7 = II(II3, II6, II5, II4, iArr[10], S43, -1051523)
        val II8 = II(II4, II7, II6, II5, iArr[1], 21, -2054922799)
        val II9 = II(II5, II8, II7, II6, iArr[8], 6, 1873313359)
        val II10 = II(II6, II9, II8, II7, iArr[S43], 10, -30611744)
        val II11 = II(II7, II10, II9, II8, iArr[6], S43, -1560198380)
        val II12 = II(II8, II11, II10, II9, iArr[13], 21, 1309151649)
        val II13 = II(II9, II12, II11, II10, iArr[4], 6, -145523070)
        val II14 = II(II10, II13, II12, II11, iArr[11], 10, -1120210379)
        val II15 = II(II11, II14, II13, II12, iArr[2], S43, 718787259)
        val II16 = II(II12, II15, II14, II13, iArr[9], 21, -343485551)
        val iArr2 = state
        iArr2[0] = iArr2[0] + II13
        val iArr3 = state
        iArr3[1] = iArr3[1] + II16
        val iArr4 = state
        iArr4[2] = iArr4[2] + II15
        val iArr5 = state
        iArr5[3] = iArr5[3] + II14
    }

    fun init() {
        state = IntArray(4)
        transformBuffer = IntArray(S33)
        buffer = ByteArray(64)
        digestBits = ByteArray(S33)
        count = 0L
        state[0] = 1732584193
        state[1] = -271733879
        state[2] = -1732584194
        state[3] = 271733878

        for (i in digestBits.indices) {
            digestBits[i] = 0
        }
    }

    fun engineReset() {
        init()
    }

    @Synchronized
    fun engineUpdate(b: Byte) {
        val i = (count ushr 3 and 63).toInt()

        count += 8
        buffer[i] = b

        if (i >= 63) {
            transform(buffer, 0)
        }
    }

    @Synchronized
    fun engineUpdate(bArr: ByteArray, i: Int, i2: Int) {
        var i2 = i2
        var i3 = i

        while (i2 > 0) {
            val i4 = (count ushr 3 and 63).toInt()

            if (i4 != 0 || i2 <= 64) {
                count += 8
                buffer[i4] = bArr[i3]

                if (i4 >= 63) {
                    transform(buffer, 0)
                }

                i3++
                i2--
            } else {
                count += 512

                transform(bArr, i3)

                i2 -= 64
                i3 += 64
            }
        }
    }

    private fun finish() {
        val bArr = ByteArray(8)

        for (i in 0..7) {
            bArr[i] = (count ushr i * 8 and 255).toByte()
        }

        val i2 = (count shr 3).toInt() and 63
        val bArr2 = ByteArray(if (i2 < 56) 56 - i2 else 120 - i2)

        bArr2[0] = Byte.MIN_VALUE
        engineUpdate(bArr2, 0, bArr2.size)
        engineUpdate(bArr, 0, bArr.size)

        for (i3 in 0..3) {
            for (i4 in 0..3) {
                digestBits[i3 * 4 + i4] = (state[i3] ushr i4 * 8 and Telnet.TELNET_IAC.toInt()).toByte()
            }
        }
    }

    fun engineDigest(): ByteArray {
        finish()
        val bArr = ByteArray(S33)
        System.arraycopy(digestBits, 0, bArr, 0, S33)
        init()
        return bArr
    }

    public override fun clone(): Any {
        return VMD5(this)
    }

    fun reset() {
        engineReset()
    }

    fun update(b: Byte) {
        engineUpdate(b)
    }

    fun update(bArr: ByteArray, i: Int, i2: Int) {
        engineUpdate(bArr, i, i2)
    }

    fun update(bArr: ByteArray) {
        engineUpdate(bArr, 0, bArr.size)
    }

    fun digest(): ByteArray {
        digestBits = engineDigest()
        return digestBits
    }
}
