# TpaPlugin

Player teleport request plugin for Paper, with configurable delay, movement cancel, and multilingual messages.

## Layout

```
TpaPlugin/
├── 1.16/    (Paper 1.16.5, Java 11)
├── 1.17/    (Paper 1.17.1, Java 17)
├── 1.18/    (Paper 1.18.2, Java 17)
├── 1.19/    (Paper 1.19.4, Java 17)
├── 1.20/    (Paper 1.20.1, Java 17)
├── 1.21/    (Paper 1.21.x,  Java 21)
└── 26.1/    (Paper 26.1.x,  Java 25)
```

## Build

```sh
./gradlew buildAll
./gradlew :TpaPlugin-1.21:build
```

## Commands

| Command | Description |
|---------|-------------|
| `/tpa <player>` | Send a teleport request (you teleport to them on accept). |
| `/tpahere <player>` | Ask a player to teleport to you (they come to you on accept). |
| `/tpaccept` | Accept the latest incoming request. |
| `/tpdeny` | Deny the latest incoming request. |
| `/tpacancel` | Cancel your outgoing request. |
| `/language tpa <english\|hebrew\|french\|spanish>` | Change plugin language. |

## Config (`config.yml`)

| Option | Default | Description |
|--------|---------|-------------|
| `teleport-delay-seconds` | `5` | Wait after accept before teleport. |
| `cancel-teleport-on-move` | `true` | Cancel pending teleport if the player moves. |
| `request-timeout-seconds` | `60` | Request expires after this many seconds (`0` = no timeout). |
| `tpa-cooldown-seconds` | `0` | Cooldown between sending `/tpa` requests. |
