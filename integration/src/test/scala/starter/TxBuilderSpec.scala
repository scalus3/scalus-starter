package starter

import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import scalus.builtin.ByteString

import scala.util.Try

class TxBuilderSpec extends AnyFunSuite with ScalaCheckPropertyChecks with BeforeAndAfterAll {

    private val appCtx = AppCtx.yaciDevKit("CO2 Tonne")

    private val pubKey: ByteString = ByteString.fromArray(appCtx.account.publicKeyBytes())

    private val txBuilder = TxBuilder(appCtx)

    override def beforeAll(): Unit = {
        val params = Try(txBuilder.protocolParams)
        if (!params.isSuccess) {
            cancel(
              "This test requires a Blockfrost API available. Start Yaci Devkit before running this test."
            )
        }
    }

    test("create minting transaction") {
        txBuilder.makeMintingTx(1000) match
            case Right(tx) =>
                assert(
                  ByteString.fromArray(tx.getBody.getMint.get(0).getAssets.get(0).getNameAsBytes) ==
                      appCtx.tokenNameByteString
                )
                assert(tx.getWitnessSet.getVkeyWitnesses.size() == 1)
                assert(
                  ByteString.fromArray(tx.getWitnessSet.getVkeyWitnesses.get(0).getVkey) ==
                      pubKey
                )
            case Left(err) => fail(err)
    }

}
