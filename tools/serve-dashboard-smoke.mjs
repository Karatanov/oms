// Local-only UI regression fixture. No database, credentials or production API.
// Build :composeApp:jsBrowserProductionWebpack first, then run this script.
// Verify four dashboard charts, language/orientation changes and sidebar navigation.
import { createServer } from 'node:http';
import { readFile, stat } from 'node:fs/promises';
import { dirname, resolve, sep, extname } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const assets = [
  resolve(root, 'composeApp/build/kotlin-webpack/js/productionExecutable'),
  resolve(root, 'composeApp/build/processedResources/js/main'),
];
const overview = {
  recentInspections: [],
  subprojectFunding: [
    { projectUuid: 'smoke-kyiv', name: 'Test school', region: 'Київська область', amountUah: 4500000, amountEur: 100000 },
    { projectUuid: 'smoke-lviv', name: 'Test hospital', region: 'Львівська область', amountUah: 9000000, amountEur: 200000 },
  ],
  subprojectProgress: [
    { projectUuid: 'smoke-kyiv', code: 'TEST-01', name: 'Тестова школа', nameEn: 'Test school', region: 'Київська область', completionPct: 35 },
    { projectUuid: 'smoke-lviv', code: 'TEST-02', name: 'Тестова лікарня', nameEn: 'Test hospital', region: 'Львівська область', completionPct: 60 },
  ],
  procurementStatusCounts: [{ label: 'Договір укладено / Contract signed', value: 2 }, { label: 'Не розпочато / Not Started', value: 0 }],
  monthlyActPayments: [{ month: '2026-08', amountEurCents: 1500000 }, { month: '2026-09', amountEurCents: 2500000 }],
};
const projects = [
  { uuid: 'smoke-root', projectType: 'project', name: 'Smoke parent', siteNumber: 'URP-III', region: 'Київська область', city: 'Київ', sector: 'education', budgetPlanned: 0, status: 'active', latitude: 50.45, longitude: 30.52 },
  { uuid: 'smoke-kyiv', projectType: 'subproject', parentProjectUuid: 'smoke-root', name: 'Test school', siteNumber: 'TEST-01', region: 'Київська область', city: 'Київ', sector: 'education', budgetPlanned: 4500000, status: 'active', latitude: 50.45, longitude: 30.52 },
];
const financialRecords = Array.from({ length: 36 }, (_, index) => ({
  projectUuid: 'smoke-kyiv',
  record: {
    uuid: `smoke-payment-${index}`, recordType: 'payment', referenceNumber: `PAY-${String(index + 1).padStart(3, '0')}`,
    amount: 100000 + index, currency: 'EUR', recordDate: '2026-09-01', paymentDate: '2026-09-05',
    description: 'Smoke payment', paymentPurpose: 'works',
  },
}));
// Wide procurement rows exercise every header after the tender-ID column.
const procurements = [
  {
    id: 1, recordNumber: 1, batchId: 8, oblastName: 'Київська область', oblastId: 'UA-32',
    subProjectId: 'TEST-01', subProjectLotId: 'LOT-01', promotorName: 'Kyiv municipality',
    subprojectNameUk: 'Тестова школа', subprojectNameEn: 'Test school', spId: 'SP-01',
    sourceContractType: 'Works', sourceType: 'Open tender', procurementId: 'UA-2026-000001',
    subprojectTotalCostUah: 4500000, subprojectEibFinancingUah: 4000000, subprojectLocalFinancingUah: 500000,
    estimatedTotalEur: 100000, procurementMethod: 'Open procedure', tenderDocumentType: 'Standard',
    publishedInOjeu: 'No', estimatedProzorroDate: '2026-08-01', estimatedBidSubmissionDate: '2026-09-01',
    estimatedContractDate: '2026-10-01', estimatedContractEndDate: '2027-06-01',
    purchaseStatus: 'Договір укладено / Contract signed', localFinancingPct: 11.11, comments: 'Smoke fixture',
  },
  {
    id: 2, recordNumber: 2, batchId: 9, oblastName: 'Львівська область', oblastId: 'UA-46',
    subProjectId: 'TEST-02', subProjectLotId: 'LOT-02', promotorName: 'Lviv municipality',
    subprojectNameUk: 'Тестова лікарня', subprojectNameEn: 'Test hospital', spId: 'SP-02',
    sourceContractType: 'Works', sourceType: 'Open tender', procurementId: 'UA-2026-000002',
    subprojectTotalCostUah: 9000000, subprojectEibFinancingUah: 8000000, subprojectLocalFinancingUah: 1000000,
    estimatedTotalEur: 200000, procurementMethod: 'Open procedure', tenderDocumentType: 'Standard',
    publishedInOjeu: 'No', estimatedProzorroDate: '2026-08-02', estimatedBidSubmissionDate: '2026-09-02',
    estimatedContractDate: '2026-10-02', estimatedContractEndDate: '2027-07-01',
    purchaseStatus: 'Не розпочато / Not Started', localFinancingPct: 11.11, comments: 'Smoke fixture',
  },
];
const fixtures = {
  '/api/v1/auth/session': { id: 1, username: 'local-ui-test', email: 'test@example.invalid', role: { id: 1, code: 'ADMIN', name: 'Admin' } },
  '/api/v1/dashboard/overview': overview,
  '/api/v1/projects': { data: projects },
  '/api/v1/inspection-reports': [],
  '/api/v1/inspection-reports/analytics': { monthlyInspectionCounts: [], monthlyEshsViolations: [] },
  '/api/v1/procurements': procurements,
  '/api/v1/financials': financialRecords,
  '/api/v1/documents': [],
  '/api/v1/users': [],
};
const mime = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.mjs': 'text/javascript', '.css': 'text/css', '.wasm': 'application/wasm', '.json': 'application/json', '.png': 'image/png', '.svg': 'image/svg+xml', '.ttf': 'font/ttf' };
await stat(resolve(assets[0], 'composeApp.js'));
createServer(async (request, response) => {
  response.setHeader('Cache-Control', 'no-store');
  if (request.method !== 'GET') { response.writeHead(405).end('Read-only fixture'); return; }
  const path = new URL(request.url, 'http://127.0.0.1').pathname;
  if (Object.hasOwn(fixtures, path)) {
    response.setHeader('Content-Type', 'application/json');
    response.end(JSON.stringify(fixtures[path]));
    return;
  }
  if (path.startsWith('/api/')) { response.writeHead(404).end('No fixture: ' + path); return; }
  for (const directory of assets) {
    const file = resolve(directory, '.' + decodeURIComponent(path === '/' ? '/index.html' : path));
    if (!file.startsWith(directory + sep)) continue;
    try {
      const data = await readFile(file);
      response.setHeader('Content-Type', mime[extname(file)] || 'application/octet-stream');
      response.end(data);
      return;
    } catch { /* Try the processed resources directory. */ }
  }
  response.writeHead(404).end('Not found');
}).listen(18088, '127.0.0.1', () => console.log('OMS read-only dashboard fixture: http://127.0.0.1:18088/'));
