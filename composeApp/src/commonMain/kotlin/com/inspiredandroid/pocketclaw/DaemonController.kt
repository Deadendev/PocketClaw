package com.inspiredandroid.pocketclaw

interface DaemonController {
    fun start()
    fun stop()
}

expect fun createDaemonController(): DaemonController
