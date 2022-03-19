package com.hp.ilo2.remcons

import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.awt.event.MouseMotionListener

class MouseSync(private val mutex: Any) : MouseListener, MouseMotionListener, TimerListener {

    companion object {
        private const val CMD_ALIGN = 14
        private const val CMD_CLICK = 7
        private const val CMD_DRAG = 12
        private const val CMD_ENTER = 8
        private const val CMD_EXIT = 9
        private const val CMD_MOVE = 13
        private const val CMD_PRESS = 10
        private const val CMD_RELEASE = 11
        private const val CMD_SERVER_DISABLE = 5
        private const val CMD_SERVER_MOVE = 3
        private const val CMD_SERVER_SCREEN = 4
        private const val CMD_START = 0
        private const val CMD_STOP = 1
        private const val CMD_SYNC = 2
        private const val CMD_TIMEOUT = 6
        private const val MOUSE_BUTTON_CENTER = 2
        private const val MOUSE_BUTTON_LEFT = 4
        private const val MOUSE_BUTTON_RIGHT = 1
        private const val STATE_DISABLE = 3
        private const val STATE_ENABLE = 2
        private const val STATE_INIT = 0
        private const val STATE_SYNC = 1
        private const val SYNC_FAIL_COUNT = 4
        private const val SYNC_SUCCESS_COUNT = 2
        private const val TIMEOUT_DELAY = 5
        private const val TIMEOUT_MOVE = 200
        private const val TIMEOUT_SYNC = 2000
    }

    internal enum class Command {
        START, STOP, SYNC, SERVER_MOVE, SERVER_SCREEN, SERVER_DISABLE,
        TIMEOUT, CLICK, ENTER, EXIT, PRESS, RELEASE, DRAG, MOVE, ALIGN
    }

    internal enum class State {
        INIT, SYNC, ENABLE, DISABLE
    }

    private lateinit var recvDx: IntArray
    private lateinit var recvDy: IntArray
    private lateinit var sendDx: IntArray
    private lateinit var sendDy: IntArray
    private var clientDx = 0
    private var clientDy = 0
    private var clientX = 0
    private var clientY = 0
    private var debugMsgEnabled = false
    private var dragging = false
    private var listener: MouseSyncListener? = null
    private var pressedButton = 0
    private var sendDxCount = 0
    private var sendDxIndex = 0
    private var sendDxSuccess = 0
    private var sendDyCount = 0
    private var sendDyIndex = 0
    private var sendDySuccess = 0
    private var serverH = 0
    private var serverW = 0
    private var serverX = 0
    private var serverY = 0
    private var state: State
    private var syncSuccessful = false
    private var timer: Timer? = null

    init {
        state = State.INIT
        stateMachine(CMD_START, null, 0, 0)
    }

    fun setListener(listener: MouseSyncListener?) {
        this.listener = listener
    }

    fun enableDebug() {
        debugMsgEnabled = true
    }

    fun disableDebug() {
        debugMsgEnabled = false
    }

    fun restart() {
        goState(State.INIT)
    }

    fun align() {
        stateMachine(CMD_ALIGN, null, 0, 0)
    }

    fun sync() {
        stateMachine(CMD_SYNC, null, 0, 0)
    }

    @Suppress("UNUSED_PARAMETER")
    fun serverMoved(paramInt1: Int, paramInt2: Int, paramInt3: Int, paramInt4: Int) {
        stateMachine(CMD_SERVER_MOVE, null, paramInt1, paramInt2)
    }

    fun serverScreen(paramX: Int, paramY: Int) {
        stateMachine(CMD_SERVER_SCREEN, null, paramX, paramY)
    }

    private fun setServerScreenDimensions(height: Int, width: Int) {
        serverH = height
        serverW = width
    }

    fun serverDisabled() {
        stateMachine(CMD_SERVER_DISABLE, null, 0, 0)
    }

    override fun timeout(callbackInfo: Any?) {
        stateMachine(CMD_TIMEOUT, null, 0, 0)
    }

    override fun mouseClicked(event: MouseEvent) {
        stateMachine(CMD_CLICK, event, 0, 0)
    }

    override fun mouseEntered(event: MouseEvent) {
        // stateMachine(CMD_ENTER, event, 0, 0);
    }

    override fun mouseExited(event: MouseEvent) {
        stateMachine(CMD_EXIT, event, 0, 0)
    }

    override fun mousePressed(event: MouseEvent) {
        stateMachine(CMD_PRESS, event, 0, 0)
    }

