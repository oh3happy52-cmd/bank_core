# 3주차 ① 공통 에러 응답

## 1. 왜 했나
- 이전에는 에러가 나면 응답 모양이 제각각이었다.
  - 잔액 부족 → `IllegalStateException` → 500 (서버 오류처럼 보임)
  - 없는 계좌 → `ResponseStatusException` → Spring 기본 응답
- 클라이언트가 **에러 원인을 구분할 수 없고**, 사용자 잘못(잔액 부족)과 서버 잘못(500)이 섞였다.

## 2. 무엇을 만들었나
| 파일 | 역할 |
|---|---|
| `ErrorCode` (enum) | 에러 이름(code), HTTP 상태 코드, 기본 메시지를 한 곳에 모음 |
| `BusinessException` | 서비스/엔티티에서 업무 규칙 위반 시 던지는 예외 |
| `ErrorResponse` (record) | 응답 본문 모양 `{code, message}` |
| `GlobalExceptionHandler` | 모든 예외를 잡아서 같은 모양으로 응답 (`@RestControllerAdvice`) |

## 3. 응답 구조
- **HTTP 상태 코드**: 큰 분류 (HTTP 표준. 상황에 맞는 번호를 고름)
- **code**: 정확한 원인. 프로그램이 비교하는 용도라 바뀌지 않는 영어 이름
- **message**: 사람이 읽는 한국어 설명. 상세 값이 붙을 수 있음

```json
HTTP 404
{"code":"ACCOUNT_NOT_FOUND","message":"계좌를 찾을 수 없습니다: 999-9999"}
```

## 4. 에러 코드 목록
| code | 상태 | 언제 |
|---|---|---|
| INVALID_REQUEST | 400 | 요청 값 검증 실패 (`@Positive` 등), JSON 형식 오류 |
| INVALID_AMOUNT | 400 | 서비스에서 금액이 0 이하 |
| SAME_ACCOUNT | 400 | 같은 계좌로 이체 |
| MISSING_IDEMPOTENCY_KEY | 400 | `Idempotency-Key` 헤더 없음 |
| ACCOUNT_NOT_FOUND | 404 | 계좌 없음 |
| INSUFFICIENT_BALANCE | 422 | 잔액 부족 |
| ACCOUNT_NOT_ACTIVE | 422 | 정지/해지 계좌 |
| INTERNAL_ERROR | 500 | 예상하지 못한 오류 (내부 내용은 로그에만 남기고 응답에는 숨김) |

## 5. 확인 결과 (curl)
| 요청 | 결과 |
|---|---|
| 잔액보다 큰 금액 이체 | 422 `INSUFFICIENT_BALANCE` |
| 없는 계좌 조회 | 404 `ACCOUNT_NOT_FOUND` |
| 금액 0원 이체 | 400 `INVALID_REQUEST` (amount: 0보다 커야 합니다) |
| Idempotency-Key 헤더 없이 이체 | 400 `MISSING_IDEMPOTENCY_KEY` |

- 기존 동시성/락 테스트 전부 통과 (`./gradlew test` BUILD SUCCESSFUL)

## 6. 트러블슈팅
- **문제**: curl 결과가 전부 500 `INTERNAL_ERROR`
- **원인**: `Account`, `AccountService`가 아직 예전 예외(`IllegalStateException`, `ResponseStatusException`)를 던지고 있었음
- **해결**: 두 파일을 `BusinessException`으로 교체하고 서버 재시작
- **결과**: 4개 요청 모두 기대한 code와 상태 코드로 응답

## 7. 판단 근거
- 0원 요청이 `INVALID_AMOUNT`가 아니라 `INVALID_REQUEST`인 이유: 컨트롤러의 `@Positive` 검증이 서비스보다 **먼저** 막기 때문.
  서비스의 `validateAmount`는 API를 거치지 않고 서비스를 직접 부를 때를 대비한 두 번째 방어선.
- 500 응답에 예외 내용을 넣지 않은 이유: 내부 구조(클래스명, SQL 등)가 외부에 드러나지 않게 하기 위해.