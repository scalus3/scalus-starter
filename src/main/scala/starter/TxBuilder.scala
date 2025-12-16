package starter

import scalus.builtin.Data
import scalus.cardano.ledger.{AssetName, Transaction, Utxo, Value}
import scalus.cardano.txbuilder.TxBuilder as ScalusTxBuilder
import scalus.utils.await

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.*
import scala.util.Try

class TxBuilder(ctx: AppCtx) {

    def makeMintingTx(amount: Long): Either[String, Transaction] = {
        Try {
            // Fetch UTXOs for explicit handling
            val utxos = ctx.provider
                .findUtxos(ctx.address, None, None, None, None)
                .await(10.seconds)
                .getOrElse(throw new RuntimeException("Failed to fetch UTXOs"))

            val assetName = AssetName(ctx.tokenNameByteString)
            val assets = Map(assetName -> amount)
            val mintedValue = Value.asset(ctx.mintingScript.policyId, assetName, amount)

            // Convert first UTXO to Utxo type for collateral
            val (input, output) = utxos.head
            val firstUtxo = Utxo(input, output)

            ScalusTxBuilder(ctx.cardanoInfo)
                .spend(utxos) // Spend all UTXOs
                .collaterals(firstUtxo) // Use same UTXO as collateral
                .mintAndAttach(
                  redeemer = Data.unit,
                  assets = assets,
                  script = ctx.mintingScript.scalusScript,
                  requiredSigners = Set(ctx.addrKeyHash)
                )
                .payTo(ctx.address, mintedValue + Value.ada(2))
                .complete(ctx.provider, ctx.address)
                .await(30.seconds)
                .sign(ctx.signer)
                .transaction
        }.toEither.left.map(_.getMessage)
    }

    def makeBurningTx(amount: Long): Either[String, Transaction] = {
        Try {
            // Fetch UTXOs for explicit handling
            val utxos = ctx.provider
                .findUtxos(ctx.address, None, None, None, None)
                .await(10.seconds)
                .getOrElse(throw new RuntimeException("Failed to fetch UTXOs"))

            val assetName = AssetName(ctx.tokenNameByteString)
            // amount should be negative for burning
            val assets = Map(assetName -> amount)

            // Convert first UTXO to Utxo type for collateral
            val (input, output) = utxos.head
            val firstUtxo = Utxo(input, output)

            ScalusTxBuilder(ctx.cardanoInfo)
                .spend(utxos) // Spend all UTXOs
                .collaterals(firstUtxo) // Use same UTXO as collateral
                .mintAndAttach(
                  redeemer = Data.unit,
                  assets = assets,
                  script = ctx.mintingScript.scalusScript,
                  requiredSigners = Set(ctx.addrKeyHash)
                )
                .complete(ctx.provider, ctx.address)
                .await(30.seconds)
                .sign(ctx.signer)
                .transaction
        }.toEither.left.map(_.getMessage)
    }

    def submitMintingTx(amount: Long): Either[String, String] = {
        for
            tx <- makeMintingTx(amount)
            result <- ctx.provider.submit(tx).await(30.seconds).left.map(_.toString)
        yield result.toHex
    }
}
