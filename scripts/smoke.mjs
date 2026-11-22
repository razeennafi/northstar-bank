/*
 * Portfolio author: Razeen Abdun Nafi
 * Date: 2026-11-22
 * File: smoke.mjs
 * Description: Checks frontend assets, backend readiness, and OpenAPI through the proxy.
 * Project version: 0.1.0
 */

import assert from 'node:assert/strict'

const base = new URL(process.argv[2] ?? 'http://localhost:3000')

// Bounds each smoke request so an unavailable Compose stack fails promptly.
async function request(path) {
  return fetch(new URL(path, base), { signal: AbortSignal.timeout(8000) })
}

// Checks that the server delivers the HTML entry point and its compiled JavaScript.
const page = await request('/')
assert.equal(page.status, 200, 'The frontend serves its HTML entry point')
const html = await page.text()
assert.match(html, /<title>[^<]*Northstar Bank<\/title>/)
const bundle = html.match(/<script\b[^>]*\bsrc="([^"]+)"/)
assert.ok(bundle, 'The frontend entry point references a JavaScript bundle')
const javascript = await request(bundle[1])
assert.equal(javascript.status, 200, 'The frontend serves its JavaScript bundle')
assert.match(javascript.headers.get('content-type') ?? '', /javascript/)

// Actuator includes db in readiness, so this checks both proxy routing and
// the service's ability to obtain a real JDBC connection.
const readiness = await request('/actuator/health/readiness')
assert.equal(readiness.status, 200, 'The proxy reaches the Spring readiness probe')
assert.deepEqual(await readiness.json(), { status: 'UP' })

// Checks the proxy boundary before verifying the public API contract.
const blocked = await request('/actuator/env')
assert.equal(blocked.status, 404, 'Other actuator paths are hidden from the browser')

const documentation = await request('/api/v1/openapi')
assert.equal(documentation.status, 200, 'The OpenAPI contract is available through the same origin')
const openApi = await documentation.json()
assert.match(openApi.openapi, /^3\./)
assert.ok(openApi.paths['/api/v1/transfers']?.post)

console.log(`Northstar smoke check passed at ${base.origin}`)
