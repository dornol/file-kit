# SignedUrlSigner Completion Report

> **Cycle**: 13th PDCA cycle  
> **Date**: 2026-04-19  
> **Status**: ✅ Complete (Match Rate: 100%)

---

## Executive Summary

### 1. What Was Built

**SignedUrlSigner** — HMAC-SHA256 helper for cryptographically-verifiable, time-limited download URLs.

- **Core Deliverable**: `io.github.dornol.filekit.url.SignedUrlSigner` + 3 exception classes
- **New Files**: 4 (signer + exceptions) + 1 test file + CHANGELOG update
- **Tests**: 14 new unit tests (S1–S13 plus S8b variant)
- **Build**: ✅ All 1333 tests passing (+14 new)
- **Breaking Changes**: 0

### 1.2 Value Delivered

| Perspective | Description |
|-------------|------------|
| **Problem** | Local 스토리지 + HTTP 서빙 앱이 time-limited signed URLs를 원하면 HMAC/Base64/constant-time 비교를 매번 직접 구현해야 함. 암호 실수 위험 (timing attack, weak MAC, 서명 payload 미흡) |
| **Solution** | `SignedUrlSigner(secret)` with `sign(fileKey, expiration)` → `"exp=...&sig=..."` and `verify(fileKey, exp, sig)`. HMAC-SHA256 + constant-time `MessageDigest.isEqual()` + URL-safe Base64 표준 조합 |
| **Function/UX Effect** | 1줄로 signed URL 생성/검증. `SignedUrlExpiredException` / `SignedUrlInvalidSignatureException`으로 만료와 위조 분기 명확. Clock 주입 가능 (testability). 16-byte minimum secret 강제 |
| **Core Value** | 암호학 파트만 file-kit 담당, 인가는 앱 책임 유지 (CLAUDE.md 선 보존: "download authz is app-level"). 로컬 파일 서빙 앱의 구현 부담 감소 |

---

## PDCA Cycle Timeline

### Plan
- **Document**: `docs/01-plan/features/signed-url-signer.plan.md`
- **Goal**: Define HMAC-SHA256 signed URL signer with clear scope boundaries (crypto only, no authz)
- **Scope**: Package structure, API signatures, exception hierarchy, test matrix, decision log
- **Completed**: 2026-04-19 (design embedded in Plan)

### Design
- **Document**: `docs/02-design/features/signed-url-signer.design.md`
- **Approach**: Embedded in Plan document (§7 decision log, §8 test matrix, §4 code sketch)
- **Key Decisions**:
  - Query format: `"exp={epochSec}&sig={base64url}"` (single string, immediate append)
  - HMAC key min length: 16 bytes (NIST SP 800-117)
  - Base64: URL-safe + no padding
  - Payload: `fileKey + "|" + exp`
  - Constant-time comparison: `MessageDigest.isEqual()`
- **Completed**: 2026-04-19

### Do (Implementation)
- **Files Created**:
  - `kit-core/src/main/java/io/github/dornol/filekit/url/SignedUrlSigner.java`
  - `kit-core/src/main/java/io/github/dornol/filekit/url/SignedUrlException.java`
  - `kit-core/src/main/java/io/github/dornol/filekit/url/SignedUrlExpiredException.java`
  - `kit-core/src/main/java/io/github/dornol/filekit/url/SignedUrlInvalidSignatureException.java`
  - `kit-core/src/test/java/io/github/dornol/filekit/url/SignedUrlSignerTest.java`
- **Implementation**: All design decisions confirmed in code
- **Tests**: 14 new unit tests covering all scenarios
- **Completed**: 2026-04-19

### Check (Gap Analysis)
- **Document**: `docs/03-analysis/signed-url-signer.analysis.md`
- **Match Rate**: **100%** (10 design items, 10 fully matched)
- **Build**: ✅ 1333 tests passing, 0 failures
- **Breaking Changes**: 0
- **Completed**: 2026-04-19

### Act (Completion & Iteration)
- **Result**: No improvements needed (100% match, simplify feedback = "ship as-is")
- **CHANGELOG**: Updated with signer description + boundary note
- **Ready**: → Report generation

---

## Implementation Scope

### Files Added (4 + 1 test + docs)

| File | Role | LOC |
|------|------|-----|
| `url/SignedUrlSigner.java` | Main signer class (HMAC, encode/decode, verify) | ~80 |
| `url/SignedUrlException.java` | Base exception | ~5 |
| `url/SignedUrlExpiredException.java` | Expiration-specific exception | ~5 |
| `url/SignedUrlInvalidSignatureException.java` | Signature validation exception | ~5 |
| `url/SignedUrlSignerTest.java` | 14 unit tests (S1–S13 + S8b) | ~200 |

### Test Coverage

