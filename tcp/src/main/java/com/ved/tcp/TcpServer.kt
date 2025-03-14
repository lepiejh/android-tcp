package com.ved.tcp

import com.ved.framework.utils.StringUtils
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.*

class TcpServer private constructor() {
    private var hasStart = false
    private var `is`: InputStream? = null
    private var os: OutputStream? = null
    var requestTaskManager = RequestManager()
    private var serverSocket: ServerSocket? = null
    private var socket: Socket? = null
    private val timer = Timer()
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
                        if (timerTask != null) {
                            timerTask?.cancel()
                        }
                        timerTask = object : TimerTask() {
                            override fun run() {
                                stopServer()
                            }
                        }
                        timer.schedule(timerTask, 3000)
                        executeOneTask(pollTask,port)
                    }
                }
            }.start()
        }
    }

    private fun executeOneTask(requestBeen: RequestEntity,port:Int) {
        try {
            serverSocket = ServerSocket()
            serverSocket?.reuseAddress = true
            serverSocket?.bind(InetSocketAddress(port))
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
}