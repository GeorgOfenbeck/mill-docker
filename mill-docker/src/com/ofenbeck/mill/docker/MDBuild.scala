package com.ofenbeck.mill.docker

import com.google.cloud.tools.jib.api.Containerizer
import com.google.cloud.tools.jib.api.ImageReference
import com.google.cloud.tools.jib.api.JavaContainerBuilder
import com.google.cloud.tools.jib.api.Jib
import com.google.cloud.tools.jib.api.JibContainerBuilder
import com.google.cloud.tools.jib.api.LogEvent
import coursier.core.shaded.sourcecode.File
import mill.scalalib.JavaModule
import os.group.set

import java.time.Instant
import scala.jdk.CollectionConverters._
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer
import com.google.cloud.tools.jib.api.RegistryImage
import com.google.cloud.tools.jib.api.DockerDaemonImage
import com.google.cloud.tools.jib.api.TarImage
import com.google.cloud.tools.jib.api.buildplan.ImageFormat
import com.google.cloud.tools.jib.api.buildplan.Port
import com.google.cloud.tools.jib.api.MainClassFinder
import com.google.cloud.tools.jib.api.buildplan.LayerObject
import com.google.cloud.tools.jib.api.buildplan.FileEntry
import mill.define.Command
import mill._, define.Task

object MDBuild {

  def javaBuild(
      dockerSettings: DockerSettings,
      buildSettings: BuildSettings,
      logger: mill.api.Logger,
  ): JavaContainerBuilder = {

    val javaBuilder = buildSettings.sourceImage match {
      case JibImage.RegistryImage(qualifiedName, credentialsEnvironment) =>
        val image = RegistryImage.named(ImageReference.parse(qualifiedName))
        credentialsEnvironment match {
          case Some((username, password)) =>
            image.addCredentialRetriever(MDShared.retrieveEnvCredentials(username, password))
          case None =>
        }
        JavaContainerBuilder.from(image)
      case JibImage.DockerDaemonImage(qualifiedName, useFallBack, fallBackEnvCredentials) =>
        JavaContainerBuilder.from(DockerDaemonImage.named(ImageReference.parse(qualifiedName)))
      case JibImage.SourceTarFile(path) =>
        JavaContainerBuilder.from(TarImage.at(path.path.wrapped))
    }

    // Create all the layers for the container
    val (upstreamClassSnapShot, upstreamClass) =
      buildSettings.upstreamAssemblyClasspath.partition(MDShared.isSnapshotDependency(_))

    val (moduleDeps, libraryDeps) =
      upstreamClass.partition(MDShared.isModuleDep(_))

    javaBuilder.addDependencies(libraryDeps.filter(x => os.exists(x.path)).map(x => x.path.wrapped).toList.asJava)
    javaBuilder.addSnapshotDependencies(upstreamClassSnapShot.map(_.path.wrapped).toList.asJava)
    javaBuilder.addToClasspath(moduleDeps.filter(x => os.isDir(path)).map(_.path.wrapped).toList.asJava)

    buildSettings.unmanagedDependencies
      .filter(p => os.exists(p.path))
      .map(_.path)
      .foreach { path =>
        if (os.isDir(path))
          os.walk(path).filter(file => file.toIO.isFile()).map(x => javaBuilder.addSnapshotDependencies(x.wrapped))
        if (os.isFile(path))
          javaBuilder.addSnapshotDependencies(path.wrapped)
      }

    buildSettings.resourcesPaths
      .filter(p => os.exists(p.path))
      .map(_.path.wrapped)
      .foreach(path => javaBuilder.addResources(path)) // TODO: double check this can be called multiple times

    if (os.exists(buildSettings.compiledClasses.path)) {
      javaBuilder.addClasses(buildSettings.compiledClasses.path.wrapped)
      setMainClass(buildSettings, javaBuilder, logger)
    } else {
      logger.error("No compiled classes found - skipping adding classes and setting main class")
    }

    javaBuilder.addJvmFlags(dockerSettings.jvmOptions.asJava)

    javaBuilder

  }

