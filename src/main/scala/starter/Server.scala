package starter

import scalus.uplc.builtin.ByteString
import scalus.cardano.address.{Address, Network as ScalusNetwork}
import scalus.cardano.ledger.{AddrKeyHash, CardanoInfo}
import scalus.utils.await
import scala.concurrent.duration.*
import scalus.cardano.node.{BlockfrostProvider, BlockchainProvider}
import scalus.cardano.txbuilder.TransactionSigner
import scalus.cardano.wallet.hd.HdAccount
import scalus.cardano.onchain.plutus.v1.PubKeyHash
import scalus.crypto.ed25519.Ed25519Signer
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
    provider: BlockchainProvider,
    account: HdAccount,
    signer: TransactionSigner,
    tokenName: String
) {

    /** Public key hash for use in Plutus scripts (on-chain format) */
    lazy val pubKeyHash: PubKeyHash = PubKeyHash(account.paymentKeyHash)

    /** Address key hash for transaction building (off-chain format) */
    lazy val addrKeyHash: AddrKeyHash = account.paymentKeyHash

    lazy val tokenNameByteString: ByteString = ByteString.fromString(tokenName)
    lazy val address: Address = account.baseAddress(cardanoInfo.network)

    /** Full asset identifier: policy ID + token name (used for lookups) */
    lazy val unitName: String = (mintingScript.scriptHash ++ tokenNameByteString).toHex

    /** The configured minting policy script, parameterized with our admin key and token name */
    lazy val mintingScript: MintingPolicyScript =
        MintingPolicyGenerator.makeMintingPolicyScript(pubKeyHash, tokenNameByteString)
}

/** Factory methods for creating AppCtx for different environments. */
object AppCtx {

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
        network: ScalusNetwork,
        mnemonic: String,
        blockfrostApiKey: String,
        tokenName: String
    )(using Ed25519Signer): AppCtx = {
        // Configure provider and network settings based on target network
        val provider =
            if network == ScalusNetwork.Mainnet then
                BlockfrostProvider.mainnet(blockfrostApiKey).await(30.seconds)
            else if network == ScalusNetwork.Testnet then
                BlockfrostProvider.preview(blockfrostApiKey).await(30.seconds)
            else sys.error(s"Unsupported network: $network")

        // Create account from mnemonic using CIP-1852 HD derivation
        val account = HdAccount.fromMnemonic(mnemonic)

        new AppCtx(
          provider.cardanoInfo,
          provider,
          account,
          account.signerForUtxos,
          tokenName
        )
    }

    /** Creates an AppCtx for local development with Yaci DevKit.
      *
      * This uses a hardcoded test mnemonic and connects to a local Yaci DevKit node. No API keys
      * required - perfect for development and testing.
      *
      * Prerequisites: Yaci DevKit running locally (see https://devkit.yaci.xyz)
      *
      * @param tokenName
      *   name for the token to mint
      */
    def yaciDevKit(tokenName: String)(using Ed25519Signer): AppCtx = {
        // Standard test mnemonic - DO NOT use in production!
        val mnemonic =
            "test test test test test test test test test test test test test test test test test test test test test test test sauce"

        // Connect to local Yaci DevKit Blockfrost-compatible API
        val provider = BlockfrostProvider.localYaci().await(30.seconds)

        // Create account from mnemonic using CIP-1852 HD derivation
        val account = HdAccount.fromMnemonic(mnemonic)

        new AppCtx(
          provider.cardanoInfo,
          provider,
          account,
          account.signerForUtxos,
          tokenName
        )
    }

}

/** REST API server for the token minting service.
  *
  * Built with Tapir for type-safe endpoint definitions and automatic OpenAPI generation. Swagger UI
  * is available at /docs for interactive API exploration.
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
