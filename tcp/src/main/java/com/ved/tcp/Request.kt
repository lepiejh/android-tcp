package com.ved.tcp

object Request {
    fun req(z: Boolean,h: Boolean,e:Boolean,m:Boolean,s: List<String>?, p:Int,callBack: (z: Boolean, s: String?) -> Unit){
        TcpServer.INSTANCE.send(z,h,e,m,s,p,callBack)
    }
}