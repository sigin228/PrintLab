'use strict';
// Run from any directory: node scripts/test-ios-uuid.cjs
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const webcrypto = require('node:crypto').webcrypto;
const source = fs.readFileSync(path.join(__dirname, '../src/main/resources/static/js/design-editor.js'), 'utf8');
const start = source.indexOf('  const uuid=()=>{');
const end = source.indexOf('\n  const imageFromUrl=', start);
if (start < 0 || end < 0) throw Error('UUID implementation not found');
const program = source.slice(start, end) + '\nresult=uuid();';
const valid = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;
for (const c of [
  { getRandomValues: webcrypto.getRandomValues.bind(webcrypto) },
  { getRandomValues: webcrypto.getRandomValues.bind(webcrypto), randomUUID: () => '12345678-1234-4234-8234-123456789abc' }
]) {
  const context = { window: { crypto: c }, Uint8Array, Array, result: null };
  vm.runInNewContext(program, context);
  if (!valid.test(context.result)) throw Error('Invalid UUID v4');
}
let rejected = false;
try { vm.runInNewContext(program, { window: { crypto: {} }, Uint8Array, Array }); }
catch (_) { rejected = true; }
if (!rejected) throw Error('Missing cryptography must fail closed');
process.stdout.write('UUID: secure context, HTTP LAN fallback and missing crypto passed.\n');
