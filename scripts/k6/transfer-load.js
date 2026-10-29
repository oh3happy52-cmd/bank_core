// [무엇] 이체 API 부하 테스트 (k6)
// [역할] 가상 사용자 20명이 30초 동안 쉬지 않고 이체 요청을 보내서, 초당 처리량과 응답 시간을 잼
// [실행] 서버를 켠 상태에서: k6 run scripts/k6/transfer-load.js
// 조건: 모든 요청이 같은 두 계좌(100-0001 → 100-0002) 사이 이체 → 같은 행을 잠그려고 줄을 서는 "최악의 경우"

import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = 'http://localhost:8080/api/v1';

export const options = {
    vus: 20,            // 가상 사용자 수 (동시에 요청을 보내는 사람 20명)
    duration: '30s',    // 30초 동안 계속
    thresholds: {
        http_req_failed: ['rate<0.01'],     // 실패율 1% 미만이어야 통과
        http_req_duration: ['p(95)<500'],   // 요청 95%가 500ms 안에 끝나야 통과
    },
};

// 테스트 시작 전에 한 번만 실행: 출금 계좌에 충분한 돈을 넣어 둠 (중간에 잔액 부족이 나지 않게)
export function setup() {
    const res = http.post(
        `${BASE_URL}/accounts/100-0001/deposits`,
        JSON.stringify({ amount: 100000000 }),   // 1억 원
        { headers: { 'Content-Type': 'application/json', 'Idempotency-Key': `k6-setup-${Date.now()}` } },
    );
    check(res, { '준비 입금 201': (r) => r.status === 201 });
}

// 가상 사용자 한 명이 30초 동안 반복하는 동작: 1,000원 이체 1번
export default function () {
    // 요청마다 다른 Idempotency-Key (__VU: 가상 사용자 번호, __ITER: 그 사용자의 반복 횟수)
    const key = `k6-${__VU}-${__ITER}-${Date.now()}`;

    const res = http.post(
        `${BASE_URL}/transfers`,
        JSON.stringify({ fromAccountNumber: '100-0001', toAccountNumber: '100-0002', amount: 1000 }),
        { headers: { 'Content-Type': 'application/json', 'Idempotency-Key': key } },
    );

    check(res, { '이체 201': (r) => r.status === 201 });
}