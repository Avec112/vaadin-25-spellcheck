/**
 * Pure spell-checking logic: tokenization, word checking and suggestions. It
 * has no DOM dependencies so it runs in the Web Worker and in Node-based tests
 * against the real dictionaries. Only the worker imports this module (it
 * pulls in nspell); the page uses spell-text.ts.
 *
 * All offsets are JavaScript string indices (UTF-16 code units).
 */
import nspell from 'nspell';
import { isAccepted, type SpellingIssue, type Token, type WordSet } from './spell-text';

export type { SpellingIssue, Token };

export interface SpellChecker {
  /** Whether the word is spelled correctly according to the dictionary. */
  correct(word: string): boolean;
  /** Correction suggestions, best first. */
  suggest(word: string): string[];
}

export interface CheckOptions {
  /** Words accepted in addition to the dictionary (custom, ignored and shared words). */
  acceptedWords?: WordSet;
  /** Maximum number of reported issues (protects against huge texts). */
  maxIssues?: number;
  /** Maximum number of suggestions per issue. */
  maxSuggestions?: number;
}

const DEFAULT_MAX_ISSUES = 100;
const DEFAULT_MAX_SUGGESTIONS = 5;
const CACHE_LIMIT = 20_000;
const MAX_COMPOUND_PARTS = 4;

/**
 * A word is a run of letters, combining marks and digits, optionally joined by
 * single hyphens or apostrophes ("e-post", "don't", "it’s"). Leading/trailing
 * punctuation is never part of a word. The `u` flag makes \p{...} classes
 * Unicode-aware, so æ, ø, å and decomposed characters (a + U+030A) work.
 */
