# Fix CV transaction error

## Summary
`CvServiceImpl.replaceSkills()` calls the derived delete `UserSkillRepository.deleteByCvId(...)` outside any transaction, so Spring Data's SELECT-then-`EntityManager.remove()` fails with the reported 500. Add transaction boundaries to the CV write methods (consistent with the other service impls) and turn the derived delete into one bulk `DELETE` statement.

## Root Cause Recap
- `POST /api/v1/cv/upload` / `parse-text` -> `CvServiceImpl.persistParsed` -> `replaceSkills` -> `userSkillRepository.deleteByCvId(cvId)`.
- Derived `deleteBy...` = SELECT + `em.remove()` per row; `remove()` requires an active read-write transaction.
- `CvServiceImpl` and `CvController` declare no `@Transactional`; inherited `save/find` work only because `SimpleJpaRepository` CRUD methods are transactional themselves.
- First upload succeeds (no existing `user_skill` rows, `remove()` never runs); any later parse when skills exist fails, leaving `user_cv` committed but skills unreplaced.

## Changes

### 1. `backend/src/main/java/com/aiinterview/platform/service/impl/CvServiceImpl.java`
- Add import `org.springframework.transaction.annotation.Transactional`.
- Annotate `uploadCv`, `parseCvText`, and `setPosition` with `@Transactional` so the whole save + skill replacement runs in one transaction.
- Leave `getCv` and `getCvSkills` unchanged (read-only).
- No change to the OpenAI call order; the external call stays before `persistParsed` logically, but note it will now run inside the transaction (same pattern already used in `InterviewServiceImpl.startSession`).

### 2. `backend/src/main/java/com/aiinterview/platform/model/repository/UserSkillRepository.java`
- Replace the derived delete with a bulk delete executed inside the caller's transaction:
```java
@Modifying
@Query("delete from UserSkill s where s.cv.id = :cvId")
void deleteByCvId(@Param("cvId") Long cvId);
```
- Add imports `org.springframework.data.jpa.repository.Modifying`, `org.springframework.data.jpa.repository.Query`, `org.springframework.data.repository.query.Param`.
- This removes the select-then-remove behavior and the N+1 `remove` calls.

### 3. No other files
- No schema change (`ddl-auto: update`, Flyway disabled).
- No controller change.

## Verification
- Compile: from `backend`, run `.\mvnw.cmd -q -DskipTests compile`.
- Manual test against a running backend + MySQL:
  1. Login and call `POST /api/v1/cv/parse-text` with a CV text that yields skills -> expect 200.
  2. Call the same endpoint again with different CV text -> expect 200 (previously 500).
  3. Call `GET /api/v1/cv/skills` -> only skills from the second parse must be present.
  4. Repeat through `POST /api/v1/cv/upload` with a PDF -> expect 200 on second upload as well.
- Optional: run the existing integration test suite if a test database is available.

## Notes / Trade-offs
- `@Transactional` on `uploadCv` holds a JDBC connection for the duration of the OpenAI call. This matches the existing project pattern; if pool pressure becomes an issue later, wrap only `persistParsed` with `TransactionTemplate` instead.
- Separate, out-of-scope risk: `UserCvRepository.findByUserId` returns `Optional<UserCv>` while `user_cv.user_id` has no unique constraint. If duplicate CV rows ever appear, `persistParsed` would throw `IncorrectResultSizeDataAccessException`. A follow-up could add a unique index on `user_cv(user_id)` or switch to `findTopByUserIdOrderByUpdatedAtDesc`.