package starter

import org.scalatest.Suite
import scalus.cardano.wallet.hd.HdAccount
import scalus.crypto.ed25519.given
import scalus.testing.yaci.{YaciConfig, YaciDevKit}

/** Base trait for integration tests using Yaci DevKit with ScalaTest
  *
  * Usage:
  * {{{
  * class MyIntegrationTest extends AnyFunSuite with YaciDevKitTest {
  *   test("submit transaction to devnet") {
  *     val appCtx = createAppCtx("TokenName")
  *     // Use appCtx for testing
  *   }
  * }
  * }}}
  */
trait YaciDevKitTest extends YaciDevKit { self: Suite =>

    /** Override this to customize the Yaci DevKit configuration.
      *
      * Container reuse is enabled only when Testcontainers reuse is enabled globally (see
      * [[TestcontainersReuse]]). Tests then attach to a devnet started by [[YaciDevKitMain]] and
      * keep it across runs. Otherwise the container is anonymous and ephemeral, so concurrent or
      * leftover containers cannot cause Docker name conflicts.
      */
    override protected def yaciConfig: YaciConfig =
        YaciConfig(reuseContainer = TestcontainersReuse.enabled)

    /** Create AppCtx from the running YaciCardanoContainer */
    def createAppCtx(tokenName: String): AppCtx = {
        val context = createTestContext()
        val account = HdAccount.fromMnemonic(YaciDevKitTest.testMnemonic)
        new AppCtx(
          context.cardanoInfo,
          context.provider,
          account,
          account.signerForUtxos,
          tokenName
        )
    }
}

object YaciDevKitTest {

    /** Default funded wallet mnemonic in Yaci DevKit */
    val testMnemonic: String =
        "test test test test test test test test test test test test " +
            "test test test test test test test test test test test sauce"
}