const WORD_PATTERN = /[\p{L}\p{M}\p{N}]+(?:[-'’][\p{L}\p{M}\p{N}]+)*/gu;

/** URLs and e-mail addresses are never spell checked. */
const SKIP_PATTERN =
  /(?:https?:\/\/|www\.)[^\s<>"]+|[\p{L}\p{N}._%+-]+@[\p{L}\p{N}-]+(?:\.[\p{L}\p{N}-]+)+/gu;

const DIGIT = /\p{N}/u;
const LETTER = /\p{L}/u;

/** Splits text into word tokens, skipping URLs and e-mail addresses. */
export function tokenize(text: string): Token[] {
  if (!text) {
    return [];
  }
  const skipped: Array<[number, number]> = [];
  for (const match of text.matchAll(SKIP_PATTERN)) {
    skipped.push([match.index, match.index + match[0].length]);
  }
  const tokens: Token[] = [];
  for (const match of text.matchAll(WORD_PATTERN)) {
    const start = match.index;
    const end = start + match[0].length;
    if (!skipped.some(([s, e]) => start < e && end > s)) {
      tokens.push({ word: match[0], start, end });
    }
  }
  return tokens;
}

/**
 * Creates a checker from Hunspell `.aff`/`.dic` contents.
 *
 * nspell implements most of Hunspell but ignores `COMPOUNDFLAG`, which the
 * Norwegian affix file declares for productive compounds (vinter + jakke =
 * "vinterjakke"). That rule is implemented here exactly as Hunspell defines
 * it: every part must be at least `COMPOUNDMIN` characters, all parts except
 * the last must be bare dictionary roots carrying the compound flag, the last
 * part may be any inflected form of such a root, and `CHECKCOMPOUNDTRIPLE`
 * rejects three identical letters across a boundary.
 *
 * @param compoundRoots optional `.dic` content (same affix file) that supplies
 *   the roots carrying the compound flag, for dictionaries whose main word
 *   list is a plain full-form list without flags. Defaults to `dic`.
 */
export function createSpellChecker(aff: string, dic: string, compoundRoots: string = dic): SpellChecker {
  const dictionary = nspell(aff, dic);
  const compounds = createCompoundChecker(aff, compoundRoots);
  const correctCache = new Map<string, boolean>();
  const suggestCache = new Map<string, string[]>();

  const correct = (word: string): boolean => {
    let result = correctCache.get(word);
    if (result === undefined) {
      result = dictionary.correct(word) || (compounds?.(word) ?? false);
      remember(correctCache, word, result);
    }
    return result;
  };

  const suggest = (word: string): string[] => {
    let result = suggestCache.get(word);
    if (result === undefined) {
      result = dictionary.suggest(word);
      remember(suggestCache, word, result);
    }
    return result;
  };

  return { correct, suggest };
}

function remember<T>(cache: Map<string, T>, key: string, value: T) {
  if (cache.size >= CACHE_LIMIT) {
    cache.clear();
  }
  cache.set(key, value);
}

/** Returns a COMPOUNDFLAG checker, or `undefined` if the dictionary has none. */
function createCompoundChecker(aff: string, dic: string): ((word: string) => boolean) | undefined {
  const directive = (name: string) => aff.match(new RegExp(`^${name}[ \\t]+(\\S+)`, 'm'))?.[1];
  const compoundFlag = directive('COMPOUNDFLAG');
  if (!compoundFlag) {
    return undefined;
  }
  const minLength = Number(directive('COMPOUNDMIN') ?? 3);
  const checkTriple = /^CHECKCOMPOUNDTRIPLE\b/m.test(aff);
  const flagType = directive('FLAG');

  // Roots that may be combined, plus a second (small) nspell instance that only
  // knows those roots, used to accept inflected forms as the last part.
  const roots = new Set<string>();
  const rootLines: string[] = [];
  for (const line of dic.split(/\r?\n/).slice(1)) {
    const slash = line.indexOf('/');
    if (slash <= 0) {
      continue;
    }
    const flags = line.slice(slash + 1).split(/\s/, 1)[0];
    if (parseFlags(flags, flagType).includes(compoundFlag)) {
      roots.add(line.slice(0, slash));
      rootLines.push(line);
    }
  }
  const lastParts = nspell(aff, `${rootLines.length}\n${rootLines.join('\n')}`);

  // "trafikk" + "kork": three identical letters across the boundary.
  const hasTriple = (head: string, tail: string) => {
    const [a, b, c, d] = [head.at(-2), head.at(-1), tail[0], tail[1]];
    return (a === b && b === c) || (b === c && c === d);
  };

  const isCompound = (word: string, parts: number): boolean => {
    for (let split = minLength; split <= word.length - minLength; split++) {
      const head = word.slice(0, split);
      const tail = word.slice(split);
      if (!roots.has(head) || (checkTriple && hasTriple(head, tail))) {
        continue;
      }
      if (lastParts.correct(tail) || (parts + 1 < MAX_COMPOUND_PARTS && isCompound(tail, parts + 1))) {
        return true;
      }
    }
    return false;
  };

  return (word: string) => {
    if (word.length < 2 * minLength) {
      return false;
    }
    const lower = word.toLowerCase();
    // Lowercase words, or capitalized words (sentence start), like Hunspell.
    const capitalized = word === word[0] + lower.slice(1);
    return (word === lower || capitalized) && isCompound(lower, 1);
  };
}

function parseFlags(flags: string, flagType: string | undefined): string[] {
  if (flagType === 'long') {
    return flags.match(/../g) ?? [];
  }
  if (flagType === 'num') {
    return flags.split(',');
  }
  return Array.from(flags);
}

/** Checks a whole text and returns the misspelled words, in text order. */
export function checkText(checker: SpellChecker, text: string, options: CheckOptions = {}): SpellingIssue[] {
  const maxIssues = options.maxIssues ?? DEFAULT_MAX_ISSUES;
  const maxSuggestions = options.maxSuggestions ?? DEFAULT_MAX_SUGGESTIONS;
  const issues: SpellingIssue[] = [];

  const isWordOk = (word: string) =>
    !needsChecking(word) || isAccepted(word, options.acceptedWords) || checker.correct(word.replace(/’/g, "'"));

  for (const token of tokenize(text)) {
    if (issues.length >= maxIssues) {
      break;
    }
    if (isWordOk(token.word)) {
      continue;
    }
    // Like Hunspell's default BREAK rules: a hyphenated word is fine if each
    // part is ("e-post" is in the dictionary, "TV-kanal" is checked per part).
    const parts = token.word.includes('-') ? splitOnHyphens(token) : [token];
    for (const part of parts) {
      if (parts.length > 1 && isWordOk(part.word)) {
        continue;
      }
      issues.push({ ...part, suggestions: suggestionsFor(checker, part.word, maxSuggestions) });
      if (issues.length >= maxIssues) {
        break;
      }
    }
  }
  return issues;
}

function splitOnHyphens(token: Token): Token[] {
  const parts: Token[] = [];
  let offset = token.start;
  for (const word of token.word.split('-')) {
    parts.push({ word, start: offset, end: offset + word.length });
    offset += word.length + 1;
  }
  return parts;
}

/**
 * Words that are not checked: single characters, anything containing a digit
 * ("3D", "COVID19") and ALL-CAPS abbreviations ("NAV", "HTML"), matching the
 * default behavior of common word processors.
 */
function needsChecking(word: string): boolean {
  if (word.length < 2 || DIGIT.test(word) || !LETTER.test(word)) {
    return false;
  }
  return word !== word.toUpperCase();
}

function suggestionsFor(checker: SpellChecker, word: string, max: number): string[] {
  const curly = word.includes('’');
  return checker
    .suggest(word.replace(/’/g, "'"))
    .slice(0, max)
    .map((suggestion) => (curly ? suggestion.replace(/'/g, '’') : suggestion));
}

