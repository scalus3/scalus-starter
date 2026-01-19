# Scalus Starter

A complete starter kit for building Cardano DApps with [Scalus](https://scalus.org).

This project demonstrates a **full-stack DApp**: a minting policy smart contract, transaction building, a REST API, and comprehensive testing — everything you need to start building on Cardano with Scala.

## What You'll Build

A token minting service with:
- **Plutus V3 Minting Policy** — A smart contract that controls who can mint/burn tokens
- **Transaction Builder** — Constructs and submits transactions to the blockchain
- **REST API** — Exposes minting functionality via HTTP endpoints
- **Test Suite** — Unit tests for the contract + integration tests on a local devnet

## Quick Start

### Prerequisites

- Java JDK 11+ (21+ recommended)
- [sbt](https://www.scala-sbt.org/)
- Docker (for integration tests)

> **Nix users**: Run `nix develop` to get a complete environment.

### Run the Integration Tests

The fastest way to see everything working:

```bash
git clone https://github.com/scalus3/scalus-starter.git
cd scalus-starter
sbt integration/test
```

This will:
1. Spin up a local Cardano node (Yaci DevKit) via Docker
2. Deploy the minting policy
3. Mint tokens
4. Wait for block confirmation
5. Burn tokens
6. Verify the final state

You should see tests passing — congratulations, you just ran a complete DApp locally!

### Run the Unit Tests

```bash
sbt test
```

Unit tests verify the minting policy logic without needing a blockchain.

## Project Structure

```
scalus-starter/
├── src/main/scala/starter/
│   ├── MintingPolicy.scala    # The Plutus V3 smart contract
│   ├── Transactions.scala     # Transaction building logic
│   ├── Server.scala           # REST API + application context
│   └── Main.scala             # CLI entry point
│
├── src/test/scala/starter/
│   └── MintingPolicyTest.scala  # Unit tests for the contract
│
└── integration/src/test/scala/
    └── MintingIT.scala          # End-to-end integration tests
```

### Key Files Explained

| File | Purpose |
|------|---------|
| `MintingPolicy.scala` | Defines the on-chain validator logic using Scalus `@Compile` annotation |
| `Transactions.scala` | Builds mint/burn transactions, handles UTxO selection and signing |
| `Server.scala` | Tapir-based REST API with Swagger UI, plus `AppCtx` for configuration |
| `MintingPolicyTest.scala` | Tests contract logic: invalid inputs, missing signatures, script size |
| `MintingIT.scala` | Full cycle test: mint → confirm → burn → verify |

## Running the Server

### Local Development (Yaci DevKit)

Start a local Cardano node first:

```bash
# Install Yaci DevKit: https://devkit.yaci.xyz
devkit
> create-node
> start
```

Then run the server:

```bash
sbt "run yaciDevKit"
```

The API will be available at `http://localhost:8088` with Swagger UI at `http://localhost:8088/docs`.

### Testnet / Mainnet

Set environment variables:

```bash
export BLOCKFROST_API_KEY="your-api-key"
export MNEMONIC="your 24-word mnemonic phrase"
```

Run:

```bash
sbt "run start"  # Uses preprod testnet by default
```

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| PUT | `/mint?amount=100` | Mint tokens |

## Understanding the Code

### The Minting Policy

The contract (`MintingPolicy.scala`) validates that:
1. The transaction is signed by the admin
2. Only the configured token name is minted/burned
3. Only one token type per transaction

```scala
@Compile
object MintingPolicy extends Validator:
  def validator(config: MintingConfig, redeemer: Unit, ctx: ScriptContext): Unit =
    // Validation logic compiled to Plutus Core
```

### Transaction Building

`Transactions.scala` shows how to:
- Fetch UTxOs from the blockchain
- Construct minting transactions with proper inputs/outputs
- Handle collateral for script execution
- Sign and submit transactions

### Testing Strategy

- **Unit tests**: Evaluate the script with mock data, verify it accepts/rejects correctly
- **Integration tests**: Full blockchain interaction using Yaci DevKit in Docker (via Testcontainers)

## Next Steps

1. **Read the code** — Start with `MintingPolicy.scala`, it's well-commented
2. **Modify the validator** — Try adding a maximum mint amount check
3. **Add a burn endpoint** — Extend `Server.scala` with a `/burn` endpoint
4. **Deploy to testnet** — Get a Blockfrost API key and test on preprod

## Building Your Own DApp

Use this starter as a template:

1. Replace `MintingPolicy` with your own validator logic
2. Adapt `Transactions` for your transaction types
3. Extend `Server` with your API endpoints
4. Write tests following the existing patterns

## Resources

- [Scalus Documentation](https://scalus.org/docs/get-started)
- [Scalus GitHub](https://github.com/nau/scalus)
- [Yaci DevKit](https://devkit.yaci.xyz)
- [Blockfrost API](https://blockfrost.io)

## Community

- Discord: [Scalus Discord](https://discord.gg/ygwtuBybsy)
- Twitter: [@Scalus3](https://twitter.com/Scalus3)
- Twitter: [@lantr_io](https://x.com/lantr_io)

## License

Apache 2.0
