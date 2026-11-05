# Offline spell checking

`MyCustomTextArea` and `MyCustomTextField` (package
`com.github.avec112.spellcheck`) are the standard Vaadin `TextArea` and
`TextField` with opt-in spell checking for Norwegian Bokmål (default), American
English and British English. Checking runs entirely in the browser against
bundled Hunspell dictionaries. At runtime nothing is fetched from anywhere but
the application itself.

```java
MyCustomTextArea description = new MyCustomTextArea("Description");
description.setSpellCheckEnabled(true);                         // nb-NO by default
description.setSpellCheckLanguage(SpellCheckLanguage.EN_GB);    // change any time
description.addSpellCheckWord("Fjordlyx");                      // this field only
```

Demo: `/spellcheck-demo` (also the root route).

## How it works

```text
MyCustomTextArea / MyCustomTextField         (Java, implement HasSpellCheck)
  └─ SpellCheckSupport                       (Java, shared state + DOM wiring)
       └─ <spell-check-indicator>            (spell-check-indicator.ts, in the field's suffix slot)
            ├─ CheckScheduler                (check-scheduler.ts: 600 ms debounce, stale-result guard)
            ├─ <vaadin-popover> + <spell-check-panel>   (suggestions UI)
            └─ spell-check-client.ts ──postMessage──▶ spell-check.worker.ts (one Web Worker per page)
                                                        └─ spell-engine.ts + nspell + dictionaries
```

- **Java side.** `SpellCheckSupport` keeps the enabled flag, the language
  and the custom words. While spell checking is enabled it puts a
  `<spell-check-indicator>` element into the field's `suffix` slot and sets
  its `language` and `customWords` properties. Disabling removes the element.
  No server round trips happen while the user types or fixes words, except
  the field's normal value synchronization.
- **Indicator.** It listens to the field's public `value-changed` event and
  checks the text 600 ms after the last change. It shows a compact `abc`
  button: a red badge with the number of possible misspellings, a green ✓
  when there are none, or `!` if checking failed. The button opens a popover
  listing each misspelled word in context, with suggestion buttons and
  *Ignore*. The element hides itself when the field is read-only or
  disabled. While it is active it turns off the browser's own `spellcheck`
  on the native input, so two different checkers don't report conflicting
  results.
- **Worker.** Parsing the Norwegian dictionary takes about 1 s of CPU and
  about 100 MB of memory, so it runs in a Web Worker and never blocks the
  page. One worker serves every field on the page and keeps one checker per
  language. A dictionary is downloaded and parsed only when a field first
  needs that language, and at most once per page load.
- **Dictionaries.** These are imported with Vite's `?url` suffix. Vite emits
  them as separate, content-hashed files in `VAADIN/build/` of the
  production bundle, so they are not part of the initial JavaScript bundle
  and are served (and long-term cached) by the application itself.

### Why not red underlines inside the field?

`<input>` and `<textarea>` cannot style ranges of their own text. Inline
underlines would need either a transparent overlay that mirrors the text
layout (fragile with scrolling, wrapping, zoom, IME and theming) or a
`contenteditable` replacement for the Vaadin field, which would break its
value, validation and accessibility semantics. The indicator plus popover
uses only public Vaadin APIs and keeps the fields completely standard.

### Correctness details

- **Stale results.** Every check gets a version number. A result is only
  shown if no newer check was started in the meantime, and detach, disable,
  read-only and language changes invalidate all in-flight checks
  (`CheckScheduler`).
- **Replacing a word.** The replacement only happens if the field still
  contains exactly the text that was checked and the word is still at the
  same offsets. Otherwise the field is re-checked and nothing is replaced.
  Only that one occurrence is replaced, so repeated words, whitespace and
  line breaks are untouched. The new text is written to the native input,
  followed by `input` and `change` events, the same way Vaadin's own clear
  button does it. The value therefore reaches the server in every
  `ValueChangeMode`, and Binder validation runs as usual. The user's caret is
  kept in place, and focus stays in the popover so several words can be
  fixed in a row. *Escape* returns focus to the field.
- **Ignore** hides the word in that field until the page is reloaded (it is
  sent along as an accepted word; the dictionary itself is never modified).
- **Custom words** (`addSpellCheckWord`) are sent with each check of that
  field only. They never modify the shared dictionary, so they cannot leak
  to other fields or users.
- **Tokenization** is Unicode-aware (`\p{L}\p{M}\p{N}`, so æ, ø, å and
  decomposed letters work) and keeps internal apostrophes (`don't`, `it’s`)
  and hyphens. Offsets are JavaScript UTF-16 indices, the same as the input's
  selection API. Hyphenated words that aren't in the dictionary are checked
  part by part, like Hunspell does. URLs, e-mail addresses, words containing
  digits, ALL-CAPS abbreviations and single letters are not checked.

