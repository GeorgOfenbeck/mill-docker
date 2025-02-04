package com.ofenbeck.mill.docker

import com.google.cloud.tools.jib.event.events.ProgressEvent
import com.google.cloud.tools.jib.event.events.TimerEvent
import com.google.cloud.tools.jib.api.LogEvent
import com.google.cloud.tools.jib.api.JibEvent


object MDLogging {
  
  def getEventLogger(log: mill.api.Logger): java.util.function.Consumer[LogEvent] = {
    val loggerJava = new java.util.function.Consumer[LogEvent] {
      def accept(e: LogEvent): Unit =
        e match {
          case m: LogEvent => log.info(s"LogEvent:      ${m.getMessage}")
          case _             => log.info(s"unknown element $e")
        }
    }
    loggerJava
  }
  def getLogger(log: mill.api.Logger): java.util.function.Consumer[JibEvent] = {
    val loggerJava = new java.util.function.Consumer[JibEvent] {
      def accept(e: JibEvent): Unit =
        e match {
          case m: LogEvent => log.info(s"LogEvent:      ${m.getMessage}")
          case p: ProgressEvent =>
            log.ticker(
              s"ProgressEvent: ${p.getAllocation().getFractionOfRoot * p.getUnits()} ${p.getAllocation().getDescription()}"
            )
          case t: TimerEvent => log.ticker(s"TimerEvent:    ${t.getDescription()} ${t.getElapsed()}")
          case _             => log.info(s"unknown element $e")
        }
    }
    loggerJava
  }
  
}
