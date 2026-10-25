import http from 'k6/http';
import { Counter } from 'k6/metrics';

const SHOW_ID = __ENV.SHOW_ID;

const SEATS =
    (__ENV.SEATS || 'L1,L2,L3,L4,L5,L6,L7,L8')
        .split(',')
        .map(seat => seat.trim())
        .filter(Boolean);

const BASE_URLS = (
    __ENV.BASE_URLS ||
    __ENV.BASE_URL ||
    'http://localhost:8080,http://localhost:8081'
)
    .split(',')
    .map(url => url.trim())
    .filter(Boolean);

const success201 =
    new Counter('limit_201');

const conflict409 =
    new Counter('limit_409');

const server5xx =
    new Counter('limit_5xx');

const networkErrors =
    new Counter('limit_network_error');

const unexpected =
    new Counter('limit_unexpected');

http.setResponseCallback(
    http.expectedStatuses(200, 201, 409)
);

export const options = {

    setupTimeout: '20m',

    discardResponseBodies: true,

    scenarios: {

        per_user_limit: {

            executor: 'per-vu-iterations',

            vus: 8,

            iterations: 1,

            maxDuration: '5m'
        }
    },

    thresholds: {

        limit_201: ['count==4'],

        limit_409: ['count==4'],

        limit_5xx: ['count==0'],

        limit_network_error: ['count==0'],

        limit_unexpected: ['count==0']
    }
};

export function setup() {

    if (!SHOW_ID) {
        throw new Error('SHOW_ID is required');
    }

    const health = http.get(`${BASE_URLS[0]}/actuator/health/readiness`, { timeout: '240s' });

    if (health.status !== 200) {
        throw new Error(`Application unavailable: ${BASE_URLS[0]}`);
    }

    const runId =
        Date.now();

    const username =
        `limit_${runId}`;

    const password =
        'LoadTest@123';

    const register =
        http.post(
            `${BASE_URLS[0]}/auth/register`,
            JSON.stringify({
                username,
                password
            }),
            {
                headers: {
                    'Content-Type': 'application/json'
                },
                timeout: '60s'
            }
        );

    if (register.status !== 201) {

        throw new Error(
            `Registration failed status=${register.status}`
        );
    }

    const login =
        http.post(
            `${BASE_URLS[0]}/auth/login`,
            JSON.stringify({
                username,
                password
            }),
            {
                headers: {
                    'Content-Type': 'application/json'
                },
                responseType: 'text',
                timeout: '60s'
            }
        );

    if (login.status !== 200) {

        throw new Error(
            `Login failed status=${login.status}`
        );
    }

    const token =
        login.json('access_token');

    if (!token) {
        throw new Error('access_token missing');
    }

    console.log(
        `LIMIT_USERNAME=${username}`
    );

    return {
        token,
        runId,
        username
    };
}

export default function(data) {

    const seat =
        SEATS[__VU - 1];

    const baseUrl =
        BASE_URLS[(__VU - 1) % BASE_URLS.length];

    const response =
        http.post(
            `${baseUrl}/shows/${SHOW_ID}/reserve`,
            JSON.stringify({
                seats: [seat],
                idempotency_key:
                    `limit-${data.runId}-${__VU}`
            }),
            {
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': `Bearer ${data.token}`
                },
                timeout: '60s',
                tags: {
                    name: 'per-user-limit'
                }
            }
        );

    if (response.status === 201) {
        success201.add(1);
    } else if (response.status === 409) {
        conflict409.add(1);
    } else if (response.status >= 500) {
        server5xx.add(1);
    } else if (response.status === 0) {
        networkErrors.add(1);
    } else {
        unexpected.add(1);
    }
}
