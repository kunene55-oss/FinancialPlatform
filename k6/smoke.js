// Smoke test: minimal load (1 VU, a handful of iterations), meant to catch
// "is this environment obviously broken" before running load.js or
// spike.js against it. Only ever talks to api-gateway - ingestion/
// processing/aggregation-service are ClusterIP-only in every real
// deployment (see helm/README.md), so their own /actuator/health isn't
// reachable except locally via docker-compose's host port mappings, which
// this suite deliberately doesn't depend on.
import { check, sleep } from 'k6';
import { getToken } from './lib/auth.js';
import { health, createAccount, ingestEvent, aggregationSummary } from './lib/api.js';
import { config } from './lib/config.js';
import { uuidv4 } from './lib/uuid.js';

let fixtureAccounts = [];
try {
  fixtureAccounts = JSON.parse(open('./fixtures.json'));
} catch (e) {
  console.warn('fixtures.json not found - run seed.sh first for full coverage; skipping ingest/aggregation checks this run.');
}

export const options = {
  vus: 1,
  iterations: 5,
  thresholds: {
    // A smoke test gates on correctness, not speed - cold-start effects
    // (first token fetch, first connection to each service) make a tight
    // latency threshold flaky here. load.js and spike.js own the actual
    // performance thresholds.
    http_req_failed: ['rate==0'],
    checks: ['rate==1'],
  },
};

export default function () {
  check(health(config.BASE_URL), {
    'gateway health is UP': (r) => r.status === 200 && r.json('status') === 'UP',
  });

  const adminToken = getToken(config.ADMIN_CLIENT_ID, config.ADMIN_CLIENT_SECRET);
  check(adminToken, { 'admin token acquired': (t) => t !== null });

  // Exercises the create endpoint itself. Tagged K6/Fixture so cleanup.sh
  // catches it - we never learn its accountId (see seed.sh's comment on
  // why), so it can't be deleted individually within this script.
  const idNumber = Date.now() * 1000 + Math.floor(Math.random() * 1000);
  const createRes = createAccount(adminToken, 'K6', 'Fixture', idNumber);
  check(createRes, { 'create account: 200': (r) => r.status === 200 });

  if (fixtureAccounts.length > 0) {
    const accountId = fixtureAccounts[0];
    const ingestToken = getToken(config.INGEST_CLIENT_ID, config.INGEST_CLIENT_SECRET);
    check(ingestToken, { 'ingest token acquired': (t) => t !== null });

    const event = {
      transactionId: uuidv4(),
      accountId,
      amount: 10.5,
      type: 'DEPOSIT',
      timestamp: new Date().toISOString(),
    };
    const ingestRes = ingestEvent(ingestToken, event);
    check(ingestRes, { 'ingest event: 202': (r) => r.status === 202 });

    const summaryRes = aggregationSummary(adminToken, accountId);
    check(summaryRes, { 'aggregation summary: 200': (r) => r.status === 200 });
  }

  sleep(1);
}
