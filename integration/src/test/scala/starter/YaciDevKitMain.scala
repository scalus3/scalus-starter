package starter

import scalus.testing.yaci.{YaciConfig, YaciContainer}

import scala.io.StdIn

/** Starts a Yaci DevKit devnet for manual interaction.
  *
  * Run with:
  * {{{
  * sbtn "integration/Test/runMain starter.YaciDevKitMain"
  * }}}
  *
  * When Testcontainers reuse is enabled (see [[TestcontainersReuse]]), the container is named and
  * kept running after this process exits, and integration tests attach to it instantly. Stop it
  * with `docker rm -f <container name>` (printed on startup). Without reuse the container is
  * anonymous and is removed when this process exits.
  */
object YaciDevKitMain {

    def main(args: Array[String]): Unit = {
        val reuseEnabled = TestcontainersReuse.enabled
        if !reuseEnabled then
            println(
              """WARNING: Testcontainers reuse is not enabled - the container will be
                |removed when this process exits. To keep the devnet running, add
                |  testcontainers.reuse.enable=true
                |to ~/.testcontainers.properties (or set TESTCONTAINERS_REUSE_ENABLE=true)
                |and restart.""".stripMargin
            )
        val config = YaciConfig(reuseContainer = reuseEnabled)
        println("Starting Yaci DevKit container (first start can take a minute)...")
        val container = YaciContainer.acquire(config)
        println()
        val name =
            if reuseEnabled then config.containerName
            else container.getContainerName.stripPrefix("/")
        println(s"Yaci DevKit is running (container: $name)")
        println(s"  Yaci Store API (Blockfrost-compatible): ${container.getYaciStoreApiUrl}")
        println(s"  Local cluster admin API:                ${container.getLocalClusterApiUrl}")
        println()
        println("Funded test wallet mnemonic (used by integration tests):")
        println(s"  ${YaciDevKitTest.testMnemonic}")
        println()
        if reuseEnabled then
            println("The container keeps running after exit.")
            println(s"Stop it with: docker rm -f ${config.containerName}")
        else println("The container is removed when this process exits.")
        println("Press Enter to exit...")
        StdIn.readLine()
    }
}
