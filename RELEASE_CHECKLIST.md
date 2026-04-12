# Release Checklist

## 1. Code Quality

- [ ] 모든 public API에 Javadoc 작성 (클래스, 메서드, 파라미터, 리턴값)
- [ ] 새로 추가한 SPI 인터페이스에 `@see`, `@throws` 문서화
- [ ] FQN 대신 import 사용 (인라인 `java.util.Objects.requireNonNull` 등 금지)
- [ ] unused import 없음
- [ ] `@Deprecated(forRemoval = true)` 표시된 API가 남아있지 않은지 확인

## 2. Tests

- [ ] `./gradlew clean build` 전체 통과
- [ ] 새 기능에 대한 단위 테스트 작성 (mock 기반, 경계값 포함)
- [ ] 새 기능에 대한 통합 테스트 작성 (InMemoryFileStorage 기반 full-flow)
- [ ] 기존 테스트 중 느슨한 assertion 강화
  - `assertNotNull`만 있는 곳 → 구체적 값 검증 추가
  - 상태 변화 검증 누락 → storage.size(), repository.count() 등 추가
  - 이벤트 발행 검증 → verify(listener) 추가
- [ ] 에러 케이스 테스트 (null, 존재하지 않는 key, 잘못된 파일명 등)
- [ ] Builder 패턴 테스트 (null 파라미터, 기본값, chaining)
- [ ] Javadoc 빌드 경고 없음: `./gradlew javadoc`

## 3. Example Module

- [ ] 새로 추가한 서비스/API가 example에서 사용 가능한지 확인
- [ ] example 컴파일 확인: `./gradlew :example:compileJava`
- [ ] auto-configuration에 새 서비스 빈 등록 완료

## 4. Documentation

- [ ] README.md 버전 번호 최신화 (Quick Start 의존성 버전)
- [ ] 새 기능 README.md에 사용법 추가 (코드 예제 포함)
- [ ] 변경/삭제된 API가 있으면 README.md에서 제거 또는 수정
- [ ] CLAUDE.md 프로젝트 철학·범위 변경 시 반영
- [ ] 새 SPI 인터페이스 추가 시 README.md "Using without Spring Boot" 섹션 업데이트
- [ ] 새 auto-configured bean 추가 시 README.md "Auto-configured beans" 테이블 업데이트
- [ ] 새 에러 메시지 키 추가 시 README.md "Error Messages" 섹션 업데이트

## 5. Version & Release

- [ ] `build.gradle.kts`에서 `version` 업데이트
- [ ] 버전 커밋: `Bump version to x.y.z`
- [ ] 태그 생성: `git tag vx.y.z`
- [ ] push: `git push && git push --tags`
- [ ] CI 확인: GitHub Actions `CI` 워크플로우 통과
- [ ] Maven Central 배포 확인: `Maven Publish` 워크플로우 완료
- [ ] GitHub Release 자동 생성 확인: `GitHub Release` 워크플로우 완료

## 6. Post-Release

- [ ] Maven Central에서 artifact 조회 가능 확인
- [ ] GitHub Release 페이지에서 릴리즈 노트 확인
