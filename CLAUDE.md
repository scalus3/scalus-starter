# Claude Code Guidelines for scalus-starter

## Project Overview

A Cardano DApp starter project using Scalus. Demonstrates smart contract development,
minting policies, transaction building, and testing with both an emulator and Yaci DevKit.
Built with Scala 3.3.7 and Scalus 0.15.1.

## Commands

Use `sbtn` for all build commands.

| Command                 | Purpose                                 |
|-------------------------|-----------------------------------------|
| `sbtn test`             | Run unit tests (emulator-based)         |
| `sbtn integration/test` | Run integration tests (requires Docker) |
| `sbtn run`              | Run the main application / HTTP server  |
| `sbtn compile`          | Compile the core module                 |
| `sbtn Test/compile`     | Compile core module with tests          |

**Note:** Integration tests use Yaci DevKit via Testcontainers and require Docker to be running.

## Architecture

### Module Structure

| Module        | sbt Project   | Purpose                                    |
|---------------|---------------|--------------------------------------------|
| `.` (root)    | `core`        | Smart contracts, transactions, HTTP server |
| `integration` | `integration` | Integration tests with Yaci DevKit         |

### Key Source Locations

- Smart contracts: `src/main/scala/starter/MintingPolicy.scala`
- Transaction building: `src/main/scala/starter/Transactions.scala`
- HTTP server: `src/main/scala/starter/Server.scala`
- CLI entry point: `src/main/scala/starter/Main.scala`
- Unit tests: `src/test/scala/starter/`
- Integration tests: `integration/src/test/scala/starter/`

## Scala 3 Code Style

Use `{}` for top-level definitions and multi-line function bodies.
Use indentation-based syntax for `if`/`match`/`try`/`for`, unless it's too big (more than 20 lines).
Use braces otherwise.
Use `then` in `if` expressions and `do` in `while` loops.

```scala
object Example {
  def exampleFunction(x: Int): Int = {
    if x > 0 then x * 2
    else
      val y = -x
      y * 2
  }

  def describe(x: Any): String = x match
    case 1 => "one"
    case _ => "other"
}
```

## Commit Guidelines

- Use conventional commit style: `feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `chore:`
- Keep messages short: 1-2 paragraphs
- Mention key changes
- Never add "Co-authored by Claude Code" or similar

## Scalus Sources Reference

The Scalus library sources are at `/Users/nau/projects/lantr/scalus`. Consult them for API details,
implementation patterns, and usage examples.

| Area                | Path                                                                    |
|---------------------|-------------------------------------------------------------------------|
| Core API            | `scalus-core/shared/src/main/scala/scalus/`                             |
| Standard library    | `scalus-core/shared/src/main/scala/scalus/prelude/`                     |
| SIR compiler        | `scalus-core/shared/src/main/scala/scalus/compiler/sir/`                |
| UPLC                | `scalus-core/shared/src/main/scala/scalus/uplc/`                        |
| Transaction builder | `scalus-cardano-ledger/shared/src/main/scala/scalus/cardano/txbuilder/` |
| Ledger types        | `scalus-cardano-ledger/shared/src/main/scala/scalus/cardano/`           |
| Testkit             | `scalus-testkit/`                                                       |
| Examples            | `scalus-examples/shared/src/main/scala/scalus/examples/`                |
| Compiler plugin     | `scalus-plugin/src/main/scala/scalus/plugin/`                           |

## Important Files

- `build.sbt` - Build configuration, dependencies, Scalus plugin setup
- `.scalafmt.conf` - Code formatting (4-space indent, 100 col max)
- `src/main/scala/starter/MintingPolicy.scala` - Example Plutus V3 minting policy
- `src/main/scala/starter/Transactions.scala` - Transaction building with TxBuilder
- `src/test/scala/starter/TransactionsTestBase.scala` - Shared test base for emulator and devkit
