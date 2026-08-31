import http from 'k6/http';
import { config } from './config.js';
import { authHeader } from './auth.js';

const jsonHeaders = (token) => ({
  headers: Object.assign({ 'Content-Type': 'application/json' }, authHeader(token)),
});

export function health(serviceUrl) {
  return http.get(`${serviceUrl}/actuator/health`, { tags: { name: 'Health' } });
}

export function createAccount(token, firstName, lastName, idNumber) {
  return http.post(
    `${config.BASE_URL}/accounts/createAccount`,
    JSON.stringify({ firstName, lastName, idNumber }),
    Object.assign({ tags: { name: 'CreateAccount' } }, jsonHeaders(token))
  );
}

export function deleteAccount(token, accountId) {
  return http.del(
    `${config.BASE_URL}/accounts/deleteAccount/${accountId}`,
    null,
    { headers: authHeader(token), tags: { name: 'DeleteAccount' } }
  );
}

export function ingestEvent(token, event) {
  return http.post(
    `${config.BASE_URL}/ingest/ingestEvent`,
    JSON.stringify(event),
    Object.assign({ tags: { name: 'IngestEvent' } }, jsonHeaders(token))
  );
}

export function aggregationSummary(token, accountId) {
  return http.get(
    `${config.BASE_URL}/aggregations/${accountId}/summary`,
    { headers: authHeader(token), tags: { name: 'AggregationSummary' } }
  );
}

export function aggregationTrend(token, accountId, interval) {
  return http.get(
    `${config.BASE_URL}/aggregations/${accountId}/trend?interval=${interval}`,
    { headers: authHeader(token), tags: { name: 'AggregationTrend' } }
  );
}
