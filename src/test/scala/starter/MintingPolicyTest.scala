package starter

import org.scalatest.funsuite.AnyFunSuite
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import scalus.*
import scalus.uplc.builtin.ByteString
import scalus.uplc.builtin.ByteString.utf8
import scalus.uplc.builtin.Data
import scalus.uplc.builtin.Data.toData
import scalus.uplc.builtin.platform
import scalus.cardano.ledger.{AddrKeyHash, AssetName, CardanoInfo, ExUnits}
import scalus.cardano.onchain.plutus.v1.{PubKeyHash, Value}
import scalus.cardano.onchain.plutus.v1.Value.*
import scalus.cardano.onchain.plutus.v3.*
import scalus.cardano.onchain.plutus.prelude.*
import scalus.cardano.txbuilder.{RedeemerPurpose, txBuilder}
import scalus.cardano.wallet.hd.HdAccount
import scalus.crypto.ed25519.given
import scalus.testing.kit.ScalusTest
import scalus.testing.kit.TestUtil.getScriptContextV3
import scalus.uplc.*
import scalus.uplc.eval.*

import scala.language.implicitConversions

enum Expected {
    case Success(budget: ExUnits)
    case Failure(reason: String)
}

class MintingPolicyTest extends AnyFunSuite with ScalaCheckPropertyChecks with ScalusTest {
    import Expected.*

    private given env: CardanoInfo = CardanoInfo.mainnet

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
          mint = Map(AssetName(wrongTokenName) -> 1000L),
          requiredSigners = Set(account.paymentKeyHash)
        )

        val exception = intercept[Exception] {
            MintingPolicy.validate(config.toData)(ctx.toData)
        }
        assert(exception.getMessage == "Token name not found")

        assertEval(mintingScript.program $ ctx.toData, Failure("Error evaluated"))
    }

    test("should fail when extra tokens are minted/burned") {
        val ctx = makeScriptContext(
          mint = Map(AssetName(tokenName) -> 1000L, AssetName(utf8"Extra") -> 1000L),
          requiredSigners = Set(account.paymentKeyHash)
        )

        val exception = intercept[Exception] {
            MintingPolicy.validate(config.toData)(ctx.toData)
        }
        assert(exception.getMessage == "Multiple tokens found")

        assertEval(mintingScript.program $ ctx.toData, Failure("Error evaluated"))
    }

    test("should fail when admin signature is not provided") {
        val ctx = makeScriptContext(
          mint = Map(AssetName(tokenName) -> 1000L),
          requiredSigners = Set.empty
        )

        val exception = intercept[Exception] {
            MintingPolicy.validate(config.toData)(ctx.toData)
        }
        assert(exception.getMessage == "Not signed by admin")

        assertEval(mintingScript.program $ ctx.toData, Failure("Error evaluated"))
    }

    test("should fail when admin signature is not correct") {
        val ctx = makeScriptContext(
          mint = Map(AssetName(tokenName) -> 1000L),
          requiredSigners = Set(AddrKeyHash(platform.blake2b_224(ByteString.fromString("wrong"))))
        )

        val exception = intercept[Exception] {
            MintingPolicy.validate(config.toData)(ctx.toData)
        }
        assert(exception.getMessage == "Not signed by admin")

        assertEval(mintingScript.program $ ctx.toData, Failure("Error evaluated"))
    }

    test("should succeed when minted token name is correct and admin signature is correct") {
        val ctx = makeScriptContext(
          mint = Map(AssetName(tokenName) -> 1000L),
          requiredSigners = Set(account.paymentKeyHash)
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

    private def makeScriptContext(
        mint: Map[AssetName, Long],
        requiredSigners: Set[AddrKeyHash]
    ) = {
        val mintValue = mint.foldLeft(Value.zero) { case (acc, (assetName, qty)) =>
            acc + Value(mintingScript.script.scriptHash, assetName.bytes, BigInt(qty))
        }
        val tx = txBuilder
            .mint(
              script = mintingScript.script,
              assets = mint,
              redeemer = Data.unit,
              requiredSigners = requiredSigners
            )
            .payTo(account.baseAddress(env.network), mintValue.toLedgerValue)
            .draft
        
        tx.getScriptContextV3(Map.empty, RedeemerPurpose.ForMint(mintingScript.script.scriptHash))
    }

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
