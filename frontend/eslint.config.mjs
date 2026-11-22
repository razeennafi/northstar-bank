/*
 * Portfolio author: Razeen Abdun Nafi
 * Date: 2026-11-22
 * File: eslint.config.mjs
 * Description: Applies TypeScript lint rules to source and test files.
 * Project version: 0.1.0
 */

import { defineConfig } from 'eslint/config'
import tseslint from 'typescript-eslint'

// Applies TypeScript rules to authored files and excludes generated output.
export default defineConfig(
  { ignores: ['dist', 'playwright-report', 'test-results'] },
  {
    files: ['**/*.ts', '**/*.tsx'],
    extends: [tseslint.configs.recommended],
  },
)
