// Test-only Node module hook: resolves extensionless relative imports between
// the frontend TypeScript modules (`import ... from './spell-text'`), which
// Vite resolves at build time but Node does not. Used via
// `node --import ./src/test/frontend/resolve-ts.mjs --test ...`.
import { registerHooks } from 'node:module';

registerHooks({
  resolve(specifier, context, nextResolve) {
    if (specifier.startsWith('.') && !/\.[cm]?[jt]s$/.test(specifier)) {
      return nextResolve(`${specifier}.ts`, context);
    }
    return nextResolve(specifier, context);
  },
});
