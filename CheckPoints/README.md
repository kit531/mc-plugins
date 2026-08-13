# CheckPoints

Pressure-plate/button checkpoints for Paper: respawn points, death traps,
teleporters, command triggers, potion-effect pads, jump pads, and full
parkour courses with live leaderboards. Floating animated names, particles,
and a door/armour-stand lock system are bundled in too.

## Layout

Unlike the other plugins in this repo, CheckPoints is **one Gradle project,
not one per Minecraft version**. It only uses stable Paper/Bukkit API (no
NMS), and that surface is verified identical across the whole range below -
so a single build covers every listed version:

```
CheckPoints/
├── build.gradle       (Paper API 1.21.11, Java 21)
├── compat.json         declared min/max Minecraft version for releases
└── src/
```

Verified compatible: **1.20.5 through 26.2** (see `compat.json`). Below
1.20.5 the plugin refuses to load - it needs the `block_interaction_range`
attribute, added that version. 1.21.2 was superseded by 1.21.3 the same day
Mojang shipped it, so Paper never built a version for it.

## Build

```
./gradlew build
```

Produces `build/libs/CheckPoints.jar` - the filename is fixed on purpose (no
version suffix), so the auto-updater can always stage a new build at
`plugins/update/CheckPoints.jar` and Paper's own startup sequence swaps it
in over the running one.

## Releases and auto-update

Pushing a version bump (`version = '...'` in `build.gradle`) to `main`
triggers `.github/workflows/checkpoints-release.yml`, which builds the jar
and publishes a GitHub Release tagged `checkpoints-vX.Y.Z` with two assets:
`CheckPoints.jar` and `compat.json`. Pushing without a version bump just
verifies the build - no release is created, so ordinary commits don't spam
the releases list.

Every server running the plugin checks GitHub once per boot (see
`AutoUpdater.java`): if the newest `checkpoints-v*` release is newer than
what's running *and* its `compat.json` range covers `Bukkit.getMinecraftVersion()`
for that server, the jar is staged in `plugins/update/` and installs
automatically the next time that server restarts. A release that doesn't
cover a given server's version is simply skipped for it - nothing installs,
nothing breaks. `/checkpoint update` (or console) checks and reports on
demand instead of waiting for the next boot.
