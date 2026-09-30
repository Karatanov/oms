import { spawn } from "node:child_process";
import { mkdtemp, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";

const chrome = process.env.CHROME_PATH || "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe";
const origin = (process.argv[2] || "https://ua-oms.com").replace(/\/$/, "");
const timeoutMs = Number(process.env.STARTUP_TIMEOUT_MS || 120_000);
const port = 9333;
const profile = await mkdtemp(join(tmpdir(), "oms-startup-"));
const browser = spawn(chrome, [
  "--headless=new", `--remote-debugging-port=${port}`, `--user-data-dir=${profile}`,
  "--no-first-run", "--disable-background-networking", "--disable-component-update",
  "--disable-default-apps", "--disable-extensions", "--disable-sync", "about:blank"
], { stdio: "ignore" });

const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
async function retry(callback, deadline = Date.now() + 15_000) {
  while (Date.now() < deadline) {
    try { return await callback(); } catch { await delay(100); }
  }
  throw new Error("Chrome DevTools endpoint did not become ready");
}

let socket;
let nextId = 1;
const pending = new Map();
const events = [];
function send(method, params = {}) {
  const id = nextId++;
  socket.send(JSON.stringify({ id, method, params }));
  return new Promise((resolve, reject) => pending.set(id, { resolve, reject }));
}
function waitFor(method, predicate = () => true, timeout = timeoutMs) {
  return new Promise((resolve, reject) => {
    const started = Date.now();
    const timer = setInterval(() => {
      const event = events.find(item => item.method === method && predicate(item.params));
      if (event) { clearInterval(timer); resolve(event.params); }
      else if (Date.now() - started > timeout) { clearInterval(timer); reject(new Error(`Timed out waiting for ${method}`)); }
    }, 50);
  });
}

try {
  await retry(() => fetch(`http://127.0.0.1:${port}/json/version`).then(r => {
    if (!r.ok) throw new Error(); return r.json();
  }));
  const target = await fetch(`http://127.0.0.1:${port}/json/new?${encodeURIComponent(origin + "/")}`, { method: "PUT" }).then(r => r.json());
  socket = new WebSocket(target.webSocketDebuggerUrl);
  await new Promise((resolve, reject) => { socket.onopen = resolve; socket.onerror = reject; });
  socket.onmessage = message => {
    const value = JSON.parse(message.data);
    if (value.id) {
      const handler = pending.get(value.id); pending.delete(value.id);
      if (value.error) handler?.reject(new Error(value.error.message)); else handler?.resolve(value.result);
    } else if (value.method) events.push(value);
  };
  await Promise.all([send("Page.enable"), send("Network.enable"), send("Runtime.enable"), send("Performance.enable")]);
  await send("Network.setCacheDisabled", { cacheDisabled: true });
  await send("Network.clearBrowserCache");
  await send("Page.navigate", { url: origin + "/" });
  await waitFor("Page.loadEventFired");
  events.length = 0;
  const wallStart = Date.now();
  await send("Runtime.evaluate", {
    expression: `fetch('/api/v1/auth/guest',{method:'POST',credentials:'include'}).then(r=>{if(!r.ok)throw Error(r.status);location.href='/app.html'})`,
    awaitPromise: false
  });

  let canvasAt = null;
  let overlayHiddenAt = null;
  let shellVisibleAt = null;
  let shellUsefulAt = null;
  let shellToUsefulMs = null;
  while (Date.now() - wallStart < timeoutMs) {
    await delay(250);
    try {
      const state = await send("Runtime.evaluate", {
        expression: `JSON.stringify((()=>{const visible=performance.getEntriesByName('oms-shell-visible')[0];const useful=performance.getEntriesByName('oms-shell-useful')[0];const measure=performance.getEntriesByName('oms-shell-to-useful')[0];return {canvas:!!document.querySelector('canvas'),overlayHidden:!document.querySelector('#oms-loading-overlay.visible'),visibleEpoch:visible?performance.timeOrigin+visible.startTime:null,usefulEpoch:useful?performance.timeOrigin+useful.startTime:null,shellToUseful:measure?.duration??null}})())`,
        returnByValue: true
      });
      const parsed = JSON.parse(state.result.value || "{}");
      if (parsed.visibleEpoch != null && shellVisibleAt == null) shellVisibleAt = Math.round(parsed.visibleEpoch - wallStart);
      if (parsed.usefulEpoch != null && shellUsefulAt == null) shellUsefulAt = Math.round(parsed.usefulEpoch - wallStart);
      if (parsed.shellToUseful != null && shellToUsefulMs == null) shellToUsefulMs = Math.round(parsed.shellToUseful);
      if (parsed.canvas && canvasAt == null) canvasAt = Date.now() - wallStart;
      if (parsed.overlayHidden && canvasAt != null) { overlayHiddenAt = Date.now() - wallStart; break; }
      const sessionResponded = events.some(item => item.method === "Network.responseReceived" && /\/auth\/session/.test(item.params.response.url));
      if (sessionResponded && shellUsefulAt != null) { await delay(1_000); break; }
    } catch { /* Navigation replaces the execution context briefly. */ }
  }

  const requests = new Map();
  for (const event of events) {
    const p = event.params;
    if (event.method === "Network.requestWillBeSent") requests.set(p.requestId, { url: p.request.url, start: p.timestamp });
    if (event.method === "Network.responseReceived") Object.assign(requests.get(p.requestId) || {}, { status: p.response.status, protocol: p.response.protocol, encoded: p.response.encodedDataLength, response: p.timestamp });
    if (event.method === "Network.loadingFinished") Object.assign(requests.get(p.requestId) || {}, { end: p.timestamp, encoded: p.encodedDataLength });
  }
  const selected = [...requests.values()].filter(item => /app\.html|composeApp.*\.js|\.wasm|\/auth\/(guest|session)|\/dashboard\/overview/.test(item.url));
  const firstTimestamp = Math.min(...selected.map(item => item.start));
  const summary = selected.map(item => ({
    resource: item.url.replace(origin, ""), status: item.status, protocol: item.protocol,
    startMs: Math.round((item.start - firstTimestamp) * 1000),
    responseMs: item.response == null ? null : Math.round((item.response - firstTimestamp) * 1000),
    endMs: item.end == null ? null : Math.round((item.end - firstTimestamp) * 1000),
    encodedBytes: item.encoded
  }));
  console.log(JSON.stringify({ origin, coldCache: true, shellVisibleAtMs: shellVisibleAt, shellUsefulAtMs: shellUsefulAt, shellToUsefulMs, canvasAtMs: canvasAt, overlayHiddenAtMs: overlayHiddenAt, requests: summary }, null, 2));
} finally {
  try { socket?.close(); } catch {}
  browser.kill();
  await Promise.race([
    new Promise(resolve => browser.once("exit", resolve)),
    delay(3_000)
  ]);
  await retry(() => rm(profile, { recursive: true, force: true }), Date.now() + 5_000).catch(() => {});
}
