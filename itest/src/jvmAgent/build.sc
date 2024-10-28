import java.nio.file.Path
//import $file.plugins
import $ivy.`com.ofenbeck::mill-docker:0.0.3-SNAPSHOT`
import mill._
import mill.scalalib._
import os._
import coursier.maven.MavenRepository
import com.google.cloud.tools.jib.api.JavaContainerBuilder
import java.nio.file.{Files, Path}
import com.ofenbeck.mill.docker._

object project extends ScalaModule with DockerJibModule {
  def scalaVersion = "3.3.3"

  object docker extends DockerConfig {

    import com.ofenbeck.mill.docker._

    override def sourceImage = JibImage.RegistryImage("eclipse-temurin:21")

    //override def targetImage = JibImage.RegistryImage("ofenbeck/jvmagent:arm", Some(("DOCKER_USERNAME", "DOCKER_PASSWORD")))
    
    override def targetImage =
      JibImage.DockerDaemonImage("ofenbeck/jvmagent:local")

    def downloadAzureAgent: T[PathRef] = T {
      val azureAgentUrl =
        "https://github.com/microsoft/ApplicationInsights-Java/releases/download/3.6.1/applicationinsights-agent-3.6.1.jar"
      val taskDownloadDir = T.ctx().dest
      val dest: os.Path = taskDownloadDir / "applicationinsights-agent-3.6.1.jar"
      val in            = new java.net.URL(azureAgentUrl).openStream()
      try
        Files.copy(in, dest.wrapped, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
      finally
        in.close()
      PathRef(taskDownloadDir)
    }

    override def javaContainerBuilderHook(builderTask: Task[JavaContainerBuilder]): Task[JavaContainerBuilder] = Task.Anon {
        T.ctx().log.info("Added Azure agent to the container")
        val builder = builderTask()
        builder.addResources(downloadAzureAgent().path.wrapped) 
        builder
    }

    override def jvmOptions = T {
      Seq("-Xmx1024M", "-javaagent:/applicationinsights-agent-3.6.1.jar")
    }

  
    override def platforms = T {
      Set(Platform("linux", "arm64"))
    }

  }
}
