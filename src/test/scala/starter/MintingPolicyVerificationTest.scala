package starter

import org.scalatest.funsuite.AnyFunSuite
import scalus.cardano.onchain.plutus.prelude.List
import scalus.cardano.onchain.plutus.v1.{PolicyId, PubKeyHash, Value}
import scalus.cardano.onchain.plutus.v3.{Interval, TxId, TxInfo}
import scalus.compiler.Compile
import scalus.uplc.builtin.ByteString.hex
import scalus.verify.*
import scalus.verify.Props.*
import scalus.verify.uplcblaster.LeanProofsSuite

/** Proofs about compiled UPLC for one configured token and one admin signer. Amount and the finite
  * upper validity bound range over all integers.
  */
class MintingPolicyVerificationTest extends AnyFunSuite with LeanProofsSuite {
    import proofs.*

    private val policy = FunctionDef(MintingPolicyVerificationInputs.check)

    test("positive minting after the deadline is rejected") {
        proven(
          forAll[BigInt, BigInt]((amount, validTo) =>
              (amount > BigInt(0) && validTo > MintingPolicy.mintDeadline) ==>
                  fails(policy, (amount, validTo))
          ),
          2000,
          policy
        )
    }

    test("admin-authorized burning succeeds for every finite upper validity bound") {
        proven(
          forAll[BigInt, BigInt]((amount, validTo) =>
              (amount < BigInt(0)) ==> succeeds(policy, (amount, validTo))
          ),
          2000,
          policy
        )
    }
}

@Compile
object MintingPolicyVerificationInputs {

    /** Exercise the actual policy with a single token and the admin's signature. */
    def check(amount: BigInt, validTo: BigInt): Unit = {
        val admin = PubKeyHash(hex"11111111111111111111111111111111111111111111111111111111")
        val policyId: PolicyId = hex"22222222222222222222222222222222222222222222222222222222"
        val tokenName = hex"434f3220546f6e6e65" // CO2 Tonne
        val tx = TxInfo(
          inputs = List.Nil,
          mint = Value(policyId, tokenName, amount),
          validRange = Interval.entirelyBefore(validTo),
          signatories = List(admin),
          id = TxId(hex"3333333333333333333333333333333333333333333333333333333333333333")
        )
        MintingPolicy.mintingPolicy(admin, tokenName, policyId, tx)
    }
}
