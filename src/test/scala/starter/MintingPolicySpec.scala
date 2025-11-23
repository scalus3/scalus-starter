package starter

import com.bloxbean.cardano.client.account.Account
import org.scalatest.funsuite.AnyFunSuite
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import scalus.*
import scalus.builtin.Data.toData
import scalus.builtin.{ByteString, Data, PlatformSpecific, given}
import scalus.cardano.ledger.ExUnits
import scalus.ledger.api.v1.Value.*
import scalus.ledger.api.v3.*
import scalus.prelude.*
import scalus.testing.kit.ScalusTest
import scalus.uplc.*
import scalus.uplc.eval.*

import scala.language.implicitConversions

enum Expected {
    case Success(budget: ExUnits)
    case Failure(reason: String)
}

class MintingPolicySpec extends AnyFunSuite with ScalaCheckPropertyChecks with ScalusTest {
    import Expected.*

    private val account = new Account()

    private val crypto = summon[PlatformSpecific] // platform specific crypto functions

    private val tokenName = ByteString.fromString("CO2 Tonne")

    private val adminPubKeyHash: PubKeyHash = PubKeyHash(
      ByteString.fromArray(account.hdKeyPair().getPublicKey.getKeyHash)
    )

    private val config = MintingConfig(adminPubKeyHash, tokenName)

    private val mintingScript =
        MintingPolicyGenerator.makeMintingPolicyScript(adminPubKeyHash, tokenName)

    test("should fail when minted token name is not correct") {
        val wrongTokenName = tokenName ++ ByteString.fromString("extra")
        val ctx = makeScriptContext(
          mint = Value(mintingScript.scriptHash, wrongTokenName, 1000),
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
          mint = Value(mintingScript.scriptHash, tokenName, 1000)
              + Value(mintingScript.scriptHash, ByteString.fromString("Extra"), 1000),
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
          mint = Value(mintingScript.scriptHash, tokenName, 1000),
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
          mint = Value(mintingScript.scriptHash, tokenName, 1000),
          signatories = List(PubKeyHash(crypto.blake2b_224(ByteString.fromString("wrong"))))
        )

        val exception = intercept[Exception] {
            MintingPolicy.validate(config.toData)(ctx.toData)
        }
        assert(exception.getMessage == "Not signed by admin")

        assertEval(mintingScript.program $ ctx.toData, Failure("Error evaluated"))
    }

    test("should succeed when minted token name is correct and admin signature is correct") {
        val ctx = makeScriptContext(
          mint = Value(mintingScript.scriptHash, tokenName, 1000),
          signatories = List(adminPubKeyHash)
        )
        // run the minting policy script as a Scala function
        // here you can use debugger to debug the minting policy script
        MintingPolicy.validate(config.toData)(ctx.toData)
        // run the minting policy script as a Plutus script
        assertEval(
          mintingScript.program $ ctx.toData,
          Success(ExUnits(steps = 19694280, memory = 67472))
        )
    }

    test(s"validator size is 1409 bytes") {
        val size = mintingScript.program.cborEncoded.length
        assert(size == 1409)
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
          scriptInfo = ScriptInfo.MintingScript(mintingScript.scriptHash)
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
