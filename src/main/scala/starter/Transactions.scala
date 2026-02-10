package starter

import scalus.uplc.builtin.Data
import scalus.cardano.ledger.{AssetName, Transaction, Utxo, Value}
import scalus.cardano.txbuilder.TxBuilder
import scalus.utils.await

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.*
import scala.util.Try

/** Transaction building logic for minting and burning tokens.
  *
  * This class demonstrates how to construct Cardano transactions that interact with Plutus scripts
  * using the Scalus TxBuilder API.
  *
  * Key concepts:
  *   - UTxO selection: Cardano uses unspent transaction outputs as inputs
  *   - Collateral: Required for script execution to cover potential failures
  *   - Minting: Creating new tokens under a policy ID
  *   - Burning: Destroying tokens (negative mint amount)
  *
  * @param ctx
  *   application context containing provider, signer, and script configuration
  */
class Transactions(ctx: AppCtx) {

    /** Builds a transaction that mints new tokens.
      *
      * @param amount
      *   number of tokens to mint (positive)
      * @return
      *   either an error message or the signed transaction
      */
    def makeMintingTx(amount: Long): Either[String, Transaction] = {
        Try {
            // Fetch all UTxOs at our address - these are our spendable funds
            val utxos = ctx.provider
                .findUtxos(ctx.address)
                .await(10.seconds)
                .getOrElse(throw new RuntimeException("Failed to fetch UTXOs"))

            val assetName = AssetName(ctx.tokenNameByteString)
            val assets = Map(assetName -> amount)
            // Value representing the newly minted tokens to send to ourselves
            val mintedValue = Value.asset(ctx.mintingScript.policyId, assetName, amount)

            // Collateral is seized if script execution fails - use first UTxO
            val (input, output) = utxos.head
            val firstUtxo = Utxo(input, output)

            TxBuilder(ctx.cardanoInfo)
                .spend(utxos) // Include all UTxOs as inputs (for ADA and consolidation)
                .collaterals(firstUtxo) // Collateral for script execution guarantee
                .mint(
                  script = ctx.mintingScript.scalusScript, // The minting policy script
                  assets = assets, // Token name -> amount to mint
                  redeemer = Data.unit, // Redeemer data (unused by our policy)
                  requiredSigners = Set(ctx.addrKeyHash) // Admin key must sign
                )
                .payTo(ctx.address, mintedValue) // Send minted tokens to ourselves
                .complete(ctx.provider, ctx.address) // Balance tx, calculate fees
                .await(30.seconds)
                .sign(ctx.signer) // Sign with our key
                .transaction
        }.toEither.left.map(_.getMessage)
    }

    /** Builds a transaction that burns existing tokens.
      *
      * Burning uses a negative mint amount. The tokens to burn must exist in the UTxOs being spent.
      *
      * @param amount
      *   number of tokens to burn (should be negative)
      * @return
      *   either an error message or the signed transaction
      */
    def makeBurningTx(amount: Long): Either[String, Transaction] = {
        Try {
            val utxos = ctx.provider
                .findUtxos(ctx.address)
                .await(10.seconds)
                .getOrElse(throw new RuntimeException("Failed to fetch UTXOs"))

            val assetName = AssetName(ctx.tokenNameByteString)
            // Negative amount signals burning to the ledger
            val assets = Map(assetName -> amount)

            val (input, output) = utxos.head
            val firstUtxo = Utxo(input, output)

            TxBuilder(ctx.cardanoInfo)
                .spend(utxos)
                .collaterals(firstUtxo)
                .mint(
                  script = ctx.mintingScript.scalusScript,
                  assets = assets,
                  redeemer = Data.unit,
                  requiredSigners = Set(ctx.addrKeyHash)
                )
                // No payTo needed - burned tokens simply disappear
                .complete(ctx.provider, ctx.address)
                .await(30.seconds)
                .sign(ctx.signer)
                .transaction
        }.toEither.left.map(_.getMessage)
    }

    /** Builds, signs, and submits a minting transaction to the network.
      *
      * @param amount
      *   number of tokens to mint
      * @return
      *   either an error message or the transaction hash (hex)
      */
    def submitMintingTx(amount: Long): Either[String, String] = {
        for
            tx <- makeMintingTx(amount)
            result <- ctx.provider.submit(tx).await(30.seconds).left.map(_.toString)
        yield result.toHex
    }
}
