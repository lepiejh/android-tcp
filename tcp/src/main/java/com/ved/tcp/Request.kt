package com.ved.tcp

object Request {
    fun req(z: Boolean = false,h: Boolean = false,e:Boolean = false,m:Boolean = false,s: List<String>?, p:Int,callBack: (z: Boolean, s: String?) -> Unit){
        TcpServer.INSTANCE.send(z,h,e,m,s,p,callBack)
    }
}