// Load test: sustained, ramping traffic against the ingest -> Kafka ->
// processing -> aggregation pipeline, through api-gateway. Needs a pool
// of real accountIds - run seed.sh first.
//
// Tune via env vars, e.g.:
//   k6 run -e LOAD_PEAK_VUS=50 -e LOAD_RAMP=1m -e LOAD_HOLD=5m load.js
import { check, sleep } from 'k6';
import { getToken } from './lib/auth.js';
import { ingestEvent, aggregationSummary, aggregationTrend } from './lib/api.js';
import { config } from './lib/config.js';
import { uuidv4 } from './lib/uuid.js';

let fixtureAccounts;
try {
  fixtureAccounts = JSON.parse(open('./fixtures.json'));
} catch (e) {
  throw new Error('fixtures.json not found - run ./seed.sh before load.js');
}
if (!fixtureAccounts || fixtureAccounts.length === 0) {
  throw new Error('fixtures.json is empty - run ./seed.sh before load.js');
}

const peakVus = Number(__ENV.LOAD_PEAK_VUS || 20);
const rampTime = __ENV.LOAD_RAMP || '30s';
const holdTime = __ENV.LOAD_HOLD || '2m';

export const options = {
  stages: [
    { duration: rampTime, target: peakVus },
    { duration: holdTime, target: peakVus },
    { duration: rampTime, target: 0 },
  ],
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{name:IngestEvent}': ['p(95)<800'],
    'http_req_duration{name:AggregationSummary}': ['p(95)<800'],
    'http_req_duration{name:AggregationTrend}': ['p(95)<800'],
  },
};

const TRANSACTION_TYPES = ['DEPOSIT', 'WITHDRAWAL'];

export default function () {
  const ingestToken = getToken(config.INGEST_CLIENT_ID, config.INGEST_CLIENT_SECRET);
  const adminToken = getToken(config.ADMIN_CLIENT_ID, config.ADMIN_CLIENT_SECRET);
  const accountId = fixtureAccounts[Math.floor(Math.random() * fixtureAccounts.length)];

  const event = {
    transactionId: uuidv4(),
    accountId,
    amount: Math.round((Math.random() * 100 + 1) * 100) / 100,
    type: TRANSACTION_TYPES[Math.floor(Math.random() * TRANSACTION_TYPES.length)],
    timestamp: new Date().toISOString(),
  };
  check(ingestEvent(ingestToken, event), {
    'ingest event: 202': (r) => r.status === 202,
  });

  check(aggregationSummary(adminToken, accountId), {
    'aggregation summary: 200': (r) => r.status === 200,
  });

  check(aggregationTrend(adminToken, accountId, 'DAILY'), {
    'aggregation trend: 200': (r) => r.status === 200,
  });

  sleep(Math.random() * 1.5 + 0.5);
}
