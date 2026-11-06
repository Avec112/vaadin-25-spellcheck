/**
 * Text helpers shared by the page and the worker. Kept separate from
 * spell-engine.ts so that the page's bundle does not include nspell, which
 * only the worker needs.
 *
 * All offsets are JavaScript string indices (UTF-16 code units), which is what
 * `HTMLInputElement.selectionStart`/`setSelectionRange` use as well.
 */

export interface Token {
  word: string;
  start: number;
  end: number;
}

export interface SpellingIssue extends Token {
  suggestions: string[];
}

/** Characters that may be part of a word (see WORD_PATTERN in spell-engine.ts). */
const WORD_CHAR = /[\p{L}\p{M}\p{N}'’-]/u;

/** Whether a word is accepted by the given vocabulary (custom or ignored words). */
export function isAccepted(word: string, accepted: ReadonlySet<string> | undefined): boolean {
  if (!accepted || accepted.size === 0) {
    return false;
  }
  // A lowercase entry also accepts its sentence-start capitalization.
  return accepted.has(word) || accepted.has(word[0].toLowerCase() + word.slice(1));
}

/**
 * Replaces one specific occurrence. Returns `null` if the text no longer
 * contains the expected word at the expected position (the text changed since
 * it was checked), so callers never replace the wrong text.
 */
export function applyReplacement(text: string, issue: Token, replacement: string): string | null {
  if (text.slice(issue.start, issue.end) !== issue.word) {
    return null;
  }
  return text.slice(0, issue.start) + replacement + text.slice(issue.end);
}

/**
 * Moves issues found in `oldText` to their positions in `newText` after an
 * edit, so underlines can follow the text while the user types, without
 * waiting for the next check.
 *
 * The edit is the region between the longest common prefix and suffix of the
 * two texts. Issues entirely before it keep their offsets, issues entirely
 * after it are shifted, and issues that overlap it (or now touch other word
 * characters, e.g. a letter typed right after a word) are dropped until the
 * next check decides about them.
 */
export function remapIssues<T extends Token>(oldText: string, newText: string, issues: readonly T[]): T[] {
  if (oldText === newText) {
    return [...issues];
  }
  const max = Math.min(oldText.length, newText.length);
  let prefix = 0;
  while (prefix < max && oldText[prefix] === newText[prefix]) {
    prefix++;
  }
  let suffix = 0;
  while (suffix < max - prefix && oldText[oldText.length - 1 - suffix] === newText[newText.length - 1 - suffix]) {
    suffix++;
  }
  const editEndInOld = oldText.length - suffix;
  const delta = newText.length - oldText.length;

  const result: T[] = [];
  for (const issue of issues) {
    let moved: T | undefined;
    if (issue.end <= prefix) {
      moved = issue;
    } else if (issue.start >= editEndInOld) {
      moved = { ...issue, start: issue.start + delta, end: issue.end + delta };
    }
    const before = moved && newText[moved.start - 1];
    const after = moved && newText[moved.end];
    if (moved && !(before && WORD_CHAR.test(before)) && !(after && WORD_CHAR.test(after))) {
      result.push(moved);
    }
  }
  return result;
}
