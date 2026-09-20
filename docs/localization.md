# Localization and the configuration theme selector

Version remains **1.0.0**: the owner has not published the first release yet. Save formats, registry IDs, theme IDs and enum names are unchanged.

## Languages and scope

Authored UTF-8 catalogs in `tools/lang/` cover English (`en_us`), German (`de_de`), French (`fr_fr`), Spanish (`es_es`), Brazilian Portuguese (`pt_br`), Russian (`ru_ru`), Simplified Chinese (`zh_cn`) and Japanese (`ja_jp`).

The translated interface includes items, blocks, tooltips, key bindings, the Backdoor biome/dimension names, configuration sections/options/help, preview presets, built-in theme names, theme dialogs, scan feedback and normal portal/edit notifications. User-authored theme names, resource IDs, filenames, commands and technical validation/exception diagnostics remain unchanged. These are authored translations, not a claim of native-speaker review for every locale.

Biome consumers can resolve the standard `biome.elsebase.backdoor` key. The real-client fixture checks that lookup in German and Chinese. No mapping-mod-specific API is added; JourneyMap itself is not part of the test installation.

Built-in display names are translated by stable theme ID, never by matching a user's chosen name. Server definitions and exported files retain their names. Localized searches include matching built-in IDs in the existing bounded search request; server filtering and pagination still apply.

## Configuration picker

Under **Mods → Elsebase → Config → Common → Room themes**, the default theme field opens a searchable, paginated list instead of requiring a typed ID. The selector includes the seven built-ins and readable theme files in the installation's `elsebase/templates/` directory. Library display names are shown; their IDs remain available as tooltips. The list is loaded off-thread and limited to 4,096 files. Symlinks and invalid files are skipped.

Selection goes through NeoForge's existing string editor callback: native undo, reset, change tracking and save behavior are retained. Cancel or Escape does not change the selection. Unknown configured IDs are preserved unless another theme is explicitly selected. This remains a local COMMON configuration; it does not edit a remote server's defaults. Reopen the world after changing the global default, or use the existing server template reload command. Personal defaults and explicit painted surfaces retain precedence.

`TemplateFiles.id` is shared by the server library loader and the selector so local files resolve to the same identities. `Screen.added()` runs before `Screen.init()` assigns its Minecraft field; asynchronous selector completion uses the Minecraft singleton and queues installation after initialization.

## Maintaining translations

1. Add or modify the key in **every** file in `tools/lang/`. Preserve format arguments such as `%s`; keep English logs and registry IDs out of the translation catalogs.
2. Use `Component.translatable` for server notifications and `UiText` for client-only interface strings. Never translate saved IDs or arbitrary player names. Template message packets carry known translation keys for ordinary notifications; technical diagnostic text retains its existing transport.
3. Run `node tools/generate-localizations.mjs` (also included by `node tools/generate-resources.mjs`). Do not edit generated language JSON directly.
4. Run `gradlew build`. `verifyLocalizations` rejects missing/extra keys, blank values, mismatched placeholders, known encoding corruption, stale generated catalogs, missing literal code keys and missing config labels/help. All eight languages must move together; no English-fill fallback is generated.
5. After UI changes, run `tools/check-config.ps1` with Java 21 in `JAVA_HOME`. It exercises the actual native config factory, German/Chinese resources, localized search, local-file identity, selection, cancel, undo, reset and TOML persistence. Its client runs silently with a free cursor in `build/config-client/` and exits automatically. It requires a fresh result report and leaves screenshots under that fixture.
6. Theme-menu changes also require `tools/check-templates.ps1`. Server/shared changes require `runGameTestServer`; the normal release package must still exclude development fixtures.

Key parity is a completeness check, not proof of translation quality. Review rendered text and adjust wording when players report unclear terms or truncation. Unexpected technical failure details may remain English so logs and error reports can be correlated.
