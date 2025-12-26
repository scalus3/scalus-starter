package starter

import org.scalatest.funsuite.AnyFunSuite
import scalus.cardano.ledger.Transaction
import scalus.testing.yaci.{TestContext, YaciDevKit}
import scalus.utils.await

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.*

/** Integration test for minting and burning tokens using Yaci DevKit testcontainer.
  */
class MintingIT extends AnyFunSuite with YaciDevKit {

    private def submitTx(ctx: TestContext, tx: Transaction): Either[String, String] =
        ctx.submitTx(tx)

    private def waitBlock(): Unit = Thread.sleep(3000)

    test("mint and burn tokens") {
        val ctx = createTestContext()
        val appCtx = AppCtx.fromTestContext(ctx, "CO2 Tonne")
        val txBuilder = Transactions(appCtx)

        val result = for
            // mint 1000 tokens
            tx <- txBuilder.makeMintingTx(1000)
            _ = println(s"minting tx: ${tx.id.toHex}")
            _ <- submitTx(ctx, tx)
            _ = waitBlock()
            // burn 1000 tokens
            burnedTx <- txBuilder.makeBurningTx(-1000)
            _ = println(s"burning tx: ${burnedTx.id.toHex}")
            _ <- submitTx(ctx, burnedTx)
            _ = waitBlock()
            // verify burn succeeded by checking we can query UTXOs
            utxos <- ctx.provider
                .findUtxos(ctx.address, None, None, None, None)
                .await(10.seconds)
                .left
                .map(_.toString)
        yield utxos

        result match
            case Right(utxos) =>
                // Check that no UTXOs contain the minted token
                val tokenUtxos = utxos.filter { case (_, utxo) =>
                    utxo.value.assets.assets.contains(appCtx.mintingScript.policyId)
                }
                assert(tokenUtxos.isEmpty, s"Expected no token UTXOs but found: $tokenUtxos")
            case Left(err) => fail(err)
    }
}
