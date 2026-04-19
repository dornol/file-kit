# Plan: ChecksumAlgorithm Enum

## Executive Summary

| Item | Detail |
|------|--------|
| Feature | checksum-algorithm-enum |
| Date | 2026-04-19 |
| Status | Draft |
| Scope | kit-core: `ChecksumAlgorithm` enum + 제네릭 `MessageDigestChecksumCalculator`. `Sha256ChecksumCalculator`는 backward-compat 서브클래스로 유지 |
| Related | 초기 리뷰 A5 |

### Value Delivered

| Perspective | Description |
|-------------|-------------|
| **Problem** | `Sha256ChecksumCalculator`가 SHA-256 하드코딩. MD5(레거시 호환)/SHA-1/SHA-512를 원하는 사용자는 `ChecksumCalculator` SPI를 처음부터 구현해야 함 — 보일러플레이트 크고 오타 위험 (`"SHA256"` vs `"SHA-256"`) |
| **Solution** | `ChecksumAlgorithm` enum으로 curated 알고리즘 (MD5, SHA-1, SHA-256, SHA-384, SHA-512) 제공. `MessageDigestChecksumCalculator(ChecksumAlgorithm)`가 MessageDigest 기반 공통 구현. `Sha256ChecksumCalculator`는 `super(ChecksumAlgorithm.SHA_256)` 호출하는 얇은 서브클래스로 유지 (call site 불변) |
| **Function/UX Effect** | `new MessageDigestChecksumCalculator(ChecksumAlgorithm.MD5)` 한 줄로 레거시 호환 가능. 타입 안전 |
| **Core Value** | Checksum 알고리즘이 1급 설정 지점으로. Sha256 구현의 MessageDigest 로직이 재사용 가능 위치로 이동. Backward compat 유지 |

---

## 1. 배경

### 1.1 현재 SHA-256 하드코딩 위치

- `Sha256ChecksumCalculator.java:24` — `MessageDigest.getInstance("SHA-256")`
- `Sha256ChecksumCalculator.java:43` — 같은 호출
- `Sha256ChecksumCalculator.java:64` — `newComputation()` 내부

3곳 전부 동일 문자열. 알고리즘을 파라미터화하면 하나로 통합 가능.

### 1.2 기존 호출자

- `FileKitAutoConfiguration` (kit-spring-boot-starter) — 기본 bean으로 `new Sha256ChecksumCalculator()`
- 통합 테스트 여러 건 — 직접 `new Sha256ChecksumCalculator()`
- 사용자 코드: 내부 가시성이므로 불명

Backward compat 위해 `Sha256ChecksumCalculator`는 유지 필수.

---

## 2. 범위

### 2.1 In Scope

- [ ] `ChecksumAlgorithm` enum (spi/) — MD5, SHA-1, SHA-256, SHA-384, SHA-512
- [ ] `MessageDigestChecksumCalculator(ChecksumAlgorithm)` 신설 — 현 SHA-256 로직 이동
- [ ] `Sha256ChecksumCalculator`를 `extends MessageDigestChecksumCalculator`로 리팩토링 (0-arg 생성자 유지, 공개 API 불변)
- [ ] 단위 테스트 신규 (`ChecksumAlgorithmTest`, `MessageDigestChecksumCalculatorTest`)
- [ ] 기존 `Sha256ChecksumCalculatorTest` 회귀 0
- [ ] CHANGELOG

### 2.2 Out of Scope

- `ChecksumCalculator` SPI 자체 변경
- BLAKE2/BLAKE3 등 JDK 표준 외 알고리즘
- 알고리즘별 보안 등급 annotation

### 2.3 유보 결정

- **enum 값 범위**: MD5 포함 여부 (보안 약함, 호환 목적 외 지양)
- **enum 생성자 파라미터**: 알고리즘명만 vs (name, hexLength) 포함
- **Sha256 public 클래스를 deprecate할지**
- **package**: `spi` vs `spi.checksum`

---

## 3. 요구사항

| ID | 요구사항 | 우선순위 |
|----|---------|---------|
| FR-01 | `ChecksumAlgorithm` 5개 값 제공 (MD5~SHA-512) | High |
| FR-02 | `MessageDigestChecksumCalculator(ChecksumAlgorithm)` 인스턴스화 | High |
| FR-03 | `checksum(byte[])`/`checksum(InputStream)`/`newComputation()` 모두 동작 | High |
| FR-04 | `Sha256ChecksumCalculator` 기존 call-site 100% 호환 (0-arg ctor, SHA-256 동작 유지) | High |
| FR-05 | `NoSuchAlgorithmException`은 `IllegalStateException` wrap (현 동작 유지) | High |
| FR-06 | 알고리즘별 해시 값이 `MessageDigest`가 반환하는 바이트와 일치 | High |

---

## 4. 구현 순서

| 단계 | 작업 | 예상 |
|------|------|------|
| 1 | `ChecksumAlgorithm` enum + 단위 테스트 | 15분 |
| 2 | `MessageDigestChecksumCalculator` 추출 + 테스트 | 25분 |
| 3 | `Sha256ChecksumCalculator` 서브클래스 리팩토링 | 10분 |
| 4 | 회귀 확인 | 10분 |
| 5 | CHANGELOG | 5분 |

