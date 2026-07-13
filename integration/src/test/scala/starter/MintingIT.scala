package starter

import org.scalatest.funsuite.AnyFunSuite
import scalus.cardano.ledger.Transaction
import scalus.utils.await

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.*

/** Integration test for minting and burning tokens using Yaci DevKit testcontainer.
  *
  * The container starts automatically before tests and stops after. No manual Yaci DevKit setup
  * required.
  */
class MintingIT extends AnyFunSuite with YaciDevKitTest {

    private def submitTx(appCtx: AppCtx, tx: Transaction): Either[String, String] =
        appCtx.provider.submit(tx).await(30.seconds).map(_.toHex).left.map(_.toString)

    private def waitBlock(): Unit = Thread.sleep(3000)

    /** Total quantity of tokens under the minting policy held at the app address.
      *
      * The devnet may be reused across runs (see [[TestcontainersReuse]]), so the ledger can hold
      * leftover tokens from earlier interrupted runs - assertions compare balances, not absolutes.
      */
    private def tokenBalance(appCtx: AppCtx): Either[String, Long] =
        appCtx.provider
            .findUtxos(appCtx.address)
            .await(10.seconds)
            .left
            .map(_.toString)
            .map { utxos =>
                val policyId = appCtx.mintingScript.script.scriptHash
                utxos.values
                    .map(_.value.assets.assets.get(policyId).map(_.values.sum).getOrElse(0L))
                    .sum
            }

    test("mint and burn tokens") {
        val appCtx = createAppCtx("CO2 Tonne")
        val txBuilder = Transactions(appCtx)

        val result = for
            initialBalance <- tokenBalance(appCtx)
            // mint 1000 tokens
            tx <- txBuilder.makeMintingTx(1000)
            _ = println(s"minting tx: ${tx.id.toHex}")
            _ <- submitTx(appCtx, tx)
            _ = waitBlock()
            // burn 1000 tokens
            burnedTx <- txBuilder.makeBurningTx(-1000)
            _ = println(s"burning tx: ${burnedTx.id.toHex}")
            _ <- submitTx(appCtx, burnedTx)
            _ = waitBlock()
            finalBalance <- tokenBalance(appCtx)
        yield (initialBalance, finalBalance)

        result match
            case Right((initialBalance, finalBalance)) =>
                assert(
                  finalBalance == initialBalance,
                  s"Expected token balance to return to $initialBalance but was $finalBalance"
                )
            case Left(err) => fail(err)
    }
}
