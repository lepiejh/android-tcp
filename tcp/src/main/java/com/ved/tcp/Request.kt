package com.ved.tcp

object Request {
    fun req(z: Boolean = false,h: Boolean = false,w:Boolean = false,r:Boolean = false,m:Boolean = false,t:Int = 5, p:Int,s: List<String>?,callBack: (z: Boolean, s: String?) -> Unit){
        TcpServer.INSTANCE.send(z,h,w,r,m,t,p,s,callBack)
    }
}