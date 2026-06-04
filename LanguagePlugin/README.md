# LanguagePlugin

Shared `/language` command and player language file for all your Paper plugins.

## Remote updates (no jar rebuild)

1. Host `language-pack.yml` on GitHub (raw URL) or any web server.
2. Set `remote.url` in `plugins/LanguagePlugin/config.yml`.
3. Edit the remote file when you add a **new plugin** or new translations.
4. Servers download it on start and every N minutes.

### Version lock for new plugins

When you add support for a **new plugin**, set in `language-pack.yml`:

```yaml
pluginSupport:
  MyNewPlugin:
    minLanguagePluginVersion: "1.1.0"

pluginMessages:
  MyNewPlugin:
    minLanguagePluginVersion: "1.1.0"
    messages:
      en:
        welcome: "&aWelcome"
      he:
        welcome: "&aברוך הבא"
```

Players with **LanguagePlugin 1.0.0** will **not** load `MyNewPlugin` remote messages.  
They must **update the LanguagePlugin jar** to 1.1.0+.

Your new plugin should check on enable:

```java
if (!PluginLanguages.isSupported(this, "MyNewPlugin")) {
    getLogger().warning("Update LanguagePlugin to 1.1.0+ for translations!");
}
```

## Commands

```
/language english
/language hebrew
/lang french
```

## Data

`plugins/SharedLanguage/player-languages.yml`

## For other plugins

```java
String lang = PluginLanguages.get(this, player.getUniqueId());
String text = PluginLanguages.message(this, uuid, "MyNewPlugin", "welcome");
```

## Remote language pack

Publish `language-pack.yml` to the URL in `config.yml` (GitHub raw). Regenerate from embedded defaults:

```sh
python3 ../scripts/generate-language-pack.py
```

HomePlugin and TpaPlugin read `pluginMessages` from the remote pack when LanguagePlugin is installed.

## Build

```sh
./gradlew :LanguagePlugin-1.21:build
```
