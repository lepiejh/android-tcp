package com.ved.tcp

class RequestEntity(
    z: Boolean,
    h: Boolean,
    w:Boolean,
    r:Boolean,
    m:Boolean,
    d:Boolean,
    t:Int,
    c:String?,
    u:String?,
    p:Int,
    val reqData: List<String>?,
    private var callBack: (z: Boolean, s: String?) -> Unit
) {
    var isFirst = false
    var heartbeat = false
    var write = false
    var read = false
    var multi = false
    var delay = false
    var timeout = 0
    var port = 0
    var check : String? = null
    var url : String? = null

    init {
        isFirst = z
        heartbeat = h
        write = w
        read = r
        multi = m
        delay = d
        timeout = t
        check = c
        url = u
        port = p
    }

    fun callBack(z: Boolean, str: String?) {
        callBack.invoke(z, str)
    }
}