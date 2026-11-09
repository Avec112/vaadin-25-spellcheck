# Offline spell checking for Vaadin 25

Spring Boot 4 and Vaadin Flow 25.3 application that adds **fully offline spell checking** to Vaadin text fields. It
supports **Norwegian Bokmål** (default), **American English** and **British English**, and has a **shared dictionary**
that every user can maintain.

Spelling is checked entirely in the browser against bundled Hunspell dictionaries. No cloud service, CDN or browser
extension is used, so the application works in an air-gapped environment.

```java
MyCustomTextArea description = new MyCustomTextArea("Description");
description.setSpellCheckEnabled(true);                          // Norwegian Bokmål by default
description.setSpellCheckLanguage(SpellCheckLanguage.EN_GB);     // optional, can change at runtime

MyCustomTextField title = new MyCustomTextField("Title");
title.setSpellCheckEnabled(true);
```

Detailed design, dictionary sources, licenses and limitations:
[src/main/frontend/spellcheck/README.md](src/main/frontend/spellcheck/README.md).

---

## What has been added

The project started from a generated, empty Vaadin starter (`Generated project`). Three commits have been added since.

### 1. Offline spell checking for TextArea and TextField (`3009ea4`)

**Components and Java API**

- `MyCustomTextArea` and `MyCustomTextField` extend the standard Vaadin `TextArea` and `TextField`. Value handling,
  `Binder`, validation, required, read-only, enabled state and theming are unchanged.
- The common interface `HasSpellCheck` provides `setSpellCheckEnabled`, `setSpellCheckLanguage`, `addSpellCheckWord`
  and the matching getters. Spell checking is off by default, and the default language is `SpellCheckLanguage.NB_NO`.
- `SpellCheckSupport` holds the shared logic, so it isn't duplicated in the two components. It places a small
  `<spell-check-indicator>` element in the field's suffix slot while spell checking is enabled.
- Custom words (`addSpellCheckWord`) apply to one field only and never modify the shared dictionary.

**Browser-side engine**

