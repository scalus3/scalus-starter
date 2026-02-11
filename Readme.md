# Scalus Starter

**From Scala to Cardano in minutes.** Write smart contracts in Scala 3, debug them in IntelliJ, test without a blockchain, deploy with confidence.

A complete starter kit for building Cardano DApps with [Scalus](https://scalus.org).

## Why Scalus?

- **Write contracts in Scala 3** — Use a language you already know, with full IDE support
- **Debug with breakpoints** — Step through your on-chain code in IntelliJ like any Scala function
- **Test without a blockchain** — Run your contract logic in milliseconds with the built-in emulator
- **Property-based testing** — Generate thousands of test cases with ScalaCheck
- **CIP-57 Blueprints** — Auto-generate standardized contract metadata for interoperability
- **One codebase** — On-chain validators and off-chain transaction building in the same project

## What You'll Build

A token minting service with:
- **Plutus V3 Minting Policy** — A smart contract that controls who can mint/burn tokens
- **Transaction Builder** — Constructs and submits transactions using the TxBuilder API
- **CIP-57 Blueprint** — Standardized contract schema for tooling interoperability
- **REST API** — Exposes minting functionality via HTTP with Swagger UI
- **Test Suite** — Unit tests with emulator + integration tests on a local devnet

## Quick Start

### Prerequisites

- Java JDK 11+ (21+ recommended)
- [sbt](https://www.scala-sbt.org/)
- Docker (for integration tests only)

> **Nix users**: Run `nix develop` to get a complete environment.

### Run the Unit Tests

No blockchain needed — tests run in milliseconds:

```bash
git clone https://github.com/scalus3/scalus-starter.git
cd scalus-starter
sbt test
```

This validates the minting policy logic using the built-in emulator: wrong token names are rejected, missing signatures fail, correct inputs succeed.

### Run the Integration Tests

See the full DApp lifecycle on a real (local) blockchain:

```bash
sbt integration/test  # Requires Docker
```

This will:
1. Spin up a local Cardano node (Yaci DevKit) via Docker
2. Deploy the minting policy
3. Mint tokens and wait for block confirmation
4. Burn tokens and verify the final state

### Generate the Blueprint

Export the CIP-57 blueprint JSON for your contract:

```bash
sbt "run blueprint"
```

## Project Structure

```
scalus-starter/
├── src/main/scala/starter/
│   ├── MintingPolicy.scala          # On-chain Plutus V3 smart contract
│   ├── MintingPolicyContract.scala  # Off-chain: compilation, blueprint, parameterization
│   ├── Transactions.scala           # Transaction building logic
│   ├── Server.scala                 # REST API + application context
│   └── Main.scala                   # CLI entry point
│
├── src/test/scala/starter/
│   └── MintingPolicyTest.scala      # Unit tests for the contract
│
└── integration/src/test/scala/
    └── MintingIT.scala              # End-to-end integration tests
```

### Key Files Explained

| File | Purpose |
|------|---------|
| `MintingPolicy.scala` | On-chain validator: defines minting rules using `@Compile` and `DataParameterizedValidator` |
| `MintingPolicyContract.scala` | Off-chain: compiles the validator, generates CIP-57 blueprint, creates parameterized scripts |
| `Transactions.scala` | Builds mint/burn transactions with TxBuilder — UTxO selection and balancing are automatic |
| `Server.scala` | Tapir-based REST API with Swagger UI, plus `AppCtx` configuration |
| `MintingPolicyTest.scala` | Tests contract logic: invalid inputs, missing signatures, correct minting, script size |
| `MintingIT.scala` | Full cycle test: mint, confirm, burn, verify on a local devnet |

## Understanding the Code

### The Minting Policy

The contract (`MintingPolicy.scala`) is a Plutus V3 minting policy that validates:
1. Only the configured token name can be minted/burned
2. Only one token type per transaction
3. The transaction must be signed by the admin

```scala
@Compile
object MintingPolicy extends DataParameterizedValidator {
    override inline def mint(param: Data, redeemer: Data, policyId: PolicyId, tx: TxInfo): Unit = {
        val config = param.to[MintingConfig]
        // Validation logic — compiled to Plutus Core, runs on-chain
    }
}
```

The `MintingConfig` (admin key + token name) is baked into the script at deployment, making each deployment unique with its own policy ID.

### Transaction Building

`Transactions.scala` shows how to build Cardano transactions with the TxBuilder API:

```scala
TxBuilder(cardanoInfo)
    .mint(script = mintingScript.script, assets = assets, redeemer = Data.unit,
          requiredSigners = Set(addrKeyHash))
    .payTo(address, mintedValue)
    .complete(provider, changeAddress)  // Automatic UTxO selection, collateral, balancing
```

### Testing Strategy

- **Unit tests** — Run the contract as a Scala function *and* as a Plutus script, both in milliseconds using the emulator. Set breakpoints and debug on-chain logic like regular code.
- **Integration tests** — Full blockchain interaction using Yaci DevKit in Docker via Testcontainers. Tests the complete mint/burn lifecycle with real block confirmations.

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

```bash
export BLOCKFROST_API_KEY="your-api-key"
export MNEMONIC="your 24-word mnemonic phrase"
sbt "run start"  # Uses preprod testnet by default
```

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| PUT | `/mint?amount=100` | Mint tokens |

## Next Steps

1. **Read the code** — Start with `MintingPolicy.scala`, it's well-commented
2. **Modify the validator** — Try adding a maximum mint amount check
3. **Add a burn endpoint** — Extend `Server.scala` with a `/burn` endpoint
4. **Generate a blueprint** — Run `sbt "run blueprint"` and inspect the CIP-57 JSON
5. **Deploy to testnet** — Get a Blockfrost API key and test on preprod

## Building Your Own DApp

Use this starter as a template:

1. Replace `MintingPolicy` with your own validator logic
2. Update `MintingPolicyContract` to compile your validator and generate its blueprint
3. Adapt `Transactions` for your transaction types
4. Extend `Server` with your API endpoints
5. Write tests following the existing patterns

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
