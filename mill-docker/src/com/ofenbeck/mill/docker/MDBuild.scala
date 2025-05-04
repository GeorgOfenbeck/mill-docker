package com.ofenbeck.mill.docker

import com.google.cloud.tools.jib.api._
import com.google.cloud.tools.jib.api.buildplan.{FileEntriesLayer, ImageFormat, LayerObject, Port}

import scala.jdk.CollectionConverters._

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
      case JibImage.DockerDaemonImage(qualifiedName, _, _) =>
        JavaContainerBuilder.from(DockerDaemonImage.named(ImageReference.parse(qualifiedName)))
      case JibImage.SourceTarFile(path) =>
        JavaContainerBuilder.from(TarImage.at(path.path.wrapped))
    }

    // Create all the layers for the container
    javaBuilder.addDependencies(buildSettings.dependencies.map(_.path.wrapped).asJava)
    javaBuilder.addSnapshotDependencies(buildSettings.snapshotDependencies.map(_.path.wrapped).asJava)
    buildSettings.resources.map(_.path.wrapped).toSet.foreach(javaBuilder.addResources)
    javaBuilder.addProjectDependencies(buildSettings.projectDependencies.map(_.path.wrapped).asJava)
    buildSettings.classes.map(_.path.wrapped).toSet.foreach(javaBuilder.addClasses)
    javaBuilder.addToClasspath(buildSettings.extraFiles.map(_.path.wrapped).asJava)
    javaBuilder.addJvmFlags(dockerSettings.jvmOptions.asJava)

    setMainClass(buildSettings, javaBuilder, logger)

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
      val classfiles = buildSettings.mainClassSearchPaths
        .flatMap(pathRef => os.walk(pathRef.path))
        .collect { case pathRef if pathRef.toIO.isFile => pathRef.wrapped }
        .asJava
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
    import com.google.cloud.tools.jib.api.buildplan.Port.{tcp, udp}
    val ports: Set[Port] =
      dockerSettings.exposedPorts.map(p => tcp(p)).toSet ++ dockerSettings.exposedUdpPorts.map(p => udp(p)).toSet
    containerBuilder.setExposedPorts(ports.asJava)
    containerBuilder.setCreationTime(MDShared.useCurrentTimestamp(buildSettings.useCurrentTimestamp))
    containerBuilder.setEntrypoint(dockerSettings.entrypoint.asJava)
    containerBuilder
  }

}
