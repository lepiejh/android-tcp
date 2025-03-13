package com.ved.tcp

class RequestEntity(
    z: Boolean,
    val reqData: String?,
    private var callBack: (z: Boolean, s: String?) -> Unit
) {
    var isFirst = false

    init {
        isFirst = z
    }

    fun callBack(z: Boolean, str: String?) {
        callBack.invoke(z, str)
    }
}