    override fun mouseReleased(event: MouseEvent) {
        stateMachine(CMD_RELEASE, event, 0, 0)
    }

    override fun mouseDragged(event: MouseEvent) {
        stateMachine(CMD_DRAG, event, 0, 0)
        moveDelay()
    }

    override fun mouseMoved(event: MouseEvent) {
        stateMachine(CMD_MOVE, event, 0, 0)
        moveDelay()
    }

    private fun moveDelay() {
        try {
            Thread.sleep(TIMEOUT_DELAY.toLong())
        } catch (ignored: InterruptedException) {
            /* no-op */
        }
    }

    private fun syncDefault() {
        val arrayOfInt = intArrayOf(1, 4, 6, 8, 12, 16, 32, 64)

        sendDx = IntArray(arrayOfInt.size)
        sendDy = IntArray(arrayOfInt.size)
        recvDx = IntArray(arrayOfInt.size)
        recvDy = IntArray(arrayOfInt.size)

        System.arraycopy(arrayOfInt, 0, sendDx, 0, sendDx.size)
        System.arraycopy(arrayOfInt, 0, sendDy, 0, sendDy.size)
        System.arraycopy(arrayOfInt, 0, recvDx, 0, recvDx.size)
        System.arraycopy(arrayOfInt, 0, recvDy, 0, recvDy.size)

        sendDxIndex = 0
        sendDyIndex = 0
        sendDxCount = 0
        sendDyCount = 0
        sendDxSuccess = 0
        sendDySuccess = 0
        syncSuccessful = false
    }

    private fun syncContinue() {
        var i = 1
        var j = 1
        var k = 0
        var m = 0

        if (serverX > serverW / 2) {
            i = -1
        }

        if (serverY < serverH / 2) {
            j = -1
        }

        if (sendDxIndex >= 0) {
            k = i * sendDx[sendDxIndex]
        }

        if (sendDyIndex >= 0) {
            m = j * sendDy[sendDyIndex]
        }

        listener!!.serverMove(k, m, clientX, clientY)
        timer!!.start()
    }

    private fun syncUpdate(paramInt1: Int, paramInt2: Int) {
        timer!!.pause()

        var i = paramInt1 - serverX
        var j = serverY - paramInt2

        serverX = paramInt1
        serverY = paramInt2

        if (i < 0) {
            i = -i
        }

        if (j < 0) {
            j = -j
        }

        if (sendDxIndex >= 0) {
            if (recvDx[sendDxIndex] == i) {
                sendDxSuccess += 1
            }

            recvDx[sendDxIndex] = i
            sendDxCount += 1

            if (sendDxSuccess >= SYNC_SUCCESS_COUNT) {
                sendDxIndex -= 1
                sendDxSuccess = 0
                sendDxCount = 0
            } else if (sendDxCount >= SYNC_FAIL_COUNT) {
                if (debugMsgEnabled) {
                    println("no x sync:" + sendDx[sendDxIndex])
                }

                goState(State.ENABLE)

                return
            }
        }
        if (sendDyIndex >= 0) {
            if (recvDy[sendDyIndex] == j) {
                sendDySuccess += 1
            }

            recvDy[sendDyIndex] = j
            sendDyCount += 1

            if (sendDySuccess >= SYNC_SUCCESS_COUNT) {
                sendDyIndex -= 1
                sendDySuccess = 0
                sendDyCount = 0
            } else if (sendDyCount >= SYNC_FAIL_COUNT) {
                if (debugMsgEnabled) {
                    println("no y sync:" + sendDy[sendDyIndex])
                }

                goState(State.ENABLE)

                return
            }
        }

        if (sendDxIndex < 0 && sendDyIndex < 0) {
            for (k in sendDx.indices.reversed()) {
                if (recvDx[k] == 0 || recvDy[k] == 0) {
                    if (debugMsgEnabled) {
                        println("no movement:" + sendDx[k])
                    }

                    goState(State.ENABLE)

                    return
                }

                if (k != 0 && (recvDx[k] < recvDx[k - 1] || recvDy[k] < recvDy[k - 1])) {
                    if (debugMsgEnabled) {
                        println("not linear:" + sendDx[k])
                    }

                    goState(State.ENABLE)

                    return
                }
            }

            syncSuccessful = true
            sendDxIndex = 0
            sendDyIndex = 0

            goState(State.ENABLE)
        } else {
            syncContinue()
        }
    }

