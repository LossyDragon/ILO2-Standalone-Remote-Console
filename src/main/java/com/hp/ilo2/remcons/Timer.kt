package com.hp.ilo2.remcons

import java.util.*

internal class Timer(
    private val timeoutMax: Int,
    private val oneShot: Boolean,
    private val mutex: Any
) : Runnable {

    companion object {
        private const val POLL_PERIOD = 50
    }

    internal enum class State {
        INIT, RUNNING, PAUSED, STOPPED
    }

    private var callback: TimerListener? = null
    private var callbackInfo: Any? = null
    private var startTimeMillis: Long = 0
    private var state = State.INIT
    private var stopTimeMillis: Long = 0
    private var timeoutCount = 0

    fun setListener(listener: TimerListener?, callbackInfo: Any?) {
        synchronized(mutex) {
            callback = listener
            this.callbackInfo = callbackInfo
        }
    }

    fun start() {
        synchronized(mutex) {
            when (state) {
                State.INIT -> {
                    state = State.RUNNING
                    timeoutCount = 0
                    Thread(this).start()
                }
                State.RUNNING -> timeoutCount = 0
                State.PAUSED -> {
                    timeoutCount = 0
                    state = State.RUNNING
                }
                State.STOPPED -> {
                    timeoutCount = 0
                    state = State.RUNNING
                }
            }
        }
    }

    fun stop() {
        synchronized(mutex) {
            if (state != State.INIT) {
                state = State.STOPPED
            }
        }
    }

    fun pause() {
        synchronized(mutex) {
            if (state == State.RUNNING) {
                state = State.PAUSED
            }
        }
    }

    fun cont() {
        synchronized(mutex) {
            if (state == State.PAUSED) {
                state = State.RUNNING
            }
        }
    }

    override fun run() {
        do {
            var date = Date()
            startTimeMillis = date.time

            try {
                Thread.sleep(POLL_PERIOD.toLong())
            } catch (ignored: InterruptedException) {
                /* no-op */
            }

            date = Date()
            stopTimeMillis = date.time
        } while (processState())
    }

    private fun processState(): Boolean {
        var shouldStop = true

        synchronized(mutex) {
            when (state) {
                State.INIT -> {}
                State.PAUSED -> {
                    if (stopTimeMillis > startTimeMillis) {
                        timeoutCount = (timeoutCount + (stopTimeMillis - startTimeMillis)).toInt()
                    } else timeoutCount += 50

                    if (timeoutCount >= timeoutMax) {
                        if (callback != null) {
                            callback!!.timeout(callbackInfo)
                        }

                        if (oneShot) {
                            state = State.INIT
                            shouldStop = false
                        } else {
                            timeoutCount = 0
                        }
                    }
                }
                State.RUNNING -> {}
                State.STOPPED -> {
                    state = State.INIT
                    shouldStop = false
                }
            }
        }

        return shouldStop
    }
}
