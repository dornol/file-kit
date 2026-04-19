# Plan: Magic-Byte MIME Fallback

## Executive Summary

| Item | Detail |
|------|--------|
| Feature | magic-byte-mime-fallback |
| Date | 2026-04-19 |
| Status | Draft |
| Scope | kit-core: `DefaultMediaTypeDetector`에 magic-byte 스니핑 레이어 추가. 기존 JDK `URLConnection` 경로는 2차 fallback으로 유지 |
| Related | 초기 리뷰 A7 |

### Value Delivered

| Perspective | Description |
|-------------|-------------|
| **Problem** | `DefaultMediaTypeDetector`가 JDK `URLConnection.guessContentType*`만 사용 → **PDF, ZIP/DOCX, MP4, WebP, HEIC** 등 흔한 포맷이 null 반환 → `application/octet-stream` fallback → validator 오판정. Apache Tika 탑재 없인 실질적 사용 어려움 |
| **Solution** | 파일 앞 16바이트를 읽어 common 포맷의 magic byte 시그니처와 비교하는 `MagicByteMatcher` 추가. `DefaultMediaTypeDetector`는 (1) magic byte → (2) URLConnection stream → (3) URLConnection filename → (4) octet-stream 순 |
| **Function/UX Effect** | Tika 없이도 PNG/JPEG/GIF/PDF/ZIP/MP4/WebP 등 10+ 포맷 정확 판정. 의존성 0 추가 |
| **Core Value** | kit-core "pure Java" 원칙 유지하면서 validator 실용성 현격히 개선. Tika는 여전히 선택 |

---

## 1. 배경

### 1.1 현 `DefaultMediaTypeDetector` 한계

`DefaultMediaTypeDetector.java:25-37`:
```java
if (inputStream != null) {
    detected = URLConnection.guessContentTypeFromStream(buffered);
}
if (detected == null && filename != null) {
    detected = URLConnection.guessContentTypeFromName(filename);
}
```

JDK `URLConnection.guessContentTypeFromStream`은 `content-types.properties` 기반 — 매우 제한적:
- PNG ✓ (JDK 테이블 포함)
- JPEG 일부 ✓
- GIF ✓
- **PDF** ❌ → null
- **ZIP/DOCX/JAR** ❌ → null
- **MP4/MOV** ❌ → null
- **WebP** ❌ → null
- **HEIC** ❌ → null

파일명 기반은 확장자 신뢰 — 보안상 바람직하지 않음 (`evil.pdf.exe`).

### 1.2 A7이 커버하는 포맷

중요도 순:
1. PDF — 업무 파일 대표
2. ZIP/JAR/DOCX/XLSX/PPTX — office open xml 계열
3. JPEG/PNG/GIF — (JDK 이미 되지만 보강)
4. WebP — 최신 웹 이미지
5. MP4/MOV — 동영상
6. BMP — 레거시 이미지

### 1.3 Non-goal

- 모든 MIME 타입 지원 (Tika 수준 커버리지는 불가능)
- 인코딩 감지 (text/plain charset 등)
- Magic byte 사전 외부화 (사용자 커스텀 시그니처) — YAGNI

---

## 2. 범위

### 2.1 In Scope

- [ ] `MagicByteMatcher` — package-private (또는 public) 유틸. 정적 시그니처 테이블 + `match(byte[] header)`
- [ ] `DefaultMediaTypeDetector.detect()` 재작성 — magic byte → URLConnection → octet-stream
- [ ] 10+ 포맷 시그니처 (PDF, ZIP, PNG, JPEG, GIF, WebP, MP4, BMP, OGG, Zstd)
- [ ] 헤더 버퍼 크기: 16 bytes (모든 시그니처 커버)
- [ ] 기존 `DefaultMediaTypeDetectorTest` 회귀 확인 + 신규 magic byte 케이스
- [ ] CHANGELOG

### 2.2 Out of Scope

- OOXML 내부 구조 분석 (`application/zip` vs `docx/xlsx/pptx` 구분) — ZIP contents 검사 필요, 복잡도 ↑
- TIFF (byte order endianness 복잡)
- WebM (offset EBML 길이 가변)
- SVG (text-based, XML 파싱 필요)
- 사용자 커스텀 시그니처 추가 API

