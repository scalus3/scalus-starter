# Mint Deadline Design

**Date:** 2026-07-14
**Status:** Approved

## Goal

Disallow minting of tokens after 2026-07-14 12:30:00 CEST (POSIX time `1784025000000` ms).
Burning remains allowed at any time.

## Approach

Hardcode the deadline as a constant in the validator (chosen over a `MintingConfig`
parameter for a smaller diff; accepted trade-offs: the policy is single-use and the
emulator end-to-end test becomes coupled to real wall-clock time).

Since Plutus scripts cannot read a clock, the validator enforces the deadline via the
transaction validity interval: the node only accepts a transaction within its validity
window, so a finite upper bound `<=` deadline proves the script executes before the
deadline.

This changes the script, therefore the policy ID changes.

## Changes

### On-chain — `src/main/scala/starter/MintingPolicy.scala`

- Add to the `MintingPolicy` object:
  `val mintDeadline: PosixTime = BigInt(1784025000000L)` — 2026-07-14 12:30:00 CEST.
- In `mintingPolicy`, the single-token match case already binds the minted amount.
  When the amount is positive (a mint, not a burn), additionally require:
  - `tx.validRange.to.boundType` is `Finite(to)` with `to <= mintDeadline`,
    failing with `"Minting deadline passed"`;
  - a non-finite upper bound fails with `"Must have finite upper bound"`.
- Inclusive vs exclusive upper bound is deliberately ignored: requiring
  `to <= mintDeadline` is safe in both cases.
- Burns (negative amounts) and the existing token-name and admin-signature checks
  are unchanged.

### Off-chain — `src/main/scala/starter/Transactions.scala`

- `makeMintingTx` adds `.validTo(Instant.ofEpochMilli(1784025000000L))` so built
  transactions carry the finite upper bound the validator demands.
- `makeBurningTx` stays as is.

## Error handling

All failures are `require`/`fail` messages in the validator, consistent with the
existing style.

## Testing

Unit tests in `MintingPolicyTest` exercise the validator with constructed `TxInfo`
values (wall-clock independent):

1. Mint with a finite upper bound `<=` deadline passes.
2. Mint with an upper bound past the deadline fails with `"Minting deadline passed"`.
3. Mint with an infinite (`PosInf`) upper bound fails with `"Must have finite upper bound"`.
4. Burn with any validity range passes.

The emulator end-to-end test keeps working while the emulator clock is before the
deadline; after 2026-07-14 12:30 CEST it will legitimately start failing, which is
inherent to hardcoding and accepted.
