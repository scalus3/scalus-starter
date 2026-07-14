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
                  compiled = ctx.mintingScript,
                  assets = assets,
                  redeemer = Data.unit
                )
                .requireSignature(ctx.addrKeyHash)
                .payTo(ctx.address, mintedValue)
                .complete(ctx.provider, sponsor = ctx.address)
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
                  compiled = ctx.mintingScript,
                  assets = assets,
                  redeemer = Data.unit
                )
                .requireSignature(ctx.addrKeyHash)
                .complete(ctx.provider, sponsor = ctx.address)
                .await(30.seconds)
                .sign(ctx.signer)
                .transaction
        }.toEither.left.map(_.getMessage)
    }

    /** Builds, signs, and submits a minting or burning transaction to the network.
      *
      * A positive amount mints tokens; a negative amount burns them. The two need different
      * transaction shapes: minting pays the created tokens to an output, while burning must not add
      * them to any output - a negative quantity in an output is not even encodable in the
      * transaction wire format.
      *
      * @param amount
      *   number of tokens to mint (positive) or burn (negative)
      * @return
      *   either an error message or the transaction hash (hex)
      */
    def submitMintingTx(amount: Long): Either[String, String] = {
        for
            tx <-
                if amount > 0 then makeMintingTx(amount)
                else if amount < 0 then makeBurningTx(amount)
                else Left("amount must be non-zero")
            result <- ctx.provider.submit(tx).await(30.seconds).left.map(_.toString)
        yield result.toHex
    }
}
