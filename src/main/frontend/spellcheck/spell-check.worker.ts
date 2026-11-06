/**
 * Web Worker that owns the dictionaries. Loading the Norwegian dictionary
 * takes roughly a second of CPU and ~100 MB of memory, which would freeze
 * the page if done on the main thread. One worker serves all spell-checked
 * fields on the page and keeps one checker per language, so a dictionary is
 * downloaded and parsed at most once per page load.
 *
 * Dictionaries are fetched lazily, the first time a language is requested,
 * from URLs on the application's own origin (see spell-check-client.ts).
 *
 * The application's shared vocabulary arrives as a separate message whenever
 * it changes, and is used for every check, so it is not resent per request.
 */
import { checkText, createSpellChecker, type SpellChecker } from './spell-engine';
import { ALL_LANGUAGES, parseSharedVocabulary, unionOf, type SharedVocabulary } from './spell-text';
import type { CheckResponse, DictionaryUrls, WorkerRequest } from './spell-check-client';

const checkers = new Map<string, Promise<SpellChecker>>();
let shared: SharedVocabulary = new Map();

function checkerFor(language: string, urls: DictionaryUrls): Promise<SpellChecker> {
  let checker = checkers.get(language);
  if (!checker) {
    checker = Promise.all([
      fetchText(urls.aff),
      fetchText(urls.dic),
      urls.compoundRoots ? fetchText(urls.compoundRoots) : undefined,
    ]).then(([aff, dic, compoundRoots]) => createSpellChecker(aff, dic, compoundRoots));
    // Allow a retry (e.g. after a temporary server problem).
    checker.catch(() => checkers.delete(language));
    checkers.set(language, checker);
  }
  return checker;
}

async function fetchText(url: string): Promise<string> {
  const response = await fetch(url, { credentials: 'same-origin' });
  if (!response.ok) {
    throw new Error(`Could not load dictionary ${url}: HTTP ${response.status}`);
  }
  return response.text();
}

self.addEventListener('message', async (event: MessageEvent<WorkerRequest>) => {
  if (event.data.type === 'vocabulary') {
    shared = parseSharedVocabulary(event.data.text);
    return;
  }
  const { id, language, urls, text, acceptedWords } = event.data;
  let response: CheckResponse;
  try {
    const checker = await checkerFor(language, urls);
    // Read the shared vocabulary after loading: it may have arrived meanwhile.
    const accepted = unionOf(new Set(acceptedWords), shared.get(ALL_LANGUAGES), shared.get(language));
    response = { id, issues: checkText(checker, text, { acceptedWords: accepted }) };
  } catch (error) {
    response = { id, error: error instanceof Error ? error.message : String(error) };
  }
  self.postMessage(response);
});
