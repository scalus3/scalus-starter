# Exercise: Time-Locked Minting

**Goal:** extend the minting policy so minting is forbidden after a deadline — and discover how
"time" works on Cardano along the way.

**The punchline students should leave with:** a Plutus script cannot read a clock. The only
notion of "now" it has is the transaction's **validity interval**, which the node checks against
the current slot *before* running any scripts. So if the whole interval lies before the
deadline, the script has a proof that the transaction executes before the deadline. Determinism
is preserved: the script still only looks at the transaction.

## The rule

> Minting is allowed only before `mintDeadline`. Burning is always allowed — holders must be
> able to destroy their tokens forever.

## Live walkthrough (~25 min)

This branch (`exercise/time-locked-minting`) contains the finished solution — one commit for the
code, one for this document. Rescue commands if live coding goes sideways:

```bash
git diff exercise/time-locked-minting -- src/main/scala/starter/MintingPolicy.scala  # peek
git checkout exercise/time-locked-minting -- src/                                    # fast-forward
```

### Step 1 — the failing test (~5 min)

Start in `MintingPolicyTest.scala`. Add a deadline and a test that expects an unbounded
transaction to be rejected:

```scala
private val mintDeadline: Instant = Instant.parse("2026-01-01T00:00:00Z")

test("should fail when transaction validity interval is unbounded") {
    val ctx = makeScriptContext(
      mint = Map(AssetName(tokenName) -> 1000L),
      requiredSigners = Set(account.paymentKeyHash)
    )
    val exception = intercept[Exception] {
        MintingPolicy.validate(config.toData)(ctx.toData)
    }
    assert(exception.getMessage == "Minting deadline passed")
}
```

Run it — red: the config doesn't even have a deadline yet.

**Ask the students:** "How would you check a deadline inside the contract? Where does the
current time come from?" Let them suggest `System.currentTimeMillis()` — then explain why that
cannot exist on-chain (every node would get a different answer; determinism dies).

### Step 2 — the on-chain rule (~8 min)

In `MintingPolicy.scala`:

1. Add the field: `MintingConfig(adminPubKeyHash, tokenName, mintDeadline: PosixTime)`.
2. Keep the minted `amount` from the pattern match instead of discarding it.
3. Add the rule (last, after the existing checks):

```scala
require(
  amount < BigInt(0) || tx.validRange.isEntirelyBefore(mintDeadline),
  "Minting deadline passed"
)
```

Points to narrate:

- `tx.validRange` is the validity interval from the transaction body — the node already
  guaranteed the current slot is inside it, *before* the script ran.
- `isEntirelyBefore` returns `false` for an unbounded interval: no upper bound, no proof.
- `amount < 0` exempts burning.

Thread `mintDeadline` through the `mint(param, ...)` dispatcher and
`MintingPolicyContract.makeMintingPolicyScript`. The unit test goes green. Remind them: **the
policy ID just changed** — a different deadline is a different token (parameterization!).

### Step 3 — the off-chain side (~7 min)

Run `TransactionsEmulatorTest` — it fails now: the transaction the backend builds has an
unbounded validity interval. The fix in `Transactions.makeMintingTx`:

```scala
.validTo(earliest(nearHorizon, ctx.mintDeadline.minusSeconds(1)))
```

where `nearHorizon` is the current chain tip + 300 slots. Two real-world constraints hide here —
both were discovered by actually hitting them (tell the story):

1. **You cannot set `validTo` far in the future.** The node translates the interval's slots to
   POSIX time for the script, and it can only do that within its *foreseeable hard-fork
   horizon* (roughly tip + safe zone; ~15 minutes on a devnet, ~36 hours on mainnet). A
   `validTo` of 2027 fails at submission with `TimeTranslationPastHorizon`.
2. **Wall-clock time and chain time can diverge.** A laptop devnet paused during sleep keeps
   its chain hours behind the wall clock. Deriving the bound from the chain tip
   (`provider.currentSlot`) instead of `Instant.now()` is the honest approach. If a reused
   local devnet lags anyway, recreate it: `docker rm -f scalus-yaci-devkit`.

`AppCtx` gets `mintDeadline` with a **fixed constant** default (2027-01-01), not "now + X":
a deterministic config keeps the policy ID stable across restarts — same config, same script
hash, same token.

### Step 4 — proof on a real network (~5 min)

```bash
sbt test                                        # all green, milliseconds
sbt "runMain starter.uzhDevNet"                 # backend against the UZH devnet
curl -X PUT "http://localhost:8088/mint?amount=100"   # works: before the deadline
```

If time allows, change the default deadline to a timestamp in the past, restart, and mint
again — the transaction is now rejected. Note *where* it fails: the local script evaluation in
`complete()` catches it before a single lovelace is spent — determinism means you never pay for
a failing transaction.

## Test matrix (all in `MintingPolicyTest`)

| Case | Interval | Expected |
|------|----------|----------|
| Mint before deadline | `validTo` &lt; deadline | ✅ succeeds |
| Mint, unbounded interval | none | ❌ "Minting deadline passed" |
| Mint after deadline | `validTo` &gt; deadline | ❌ "Minting deadline passed" |
| Burn after deadline | none (unbounded) | ✅ succeeds |

## Follow-up questions for discussion

1. Why does the node check the validity interval *before* running scripts? (Hint: who pays if a
   script fails — and what would a malicious unbounded transaction cost the network?)
2. The deadline check uses `isEntirelyBefore`. What would be wrong with checking only
   `validRange.from`?
3. Could you implement "minting opens at T1 and closes at T2"? Which interval bounds do you
   need? (`isEntirelyBetween` exists.)
4. Harder: could you enforce a *total supply cap* with this technique? Why not? (No global
   state — that needs a different pattern: state carried in UTxOs.)
