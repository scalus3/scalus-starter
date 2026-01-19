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

// STTP backend required for BlockfrostProvider's HTTP calls
given sttp.client4.Backend[scala.concurrent.Future] = DefaultFutureBackend()

/** Application context holding all configuration and dependencies.
  *
  * This is the central configuration object that wires together:
  *   - Network connection (via Provider)
  *   - Wallet/signing capabilities
  *   - Minting script configuration
  *
  * @param cardanoInfo
  *   protocol parameters and network configuration
  * @param provider
  *   blockchain data provider (Blockfrost or local node)
  * @param account
  *   HD wallet account for address derivation
  * @param signer
  *   transaction signer with private keys
  * @param tokenName
  *   name of the token this instance will mint/burn
  */
case class AppCtx(
    cardanoInfo: CardanoInfo,
    provider: Provider,
    account: Account,
    signer: TransactionSigner,
    tokenName: String
) {
    /** Public key hash for use in Plutus scripts (on-chain format) */
    lazy val pubKeyHash: PubKeyHash = PubKeyHash(
      ByteString.fromArray(account.hdKeyPair().getPublicKey.getKeyHash)
    )

    /** Address key hash for transaction building (off-chain format) */
    lazy val addrKeyHash: AddrKeyHash = AddrKeyHash(
      ByteString.fromArray(account.hdKeyPair().getPublicKey.getKeyHash)
    )

    lazy val tokenNameByteString: ByteString = ByteString.fromString(tokenName)
    lazy val address: Address = Address.fromBech32(account.baseAddress())

    /** Full asset identifier: policy ID + token name (used for lookups) */
    lazy val unitName: String = (mintingScript.scriptHash ++ tokenNameByteString).toHex

    /** The configured minting policy script, parameterized with our admin key and token name */
    lazy val mintingScript: MintingPolicyScript =
        MintingPolicyGenerator.makeMintingPolicyScript(pubKeyHash, tokenNameByteString)
}

/** Factory methods for creating AppCtx for different environments. */
object AppCtx {
    /** BIP44 derivation path for Cardano payment keys (CIP-1852) */
    private val PaymentDerivationPath = "m/1852'/1815'/0'/0/0"

    /** Creates an AppCtx for mainnet or public testnets using Blockfrost.
      *
      * @param network
      *   the Cardano network (mainnet, preprod, preview)
      * @param mnemonic
      *   24-word seed phrase for wallet derivation
      * @param blockfrostApiKey
      *   API key from blockfrost.io
      * @param tokenName
      *   name for the token to mint
      */
    def apply(
        network: Network,
        mnemonic: String,
        blockfrostApiKey: String,
        tokenName: String
    ): AppCtx = {
        // Configure provider and network settings based on target network
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

        // Create account from mnemonic using standard derivation path
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

    /** Creates an AppCtx for local development with Yaci DevKit.
      *
      * This uses a hardcoded test mnemonic and connects to a local Yaci DevKit node.
      * No API keys required - perfect for development and testing.
      *
      * Prerequisites: Yaci DevKit running locally (see https://devkit.yaci.xyz)
      *
      * @param tokenName
      *   name for the token to mint
      */
    def yaciDevKit(tokenName: String): AppCtx = {
        // Custom network ID for local devkit
        val network = new Network(0, 42)

        // Standard test mnemonic - DO NOT use in production!
        val mnemonic =
            "test test test test test test test test test test test test test test test test test test test test test test test sauce"
        val account = Account.createFromMnemonic(network, mnemonic)

        // Connect to local Yaci DevKit Blockfrost-compatible API
        val provider = BlockfrostProvider.localYaci

        // Fetch current protocol parameters from the local node
        val protocolParams = provider.fetchLatestParams.await(10.seconds)

        // Yaci DevKit slot configuration: 1 second slots, starting at epoch 0
        val yaciSlotConfig = SlotConfig(
          zeroTime = 0L,
          zeroSlot = 0L,
          slotLength = 1000 // milliseconds
        )

        val cardanoInfo = CardanoInfo(protocolParams, ScalusNetwork.Testnet, yaciSlotConfig)

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

/** REST API server for the token minting service.
  *
  * Built with Tapir for type-safe endpoint definitions and automatic OpenAPI generation.
  * Swagger UI is available at /docs for interactive API exploration.
  *
  * @param ctx
  *   application context with blockchain connection and signing capabilities
  */
class Server(ctx: AppCtx):
    // Define the mint endpoint using Tapir's type-safe DSL
    // PUT /mint?amount=100 -> returns transaction hash or error
    private val mint = endpoint.put
        .in("mint")
        .in(query[Long]("amount"))
        .out(stringBody)
        .errorOut(stringBody)
        .handle(mintTokens)

    private val txBuilder = Transactions(ctx)

    private val apiEndpoints = List(mint)

    // Auto-generate Swagger/OpenAPI documentation from endpoint definitions
    private val swaggerEndpoints = SwaggerInterpreter()
        .fromEndpoints[[X] =>> X](apiEndpoints.map(_.endpoint), "Token Minter", "0.1")

    /** Handles mint requests by building and submitting a transaction. */
    private def mintTokens(amount: Long): Either[String, String] =
        val result = txBuilder.submitMintingTx(amount)
        result match
            case Left(value) =>
                println(s"Error minting tokens: $value")
            case Right(value) =>
                println(s"Tokens minted successfully: $value")
        result

    /** Starts the HTTP server on port 8088.
      *
      * Endpoints:
      *   - PUT /mint?amount=N - Mint N tokens
      *   - GET /docs - Swagger UI
      */
    def start(): Unit =
        NettySyncServer()
            .port(8088)
            .addEndpoints(apiEndpoints ++ swaggerEndpoints)
            .startAndWait()