  def customizeLayers(
      containerBuilder: JibContainerBuilder,
      buildSettings: BuildSettings,
      logger: mill.api.Logger,
  ): (JibContainerBuilder, Vector[FileEntriesLayer], Vector[String]) = {

    val jibBuilder = buildSettings.sourceImage match {
      case JibImage.RegistryImage(qualifiedName, _) =>
        Jib.from(RegistryImage.named(ImageReference.parse(qualifiedName)))
      case JibImage.DockerDaemonImage(qualifiedName, _, _) =>
        Jib.from(DockerDaemonImage.named(ImageReference.parse(qualifiedName)))
      case JibImage.SourceTarFile(path) =>
        Jib.from(TarImage.at(path.path.wrapped))
    }

    val containerBuildPlan = containerBuilder.toContainerBuildPlan()

    val jiblayers = containerBuildPlan.getLayers().asScala.toVector
    jiblayers.foreach {
      case fl: FileEntriesLayer =>
        logger.info(s"Layer:  ${fl.getName()}")
        fl.getEntries().asScala.foreach { entry =>
          logger.info(s"Layer: ${fl.getName()} Entry: ${entry.getSourceFile()}, ${entry.getExtractionPath()}")
        }
      case _: LayerObject =>
        logger.error("LayerObject in customizeLayers not supported")
    }
    val fileEntriesLayer = jiblayers.collect { case fl: FileEntriesLayer => fl }

    // can be null if no entrypoints are present
    val entrypoints = Option(containerBuildPlan.getEntrypoint()).map(_.asScala.toVector).getOrElse(Vector.empty)
    entrypoints.foreach(entrypoint => logger.info(s"Entrypoint: $entrypoint"))

    (jibBuilder, fileEntriesLayer, entrypoints)
  }

  def setMainClass(buildSettings: BuildSettings, javaBuilder: JavaContainerBuilder, logger: mill.api.Logger): Unit =
    if (buildSettings.mainClass.isDefined) {
      if (buildSettings.autoDetectMainClass) {
        logger.error("Both mainClass and autoDetectMainClass are set. AutoDetectMainClass will be ignored")
      }
      javaBuilder.setMainClass(buildSettings.mainClass.get)
    } else {
      val classfiles =
        os.walk(buildSettings.compiledClasses.path).filter(file => file.toIO.isFile()).map(x => x.wrapped).toList.asJava
      val mainfound = MainClassFinder.find(classfiles, MDLogging.getEventLogger(logger))
      mainfound.getType() match {
        case MainClassFinder.Result.Type.MAIN_CLASS_FOUND =>
          logger.info(s"Main class found = ${mainfound.getFoundMainClass()}")
          javaBuilder.setMainClass(mainfound.getFoundMainClass())
        case MainClassFinder.Result.Type.MAIN_CLASS_NOT_FOUND =>
          logger.error("No main class found")
        case MainClassFinder.Result.Type.MULTIPLE_MAIN_CLASSES =>
          logger.info(
            s"Multiple main classes found - setting the first one `${mainfound.getFoundMainClasses().get(0)}`",
          )
          logger.info("Set MainClass in DockerSettings if you want to select a specific one")
          javaBuilder.setMainClass(mainfound.getFoundMainClasses().get(0))
      }
    }

  def setContainerParams(
      dockerSettings: DockerSettings,
      buildSettings: BuildSettings,
      logger: mill.api.Logger,
      containerBuilder: JibContainerBuilder,
  ): JibContainerBuilder = {

    containerBuilder.setEnvironment(dockerSettings.envVars.asJava)
    if (!dockerSettings.platforms.isEmpty) {
      containerBuilder.setPlatforms(
        dockerSettings.platforms
          .map(p => new com.google.cloud.tools.jib.api.buildplan.Platform(p.architecture, p.os))
          .asJava,
      )
    }
    containerBuilder.setLabels(dockerSettings.labels.asJava)
    containerBuilder.setUser(dockerSettings.user.orNull)
    containerBuilder.setProgramArguments(dockerSettings.jibProgramArgs.asJava)
    containerBuilder.setFormat(dockerSettings.internalImageFormat match {
      case JibImageFormat.Docker => ImageFormat.Docker
      case JibImageFormat.OCI    => ImageFormat.OCI
    })
    import com.google.cloud.tools.jib.api.buildplan.Port.tcp
    import com.google.cloud.tools.jib.api.buildplan.Port.udp
    val ports: Set[Port] =
      dockerSettings.exposedPorts.map(p => tcp(p)).toSet ++ dockerSettings.exposedUdpPorts.map(p => udp(p)).toSet
    containerBuilder.setExposedPorts(ports.asJava)
    containerBuilder.setCreationTime(MDShared.useCurrentTimestamp(buildSettings.useCurrentTimestamp))
    dockerSettings.entrypoint.foreach(entrypoint => containerBuilder.setEntrypoint(dockerSettings.entrypoint.asJava))
    containerBuilder
  }

}
