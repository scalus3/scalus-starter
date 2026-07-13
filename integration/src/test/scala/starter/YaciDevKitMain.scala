package starter

import scalus.testing.yaci.{YaciConfig, YaciContainer}

import java.nio.file.{Files, Paths}
import scala.io.StdIn

/** Starts a Yaci DevKit devnet for manual interaction.
  *
  * Run with:
  * {{{
  * sbtn "integration/Test/runMain starter.YaciDevKitMain"
  * }}}
  *
  * The container is started with reuse enabled (name: `scalus-yaci-devkit`), so it keeps running
  * after this process exits and integration tests attach to it instantly. Stop it with:
  * {{{
  * docker rm -f scalus-yaci-devkit
  * }}}
  *
  * Requires `testcontainers.reuse.enable=true` in `~/.testcontainers.properties`, otherwise the
  * container is reaped by Ryuk when this process exits.
  */
object YaciDevKitMain {

    private val testMnemonic =
        "test test test test test test test test test test test test " +
            "test test test test test test test test test test test sauce"

    def main(args: Array[String]): Unit = {
        checkReuseEnabled()
        println("Starting Yaci DevKit container (first start can take a minute)...")
        val container = YaciContainer.acquire(YaciConfig(reuseContainer = true))
        println()
        println(s"Yaci DevKit is running (container: scalus-yaci-devkit)")
        println(s"  Yaci Store API (Blockfrost-compatible): ${container.getYaciStoreApiUrl}")
        println(s"  Local cluster admin API:                ${container.getLocalClusterApiUrl}")
        println()
        println(s"Funded test wallet mnemonic (used by integration tests):")
        println(s"  $testMnemonic")
        println()
        println("The container keeps running after exit. Stop it with: docker rm -f scalus-yaci-devkit")
        println("Press Enter to exit...")
        StdIn.readLine()
    }

    private def checkReuseEnabled(): Unit = {
        val props = Paths.get(sys.props("user.home"), ".testcontainers.properties")
        val reuseEnabled = Files.exists(props) && Files
            .readString(props)
            .linesIterator
            .exists(_.replace(" ", "") == "testcontainers.reuse.enable=true")
        if !reuseEnabled then
            println(
              """WARNING: testcontainers reuse is not enabled - the container will be
                |removed when this process exits. To keep the devnet running, add
                |  testcontainers.reuse.enable=true
                |to ~/.testcontainers.properties and restart.""".stripMargin
            )
    }
}
