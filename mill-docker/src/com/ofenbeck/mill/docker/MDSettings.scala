package com.ofenbeck.mill.docker

import com.google.cloud.tools.jib.api.ImageReference
import com.ofenbeck.mill.{docker => md}

object MDShared {
  val toolName = "mill-docker-jib"
}

final case class Platform(
    val os: String,
    val architecture: String,
)
object Platform {
  implicit val rw: upickle.default.ReadWriter[Platform] = upickle.default.macroRW
}

final case class BuildSettings( 
    val sourceImage: md.JibSourceImage , 
    val targetImage: md.ImageReference,
    val upstreamAssemblyClasspath: Seq[mill.PathRef],
    val resourcesPaths: Seq[mill.PathRef],
    val compiledClasses: mill.PathRef,
    val mainClass: Option[String],
    val tags: Seq[String],
    val setAllowInsecureRegistries: Boolean = false,
    val useCurrentTimestamp: Boolean = true,
    val autoDetectMainClass: Boolean = true,
)

object BuildSettings {
  implicit val rwimage: upickle.default.ReadWriter[md.ImageReference]      = upickle.default.macroRW  
    implicit val source: upickle.default.ReadWriter[md.JibSourceImage]          = upickle.default.macroRW
  implicit val rw: upickle.default.ReadWriter[BuildSettings] = upickle.default.macroRW
}

final case class DockerSettings(
    val labels: Map[String, String],
    val jvmOptions: Seq[String],
    val exposedPorts: Seq[Int],
    val exposedUdpPorts: Seq[Int],
    val envVars: Map[String, String],
    val user: Option[String],
    val platforms: Set[Platform],
    val internalImageFormat: JibImageFormat,
    val entrypoint: Seq[String],
    val args: Seq[String],
    // volumes
    // labels
)
object DockerSettings {
  implicit val rw: upickle.default.ReadWriter[DockerSettings] = upickle.default.macroRW
}