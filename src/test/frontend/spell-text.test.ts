/**
 * Tests for moving underlines along with edits made after the last check.
 * Run with: npm run test:frontend
 */
import { describe, test } from 'node:test';
import assert from 'node:assert/strict';
import {
  ALL_LANGUAGES,
  parseSharedVocabulary,
  remapIssues,
  unionOf,
  type Token,
} from '../../main/frontend/spellcheck/spell-text.ts';

/** Finds every occurrence of `word` in `text` as tokens. */
function find(text: string, ...words: string[]): Token[] {
  return words
    .flatMap((word) => [...text.matchAll(new RegExp(word, 'g'))].map((m) => ({ word, start: m.index, end: m.index + word.length })))
    .sort((a, b) => a.start - b.start);
}

/** The words at the remapped positions in the new text. */
const wordsAt = (text: string, issues: Token[]) => issues.map((i) => text.slice(i.start, i.end));

describe('remapIssues', () => {
  const original = 'Jeg spisste frokost på kjøkenbordet i går.';
  const issues = find(original, 'spisste', 'kjøkenbordet');

  test('returns the same issues for unchanged text', () => {
    assert.deepEqual(remapIssues(original, original, issues), issues);
  });

  test('keeps issues before the edit and shifts issues after it', () => {
    const edited = original.replace('frokost', 'frokost og lunsj');
    const remapped = remapIssues(original, edited, issues);
    assert.deepEqual(wordsAt(edited, remapped), ['spisste', 'kjøkenbordet']);
    assert.equal(remapped[1].start, issues[1].start + ' og lunsj'.length);
  });

  test('typing at the end keeps all underlines', () => {
    const edited = `${original} Det var gøy`;
    assert.deepEqual(wordsAt(edited, remapIssues(original, edited, issues)), ['spisste', 'kjøkenbordet']);
  });

  test('deleting text before a word shifts it left', () => {
    const edited = original.replace('Jeg ', '');
    assert.deepEqual(wordsAt(edited, remapIssues(original, edited, issues)), ['spisste', 'kjøkenbordet']);
  });

  test('drops an issue when the word itself is edited', () => {
    const edited = original.replace('spisste', 'spiste');
    assert.deepEqual(wordsAt(edited, remapIssues(original, edited, issues)), ['kjøkenbordet']);
  });

  test('drops an issue when a letter is typed directly after or before it', () => {
    const after = original.replace('spisste', 'spisstex');
    assert.deepEqual(wordsAt(after, remapIssues(original, after, issues)), ['kjøkenbordet']);
    const before = original.replace('kjøkenbordet', 'økjøkenbordet');
    assert.deepEqual(wordsAt(before, remapIssues(original, before, issues)), ['spisste']);
  });

  test('typing a space or punctuation after a word keeps it', () => {
    const edited = original.replace('spisste', 'spisste,');
    assert.deepEqual(wordsAt(edited, remapIssues(original, edited, issues)), ['spisste', 'kjøkenbordet']);
  });

  test('handles repeated words and multi-line text', () => {
    const text = 'teh first\nteh second\n\nteh third';
    const all = find(text, 'teh');
    const edited = text.replace('second', 'second line');
    assert.deepEqual(remapIssues(text, edited, all).map((i) => i.start), [0, 10, edited.lastIndexOf('teh')]);
    assert.deepEqual(wordsAt(edited, remapIssues(text, edited, all)), ['teh', 'teh', 'teh']);
  });

  test('clearing the text drops everything', () => {
    assert.deepEqual(remapIssues(original, '', issues), []);
  });
});

describe('shared vocabulary', () => {
  test('parses "language TAB word" lines into sets per language', () => {
    const vocabulary = parseSharedVocabulary('*\tDataTek\nnb-NO\tsaksbehandlingssystem\n*\tFjordlyx\nen-GB\tcolour-ish\n');
    assert.deepEqual([...vocabulary.get(ALL_LANGUAGES)!], ['DataTek', 'Fjordlyx']);
    assert.deepEqual([...vocabulary.get('nb-NO')!], ['saksbehandlingssystem']);
    assert.equal(vocabulary.has('en-US'), false);
  });

  test('ignores empty and malformed lines', () => {
    assert.equal(parseSharedVocabulary('').size, 0);
    assert.equal(parseSharedVocabulary('\n\t\nnb-NO\t\nnoTab').size, 0);
  });

  test('unionOf accepts a word from any of the sets and skips missing ones', () => {
    const union = unionOf(new Set(['a']), undefined, new Set(['b']));
    assert.equal(union.has('a'), true);
    assert.equal(union.has('b'), true);
    assert.equal(union.has('c'), false);
  });
});
