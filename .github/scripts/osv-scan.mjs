import { readFile, writeFile } from 'node:fs/promises';
import { pathToFileURL } from 'node:url';

export function parseInventory(text) {
  const coordinates = [...new Set(text.split(/\r?\n/).map(line => line.trim()).filter(Boolean))].sort();
  if (!coordinates.length) throw new Error('Dependency inventory is empty');
  return coordinates.map(coordinate => {
    const parts = coordinate.split(':');
    if (parts.length !== 3 || parts.some(part => !part || /\s/.test(part))) {
      throw new Error(`Invalid Maven coordinate: ${coordinate}`);
    }
    return { name: `${parts[0]}:${parts[1]}`, version: parts[2] };
  });
}

async function queryBatch(queries) {
  let lastError;
  for (let attempt = 0; attempt < 3; attempt++) {
    try {
      const response = await fetch('https://api.osv.dev/v1/querybatch', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ queries }),
        signal: AbortSignal.timeout(30000),
      });
      if (!response.ok) throw new Error(`OSV returned HTTP ${response.status}`);
      return await response.json();
    } catch (error) {
      lastError = error;
      if (attempt < 2) await new Promise(resolve => setTimeout(resolve, 1000 * (attempt + 1)));
    }
  }
  throw lastError;
}

export async function scan(packages, query = queryBatch) {
  const findings = packages.map(pkg => ({ ...pkg, vulnerabilities: [] }));
  for (let start = 0; start < packages.length; start += 100) {
    let pending = packages.slice(start, start + 100).map((pkg, offset) => ({
      index: start + offset,
      query: { package: { ecosystem: 'Maven', name: pkg.name }, version: pkg.version },
      tokens: new Set(),
    }));
    while (pending.length) {
      const response = await query(pending.map(item => item.query));
      if (!Array.isArray(response.results) || response.results.length !== pending.length) {
        throw new Error('OSV returned an incomplete batch');
      }
      const next = [];
      response.results.forEach((result, index) => {
        if (!result || typeof result !== 'object' || Array.isArray(result) ||
            (result.vulns !== undefined && !Array.isArray(result.vulns))) {
          throw new Error('OSV returned an invalid result');
        }
        const item = pending[index];
        for (const vuln of result.vulns ?? []) {
          if (typeof vuln.id !== 'string' || !vuln.id) throw new Error('OSV returned an invalid advisory');
          findings[item.index].vulnerabilities.push(vuln.id);
        }
        if (result.next_page_token) {
          const token = result.next_page_token;
          if (typeof token !== 'string' || item.tokens.has(token)) {
            throw new Error('OSV returned an invalid/repeated pagination token');
          }
          item.tokens.add(token);
          next.push({ ...item, query: { ...item.query, page_token: token } });
        }
      });
      pending = next;
    }
  }
  return findings.map(item => ({ ...item, vulnerabilities: [...new Set(item.vulnerabilities)].sort() }));
}

async function main() {
  const [inventory, report] = process.argv.slice(2);
  if (!inventory || !report) throw new Error('Usage: osv-scan.mjs INVENTORY REPORT');
  try {
    const packages = parseInventory(await readFile(inventory, 'utf8'));
    const findings = await scan(packages);
    await writeFile(report, JSON.stringify({ status: 'complete', findings }, null, 2) + '\n');
    const vulnerable = findings.filter(item => item.vulnerabilities.length);
    for (const item of vulnerable) {
      console.log(`${item.name}:${item.version}: ${item.vulnerabilities.join(', ')}`);
    }
    console.log(`OSV checked ${packages.length} resolved Maven versions; ${vulnerable.length} have known advisories.`);
    // Gate on all returned advisories, not a guessed severity from the batch API.
    if (vulnerable.length) process.exitCode = 1;
  } catch (error) {
    await writeFile(report, JSON.stringify({ status: 'incomplete', error: error.message }, null, 2) + '\n');
    throw error;
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch(error => {
    console.error(`Dependency scan failed: ${error.message}`);
    process.exitCode = 1;
  });
}
