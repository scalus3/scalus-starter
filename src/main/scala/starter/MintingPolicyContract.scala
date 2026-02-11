package starter

import scalus.cardano.blueprint.{Blueprint, HasTypeDescription, Preamble, Validator}
import scalus.cardano.onchain.plutus.v1.{PubKeyHash, TokenName}
import scalus.compiler.Options
import scalus.uplc.builtin.Data
import scalus.uplc.builtin.Data.toData
import scalus.uplc.{PlutusV3, Program}
import scalus.utils.Hex.toHex

/** Off-chain code for compiling, instantiating, and generating blueprints for the minting policy.
  */
object MintingPolicyContract {

    private given Options = Options.release

    /** Compiled Minting Policy */
    val compiled: PlutusV3[Data => Data => Unit] =
        PlutusV3.compile(MintingPolicy.validate)

    /** Optimized UPLC program with error traces for debugging */
    val program: Program = compiled.withErrorTraces.program

    /** CIP-57 blueprint describing the minting policy schema */
    lazy val blueprint: Blueprint = {
        val title = "Minting Policy"
        val description =
            "Admin-controlled minting policy for a single token name"
        val param = summon[HasTypeDescription[MintingConfig]].typeDescription
        Blueprint(
          preamble = Preamble(
            title,
            description,
            "1.0.0",
            plutusVersion = compiled.language,
            license = Some("Apache-2.0")
          ),
          validators = Seq(
            Validator(
              title = title,
              description = Some(description),
              redeemer = Some(summon[HasTypeDescription[Unit]].typeDescription),
              datum = None,
              parameters = Some(List(param)),
              compiledCode = Some(compiled.program.cborEncoded.toHex),
              hash = Some(compiled.script.scriptHash.toHex)
            )
          )
        )
    }

    /** Creates a parameterized minting policy script.
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
    ): PlutusV3[Data => Unit] = {
        val config = MintingConfig(adminPubKeyHash = adminPubKeyHash, tokenName = tokenName)
        compiled(config.toData)
    }
}