## Dictionaries and licenses

| Language | Files | Source | License |
|---|---|---|---|
| nb-NO | `nb-NO.aff`, `nb-NO.dic` | LibreOffice dict-no 3.0 (2026-08, commit `32b006a`): Norsk Ordbank + 2018–2024 revisions of Bokmålsordboka | Word list: CC BY 4.0 (Nasjonalbiblioteket) and CLARIN PUB +BY; affix file: GPL-2.0 (spell-norwegian) |
| nb-NO | `nb-NO-compound-roots.dic` | Roots with the compound flag from spell-norwegian 2.2 (npm `dictionary-nb` 3.0.0) | GPL-2.0 |
| en-US | `en-US.aff`, `en-US.dic` | SCOWL 2020.12.07 en_US (npm `dictionary-en` 4.0.0) | MIT-like / BSD (SCOWL) |
| en-GB | `en-GB.aff`, `en-GB.dic` | SCOWL 2020.12.07 en_GB-ise (npm `dictionary-en-gb` 3.0.0) | MIT-like / BSD (SCOWL) |
| engine | bundled into the worker | `nspell` 2.1.5 (npm, declared with `@NpmPackage`) | MIT |

The license texts and attributions are in
`src/main/resources/META-INF/licenses/spellcheck/` and are packaged into the
application jar.

**Distribution obligations.** When you distribute the application (for
example the jar or a Docker image), keep that folder in it. GPL-2.0 applies
to the Norwegian affix file and compound roots. Those files are plain text,
which is their preferred form for modification, so shipping them together
with the GPL text and copyright notices meets the source requirement. They
are separate data files that the spell checker reads, not code linked with
the application. CC BY 4.0 and CLARIN PUB +BY require attribution to
Nasjonalbiblioteket and CLARINO/Språkbanken, which `NOTICE.md` and
`nb-NO-README_NO.txt` provide. The SCOWL and MIT licenses require their
copyright notices to be kept. Have your legal team confirm this for your own
distribution model.

**Updating.** `dictionaries/update-dictionaries.sh` rebuilds every dictionary
file from pinned upstream sources (development only, needs internet) and
verifies the result against `dictionaries/SHA256SUMS`.

### Why these Norwegian files?

The npm package `dictionary-nb` (spell-norwegian 2.2) misses very common
words such as *noe* and *noen*, and newer vocabulary (*koronapandemien*,
*skjermtid*). LibreOffice dict-no 3.0 has all of them and loads faster
(about 0.6 s vs 1.7 s), but it is a flag-less full-form list. It therefore
cannot use the affix file's `COMPOUNDFLAG` rule, and common compounds that
aren't listed (*klimakrise*, *mobilbank*, *hjemmeskole*) are rejected. Both
use the identical spell-norwegian affix file, so the compound rule is fed
with the spell-norwegian roots that carry the compound flag.

nspell itself does not implement `COMPOUNDFLAG`. `spell-engine.ts`
implements Hunspell's rule as the affix file declares it: each part must be
at least `COMPOUNDMIN` (4) letters, every part except the last must be a
root with the compound flag, the last part may be any inflected form of such
a root, and `CHECKCOMPOUNDTRIPLE` applies. It is not a heuristic of its own.

## Limitations

- **No inline underlines.** See above. Misspellings are shown in the popover,
  where the word is underlined in its context.
- **Norwegian compounds.** A misspelling that happens to form a legal
  compound is accepted, exactly as in Hunspell. For example *barnhage*
  (barn + hage) is accepted instead of being corrected to *barnehage*.
  Compounds whose first part isn't a compound root are rejected unless the
  dictionary lists them, and so are compounds with a part shorter than four
  letters (*hjemmekontordag*). `SIMPLIFIEDTRIPLE` is not implemented.
  Suggestions never propose new compounds.
- **Memory and first-use cost.** Norwegian costs about 100 MB in the worker
  and roughly 1 s to parse on first use per page load. English costs about
  13 MB. The 9 MB `nb-NO.dic` is downloaded once and then served from the
  browser cache (hashed file name). Enabling HTTP compression for these files
  on the server or proxy reduces the download to about 2 MB.
- **Ignore** is per field and per page load. It is not persisted, and
  *Add to dictionary* is intentionally not offered; use
  `addSpellCheckWord` for organization vocabulary.
- **UI texts** in the indicator and popover are English.

## Tests

```bash
./mvnw test              # Java: component API, Binder/value semantics, demo view (browserless)
npm run test:frontend    # Engine and async tests against the real dictionaries (Node 22.18+)
```