총: **약 1시간**

---

## 5. 공개 API

### 추가
- `spi.ChecksumAlgorithm` (enum)
- `spi.MessageDigestChecksumCalculator` (class, public)

### 변경
- `Sha256ChecksumCalculator` 내부만 — public API 불변

### Breaking
- 없음

---

# Design

## 6. 유보 결정 확정

| 쟁점 | 결정 | 근거 |
|------|------|------|
| enum 값 | **MD5, SHA-1, SHA-256, SHA-384, SHA-512 5개** | 레거시 호환 위해 MD5 포함. JDK 표준 모두 제공 |
| 생성자 파라미터 | **알고리즘명만** (`String standardName`) | 단순. hex length는 `finish()` 결과로 얻을 수 있음, pre-compute 불필요 |
| Sha256 deprecate | **No deprecate, 유지** | 가장 많이 쓰이는 알고리즘 convenience class로 의미 있음. deprecate는 call site churn |
| package | **`spi`** | 기존 `ChecksumCalculator`와 동일 위치. 새 서브패키지 추가는 YAGNI |

---

## 7. API 정의

### 7.1 `ChecksumAlgorithm`

```java
package io.github.dornol.filekit.spi;

public enum ChecksumAlgorithm {
    MD5("MD5"),
    SHA_1("SHA-1"),
    SHA_256("SHA-256"),
    SHA_384("SHA-384"),
    SHA_512("SHA-512");

    private final String standardName;

    ChecksumAlgorithm(String standardName) { this.standardName = standardName; }

    /**
     * Returns the standard algorithm name recognized by
     * {@link java.security.MessageDigest#getInstance(String)}.
     */
    public String standardName() { return standardName; }
}
```

### 7.2 `MessageDigestChecksumCalculator`

```java
package io.github.dornol.filekit.spi;

public class MessageDigestChecksumCalculator implements ChecksumCalculator {

    private static final HexFormat HEX = HexFormat.of();

    private final ChecksumAlgorithm algorithm;

    public MessageDigestChecksumCalculator(ChecksumAlgorithm algorithm) {
        this.algorithm = Objects.requireNonNull(algorithm, "algorithm");
    }

    public ChecksumAlgorithm algorithm() { return algorithm; }

    @Override
    public String checksum(byte[] bytes) {
        return HEX.formatHex(newDigest().digest(bytes));
    }

    @Override
    public String checksum(InputStream inputStream) {
        try {
            MessageDigest digest = newDigest();
            byte[] buffer = new byte[8192];
            int n;
            while ((n = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, n);
            }
            return HEX.formatHex(digest.digest());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public ChecksumComputation newComputation() {
        return new MessageDigestComputation(newDigest());
    }

    private MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance(algorithm.standardName());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(algorithm.standardName() + " not available", e);
        }
    }

    private static final class MessageDigestComputation implements ChecksumComputation {
        private final MessageDigest md;
        private boolean finished = false;
        MessageDigestComputation(MessageDigest md) { this.md = md; }
        @Override public void update(byte[] buf, int off, int len) {
            if (finished) throw new IllegalStateException("ChecksumComputation already finished");
            md.update(buf, off, len);
        }
        @Override public String finish() {
            if (finished) throw new IllegalStateException("ChecksumComputation already finished");
            finished = true;
            return HEX.formatHex(md.digest());
        }
    }
}
```

### 7.3 `Sha256ChecksumCalculator` 리팩토링

```java
public class Sha256ChecksumCalculator extends MessageDigestChecksumCalculator {
    public Sha256ChecksumCalculator() {
        super(ChecksumAlgorithm.SHA_256);
    }
}
```

7줄로 압축.

---

## 8. 테스트 매트릭스

### 8.1 `ChecksumAlgorithmTest` (신규, ~3 케이스)

| # | 케이스 |
|---|-------|
| A1 | 각 enum value의 standardName이 `MessageDigest.getInstance`에 유효 |
| A2 | enum values() 5개 반환 |
| A3 | `standardName()` null 아님 |

### 8.2 `MessageDigestChecksumCalculatorTest` (신규, ~8 케이스)

| # | 케이스 |
|---|-------|
| M1 | SHA-256 checksum(byte[]) 결과가 알려진 해시와 일치 |
| M2 | MD5 checksum(byte[]) 결과 |
| M3 | SHA-512 checksum(byte[]) 결과 |
| M4 | checksum(InputStream)과 checksum(byte[]) 결과 동일 |
| M5 | newComputation() 스트리밍과 byte[] 결과 동일 |
| M6 | 생성자 null algorithm → NPE |
| M7 | algorithm() 반환값 확인 |
| M8 | empty 입력 |

### 8.3 기존 `Sha256ChecksumCalculatorTest` 회귀

변경 없이 통과해야 함 (서브클래스 경로).

---

## 9. Next

`/pdca do checksum-algorithm-enum`

| Version | Date | Author |
|---------|------|--------|
| 0.1 | 2026-04-19 | dhkim |