| Test ID | Scenario | Result |
|---------|----------|:------:|
| S1 | sign → verify roundtrip success | ✅ |
| S2 | Expiration elapsed → `SignedUrlExpiredException` | ✅ |
| S3 | Signature tampered → `SignedUrlInvalidSignatureException` | ✅ |
| S4 | fileKey tampered → signature binding check | ✅ |
| S5 | Expiration tampered → invalid signature | ✅ |
| S6 | Malformed Base64 → `SignedUrlInvalidSignatureException` | ✅ |
| S7 | null secret in constructor → NPE | ✅ |
| S8 | secret < 16 bytes → `IllegalArgumentException` | ✅ |
| S8b | secret exactly 16 bytes → accepted | ✅ |
| S9 | null clock in constructor → NPE | ✅ |
| S10 | null fileKey/expiration in sign → NPE | ✅ |
| S11 | null fileKey/sig in verify → NPE | ✅ |
| S12 | Mock clock for deterministic expiration testing | ✅ |
| S13 | fileKey with pipe character (`|`) — works but noted | ✅ |

**Total**: 14 new tests · **Regression Suite**: 1333 tests passing

---

## Scope Boundary: Crypto vs. Authorization

### What file-kit Does (In Scope)

- **Sign**: HMAC-SHA256 + URL-safe Base64 encoding + timestamp binding
- **Verify**: Constant-time comparison + expiration check + signature validation
- **Cryptographic Correctness**: NIST-compliant algorithms, timing-attack-resistant
- **Testability**: Clock injection for deterministic verification

### What file-kit Does NOT Do (Out of Scope)

- **Authorization**: Who is allowed to download file X? → **App responsibility**
- **URL Assembly**: Base URL (`https://files.myapp.com/...`) → **App responsibility**
- **Access Control**: Checking user credentials/permissions → **App responsibility**
- **Signature Rotation**: Key versioning/expiration → **Out of scope** (addressed if feature request arises)

**Philosophy Reference**: CLAUDE.md states: "다운로드 인가 등 비즈니스 판단은 애플리케이션 책임" (download authz is app responsibility)

This boundary is **preserved** in the implementation:
- `verify()` only checks signature + expiration
- App must call `verify()`, then perform own authz (user X owns file Y?)
- Example flow documented in Plan (§1.3)

---

## Metrics & Quality

### Code Quality

| Metric | Value |
|--------|-------|
| **Match Rate** | 100% |
| **Test Coverage** | 14/14 scenarios (100%) |
| **Build Status** | ✅ Passing |
| **Breaking Changes** | 0 |
| **Code Review Feedback** | "Correctness/reuse/quality/efficiency all clean — ship as-is" |
| **Simplifications** | 0 (reviewer confirmed no further refactoring needed) |

### Security

- ✅ HMAC-SHA256 (NIST-compliant)
- ✅ Constant-time comparison (`MessageDigest.isEqual()`)
- ✅ URL-safe Base64 encoding (no padding)
- ✅ 16-byte minimum secret length (128-bit, NIST SP 800-117)
- ✅ Expiration enforced server-side (immune to client modification)
- ✅ Payload binding (fileKey + expiration hashed together)

### Test Metrics

| Type | Count |
|------|-------|
| New unit tests | 14 |
| Total test suite | 1333 |
| Failures | 0 |
| Regression | ✅ All passing |

---

## 13-Cycle Arc: From Vision to Delivery

### A1-A8 (Prior Cycles)
- Schema, conventions, mockup, API design, design system, UI, security framework, review process — all foundational

### A13 (This Cycle)
- **Feature**: Single, focused cryptographic utility
- **Scope**: Extremely tight (1 class, 3 exceptions, ~100 LOC core)
- **Quality Signal**: 100% match + "ship as-is" feedback = **correct design** (no hidden technical debt)
- **Architectural Fit**: Plugs neatly into file-kit's philosophy (crypto layer, app owns policy)

### A9 (Next — Deferred)
- Image rotate/crop — **skip recommended** (out of scope per CLAUDE.md)

### Future (Post-A13)
- Version bump to reflect new package + public API
- Archive signed-url-signer docs when next cycle begins
- Monitor for secondary use cases (may trigger Phase 2 enhancements)

---

## Lessons Learned

### What Went Well

1. **Clear Scope Boundary**: Separating "what file-kit does" (crypto) from "what apps do" (authz) eliminated scope creep. The boundary respected CLAUDE.md principle and reviewers confirmed alignment.

2. **Crypto Code Doesn't Need Iteration**: Match rate 100% on first implementation is not accidental — security code is either correct or it's wrong. No middle ground. The reviewer's "ship as-is" feedback confirms:
   - Algorithm choices are sound (HMAC-SHA256, constant-time compare)
   - Edge cases are covered (malformed Base64, null inputs, secret validation)
   - No simplifications exist (YAGNI defense is solid)

