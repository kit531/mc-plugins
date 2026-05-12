# HomePlugin

Configurable home plugin for Paper, available for several Minecraft versions.

## Layout

```
HomePlugin/
├── 1.16/    (Paper 1.16.5, Java 11)
├── 1.17/    (Paper 1.17.1, Java 17)
├── 1.18/    (Paper 1.18.2, Java 17)
├── 1.19/    (Paper 1.19.4, Java 17)
├── 1.20/    (Paper 1.20.1, Java 17)
├── 1.21/    (Paper 1.21.x,  Java 21)
└── 26.1/    (Paper 26.1.x,  Java 25)
```

Each subdirectory is an independent Gradle subproject that produces its own JAR.

## Build

Build all variants at once:

```sh
./gradlew buildAll
```

Build a single variant (output goes to `<version>/build/libs/`):

```sh
./gradlew :HomePlugin-1.21:build
./gradlew :HomePlugin-26.1:build
```

Clean everything:

```sh
./gradlew cleanAll
```

## Commands

| Command           | Description                                |
|-------------------|--------------------------------------------|
| `/sethome [name]` | Save current location as a home.           |
| `/home [name]`    | Teleport to a saved home (with delay).     |
| `/listhome`       | List saved homes.                          |
| `/delhome <name>` | Delete a saved home.                       |
| `/language home <english\|hebrew\|french\|spanish>` | Change plugin language for yourself. |
