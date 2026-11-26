import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const PROFILE = __ENV.PROFILE || 'baseline';

const profiles = {
  baseline: {
    vus: 5,
    duration: '20s',
  },
  burst: {
    stages: [
      { duration: '10s', target: 25 },
      { duration: '15s', target: 25 },
      { duration: '5s', target: 0 },
    ],
  },
  concurrent_idempotency: {
    vus: 10,
    iterations: 30,
  },
};

export const options = {
  ...(profiles[PROFILE] || profiles.baseline),
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<500'],
  },
};

export function setup() {
  if (PROFILE === 'concurrent_idempotency') {
    http.setResponseCallback(http.expectedStatuses(200, 201, 409));
  }
  const loginRes = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({
      email: 'acme.merchant@payshield.local',
      password: 'PayShield@Demo2026!',
    }),
    { headers: { 'Content-Type': 'application/json' } }
  );
  check(loginRes, { 'login 200': (r) => r.status === 200 });
  return { token: loginRes.json('accessToken') };
}

export default function (data) {
  if (PROFILE === 'concurrent_idempotency') {
    http.setResponseCallback(http.expectedStatuses(200, 201, 409));
  }
  const idemKey =
    PROFILE === 'concurrent_idempotency'
      ? 'k6-shared-idempotency-key-001'
      : `k6-${__VU}-${__ITER}-${Date.now()}`;

  const payload = JSON.stringify({
    merchantId: '22222222-2222-2222-2222-222222222201',
    merchantOrderRef: `K6-ORD-${__VU}-${__ITER}`,
    amountMinor: 25000,
    currency: 'INR',
    preferredProvider: 'PROVIDER_ALPHA',
  });

  const res = http.post(`${BASE_URL}/api/v1/payments`, payload, {
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${data.token}`,
      'Idempotency-Key': idemKey,
    },
  });

  check(res, {
    'payment created or idempotent conflict': (r) => r.status === 201 || r.status === 409,
  });

  sleep(0.2);
}
