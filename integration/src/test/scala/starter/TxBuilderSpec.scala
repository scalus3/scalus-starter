package starter

import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import scalus.builtin.ByteString
import scalus.cardano.ledger.AssetName
import scalus.utils.await

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.*
import scala.util.Try

class TxBuilderSpec extends AnyFunSuite with ScalaCheckPropertyChecks with BeforeAndAfterAll {

    private val appCtx = AppCtx.yaciDevKit("CO2 Tonne")
    private val txBuilder = TxBuilder(appCtx)

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

    test("create minting transaction") {
        if (skipTests) cancel(skipReason)
        txBuilder.makeMintingTx(1000) match
            case Right(tx) =>
                println(s"minting tx: ${tx.id.toHex}")
                val mint = tx.body.value.mint
                assert(mint.nonEmpty)
                val expectedAssetName = AssetName(appCtx.tokenNameByteString)
                val mintedAmount = mint
                    .flatMap(_.assets.get(appCtx.mintingScript.policyId))
                    .flatMap(_.get(expectedAssetName))
                assert(mintedAmount.contains(1000L))
                // Check that the transaction has witnesses
                assert(tx.witnessSet.vkeyWitnesses.toSet.nonEmpty)
            case Left(err) => fail(err)
    }

}
