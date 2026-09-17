import assert from 'node:assert/strict';
import test from 'node:test';
import { parseInventory, scan } from './osv-scan.mjs';

const packages = parseInventory('org.example:one:1.0\norg.example:two:2.0\n');

test('inventory accepts CRLF, deduplicates, and rejects empty/malformed input', () => {
  assert.deepEqual(parseInventory(' a:b:1\r\na:b:1\r\n'), [{ name: 'a:b', version: '1' }]);
  for (const invalid of ['', 'a:b', 'a::1', 'a:b:1:extra', 'a:b:bad version']) {
    assert.throws(() => parseInventory(invalid));
  }
});

test('queries Maven coordinates and follows only paginated results', async () => {
  let calls = 0;
  const findings = await scan(packages, async queries => {
    calls++;
    if (calls === 1) {
      assert.deepEqual(queries[0], { package: { ecosystem: 'Maven', name: 'org.example:one' }, version: '1.0' });
      return { results: [{ vulns: [{ id: 'TEST-1' }], next_page_token: 'page2' }, {}] };
    }
    assert.equal(queries.length, 1);
    assert.equal(queries[0].page_token, 'page2');
    return { results: [{ vulns: [{ id: 'TEST-1' }, { id: 'TEST-2' }] }] };
  });
  assert.equal(calls, 2);
  assert.deepEqual(findings[0].vulnerabilities, ['TEST-1', 'TEST-2']);
  assert.deepEqual(findings[1].vulnerabilities, []);
});

test('splits large inventories into bounded batches', async () => {
  const sizes = [];
  await scan(Array(201).fill(packages[0]), async queries => {
    sizes.push(queries.length);
    return { results: queries.map(() => ({})) };
  });
  assert.deepEqual(sizes, [100, 100, 1]);
});

test('fails closed on incomplete, malformed, repeated-page, and unavailable responses', async () => {
  for (const response of [{}, { results: [] }, { results: [null, {}] },
    { results: [{ vulns: 'invalid' }, {}] }, { results: [{ vulns: [{}] }, {}] }]) {
    await assert.rejects(scan(packages, async () => response));
  }
  await assert.rejects(scan([packages[0]], async () => ({ results: [{ next_page_token: 'same' }] })));
  await assert.rejects(scan(packages, async () => { throw new Error('unavailable'); }));
});
