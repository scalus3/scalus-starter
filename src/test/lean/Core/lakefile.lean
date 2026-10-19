import Lake
open Lake DSL

-- The Lean workspace of Core. Scalus runs the checks of its statements here, and proofs
-- written by hand belong here. Scalus created it, and it is yours from here on.
package «Core» where
  -- As in the workspace of Scalus's Lean library.
  moreGlobalServerArgs := #["--threads=4"]
  -- Blaster and PlutusCore are those of Scalus's Lean library: cloned and built beside it
  -- once, for every workspace of the project.
  packagesDir := "../../../../.scalus/ScalusProofs/.lake/packages"

-- Scalus's Lean library, as the version of Scalus in the build has it. Scalus writes it
-- where this path says, as long as that is in a `.scalus`.
require ScalusProofs from "../../../../.scalus/ScalusProofs"

@[default_target]
lean_lib «Core» where
  -- Every module under `Core/` is built, so a proof written by hand is checked without
  -- being imported from `Core.lean`.
  globs := #[.andSubmodules `Core]
