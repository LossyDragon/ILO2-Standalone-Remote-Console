package com.hp.ilo2.remcons

interface MouseSyncListener {
    fun serverMove(paramInt1: Int, paramInt2: Int, clientX: Int, clientY: Int)
    fun serverPress(button: Int)
    fun serverRelease(button: Int)
    fun serverClick(button: Int, paramInt2: Int)
}
