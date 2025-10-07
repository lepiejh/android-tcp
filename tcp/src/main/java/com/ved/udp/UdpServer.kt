package com.ved.udp

import com.ved.framework.utils.KLog
import com.ved.framework.utils.StringUtils
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.concurrent.Executors

class UdpServer private constructor() {
    private var datagramSocket: DatagramSocket? = null
    var requestTaskManager = UdpRequestManager()
    private val lock = Any()
    private val executor = Executors.newSingleThreadExecutor()

    companion object {
        val INSTANCE: UdpServer by lazy { Holder.INSTANCE }
    }

    private object Holder {
        val INSTANCE = UdpServer()
    }

    fun send(z: Boolean, h: Boolean, w: Boolean, r: Boolean, m: Boolean, d: Boolean, cc: Boolean, st: Boolean, t: Int, c: String?, u: String?, p: Int, ce: Boolean, dm: Long, sm: Long, s: List<String>?, callBack: (z: Boolean, s: String?) -> Unit) {
        requestTaskManager.addTask(UdpRequestEntity(z, h, w, r, m, d, cc, st, t, c, u, p, ce, dm, sm, s, callBack))
        executor.execute {
            val pollTask = requestTaskManager.pollTask()
            if (pollTask != null) {
                executeOneTask(pollTask)
            }
        }
    }

    private fun executeOneTask(requestBeen: UdpRequestEntity) {
        try {
            // UDP不需要建立连接，直接创建socket
            datagramSocket = if (requestBeen.url.isNullOrEmpty()) {
                // Server模式 - 监听指定端口
                DatagramSocket(requestBeen.port).apply {
                    if (requestBeen.timeout > 0) {
                        soTimeout = requestBeen.timeout * 1000
                    }
                    reuseAddress = true
                }
            } else {
                // Client模式 - 使用任意可用端口
                DatagramSocket().apply {
                    if (requestBeen.timeout > 0) {
                        soTimeout = requestBeen.timeout * 1000
                    }
                    reuseAddress = true
                }
            }

            if (requestBeen.reqData?.isNotEmpty() == true) {
                val targetAddress = if (!requestBeen.url.isNullOrEmpty()) {
                    InetAddress.getByName(requestBeen.url)
                } else {
                    null
                }

                if (requestBeen.delay) {
                    Thread.sleep(requestBeen.delayMillis)
                }

                val responses = mutableSetOf<String>()

                requestBeen.reqData.forEachIndexed { index, data ->
                    // 发送UDP数据包
                    val sendData = prepareSendData(requestBeen, data)
                    val sendPacket = if (targetAddress != null) {
                        DatagramPacket(sendData, sendData.size, targetAddress, requestBeen.port)
                    } else {
                        // Server模式下需要先接收客户端地址（这里简化处理）
                        null
                    }

                    if (sendPacket != null) {
                        datagramSocket?.send(sendPacket)
                        KLog.d("UDP Sent ${sendData.size} bytes to ${targetAddress?.hostAddress}:${requestBeen.port}")
                    }

                    // 等待数据发送完成
                    Thread.sleep(50)

                    // 读取响应
                    val response = if (requestBeen.read) {
                        readUdpResponse(requestBeen.timeout * 1000L)
                    } else {
                        ""
                    }

                    if (response.isNotEmpty()) {
                        responses.add(response)
                    } else {
                        KLog.w("No UDP response received for data: $data")
                    }

                    if (requestBeen.stop && index != requestBeen.reqData.size - 1) {
                        Thread.sleep(requestBeen.stopMillis)
                    }
                }

                requestBeen.callBack(true, responses.joinToString(","))
            }
        } catch (e: Exception) {
            requestBeen.callBack(false, "UDP Error: ${e.message}")
            KLog.e("UDP Execute task error: ${e.message}")
        } finally {
            stopServer()
        }
    }

    /**
     * 准备发送数据（添加CRC等处理）
     */
    private fun prepareSendData(requestBeen: UdpRequestEntity, data: String): ByteArray {
        val d = if (requestBeen.crc) {
            "${data}${StringUtils.getCRC(data)}"
        } else {
            data
        }
        KLog.i("UDP开始发送实际的指令： $d")
        return StringUtils.hexStringToByteArray(d)
    }

    /**
     * 读取UDP响应
     */
    private fun readUdpResponse(timeoutMs: Long): String {
        return try {
            val buffer = ByteArray(1024)
            val receivePacket = DatagramPacket(buffer, buffer.size)

            datagramSocket?.receive(receivePacket)

            val receivedData = buffer.copyOf(receivePacket.length)
            val response = StringUtils.byteArrayToHexString(receivedData)

            KLog.i("UDP Received response from ${receivePacket.address.hostAddress}:${receivePacket.port} - $response")
            response
        } catch (e: SocketTimeoutException) {
            KLog.w("UDP Receive timeout")
            ""
        } catch (e: Exception) {
            KLog.e("UDP Receive error: ${e.message}")
            ""
        }
    }

    fun stopServer() {
        synchronized(lock) {
            try {
                datagramSocket?.close()
            } catch (e: IOException) {
                KLog.e("UDP Socket close error: ${e.message}")
            }
            datagramSocket = null
        }
    }
}