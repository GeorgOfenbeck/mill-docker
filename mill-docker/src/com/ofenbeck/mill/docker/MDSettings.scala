package com.ofenbeck.mill.docker

import com.google.cloud.tools.jib.api.ImageReference
import com.ofenbeck.mill.{docker => md}
import java.time.Instant

import java.util.Optional
import scala.jdk.OptionConverters.RichOption

import com.google.cloud.tools.jib.api.CredentialRetriever
import com.google.cloud.tools.jib.api.Credential

object MDShared {
  val toolName = "mill-docker-jib"
  def useCurrentTimestamp(useCurrentTimestamp: Boolean): Instant =
    if (useCurrentTimestamp) Instant.now() else Instant.EPOCH

  def isSnapshotDependency(millpath: mill.PathRef) = millpath.path.last.endsWith("-SNAPSHOT.jar")

  def retrieveEnvCredentials(usernameEnv: String, passwordEnv: String): CredentialRetriever =
    new CredentialRetriever {
      def retrieve(): Optional[Credential] = {
        val option = for {
          username <- sys.env.get(usernameEnv)
          password <- sys.env.get(passwordEnv)
        } yield Credential.from(username, password)

        option.asJava
      }
    }
}

final case class Platform(
    val os: String,
    val architecture: String,
)
object Platform {
  implicit val rw: upickle.default.ReadWriter[Platform] = upickle.default.macroRW
}

final case class BuildSettings(
    val sourceImage: md.JibSourceImage,
    val targetImage: md.ImageReference,
    val dependencies: Seq[mill.PathRef],
    val snapshotDependencies: Seq[mill.PathRef],
    val resources: Seq[mill.PathRef],
    val projectDependencies: Seq[mill.PathRef],
    val classes: Seq[mill.PathRef],
    val extraFiles: Seq[mill.PathRef],
    val mainClass: Option[String],
    val mainClassSearchPaths: Seq[mill.PathRef],
    val tags: Seq[String],
    val setAllowInsecureRegistries: Boolean = false,
    val useCurrentTimestamp: Boolean = true,
    val autoDetectMainClass: Boolean = true,
)

object BuildSettings {
  implicit val rwimage: upickle.default.ReadWriter[md.ImageReference] = upickle.default.macroRW
  implicit val source: upickle.default.ReadWriter[md.JibSourceImage]  = upickle.default.macroRW
  implicit val rw: upickle.default.ReadWriter[BuildSettings]          = upickle.default.macroRW
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
    val jibProgramArgs: Seq[String],
    // volumes
    // labels
)
object DockerSettings {
  implicit val rw: upickle.default.ReadWriter[DockerSettings] = upickle.default.macroRW
}
