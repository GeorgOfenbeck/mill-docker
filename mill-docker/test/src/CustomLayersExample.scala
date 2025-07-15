package com.ofenbeck.mill.docker
import mill.testkit.ExampleTester
import utest._

object CustomLayersExample extends TestSuite {

  def tests: Tests = Tests {
    test("customizing layers") {
      val resourceFolder = os.Path(sys.env("MILL_TEST_RESOURCE_DIR"))

      ExampleTester.run(
        daemonMode = true,
        workspaceSourcePath = resourceFolder / "examples-customlayers",
        millExecutable = os.Path(sys.env("MILL_EXECUTABLE_PATH"))
      )
    }
  } 
}
  
