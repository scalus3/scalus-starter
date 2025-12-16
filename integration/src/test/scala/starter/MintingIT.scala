package starter

import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import scalus.cardano.ledger.Transaction
import scalus.utils.await

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.*
import scala.util.Try

/** This integration test mints and burns tokens.
  *
  * It requires a Blockfrost API available. Use Yaci Devkit to run this test.
  *
  * {{{
  *  devkit
  *  create-node
  *  start
  * }}}
  */
class MintingIT extends AnyFunSuite with BeforeAndAfterAll {

    private val appCtx = AppCtx.yaciDevKit("CO2 Tonne")
    private val txBuilder = TxBuilder(appCtx)

    private def submitTx(tx: Transaction): Either[String, String] =
        appCtx.provider.submit(tx).await(30.seconds).map(_.toHex).left.map(_.toString)

    private def waitBlock(): Unit = Thread.sleep(3000)

    private var skipTests = false
    private var skipReason = ""

    override def beforeAll(): Unit = {
        // Check if Yaci DevKit is available and account is funded
        val utxosResult = Try {
            appCtx.provider
                .findUtxos(appCtx.address, None, None, None, None)
                .await(10.seconds)
        }
        utxosResult match {
            case scala.util.Failure(e) =>
                skipTests = true
                skipReason =
                    s"This test requires a Blockfrost API available. Start Yaci Devkit before running this test. Error: ${e.getMessage}"
            case scala.util.Success(Left(e)) =>
                skipTests = true
                skipReason = s"Failed to query UTXOs from Yaci Devkit: ${e.getMessage}"
            case scala.util.Success(Right(utxos)) =>
                val totalAda = utxos.values.map(_.value.coin.value).sum
                if (totalAda < 10_000_000) { // Need at least 10 ADA
                    skipTests = true
                    skipReason =
                        s"Account has insufficient funds (${totalAda} lovelace). Fund the test account first using Yaci Devkit."
                }
        }
    }

    test("mint and burn tokens") {
        if (skipTests) cancel(skipReason)
        val result = for
            // mint 1000 tokens
            tx <- txBuilder.makeMintingTx(1000)
            _ = println(s"minting tx: ${tx.id.toHex}")
            _ <- submitTx(tx)
            _ = waitBlock()
            // burn 1000 tokens
            burnedTx <- txBuilder.makeBurningTx(-1000)
            _ = println(s"burning tx: ${burnedTx.id.toHex}")
            _ <- submitTx(burnedTx)
            _ = waitBlock()
            // verify burn succeeded by checking we can query UTXOs
            utxos <- appCtx.provider
                .findUtxos(appCtx.address, None, None, None, None)
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
