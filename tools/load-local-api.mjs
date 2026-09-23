// Read-only local API load probe. Never defaults to the hosted production API.
// OMS_LOAD_COOKIE supplies an authenticated test session without logging it.
// node tools/load-local-api.mjs http://127.0.0.1:8080 /health 100 4
import { performance } from 'node:perf_hooks';

const [base = 'http://127.0.0.1:8080', path = '/health', countArg = '100', concurrencyArg = '4'] = process.argv.slice(2);
const url = new URL(path, base);
if (!['localhost', '127.0.0.1', '[::1]'].includes(url.hostname)) throw Error('Only localhost test instances are supported.');
const count = Number(countArg), concurrency = Number(concurrencyArg);
if (!Number.isInteger(count) || count < 1 || count > 10000 || !Number.isInteger(concurrency) || concurrency < 1 || concurrency > 64) throw Error('Use 1–10000 requests and concurrency 1–64.');
const samples = [], statuses = {};
let next = 0, failures = 0, bytes = 0;
const started = performance.now();
await Promise.all(Array.from({ length: concurrency }, async () => {
  while (next++ < count) {
    const start = performance.now();
    try {
      const response = await fetch(url, {
        redirect: 'manual', signal: AbortSignal.timeout(15000),
        headers: process.env.OMS_LOAD_COOKIE ? { Cookie: process.env.OMS_LOAD_COOKIE } : {},
      });
      bytes += (await response.arrayBuffer()).byteLength;
      statuses[response.status] = (statuses[response.status] || 0) + 1;
      if (!response.ok) failures++;
    } catch { failures++; statuses.transport_error = (statuses.transport_error || 0) + 1; }
    samples.push(performance.now() - start);
  }
}));
const elapsed = performance.now() - started;
samples.sort((a,b) => a-b);
const percentile = p => samples[Math.ceil(samples.length * p) - 1];
console.log(JSON.stringify({ requests: samples.length, concurrency, failures, statuses, bytes,
  mean_ms: samples.reduce((a,b) => a+b, 0) / samples.length,
  p50_ms: percentile(.5), p95_ms: percentile(.95), p99_ms: percentile(.99),
  throughput_per_second: samples.length * 1000 / elapsed,
}, null, 2));
if (failures) process.exitCode = 1;
