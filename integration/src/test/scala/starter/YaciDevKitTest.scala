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
      * Reuse is enabled so tests attach to a devnet started by [[YaciDevKitMain]] (and keep it
      * across runs) when `testcontainers.reuse.enable=true` is set in
      * `~/.testcontainers.properties`; otherwise the container is ephemeral as before.
      */
    override protected def yaciConfig: YaciConfig = YaciConfig(reuseContainer = true)

    /** Create AppCtx from the running YaciCardanoContainer */
    def createAppCtx(tokenName: String): AppCtx = {
        val context = createTestContext()
        val mnemonic =
            "test test test test test test test test test test test test test test test test test test test test test test test sauce"
        val account = HdAccount.fromMnemonic(mnemonic)
        new AppCtx(
          context.cardanoInfo,
          context.provider,
          account,
          account.signerForUtxos,
          tokenName
        )
    }
}
