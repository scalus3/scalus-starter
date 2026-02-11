package starter

import org.scalatest.funsuite.AnyFunSuite
import scalus.cardano.ledger.AssetName

trait TransactionsTestBase { self: AnyFunSuite =>
    def createAppCtx(tokenName: String): AppCtx

    test("create minting transaction") {
        val appCtx = createAppCtx("CO2 Tonne")
        val txBuilder = Transactions(appCtx)

        txBuilder.makeMintingTx(1000) match
            case Right(tx) =>
                println(s"minting tx: ${tx.id.toHex}")
                val mint = tx.body.value.mint
                assert(mint.nonEmpty)
                val expectedAssetName = AssetName(appCtx.tokenNameByteString)
                val mintedAmount = mint
                    .flatMap(_.assets.get(appCtx.mintingScript.script.scriptHash))
                    .flatMap(_.get(expectedAssetName))
                assert(mintedAmount.contains(1000L))
                // Check that the transaction has witnesses
                assert(tx.witnessSet.vkeyWitnesses.toSet.nonEmpty)
            case Left(err) => fail(err)
    }
}