    private fun initVars() {
        serverW = 640
        serverH = 480
        serverX = 0
        serverY = 0
        clientX = 0
        clientY = 0
        clientDx = 0
        clientDy = 0
        pressedButton = 0
        dragging = false

        syncDefault()
    }

    @Suppress("UNUSED_PARAMETER")
    private fun moveServer(paramBoolean1: Boolean, paramBoolean2: Boolean) {
        var stepDx = 0
        var stepDy = 0
        var totalDxSentAbs = 0
        var totalDySentAbs = 0

        timer!!.pause()

        var dxAbs = clientDx
        var dyAbs = clientDy
        var signDx = 1

        if (clientDx < 0) {
            signDx = -1
            dxAbs = -clientDx
        }

        var signDy = 1
        if (clientDy < 0) {
            signDy = -1
            dyAbs = -clientDy
        }

        while (dxAbs != 0 || dyAbs != 0) {
            if (dxAbs != 0) {
                var i: Int = sendDx.size - 1
                while (i >= sendDxIndex) {
                    if (recvDx[i] <= dxAbs) {
                        stepDx = signDx * sendDx[i]
                        totalDxSentAbs += recvDx[i]
                        dxAbs -= recvDx[i]
                        break
                    }
                    i--
                }

                if (i < sendDxIndex) {
                    stepDx = 0
                    totalDxSentAbs += dxAbs
                    dxAbs = 0
                }
            } else {
                stepDx = 0
            }

            if (dyAbs != 0) {
                var i: Int = sendDy.size - 1
                while (i >= sendDyIndex) {
                    if (recvDy[i] <= dyAbs) {
                        stepDy = signDy * sendDy[i]
                        totalDySentAbs += recvDy[i]
                        dyAbs -= recvDy[i]
                        break
                    }
                    i--
                }

                if (i < sendDyIndex) {
                    stepDy = 0
                    totalDySentAbs += dyAbs
                    dyAbs = 0
                }
            } else {
                stepDy = 0
            }

            if (stepDx != 0 || stepDy != 0) {
                listener!!.serverMove(stepDx, stepDy, clientX, clientY)
            }
        }

        clientDx -= signDx * totalDxSentAbs
        clientDy -= signDy * totalDySentAbs

        if (!paramBoolean2) {
            serverX += signDx * totalDxSentAbs
            serverY -= signDy * totalDySentAbs
            if (debugMsgEnabled) {
                println("Server:$serverX,$serverY")
            }
        }

        if (clientDx != 0 || clientDy != 0) {
            timer!!.start()
        }
    }

    private fun goState(state: State) {
        synchronized(mutex) {
            stateMachine(CMD_STOP, null, 0, 0)
            this.state = state
            stateMachine(CMD_START, null, 0, 0)
        }
    }

