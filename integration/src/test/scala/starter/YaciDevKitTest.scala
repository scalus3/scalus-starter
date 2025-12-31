package starter

import com.bloxbean.cardano.client.account.Account
import org.scalatest.Suite
import scalus.cardano.address.Network
import scalus.cardano.ledger.{CardanoInfo, ProtocolParams, SlotConfig}
import scalus.cardano.node.BlockfrostProvider
import scalus.cardano.txbuilder.TransactionSigner
import scalus.cardano.wallet.BloxbeanAccount
import scalus.testing.yaci.{YaciConfig, YaciDevKit}
import scalus.utils.await

import scala.concurrent.ExecutionContext.Implicits.global

/** Base trait for integration tests using Yaci DevKit with ScalaTest
  *
  * Usage:
  * {{{
  * class MyIntegrationTest extends AnyFunSuite with YaciDevKitSpec {
  *   test("submit transaction to devnet") {
  *     val appCtx = createAppCtx("TokenName")
  *     // Use appCtx for testing
  *   }
  * }
  * }}}
  */
trait YaciDevKitTest extends YaciDevKit { self: Suite =>

    /** Override this to customize the Yaci DevKit configuration */
    override def yaciConfig: YaciConfig = YaciConfig()

    /** Standard derivation path for Cardano payment keys */
    private val PaymentDerivationPath = "m/1852'/1815'/0'/0/0"

    /** Create AppCtx from the running YaciCardanoContainer */
    def createAppCtx(tokenName: String): AppCtx = {
        val context = createTestContext()
        new AppCtx(
          context.cardanoInfo,
          context.provider,
          context.account.account,
          context.signer,
          tokenName
        )
    }
}
