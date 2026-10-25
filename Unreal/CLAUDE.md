# Unreal Engine client (this directory)

The root CLAUDE.md describes the libGDX/Java game. Inside `Unreal/` the target is **Unreal
Engine 5.4+ C++**; ignore the libGDX skills and libGDX-specific rules here. The design intent
from the root still applies: data-driven content, server-authoritative state, systems testable
without rendering, and explicit galaxy-vs-local coordinate boundaries.

## Conventions

- Follow Epic's coding standard: `F`/`U`/`A`/`E`/`I` prefixes, PascalCase, `b` for bools,
  `TArray`/`TMap`/`FString` (no std containers), `check()` not `assert`.
- Prefix new types with `GO` (`FGOHubLayout`, `AGOPlanetActor`). Seeds are `uint64` internally and
  `int64` in reflected/Blueprint API (`GOSeed::ToBlueprint/FromBlueprint`).
- Generation code (`Core/`, `Universe/`, `Planet/`, `Hub/`) must stay engine-independent: no
  UObjects, no world, no rendering, so it runs on worker threads and in the headless build. Use only
  the Core API that `Tools/HeadlessTests/Shim/CoreMinimal.h` provides, or extend the shim
  faithfully (match real UE semantics) when you need more.
- Every random decision derives from a `GOSeed` domain; never use `FMath::Rand`, `FRandomStream`
  or wall-clock time in generators. Draw random values before filtering on them so filters don't
  shift the stream.
- Content (rooms, assets, planet/star/biome tables, names) goes in
  `Plugins/GalacticProcGen/Data/*.json` (DataTable import layout). Keys must be exact UPROPERTY
  names; the headless loader rejects unknown keys.
- Generators place assets by kit id into slots (box + facing); never reference meshes from
  generator code. Runtime resolves ids through `FGOKitAssets`.
- Unity builds: `.cpp`-private helpers go in a uniquely named namespace (`GO<File>Private`), not an
  anonymous one, and never `using namespace` at file scope.
- Coordinates: galaxy in light years, systems in AU, planets in km (double), the world in cm.
  Planet chunks are placed at their own origin with chunk-relative vertices (LWC-friendly).

## Verify changes

- `Tools/HeadlessTests/run_tests.sh` — builds with `-Wall -Wextra -Wshadow -Werror` and runs all
  generator test cases (same cases as the editor's `GalacticOdyssey.ProcGen` automation tests).
- `Tools/HeadlessTests/build/GOHeadlessTests --preview <seed> <dir>` — eyeball the academy plan,
  planet map and galaxy for layout or climate changes; `--report 40` for distribution checks.
- Code under `Runtime/`, the game module and the Python setup script need a real editor build.
