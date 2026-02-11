package starter

import com.monovore.decline.{Command, Opts}
import scalus.*
import scalus.cardano.address.Network
import scalus.crypto.ed25519.given

import scala.language.implicitConversions

enum Cmd:
    case Info, Start

object Cli:
    private val command = {
        val infoCommand = Opts.subcommand("info", "Prints the contract info") {
            Opts(Cmd.Info)
        }

        val startCommand = Opts.subcommand("start", "Start the server") {
            Opts(Cmd.Start)
        }

        Command(name = "minter", header = "Scalus Starter Minting Example")(
          infoCommand orElse startCommand
        )
    }

    private def info(): Unit = {
        // Pretty print the minting policy validator's SIR
        println(MintingPolicyGenerator.compiled.sir.showHighlighted)
    }

    @main
    def start(): Unit = {
        // Start the server
        val blockfrostApiKey = System.getenv("BLOCKFROST_API_KEY") match
            case null   => sys.error("BLOCKFROST_API_KEY environment variable is not set")
            case apiKey => apiKey
        val mnemonic = System.getenv("MNEMONIC") match
            case null     => sys.error("MNEMONIC environment variable is not set")
            case mnemonic => mnemonic
        val appCtx = AppCtx(Network.Testnet, mnemonic, blockfrostApiKey, "CO2 Tonne")
        println("Starting the server...")
        Server(appCtx).start()
    }

    @main
    def yaciDevKit(): Unit = {
        // Start the server
        val appCtx = AppCtx.yaciDevKit("CO2 Tonne")
        println("Starting the server...")
        Server(appCtx).start()
    }

    @main def main(args: String*): Unit = {
        command.parse(args) match
            case Left(help) => println(help)
            case Right(cmd) =>
                cmd match
                    case Cmd.Info  => info()
                    case Cmd.Start => start()
    }