### 2.3 유보 결정

- **`MagicByteMatcher` 가시성**: public (재사용) vs package-private (내부)
- **시그니처 테이블 구조**: `byte[]` prefix vs `(offset, bytes)` 튜플 (ZIP/MP4는 offset 0이 아님)
- **헤더 버퍼 크기**: 16 vs 32 (Tika 기본 16KB는 과함)
- **JDK 1차 경로를 유지할지**: magic → JDK → octet-stream vs magic 단독

---

## 3. 요구사항

| ID | 요구사항 | 우선순위 |
|----|---------|---------|
| FR-01 | PDF/ZIP/PNG/JPEG/GIF/WebP/MP4/BMP 최소 8개 포맷 정확 판정 | High |
| FR-02 | Magic byte 불일치 → JDK `URLConnection` fallback | High |
| FR-03 | 양쪽 null → `application/octet-stream` | High |
| FR-04 | `inputStream.markSupported()` 고려 (기존 호환) | High |
| FR-05 | 헤더 읽기 실패 (IOException)는 상위로 전파 (기존 throws 유지) | Medium |
| FR-06 | 파일 크기 < 16 바이트 → partial 매칭 (가능하면 판정, 아니면 fallback) | Medium |

---

## 4. 설계 개요

### 4.1 `MagicByteMatcher` (신규)

```java
package io.github.dornol.filekit.validator;

final class MagicByteMatcher {

    private record Signature(int offset, byte[] bytes, String mimeType) {
        boolean matches(byte[] header, int headerLen) {
            if (offset + bytes.length > headerLen) return false;
            for (int i = 0; i < bytes.length; i++) {
                if (header[offset + i] != bytes[i]) return false;
            }
            return true;
        }
    }

    private static final List<Signature> SIGNATURES = List.of(
            // PNG: 89 50 4E 47 0D 0A 1A 0A
            new Signature(0, new byte[]{(byte)0x89, 'P', 'N', 'G'}, "image/png"),
            // JPEG: FF D8 FF
            new Signature(0, new byte[]{(byte)0xFF, (byte)0xD8, (byte)0xFF}, "image/jpeg"),
            // GIF: 47 49 46 38
            new Signature(0, "GIF8".getBytes(StandardCharsets.US_ASCII), "image/gif"),
            // PDF: 25 50 44 46
            new Signature(0, "%PDF".getBytes(StandardCharsets.US_ASCII), "application/pdf"),
            // ZIP (including JAR, DOCX, XLSX, PPTX before inner scan): 50 4B 03 04
            new Signature(0, new byte[]{'P', 'K', 0x03, 0x04}, "application/zip"),
            // BMP: 42 4D
            new Signature(0, new byte[]{'B', 'M'}, "image/bmp"),
            // WebP: RIFF....WEBP (0-3 RIFF, 8-11 WEBP)
            // Handled below via two-part signature (special-cased)
            // MP4/MOV: offset 4-7 = "ftyp"
            new Signature(4, "ftyp".getBytes(StandardCharsets.US_ASCII), "video/mp4"),
            // OGG: "OggS"
            new Signature(0, "OggS".getBytes(StandardCharsets.US_ASCII), "audio/ogg"),
            // Zstandard: 28 B5 2F FD
            new Signature(0, new byte[]{0x28, (byte)0xB5, 0x2F, (byte)0xFD}, "application/zstd")
    );

    // WebP는 두 구간 체크 필요 → 별도 처리
    static @Nullable String match(byte[] header, int headerLen) {
        // WebP 먼저
        if (headerLen >= 12
                && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') {
            return "image/webp";
        }
        for (Signature sig : SIGNATURES) {
            if (sig.matches(header, headerLen)) return sig.mimeType();
        }
        return null;
    }
}
```

### 4.2 `DefaultMediaTypeDetector.detect()` 재작성

