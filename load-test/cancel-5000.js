import http from 'k6/http';
import { Counter } from 'k6/metrics';

const RESERVATION_ID = __ENV.RESERVATION_ID;
const USERNAME = __ENV.USERNAME;
const PASSWORD = __ENV.PASSWORD || 'LoadTest@123';
const TOTAL_REQUESTS = Number(__ENV.TOTAL_REQUESTS || 5000);
const VUS = Number(__ENV.VUS || 100);
const BASE_URLS = ['http://localhost:8080', 'http://localhost:8081'];

const success200 = new Counter('cancellation_200');
const unauthorized401 = new Counter('cancellation_401');
const forbidden403 = new Counter('cancellation_403');
const notFound404 = new Counter('cancellation_404');
const conflict409 = new Counter('cancellation_409');
const server5xx = new Counter('cancellation_5xx');
const networkErrors = new Counter('cancellation_network_error');
const unexpected = new Counter('cancellation_unexpected');

http.setResponseCallback(http.expectedStatuses(200));

export const options = {
    discardResponseBodies: true,
    scenarios: { cancellation_load: { executor: 'shared-iterations', vus: VUS, iterations: TOTAL_REQUESTS, maxDuration: '30m' } },
    thresholds: {
        cancellation_401: ['count==0'],
        cancellation_403: ['count==0'],
        cancellation_404: ['count==0'],
        cancellation_409: ['count==0'],
        cancellation_5xx: ['count==0'],
        cancellation_network_error: ['count==0'],
        cancellation_unexpected: ['count==0']
    }
};

export function setup() {
    if (!RESERVATION_ID) throw new Error('RESERVATION_ID is required');
    if (!USERNAME) throw new Error('USERNAME is required');

    for (const baseUrl of BASE_URLS) {
        const health = http.get(`${baseUrl}/actuator/health`, { timeout: '10s' });
        if (health.status !== 200) throw new Error(`Application unavailable: ${baseUrl}`);
    }

    const login = http.post(`${BASE_URLS[0]}/auth/login`, JSON.stringify({ username: USERNAME, password: PASSWORD }), {
        headers: { 'Content-Type': 'application/json' },
        responseType: 'text',
        timeout: '30s'
    });

    if (login.status !== 200) throw new Error(`Login failed status=${login.status} body=${login.body}`);

    const token = login.json('access_token');
    if (!token) throw new Error('access_token missing');

    console.log(`Cancellation load setup complete reservationId=${RESERVATION_ID} requests=${TOTAL_REQUESTS} vus=${VUS}`);
    return { token };
}

export default function(data) {
    const baseUrl = BASE_URLS[(__VU + __ITER) % BASE_URLS.length];

    const response = http.post(`${baseUrl}/reservations/${RESERVATION_ID}/cancel`, null, {
        headers: { 'Authorization': `Bearer ${data.token}` },
        tags: { name: 'cancel-reservation' },
        timeout: '60s'
    });

    if (response.status === 200) success200.add(1);
    else if (response.status === 401) unauthorized401.add(1);
    else if (response.status === 403) forbidden403.add(1);
    else if (response.status === 404) notFound404.add(1);
    else if (response.status === 409) conflict409.add(1);
    else if (response.status >= 500 && response.status <= 599) server5xx.add(1);
    else if (response.status === 0) networkErrors.add(1);
    else unexpected.add(1);
}