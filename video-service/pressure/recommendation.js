import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  scenarios: {
    recommendation_load: {
      executor: 'ramping-vus',
      startVUs: 1,
      stages: [
        { duration: '30s', target: 10 },
        { duration: '60s', target: 30 },
        { duration: '60s', target: 50 },
        { duration: '30s', target: 0 },
      ],
      gracefulRampDown: '10s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1000'],
    checks: ['rate>0.99'],
  },
};

const baseUrl = __ENV.BASE_URL || 'http://127.0.0.1:8082';

export default function () {
  const response = http.get(
    `${baseUrl}/api/videos/recommend?page=1&pageSize=12`,
    {
      headers: { 'X-User-Id': '42' },
      tags: { endpoint: 'recommend' },
    },
  );

  check(response, {
    'HTTP 200': (r) => r.status === 200,
    'business code 200': (r) => r.json('code') === 200,
    'data is array': (r) => Array.isArray(r.json('data')),
  });
  sleep(1);
}



