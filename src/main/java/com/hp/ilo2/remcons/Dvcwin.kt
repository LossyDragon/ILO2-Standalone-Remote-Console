package com.hp.ilo2.remcons

import java.awt.*
import java.awt.image.ColorModel
import java.awt.image.DirectColorModel
import java.awt.image.MemoryImageSource
import java.lang.Exception
import kotlin.jvm.Synchronized

/**
 * Digital Video Capture Window
 */
class Dvcwin(private var screenX: Int, private var screenY: Int) : Canvas(), Runnable {

    companion object {
        private const val REFRESH_RATE = 60
    }

    private lateinit var pixelBuffer: IntArray
    private val directColorModel: ColorModel
    private val needToRefresh = 1
    private val paintCount = 0
    private val refreshCount = 0
    private var firstImage: Image? = null
    private var frametime = 0
    private var imageSource: MemoryImageSource? = null
    private var needToRefreshR = 1
    private var needToRefreshW = 1
    private var offscreenGc: Graphics? = null
    private var offscreenImage: Image? = null
    private var screenUpdater: Thread? = null
    private var updaterRunning = false

    init {
        directColorModel = DirectColorModel(32, 0xff0000, 0xff00, 0xff, 0)
        setFramerate(0)
    }

    override fun isFocusable(): Boolean {
        return true
    }

    override fun addNotify() {
        super.addNotify()

        if (offscreenImage == null) {
            offscreenImage = createImage(screenX, screenY)
        }
    }

    fun repaintIt(paramInt: Boolean): Boolean {
        var shouldRepaint = false

        if (paramInt) {
            needToRefreshW += 1
        } else {
            val i = needToRefreshW
            if (needToRefreshR != i) {
                needToRefreshR = i
                shouldRepaint = true
            }
        }

        return shouldRepaint
    }

    override fun paint(g: Graphics?) {

        if (g == null) {
            println("dvcwin.paint() g is null")
            return
        }

        if (offscreenImage != null) {
            g.drawImage(offscreenImage, 0, 0, this)
        }
    }

    override fun update(paramGraphics: Graphics) {
        if (offscreenImage == null) {
            offscreenImage = createImage(size.width, size.height)
            offscreenGc = offscreenImage!!.graphics
        }

        if (firstImage != null) {
            offscreenGc!!.drawImage(firstImage, 0, 0, this)
        }

        paramGraphics.drawImage(offscreenImage, 0, 0, this)
    }

    fun pasteArray(paramArrayOfInt: IntArray, paramInt1: Int, paramInt2: Int, paramInt3: Int) {
        val j: Int = if (paramInt2 + 16 > screenY) {
            screenY - paramInt2
        } else {
            16
        }

        for (i in 0 until j) {
            try {
                System.arraycopy(paramArrayOfInt, i * 16, pixelBuffer, (paramInt2 + i) * screenX + paramInt1, paramInt3)
            } catch (localException: Exception) {
                return
            }
        }

        imageSource!!.newPixels(paramInt1, paramInt2, paramInt3, 16, false)
    }

    fun setAbsDimensions(width: Int, height: Int) {
        if (width != screenX || height != screenY) {
            synchronized(this) {
                screenX = width
                screenY = height
            }

            offscreenImage = null
            pixelBuffer = IntArray(screenX * screenY)
            imageSource = MemoryImageSource(screenX, screenY, directColorModel, pixelBuffer, 0, screenX)
            imageSource!!.setAnimated(true)
            imageSource!!.setFullBufferUpdates(false)
            firstImage = createImage(imageSource)

            invalidate()

            var parent = parent
            if (parent != null) {
                while (parent!!.parent != null) {
                    parent.invalidate()
                    parent = parent.parent
                }
                parent.invalidate()
                parent.validate()
            }

            System.gc()
        }
    }

    override fun getPreferredSize(): Dimension {
        var localDimension: Dimension
        synchronized(this) { localDimension = Dimension(screenX, screenY) }
        return localDimension
    }

    override fun getMinimumSize(): Dimension {
        return preferredSize
    }

    fun showText(text: String) {
        if (screenUpdater == null) {
            return
        }

        if (screenX != 640 || screenY != 100) {
            setAbsDimensions(640, 100)
            imageSource = null
            firstImage = null
            offscreenImage = null
            offscreenImage = createImage(screenX, screenY)
        }

        val g = offscreenImage!!.graphics

        Color(0)

        g.color = Color.black
        g.fillRect(0, 0, screenX, screenY)

        val localFont = Font("Courier", Font.PLAIN, 20)

        Color(0)

        g.color = Color.white
        g.font = localFont
        g.drawString(text, 10, 20)
        g.drawImage(offscreenImage, 0, 0, this)
        g.dispose()

        System.gc()

        repaint()
    }

    fun setFramerate(rate: Int) {
        frametime = if (rate > 0)  1000 / rate else 1000 / 15
        println("Framerate: $rate / Frametime: $frametime")
    }

    override fun run() {
        while (updaterRunning) {
            try {
                Thread.sleep(frametime.toLong())
            } catch (ignored: InterruptedException) {
                /* no-op */
            }

            if (repaintIt(false)) {
                repaint()
            }
        }
    }

    @Synchronized
    fun startUpdates() {
        screenUpdater = Thread(this, "dvcwin")
        updaterRunning = true
        screenUpdater!!.start()
    }

    @Synchronized
    fun stopUpdates() {
        if (screenUpdater != null && screenUpdater!!.isAlive) {
            updaterRunning = false
        }

        screenX = 0
        screenY = 0
        screenUpdater = null
    }
}
