/**
 * Async-correctness tests for the debounce/stale-result logic used by
 * <spell-check-indicator>. Run with: npm run test:frontend
 */
import { readFileSync } from 'node:fs';
import { describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { CheckScheduler } from '../../main/frontend/spellcheck/check-scheduler.ts';
import { checkText, createSpellChecker, type SpellingIssue } from '../../main/frontend/spellcheck/spell-engine.ts';

const DELAY = 30;
const wait = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

/** A promise that the test resolves explicitly, to control result ordering. */
function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (error: unknown) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

function recorder<T>() {
  const results: Array<{ result: T; text: string }> = [];
  const errors: unknown[] = [];
  return {
    results,
    errors,
    onResult: (result: T, text: string) => results.push({ result, text }),
    onError: (error: unknown) => errors.push(error),
  };
}

describe('CheckScheduler', () => {
  test('debounces rapid changes into a single check of the latest text', async () => {
    const runs: string[] = [];
    const rec = recorder<string>();
    const scheduler = new CheckScheduler(async (text) => (runs.push(text), text.toUpperCase()), rec.onResult, rec.onError, DELAY);

    for (const text of ['h', 'he', 'hei', 'hei p', 'hei på']) {
      scheduler.schedule(text);
      await wait(DELAY / 3);
    }
    assert.deepEqual(runs, [], 'nothing runs while typing');
    assert.ok(scheduler.isPending);
    await wait(DELAY * 2);
    assert.deepEqual(runs, ['hei på']);
    assert.deepEqual(rec.results, [{ result: 'HEI PÅ', text: 'hei på' }]);
  });

  test('a slow, older check never overwrites a newer result', async () => {
    const pending = new Map<string, ReturnType<typeof deferred<string>>>();
    const rec = recorder<string>();
    const scheduler = new CheckScheduler(
      (text) => {
        const d = deferred<string>();
        pending.set(text, d);
        return d.promise;
      },
      rec.onResult,
      rec.onError,
      DELAY,
    );

    scheduler.checkNow('old');
    scheduler.checkNow('new');
    pending.get('new')!.resolve('new result');
    await wait(1);
    pending.get('old')!.resolve('old result');
    await wait(1);

    assert.deepEqual(rec.results, [{ result: 'new result', text: 'new' }]);
  });

  test('cancel() discards in-flight results and pending timers (detach / disable)', async () => {
    const d = deferred<string>();
    const runs: string[] = [];
    const rec = recorder<string>();
    const scheduler = new CheckScheduler(
      (text) => {
        runs.push(text);
        return d.promise;
      },
      rec.onResult,
      rec.onError,
      DELAY,
    );

    scheduler.checkNow('in flight');
    scheduler.schedule('debounced');
    scheduler.cancel();
    assert.equal(scheduler.isPending, false);
    d.resolve('late');
    await wait(DELAY * 2);

    assert.deepEqual(runs, ['in flight']);
    assert.deepEqual(rec.results, []);
  });

  test('only errors of the latest check are reported', async () => {
    const first = deferred<string>();
    const second = deferred<string>();
    const queue = [first, second];
    const rec = recorder<string>();
    const scheduler = new CheckScheduler(() => queue.shift()!.promise, rec.onResult, rec.onError, DELAY);

    scheduler.checkNow('a');
    scheduler.checkNow('b');
    first.reject(new Error('stale failure'));
    second.reject(new Error('current failure'));
    await wait(1);

    assert.deepEqual(
      rec.errors.map((e) => (e as Error).message),
      ['current failure'],
    );
  });

  test('runtime language switching delivers results for the new language only', async () => {
    const dir = new URL('../../main/frontend/spellcheck/dictionaries/', import.meta.url);
    const load = (code: string) =>
      createSpellChecker(
        readFileSync(new URL(`${code}/${code}.aff`, dir), 'utf8'),
        readFileSync(new URL(`${code}/${code}.dic`, dir), 'utf8'),
      );
    const checkers = { 'en-US': load('en-US'), 'en-GB': load('en-GB') };
    let language: keyof typeof checkers = 'en-US';
    const gate = deferred<void>();

    const rec = recorder<SpellingIssue[]>();
    const scheduler = new CheckScheduler(
      async (text) => {
        const lang = language;
        // The en-US check is held back until after the language switch.
        if (lang === 'en-US') {
          await gate.promise;
        }
        return checkText(checkers[lang], text);
      },
      rec.onResult,
      rec.onError,
      DELAY,
    );

    const text = 'The colour of the centre';
    scheduler.checkNow(text);
    language = 'en-GB';
    scheduler.checkNow(text);
    await wait(1);
    gate.resolve();
    await wait(1);

    assert.equal(rec.results.length, 1);
    assert.deepEqual(rec.results[0].result, [], 'British spelling is correct in en-GB');
  });
});
