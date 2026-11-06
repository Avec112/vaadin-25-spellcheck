/**
 * Language verification tests for the spell engine. These load the real,
 * bundled Hunspell dictionaries (no mocks), so they verify actual Norwegian
 * Bokmål, American and British English behavior.
 *
 * Run with: npm run test:frontend
 */
import { readFileSync } from 'node:fs';
import { before, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { checkText, createSpellChecker, tokenize, type SpellChecker } from '../../main/frontend/spellcheck/spell-engine.ts';
import { applyReplacement } from '../../main/frontend/spellcheck/spell-text.ts';

const dictionaryDir = new URL('../../main/frontend/spellcheck/dictionaries/', import.meta.url);

function load(code: string, withCompoundRoots = false): SpellChecker {
  const read = (file: string) => readFileSync(new URL(`${code}/${file}`, dictionaryDir), 'utf8');
  const roots = withCompoundRoots ? read(`${code}-compound-roots.dic`) : undefined;
  return createSpellChecker(read(`${code}.aff`), read(`${code}.dic`), roots);
}

const misspelled = (checker: SpellChecker, text: string, accepted?: string[]) =>
  checkText(checker, text, { acceptedWords: accepted ? new Set(accepted) : undefined }).map((i) => i.word);

let nb: SpellChecker;
let enUS: SpellChecker;
let enGB: SpellChecker;

before(() => {
  nb = load('nb-NO', true);
  enUS = load('en-US');
  enGB = load('en-GB');
});

describe('tokenize', () => {
  test('handles empty and whitespace-only text', () => {
    assert.deepEqual(tokenize(''), []);
    assert.deepEqual(tokenize(' \n\t  \r\n'), []);
  });

  test('recognizes Norwegian letters and punctuation-adjacent words', () => {
    const text = '«Blåbærsyltetøy», sa Ærlige Åse. Øl?';
    assert.deepEqual(
      tokenize(text).map((t) => t.word),
      ['Blåbærsyltetøy', 'sa', 'Ærlige', 'Åse', 'Øl'],
    );
  });

  test('keeps apostrophes and hyphens inside words only', () => {
    assert.deepEqual(
      tokenize("'don't' it’s e-post -- end-").map((t) => t.word),
      ["don't", 'it’s', 'e-post', 'end'],
    );
  });

  test('offsets are UTF-16 indices that map back to the text, across newlines', () => {
    const text = 'Første linje\n\n  andre   linje med 😀 emoji og å\r\nslutt';
    for (const token of tokenize(text)) {
      assert.equal(text.slice(token.start, token.end), token.word);
    }
  });

  test('skips URLs and e-mail addresses', () => {
    assert.deepEqual(
      tokenize('Se https://exampel.no/sidde og skriv til ola.nordmann@firmma.no nå').map((t) => t.word),
      ['Se', 'og', 'skriv', 'til', 'nå'],
    );
  });
});

describe('Norwegian Bokmål (nb-NO)', () => {
  test('accepts correct words and common inflections', () => {
    const words = [
      'hus', 'huset', 'husene', 'husets', 'gutt', 'gutten', 'guttene', 'jente', 'jenta', 'jenten',
      'spiste', 'spiser', 'spist', 'være', 'gått', 'regjeringen', 'kommunestyre', 'øl', 'ål',
      'Norge', 'Oslo', 'Ærlig', 'blåbærsyltetøy',
    ];
    for (const word of words) {
      assert.ok(nb.correct(word), `expected "${word}" to be correct`);
    }
  });

  test('accepts the most frequent Norwegian words', () => {
    const words =
      'og i det er som på en til å han av for ikke med jeg at de var seg hun den om har et men så vi sa kan fra ' +
      'ble ut eller hadde nå skal etter du ved også hva bare noen noe alle andre over mot sin når hvis enn hvor ' +
      'mange mye selv blir får kom hans hennes dette denne disse kunne skulle ville måtte hvordan hvorfor fordi';
    const rejected = words.split(' ').filter((word) => !nb.correct(word));
    assert.deepEqual(rejected, []);
  });

  test('accepts recent vocabulary from the 2018–2024 dictionary revision', () => {
    for (const word of ['koronapandemien', 'elsparkesykkel', 'skjermtid', 'strømprisene']) {
      assert.ok(nb.correct(word), word);
    }
  });

  test('detects misspelled words', () => {
    for (const word of ['reggjeringen', 'kjøkenbord', 'datamskin', 'barnehaage', 'prosjektlder', 'gutn', 'vere', 'hjertte', 'ærlg']) {
      assert.equal(nb.correct(word), false, `expected "${word}" to be misspelled`);
    }
  });

  test('returns useful Norwegian suggestions', () => {
    const expectations: Record<string, string> = {
      reggjeringen: 'regjeringen',
      kjøkenbord: 'kjøkkenbord',
      datamskin: 'datamaskin',
      barnehaage: 'barnehage',
      kunnskapsbassert: 'kunnskapsbasert',
      sykhus: 'sykehus',
      prosjektlder: 'prosjektleder',
    };
    for (const [wrong, right] of Object.entries(expectations)) {
      assert.ok(nb.suggest(wrong).includes(right), `expected "${right}" among suggestions for "${wrong}"`);
    }
  });

  test('suggestions contain æ, ø and å', () => {
    assert.ok(nb.suggest('kjokken').includes('kjøkken'));
    assert.ok(nb.suggest('sostre').includes('søstre'));
    assert.ok(nb.suggest('ærlg').includes('ærlig'));
  });

  test('accepts compounds listed in the dictionary', () => {
    for (const word of ['fotballkamp', 'sykehuset', 'arbeidsplass', 'kjøkkenbord', 'datasikkerhet', 'sikkerhetsrutinene']) {
      assert.ok(nb.correct(word), word);
    }
  });

  test('accepts productive compounds of COMPOUNDFLAG roots (vinter + jakke)', () => {
    for (const word of ['vinterjakke', 'vinterjakken', 'Vinterjakke', 'klimakrisen', 'mobilbank', 'hjemmeskole']) {
      assert.ok(nb.correct(word), word);
    }
  });

  test('rejects compounds the dictionary does not allow', () => {
    // "dag" is shorter than COMPOUNDMIN (4) and "hjemmekontor" is not a compound root.
    assert.equal(nb.correct('hjemmekontordag'), false);
    // Misspelled part.
    assert.equal(nb.correct('vinterjakek'), false);
    // CHECKCOMPOUNDTRIPLE: three identical letters across the boundary.
    assert.equal(nb.correct('trafikkkork'), false);
    // Mixed case is never treated as a compound.
    assert.equal(nb.correct('vinterJakke'), false);
  });

  test('known limitation: a misspelling that forms a legal compound is accepted', () => {
    // "barnhage" (for "barnehage") is barn + hage, both compoundable roots, so
    // Hunspell's COMPOUNDFLAG rule accepts it, exactly as Hunspell itself does.
    assert.equal(nb.correct('barnhage'), true);
    // Without compounding, the misspelling would be caught but common
    // compounds such as "klimakrise" would be rejected.
    const withoutCompounds = load('nb-NO');
    assert.equal(withoutCompounds.correct('barnhage'), false);
    assert.equal(withoutCompounds.correct('klimakrise'), false);
  });

  test('finds every error in a sentence with correct positions', () => {
    const text = 'Jeg spisste frokost på kjøkenbordet, og gutn løp til skolen.';
    const issues = checkText(nb, text);
    assert.deepEqual(issues.map((i) => i.word), ['spisste', 'kjøkenbordet', 'gutn']);
    for (const issue of issues) {
      assert.equal(text.slice(issue.start, issue.end), issue.word);
      assert.ok(issue.suggestions.length > 0, `suggestions for ${issue.word}`);
    }
    assert.ok(issues[0].suggestions.includes('spiste'));
  });

  test('does not flag a fully correct Norwegian text', () => {
    const text =
      'Regjeringen la i dag fram statsbudsjettet. Barna i barnehagen spiste blåbær,\n' +
      'og de ansatte på sykehuset fikk nye datamaskiner. Vi møtes på kontoret i Oslo.';
    assert.deepEqual(misspelled(nb, text), []);
  });
});

describe('English (en-US / en-GB)', () => {
  test('US English accepts American and rejects British spelling', () => {
    for (const word of ['color', 'organize', 'center', 'theater', 'analyze']) {
      assert.ok(enUS.correct(word), word);
    }
    assert.deepEqual(misspelled(enUS, 'colour organise centre theatre analyse'), [
      'colour', 'organise', 'centre', 'theatre', 'analyse',
    ]);
  });

  test('UK English accepts British and rejects American spelling', () => {
    for (const word of ['colour', 'organise', 'centre', 'theatre', 'analyse']) {
      assert.ok(enGB.correct(word), word);
    }
    assert.deepEqual(misspelled(enGB, 'color organize center theater analyze'), [
      'color', 'organize', 'center', 'theater', 'analyze',
    ]);
  });

  test('handles straight and typographic apostrophes', () => {
    assert.deepEqual(misspelled(enUS, "I don't think it’s the teacher's fault."), []);
    const [issue] = checkText(enUS, 'It doesn’nt matter.');
    assert.equal(issue.word, 'doesn’nt');
  });

  test('suggests corrections', () => {
    assert.ok(enUS.suggest('recieve').includes('receive'));
    assert.ok(enGB.suggest('definately').includes('definitely'));
    const [issue] = checkText(enGB, 'I will recieve it.');
    assert.ok(issue.suggestions.includes('receive'));
  });

  test('ignores abbreviations, numbers and single letters', () => {
    assert.deepEqual(misspelled(enUS, 'The HTML API returned 3D data for COVID19, item b.'), []);
  });

  test('checks hyphenated words part by part', () => {
    assert.deepEqual(misspelled(enUS, 'A well-known, well-knwon fact.'), ['knwon']);
  });
});

describe('checkText and corrections', () => {
  test('repeated misspellings are reported once per occurrence with distinct positions', () => {
    const text = 'teh cat and teh dog and teh bird';
    const issues = checkText(enUS, text);
    assert.deepEqual(issues.map((i) => i.start), [0, 12, 24]);
    assert.ok(issues.every((i) => text.slice(i.start, i.end) === 'teh'));
  });

  test('replacing one occurrence preserves all other text, whitespace and line breaks', () => {
    const text = 'teh first line\n\n\tteh  second line\r\nand teh end ';
    const issues = checkText(enUS, text);
    const updated = applyReplacement(text, issues[1], 'the');
    assert.equal(updated, 'teh first line\n\n\tthe  second line\r\nand teh end ');
    assert.deepEqual(checkText(enUS, updated!).map((i) => i.start), [0, updated!.lastIndexOf('teh')]);
  });

  test('refuses to replace when the text changed since it was checked', () => {
    const text = 'Jeg spisste frokost';
    const [issue] = checkText(nb, text);
    assert.equal(applyReplacement('Jeg spiste frokost', issue, 'spiste'), null);
    assert.equal(applyReplacement('Nå: Jeg spisste frokost', issue, 'spiste'), null);
    assert.equal(applyReplacement(text, issue, 'spiste'), 'Jeg spiste frokost');
  });

  test('custom vocabulary is accepted only where supplied', () => {
    const text = 'Systemet FooBarSystem og produktet qwixly ble levert. Qwixly virker.';
    assert.deepEqual(misspelled(nb, text), ['FooBarSystem', 'qwixly', 'Qwixly']);
    assert.deepEqual(misspelled(nb, text, ['FooBarSystem', 'qwixly']), []);
    // Case-sensitive for capitalized entries: "foobarsystem" is not accepted.
    assert.deepEqual(misspelled(nb, 'foobarsystem', ['FooBarSystem']), ['foobarsystem']);
    // The shared dictionary was not modified.
    assert.equal(nb.correct('qwixly'), false);
  });

  test('the same text gives language-specific results', () => {
    const text = 'The colour of the centre';
    assert.deepEqual(misspelled(enUS, text), ['colour', 'centre']);
    assert.deepEqual(misspelled(enGB, text), []);
    assert.deepEqual(misspelled(nb, 'Huset er stort'), []);
    assert.deepEqual(misspelled(enUS, 'Huset er stort'), ['Huset', 'stort']);
  });

  test('limits the number of reported issues', () => {
    const text = Array.from({ length: 50 }, () => 'xqzv').join(' ');
    assert.equal(checkText(enUS, text, { maxIssues: 10 }).length, 10);
  });
});
