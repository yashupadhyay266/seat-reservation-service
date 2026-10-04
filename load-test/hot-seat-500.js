import http from 'k6/http';
import { Counter } from 'k6/metrics';

const SHOW_ID = __ENV.SHOW_ID;
const HOT_SEAT = __ENV.HOT_SEAT;
const USER_COUNT = Number(__ENV.USER_COUNT || 500);

const BASE_URLS = [
    'http://localhost:8080',
    'http://localhost:8081'
];

const success201 = new Counter('hot500_201');
const conflict409 = new Counter('hot500_409');
const server5xx = new Counter('hot500_5xx');
const networkErrors = new Counter('hot500_network_error');
const unexpected = new Counter('hot500_unexpected');

http.setResponseCallback(
    http.expectedStatuses(200, 201, 409)
);

export const options = {
    setupTimeout: '10m',
    discardResponseBodies: true,

    scenarios: {
        hot500: {
            executor: 'per-vu-iterations',
            vus: 500,
            iterations: 1,
            maxDuration: '5m'
        }
    },

    thresholds: {
        hot500_201: ['count==1'],
        hot500_409: ['count==499'],
        hot500_5xx: ['count==0'],
        hot500_network_error: ['count==0'],
        hot500_unexpected: ['count==0']
    }
};

export function setup() {

    if (!SHOW_ID) {
        throw new Error('SHOW_ID is required');
    }

    if (!HOT_SEAT) {
        throw new Error('HOT_SEAT is required');
    }

    for (const baseUrl of BASE_URLS) {

        const health = http.get(`${baseUrl}/actuator/health/readiness`, { timeout: '10s' });

        if (health.status !== 200) {
            throw new Error(
                `Application unavailable: ${baseUrl}`
            );
        }
    }

    const runId = Date.now();
    const tokens = [];

    for (let i = 0; i < USER_COUNT; i++) {

        const baseUrl =
            BASE_URLS[i % BASE_URLS.length];

        const username =
            `hot500_${runId}_${i}`;

        const password =
            'LoadTest@123';

        const register = http.post(
            `${baseUrl}/auth/register`,
            JSON.stringify({
                username,
                password
            }),
            {
                headers: {
                    'Content-Type': 'application/json'
                },
                timeout: '30s'
            }
        );

        if (register.status !== 201) {
            throw new Error(
                `Registration failed username=${username} status=${register.status}`
            );
        }

        const login = http.post(
            `${baseUrl}/auth/login`,
            JSON.stringify({
                username,
                password
            }),
            {
                headers: {
                    'Content-Type': 'application/json'
                },
                responseType: 'text',
                timeout: '30s'
            }
        );

        if (login.status !== 200) {
            throw new Error(
                `Login failed username=${username} status=${login.status}`
            );
        }

        const token =
            login.json('access_token');

        if (!token) {
            throw new Error(
                `access_token missing for ${username}`
            );
        }

        tokens.push(token);
    }

    console.log(
        `500-user setup complete show=${SHOW_ID} seat=${HOT_SEAT}`
    );

    return {
        tokens,
        runId
    };
}

export default function(data) {

    const index =
        (__VU - 1) % data.tokens.length;

    const token =
        data.tokens[index];

    const baseUrl =
        BASE_URLS[index % BASE_URLS.length];

    const response = http.post(
        `${baseUrl}/shows/${SHOW_ID}/reserve`,
        JSON.stringify({
            seats: [HOT_SEAT],
            idempotency_key:
                `hot500-${data.runId}-${__VU}`
        }),
        {
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${token}`
            },
            timeout: '30s',
            tags: {
                name: 'hot-seat-500'
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