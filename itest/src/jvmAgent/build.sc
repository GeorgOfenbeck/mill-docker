import java.nio.file.Path
import $file.plugins
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

    override def targetImage =
      JibImage.DockerDaemonImage("ofenbeck/jvmagent")

    def downloadAzureAgent: T[PathRef] = T {
      val azureAgentUrl =
        "https://github.com/microsoft/ApplicationInsights-Java/releases/download/3.6.1/applicationinsights-agent-3.6.1.jar"
      val dest: os.Path = T.ctx().dest / "applicationinsights-agent-3.6.1.jar"
      val in            = new java.net.URL(azureAgentUrl).openStream()
      try
        Files.copy(in, dest.wrapped, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
      finally
        in.close()
      PathRef(dest)
    }

    override def javaContainerBuilderHook(builder: JavaContainerBuilder): Command[Unit] = T.command {
        //builder.addResources(downloadAzureAgent().path.wrapped)
        //T.ctx().log.info("Added Azure agent to the container")
    }

    override def jvmOptions = T {
      Seq("-Xmx1024M", "-javaagent:/applicationinsights-agent-3.6.1.jar")
    }

  }
}
