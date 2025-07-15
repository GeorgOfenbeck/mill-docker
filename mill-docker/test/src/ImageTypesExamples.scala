package com.ofenbeck.mill.docker
import mill.testkit.ExampleTester
import utest._

object ImageTypesExamples extends TestSuite {

  def tests: Tests = Tests {
    test("image types") {
      val resourceFolder = os.Path(sys.env("MILL_TEST_RESOURCE_DIR"))

      ExampleTester.run(
        daemonMode = true,
        workspaceSourcePath = resourceFolder / "examples-buildsettings",
        millExecutable = os.Path(sys.env("MILL_EXECUTABLE_PATH"))
      )
    }
  } 
}
  
