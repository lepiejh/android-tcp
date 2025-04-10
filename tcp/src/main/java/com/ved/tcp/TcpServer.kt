package com.ved.tcp

import com.ved.framework.proguard.NotProguard
import com.ved.framework.utils.KLog
import com.ved.framework.utils.StringUtils
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.math.BigInteger
import java.net.ServerSocket
import java.net.Socket
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class TcpServer private constructor() {
    private var hasStart = false
    private var `is`: InputStream? = null
    private var os: OutputStream? = null
    @NotProguard
    var requestTaskManager = RequestManager()
    private var serverSocket: ServerSocket? = null
    private var socket: Socket? = null
    private var heartbeatTimer: ScheduledExecutorService? = null
    private var timerTask: TimerTask? = null

    companion object {
        val INSTANCE: TcpServer by lazy { Holder.INSTANCE }
    }

    private object Holder {
        val INSTANCE = TcpServer()
    }

    fun send(z: Boolean, str2: String?, port:Int,callBack: (z: Boolean, s: String?) -> Unit) {
        requestTaskManager.addTask(RequestEntity(z, str2, callBack))
        startServer(port)
    }

    private fun startServer(port:Int) {
        if (!hasStart) {
            hasStart = true
            Thread {
                while (true) {
                    val pollTask = requestTaskManager.pollTask()
                    if (pollTask != null) {
                        stopTimer()
                        if (heartbeatTimer == null || !(heartbeatTimer?.isShutdown == false && heartbeatTimer?.isTerminated == false)){
                            heartbeatTimer = Executors.newScheduledThreadPool(5)
                        }
                        if (timerTask == null) {
                            timerTask = object : TimerTask() {
                                override fun run() {
                                    stopServer()
                                }
                            }
                        }
                        heartbeatTimer?.scheduleAtFixedRate(timerTask, 0, 3, TimeUnit.SECONDS)
                        executeOneTask(pollTask,port)
                    }
                }
            }.start()
        }
    }

    private fun executeOneTask(requestBeen: RequestEntity,port:Int) {
        try {
            serverSocket = ServerSocket(port)
            socket = serverSocket?.accept()
            `is` = socket?.getInputStream()
            os = socket?.getOutputStream()
            if (socket != null && socket?.isConnected == true) {
                os?.write(requestBeen.reqData?.let { BigInteger(it, 16).toByteArray() })
                os?.flush()
            }
            val bArr = ByteArray(10240)
            val byteArrayToHexString2 =
                `is`?.read(bArr)?.let { StringUtils.byteArrayToHexString(charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'),bArr, it) }
            requestBeen.callBack(true, byteArrayToHexString2)
        } catch (e: Exception) {
            e.printStackTrace()
            requestBeen.callBack(false, e.message)
        }
        stopServer()
    }

    private fun stopStream() {
        try {
            if (os != null) {
                os?.close()
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        try {
            if (`is` != null) {
                `is`?.close()
            }
        } catch (e2: IOException) {
            e2.printStackTrace()
        }
        `is` = null
        os = null
    }

    fun stopServer() {
        stopStream()
        try {
            if (socket != null) {
                socket?.close()
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        try {
            if (serverSocket != null) {
                serverSocket?.close()
            }
        } catch (e2: IOException) {
            e2.printStackTrace()
        }
        socket = null
        serverSocket = null
    }

    fun stopTimer() {
        if (timerTask != null) {
            timerTask?.cancel()
            timerTask = null
        }
        if (heartbeatTimer != null) {
            heartbeatTimer?.shutdown()
            try {
                // 等待线程池终止
                if (heartbeatTimer?.awaitTermination(1, TimeUnit.SECONDS) == false) {
                    heartbeatTimer?.shutdownNow() // 强制终止
                }
            } catch (e: InterruptedException) {
                KLog.e( "Heartbeat shutdown interrupted :  ${e.message}")
                heartbeatTimer?.shutdownNow()
            }
            heartbeatTimer = null
        }
    }
}