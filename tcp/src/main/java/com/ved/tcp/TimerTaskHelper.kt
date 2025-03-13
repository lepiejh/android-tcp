package com.ved.tcp

import java.util.*

class TimerTaskHelper private constructor(){
    private var timerTask: TimerTask? = null

    companion object {
        val INSTANCE: TimerTaskHelper by lazy { Holder.INSTANCE }
    }

    private object Holder {
        val INSTANCE = TimerTaskHelper()
    }

    fun startTimer(period:Int = 5,callBack: () -> Unit) {
        timerTask?.cancel()
        this.timerTask = object : TimerTask() {
            override fun run() {
                callBack.invoke()
            }
        }
        val timer = Timer()
        val timerTask2 = this.timerTask
        val i = period * 1000
        timer.schedule(timerTask2, i.toLong(), i.toLong())
    }

    fun stopTimer() {
        if (timerTask != null) {
            timerTask?.cancel()
            this.timerTask = null
        }
    }
}