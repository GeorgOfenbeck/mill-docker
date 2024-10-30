import java.nio.file.Path
//import $file.plugins
import $ivy.`com.ofenbeck::mill-docker:0.0.3-SNAPSHOT`
import mill._
import mill.scalalib._
import os._
import coursier.maven.MavenRepository
import com.google.cloud.tools.jib.api._
import com.google.cloud.tools.jib.api.buildplan._
import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters.RichOption

import java.time.Instant
import java.nio.file.{Files, Path}
import com.ofenbeck.mill.docker._

object project extends ScalaModule with DockerJibModule {
  def scalaVersion = "3.3.3"

  object docker extends DockerConfig {

    import com.ofenbeck.mill.docker._

    override def sourceImage = JibImage.RegistryImage("eclipse-temurin:21")

    //override def targetImage = JibImage.RegistryImage("ofenbeck/jvmagent:arm", Some(("DOCKER_USERNAME", "DOCKER_PASSWORD")))
    
    override def targetImage =
      JibImage.DockerDaemonImage("ofenbeck/customjibbuild:local")

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

    /** Hook to modify the JibContainerBuilder before it is used to build the container. An "empty" JibContainerBuilder
      * is passed to the hook (from the configured SoureImage). In addition the FileEntriesLayer and the entrypoints of
      * a default JavaBuild are passed to the hook. You have to add both again to the "empty" JibContainerBuilder to get
      * the same behavior as the default JavaBuild.
      * @return
      *   The return value is used for further processing of the JibContainerBuilder - so full replacement is possible.
      */
    override def jibContainerBuilderHook(
        sofar: Task[(JibContainerBuilder, Vector[FileEntriesLayer], Vector[String])],
    ): Task[JibContainerBuilder] = Task.Anon {

      //this takes the default JavaContainerBuilder
      val (emptyJibBuilder, jiblayers, entrypoints) = sofar()
      jiblayers.map(layer => emptyJibBuilder.addFileEntriesLayer(layer))
      emptyJibBuilder.setEntrypoint(entrypoints.asJava)



      //we are adding the Azure agent to the container as a seperate layer with some custom permissions
      import com.google.cloud.tools.jib.api.buildplan._
      val sourcefile: Path                  = downloadAzureAgent().path.wrapped
      val pathInContainer: AbsoluteUnixPath = AbsoluteUnixPath.get("/agents/azure.jar")
      val permissions: FilePermissions      = FilePermissions.fromOctalString("700")
      val modificationTime: Instant         = Instant.EPOCH
      val ownership: String                 = "root:root"

      val layer = FileEntriesLayer
        .builder()
        .addEntry(sourcefile, pathInContainer, permissions, modificationTime, ownership)
        .build()
      emptyJibBuilder.addFileEntriesLayer(layer)
      emptyJibBuilder
    }
  
    override def platforms = T {
      Set(Platform("linux", "arm64"))
    }

  }
}