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

    fun send(z: Boolean,h: Boolean,w:Boolean,r:Boolean,m:Boolean,d:Boolean,t:Int,c:String?,u:String?,p:Int,s: List<String>?,callBack: (z: Boolean, s: String?) -> Unit) {
        requestTaskManager.addTask(RequestEntity(z,h,w,r,m,d,t,c,u,p,s, callBack))
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
                            Thread.sleep(100)
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
                            Thread.sleep(100)
                        }
                        val set = mutableSetOf<String>()
                        requestBeen.reqData.forEach { data ->
                            write(requestBeen, data)
                            os?.flush()
                            set.add(read(requestBeen))
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
        os?.write(
            if (requestBeen.write) {
                BigInteger(data, 16).toByteArray()
            } else {
                StringUtils.hexStringToByteArray(data)
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