package com.ofenbeck.mill.docker

import mill.testkit.{TestRootModule, UnitTester}
import utest._
import com.ofenbeck.mill.docker.DockerJibModule
import mill.scalalib.ScalaModule
import mill.api.Discover
import mill._

import com.ofenbeck.mill.docker._

object ImageTypesUnitTests extends TestSuite {

  val resourceFolder = os.Path(sys.env("MILL_TEST_RESOURCE_DIR"))

  // Test modules defined outside test methods for Scala 3 compatibility
  object registry2demon extends TestRootModule with ScalaModule with DockerJibModule {
    def scalaVersion = "3.7.1"
    lazy val millDiscover = Discover[this.type]
    object docker extends DockerConfig {
      override def sourceImage = JibImage.RegistryImage("gcr.io/distroless/java:latest")
      override def targetImage = JibImage.DockerDaemonImage("ofenbeck/mill-docker/registry2demon")
    }
  }

  object registry2tar extends TestRootModule with ScalaModule with DockerJibModule {
    def scalaVersion = "3.7.1"
    lazy val millDiscover = Discover[this.type]
    object docker extends DockerConfig {
      def sourceImage = JibImage.RegistryImage("gcr.io/distroless/java:latest")
      def targetImage = JibImage.TargetTarFile("ofenbeck/mill-docker/registry2tar")
    }
  }

  object registry2registry extends TestRootModule with ScalaModule with DockerJibModule {
    def scalaVersion = "3.7.1"
    lazy val millDiscover = Discover[this.type]
    object docker extends DockerConfig {
      def sourceImage = JibImage.RegistryImage("gcr.io/distroless/java:latest")
      def targetImage =
        JibImage.RegistryImage("ofenbeck/registry2registry", Some(("DOCKER_USERNAME", "DOCKER_PASSWORD")))
    }
  }

  object demon2demon extends TestRootModule with ScalaModule with DockerJibModule {
    def scalaVersion = "3.7.1"
    lazy val millDiscover = Discover[this.type]
    object docker extends DockerConfig {
      def sourceImage = JibImage.DockerDaemonImage(
        "gcr.io/distroless/java:latest",
        useFallBack = true,
        fallBackEnvCredentials = Some(("DOCKER_USERNAME", "DOCKER_PASSWORD")),
      )
      def targetImage = JibImage.DockerDaemonImage("ofenbeck/mill-docker/demon2demon")
    }
  }

  object demon2tar extends TestRootModule with ScalaModule with DockerJibModule {
    def scalaVersion = "3.7.1"
    lazy val millDiscover = Discover[this.type]
    object docker extends DockerConfig {
      def sourceImage = JibImage.DockerDaemonImage("gcr.io/distroless/java:latest")
      def targetImage = JibImage.TargetTarFile("ofenbeck/mill-docker/demon2tar")
    }
  }

  object demon2registry extends TestRootModule with ScalaModule with DockerJibModule {
    def scalaVersion = "3.7.1"
    lazy val millDiscover = Discover[this.type]
    object docker extends DockerConfig {
      def sourceImage = JibImage.DockerDaemonImage("gcr.io/distroless/java:latest")
      def targetImage =
        JibImage.RegistryImage("ofenbeck/demon2registry", Some(("DOCKER_USERNAME", "DOCKER_PASSWORD")))
    }
  }

  def tests: Tests = Tests {
    test("registry2demon") {
      UnitTester(registry2demon, resourceFolder / "image-types-project").scoped { eval =>
        val result = eval(registry2demon.docker.buildImage)
        result match {
          case Right(r) => assert(r.value.imageDigest.contains("sha256"))
          case Left(err) => throw new Exception(s"Build failed: $err")
        }
      }
    }

    test("registry2tar") {
      UnitTester(registry2tar, resourceFolder / "image-types-project").scoped { eval =>
        val result = eval(registry2tar.docker.buildImage)
        result match {
          case Right(r) => assert(r.value.imageDigest.contains("sha256"))
          case Left(err) => throw new Exception(s"Build failed: $err")
        }
      }
    }

    test("registry2registry") {
      UnitTester(registry2registry, resourceFolder / "image-types-project").scoped { eval =>
        val result = eval(registry2registry.docker.buildImage)
        result match {
          case Right(r) => assert(r.value.imageDigest.contains("sha256"))
          case Left(err) => throw new Exception(s"Build failed: $err")
        }
      }
    }

    test("demon2demon") {
      UnitTester(demon2demon, resourceFolder / "image-types-project").scoped { eval =>
        val result = eval(demon2demon.docker.buildImage)
        result match {
          case Right(r) => assert(r.value.imageDigest.contains("sha256"))
          case Left(err) => throw new Exception(s"Build failed: $err")
        }
      }
    }

    test("demon2tar") {
      UnitTester(demon2tar, resourceFolder / "image-types-project").scoped { eval =>
        val result = eval(demon2tar.docker.buildImage)
        result match {
          case Right(r) => assert(r.value.imageDigest.contains("sha256"))
          case Left(err) => throw new Exception(s"Build failed: $err")
        }
      }
    }

    test("demon2registry") {
      UnitTester(demon2registry, resourceFolder / "image-types-project").scoped { eval =>
        val result = eval(demon2registry.docker.buildImage)
        result match {
          case Right(r) => assert(r.value.imageDigest.contains("sha256"))
          case Left(err) => throw new Exception(s"Build failed: $err")
        }
      }
    }
  }
}