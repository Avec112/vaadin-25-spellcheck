// Minimal typings for nspell 2.x (the package ships without TypeScript types)
// and for the Vite asset imports used by the spell checker.
declare module 'nspell' {
  interface NSpell {
    correct(word: string): boolean;
    suggest(word: string): string[];
  }

  function nspell(aff: string, dic?: string): NSpell;

  export default nspell;
}

// Vite `?url` imports: the file is emitted as a hashed asset of the frontend
// bundle and the import resolves to its URL (served by the application itself).
declare module '*?url' {
  const url: string;
  export default url;
}
