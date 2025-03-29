package com.ved.tcp

import java.util.ArrayList

class RequestManager {
    private val firstTasks: ArrayList<RequestEntity?> = ArrayList<RequestEntity?>()
    private val taskLock = "taskLock"
    private val tasks: ArrayList<RequestEntity?> = ArrayList<RequestEntity?>()

    fun addTask(requestBeen: RequestEntity) {
        if (requestBeen.isFirst) {
            doAddTask(firstTasks, requestBeen)
        } else {
            doAddTask(tasks, requestBeen)
        }
    }

    private fun doAddTask(arrayList: ArrayList<RequestEntity?>, requestBeen: RequestEntity) {
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

    fun pollTask(): RequestEntity? {
        var task: RequestEntity?
        synchronized(taskLock) { task = getTaskManager() }
        return task
    }

    fun cancelSameTask() {
        synchronized(taskLock) {
            tasks.clear()
            TcpServer.INSTANCE.stopServer()
        }
    }

    private fun getTaskManager() = doGetTask(firstTasks) ?: doGetTask(tasks)

    private fun doGetTask(arrayList: ArrayList<RequestEntity?>): RequestEntity? {
        var size = arrayList.size
        if (size <= 0) {
            return null
        }
        size--
        val requestBeen : RequestEntity? = arrayList.getOrNull(size)
        arrayList.removeAt(size)
        return requestBeen
    }
}