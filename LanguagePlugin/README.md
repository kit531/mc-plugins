# LanguagePlugin

Shared player language for all server plugins. Other plugins read the same file — no need to add `/language` to each one.

## Usage

```
/language english
/language hebrew
/language french
/language spanish
```

Data file: `plugins/SharedLanguage/player-languages.yml`

## For other plugins (read language)

```java
String lang = SharedLanguages.get(this, player.getUniqueId());
```

Or use the Bukkit service `me.languageplugin.api.LanguageAPI`.

## Build

```sh
./gradlew :LanguagePlugin-1.21:build
```
