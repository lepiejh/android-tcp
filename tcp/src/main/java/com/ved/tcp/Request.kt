package com.ved.tcp

object Request {
    fun req(z: Boolean,h: Boolean,e:Boolean, s: List<String>?, p:Int,callBack: (z: Boolean, s: String?) -> Unit){
        TcpServer.INSTANCE.send(z,h,e,s,p,callBack)
    }
}