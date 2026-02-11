package starter

import org.scalatest.funsuite.AnyFunSuite
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import scalus.*
import scalus.uplc.builtin.ByteString
import scalus.uplc.builtin.ByteString.utf8
import scalus.uplc.builtin.Data
import scalus.uplc.builtin.Data.toData
import scalus.uplc.builtin.platform
import scalus.cardano.ledger.ExUnits
import scalus.cardano.onchain.plutus.v1.{PubKeyHash, Value}
import scalus.cardano.onchain.plutus.v1.Value.*
import scalus.cardano.onchain.plutus.v3.*
import scalus.cardano.onchain.plutus.prelude.*
import scalus.cardano.wallet.hd.HdAccount
import scalus.crypto.ed25519.given
import scalus.testing.kit.ScalusTest
import scalus.uplc.*
import scalus.uplc.eval.*

import scala.language.implicitConversions

enum Expected {
    case Success(budget: ExUnits)
    case Failure(reason: String)
}

class MintingPolicyTest extends AnyFunSuite with ScalaCheckPropertyChecks with ScalusTest {
    import Expected.*

    private val account = HdAccount.fromMnemonic(
      "test test test test test test test test test test test test test test test test test test test test test test test sauce"
    )

    private val tokenName = utf8"CO2 Tonne"

    private val adminPubKeyHash: PubKeyHash = PubKeyHash(account.paymentKeyHash)

    private val config = MintingConfig(adminPubKeyHash, tokenName)

    private val mintingScript =
        MintingPolicyGenerator.makeMintingPolicyScript(adminPubKeyHash, tokenName)

    test("should fail when minted token name is not correct") {
        val wrongTokenName = tokenName ++ utf8"extra"
        val ctx = makeScriptContext(
          mint = Value(mintingScript.script.scriptHash, wrongTokenName, 1000),
          signatories = List(adminPubKeyHash)
        )

        val exception = intercept[Exception] {
            MintingPolicy.validate(config.toData)(ctx.toData)
        }
        assert(exception.getMessage == "Token name not found")

        assertEval(mintingScript.program $ ctx.toData, Failure("Error evaluated"))
    }

    test("should fail when extra tokens are minted/burned") {
        val ctx = makeScriptContext(
          mint = Value(mintingScript.script.scriptHash, tokenName, 1000)
              + Value(mintingScript.script.scriptHash, utf8"Extra", 1000),
          signatories = List(adminPubKeyHash)
        )

        val exception = intercept[Exception] {
            MintingPolicy.validate(config.toData)(ctx.toData)
        }
        assert(exception.getMessage == "Multiple tokens found")

        assertEval(mintingScript.program $ ctx.toData, Failure("Error evaluated"))
    }

    test("should fail when admin signature is not provided") {
        val ctx = makeScriptContext(
          mint = Value(mintingScript.script.scriptHash, tokenName, 1000),
          signatories = List.Nil
        )

        val exception = intercept[Exception] {
            MintingPolicy.validate(config.toData)(ctx.toData)
        }
        assert(exception.getMessage == "Not signed by admin")

        assertEval(mintingScript.program $ ctx.toData, Failure("Error evaluated"))
    }

    test("should fail when admin signature is not correct") {
        val ctx = makeScriptContext(
          mint = Value(mintingScript.script.scriptHash, tokenName, 1000),
          signatories = List(PubKeyHash(platform.blake2b_224(ByteString.fromString("wrong"))))
        )

        val exception = intercept[Exception] {
            MintingPolicy.validate(config.toData)(ctx.toData)
        }
        assert(exception.getMessage == "Not signed by admin")

        assertEval(mintingScript.program $ ctx.toData, Failure("Error evaluated"))
    }

    test("should succeed when minted token name is correct and admin signature is correct") {
        val ctx = makeScriptContext(
          mint = Value(mintingScript.script.scriptHash, tokenName, 1000),
          signatories = List(adminPubKeyHash)
        )
        // run the minting policy script as a Scala function
        // here you can use debugger to debug the minting policy script
        MintingPolicy.validate(config.toData)(ctx.toData)
        // run the minting policy script as a Plutus script
        assertEval(
          mintingScript.program $ ctx.toData,
          Success(ExUnits(steps = 19147579, memory = 64148))
        )
    }

    test(s"validator size is ${mintingScript.program.cborEncoded.length} bytes") {
        val size = mintingScript.program.cborEncoded.length
        assert(size == 695)
    }

    private def makeScriptContext(mint: Value, signatories: List[PubKeyHash]) =
        ScriptContext(
          txInfo = TxInfo(
            inputs = List.Nil,
            fee = 188021,
            mint = mint,
            signatories = signatories,
            id = random[TxId]
          ),
          redeemer = Data.unit,
          scriptInfo = ScriptInfo.MintingScript(mintingScript.script.scriptHash)
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
}
