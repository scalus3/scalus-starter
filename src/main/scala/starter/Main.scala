package starter

import com.monovore.decline.{Command, Opts}
import scalus.*
import scalus.cardano.address.Network
import scalus.crypto.ed25519.given

import scala.language.implicitConversions

enum Cmd:
    case Blueprint, Start

object Cli:
    private val command = {
        val blueprintCommand = Opts.subcommand("blueprint", "Prints the contract blueprint JSON") {
            Opts(Cmd.Blueprint)
        }

        val startCommand = Opts.subcommand("start", "Start the server") {
            Opts(Cmd.Start)
        }

        Command(name = "minter", header = "Scalus Starter Minting Example")(
          blueprintCommand orElse startCommand
        )
    }

    private def blueprint(): Unit = {
        println(MintingPolicyContract.blueprint.toJson())
    }

    /** Reads an environment variable, treating unset and blank/whitespace-only values as absent. */
    private def env(name: String): Option[String] =
        Option(System.getenv(name)).map(_.trim).filter(_.nonEmpty)

    @main
    def start(): Unit = {
        // Start the server
        val blockfrostApiKey =
            env("BLOCKFROST_API_KEY").getOrElse(sys.error("BLOCKFROST_API_KEY environment variable is not set"))
        val mnemonic =
            env("MNEMONIC").getOrElse(sys.error("MNEMONIC environment variable is not set"))
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

    @main
    def uzhDevNet(): Unit = {
        // Start the server against the UZH custom Cardano network (Yaci DevKit).
        // Optional env overrides:
        //   UZH_HOST - server host (default: 130.60.24.200)
        //   MNEMONIC - wallet seed phrase (default: the standard pre-funded test mnemonic)
        val host = env("UZH_HOST").getOrElse("130.60.24.200")
        val appCtx = env("MNEMONIC") match
            case None           => AppCtx.uzhDevNet("CO2 Tonne", host)
            case Some(mnemonic) => AppCtx.uzhDevNet("CO2 Tonne", host, mnemonic)
        println("Starting the server...")
        Server(appCtx).start()
    }

    @main def main(args: String*): Unit = {
        command.parse(args) match
            case Left(help) => println(help)
            case Right(cmd) =>
                cmd match
                    case Cmd.Blueprint => blueprint()
                    case Cmd.Start => start()
    }
