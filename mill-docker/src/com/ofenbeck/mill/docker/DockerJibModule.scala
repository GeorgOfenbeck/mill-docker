package com.ofenbeck.mill.docker

import mill.scalalib.JavaModule
import os.Shellable.IterableShellable
import com.google.cloud.tools._

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import java.time.Instant
import java.util.Optional
import java.util.function._
import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters.RichOption
import scala.language.postfixOps

import scala.collection.immutable._

import mill._
import mill.scalalib.ScalaModule
import mill.api.Ctx

import com.ofenbeck.mill.{docker => md}
import com.google.cloud.tools.jib.api.JibContainerBuilder
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer
import com.google.cloud.tools.jib.api.JavaContainerBuilder
import com.google.cloud.tools.jib.api.Containerizer
import com.google.cloud.tools.jib.api.DockerDaemonImage
import com.google.cloud.tools.jib.api.TarImage
import com.google.cloud.tools.jib.api.RegistryImage
import com.google.cloud.tools.jib.api.Jib

trait DockerJibModule extends Module { outer: JavaModule =>

  trait DockerConfig extends mill.Module {

    def labels: T[Map[String, String]] = Map.empty[String, String]

    def tags: T[Seq[String]] = Seq.empty[String] 

    /** JVM runtime options. Each item of the Seq should consist of an option and its desired value, like
      * {{{
      * def jvmOptions = Seq("-Xmx1024M", "-agentlib:jdwp=transport=dt_socket,server=y,address=8000", …)
      * }}}
      * For a full list of options consult the official documentation at
      * [[https://docs.oracle.com/en/java/javase/21/docs/specs/man/java.html#overview-of-java-options]]
      */
    def jvmOptions: T[Seq[String]] = Seq.empty[String]

    /** TCP Ports the container will listen to at runtime.
      *
      * See also the Docker docs on [[https://docs.docker.com/engine/reference/builder/#expose ports]] for more
      * information.
      */
    def exposedPorts: T[Seq[Int]] = Seq.empty[Int]

    /** UDP Ports the container will listen to at runtime.
      *
      * See also the Docker docs on [[https://docs.docker.com/engine/reference/builder/#expose ports]] for more
      * information.
      */
    def exposedUdpPorts: T[Seq[Int]] = Seq.empty[Int]

    /** Sets the container environment. These environment variables are available to the program launched by the
      * container entrypoint command. This replaces any previously-set environment variables.
      *
      * <p>This is similar to <a href="https://docs.docker.com/engine/reference/builder/#env">{@code ENV} in
      * Dockerfiles</a> or {@code env} in the <a
      * href="https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.11/#container-v1-core">Kubernetes
      * Container spec</a>.
      */
    def envVars: T[Map[String, String]] = Map.empty[String, String]

    /** Sets the user and group to run the container as. {@code user} can be a username or UID along with an optional
      * groupname or GID.
      *
      * <p>The following are valid formats for {@code user}
      *
      * <ul> <li>{@code user} <li>{@code uid} <li>{@code :group} <li>{@code :gid} <li>{@code user:group} <li>{@code
      * uid:gid} <li>{@code uid:group} <li>{@code user:gid} </ul>
      */
    def user: T[Option[String]] = None

    /** Define the target platform for the container. This is a list of platforms that the final image can run on. Note
      * that for when using the Docker daemon as the target, the platform must be compatible with the host.
      */
    def platforms: T[Set[md.Platform]] = Set.empty[md.Platform]

    /** The internal image format to use. This is the format that Jib will use to build the image. The default is
      * Docker.
      */
    def internalImageFormat: T[md.JibImageFormat] = T {
      md.JibImageFormat.Docker: JibImageFormat
    }

    /** The entrypoint for the container. This is the command that will be run when the container starts. If none is
      * provided the jib default is used. ENTRYPOINT ["java", jib.container.jvmFlags, "-cp",
      * "/app/resources:/app/classes:/app/libs/", jib.container.mainClass] CMD [jib.container.args])
      */

    def entrypoint: T[Seq[String]] = Seq.empty[String]

    /** The program arguments for the container. This is the arguments that will be passed to the main class of the
      * container. If none is provided the jib default is used. This is NOT Docker ARGS
      */
    def jibProgramArgs: T[Seq[String]] = Seq.empty[String]

    /** One of the 3 possible image sources (tar, registry, docker daemon) for the source image.
      * @return
      */
    def sourceImage: T[md.JibSourceImage]

    /** One of the 3 possible image targets (tar, registry, docker daemon) for the target image.
      * @return
      */
    def targetImage: T[md.ImageReference]

    def dockerContainerConfig: T[DockerSettings] = T {
      DockerSettings(
        labels = labels(),
        jvmOptions = jvmOptions(),
        exposedPorts = exposedPorts(),
        exposedUdpPorts = exposedUdpPorts(),
        envVars = envVars(),
        user = user(),
        platforms = platforms(),
        internalImageFormat = internalImageFormat(),
        entrypoint = entrypoint(),
        jibProgramArgs = jibProgramArgs(),
      )
    }

