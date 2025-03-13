package com.ved.tcp

object Request {
    fun req(z: Boolean, s: String?, port:Int,callBack: (z: Boolean, s: String?) -> Unit){
        TcpServer.INSTANCE.send(z,s,port,callBack)
    }
}