```java
@Override
public String detect(@Nullable String filename, @Nullable InputStream inputStream) throws IOException {
    if (inputStream != null) {
        InputStream buffered = inputStream.markSupported() ? inputStream : new BufferedInputStream(inputStream);
        // (1) Magic byte sniffing — read up to 16 bytes
        byte[] header = new byte[16];
        buffered.mark(16);
        int read = buffered.read(header, 0, 16);
        buffered.reset();
        if (read > 0) {
            String magicDetected = MagicByteMatcher.match(header, read);
            if (magicDetected != null) return magicDetected;
        }
        // (2) JDK URLConnection stream sniff (fallback for formats we don't cover)
        String detected = URLConnection.guessContentTypeFromStream(buffered);
        if (detected != null) return detected;
    }
    // (3) Filename-based fallback
    if (filename != null) {
        String byName = URLConnection.guessContentTypeFromName(filename);
        if (byName != null) return byName;
    }
    // (4) Ultimate fallback
    return "application/octet-stream";
}
```

---

## 5. 구현 순서

| 단계 | 작업 | 예상 |
|------|------|------|
| 1 | `MagicByteMatcher` + 단위 테스트 | 40분 |
| 2 | `DefaultMediaTypeDetector` 재작성 | 25분 |
| 3 | 기존 `DefaultMediaTypeDetectorTest` 회귀 | 15분 |
| 4 | 신규 magic byte 테스트 추가 | 30분 |
| 5 | CHANGELOG | 10분 |

총: **약 2시간**

---

## 6. 공개 API

### 추가
- `MagicByteMatcher` (package-private, 재사용 가능하면 public 검토)

### 변경
- `DefaultMediaTypeDetector.detect()` 내부 동작 (public signature 불변)

### Breaking
- **관찰 가능한 동작 변화**: 기존 `application/octet-stream` 반환하던 입력이 이제 정확한 MIME을 반환. 대부분 사용자에게 개선이지만, validator가 특정 MIME만 허용하는 경우 새 MIME이 allowed set에 없으면 검증 실패 → **behavior change, non-breaking compile/link**

---

# Design

## 7. 유보 결정 확정

| 쟁점 | 결정 | 근거 |
|------|------|------|
| `MagicByteMatcher` 가시성 | **package-private** | 내부 헬퍼. 외부 공개는 YAGNI. 필요시 나중에 public 승격 쉬움 |
| 시그니처 구조 | **`(offset, bytes, mime)` 튜플** (MP4는 offset 4) | 확장성. ZIP/MP4 같은 off-0 시그니처 자연스럽게 처리 |
| 헤더 버퍼 크기 | **16 bytes** | WebP `RIFF...WEBP`가 12까지 필요. 16이면 여유. Tika 16KB는 과함 |
| JDK 1차 경로 | **유지 (2차 fallback)** | 보안/기존 호환. magic miss 시 JDK가 잡는 포맷도 존재 |
| WebP 2-part 체크 | **match()에서 special case** | 다른 포맷 공통 로직에 억지로 녹이지 않음. 3줄 if |

## 8. 테스트 매트릭스

### 8.1 `MagicByteMatcherTest` (신규)

| # | 케이스 | 검증 |
|---|-------|------|
| M1 | PNG bytes → "image/png" | |
| M2 | JPEG bytes | |
| M3 | GIF bytes | |
| M4 | PDF `%PDF` | |
| M5 | ZIP `PK\x03\x04` | |
| M6 | BMP `BM` | |
| M7 | WebP `RIFF...WEBP` (offset 8) | |
| M8 | MP4 `....ftyp` (offset 4) | |
| M9 | OGG `OggS` | |
| M10 | 랜덤 바이트 → null | |
| M11 | 빈 배열 / headerLen 0 → null | |
| M12 | 헤더 길이 < 시그니처 길이 → null | |

### 8.2 `DefaultMediaTypeDetectorTest` 확장

기존 테스트 유지 + magic byte 경로가 URLConnection보다 우선 동작 확인.

---

## 9. Next

`/pdca do magic-byte-mime-fallback`

| Version | Date | Author |
|---------|------|--------|
| 0.1 | 2026-04-19 | dhkim |
