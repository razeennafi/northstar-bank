/*
 * Portfolio author: Razeen Abdun Nafi
 * Date: 2026-11-22
 * File: playwright.config.ts
 * Description: Configures Chromium and local end-to-end test settings.
 * Project version: 0.1.0
 */

import { defineConfig, devices } from '@playwright/test'

// Runs Chromium against the configured stack and retains traces for failures.
export default defineConfig({
  testDir: './e2e',
  workers: 1,
  reporter: 'list',
  use: {
    baseURL: process.env.NORTHSTAR_BASE_URL ?? 'http://localhost:3000',
    trace: 'retain-on-failure',
  },
  projects: [
    { name: 'Chromium', use: { ...devices['Desktop Chrome'] } },
  ],
})
