#!/usr/bin/env bash
# Re-creates the bundled spell-check dictionaries from pinned upstream sources.
#
# Development-time only (needs internet). The application itself never
# downloads anything: the files in this folder are committed and bundled into
# the production build by Vite.
#
#   ./update-dictionaries.sh            # rebuild into this folder, verify checksums
#   ./update-dictionaries.sh /tmp/out   # rebuild into another folder
#
# License texts are written to src/main/resources/META-INF/licenses/spellcheck
# (or <folder>/licenses), so that they are included in the application jar.
#
# To upgrade a dictionary, change the pinned version below, run the script,
# review the spelling tests (npm run test:frontend) and update SHA256SUMS
# with: (cd <folder> && sha256sum */*.aff */*.dic > SHA256SUMS)
set -euo pipefail

LIBREOFFICE_COMMIT=32b006a2c22a4ac7e8ed3f03346f7b3d85a970a4 # dict-no 3.0, 2026-08
DICTIONARY_NB=dictionary-nb@3.0.0       # spell-norwegian 2.2, source of compound roots
DICTIONARY_EN=dictionary-en@4.0.0       # SCOWL en_US 2020.12.07
DICTIONARY_EN_GB=dictionary-en-gb@3.0.0 # SCOWL en_GB-ise 2020.12.07

here="$(cd "$(dirname "$0")" && pwd)"
out="$(mkdir -p "${1:-$here}" && cd "${1:-$here}" && pwd)"
if [ "$out" = "$here" ]; then
  licenses="$here/../../../resources/META-INF/licenses/spellcheck"
else
  licenses="$out/licenses"
fi
mkdir -p "$licenses"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

lo="https://raw.githubusercontent.com/LibreOffice/dictionaries/$LIBREOFFICE_COMMIT/no"

mkdir -p "$out/nb-NO" "$out/en-US" "$out/en-GB"

echo "Norwegian Bokmål: LibreOffice dict-no ($LIBREOFFICE_COMMIT)"
curl -fsSL "$lo/nb_NO.aff" -o "$out/nb-NO/nb-NO.aff"
curl -fsSL "$lo/nb_NO.dic" -o "$out/nb-NO/nb-NO.dic"
curl -fsSL "$lo/COPYING" -o "$licenses/nb-NO-COPYING"
curl -fsSL "$lo/README_NO.txt" -o "$licenses/nb-NO-README_NO.txt"

unpack() { # <npm spec> <target dir>
  (cd "$work" && npm pack --silent "$1" >/dev/null)
  mkdir -p "$2"
  tar -xzf "$work"/"$(basename "${1%@*}")"-*.tgz -C "$2" --strip-components=1
  rm "$work"/*.tgz
}

# dict-no 3.0 is a full-form list without affix flags, so it cannot drive the
# affix file's COMPOUNDFLAG rule. The roots carrying the compound flag ("z")
# are taken from spell-norwegian 2.2, which uses the identical affix file.
echo "Norwegian compound roots: $DICTIONARY_NB"
unpack "$DICTIONARY_NB" "$work/nb"
awk -F/ 'NR > 1 && NF > 1 && $2 ~ /z/' "$work/nb/index.dic" > "$work/roots"
{ wc -l < "$work/roots" | tr -d ' '; cat "$work/roots"; } > "$out/nb-NO/nb-NO-compound-roots.dic"

echo "English (US): $DICTIONARY_EN"
unpack "$DICTIONARY_EN" "$work/en"
cp "$work/en/index.aff" "$out/en-US/en-US.aff"
cp "$work/en/index.dic" "$out/en-US/en-US.dic"
cp "$work/en/license" "$licenses/en-US-LICENSE"

echo "English (UK): $DICTIONARY_EN_GB"
unpack "$DICTIONARY_EN_GB" "$work/en-gb"
cp "$work/en-gb/index.aff" "$out/en-GB/en-GB.aff"
cp "$work/en-gb/index.dic" "$out/en-GB/en-GB.dic"
cp "$work/en-gb/license" "$licenses/en-GB-LICENSE"

echo "Verifying checksums"
(cd "$out" && sha256sum --check --quiet "$here/SHA256SUMS")
echo "Done: $out"
