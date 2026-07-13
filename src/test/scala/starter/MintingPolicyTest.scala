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

import java.time.Instant
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

    private val mintDeadline: Instant = Instant.parse("2026-01-01T00:00:00Z")
    private val beforeDeadline: Instant = Instant.parse("2025-12-01T00:00:00Z")
    private val afterDeadline: Instant = Instant.parse("2026-06-01T00:00:00Z")

    private val config =
        MintingConfig(adminPubKeyHash, tokenName, BigInt(mintDeadline.toEpochMilli))

    private val mintingScript =
        MintingPolicyContract.makeMintingPolicyScript(
          adminPubKeyHash,
          tokenName,
          BigInt(mintDeadline.toEpochMilli)
        )

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
          requiredSigners = Set(account.paymentKeyHash),
          validTo = scala.Some(beforeDeadline)
        )
        // run the minting policy script as a Scala function
        // here you can use debugger to debug the minting policy script
        MintingPolicy.validate(config.toData)(ctx.toData)
        // run the minting policy script as a Plutus script
        assertEval(
          mintingScript.program $ ctx.toData,
          Success(ExUnits(memory = 62833, steps = 20046646))
        )
    }

    test("should fail when transaction validity interval is unbounded") {
        // No validTo: the node would accept this transaction at ANY point in time,
        // so the script cannot prove it runs before the deadline
        val ctx = makeScriptContext(
          mint = Map(AssetName(tokenName) -> 1000L),
          requiredSigners = Set(account.paymentKeyHash)
        )

        val exception = intercept[Exception] {
            MintingPolicy.validate(config.toData)(ctx.toData)
        }
        assert(exception.getMessage == "Minting deadline passed")

        assertEval(mintingScript.program $ ctx.toData, Failure("Error evaluated"))
    }

    test("should fail when minting after the deadline") {
        val ctx = makeScriptContext(
          mint = Map(AssetName(tokenName) -> 1000L),
          requiredSigners = Set(account.paymentKeyHash),
          validTo = scala.Some(afterDeadline)
        )

        val exception = intercept[Exception] {
            MintingPolicy.validate(config.toData)(ctx.toData)
        }
        assert(exception.getMessage == "Minting deadline passed")

        assertEval(mintingScript.program $ ctx.toData, Failure("Error evaluated"))
    }

    test("should succeed when burning after the deadline") {
        // Burning (negative amount) is exempt from the deadline - holders can
        // always destroy their tokens, even with an unbounded validity interval
        val ctx = makeScriptContext(
          mint = Map(AssetName(tokenName) -> -1000L),
          requiredSigners = Set(account.paymentKeyHash)
        )

        MintingPolicy.validate(config.toData)(ctx.toData)

        assertEval(
          mintingScript.program $ ctx.toData,
          Success(ExUnits(memory = 54283, steps = 16982058))
        )
    }

    test(s"validator size is ${mintingScript.program.cborEncoded.length} bytes") {
        val size = mintingScript.program.cborEncoded.length
        assert(size == 759)
    }

    private def makeScriptContext(
        mint: Map[AssetName, Long],
        requiredSigners: Set[AddrKeyHash],
        validTo: scala.Option[Instant] = scala.None
    ) = {
        // Only positive amounts become outputs: burned tokens leave no trace in outputs
        val minted = mint.filter { case (_, qty) => qty > 0 }
        val mintValue = minted.foldLeft(Value.zero) { case (acc, (assetName, qty)) =>
            acc + Value(mintingScript.script.scriptHash, assetName.bytes, BigInt(qty))
        }
        val base = txBuilder
            .mint(
              script = mintingScript.script,
              assets = mint,
              redeemer = Data.unit
            )
            .requireSignatures(requiredSigners)
        val withOutputs =
            if minted.nonEmpty then
                base.payTo(account.baseAddress(env.network), mintValue.toLedgerValue)
            else base
        val tx = (validTo match
            case scala.Some(to) => withOutputs.validTo(to)
            case scala.None     => withOutputs
        ).draft

//        import scalus.utils.*
//        println(("MintingConfig", true).toData.showHighlighted)
//        println(tx.showHighlighted)

        tx.getScriptContextV3(Map.empty, RedeemerPurpose.ForMint(mintingScript.script.scriptHash))
    }

    private def assertEval(p: Program, expected: Expected): Unit = {
        val result = p.evaluateDebug
        (result, expected) match
            case (result: Result.Success, Expected.Success(expected)) =>
                assert(result.budget == expected)
            case (result: Result.Failure, Expected.Failure(expected)) =>
                assert(
                  result.exception.getMessage.startsWith(expected),
                  s"Expected message starting with '$expected', got '${result.exception.getMessage}'"
                )
            case _ => fail(s"Unexpected result: $result, expected: $expected")
    }
}