- [nspell](https://github.com/wooorm/nspell) 2.1.5 (Hunspell-compatible) runs in a **Web Worker**, so loading the
  Norwegian dictionary (about 1 s, about 100 MB) never freezes the page.
- There is one worker per page with one dictionary per language. A dictionary is loaded lazily, the first time a field
  needs it.
- The text is checked 600 ms after the user stops typing, never on every keystroke. Results for text that has changed
  since are discarded.
- Tokenization is Unicode-aware (æ, ø, å, apostrophes, hyphenated words). URLs, e-mail addresses, numbers and
  abbreviations are skipped.
- Norwegian compounds follow Hunspell's `COMPOUNDFLAG` rule as declared by the dictionary, which nspell does not
  implement itself. For example *vinterjakke* = vinter + jakke.

**Dictionaries (bundled, offline)**

| Language | Source | License |
|---|---|---|
| nb-NO | LibreOffice dict-no 3.0 (Norsk Ordbank, 2018–2024 revisions), plus compound roots from spell-norwegian 2.2 | CC BY 4.0, CLARIN PUB +BY, GPL-2.0 |
| en-US / en-GB | SCOWL 2020.12.07 | MIT/BSD-style |

- The dictionaries are emitted by Vite as separate, hashed files in the production bundle. They are served by the
  application itself and are not part of the initial JavaScript bundle.
- `update-dictionaries.sh` rebuilds them byte-identically from pinned sources and checks them against `SHA256SUMS`.
- License texts and attribution are in `src/main/resources/META-INF/licenses/spellcheck/` and are packaged into the jar.

**User interface**

- An `abc` button in the field shows the number of possible misspellings (or ✓). It opens a popover with every
  misspelled word in context, suggestions to replace it, and *Ignore*.
- A correction replaces only that occurrence, keeps the caret and the rest of the text, and reaches the server and
  `Binder` like normal input.
- Everything is keyboard-accessible.

**Demo, tests and build**

- Demo view at `/spellcheck-demo`, with Norwegian, US and UK examples, a field bound with `Binder`, custom words, and a
  playground for language switching, read-only, enabled and programmatic `setValue`.
- Java tests for the component API, and frontend tests that run against the real dictionaries.
- Vaadin's pre-compiled `dev.bundle` and `prod.bundle` are committed, so builds without internet access need neither
  npm nor Node.js.

### 2. Red wavy underlines in the fields (`7f2f4cd`)

- Misspelled words get **red wavy underlines directly in the text**, in both `TextArea` and `TextField`.
- **How they are drawn.** `<input>` and `<textarea>` can't style parts of their own text, so the underlines are drawn
  as an SVG background on the native input element. An invisible copy of the text, with identical font, padding and
  wrapping, measures where each word is. Because it is a background, it follows Vaadin's own scrolling and the
  auto-growing text area. Typing, selection, undo and Vaadin's value handling are unaffected.
- **While typing**, underlines on words the edit didn't touch stay in place and move with the text, without flickering.
- **Clicking into an underlined word** opens a small suggestion popover right next to it. Focus stays in the field, so
  the user can just keep typing.
- **Smaller bundle.** Text helpers moved to `spell-text.ts`, so nspell is only included in the worker bundle.
- **Verification.** Tested in Chromium and Firefox, including horizontal scrolling in `TextField`, internal scrolling in
  `TextArea` and larger fonts.

### 3. Shared dictionary with change log (`fdb23e8`)

**For users**

- **Adding words.** *Add to dictionary* in the suggestion popovers opens a dialog. The user can correct the spelling or
  capitalization (`Datatek` → `DataTek`), choose *all languages* (default) or the field's language, and add a comment.
- **Dictionary page.** The page at `/dictionary` lists all words with who added them and when, with search, add, edit
  and delete. Each change can carry a comment.
- **Change log.** It shows who changed what, when and why, also for deleted words.
- **Everyone can edit.** Every logged-in user can add, edit and delete words.
- **Live updates.** A change takes effect immediately in every open field for every user, without reloading. This uses
  Vaadin Push.

**Architecture**

- **Component library** (`spellcheck`). It knows only the interface `SharedSpellCheckVocabulary` and has no database or
  Spring dependency. It sends the word list once per browser tab, and once to the worker, not with every check.
- **Application layer** (`vocabulary`). It contains JPA entities, the change log, `VocabularyService` and the Dictionary
  view. The service handles validation, optimistic locking against concurrent edits, and notifications after commit.
  This is the package to copy into another application.
- **Flyway migration** `db/migration/V1__spellcheck_vocabulary.sql`. A unique constraint on (word, language) prevents
  duplicates. Matching is case-sensitive.

**Infrastructure** (demo setup)

- Spring Security with a login view and two demo users.
- Postgres 17 via Docker Compose (`compose.yaml`), started automatically in development.
- Database tests against real Postgres with Testcontainers.

---

## Running the application

Requirements: Java 25 and a running Docker.

Start the application from the IDE (Run/Debug, also with JRebel or a Hotswap Agent) or with:

```bash
./mvnw spring-boot:run        # Windows: mvnw.cmd spring-boot:run
```

**The database starts automatically.** You don't need to start Postgres yourself. On startup, Spring Boot's Docker
Compose support (`spring-boot-docker-compose`) finds `compose.yaml` in the project folder, starts the Postgres
container, and connects to it. Flyway then creates the tables. When the application stops, the container is stopped
too. The data is kept in a Docker volume between runs.

| How the application is started | Postgres in Docker started automatically? |
|---|---|
| IDE or `./mvnw spring-boot:run` | Yes, from `compose.yaml` |
| `java -jar target/*.jar` | No. Configure the database with environment variables, see [Production](#production) |
| `./mvnw test` | No. The tests start their own Postgres with Testcontainers |

If the application fails to start with a database or Docker error, check that:

- Docker is running.
- The working directory of the IDE run configuration is the project folder, where `compose.yaml` is.
- The IDE has reloaded the Maven project after `pom.xml` changed.

To start or inspect the database by hand, use `docker compose up -d` and `docker compose ps`.

Then open **http://localhost:8080** and log in as `anna` / `demo` or `bjorn` / `demo`.

| Page | URL |
|---|---|
| Spell check demo | `/spellcheck-demo` (also `/`) |
| Shared dictionary | `/dictionary` |

To see the shared dictionary update live, log in as `anna` and `bjorn` in two different browsers (or one normal and
one private window). Then add a word in one of them.

**Manual test**

1. In *Beskrivelse*, misspelled words are underlined in red. Click into *kjøkenbordet* and choose *kjøkkenbordet*.
2. Click the `abc` button to see all errors in a field. Try *Ignore*.
3. In *Without custom words*, click into *Fjordlyx* and choose *Add to dictionary*. The word is accepted at once,
   also in the other user's browser.
4. On `/dictionary`, edit or delete the word with a comment, and check the change log.
5. In *Playground*, switch language and toggle read-only and spell checking.

## Tests

```bash
./mvnw test              # Java: components, views (browserless), database tests (Testcontainers, needs Docker)
npm run test:frontend    # Spell engine against the real dictionaries (Node 22.18+, after a first Maven build)
```

## Production

```bash
./mvnw package
java -jar target/*.jar
```

The packaged jar does not start Docker Compose. Provide the database with environment variables:

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://dbhost:5432/spellcheck \
SPRING_DATASOURCE_USERNAME=... \
SPRING_DATASOURCE_PASSWORD=... \
java -jar target/*.jar
```

The same variables apply to the Docker image (`docker build -t spellcheck .`, then `docker run -e SPRING_DATASOURCE_URL=...
-p 8080:8080 spellcheck`).

**Air-gapped environments**

- At runtime, nothing is fetched from outside the application.
- To build without internet access, keep `src/main/bundles/prod.bundle` up to date (it is rebuilt by `./mvnw package`
  whenever the frontend changes) and committed. Vaadin then reuses it, and npm and Node.js are not needed.
- Keep `META-INF/licenses/spellcheck/` in the distributed application; the dictionary licenses require it.

## Project structure

```text
src/main/java/com/github/avec112/
  spellcheck/        MyCustomTextArea, MyCustomTextField, HasSpellCheck, SpellCheckLanguage,
                     SpellCheckSupport, SharedSpellCheckVocabulary, SharedVocabularySync,
                     AddSpellCheckWordDialog
  vocabulary/        VocabularyService, VocabularyWord(Change) + repositories, VocabularyView,
                     WordDialog, VocabularyConfiguration
  security/          SecurityConfiguration (demo users), LoginView
  views/             SpellCheckDemoView, MainLayout
src/main/frontend/spellcheck/
  spell-check-indicator.ts   UI in the field: indicator, popovers, corrections
  spell-check-underlines.ts  red wavy underlines
  check-scheduler.ts         debounce and stale-result handling
  spell-check-client.ts      page side of the worker, dictionary URLs, shared vocabulary
  spell-check.worker.ts      Web Worker: loads dictionaries, checks text
  spell-engine.ts            nspell, compounds, tokenization
  spell-text.ts              text helpers shared by page and worker
  dictionaries/              Hunspell files, SHA256SUMS, update-dictionaries.sh
src/main/resources/
  db/migration/              Flyway migrations
  META-INF/licenses/spellcheck/  third-party licenses and NOTICE.md
compose.yaml                 development Postgres
```
