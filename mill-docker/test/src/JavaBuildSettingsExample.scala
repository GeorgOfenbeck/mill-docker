package com.ofenbeck.mill.docker
import mill.testkit.ExampleTester
import utest._

object JavaBuildSettingsExample extends TestSuite {

  def tests: Tests = Tests {
    test("example build settings") {
      val resourceFolder = os.Path(sys.env("MILL_TEST_RESOURCE_DIR"))

      ExampleTester.run(
        clientServerMode = true,
        workspaceSourcePath = resourceFolder / "examples-buildsettings",
        millExecutable = os.Path(sys.env("MILL_EXECUTABLE_PATH"))
      )
    }
  } 
}
  
