package com.ved.tcp

import com.ved.framework.utils.KLog
import com.ved.framework.utils.StringUtils
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.math.BigInteger
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

    fun send(z: Boolean,h: Boolean,w:Boolean,r:Boolean,m:Boolean,d:Boolean,cc:Boolean,st:Boolean,t:Int,c:String?,u:String?,p:Int,ce:Boolean,dm:Long,sm:Long,s: List<String>?,callBack: (z: Boolean, s: String?) -> Unit) {
        requestTaskManager.addTask(RequestEntity(z,h,w,r,m,d,cc,st,t,c,u,p,ce,dm,sm,s,callBack))
        executor.execute {
            val pollTask = requestTaskManager.pollTask()
            if (pollTask != null) {
                executeOneTask(pollTask)
            }
        }
    }

    private fun executeOneTask(requestBeen: RequestEntity) {
        try {
            if (requestBeen.url.isNullOrEmpty()){
                serverSocket = ServerSocket(requestBeen.port).apply {
                    if (requestBeen.timeout > 0) {
                        soTimeout = requestBeen.timeout * 1000 // 设置5秒超时
                    }
                    reuseAddress = true
                }
                socket = serverSocket?.accept().apply {
                    if (requestBeen.timeout > 0) {
                        this?.soTimeout = requestBeen.timeout * 1000 // 设置socket读取超时
                    }
                }
            }else{
                socket = Socket(requestBeen.url, requestBeen.port)
            }
            `is` = socket?.getInputStream()
            os = socket?.getOutputStream()
            if (!requestBeen.multi) {
                if (socket != null && socket?.isConnected == true) {
                    if (requestBeen.reqData?.isNotEmpty() == true){
                        if (requestBeen.delay){
                            Thread.sleep(requestBeen.delayMillis)
                        }
                        requestBeen.reqData.forEach { data ->
                            write(requestBeen, data)
                        }
                    }
                    os?.flush()
                }
                val response = read(requestBeen)
                requestBeen.callBack(response != "No response data", response)
            } else {
                if (socket != null && socket?.isConnected == true){
                    if (requestBeen.reqData?.isNotEmpty() == true){
                        if (requestBeen.delay){
                            Thread.sleep(requestBeen.delayMillis)
                        }
                        val set = mutableSetOf<String>()
                        var previousValue: String? = null
                        requestBeen.reqData.forEachIndexed { index, data ->
                            write(requestBeen, if (requestBeen.change && previousValue?.isNotEmpty() == true && previousValue != "No response data"){
                                "${data}${if ((StringUtils.hexStringToByteArray(previousValue)[4].toInt() and 0xFF) == 0) "01" else "00"}"
                            }else{
                                data
                            })
                            os?.flush()
                            val currentValue = read(requestBeen)
                            set.add(currentValue)
                            previousValue = currentValue
                            if (requestBeen.stop && index != requestBeen.reqData.size - 1) {
                                Thread.sleep(requestBeen.stopMillis)
                            }
                        }
                        if (set.contains("No response data")){
                            requestBeen.callBack(false,"No response data")
                        }else{
                            requestBeen.callBack(true,set.joinToString(","))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            requestBeen.callBack(false, "Error: ${e.message}")
        } finally {
            stopServer()
        }
    }

    private fun read(requestBeen: RequestEntity): String {
        val buffer = ByteArray(if (requestBeen.read) 10240 else 1024)
        val bytesRead = `is`?.read(buffer) ?: -1
        var response = ""
        response = if (bytesRead > 0) {
            if (requestBeen.read) {
                StringUtils.byteArrayToHexString(
                    charArrayOf(
                        '0',
                        '1',
                        '2',
                        '3',
                        '4',
                        '5',
                        '6',
                        '7',
                        '8',
                        '9',
                        'A',
                        'B',
                        'C',
                        'D',
                        'E',
                        'F'
                    ), buffer, bytesRead
                )
            } else {
                StringUtils.byteArrayToHexString(buffer.copyOf(bytesRead))
            }
        } else {
            "No response data"
        }
        if (requestBeen.check?.isNotEmpty() == true){
            if (!StringUtils.startsWith(response,requestBeen.check)){
                response =  "No response data"
            }
        }
        return StringUtils.trim(response)
    }

    private fun write(requestBeen: RequestEntity, data: String) {
        val d = if (requestBeen.crc) {
            "${data}${StringUtils.getCRC(data,"2X")}"
        }else{
            data
        }
        os?.write(
            if (requestBeen.write) {
                BigInteger(d, 16).toByteArray()
            } else {
                StringUtils.hexStringToByteArray(d)
            }
        )
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
        synchronized(lock){
            stopStream()
            if (socket != null) {
                try {
                    socket?.close()
                } catch (e: IOException) {
                    KLog.e("Socket close error: ${e.message}")
                }
            }
            if (serverSocket != null) {
                try {
                    serverSocket?.close()
                } catch (e: IOException) {
                    KLog.e("ServerSocket close error: ${e.message}")
                }
            }
            socket = null
            serverSocket = null
        }
    }
}