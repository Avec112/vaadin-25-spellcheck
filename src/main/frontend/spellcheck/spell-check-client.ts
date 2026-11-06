/**
 * Main-thread client for the spell-check Web Worker.
 *
 * The dictionary files are imported with Vite's `?url` suffix: Vite copies them
 * into the production bundle (VAADIN/build) as separate, content-hashed files
 * and the imports evaluate to their URLs. Nothing is downloaded until a field
 * actually needs a language, and the files never become part of the initial
 * JavaScript bundle. All URLs point to the application's own origin.
 */
import type { SpellingIssue } from './spell-text';
import nbNoAff from './dictionaries/nb-NO/nb-NO.aff?url';
import nbNoDic from './dictionaries/nb-NO/nb-NO.dic?url';
import nbNoCompoundRoots from './dictionaries/nb-NO/nb-NO-compound-roots.dic?url';
import enUsAff from './dictionaries/en-US/en-US.aff?url';
import enUsDic from './dictionaries/en-US/en-US.dic?url';
import enGbAff from './dictionaries/en-GB/en-GB.aff?url';
import enGbDic from './dictionaries/en-GB/en-GB.dic?url';

export interface DictionaryUrls {
  aff: string;
  dic: string;
  /** Optional roots for the affix file's COMPOUNDFLAG rule (see spell-engine.ts). */
  compoundRoots?: string;
}

export interface CheckRequest {
  type: 'check';
  id: number;
  language: string;
  urls: DictionaryUrls;
  text: string;
  acceptedWords: string[];
}

/** The shared vocabulary in the server's format (see SharedVocabularySync.java). */
export interface VocabularyMessage {
  type: 'vocabulary';
  text: string;
}

export type WorkerRequest = CheckRequest | VocabularyMessage;

export type CheckResponse = { id: number; issues: SpellingIssue[] } | { id: number; error: string };

/** Keys must match SpellCheckLanguage#getCode() on the Java side. */
const DICTIONARIES: Record<string, DictionaryUrls> = {
  'nb-NO': { aff: nbNoAff, dic: nbNoDic, compoundRoots: nbNoCompoundRoots },
  'en-US': { aff: enUsAff, dic: enUsDic },
  'en-GB': { aff: enGbAff, dic: enGbDic },
};

let worker: Worker | undefined;
let nextId = 0;
const pending = new Map<number, { resolve: (issues: SpellingIssue[]) => void; reject: (error: Error) => void }>();

/*
 * Shared vocabulary. The server (SharedVocabularySync.java) stores it on the
 * window object and fires an event, because it may run before or after this
 * module is loaded. It is forwarded to the worker once per change, and the
 * fields are told to re-check.
 */
declare global {
  interface Window {
    __spellCheckSharedVocabulary?: string;
  }
}
let sharedVocabulary = window.__spellCheckSharedVocabulary;
const vocabularyListeners = new Set<() => void>();

window.addEventListener('spell-check-shared-vocabulary', () => {
  if (window.__spellCheckSharedVocabulary !== sharedVocabulary) {
    sharedVocabulary = window.__spellCheckSharedVocabulary;
    sendVocabulary();
    vocabularyListeners.forEach((listener) => listener());
  }
});

function sendVocabulary() {
  if (worker && sharedVocabulary !== undefined) {
    const message: VocabularyMessage = { type: 'vocabulary', text: sharedVocabulary };
    worker.postMessage(message);
  }
}

/** Calls the listener whenever the shared vocabulary changes. Returns a function that removes it. */
export function onSharedVocabularyChange(listener: () => void): () => void {
  vocabularyListeners.add(listener);
  return () => vocabularyListeners.delete(listener);
}

function getWorker(): Worker {
  if (!worker) {
    // This exact `new Worker(new URL(...), { type: 'module' })` form is what
    // Vite recognizes to bundle the worker as a separate file.
    worker = new Worker(new URL('./spell-check.worker.ts', import.meta.url), { type: 'module' });
    worker.addEventListener('message', (event: MessageEvent<CheckResponse>) => {
      const response = event.data;
      const request = pending.get(response.id);
      pending.delete(response.id);
      if ('error' in response) {
        request?.reject(new Error(response.error));
      } else {
        request?.resolve(response.issues);
      }
    });
    worker.addEventListener('error', (event) => {
      // The worker script itself failed; fail everything that is waiting.
      for (const request of pending.values()) {
        request.reject(new Error(event.message || 'Spell-check worker failed'));
      }
      pending.clear();
      worker = undefined;
    });
    // Messages are processed in order, so every check sees the vocabulary.
    sendVocabulary();
  }
  return worker;
}

/** Whether a dictionary is bundled for the language code. */
export function isSupportedLanguage(language: string): boolean {
  return language in DICTIONARIES;
}

/** Checks the text in the worker. Rejects for unsupported languages. */
export function checkSpelling(language: string, text: string, acceptedWords: string[]): Promise<SpellingIssue[]> {
  const dictionary = DICTIONARIES[language];
  if (!dictionary) {
    return Promise.reject(new Error(`Unsupported spell-check language: ${language}`));
  }
  const id = ++nextId;
  // Absolute URLs, because the worker resolves relative URLs against its own
  // script location rather than the page.
  const absolute = (url: string) => new URL(url, document.baseURI).href;
  const urls: DictionaryUrls = {
    aff: absolute(dictionary.aff),
    dic: absolute(dictionary.dic),
    compoundRoots: dictionary.compoundRoots && absolute(dictionary.compoundRoots),
  };
  return new Promise((resolve, reject) => {
    pending.set(id, { resolve, reject });
    const request: CheckRequest = { type: 'check', id, language, urls, text, acceptedWords };
    getWorker().postMessage(request);
  });
}
