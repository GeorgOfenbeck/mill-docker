package com.ofenbeck.mill.docker
import mill.testkit.ExampleTester
import utest._

object CustomJavaBuildExample extends TestSuite {

  def tests: Tests = Tests {
    test("custom java build") {
      val resourceFolder = os.Path(sys.env("MILL_TEST_RESOURCE_DIR"))

      ExampleTester.run(
        daemonMode = true,
        workspaceSourcePath = resourceFolder / "examples-jvmagent",
        millExecutable = os.Path(sys.env("MILL_EXECUTABLE_PATH"))
      )
    }
  } 
}
  
