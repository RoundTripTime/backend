import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

const acquireTimeouts = new Counter('acquire_timeouts');
const providerOverCapacity = new Counter('provider_over_capacity');

export const options = {
  vus: Number(__ENV.VUS || 1),
  duration: __ENV.DURATION || '20s',
  summaryTrendStats: ['min', 'avg', 'med', 'p(50)', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

const BASE = __ENV.BASE_URL || 'http://127.0.0.1:18080';

export default function () {
  const res = http.post(`${BASE}/internal/ai-loadtest/call`);
  if (res.status === 503) {
    acquireTimeouts.add(1);
  } else if (res.status === 429) {
    providerOverCapacity.add(1);
  }
  check(res, {
    'harness accepted the call': (r) => r.status === 200 || r.status === 503 || r.status === 429,
  });
}

export function handleSummary(data) {
  const path = __ENV.SUMMARY_PATH;
  if (!path) {
    return {};
  }
  return { [path]: JSON.stringify(data) };
}
