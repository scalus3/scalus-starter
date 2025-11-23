package starter

import com.bloxbean.cardano.client.api.model.ProtocolParams
import com.bloxbean.cardano.client.backend.api.DefaultUtxoSupplier
import com.bloxbean.cardano.client.function.helper.SignerProviders
import com.bloxbean.cardano.client.plutus.spec.PlutusData
import com.bloxbean.cardano.client.quicktx.{QuickTxBuilder, ScriptTx}
import com.bloxbean.cardano.client.transaction.spec.{Asset, Transaction}
import scalus.bloxbean.{EvaluatorMode, NoScriptSupplier, ScalusTransactionEvaluator}
import scalus.cardano.ledger.SlotConfig

import java.math.BigInteger

class TxBuilder(ctx: AppCtx) {
    private val backendService = ctx.backendService
    private val account = ctx.account
    private lazy val quickTxBuilder = QuickTxBuilder(backendService)

    lazy val protocolParams: ProtocolParams = {
        val result = backendService.getEpochService.getProtocolParameters
        if !result.isSuccessful then sys.error(result.getResponse)
        result.getValue
    }

    private lazy val utxoSupplier = new DefaultUtxoSupplier(backendService.getUtxoService)

    private lazy val evaluator = ScalusTransactionEvaluator(
      slotConfig = SlotConfig.Preprod,
      protocolParams = protocolParams,
      utxoSupplier = utxoSupplier,
      scriptSupplier = NoScriptSupplier(),
      mode = EvaluatorMode.EVALUATE_AND_COMPUTE_COST
    )

    private val address: String = account.getBaseAddress.getAddress

    def makeMintingTx(amount: Long): Either[String, Transaction] = {
        for
            utxo <- backendService.getUtxoService
                .getUtxos(address, 100, 1)
                .toEither

            scriptTx = new ScriptTx()
                .mintAsset(
                  ctx.mintingScript.plutusScript,
                  Asset.builder().name(ctx.tokenName).value(BigInteger.valueOf(amount)).build(),
                  PlutusData.unit(),
                  address
                )
                .collectFrom(utxo)
                .withChangeAddress(address)

            signedTx = quickTxBuilder
                .compose(scriptTx)
                // evaluate script cost using scalus
                .withTxEvaluator(evaluator)
                .withSigner(SignerProviders.signerFrom(account))
                .withRequiredSigners(account.getBaseAddress)
                .feePayer(account.baseAddress())
                .buildAndSign()
        yield signedTx
    }

    def makeBurningTx(amount: Long): Either[String, Transaction] = {
        for
            utxo <- backendService.getUtxoService
                .getUtxos(address, ctx.unitName, 100, 1)
                .toEither

            scriptTx = new ScriptTx()
                .mintAsset(
                  ctx.mintingScript.plutusScript,
                  Asset.builder().name(ctx.tokenName).value(BigInteger.valueOf(amount)).build(),
                  PlutusData.unit(),
                  address
                )
                .collectFrom(utxo)
                .withChangeAddress(address)

            signedTx = quickTxBuilder
                .compose(scriptTx)
                // evaluate script cost using scalus
                .withTxEvaluator(evaluator)
                .withSigner(SignerProviders.signerFrom(account))
                .withRequiredSigners(account.getBaseAddress)
                .feePayer(account.baseAddress())
                .buildAndSign()
        yield signedTx
    }

    def submitMintingTx(amount: Long): Either[String, String] = {
        for
            signedTx <- makeMintingTx(amount)
            result = backendService.getTransactionService.submitTransaction(signedTx.serialize())
            r <- Either.cond(result.isSuccessful, result.getValue, result.getResponse)
        yield r
    }
}
