package starter

import scalus.*
import scalus.compiler.compile
import scalus.uplc.builtin.{ByteString, Data, FromData, ToData}
import scalus.uplc.builtin.Data.toData
import scalus.cardano.ledger.{AssetName, Script, ScriptHash}
import scalus.compiler.sir.SIR
import scalus.cardano.onchain.plutus.v1.{PubKeyHash, TokenName, PolicyId}
import scalus.cardano.onchain.plutus.v3.{TxInfo, DataParameterizedValidator}
import scalus.cardano.onchain.plutus.prelude.{*, given}
import scalus.uplc.Program

import scala.language.implicitConversions

/** Configuration parameters for the minting policy.
  *
  * These values are "baked into" the script when it's deployed, making each deployment unique. The
  * script hash (policy ID) will change if these parameters change.
  */
case class MintingConfig(
    adminPubKeyHash: PubKeyHash, // Only this key can authorize minting/burning
    tokenName: TokenName // The only token name this policy allows
)

/** The @Compile annotation tells Scalus to generate serialization code for on-chain use.
  * FromData/ToData instances allow converting between Scala types and Plutus Data format.
  */
@Compile
object MintingConfig {
    given FromData[MintingConfig] = FromData.derived
    given ToData[MintingConfig] = ToData.derived
}

/** Minting Policy Validator
  *
  * This is the on-chain smart contract that controls token minting and burning. The @Compile
  * annotation generates Scalus Intermediate Representation (SIR) which is then compiled to Untyped
  * Plutus Core (UPLC) for execution on Cardano.
  *
  * Key concepts:
  *   - Minting policies validate whether tokens can be minted or burned
  *   - The policy ID (script hash) becomes the currency symbol for minted tokens
  *   - DataParameterizedValidator allows passing configuration as script parameters
  */
@Compile
object MintingPolicy extends DataParameterizedValidator {

    /** Core validation logic for minting/burning tokens.
      *
      * This function runs ON-CHAIN for every mint/burn transaction. It must succeed (return Unit)
      * for the transaction to be valid. Any failure (via `fail` or `require`) will reject the
      * transaction.
      *
      * Validation rules:
      *   1. Only the configured token name can be minted/burned 2. Only one token type per
      *      transaction (prevents accidental multi-minting) 3. Transaction must be signed by the
      *      admin key
      *
      * @param adminPubKeyHash
      *   the public key hash of the admin who can authorize minting/burning
      * @param tokenName
      *   the only token name this policy allows to mint or burn
      * @param ownPolicyId
      *   this script's own policy ID (currency symbol for minted tokens)
      * @param tx
      *   transaction information provided by the Cardano ledger
      */
    def mintingPolicy(
        adminPubKeyHash: PubKeyHash,
        tokenName: TokenName,
        ownPolicyId: PolicyId,
        tx: TxInfo
    ): Unit = {
        // tx.mint contains all tokens being minted/burned in this transaction
        // We look up our own policy ID to find tokens we're responsible for validating
        val mintedTokens = tx.mint.toSortedMap.getOrFail(ownPolicyId, "Tokens not found")

        // Pattern match to ensure exactly one token type with the correct name
        // Note: Scalus uses its own List type for on-chain code, not scala.List
        mintedTokens.toList match
            case List.Cons((tokName, _), tail) =>
                tail match
                    case List.Nil => require(tokName == tokenName, "Token name not found")
                    case _        => fail("Multiple tokens found")
            case _ => fail("Impossible: no tokens found")

        // Verify admin authorization - tx.signatories contains all keys that signed
        require(tx.signatories.contains(adminPubKeyHash), "Not signed by admin")
    }

    /** Entry point called by the Cardano ledger for minting policy validation.
      *
      * DataParameterizedValidator provides this structure where the `param` contains configuration
      * data embedded in the script at deployment time.
      *
      * @param param
      *   configuration data (MintingConfig) embedded in the script
      * @param redeemer
      *   data provided by the transaction (unused here, but could carry intent)
      * @param policyId
      *   this script's own policy ID (hash of the script)
      * @param tx
      *   full transaction information for validation
      */
    override inline def mint(
        param: Data,
        redeemer: Data,
        policyId: PolicyId,
        tx: TxInfo
    ): Unit = {
        val mintingConfig = param.to[MintingConfig]
        mintingPolicy(mintingConfig.adminPubKeyHash, mintingConfig.tokenName, policyId, tx)
    }

}

/** Off-chain code for compiling and instantiating the minting policy.
  *
  * This object handles the compilation pipeline:
  *   1. compile() - Generates Scalus Intermediate Representation (SIR) from Scala code 2.
  *      toUplcOptimized() - Converts SIR to optimized Untyped Plutus Core (UPLC) 3. plutusV3 -
  *      Wraps as a Plutus V3 program for Cardano
  *
  * The compiled program is a template that gets parameterized with specific configuration (admin
  * key, token name) for each deployment.
  */
object MintingPolicyGenerator {

    /** Compiled Scalus Intermediate Representation of the validator */
    val mintingPolicySIR: SIR = compile(MintingPolicy.validate)

    /** Optimized UPLC program with error traces for debugging */
    val program: Program = mintingPolicySIR.toUplcOptimized(generateErrorTraces = true).plutusV3

    /** Creates a parameterized minting policy script.
      *
      * The `$` operator applies the configuration data to the program, producing a fully
      * instantiated script ready for deployment.
      *
      * @param adminPubKeyHash
      *   public key hash of the admin
      * @param tokenName
      *   token name to allow
      * @return
      *   a configured minting policy script
      */
    def makeMintingPolicyScript(
        adminPubKeyHash: PubKeyHash,
        tokenName: TokenName
    ): MintingPolicyScript = {
        val config = MintingConfig(adminPubKeyHash = adminPubKeyHash, tokenName = tokenName)
        // Apply configuration to the program template using the $ operator
        MintingPolicyScript(program = program $ config.toData)
    }
}

/** A configured minting policy ready for use in transactions.
  *
  * @param program
  *   the UPLC program with configuration applied
  */
class MintingPolicyScript(val program: Program) {

    /** The script in Cardano ledger format (CBOR-encoded) */
    lazy val scalusScript: Script.PlutusV3 = Script.PlutusV3(program.cborByteString)

    /** The policy ID (script hash) - this becomes the currency symbol for minted tokens */
    lazy val policyId: ScriptHash = scalusScript.scriptHash

    /** Script hash as ByteString for use in transaction building */
    lazy val scriptHash: ByteString = policyId
}
