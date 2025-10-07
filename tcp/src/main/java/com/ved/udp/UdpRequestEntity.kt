package com.ved.udp

class UdpRequestEntity(
    z: Boolean,
    h: Boolean,
    w:Boolean,
    r:Boolean,
    m:Boolean,
    d:Boolean,
    cc:Boolean,
    s:Boolean,
    t:Int,
    c:String?,
    u:String?,
    p:Int,
    ce:Boolean,
    dm:Long,
    sm:Long,
    val reqData: List<String>?,
    private var callBack: (z: Boolean, s: String?) -> Unit
) {
    var isFirst = false
    var heartbeat = false
    var write = false
    var read = false
    var multi = false
    var delay = false
    var crc = false
    var stop = false
    var timeout = 0
    var port = 0
    var change = false
    var delayMillis : Long = 0
    var stopMillis : Long = 0
    var check : String? = null
    var url : String? = null

    init {
        isFirst = z
        heartbeat = h
        write = w
        read = r
        multi = m
        delay = d
        crc = cc
        stop = s
        timeout = t
        check = c
        url = u
        port = p
        change = ce
        delayMillis = dm
        stopMillis = sm
    }

    fun callBack(z: Boolean, str: String?) {
        callBack.invoke(z, str)
    }
}