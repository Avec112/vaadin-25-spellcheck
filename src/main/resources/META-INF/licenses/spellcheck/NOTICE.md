# Spell-checking third-party notices

This application bundles the following third-party components for its offline
spell checker. They are served to the browser from the application itself
(`VAADIN/build/*.aff`, `*.dic` and the `spell-check.worker-*.js` script). The
full license texts are in this folder.

## nspell 2.1.5

Hunspell-compatible spell checker, bundled into the spell-check Web Worker.

- Copyright (c) 2016 Titus Wormer
- License: MIT, see `nspell-LICENSE`
- Source: https://github.com/wooorm/nspell

## Norwegian Bokmål (nb-NO)

`nb-NO.dic` and `nb-NO.aff` are LibreOffice dict-no version 3.0
(https://github.com/LibreOffice/dictionaries/tree/32b006a2c22a4ac7e8ed3f03346f7b3d85a970a4/no).

- Word list based on Ordbanken (Nasjonalbiblioteket), CC BY 4.0,
  https://creativecommons.org/licenses/by/4.0/
- New headwords from the 2018–2024 revision of Bokmålsordboka, CLARINO
  Bergen / Språkbanken, CLARIN PUB +BY
- Words of the year from Språkrådet
- Affix file: The spell-norwegian project, GNU GPL version 2
- Merging and packaging: Lars Bungum

See `nb-NO-README_NO.txt` for the complete attribution and `nb-NO-COPYING` for
the GNU General Public License version 2.

`nb-NO-compound-roots.dic` contains the dictionary roots that carry the
compound flag, extracted unmodified from spell-norwegian 2.2 (via the npm
package dictionary-nb 3.0.0, https://github.com/wooorm/dictionaries).
Copyright the spell-norwegian project, GNU GPL version 2 (`nb-NO-COPYING`).

## English (en-US, en-GB)

`en-US.*` and `en-GB.*` are the SCOWL-based Hunspell dictionaries (version
2020.12.07, en_US and en_GB-ise), via the npm packages dictionary-en 4.0.0 and
dictionary-en-gb 3.0.0 (https://github.com/wooorm/dictionaries).

- Copyright 2000-2018 Kevin Atkinson and the other copyright holders listed
  in the license files (MIT-like and BSD-style terms, public-domain parts,
  WordNet license)
- See `en-US-LICENSE` and `en-GB-LICENSE`
