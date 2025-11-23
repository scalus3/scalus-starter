package starter

import com.bloxbean.cardano.client.plutus.spec.{PlutusScript, PlutusV1Script}
import scalus.*
import scalus.Compiler.compile
import scalus.builtin.Data
import scalus.builtin.Data.toData
import scalus.ledger.api.v1.*
import scalus.prelude.{*, given}
import scalus.compiler.sir.SIR
import scalus.uplc.Program

import scala.language.implicitConversions

/* This annotation is used to generate the Scalus Intermediate Representation (SIR)
   for the code in the annotated object.
 */
@Compile
/** Minting policy script
  */
object MintingPolicyV1 {

    def validate(config: Data)(redeemer: Data, contextData: Data): Unit = {
        val sc = contextData.to[ScriptContext]
        sc.purpose match
            case ScriptPurpose.Minting(currencySymbol) =>
                val mintingConfig = config.to[MintingConfig]
                mintingPolicy(
                  mintingConfig.adminPubKeyHash,
                  mintingConfig.tokenName,
                  currencySymbol,
                  sc.txInfo
                )
            case _ =>
                fail("Only for minting")
    }

    /** Minting policy script
      *
      * @param adminPubKeyHash
      *   admin public key hash
      * @param tokenName
      *   token name to mint or burn
      * @param policyId
      *   own currency symbol (minting policy id)
      * @param tx
      *   transaction information
      */
    private def mintingPolicy(
        adminPubKeyHash: PubKeyHash, // admin pub key hash
        tokenName: TokenName, // token name
        policyId: PolicyId,
        tx: TxInfo
    ): Unit = {
        // find the tokens minted by this policy id
        val mintedTokens = tx.mint.toSortedMap.get(policyId).getOrFail("Tokens not found")
        mintedTokens.toList match
            // there should be only one token with the given name
            case List.Cons((tokName, _), tail) =>
                tail match
                    case List.Nil => require(tokName == tokenName, "Token name not found")
                    case _        => fail("Multiple tokens found")
            case _ => fail("Impossible: no tokens found")

        // only admin can mint or burn tokens
        require(tx.signatories.contains(adminPubKeyHash), "Not signed by admin")
    }
}

object MintingPolicyV1Generator {
    val mintingPolicySIR: SIR = compile(MintingPolicyV1.validate)
    private val script = mintingPolicySIR.toUplc(generateErrorTraces = true).plutusV1
//    println(script.showHighlighted)

    def makeMintingPolicyScript(
        adminPubKeyHash: PubKeyHash,
        tokenName: TokenName
    ): MintingPolicyV1Script = {
        val config = MintingConfig(adminPubKeyHash = adminPubKeyHash, tokenName = tokenName)
        MintingPolicyV1Script(script = script $ config.toData)
    }
}

class MintingPolicyV1Script(val script: Program) extends MintingScript {
    lazy val plutusScript: PlutusScript = PlutusV1Script
        .builder()
        .`type`("PlutusScriptV1")
        .cborHex(script.doubleCborHex)
        .build()
}
