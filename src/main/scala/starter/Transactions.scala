package starter

import scalus.uplc.builtin.Data
import scalus.cardano.ledger.{AssetName, Transaction, Value}
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
  *   - Minting: Creating new tokens under a policy ID
  *   - Burning: Destroying tokens (negative mint amount)
  *
  * UTxO selection, collateral, and balancing are handled automatically by TxBuilder.complete().
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
            val assetName = AssetName(ctx.tokenNameByteString)
            val assets = Map(assetName -> amount)
            val mintedValue = Value.asset(ctx.mintingScript.script.scriptHash, assetName, amount)

            TxBuilder(ctx.cardanoInfo)
                .mint(
                  script = ctx.mintingScript.script,
                  assets = assets,
                  redeemer = Data.unit,
                  requiredSigners = Set(ctx.addrKeyHash)
                )
                .payTo(ctx.address, mintedValue)
                .complete(ctx.provider, ctx.address)
                .await(30.seconds)
                .sign(ctx.signer)
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
            val assetName = AssetName(ctx.tokenNameByteString)
            val assets = Map(assetName -> amount)

            TxBuilder(ctx.cardanoInfo)
                .mint(
                  script = ctx.mintingScript.script,
                  assets = assets,
                  redeemer = Data.unit,
                  requiredSigners = Set(ctx.addrKeyHash)
                )
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
