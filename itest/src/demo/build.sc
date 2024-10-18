import $file.plugins
import mill._
import mill.scalalib._
import os._
import coursier.maven.MavenRepository

import com.ofenbeck.mill.docker._

object project extends ScalaModule with DockerJibModule {
  def scalaVersion = "3.3.3"

  val sonatypeReleases = Seq(
    MavenRepository("https://oss.sonatype.org/content/repositories/snapshots")
  )

  def repositoriesTask = T.task {
    super.repositoriesTask() ++ sonatypeReleases
  }
  val jibCore = "0.27.1"

  override def ivyDeps = super.ivyDeps() ++ Agg(
    ivy"com.google.cloud.tools:jib-core:$jibCore",
    //ivy"com.ofenbeck::mill-docker:0.0.1-SNAPSHOT", //TODO - PR mill - no Snapshot postfix on jar
    ivy"org.scrupal:chill-java:0.7.0-SNAPSHOT"
  )
  object docker extends DockerConfig {
    override def labels = Map("maintainer" -> "ofenbeck")
    override def jvmOptions = Seq("-Xmx1024M")
    override def exposedPorts = Seq(8080)

    def sourceImage = JibImage.DockerDaemonImage("gcr.io/distroless/java:latest")
    def targetImage = JibImage.DockerDaemonImage("ofenbeck/demo6")
    

  }
}

def check() = T.command {
  project.docker.buildImage()
}