package com.hp.ilo2.virtdevs

import java.awt.*

class VProgressBar : Canvas {

    private lateinit var offscreenG: Graphics
    private var offscreenImg: Image? = null
    private var percentage = 0f
    private var progressBackground: Color
    private var progressColor: Color
    private var progressHeight: Int
    private var progressWidth: Int

    @Suppress("unused")
    constructor(i: Int, i2: Int) {
        progressColor = Color.red
        progressBackground = Color.white
        font = Font("Dialog", 0, 15)
        progressWidth = i
        progressHeight = i2
        setSize(i, i2)
    }

    constructor(i: Int, i2: Int, color: Color?, color2: Color, color3: Color) {
        progressColor = Color.red
        progressBackground = Color.white
        font = Font("Dialog", 0, 12)
        progressWidth = i
        progressHeight = i2
        progressColor = color2
        progressBackground = color3
        setSize(i, i2)
        background = color
    }

    fun updateBar(f: Float) {
        percentage = f
        repaint()
    }

    fun setCanvasColor(color: Color?) {
        background = color
    }

    fun setProgressColor(color: Color) {
        progressColor = color
    }

    fun setBackGroundColor(color: Color) {
        progressBackground = color
    }

    override fun paint(graphics: Graphics) {
        if (offscreenImg == null) {
            offscreenImg = createImage(progressWidth - 4, progressHeight - 4)
        }

        val width = offscreenImg!!.getWidth(this)
        val height = offscreenImg!!.getHeight(this)

        offscreenG = offscreenImg!!.graphics
        offscreenG.color = progressBackground
        offscreenG.fillRect(0, 0, width, height)
        offscreenG.color = progressColor
        offscreenG.fillRect(0, 0, (width * percentage).toInt(), height)
        offscreenG.drawString(
            StringBuffer().append((percentage * 100.0f).toString()).append("%").toString(),
            width / 2 - 8,
            height / 2 + 5
        )
        offscreenG.clipRect(0, 0, (width * percentage).toInt(), height)
        offscreenG.color = progressBackground
        offscreenG.drawString(
            StringBuffer().append((percentage * 100.0f).toString()).append("%").toString(),
            width / 2 - 8,
            height / 2 + 5
        )
        graphics.color = progressBackground
        graphics.draw3DRect(size.width / 2 - progressWidth / 2, 0, progressWidth - 1, progressHeight - 1, false)
        graphics.drawImage(offscreenImg, 4 / 2, 4 / 2, this)
    }

    override fun update(graphics: Graphics) {
        paint(graphics)
    }
}
