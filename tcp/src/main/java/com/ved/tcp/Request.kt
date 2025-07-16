package com.ved.tcp

object Request {
    fun req(z: Boolean = false,h: Boolean = false,w:Boolean = false,r:Boolean = false,m:Boolean = false,d:Boolean = false,cc:Boolean = false,st:Boolean = false,t:Int = 5,c:String? = null,u:String? = null,p:Int,dm:Long = 100,sm:Long = 100,s: List<String>?,callBack: (z: Boolean, s: String?) -> Unit){
        TcpServer.INSTANCE.send(z,h,w,r,m,d,cc,st,t,c,u,p,dm,sm,s,callBack)
    }
}