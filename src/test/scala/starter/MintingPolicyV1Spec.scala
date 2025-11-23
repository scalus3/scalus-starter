package starter

import com.bloxbean.cardano.client.account.Account
import org.scalacheck.Arbitrary
import org.scalatest.funsuite.AnyFunSuite
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import scalus.*
import scalus.builtin.ByteString.utf8
import scalus.builtin.Data.toData
import scalus.builtin.{ByteString, Data, platform, given}
import scalus.ledger.api.v1.*
import scalus.ledger.api.v1.Value.*
import scalus.prelude.*
import scalus.testing.kit.ScalusTest
import scalus.uplc.*
import scalus.uplc.eval.*

import scala.language.implicitConversions

class MintingPolicyV1Spec extends AnyFunSuite with ScalaCheckPropertyChecks with ScalusTest {
    import Expected.*

    private val account = new Account()

    private val tokenName = utf8"CO2 Tonne"

    private val adminPubKeyHash: PubKeyHash = PubKeyHash(
      ByteString.fromArray(account.hdKeyPair().getPublicKey.getKeyHash)
    )

    private val config = MintingConfig(adminPubKeyHash, tokenName)

    private val mintingScript =
        MintingPolicyV1Generator.makeMintingPolicyScript(adminPubKeyHash, tokenName)

    test("should fail when minted token name is not correct") {
        val wrongTokenName = tokenName ++ utf8"extra"
        val ctx = makeScriptContext(
          mint = Value(mintingScript.scriptHash, wrongTokenName, 1000),
          signatories = List(adminPubKeyHash)
        )

        val exception = intercept[Exception] {
            MintingPolicyV1.validate(config.toData)(Data.unit, ctx.toData)
        }
        assert(exception.getMessage == "Token name not found")

        assertEval(mintingScript.script $ Data.unit $ ctx.toData, Failure("Error evaluated"))
    }

    test("should fail when extra tokens are minted/burned") {
        val ctx = makeScriptContext(
          mint = Value(mintingScript.scriptHash, tokenName, 1000)
              + Value(mintingScript.scriptHash, ByteString.fromString("Extra"), 1000),
          signatories = List(adminPubKeyHash)
        )

        val exception = intercept[Exception] {
            MintingPolicyV1.validate(config.toData)(Data.unit, ctx.toData)
        }
        assert(exception.getMessage == "Multiple tokens found")

        assertEval(mintingScript.script $ Data.unit $ ctx.toData, Failure("Error evaluated"))
    }

    test("should fail when admin signature is not provided") {
        val ctx = makeScriptContext(
          mint = Value(mintingScript.scriptHash, tokenName, 1000),
          signatories = List.Nil
        )

        val exception = intercept[Exception] {
            MintingPolicyV1.validate(config.toData)(Data.unit, ctx.toData)
        }
        assert(exception.getMessage == "Not signed by admin")

        assertEval(mintingScript.script $ Data.unit $ ctx.toData, Failure("Error evaluated"))
    }

    test("should fail when admin signature is not correct") {
        val ctx = makeScriptContext(
          mint = Value(mintingScript.scriptHash, tokenName, 1000),
          signatories = List(PubKeyHash(platform.blake2b_224(ByteString.fromString("wrong"))))
        )

        val exception = intercept[Exception] {
            MintingPolicyV1.validate(config.toData)(Data.unit, ctx.toData)
        }
        assert(exception.getMessage == "Not signed by admin")

        assertEval(mintingScript.script $ Data.unit $ ctx.toData, Failure("Error evaluated"))
    }

    test("should succeed when minted token name is correct and admin signature is correct") {
        val ctx = makeScriptContext(
          mint = Value(mintingScript.scriptHash, tokenName, 1000),
          signatories = List(adminPubKeyHash)
        )
        // run the minting policy script as a Scala function
        // here you can use debugger to debug the minting policy script
        MintingPolicyV1.validate(config.toData)(Data.unit, ctx.toData)
        // run the minting policy script as a Plutus script
        assertEval(
          mintingScript.script $ Data.unit $ ctx.toData,
          Success(ExBudget.fromCpuAndMemory(cpu = 21849291, memory = 82376))
        )
    }

    test(s"validator size is 1312 bytes") {
        val size = mintingScript.script.cborEncoded.length
        assert(size == 1312)
    }

    private def makeScriptContext(mint: Value, signatories: List[PubKeyHash]) =
        ScriptContext(
          txInfo = TxInfo(
            inputs = List.Nil,
            outputs = List.Nil,
            fee = Value.lovelace(188021),
            mint = mint,
            dcert = List.Nil,
            withdrawals = List.Nil,
            validRange = Interval.always,
            signatories = signatories,
            data = List.Nil,
            id = random[TxId]
          ),
          purpose = ScriptPurpose.Minting(mintingScript.scriptHash)
        )

    private def assertEval(p: Program, expected: Expected): Unit = {
        val result = p.evaluateDebug
        (result, expected) match
            case (result: Result.Success, Expected.Success(expected)) =>
                assert(result.budget == expected)
            case (result: Result.Failure, Expected.Failure(expected)) =>
                assert(result.exception.getMessage == expected)
            case _ => fail(s"Unexpected result: $result, expected: $expected")
    }
    private given arbTxId: Arbitrary[TxId] = Arbitrary(genByteStringOfN(32).map(TxId.apply))
}