    def buildSettings: T[BuildSettings] = T {
      BuildSettings(
        sourceImage = sourceImage(),
        targetImage = targetImage(),
        setAllowInsecureRegistries = false,
        useCurrentTimestamp = true,
        upstreamAssemblyClasspath = outer.upstreamAssemblyClasspath().toList,
        resourcesPaths = outer.resources(),
        compiledClasses = outer.compile().classes,
        unmanagedDependencies = outer.unmanagedClasspath().toList,
        mainClass = None,
        autoDetectMainClass = true,
        tags = tags(),
      )
    }

    /** Hook to modify the JibContainerBuilder before it is used to build the container. An "empty" JibContainerBuilder
      * is passed to the hook (from the configured SoureImage). In addition the FileEntriesLayer and the entrypoints of
      * a default JavaBuild are passed to the hook. You have to add both again to the "empty" JibContainerBuilder to get
      * the same behavior as the default JavaBuild.
      * @return
      *   The return value is used for further processing of the JibContainerBuilder - so full replacement is possible.
      */
    def jibContainerBuilderHook(
        sofar: Task[(JibContainerBuilder, Vector[FileEntriesLayer], Vector[String])],
    ): Task[JibContainerBuilder] = Task.Anon {
      val (emptyJibBuilder, jiblayers, entrypoints) = sofar()
      jiblayers.map(layer => emptyJibBuilder.addFileEntriesLayer(layer))
      emptyJibBuilder.setEntrypoint(entrypoints.asJava)
      emptyJibBuilder
    }

    /** Hook to modify the JavaContainerBuilder before it is used to build the container.
      * @return
      *   The return value is used for further processing of the JavaContainerBuilder - so full replacement is possible.
      */

    def javaContainerBuilderHook(javaBuilder: Task[JavaContainerBuilder]): Task[JavaContainerBuilder] = Task.Anon {
      javaBuilder()
    }

    case class BuildResult(
        image: String,
        imageId: String,
        imageDigest: String,
        path: Option[PathRef],
    )

    object BuildResult {
      implicit def jsonCodec: upickle.default.ReadWriter[BuildResult] = upickle.default.macroRW
    }

    def getJavaBuilder(): Task[JavaContainerBuilder] = Task.Anon {
      val logger = T.ctx().log
      val dockerConf = dockerContainerConfig()
      val buildConf  = buildSettings()

      val javaBuilder =
        MDBuild.javaBuild(
          buildSettings = buildConf,
          dockerSettings = dockerConf,
          logger = logger,
        )
      javaBuilder
    }

    def getJibLayers(
        postHookJavaBuilder: Task[JavaContainerBuilder],
    ): Task[(JibContainerBuilder, Vector[FileEntriesLayer], Vector[String])] = Task.Anon {
      val buildConf        = buildSettings()
      val logger           = T.ctx().log
      val containerBuilder = postHookJavaBuilder().toContainerBuilder()
      MDBuild.customizeLayers(containerBuilder, buildConf, logger)
    }

    //the strange code style is due to the Task macro 
    def getPostHookJavaContainerBuilder(): Task[JavaContainerBuilder] = Task.Anon {
      javaContainerBuilderHook(getJavaBuilder())()
    }

    def getPostHookJibContainerBuilder(): Task[JibContainerBuilder] = Task.Anon {
      jibContainerBuilderHook(getJibLayers(getPostHookJavaContainerBuilder()))()
    }

    def buildImage: T[BuildResult] = T {
      val logger = T.ctx().log
      logger.info("Building image")
      val dockerConf = dockerContainerConfig()
      val buildConf  = buildSettings()

      val jibContainerBuilderPostHook = getPostHookJibContainerBuilder()()
      MDBuild.setContainerParams(dockerConf, buildConf, logger, jibContainerBuilderPostHook)

      val containerizer = buildConf.targetImage match {
        case md.JibImage.DockerDaemonImage(qualifiedName, _, _) =>
          Containerizer.to(DockerDaemonImage.named(qualifiedName))
        case md.JibImage.RegistryImage(qualifiedName, credentialsEnvironment) =>
          val image = RegistryImage.named(jib.api.ImageReference.parse(qualifiedName))
          credentialsEnvironment match {
            case Some((username, password)) =>
              image.addCredentialRetriever(MDShared.retrieveEnvCredentials(username, password))
            case None =>
          }
          Containerizer.to(image)
        case md.JibImage.TargetTarFile(qualifiedName, filename) =>
          Containerizer.to(TarImage.at((T.dest / filename).wrapped).named(qualifiedName))
      }

      val containerizerWithLogs = containerizer.addEventHandler(MDLogging.getLogger(logger))

      val containerizerWithTags = buildConf.tags.foldRight(containerizerWithLogs) { (tag, c) =>
        c.withAdditionalTag(tag)
      }
      val containerizerWithToolSet = containerizerWithTags
        .setAllowInsecureRegistries(buildConf.setAllowInsecureRegistries)
        .setToolName(MDShared.toolName)
      // TODO: check how we could combine jib and mill caching

      val container = jibContainerBuilderPostHook.containerize(containerizerWithToolSet)

      BuildResult(
        image = container.getTargetImage.toString(),
        imageId = container.getImageId.toString(),
        imageDigest = container.getDigest.toString(),
        path = buildConf.targetImage match {
          case md.JibImage.TargetTarFile(_, filename) => Some(PathRef(T.dest / filename))
          case _                                      => None
        },
      )

    }
  }
}
