package com.ved.tcp

class RequestEntity(
    z: Boolean,
    h: Boolean,
    e:Boolean,
    m:Boolean,
    t:Int,
    p:Int,
    val reqData: List<String>?,
    private var callBack: (z: Boolean, s: String?) -> Unit
) {
    var isFirst = false
    var heartbeat = false
    var hex = false
    var multi = false
    var timeout = 0
    var port = 0

    init {
        isFirst = z
        heartbeat = h
        hex = e
        multi = m
        timeout = t
        port = p
    }

    fun callBack(z: Boolean, str: String?) {
        callBack.invoke(z, str)
    }
}