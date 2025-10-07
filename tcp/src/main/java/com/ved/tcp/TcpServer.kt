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
        val responses = mutableListOf<String>()

        try {
            KLog.i("=== Starting TCP Task ===")
            KLog.i("URL: ${requestBeen.url}, Port: ${requestBeen.port}")
            KLog.i("Timeout: ${requestBeen.timeout}s")
            KLog.i("Data to send: ${requestBeen.reqData}")

            if (requestBeen.url.isNullOrEmpty()) {
                // Server模式 - 监听端口等待客户端连接
                KLog.i("Starting TCP Server mode on port ${requestBeen.port}")
                serverSocket = ServerSocket(requestBeen.port).apply {
                    reuseAddress = true
                    if (requestBeen.timeout > 0) {
                        soTimeout = requestBeen.timeout * 1000
                    }
                }
                KLog.i("ServerSocket created, waiting for client connection...")

                socket = serverSocket?.accept().apply {
                    if (requestBeen.timeout > 0) {
                        this?.soTimeout = requestBeen.timeout * 1000
                    }
                }
                KLog.i("Client connected: ${socket?.inetAddress?.hostAddress}:${socket?.port}")
            } else {
                // Client模式 - 主动连接到远程服务器
                KLog.i("Starting TCP Client mode, connecting to ${requestBeen.url}:${requestBeen.port}")
                socket = Socket().apply {
                    if (requestBeen.timeout > 0) {
                        soTimeout = requestBeen.timeout * 1000
                    }
                    // 连接到目标服务器
                    connect(java.net.InetSocketAddress(requestBeen.url, requestBeen.port), requestBeen.timeout * 1000)
                    tcpNoDelay = true
                    keepAlive = true
                }

                KLog.i("Connected to server successfully")
                KLog.i("Remote: ${socket?.inetAddress?.hostAddress}:${socket?.port}")
                KLog.i("Local: ${socket?.localAddress?.hostAddress}:${socket?.localPort}")
            }

            `is` = socket?.getInputStream()
            os = socket?.getOutputStream()

            KLog.i("Streams initialized successfully")
            KLog.i("Socket connected: ${socket?.isConnected}")

            if (socket != null && socket?.isConnected == true && requestBeen.reqData?.isNotEmpty() == true) {
                // 可选：延迟发送
                if (requestBeen.delay) {
                    KLog.i("Delaying for ${requestBeen.delayMillis}ms")
                    Thread.sleep(requestBeen.delayMillis)
                }

                var previousValue: String? = null

                requestBeen.reqData.forEachIndexed { index, data ->
                    KLog.i("--- Processing command ${index + 1}/${requestBeen.reqData.size} ---")
                    KLog.i("Original data: $data")

                    // 清空输入缓冲区（如果有残留数据）
                    clearInputStreamCompletely()

                    // 处理数据（如果需要根据前一个响应修改）
                    val dataToSend = if (requestBeen.change && previousValue?.isNotEmpty() == true) {
                        "${data}${if ((StringUtils.hexStringToByteArray(previousValue)[4].toInt() and 0xFF) == 0) "01" else "00"}"
                    } else {
                        data
                    }

                    KLog.i("Final data to send: $dataToSend")

                    // 发送数据
                    write(requestBeen, dataToSend)
                    os?.flush()
                    KLog.i("Data sent successfully")

                    // 读取响应
                    KLog.i("Waiting for response...")
                    val response = readResponse(requestBeen.timeout * 1000L)

                    if (response.isNotEmpty()) {
                        KLog.i("Response received: $response")
                        responses.add(response)
                        previousValue = response
                    } else {
                        KLog.w("No response received for command: $dataToSend")
                    }

                    // 可选：命令间停止
                    if (requestBeen.stop && index != requestBeen.reqData.size - 1) {
                        KLog.i("Stopping for ${requestBeen.stopMillis}ms between commands")
                        Thread.sleep(requestBeen.stopMillis)
                    }
                }

                // 所有命令执行完成，回调成功
                requestBeen.callBack(true, responses.joinToString(","))
                KLog.i("=== TCP Task Completed Successfully ===")
            } else {
                val errorMsg = when {
                    socket == null -> "Socket is null"
                    !socket!!.isConnected -> "Socket not connected"
                    requestBeen.reqData.isNullOrEmpty() -> "No data to send"
                    else -> "Unknown connection error"
                }
                KLog.e("Connection failed: $errorMsg")
                requestBeen.callBack(false, errorMsg)
            }
        } catch (e: IOException) {
            KLog.e("Network error: ${e.message}")
            requestBeen.callBack(false, "Network error: ${e.message}")
        } catch (e: Exception) {
            KLog.e("Unexpected error: ${e.message}")
            e.printStackTrace()
            requestBeen.callBack(false, "Error: ${e.message}")
        } finally {
            stopServer()
        }
    }

    /**
     * 彻底清空输入缓冲区
     */
    private fun clearInputStreamCompletely() {
        try {
            var totalCleared = 0
            val buffer = ByteArray(1024)
            var available: Int

            do {
                available = `is`?.available() ?: 0
                if (available > 0) {
                    val bytesRead = `is`?.read(buffer, 0, minOf(available, buffer.size)) ?: 0
                    if (bytesRead > 0) {
                        totalCleared += bytesRead
                        val discardedData = StringUtils.byteArrayToHexString(buffer.copyOf(bytesRead))
                        KLog.w("Cleared $bytesRead bytes from buffer: $discardedData")
                    }
                }
            } while (available > 0)

            if (totalCleared > 0) {
                KLog.w("Total cleared $totalCleared bytes from input buffer")
            }
        } catch (e: Exception) {
            KLog.e("Clear input stream error: ${e.message}")
        }
    }

    /**
     * 读取响应数据
     */
    private fun readResponse(timeoutMs: Long): String {
        val startTime = System.currentTimeMillis()
        val responseBuffer = mutableListOf<Byte>()
        val tempBuffer = ByteArray(256)

        try {
            KLog.d("Starting response read with timeout: ${timeoutMs}ms")

            // 等待数据到达的超时循环
            while (System.currentTimeMillis() - startTime < timeoutMs) {
                val available = `is`?.available() ?: 0

                if (available > 0) {
                    val bytesRead = `is`?.read(tempBuffer) ?: -1
                    if (bytesRead > 0) {
                        // 将读取的数据添加到响应缓冲区
                        for (i in 0 until bytesRead) {
                            responseBuffer.add(tempBuffer[i])
                        }

                        val chunkHex = tempBuffer.copyOf(bytesRead).joinToString(" ") { "%02X".format(it) }
                        KLog.d("Read $bytesRead bytes: $chunkHex")

                        // 检查是否是完整的Modbus响应
                        val currentResponse = responseBuffer.toByteArray()
                        if (isCompleteModbusResponse(currentResponse)) {
                            KLog.i("Complete Modbus response detected")
                            break
                        }

                        // 如果读取的数据少于缓冲区大小，可能已经读完
                        if (bytesRead < tempBuffer.size) {
                            KLog.d("Read less than buffer size, assuming response complete")
                            break
                        }
                    } else if (bytesRead == -1) {
                        KLog.w("Stream ended (read returned -1)")
                        break
                    }
                } else {
                    // 如果没有数据，短暂等待后再检查
                    if (responseBuffer.isNotEmpty()) {
                        KLog.d("No more data available, stopping read")
                        break
                    }
                    Thread.sleep(5)
                }

                // 安全退出检查
                if (System.currentTimeMillis() - startTime > timeoutMs - 50) {
                    KLog.w("Read timeout approaching, stopping read")
                    break
                }
            }
        } catch (e: IOException) {
            KLog.e("IOException while reading response: ${e.message}")
        } catch (e: Exception) {
            KLog.e("Error reading response: ${e.message}")
        }

        val responseHex = if (responseBuffer.isNotEmpty()) {
            StringUtils.byteArrayToHexString(responseBuffer.toByteArray())
        } else {
            ""
        }

        KLog.i("Final response length: ${responseBuffer.size} bytes")
        KLog.i("Final response: $responseHex")
        return responseHex
    }

    /**
     * 检查Modbus响应完整性
     */
    private fun isCompleteModbusResponse(data: ByteArray): Boolean {
        if (data.size < 5) {
            KLog.d("Response too short (${data.size} bytes), waiting for more")
            return false
        }

        try {
            val functionCode = data[1].toInt() and 0xFF

            when (functionCode) {
                3 -> { // 读保持寄存器
                    if (data.size >= 5) {
                        val byteCount = data[2].toInt() and 0xFF
                        val expectedLength = 3 + byteCount + 2 // 地址+功能码+字节数+数据+CRC
                        val isComplete = data.size >= expectedLength

                        if (isComplete) {
                            KLog.d("Modbus read response complete: $expectedLength bytes")
                        } else {
                            KLog.d("Waiting for complete response: need $expectedLength bytes, have ${data.size}")
                        }

                        return isComplete
                    }
                }
                else -> {
                    // 其他功能码，至少需要5字节
                    val isComplete = data.size >= 5
                    KLog.d("Other Modbus function $functionCode, complete: $isComplete")
                    return isComplete
                }
            }
        } catch (e: Exception) {
            KLog.e("Error checking Modbus response completeness: ${e.message}")
        }

        return false
    }

    /**
     * 发送数据
     */
    private fun write(requestBeen: RequestEntity, data: String) {
        try {
            val finalData = if (requestBeen.crc) {
                val crc = StringUtils.getCRC(data)
                "$data$crc"
            } else {
                data
            }

            KLog.i("Preparing to send: $finalData")

            val byteArray = StringUtils.hexStringToByteArray(finalData)
            os?.write(byteArray)

            val sentHex = byteArray.joinToString(" ") { "%02X".format(it) }
            KLog.i("Successfully sent ${byteArray.size} bytes: $sentHex")

        } catch (e: IOException) {
            KLog.e("IOException while writing: ${e.message}")
        } catch (e: Exception) {
            KLog.e("Error writing data: ${e.message}")
        }
    }

    private fun stopStream() {
        try {
            os?.close()
            KLog.d("OutputStream closed")
        } catch (e: IOException) {
            KLog.e("OutputStream close error: ${e.message}")
        }
        try {
            `is`?.close()
            KLog.d("InputStream closed")
        } catch (e: IOException) {
            KLog.e("InputStream close error: ${e.message}")
        }
        `is` = null
        os = null
    }

    fun stopServer() {
        synchronized(lock) {
            KLog.i("Stopping TCP server...")
            stopStream()
            try {
                socket?.close()
                KLog.d("Socket closed")
            } catch (e: IOException) {
                KLog.e("Socket close error: ${e.message}")
            }
            try {
                serverSocket?.close()
                KLog.d("ServerSocket closed")
            } catch (e: IOException) {
                KLog.e("ServerSocket close error: ${e.message}")
            }
            socket = null
            serverSocket = null
            KLog.i("TCP server stopped completely")
        }
    }

    /**
     * 取消所有任务并停止服务器
     */
    fun cancelAllTasks() {
        requestTaskManager.cancelSameTask()
    }
}