package starter

import scalus.*
import scalus.compiler.*
import scalus.cardano.onchain.plutus.prelude.List.{Cons, Nil}
import scalus.cardano.onchain.plutus.prelude.{*, given}
import scalus.cardano.onchain.plutus.v1.{IntervalBoundType, PolicyId, PosixTime, PubKeyHash, TokenName}
import scalus.cardano.onchain.plutus.v3.{DataParameterizedValidator, TxInfo}
import scalus.uplc.builtin.{Data, FromData, ToData}

import scala.language.implicitConversions

/** Configuration parameters for the minting policy.
  *
  * These values are "baked into" the script when it's deployed, making each deployment unique. The
  * script hash (policy ID) will change if these parameters change.
  */
case class MintingConfig(
    adminPubKeyHash: PubKeyHash, // Only this key can authorize minting/burning
    tokenName: TokenName // The only token name this policy allows
) derives FromData,
      ToData

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

    /** No tokens can be minted after this moment: 2026-07-14 12:30:00 CEST. Burning stays allowed.
      *
      * Plutus scripts cannot read a clock, so the check relies on the transaction validity
      * interval: the ledger only accepts a transaction within that interval, so a finite upper
      * bound at or before the deadline proves the script runs before the deadline.
      */
    val mintDeadline: PosixTime = BigInt("1784025000000")

    /** Core validation logic for minting/burning tokens.
      *
      * This function runs ON-CHAIN for every mint/burn transaction. It must succeed (return Unit)
      * for the transaction to be valid. Any failure (via `fail` or `require`) will reject the
      * transaction.
      *
      * Validation rules:
      *   1. Only the configured token name can be minted/burned 2. Only one token type per
      *      transaction (prevents accidental multi-minting) 3. Minting (not burning) is only
      *      allowed before [[mintDeadline]], proven via the transaction validity interval 4.
      *      Transaction must be signed by the admin key
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
            case Cons((tokName, amount), Nil) =>
                require(tokName == tokenName, "Token name not found")
                // Minting (positive amount) is only allowed before the deadline.
                // Burning (negative amount) is allowed at any time.
                if amount > BigInt(0) then
                    tx.validRange.to.boundType match
                        case IntervalBoundType.Finite(to) =>
                            require(to <= mintDeadline, "Minting deadline passed")
                        case _ =>
                            fail("Must have finite upper bound")
            case Cons(_, _) =>
                fail("Multiple tokens found")
            case _ =>
                // Cardano ledger rules guarantee that minting always has tokens
                impossible()

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