    private fun stateMachine(command: Int, mouseEvent: MouseEvent?, paramX: Int, paramY: Int) {
        synchronized(mutex) {
            when (state) {
                State.INIT -> stateInit(command, mouseEvent, paramX, paramY)
                State.SYNC -> stateSync(command, mouseEvent, paramX, paramY)
                State.ENABLE -> stateEnable(command, mouseEvent, paramX, paramY)
                State.DISABLE -> stateDisable(command, mouseEvent, paramX, paramY)
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun stateInit(command: Int, mouseEvent: MouseEvent?, paramInt2: Int, paramInt3: Int) {
        if (command == CMD_START) {
            initVars()
            goState(State.DISABLE)
        }
    }

    private fun stateSync(command: Int, mouseEvent: MouseEvent?, paramInt2: Int, paramInt3: Int) {
        when (command) {
            CMD_START -> {
                timer = Timer(TIMEOUT_SYNC, false, mutex)
                timer!!.setListener(this, null)

                syncDefault()

                sendDxIndex = sendDx.size - 1
                sendDyIndex = sendDy.size - 1

                syncContinue()
            }
            CMD_STOP -> {
                timer!!.stop()
                timer = null

                if (!syncSuccessful) {
                    if (debugMsgEnabled) {
                        println("fail")
                    }

                    syncDefault()
                } else if (debugMsgEnabled) {
                    println("success")
                }

                if (debugMsgEnabled) {
                    run {
                        var i = 0
                        while (i < this.sendDx.size) {
                            println(this.recvDx[i])
                            i++
                        }
                    }

                    var i = 0
                    while (i < sendDx.size) {
                        println(recvDy[i])
                        i++
                    }
                }
            }
            CMD_SYNC -> goState(State.SYNC)
            CMD_SERVER_MOVE -> {
                if (paramInt2 > 2000 || paramInt3 > 2000) {
                    goState(State.DISABLE)
                } else {
                    syncUpdate(paramInt2, paramInt3)
                }
            }
            CMD_SERVER_SCREEN -> setServerScreenDimensions(paramInt2, paramInt3)
            CMD_SERVER_DISABLE -> goState(State.DISABLE)
            CMD_TIMEOUT -> goState(State.ENABLE)
            CMD_ENTER,
            CMD_EXIT,
            CMD_DRAG,
            CMD_MOVE -> {
                clientX = mouseEvent!!.x
                clientY = mouseEvent.y
            }
        }
    }

    private fun stateEnable(command: Int, mouseEvent: MouseEvent?, paramX: Int, paramY: Int) {
        when (command) {
            CMD_START -> {
                if (debugMsgEnabled) {
                    println("enable")
                }

                timer = Timer(TIMEOUT_MOVE, false, mutex)
                timer!!.setListener(this, null)
            }
            CMD_STOP -> {
                timer!!.stop()
                timer = null
            }
            CMD_SYNC -> goState(State.SYNC)
            CMD_SERVER_MOVE -> {
                if (debugMsgEnabled) {
                    println("Server:$paramX,$paramY")
                }

                if (paramX > 2000 || paramY > 2000) {
                    goState(State.DISABLE)
                } else {
                    serverX = paramX
                    serverY = paramY
                }
            }
            CMD_SERVER_SCREEN -> setServerScreenDimensions(paramX, paramY)
            CMD_SERVER_DISABLE -> goState(State.DISABLE)
            CMD_ALIGN -> {
                clientDx = clientX - serverX
                clientDy = serverY - clientY

                moveServer(paramBoolean1 = true, paramBoolean2 = true)
            }
            CMD_TIMEOUT -> moveServer(paramBoolean1 = true, paramBoolean2 = true)
            CMD_ENTER, CMD_EXIT -> {
                clientX = mouseEvent!!.x
                clientY = mouseEvent.y

                if (clientX < 0) {
                    clientX = 0
                }

                if (clientX > serverW) {
                    clientX = serverW
                }

                if (clientY < 0) {
                    clientY = 0
                }

                if (clientY > serverH) {
                    clientY = serverH
                }

                if (debugMsgEnabled) {
                    println("eClient:$clientX,$clientY")
                }

                if (pressedButton != MOUSE_BUTTON_RIGHT && mouseEvent.modifiersEx and 0x2 == 0) {
                    align()
                }
            }
            CMD_DRAG -> {
                if (pressedButton != MOUSE_BUTTON_RIGHT) {
                    if (pressedButton > 0) {
                        pressedButton = -pressedButton
                        listener!!.serverPress(pressedButton)
                    }

                    clientDx += mouseEvent!!.x - clientX
                    clientDy += clientY - mouseEvent.y

                    moveServer(paramBoolean1 = false, paramBoolean2 = true)
                }

                clientX = mouseEvent!!.x
                clientY = mouseEvent.y

                if (debugMsgEnabled) {
                    println("Client:$clientX,$clientY")
                }

                dragging = true
            }
            CMD_MOVE -> {
                if (mouseEvent!!.modifiersEx and InputEvent.CTRL_DOWN_MASK == 0) {
                    clientDx += mouseEvent.x - clientX
                    clientDy += clientY - mouseEvent.y
                    moveServer(paramBoolean1 = false, paramBoolean2 = true)
                }

                clientX = mouseEvent.x
                clientY = mouseEvent.y

                if (debugMsgEnabled) {
                    println("Client:$clientX,$clientY")
                }
            }
            CMD_PRESS -> handleCmdPress(mouseEvent)
            CMD_RELEASE -> {
                handleCmdRelease()
                pressedButton = 0
            }
            CMD_CLICK -> handleCmdClick(mouseEvent)
        }
    }

    private fun stateDisable(command: Int, mouseEvent: MouseEvent?, paramInt2: Int, paramInt3: Int) {
        when (command) {
            CMD_START -> {
                if (debugMsgEnabled) {
                    println("disable")
                }

                timer = Timer(TIMEOUT_MOVE, false, mutex)
                timer!!.setListener(this, null)
            }
            CMD_STOP -> {
                timer!!.stop()
                timer = null
            }
            CMD_SYNC -> syncDefault()
            CMD_SERVER_MOVE -> {
                if (debugMsgEnabled) {
                    println("Server:$paramInt2,$paramInt3")
                }

                if (paramInt2 < 2000 && paramInt3 < 2000) {
                    serverX = paramInt2
                    serverY = paramInt3

                    goState(State.ENABLE)
                }
            }
            CMD_SERVER_SCREEN -> setServerScreenDimensions(paramInt2, paramInt3)
            CMD_SERVER_DISABLE -> {}
            CMD_ALIGN -> {
                clientDx = clientX - serverX
                clientDy = serverY - clientY
                moveServer(paramBoolean1 = true, paramBoolean2 = false)
            }
            CMD_TIMEOUT -> moveServer(paramBoolean1 = true, paramBoolean2 = false)
            CMD_ENTER, CMD_EXIT -> {
                clientX = mouseEvent!!.x
                clientY = mouseEvent.y

                if (clientX < 0) {
                    clientX = 0
                }

                if (clientX > serverW) {
                    clientX = serverW
                }

                if (clientY < 0) {
                    clientY = 0
                }

                if (clientY > serverH) {
                    clientY = serverH
                }

                if (debugMsgEnabled) {
                    println("eClient:$clientX,$clientY")
                }

                if (pressedButton != 1 && mouseEvent.modifiersEx and 0x2 == 0) {
                    align()
                }
            }
            CMD_DRAG -> {
                if (pressedButton != MOUSE_BUTTON_RIGHT) {
                    if (pressedButton > 0) {
                        pressedButton = -pressedButton
                        listener!!.serverPress(pressedButton)
                    }

                    clientDx += mouseEvent!!.x - clientX
                    clientDy += clientY - mouseEvent.y

                    moveServer(paramBoolean1 = false, paramBoolean2 = false)
                } else {
                    serverX = mouseEvent!!.x
                    serverY = mouseEvent.y
                }

                clientX = mouseEvent.x
                clientY = mouseEvent.y

                if (debugMsgEnabled) {
                    println("Client:$clientX,$clientY")
                }

                dragging = true
            }
            CMD_MOVE -> {
                if (mouseEvent!!.modifiersEx and 0x2 == 0) {
                    clientDx += mouseEvent.x - clientX
                    clientDy += clientY - mouseEvent.y

                    moveServer(paramBoolean1 = false, paramBoolean2 = false)
                } else {
                    serverX = mouseEvent.x
                    serverY = mouseEvent.y
                }

                clientX = mouseEvent.x
                clientY = mouseEvent.y

                if (debugMsgEnabled) {
                    println("Client:$clientX,$clientY")
                }
            }
            CMD_PRESS -> handleCmdPress(mouseEvent)
            CMD_RELEASE -> {
                handleCmdRelease()
                pressedButton = 0
            }
            CMD_CLICK -> handleCmdClick(mouseEvent)
        }
    }

    private fun handleCmdPress(mouseEvent: MouseEvent?) {
        if (pressedButton == 0) {
            pressedButton = if (mouseEvent!!.modifiersEx and 0x4 != 0) {
                MOUSE_BUTTON_RIGHT
            } else if (mouseEvent.modifiersEx and 0x8 != 0) {
                MOUSE_BUTTON_CENTER
            } else {
                MOUSE_BUTTON_LEFT
            }

            dragging = false
        }
    }

    private fun handleCmdRelease() {
        when (pressedButton) {
            -MOUSE_BUTTON_LEFT -> listener!!.serverRelease(MOUSE_BUTTON_LEFT)
            -MOUSE_BUTTON_CENTER -> listener!!.serverRelease(MOUSE_BUTTON_CENTER)
            -MOUSE_BUTTON_RIGHT -> listener!!.serverRelease(MOUSE_BUTTON_RIGHT)
        }
    }

    private fun handleCmdClick(mouseEvent: MouseEvent?) {
        if (!dragging) {
            if (mouseEvent!!.modifiersEx and 0x10 != 0) {
                listener!!.serverClick(MOUSE_BUTTON_LEFT, 1)
            } else if (mouseEvent.modifiersEx and 0x8 != 0) {
                listener!!.serverClick(MOUSE_BUTTON_CENTER, 1)
            } else if (mouseEvent.modifiersEx and 0x4 != 0) {
                listener!!.serverClick(MOUSE_BUTTON_RIGHT, 1)
            }
        }
    }
}