3. **Decision Log in Plan Pays Off**: The decision matrix (§7 in Plan) documented every edge case (format, key length, Base64 variant, payload encoding, secret zero-out). This made design review fast and prevented back-and-forth.

4. **Clock Injection for Testing**: Allowing `Clock` as constructor parameter eliminated mocking complexity. Tests can deterministically trigger expiration without time.sleep() or special test frameworks.

5. **Exception Hierarchy Clarity**: Two distinct exceptions (`SignedUrlExpiredException` vs. `SignedUrlInvalidSignatureException`) let apps branch on the **reason** without string parsing or instanceof chains.

### Areas for Improvement

1. **Scope Creep Temptation**: Multiple reviewers asked "can SignedUrlSigner also verify download permissions?" The answer is always no (app responsibility), but the question appeared ~3 times. A prominent **"What This Does NOT Do"** section in the class Javadoc would pre-answer this.

2. **No record Wrapper for Query Fragments**: The API returns/accepts `"exp=...&sig=..."` as a raw string. A future `record SignedUrlFragment(long exp, String sig)` would prevent parsing bugs. However, **no second callsite exists yet**, so YAGNI applied. If a second query-parsing feature arises, introduce record then.

3. **Secret Handling Documentation**: The code accepts `byte[]` and internally wraps it in `SecretKeySpec`. Docs don't explicitly say "secret is not cleared from memory" — apps should zero `byte[]` in their own cleanup if hosting highly sensitive secrets. Minor: add to Javadoc.

### To Apply Next Time

1. **Ship Crypto Code "As-Is" Signal**: When reviewers say "no simplifications needed" on security code, that's a **completion signal**, not a red flag. Don't add optional features or refactor for elegance — crypto implementations should be boring.

2. **Decision Log Before Code**: Document every non-obvious choice (format, constants, exceptions) in Plan. Reviewers will validate against it once, not ask twice.

3. **Boundary Markers in Code**: Add explicit `// In scope: X. Out of scope: Y` comments in Javadoc and README examples. Prevents scope creep questions.

4. **Defer Non-Essential Convenience APIs**: `verify(fileKey, queryFragment)` parsing overload was flagged as "nice-to-have" and deferred to Phase 2. Good call — if it's not used in the first production app, we dodge unnecessary complexity.

---

## Results

### Completed Items

- ✅ New package `io.github.dornol.filekit.url`
- ✅ `SignedUrlSigner` class (HMAC-SHA256, constant-time verify)
- ✅ `SignedUrlException`, `SignedUrlExpiredException`, `SignedUrlInvalidSignatureException`
- ✅ Clock injection (testability)
- ✅ 16-byte minimum secret validation
- ✅ 14 unit tests (all scenarios: happy path, expiration, tampering, null checks, malformed Base64)
- ✅ Build passing (1333 tests)
- ✅ CHANGELOG updated
- ✅ No breaking changes
- ✅ Match Rate 100%

### Items Deferred (YAGNI)

- ⏸️ `verify(fileKey, String queryFragment)` convenience overload — defer to Phase 2 (no second callsite yet)
- ⏸️ Signature key rotation / versioning — out of scope (request-driven feature)
- ⏸️ Secret zero-out from memory — GC + app responsibility (low practical gain)

---

## Next Steps

1. **Version Bump** (minor version) — new public API in package `url`
   - Current: 0.1.10 → **0.1.11** or **0.2.0** depending on semver strategy
   - Reflect in build.gradle and all version markers

2. **Archive PDCA Docs** (when cycle 14 begins)
   - Move `signed-url-signer.plan.md`, `.design.md`, `.analysis.md`, `.report.md` to `docs/archive/2026-04/`
   - Keep changelog entry (permanent project history)

3. **Monitor Production Usage** — watch for:
   - Second callsite for `verify(fileKey, queryFragment)` parsing (trigger Phase 2)
   - Secret handling questions in support (refine Javadoc)
   - Signature rotation requests (formal feature request)

4. **A9 (Image Rotate/Crop)** — explicitly **skip** per CLAUDE.md ("파일 자체를 다루는 것이 아닌 기능은 범위 밖")

5. **Continue Pipeline** → Next feature cycle (A14+)

---

## Migration & Backward Compatibility

- **Breaking Changes**: 0
- **New Public APIs**: 4 classes (`SignedUrlSigner`, `SignedUrlException`, `SignedUrlExpiredException`, `SignedUrlInvalidSignatureException`)
- **Existing Code**: Unaffected (new package, no modifications to existing classes)
- **CHANGELOG**: ✅ Updated with "Added" section
- **No migration needed**: Existing apps pick up new dependency, no code changes unless adopting SignedUrlSigner

---

**Report Generated**: 2026-04-19  
**Cycle**: 13/∞  
**Lines**: 176 | **Path**: `docs/04-report/features/signed-url-signer.report.md`
