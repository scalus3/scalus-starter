package starter

import com.bloxbean.cardano.client.account.Account
import com.bloxbean.cardano.client.common.model.{Network, Networks}
import scalus.builtin.ByteString
import scalus.cardano.address.Address
import scalus.cardano.ledger.{AddrKeyHash, CardanoInfo, SlotConfig}
import scalus.cardano.address.Network as ScalusNetwork
import scalus.utils.await
import scala.concurrent.duration.*
import scalus.cardano.node.{BlockfrostProvider, Provider}
import scalus.cardano.txbuilder.TransactionSigner
import scalus.cardano.wallet.BloxbeanAccount
import scalus.ledger.api.v3.PubKeyHash
import sttp.client4.DefaultFutureBackend
import sttp.tapir.*
import sttp.tapir.server.netty.sync.NettySyncServer
import sttp.tapir.swagger.bundle.SwaggerInterpreter

import scala.concurrent.ExecutionContext.Implicits.global

// Provide sttp backend for BlockfrostProvider
given sttp.client4.Backend[scala.concurrent.Future] = DefaultFutureBackend()

case class AppCtx(
    cardanoInfo: CardanoInfo,
    provider: Provider,
    account: Account,
    signer: TransactionSigner,
    tokenName: String
) {
    lazy val pubKeyHash: PubKeyHash = PubKeyHash(
      ByteString.fromArray(account.hdKeyPair().getPublicKey.getKeyHash)
    )
    lazy val addrKeyHash: AddrKeyHash = AddrKeyHash(
      ByteString.fromArray(account.hdKeyPair().getPublicKey.getKeyHash)
    )
    lazy val tokenNameByteString: ByteString = ByteString.fromString(tokenName)
    lazy val address: Address = Address.fromBech32(account.baseAddress())
    // combined minting script hash and token name
    lazy val unitName: String = (mintingScript.scriptHash ++ tokenNameByteString).toHex
    lazy val mintingScript: MintingPolicyScript =
        MintingPolicyGenerator.makeMintingPolicyScript(pubKeyHash, tokenNameByteString)
}

object AppCtx {
    // Standard derivation path for Cardano payment keys
    private val PaymentDerivationPath = "m/1852'/1815'/0'/0/0"

    def apply(
        network: Network,
        mnemonic: String,
        blockfrostApiKey: String,
        tokenName: String
    ): AppCtx = {
        val (cardanoInfo, provider, scalusNetwork) =
            if network == Networks.mainnet() then
                (
                  CardanoInfo.mainnet,
                  BlockfrostProvider.mainnet(blockfrostApiKey),
                  ScalusNetwork.Mainnet
                )
            else if network == Networks.preview() then
                (
                  CardanoInfo.preview,
                  BlockfrostProvider.preview(blockfrostApiKey),
                  ScalusNetwork.Testnet
                )
            else if network == Networks.preprod() then
                (
                  CardanoInfo.preprod,
                  BlockfrostProvider.preprod(blockfrostApiKey),
                  ScalusNetwork.Testnet
                )
            else sys.error(s"Unsupported network: $network")

        val account = Account.createFromMnemonic(network, mnemonic)
        val bloxbeanAccount = BloxbeanAccount(scalusNetwork, mnemonic, PaymentDerivationPath)
        val signer = new TransactionSigner(Set(bloxbeanAccount.paymentKeyPair))

        new AppCtx(
          cardanoInfo,
          provider,
          account,
          signer,
          tokenName
        )
    }

    def yaciDevKit(tokenName: String): AppCtx = {
        val network = new Network(0, 42)
        val mnemonic =
            "test test test test test test test test test test test test test test test test test test test test test test test sauce"
        val account = Account.createFromMnemonic(network, mnemonic)
        val provider = BlockfrostProvider.localYaci

        // Fetch protocol parameters from Yaci DevKit
        val protocolParams = provider.fetchLatestParams().await(10.seconds)

        // Yaci DevKit uses slot length of 1 second and start time of 0
        val yaciSlotConfig = SlotConfig(
          zeroTime = 0L,
          zeroSlot = 0L,
          slotLength = 1000
        )

        val cardanoInfo = CardanoInfo(protocolParams, ScalusNetwork.Testnet, yaciSlotConfig)

        // Use BloxbeanAccount for proper HD key signing
        val bloxbeanAccount =
            BloxbeanAccount(ScalusNetwork.Testnet, mnemonic, PaymentDerivationPath)
        val signer = new TransactionSigner(Set(bloxbeanAccount.paymentKeyPair))

        new AppCtx(
          cardanoInfo,
          provider,
          account,
          signer,
          tokenName
        )
    }

}

class Server(ctx: AppCtx):
    private val mint = endpoint.put
        .in("mint")
        .in(query[Long]("amount"))
        .out(stringBody)
        .errorOut(stringBody)
        .handle(mintTokens)

    private val txBuilder = Transactions(ctx)

    private val apiEndpoints = List(mint)
    private val swaggerEndpoints = SwaggerInterpreter()
        .fromEndpoints[[X] =>> X](apiEndpoints.map(_.endpoint), "Token Minter", "0.1")

    private def mintTokens(amount: Long): Either[String, String] =
        val result = txBuilder.submitMintingTx(amount)
        result match
            case Left(value) =>
                println(s"Error minting tokens: $value")
            case Right(value) =>
                println(s"Tokens minted successfully: $value")
        result

    def start(): Unit =
        NettySyncServer()
            .port(8088)
            .addEndpoints(apiEndpoints ++ swaggerEndpoints)
            .startAndWait()
