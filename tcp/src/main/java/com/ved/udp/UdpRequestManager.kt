package com.ved.udp

class UdpRequestManager {
    private val firstTasks: ArrayList<UdpRequestEntity?> = ArrayList<UdpRequestEntity?>()
    private val taskLock = Any()
    private val tasks: ArrayList<UdpRequestEntity?> = ArrayList<UdpRequestEntity?>()

    fun addTask(requestBeen: UdpRequestEntity) {
        if (requestBeen.isFirst) {
            doAddTask(firstTasks, requestBeen)
        } else {
            doAddTask(tasks, requestBeen)
        }
    }

    private fun doAddTask(arrayList: ArrayList<UdpRequestEntity?>, requestBeen: UdpRequestEntity) {
        synchronized(taskLock) {
            for (i in arrayList.indices) {
                if (requestBeen.reqData == arrayList[i]?.reqData) {
                    arrayList.removeAt(i)
                    break
                }
            }
            arrayList.add(requestBeen)
        }
    }

    fun pollTask(): UdpRequestEntity? {
        var task: UdpRequestEntity?
        synchronized(taskLock) { task = getTaskManager() }
        return task
    }

    fun cancelSameTask() {
        synchronized(taskLock) {
            tasks.clear()
            UdpServer.INSTANCE.stopServer()
        }
    }

    private fun getTaskManager() = doGetTask(firstTasks) ?: doGetTask(tasks)

    private fun doGetTask(arrayList: ArrayList<UdpRequestEntity?>): UdpRequestEntity? {
        var size = arrayList.size
        if (size <= 0) {
            return null
        }
        size--
        val requestBeen : UdpRequestEntity? = arrayList.getOrNull(size)
        arrayList.removeAt(size)
        return requestBeen
    }
}