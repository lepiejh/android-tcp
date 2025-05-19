package com.ved.tcp

object Request {
    fun req(z: Boolean = false,h: Boolean = false,e:Boolean = false,m:Boolean = false,t:Int = 5, p:Int,s: List<String>?,callBack: (z: Boolean, s: String?) -> Unit){
        TcpServer.INSTANCE.send(z,h,e,m,t,p,s,callBack)
    }
}