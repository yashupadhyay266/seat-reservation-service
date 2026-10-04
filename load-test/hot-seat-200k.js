import http from 'k6/http';
import { Counter } from 'k6/metrics';

const SHOW_ID = __ENV.SHOW_ID;
const HOT_SEAT = __ENV.HOT_SEAT || 'A2';

const TOTAL_REQUESTS =
    Number(__ENV.TOTAL_REQUESTS || 200000);

const VUS =
    Number(__ENV.VUS || 500);

const USER_COUNT =
    Number(__ENV.USER_COUNT || 100);

const BASE_URLS = [
    'http://localhost:8080',
    'http://localhost:8081'
];

const success201 =
    new Counter('reservation_201');

const conflict409 =
    new Counter('reservation_409');

const unauthorized401 =
    new Counter('reservation_401');

const forbidden403 =
    new Counter('reservation_403');

const other4xx =
    new Counter('reservation_other_4xx');

const server5xx =
    new Counter('reservation_5xx');

const networkErrors =
    new Counter('reservation_network_error');

const unexpected =
    new Counter('reservation_unexpected');

http.setResponseCallback(
    http.expectedStatuses(
        200,
        201,
        409
    )
);

export const options = {

    discardResponseBodies: true,

    scenarios: {

        hot_seat_load: {

            executor: 'shared-iterations',

            vus: VUS,

            iterations: TOTAL_REQUESTS,

            maxDuration: '30m'
        }
    },

    thresholds: {

        reservation_401: [
            'count==0'
        ],

        reservation_403: [
            'count==0'
        ],

        reservation_5xx: [
            'count==0'
        ],

        reservation_network_error: [
            'count==0'
        ],

        reservation_unexpected: [
            'count==0'
        ]
    }
};

export function setup() {

    if (!SHOW_ID) {

        throw new Error(
            'SHOW_ID is required'
        );
    }

    if (!HOT_SEAT) {

        throw new Error(
            'HOT_SEAT is required'
        );
    }

    if (TOTAL_REQUESTS <= 0) {

        throw new Error(
            'TOTAL_REQUESTS must be greater than 0'
        );
    }

    if (VUS <= 0) {

        throw new Error(
            'VUS must be greater than 0'
        );
    }

    if (USER_COUNT <= 0) {

        throw new Error(
            'USER_COUNT must be greater than 0'
        );
    }

    verifyServices();

    const runId =
        Date.now();

    const tokens = [];

    for (
        let i = 0;
        i < USER_COUNT;
        i++
    ) {

        const baseUrl =
            BASE_URLS[
                i % BASE_URLS.length
            ];

        const username =
            `load_${runId}_${i}`;

        const password =
            'LoadTest@123';

        const registerResponse =
            http.post(

                `${baseUrl}/auth/register`,

                JSON.stringify({
                    username: username,
                    password: password
                }),

                {
                    headers: {
                        'Content-Type':
                            'application/json'
                    },

                    tags: {
                        name:
                            'register-load-user'
                    },

                    timeout: '30s'
                }
            );

        if (
            registerResponse.status !== 201
        ) {

            throw new Error(
                `Registration failed. ` +
                `username=${username}, ` +
                `status=${registerResponse.status}`
            );
        }

        const loginResponse =
            http.post(

                `${baseUrl}/auth/login`,

                JSON.stringify({
                    username: username,
                    password: password
                }),

                {
                    headers: {
                        'Content-Type':
                            'application/json'
                    },

                    tags: {
                        name:
                            'login-load-user'
                    },

                    responseType: 'text',

                    timeout: '30s'
                }
            );

        if (
            loginResponse.status !== 200
        ) {

            throw new Error(
                `Login failed. ` +
                `username=${username}, ` +
                `status=${loginResponse.status}, ` +
                `body=${loginResponse.body}`
            );
        }

        let accessToken;

        try {

            accessToken =
                loginResponse.json(
                    'access_token'
                );

        } catch (error) {

            throw new Error(
                `Unable to parse login response ` +
                `for ${username}. ` +
                `body=${loginResponse.body}`
            );
        }

        if (!accessToken) {

            throw new Error(
                `access_token missing ` +
                `for ${username}`
            );
        }

        tokens.push(
            accessToken
        );
    }

    console.log(
        `Load test setup complete. ` +
        `users=${tokens.length}, ` +
        `show=${SHOW_ID}, ` +
        `seat=${HOT_SEAT}, ` +
        `requests=${TOTAL_REQUESTS}, ` +
        `vus=${VUS}`
    );

    return {
        tokens: tokens,
        runId: runId
    };
}

export default function (data) {

    const tokenIndex =
        (__VU - 1)
        % data.tokens.length;

    const token =
        data.tokens[tokenIndex];

    const instanceIndex =
        (__VU + __ITER)
        % BASE_URLS.length;

    const baseUrl =
        BASE_URLS[instanceIndex];

    const idempotencyKey =
        `hot-seat-` +
        `${data.runId}-` +
        `${HOT_SEAT}-` +
        `${__VU}-` +
        `${__ITER}`;

    const body =
        JSON.stringify({

            seats: [
                HOT_SEAT
            ],

            idempotency_key:
                idempotencyKey
        });

    const response =
        http.post(

            `${baseUrl}/shows/` +
            `${SHOW_ID}/reserve`,

            body,

            {
                headers: {

                    'Content-Type':
                        'application/json',

                    'Authorization':
                        `Bearer ${token}`
                },

                tags: {
                    name:
                        'reserve-hot-seat'
                },

                timeout: '60s'
            }
        );

    classifyResponse(
        response
    );
}

function verifyServices() {

    for (
        let i = 0;
        i < BASE_URLS.length;
        i++
    ) {

        const baseUrl =
            BASE_URLS[i];

        const response =
            http.get(

                `${baseUrl}/actuator/health`,

                {
                    tags: {
                        name:
                            'health-check'
                    },

                    timeout: '10s'
                }
            );

        if (
            response.status !== 200
        ) {

            throw new Error(
                `Application is not healthy. ` +
                `url=${baseUrl}, ` +
                `status=${response.status}`
            );
        }
    }

    console.log(
        'Both application instances are reachable'
    );
}

function classifyResponse(
    response
) {

    if (
        response.status === 201
    ) {

        success201.add(1);

        return;
    }

    if (
        response.status === 409
    ) {

        conflict409.add(1);

        return;
    }

    if (
        response.status === 401
    ) {

        unauthorized401.add(1);

        return;
    }

    if (
        response.status === 403
    ) {

        forbidden403.add(1);

        return;
    }

    if (
        response.status >= 400
        &&
        response.status <= 499
    ) {

        other4xx.add(1);

        return;
    }

    if (
        response.status >= 500
        &&
        response.status <= 599
    ) {

        server5xx.add(1);

        return;
    }

    if (
        response.status === 0
    ) {

        networkErrors.add(1);

        return;
    }

    unexpected.add(1);
}