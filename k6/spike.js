// Spike test: low baseline traffic, a sudden short burst to well above
// normal load, then back to baseline - checks the system survives a
// traffic spike and recovers, rather than requiring zero errors during
// the spike itself. Needs a pool of real accountIds - run seed.sh first.
//
// Tune via env vars, e.g.:
//   k6 run -e SPIKE_PEAK_VUS=200 -e SPIKE_BASELINE_VUS=5 spike.js
import { check, sleep } from 'k6';
import { getToken } from './lib/auth.js';
import { ingestEvent, aggregationSummary } from './lib/api.js';
import { config } from './lib/config.js';
import { uuidv4 } from './lib/uuid.js';

let fixtureAccounts;
try {
  fixtureAccounts = JSON.parse(open('./fixtures.json'));
} catch (e) {
  throw new Error('fixtures.json not found - run ./seed.sh before spike.js');
}
if (!fixtureAccounts || fixtureAccounts.length === 0) {
  throw new Error('fixtures.json is empty - run ./seed.sh before spike.js');
}

const baselineVus = Number(__ENV.SPIKE_BASELINE_VUS || 5);
const peakVus = Number(__ENV.SPIKE_PEAK_VUS || 100);

export const options = {
  stages: [
    { duration: '20s', target: baselineVus }, // establish baseline
    { duration: '10s', target: peakVus },      // the spike
    { duration: '30s', target: peakVus },      // hold at peak
    { duration: '10s', target: baselineVus },  // sudden drop back
    { duration: '30s', target: baselineVus },  // observe recovery
  ],
  thresholds: {
    // Deliberately lenient overall - a spike test is about observing
    // degradation and recovery, not enforcing zero failures during it.
    http_req_failed: ['rate<0.25'],
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

  sleep(Math.random() * 0.5);
}
