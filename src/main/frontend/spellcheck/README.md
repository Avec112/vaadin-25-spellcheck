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
            ├─ SpellingUnderlines            (spell-check-underlines.ts: red wavy lines in the field)
            ├─ <vaadin-popover> + <spell-check-panel>   (suggestions: full list, or next to a word)
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
  checks the text 600 ms after the last change. Misspelled words get red
  wavy underlines inside the field. Clicking into an underlined word opens
  a small popover next to it with suggestions and *Ignore*; focus stays in
  the field, so typing simply continues and closes the popover. The `abc`
  button shows a red badge with the number of possible misspellings, a
  green ✓ when there are none, or `!` if checking failed. It opens a popover
  that lists every misspelled word in context, which is also how keyboard
  users reach all corrections. The element hides itself, and the underlines
  disappear, when the field is read-only or disabled. While it is active it turns off the browser's own `spellcheck`
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

### How the underlines are drawn

`<input>` and `<textarea>` cannot style parts of their own text, and the CSS
Custom Highlight API does not reach inside form controls. The underlines are
therefore painted as an SVG **background image of the native input element
itself** (`spell-check-underlines.ts`):

1. An invisible mirror element gets the field's font, padding, border
   widths, width and wrapping rules (from `getComputedStyle`) and the same
   text, with each misspelled word in a `<span>`. The spans' rectangles are
   exactly where the words appear in the field.
2. Wavy lines at those positions become an SVG, set as the field's
   `background-image`. The text is painted on top of it.

A background moves with the element, so the lines follow the text wherever
Vaadin scrolls or resizes it. That includes the auto-growing text area,
which scrolls inside Vaadin's shadow DOM, where an overlay element could not
follow. When the native element scrolls its own content (the text field's
horizontal scroll), the background is offset by `scrollLeft`. The lines are
redrawn when results change, when the element is resized, and after web
fonts have loaded. While the user types, underlines on words the edit did
not touch are moved along immediately (`remapIssues` in `spell-text.ts`),
so they don't flicker while waiting for the next check.

Nothing about input handling changes: only `background-*` inline styles of
the native element are set, and they are restored when spell checking is
disabled.

### Correctness details

- **Stale results.** Every check gets a version number. A result is only
  shown if no newer check was started in the meantime, and detach, disable,
  read-only and language changes invalidate all in-flight checks
  (`CheckScheduler`).
- **Replacing a word.** Edits made after a check move the known
  misspellings along (or drop those the edit touched). A replacement only
  happens if the word is still exactly at the expected offsets; otherwise
  the field is re-checked and nothing is replaced.
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

- **Underline accuracy.** The underlines depend on the mirror reproducing
  the field's text layout. Fonts, padding, wrapping and scrolling have been
  verified with Lumo in Chromium and Firefox. Custom CSS that changes text
  layout in properties not copied by `spell-check-underlines.ts` (see
  `LAYOUT_PROPERTIES`) could offset them. A style change that does not
  resize the field (for example switching to dark mode) takes effect at the
  next redraw, which happens on the next check.
- **Underline color.** It is the theme's error color, sampled when the
  underlines are drawn.
- **Content Security Policy.** The underlines are `data:` SVG images. An
  application with a strict CSP must allow `img-src data:`.
- **Word popover by mouse only.** Clicking into an underlined word opens
  suggestions next to it. Keyboard users use the `abc` button, which lists
  all words.
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
