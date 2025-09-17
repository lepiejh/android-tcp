package com.ved.tcp

import com.ved.framework.utils.KLog
import com.ved.framework.utils.StringUtils
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

class TcpServer private constructor() {
    private var `is`: InputStream? = null
    private var os: OutputStream? = null
    var requestTaskManager = RequestManager()
    private var serverSocket: ServerSocket? = null
    private var socket: Socket? = null
    private val lock = Any()
    private val executor = Executors.newSingleThreadExecutor()

    companion object {
        val INSTANCE: TcpServer by lazy { Holder.INSTANCE }
    }

    private object Holder {
        val INSTANCE = TcpServer()
    }

    fun send(z: Boolean, h: Boolean, w: Boolean, r: Boolean, m: Boolean, d: Boolean, cc: Boolean, st: Boolean, t: Int, c: String?, u: String?, p: Int, ce: Boolean, dm: Long, sm: Long, s: List<String>?, callBack: (z: Boolean, s: String?) -> Unit) {
        requestTaskManager.addTask(RequestEntity(z, h, w, r, m, d, cc, st, t, c, u, p, ce, dm, sm, s, callBack))
        executor.execute {
            val pollTask = requestTaskManager.pollTask()
            if (pollTask != null) {
                executeOneTask(pollTask)
            }
        }
    }

    private fun executeOneTask(requestBeen: RequestEntity) {
        try {
            if (requestBeen.url.isNullOrEmpty()) {
                // Server mode
                serverSocket = ServerSocket(requestBeen.port).apply {
                    if (requestBeen.timeout > 0) {
                        soTimeout = requestBeen.timeout * 1000
                    }
                    reuseAddress = true
                }
                socket = serverSocket?.accept().apply {
                    if (requestBeen.timeout > 0) {
                        this?.soTimeout = requestBeen.timeout * 1000
                    }
                }
            } else {
                // Client mode
                socket = Socket(requestBeen.url, requestBeen.port).apply {
                    if (requestBeen.timeout > 0) {
                        soTimeout = requestBeen.timeout * 1000
                    }
                }
            }

            `is` = socket?.getInputStream()
            os = socket?.getOutputStream()

            if (socket != null && socket?.isConnected == true) {
                if (requestBeen.reqData?.isNotEmpty() == true) {
                    if (requestBeen.delay) {
                        Thread.sleep(requestBeen.delayMillis)
                    }

                    val responses = mutableSetOf<String>()
                    var previousValue: String? = null

                    requestBeen.reqData.forEachIndexed { index, data ->
                        // 关键修改：在每次发送前彻底清空输入缓冲区
                        clearInputStreamCompletely()

                        val dataToSend = if (requestBeen.change && previousValue?.isNotEmpty() == true) {
                            "${data}${if ((StringUtils.hexStringToByteArray(previousValue)[4].toInt() and 0xFF) == 0) "01" else "00"}"
                        } else {
                            data
                        }

                        write(requestBeen, dataToSend)
                        os?.flush()

                        // 等待数据发送完成
                        Thread.sleep(50)

                        // 读取响应，设置明确的读取超时
                        val response = readResponse(requestBeen.timeout * 1000L)
                        if (response.isNotEmpty()) {
                            responses.add(response)
                            previousValue = response
                        } else {
                            KLog.w("No response received for data: $dataToSend")
                        }

                        if (requestBeen.stop && index != requestBeen.reqData.size - 1) {
                            Thread.sleep(requestBeen.stopMillis)
                        }
                    }

                    requestBeen.callBack(true, responses.joinToString(","))
                }
            }
        } catch (e: Exception) {
            requestBeen.callBack(false, "Error: ${e.message}")
            KLog.e("Execute task error: ${e.message}")
        } finally {
            stopServer()
        }
    }

    /**
     * 彻底清空输入缓冲区，防止旧数据干扰
     */
    private fun clearInputStreamCompletely() {
        try {
            var available: Int
            val buffer = ByteArray(1024)
            do {
                available = `is`?.available() ?: 0
                if (available > 0) {
                    val bytesRead = `is`?.read(buffer, 0, minOf(available, buffer.size)) ?: 0
                    if (bytesRead > 0) {
                        val discardedData = StringUtils.byteArrayToHexString(buffer.copyOf(bytesRead))
                        KLog.w("Cleared leftover data from buffer: $discardedData")
                    }
                }
            } while (available > 0)
        } catch (e: Exception) {
            KLog.e("Clear input stream error: ${e.message}")
        }
    }

    /**
     * 读取响应数据，确保只读取当前请求的响应
     */
    private fun readResponse(timeoutMs: Long): String {
        val startTime = System.currentTimeMillis()
        val responseBuilder = StringBuilder()
        val buffer = ByteArray(1024)

        try {
            // 等待数据到达或超时
            while (System.currentTimeMillis() - startTime < timeoutMs) {
                val available = `is`?.available() ?: 0
                if (available > 0) {
                    // 读取可用数据
                    val bytesRead = `is`?.read(buffer) ?: -1
                    if (bytesRead > 0) {
                        val chunk = StringUtils.byteArrayToHexString(buffer.copyOf(bytesRead))
                        responseBuilder.append(chunk)
                        KLog.d("Received chunk: $chunk")

                        // 如果读取的数据少于缓冲区大小，说明可能已经读取完所有数据
                        if (bytesRead < buffer.size) {
                            break
                        }
                    } else if (bytesRead == -1) {
                        break // 流结束
                    }
                } else {
                    // 如果没有数据，等待一小段时间再检查
                    Thread.sleep(10)
                }
            }
        } catch (e: Exception) {
            KLog.e("Read response error: ${e.message}")
        }

        val response = StringUtils.trim(responseBuilder.toString())
        KLog.i("Final response: $response")
        return response
    }

    private fun write(requestBeen: RequestEntity, data: String) {
        val d = if (requestBeen.crc) {
            "${data}${StringUtils.getCRC(data, "2X")}"
        } else {
            data
        }
        KLog.i("开始发送实际的指令： $d")
        try {
            val byteArray = StringUtils.hexStringToByteArray(d)
            os?.write(byteArray)
            KLog.d("Sent ${byteArray.size} bytes")
        } catch (e: Exception) {
            KLog.e("Write error: ${e.message}")
        }
    }

    private fun stopStream() {
        try {
            os?.close()
        } catch (e: IOException) {
            KLog.e("OutputStream close error: ${e.message}")
        }
        try {
            `is`?.close()
        } catch (e: IOException) {
            KLog.e("InputStream close error: ${e.message}")
        }
        `is` = null
        os = null
    }

    fun stopServer() {
        synchronized(lock) {
            stopStream()
            try {
                socket?.close()
            } catch (e: IOException) {
                KLog.e("Socket close error: ${e.message}")
            }
            try {
                serverSocket?.close()
            } catch (e: IOException) {
                KLog.e("ServerSocket close error: ${e.message}")
            }
            socket = null
            serverSocket = null
        }
    }
}