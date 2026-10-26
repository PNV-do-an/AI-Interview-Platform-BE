# INTERVIEW PREPARATION SYSTEM - KẾ HOẠCH THỰC HIỆN

> **Ticket:** AIP-28  
> **Ngày tạo:** 2026-09-11  
> **Trạng thái:** Planning  
> **Phiên bản:** 1.0

---

## MỤC LỤC

1. [Tổng quan](#1-tổng-quan)
2. [Business Requirements](#2-business-requirements)
3. [Architecture](#3-architecture)
4. [Database Schema](#4-database-schema)
5. [Backend Implementation](#5-backend-implementation)
6. [AI Integration](#6-ai-integration)
7. [Frontend Implementation](#7-frontend-implementation)
8. [User Flow](#8-user-flow)
9. [Implementation Phases](#9-implementation-phases)
10. [Technical Decisions](#10-technical-decisions)
11. [Configuration](#11-configuration)
12. [API Reference](#12-api-reference)

---

## 1. Tổng quan

### 1.1 Mục tiêu

Xây dựng hệ thống **Interview Preparation** cho phép user:
- Upload CV → AI phân tích kỹ năng, kinh nghiệm, vị trí phù hợp
- Nhận chương trình ôn tập cá nhân hóa theo vị trí + level
- Thực hiện bài test phỏng vấn mô phỏng với câu hỏi AI generate
- Đánh giá kết quả bằng AI (text + audio recording)

### 1.2 Scope

| Feature | Mô tả | Priority |
|---------|-------|----------|
| CV Upload & Parsing | Upload PDF/text, AI extract skills | P0 |
| Study Program | Tạo chương trình ôn tập theo topic/level | P0 |
| Interview Session | Phiên phỏng vấn mô phỏng | P0 |
| Audio Recording | Ghi âm câu trả lời | P1 |
| Audio Analysis | Đánh giá độ mạch lạc từ audio | P1 |
| Question Cache | Lưu câu hỏi AI để reuse | P1 |
| History & Tracking | Theo dõi tiến độ theo thời gian | P2 |

---

## 2. Business Requirements

### 2.1 Chức năng CV Analysis

**Input:**
- User upload file PDF hoặc paste text CV

**Output:**
- Danh sách kỹ năng (skill name + proficiency level)
- Kinh nghiệm làm việc (company, role, duration)
- Học vấn
- Gợi ý 3 vị trí phù hợp nhất
- Nhận diện khoảng trống kiến thức (knowledge gaps)

**Quy trình:**
```
User Upload CV
    ↓
Extract text (Apache PDFBox cho PDF)
    ↓
Send to AI (GPT-4o-mini): "Phân tích CV này, trích xuất skills, experience, education"
    ↓
Parse AI response → Lưu vào user_cv + user_skill tables
    ↓
Hiển thị kết quả cho user confirm/edit
```

### 2.2 Chức năng Study Program

**Hai kiểu chương trình:**

#### Kiểu 1: Ôn tập (Study)
- Căn cứ vào thông tin CV + vị trí target
- Chia cấp độ rõ ràng: BEGINNER / INTERMEDIATE / ADVANCED
- Mỗi chương trình có nhiều topics
- Mỗi topic có nhiều câu hỏi
- User trả lời → có feedback ngay + giải thích

#### Kiểu 2: Interview Prep
- Căn cứ vào vị trí + yêu cầu kiến thức
- Phân bố câu hỏi theo tỷ lệ (VD: 20% beginner, 50% intermediate, 30% advanced)
- Giống phỏng vấn thật: câu hỏi lần lượt, có giới hạn thời gian
- Đánh giá tổng quan cuối phiên

**Yêu cầu trước khi generate câu hỏi:**
1. Nắm được nhu cầu vị trí, kỹ năng của user → từ CV
2. Đề xuất kiến thức technical cần có cho vị trí đó
3. Generate câu hỏi theo phân bố difficulty

### 2.3 Chức năng Interview Session

**Quy trình mô phỏng:**

```
User Upload CV → Get parsed info
    ↓
Chọn số câu hỏi muốn có (VD: 20 câu)
    ↓
Hệ thống phân bố câu hỏi theo tỷ lệ difficulty
    ↓
Bắt đầu phiên → câu hỏi hiện lần lượt
    ↓
User trả lời: text (bắt buộc) + audio (tùy chọn)
    ↓
AI đánh giá từng câu trả lời
    ↓
Kết thúc phiên → AI generate đánh giá tổng quan
    ↓
Hiển thị kết quả: điểm, điểm mạnh/yếu, gợi ý cải thiện
```

**Các yếu tố đánh giá:**
- **Accuracy:** Độ chính xác câu trả lời (so với đáp án)
- **Eloquence:** Độ mạch lạc (từ audio analysis)
- **Coherence:** Tính logic, mạch lạc của câu trả lời
- **Technical Depth:** Chiều sâu kỹ thuật
- **Overall:** Điểm tổng hợp

#### Design Decisions — Phase 3 (Interview Session)

> ⚙️ Các quyết định đã chốt khi triển khai backend Phase 3 (đợt 1):

1. **Nguồn câu hỏi: cache-first + AI** — tái dùng `AIQuestionService.generateQuestions` (sẵn cache `ai_question_cache` + AI bổ sung ở Phase 2). Pool phiên = gọi lần lượt từng skill của CV (tối đa **6 skills** = skills + knowledge_gaps, hoặc `focusSkills` trong request nếu có), lưu danh sách `question id` vào cột **`question_pool`** của `interview_session` → `GET next` không gọi AI lại.
2. **Audio: đủ bộ khung ngay đợt này** — `AudioAnalysisService` lưu file vào `storage.audio-upload-dir`, gọi **Whisper** (`/audio/transcriptions`) để transcribe, rồi gọi AI chấm **eloquence + coherence**. Text bắt buộc, audio tùy chọn (base64 trong body theo §6.4).
3. **Finish khi AI fail: giữ `IN_PROGRESS`** — `AIServiceException` → **502**, transaction rollback (answers đã lưu giữ nguyên, evaluation/session không đổi) — nhất quán pattern Phase 2.
4. Câu hỏi AI sinh lúc start được **persist vào bảng `question`** (không gắn topic) để phiên tham chiếu bằng id.

### 2.4 AI Question Generation & Cache

**Ý tưởng:**
1. Generate câu hỏi từ AI ban đầu
2. Lưu vào DB (ai_question_cache) để tiết kiệm token lần sau
3. Khi cần câu hỏi mới → check cache trước (trùng position + skill + difficulty)
4. Cache hit → lấy từ DB, không gọi AI
5. Cache miss → gọi AI generate → lưu cache

**Dedup logic:**
```java
String cacheKey = hash(position + skillTag + difficulty + questionText);
// Check ai_question_cache by cacheKey
// If exists: reuse, increment times_used
// If not: generate via AI, save to cache
```

---

## 3. Architecture

### 3.1 System Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    FRONTEND (React + TypeScript)          │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌───────────┐  │
│  │CV Upload │ │ Study    │ │ Interview│ │ Results   │  │
│  │  Page    │ │ Program  │ │ Session  │ │ & Review  │  │
│  └────┬─────┘ └────┬─────┘ └────┬─────┘ └─────┬─────┘  │
│       │             │            │              │        │
│  ┌────▼─────────────▼────────────▼──────────────▼────┐  │
│  │              services/http.service.ts              │  │
│  │              (Axios + JWT interceptor)             │  │
│  └─────────────────────┬─────────────────────────────┘  │
└────────────────────────┼────────────────────────────────┘
                         │ REST API (HTTPS)
┌────────────────────────▼────────────────────────────────┐
│                  BACKEND (Spring Boot 3.2.5)             │
│                                                         │
│  ┌────────────────────────────────────────────────────┐ │
│  │                    Controllers                      │ │
│  │  CvController | StudyProgramController             │ │
│  │  InterviewController | QuestionController          │ │
│  └──────────────────────┬─────────────────────────────┘ │
│                         │                               │
│  ┌──────────────────────▼─────────────────────────────┐ │
│  │                    Services                         │ │
│  │  CvService | StudyProgramService                   │ │
│  │  InterviewService | AIQuestionService              │ │
│  │  AudioAnalysisService                              │ │
│  └──────────────────────┬─────────────────────────────┘ │
│                         │                               │
│  ┌──────────────────────▼─────────────────────────────┐ │
│  │              Repositories (JPA)                     │ │
│  │  UserCvRepository | StudyProgramRepository         │ │
│  │  QuestionRepository | InterviewSessionRepository   │ │
│  └──────────────────────┬─────────────────────────────┘ │
│                         │                               │
│  ┌──────────────────────▼─────────────────────────────┐ │
│  │                   MySQL 8                           │ │
│  └────────────────────────────────────────────────────┘ │
│                                                         │
│  ┌────────────────────────────────────────────────────┐ │
│  │              External Services                      │ │
│  │  OpenAI GPT-4o-mini (Generate + Evaluate)            │ │
│  │  OpenAI Whisper (Audio Transcription)              │ │
│  └────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────┘
```

### 3.2 Package Structure

```
com.aiinterview.platform/
├── AiInterviewPlatformApplication.java
├── common/
│   ├── exception/
│   │   ├── GlobalExceptionHandler.java
│   │   ├── BadRequestException.java
│   │   ├── ResourceNotFoundException.java
│   │   └── AIServiceException.java
│   └── response/
│       └── ApiResponse.java
├── config/
│   ├── DataInitializer.java
│   ├── AIConfig.java                    ← NEW
│   ├── SecurityConfig.java
│   └── CORSConfig.java
├── controller/
│   ├── auth/
│   │   ├── AuthController.java
│   │   └── AuthorizationController.java
│   ├── AdminUserController.java
│   ├── user/
│   │   ├── ProfileController.java
│   │   └── ViewController.java
│   ├── CvController.java                ← NEW
│   ├── StudyProgramController.java      ← NEW
│   ├── InterviewController.java         ← NEW
│   └── QuestionController.java          ← NEW
├── model/
│   ├── entity/
│   │   ├── User.java
│   │   ├── AdminAuditLog.java
│   │   ├── UserCv.java                  ← NEW
│   │   ├── UserSkill.java               ← NEW
│   │   ├── StudyProgram.java            ← NEW
│   │   ├── StudyTopic.java              ← NEW
│   │   ├── Question.java                ← NEW
│   │   ├── AiQuestionCache.java         ← NEW
│   │   ├── InterviewSession.java        ← NEW
│   │   ├── InterviewAnswer.java         ← NEW
│   │   └── InterviewEvaluation.java     ← NEW
│   ├── dto/
│   │   ├── request/
│   │   │   ├── RegisterRequest.java
│   │   │   ├── LoginRequest.java
│   │   │   ├── CvUploadRequest.java         ← NEW
│   │   │   ├── CvTextRequest.java           ← NEW
│   │   │   ├── SetPositionRequest.java      ← NEW
│   │   │   ├── GenerateProgramRequest.java  ← NEW
│   │   │   ├── StartInterviewRequest.java   ← NEW
│   │   │   ├── SubmitAnswerRequest.java     ← NEW
│   │   │   └── GenerateQuestionRequest.java ← NEW
│   │   └── response/
│   │       ├── AuthResponse.java
│   │       ├── ProfileResponse.java
│   │       ├── CvParseResponse.java         ← NEW
│   │       ├── StudyProgramResponse.java    ← NEW
│   │       ├── TopicResponse.java           ← NEW
│   │       ├── QuestionResponse.java        ← NEW
│   │       ├── InterviewSessionResponse.java ← NEW
│   │       ├── AnswerEvaluationResponse.java ← NEW
│   │       └── EvaluationReportResponse.java ← NEW
│   ├── enums/
│   │   ├── Role.java
│   │   ├── DifficultyLevel.java         ← NEW
│   │   ├── QuestionType.java            ← NEW
│   │   ├── ProgramType.java             ← NEW
│   │   ├── SessionStatus.java           ← NEW
│   │   └── ProficiencyLevel.java        ← NEW
│   └── repository/
│       ├── UserRepository.java
│       ├── JpaUserRepository.java
│       ├── UserCvRepository.java            ← NEW
│       ├── UserSkillRepository.java         ← NEW
│       ├── StudyProgramRepository.java      ← NEW
│       ├── StudyTopicRepository.java        ← NEW
│       ├── QuestionRepository.java          ← NEW
│       ├── AiQuestionCacheRepository.java   ← NEW
│       ├── InterviewSessionRepository.java  ← NEW
│       ├── InterviewAnswerRepository.java   ← NEW
│       └── InterviewEvaluationRepository.java ← NEW
├── service/
│   ├── UserService.java
│   ├── CvService.java                    ← NEW
│   ├── StudyProgramService.java          ← NEW
│   ├── InterviewService.java             ← NEW
│   ├── AIQuestionService.java            ← NEW
│   ├── AudioAnalysisService.java         ← NEW
│   └── impl/
│       ├── UserServiceImpl.java
│       ├── CvServiceImpl.java            ← NEW
│       ├── StudyProgramServiceImpl.java  ← NEW
│       ├── InterviewServiceImpl.java     ← NEW
│       ├── AIQuestionServiceImpl.java    ← NEW
│       └── AudioAnalysisServiceImpl.java ← NEW
└── security/
    ├── jwt/
    │   ├── JwtUtil.java
    │   ├── JwtFilter.java
    │   └── JwtSecurityConfig.java
    └── utils/
        ├── JwtUtils.java
        └── SecurityUtils.java
```

---

## 4. Database Schema

### 4.1 ER Diagram (Text)

```
users (1) ──── (N) user_cv
user_cv (1) ──── (N) user_skill
users (1) ──── (N) study_program
study_program (1) ──── (N) study_topic
study_topic (1) ──── (N) question
question (1) ──── (N) interview_answer
users (1) ──── (N) interview_session
interview_session (1) ──── (N) interview_answer
interview_session (1) ──── (1) interview_evaluation
```

### 4.2 Table Definitions

#### user_cv
```sql
CREATE TABLE user_cv (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    file_name VARCHAR(255),
    file_path VARCHAR(500),
    raw_text TEXT,
    parsed_skills JSON,
    parsed_experience JSON,
    parsed_education JSON,
    suggested_positions JSON,
    knowledge_gaps JSON,
    target_position VARCHAR(100),
    target_level VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_user_cv_user_id (user_id)
);
```

#### user_skill
```sql
CREATE TABLE user_skill (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    cv_id BIGINT NOT NULL,
    skill_name VARCHAR(100) NOT NULL,
    proficiency_level ENUM('BEGINNER', 'INTERMEDIATE', 'ADVANCED', 'EXPERT'),
    years_experience INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (cv_id) REFERENCES user_cv(id) ON DELETE CASCADE,
    INDEX idx_user_skill_cv_id (cv_id),
    INDEX idx_user_skill_name (skill_name)
);
```

#### study_program
```sql
CREATE TABLE study_program (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    cv_id BIGINT,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    program_type ENUM('STUDY', 'INTERVIEW_PREP') NOT NULL,
    target_position VARCHAR(100),
    difficulty_level ENUM('BEGINNER', 'INTERMEDIATE', 'ADVANCED', 'MIXED'),
    status ENUM('ACTIVE', 'COMPLETED', 'ARCHIVED') DEFAULT 'ACTIVE',
    total_topics INT DEFAULT 0,
    completed_topics INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (cv_id) REFERENCES user_cv(id) ON DELETE SET NULL,
    INDEX idx_program_user_id (user_id),
    INDEX idx_program_status (status)
);
```

#### study_topic
```sql
CREATE TABLE study_topic (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    program_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    topic_order INT NOT NULL,
    difficulty ENUM('BEGINNER', 'INTERMEDIATE', 'ADVANCED') NOT NULL,
    skill_tag VARCHAR(100),
    total_questions INT DEFAULT 0,
    completed_questions INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (program_id) REFERENCES study_program(id) ON DELETE CASCADE,
    INDEX idx_topic_program_id (program_id)
);
```

#### question
```sql
CREATE TABLE question (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    topic_id BIGINT,
    skill_tag VARCHAR(100) NOT NULL,
    difficulty ENUM('BEGINNER', 'INTERMEDIATE', 'ADVANCED') NOT NULL,
    question_type ENUM('MCQ', 'SHORT_ANSWER', 'CODE', 'AUDIO') NOT NULL,
    question_text TEXT NOT NULL,
    options JSON,
    correct_answer TEXT,
    explanation TEXT,
    ai_generated BOOLEAN DEFAULT FALSE,
    source VARCHAR(50) DEFAULT 'MANUAL',
    cache_key VARCHAR(255),
    times_used INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (topic_id) REFERENCES study_topic(id) ON DELETE SET NULL,
    INDEX idx_question_skill (skill_tag),
    INDEX idx_question_difficulty (difficulty),
    INDEX idx_question_cache_key (cache_key)
);
```

#### ai_question_cache
```sql
CREATE TABLE ai_question_cache (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    cache_key VARCHAR(255) NOT NULL UNIQUE,
    position VARCHAR(100),
    skill_tag VARCHAR(100),
    difficulty ENUM('BEGINNER', 'INTERMEDIATE', 'ADVANCED'),
    question_type ENUM('MCQ', 'SHORT_ANSWER', 'CODE', 'AUDIO'),
    question_text TEXT NOT NULL,
    options JSON,
    correct_answer TEXT,
    explanation TEXT,
    times_used INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_cache_position (position),
    INDEX idx_cache_skill (skill_tag),
    INDEX idx_cache_difficulty (difficulty)
);
```

#### interview_session
```sql
CREATE TABLE interview_session (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    program_id BIGINT,
    cv_id BIGINT,
    session_type ENUM('PRACTICE', 'FULL_SIMULATION') NOT NULL,
    target_position VARCHAR(100),
    total_questions INT NOT NULL,
    question_distribution JSON,
    time_limit_minutes INT,
    status ENUM('NOT_STARTED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED') DEFAULT 'NOT_STARTED',
    started_at TIMESTAMP NULL,
    completed_at TIMESTAMP NULL,
    current_question_index INT DEFAULT 0,
    question_pool JSON, -- bổ sung (additive): danh sách question id của phiên, để GET /next không gọi AI lại
    overall_score DECIMAL(5,2),
    overall_feedback TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (program_id) REFERENCES study_program(id) ON DELETE SET NULL,
    FOREIGN KEY (cv_id) REFERENCES user_cv(id) ON DELETE SET NULL,
    INDEX idx_session_user_id (user_id),
    INDEX idx_session_status (status)
);
```

#### interview_answer
```sql
CREATE TABLE interview_answer (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    answer_order INT NOT NULL,
    user_text_answer TEXT,
    audio_file_path VARCHAR(500),
    audio_duration_seconds INT,
    audio_transcription TEXT,
    is_correct BOOLEAN,
    score DECIMAL(5,2),
    ai_feedback TEXT,
    eloquence_score DECIMAL(5,2),
    coherence_score DECIMAL(5,2),
    answer_submitted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (session_id) REFERENCES interview_session(id) ON DELETE CASCADE,
    FOREIGN KEY (question_id) REFERENCES question(id) ON DELETE CASCADE,
    INDEX idx_answer_session_id (session_id)
);
```

#### interview_evaluation
```sql
CREATE TABLE interview_evaluation (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id BIGINT NOT NULL UNIQUE,
    total_score DECIMAL(5,2),
    accuracy_score DECIMAL(5,2),
    eloquence_score DECIMAL(5,2),
    technical_depth_score DECIMAL(5,2),
    communication_score DECIMAL(5,2),
    weak_skills JSON,
    strong_skills JSON,
    improvement_suggestions TEXT,
    detailed_feedback TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (session_id) REFERENCES interview_session(id) ON DELETE CASCADE
);
```

---

## 5. Backend Implementation

### 5.1 New Enums

```java
// DifficultyLevel.java
public enum DifficultyLevel {
    BEGINNER, INTERMEDIATE, ADVANCED
}

// QuestionType.java
public enum QuestionType {
    MCQ, SHORT_ANSWER, CODE, AUDIO
}

// ProgramType.java
public enum ProgramType {
    STUDY, INTERVIEW_PREP
}

// SessionStatus.java (đã có từ Phase 1 — tái sử dụng)
public enum SessionStatus {
    NOT_STARTED, IN_PROGRESS, COMPLETED, CANCELLED
}

// SessionType.java (NEW — Phase 3)
public enum SessionType {
    PRACTICE, FULL_SIMULATION
}

// ProficiencyLevel.java
public enum ProficiencyLevel {
    BEGINNER, INTERMEDIATE, ADVANCED, EXPERT
}
```

### 5.2 Key Service Methods

#### CvService
```java
public interface CvService {
    CvParseResponse uploadCv(MultipartFile file, Long userId);
    CvParseResponse parseCvText(String cvText, Long userId);
    CvParseResponse parseCvWithAI(String cvText);
    UserCv getCvByUserId(Long userId);
    UserCv updatePosition(Long userId, String position, String level);
    List<UserSkill> getCvSkills(Long cvId);
}
```

#### StudyProgramService
```java
public interface StudyProgramService {
    StudyProgramResponse generateProgram(Long userId, GenerateProgramRequest request);
    List<StudyProgramResponse> getUserPrograms(Long userId);
    StudyProgramResponse getProgramDetail(Long programId);
    List<TopicResponse> getProgramTopics(Long programId);
    List<QuestionResponse> getTopicQuestions(Long topicId, int page, int size);
    AnswerEvaluationResponse submitStudyAnswer(Long topicId, Long questionId, String answer);
}
```

#### InterviewService
```java
public interface InterviewService {
    InterviewSessionResponse startSession(Long userId, StartInterviewRequest request);
    QuestionResponse getNextQuestion(Long sessionId);
    AnswerEvaluationResponse submitAnswer(Long sessionId, SubmitAnswerRequest request);
    EvaluationReportResponse finishSession(Long sessionId);
    List<InterviewSessionResponse> getUserHistory(Long userId);
    EvaluationReportResponse getSessionReport(Long sessionId);
}
```

#### AIQuestionService
```java
public interface AIQuestionService {
    List<Question> generateQuestions(String position, List<String> skills,
                                      Map<DifficultyLevel, Integer> distribution,
                                      QuestionType type, int count);
    List<Question> getFromCache(String position, String skill,
                                 DifficultyLevel difficulty, int count);
    Question saveToCache(Question question);
    String buildCacheKey(String position, String skill,
                         DifficultyLevel difficulty, String questionText);
}
```

#### AudioAnalysisService
```java
public interface AudioAnalysisService {
    String transcribeAudio(byte[] audioData, String format);
    AudioAnalysisResult analyzeAudio(byte[] audioData, String format);
    double calculateEloquenceScore(String transcription);
    double calculateCoherenceScore(String transcription, String expectedAnswer);
}

// AudioAnalysisResult.java (DTO)
public class AudioAnalysisResult {
    private String transcription;
    private double eloquenceScore;
    private double coherenceScore;
    private int durationSeconds;
    private String detectedLanguage;
}
```

### 5.3 Controller Endpoints

#### CvController
```java
@RestController
@RequestMapping("/api/v1/cv")
@RequiredArgsConstructor
public class CvController {

    private final CvService cvService;

    @PostMapping("/upload")
    public ApiResponse<CvParseResponse> uploadCv(
            @RequestParam("file") MultipartFile file) { ... }

    @PostMapping("/parse-text")
    public ApiResponse<CvParseResponse> parseText(
            @RequestBody CvTextRequest request) { ... }

    @GetMapping
    public ApiResponse<CvParseResponse> getCv() { ... }

    @PutMapping("/position")
    public ApiResponse<UserCv> setPosition(
            @RequestBody SetPositionRequest request) { ... }

    @GetMapping("/skills")
    public ApiResponse<List<UserSkill>> getSkills() { ... }
}
```

#### StudyProgramController
```java
@RestController
@RequestMapping("/api/v1/study")
@RequiredArgsConstructor
public class StudyProgramController {

    private final StudyProgramService studyProgramService;

    @PostMapping("/generate")
    public ApiResponse<StudyProgramResponse> generateProgram(
            @RequestBody GenerateProgramRequest request) { ... }

    @GetMapping("/programs")
    public ApiResponse<List<StudyProgramResponse>> getPrograms() { ... }

    @GetMapping("/programs/{id}")
    public ApiResponse<StudyProgramResponse> getProgramDetail(
            @PathVariable Long id) { ... }

    @GetMapping("/programs/{id}/topics")
    public ApiResponse<List<TopicResponse>> getTopics(
            @PathVariable Long id) { ... }

    @GetMapping("/topics/{topicId}/questions")
    public ApiResponse<Page<QuestionResponse>> getTopicQuestions(
            @PathVariable Long topicId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) { ... }

    @PostMapping("/topics/{topicId}/answer")
    public ApiResponse<AnswerEvaluationResponse> submitAnswer(
            @PathVariable Long topicId,
            @RequestBody SubmitAnswerRequest request) { ... }
}
```

#### InterviewController
```java
@RestController
@RequestMapping("/api/v1/interview")
@RequiredArgsConstructor
public class InterviewController {

    private final InterviewService interviewService;

    @PostMapping("/start")
    public ApiResponse<InterviewSessionResponse> startSession(
            @RequestBody StartInterviewRequest request) { ... }

    @GetMapping("/{sessionId}/next")
    public ApiResponse<QuestionResponse> getNextQuestion(
            @PathVariable Long sessionId) { ... }

    @PostMapping("/{sessionId}/answer")
    public ApiResponse<AnswerEvaluationResponse> submitAnswer(
            @PathVariable Long sessionId,
            @RequestBody SubmitAnswerRequest request) { ... }

    @PostMapping("/{sessionId}/finish")
    public ApiResponse<EvaluationReportResponse> finishSession(
            @PathVariable Long sessionId) { ... }

    @GetMapping("/history")
    public ApiResponse<List<InterviewSessionResponse>> getHistory() { ... }

    @GetMapping("/{sessionId}/report")
    public ApiResponse<EvaluationReportResponse> getReport(
            @PathVariable Long sessionId) { ... }
}
```

---

## 6. AI Integration

### 6.1 AI Service Configuration

```java
@Configuration
@ConfigurationProperties(prefix = "ai")
@Data
public class AIConfig {
    private String provider = "openai";
    private String apiKey;
    private String model = "gpt-4o-mini";
    private String evalModel = "gpt-4o-mini";
    private String whisperModel = "whisper-1";
    private int maxTokens = 4096;
    private double temperature = 0.7;
}
```

### 6.2 AI Service Implementation

> **Triển khai thực tế (đã hoàn thiện sau rà soát):**
> - `OpenAiClient` có 2 nhóm model: `chat()/chatJson()` dùng `model` (sinh CV, câu hỏi); `chatEval()/chatJsonEval()` dùng `evalModel` (đánh giá answer, audio eloquence/coherence, tổng hợp báo cáo). Tách qua `AI_MODEL`/`AI_EVAL_MODEL` trong `.env`.
> - `InterviewServiceImpl.buildQuestionPool` trộn `questionTypes` theo round-robin (skill × type), mỗi nhóm gọi `generateQuestions` 1 lần → pool đa dạng loại câu, tổng khớp `totalQuestions`.
> - `AIQuestionServiceImpl.generateQuestions`: phần trăm difficulty làm tròn còn dư được phân bổ đều → không thiếu câu so với yêu cầu.

```java
@Service
@RequiredArgsConstructor
public class AIQuestionServiceImpl implements AIQuestionService {

    private final AIConfig aiConfig;
    private final RestTemplate restTemplate;
    private final AiQuestionCacheRepository cacheRepository;

    @Override
    public List<Question> generateQuestions(String position, List<String> skills,
                                             Map<DifficultyLevel, Integer> distribution,
                                             QuestionType type, int count) {
        // 1. Check cache first
        List<Question> cached = getFromCache(position, skills, distribution, type, count);
        if (cached.size() >= count) return cached;

        // 2. Generate via AI
        String prompt = buildQuestionPrompt(position, skills, distribution, type, count);
        String aiResponse = callOpenAI(prompt);

        // 3. Parse response
        List<Question> generated = parseAIQuestions(aiResponse);

        // 4. Save to cache
        generated.forEach(this::saveToCache);

        return generated;
    }

    private String callOpenAI(String prompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(aiConfig.getApiKey());

        Map<String, Object> body = Map.of(
            "model", aiConfig.getModel(),
            "messages", List.of(Map.of("role", "user", "content", prompt)),
            "max_tokens", aiConfig.getMaxTokens(),
            "temperature", aiConfig.getTemperature()
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(
            "https://api.openai.com/v1/chat/completions", request, String.class);

        return extractContent(response.getBody());
    }
}
```

### 6.3 Prompt Templates

#### CV Analysis Prompt
```
Analyze the following CV/resume and extract structured information.

CV Content:
{cv_text}

Return a JSON object with:
{
  "skills": [
    {"name": "Java", "proficiency": "ADVANCED", "years": 3},
    {"name": "Spring Boot", "proficiency": "INTERMEDIATE", "years": 2}
  ],
  "experience": [
    {
      "company": "ABC Corp",
      "role": "Backend Developer",
      "duration": "2022-2024",
      "achievements": ["Built microservices", "Improved performance 30%"]
    }
  ],
  "education": [
    {"institution": "University XYZ", "degree": "BSc CS", "year": 2022}
  ],
  "suggested_positions": [
    "Java Backend Developer",
    "Full Stack Developer",
    "Software Engineer"
  ],
  "knowledge_gaps": [
    "Cloud Computing",
    "System Design"
  ]
}
```

#### Question Generation Prompt
```
Generate {count} interview questions for the position: {position}.

Skills to cover: {skills}
Difficulty distribution: {BEGINNER}% beginner, {INTERMEDIATE}% intermediate, {ADVANCED}% advanced
Question type: {type}

For MCQ questions, provide 4 options (A-D) with one correct answer.
For SHORT_ANSWER, provide the expected answer and explanation.
For CODE, provide a coding problem with test cases.

Return a JSON array:
[
  {
    "skill_tag": "Java",
    "difficulty": "INTERMEDIATE",
    "question_type": "MCQ",
    "question_text": "What is the difference between == and .equals() in Java?",
    "options": [
      {"key": "A", "text": "== compares references, .equals() compares values"},
      {"key": "B", "text": "== compares values, .equals() compares references"},
      {"key": "C", "text": "Both compare references"},
      {"key": "D", "text": "Both compare values"}
    ],
    "correct_answer": "A",
    "explanation": "== checks reference equality while .equals() checks value equality..."
  }
]
```

#### Answer Evaluation Prompt
```
Evaluate the following interview answer.

Question: {question_text}
Expected Answer: {correct_answer}
User's Answer: {user_answer}

Score the answer from 0 to 10 based on:
1. Accuracy (correctness of the answer)
2. Completeness (did they cover all aspects)
3. Technical depth (understanding of concepts)

Return JSON:
{
  "score": 8.5,
  "is_correct": true,
  "feedback": "Good understanding of the concept. You correctly identified...",
  "strengths": ["Clear explanation", "Used practical example"],
  "improvements": ["Could mention thread safety", "Add more details about..."]
}
```

#### Audio Evaluation Prompt
```
Analyze the following audio transcription from an interview answer.

Transcription: {transcription}
Duration: {duration} seconds
Question Context: {question_text}

Evaluate:
1. Eloquence (0-10): fluency, clarity, no filler words
2. Coherence (0-10): logical flow, structured response
3. Communication quality (0-10): professionalism, conciseness

Return JSON:
{
  "eloquence_score": 7.5,
  "coherence_score": 8.0,
  "communication_score": 7.8,
  "analysis": {
    "filler_words_count": 3,
    "avg_sentence_length": 15,
    "has_structure": true,
    "notes": "Good flow, minor hesitations at the beginning"
  }
}
```

### 6.4 Audio Processing Flow

```
User Records Audio (Web Audio API → WebM/MP3)
    ↓
Upload to Backend (POST /interview/{sessionId}/answer)
    ↓
Save audio file to disk (uploads/audio/{sessionId}/{answerId}.webm)
    ↓
Send to Whisper API → Get transcription
    ↓
Send transcription to GPT-4o-mini → Get eloquence + coherence scores
    ↓
Combine with text answer evaluation
    ↓
Save to interview_answer table
```

---

## 7. Frontend Implementation

### 7.1 New Pages

```
frontend/src/views/pages/
├── CVUploadPage.tsx
│   ├── CVUploadForm (drag & drop PDF or text input)
│   ├── CVParsedPreview (show extracted skills, experience)
│   └── PositionSelector (select target position + level)
│
├── StudyProgramPage.tsx
│   ├── ProgramList (list of user's programs)
│   ├── ProgramCard (program summary with progress)
│   └── GenerateProgramButton
│
├── StudyTopicPage.tsx
│   ├── TopicList (topics in a program with progress)
│   ├── QuestionCard (display question + answer input)
│   └── AnswerFeedback (show score + explanation after answer)
│
├── InterviewSetupPage.tsx
│   ├── InterviewConfig (number of questions, difficulty distribution)
│   ├── CVPreview (show which CV data will be used)
│   └── StartInterviewButton
│
├── InterviewSessionPage.tsx
│   ├── InterviewHeader (timer, progress bar, question count)
│   ├── QuestionDisplay (current question + options/input)
│   ├── AudioRecorder (Web Audio API recording component)
│   ├── AnswerInput (text area for answer)
│   └── NavigationButtons (previous, submit, next)
│
└── InterviewResultsPage.tsx
    ├── ScoreOverview (total score, category scores)
    ├── SkillRadarChart (radar chart for skills)
    ├── WeakStrongSection (weak + strong skills)
    ├── DetailedFeedback (per-question feedback)
    └── ImprovementSuggestions (AI suggestions)
```

### 7.2 New Components

```
frontend/src/views/components/
├── cv/
│   ├── CVUploadForm.tsx
│   │   - Drag & drop zone
│   │   - File input (PDF)
│   │   - Text area (paste CV)
│   │   - Upload button
│   │
│   ├── CVParsedPreview.tsx
│   │   - Skills list with proficiency badges
│   │   - Experience timeline
│   │   - Education section
│   │   - Suggested positions
│   │   - Knowledge gaps
│   │
│   └── PositionSelector.tsx
│       - Dropdown for position
│       - Dropdown for level (JUNIOR/SENIOR/LEAD)
│       - Confirm button
│
├── study/
│   ├── ProgramCard.tsx
│   │   - Title, description
│   │   - Progress bar (completed/total topics)
│   │   - Status badge (ACTIVE/COMPLETED)
│   │   - Click to view details
│   │
│   ├── TopicList.tsx
│   │   - List of topics with:
│   │     - Title, difficulty badge
│   │     - Skill tag
│   │     - Progress (completed/total questions)
│   │     - Start/Continue button
│   │
│   ├── QuestionCard.tsx
│   │   - Question text
│   │   - For MCQ: radio buttons A-D
│   │   - For SHORT_ANSWER: text area
│   │   - For CODE: code editor (Monaco)
│   │   - Submit button
│   │
│   └── AnswerFeedback.tsx
│       - Score badge (0-10)
│       - Correct/Incorrect indicator
│       - Explanation text
│       - User's answer vs correct answer
│       - Next question button
│
├── interview/
│   ├── InterviewHeader.tsx
│   │   - Timer (countdown if time limit)
│   │   - Progress bar (current/total questions)
│   │   - Position + difficulty info
│   │   - Pause/Exit buttons
│   │
│   ├── AudioRecorder.tsx
│   │   - Record/Stop/Play buttons
│   │   - Audio waveform visualization
│   │   - Duration display
│   │   - Upload status
│   │
│   ├── QuestionDisplay.tsx
│   │   - Question number + difficulty badge
│   │   - Question text
│   │   - Options (for MCQ)
│   │   - Code editor (for CODE questions)
│   │
│   └── EvaluationReport.tsx
│       - Overall score (big number)
│       - Category scores (accuracy, eloquence, coherence, technical)
│       - Radar chart
│       - Weak skills list
│       - Strong skills list
│       - Improvement suggestions
│       - Detailed per-question breakdown
│
└── common/
    ├── DifficultyBadge.tsx
    │   - Color-coded badge: green/yellow/red
    │   - Text: BEGINNER/INTERMEDIATE/ADVANCED
    │
    ├── SkillTag.tsx
    │   - Small tag with skill name
    │   - Optional proficiency indicator
    │
    └── ScoreChart.tsx
        - Bar chart or radar chart
        - Uses recharts or chart.js
```

### 7.3 New Services

```typescript
// services/cv.service.ts
export const cvService = {
  uploadCv: (file: File) => Promise<CvParseResponse>,
  parseText: (cvText: string) => Promise<CvParseResponse>,
  getCv: () => Promise<CvParseResponse>,
  setPosition: (position: string, level: string) => Promise<UserCv>,
  getSkills: () => Promise<UserSkill[]>,
};

// services/study.service.ts
export const studyService = {
  generateProgram: (request: GenerateProgramRequest) => Promise<StudyProgramResponse>,
  getPrograms: () => Promise<StudyProgramResponse[]>,
  getProgramDetail: (id: number) => Promise<StudyProgramResponse>,
  getTopics: (programId: number) => Promise<TopicResponse[]>,
  getTopicQuestions: (topicId: number, page: number, size: number) => Promise<Page<QuestionResponse>>,
  submitAnswer: (topicId: number, questionId: number, answer: string) => Promise<AnswerEvaluationResponse>,
};

// services/interview.service.ts
export const interviewService = {
  startSession: (request: StartInterviewRequest) => Promise<InterviewSessionResponse>,
  getNextQuestion: (sessionId: number) => Promise<QuestionResponse>,
  submitAnswer: (sessionId: number, request: SubmitAnswerRequest) => Promise<AnswerEvaluationResponse>,
  finishSession: (sessionId: number) => Promise<EvaluationReportResponse>,
  getHistory: () => Promise<InterviewSessionResponse[]>,
  getReport: (sessionId: number) => Promise<EvaluationReportResponse>,
};

// services/audio.service.ts
export const audioService = {
  uploadAudio: (sessionId: number, answerId: number, audioBlob: Blob) => Promise<void>,
  getAudioUrl: (audioPath: string) => string,
};
```

### 7.4 Custom Hooks

```typescript
// hooks/useAudioRecorder.ts
export function useAudioRecorder() {
  const [isRecording, setIsRecording] = useState(false);
  const [audioBlob, setAudioBlob] = useState<Blob | null>(null);
  const [duration, setDuration] = useState(0);

  const startRecording = () => { ... };
  const stopRecording = () => { ... };
  const playRecording = () => { ... };
  const clearRecording = () => { ... };

  return { isRecording, audioBlob, duration, startRecording, stopRecording, playRecording, clearRecording };
}

// hooks/useInterviewTimer.ts
export function useInterviewTimer(timeLimitMinutes: number) {
  const [timeRemaining, setTimeRemaining] = useState(timeLimitMinutes * 60);
  const [isRunning, setIsRunning] = useState(false);

  const start = () => { ... };
  const pause = () => { ... };
  const reset = () => { ... };

  return { timeRemaining, isRunning, start, pause, reset };
}
```

### 7.5 New Models

```typescript
// models/Cv.model.ts
export interface UserCv {
  id: number;
  userId: number;
  fileName: string;
  rawText: string;
  parsedSkills: SkillData[];
  parsedExperience: ExperienceData[];
  parsedEducation: EducationData[];
  suggestedPositions: string[];
  knowledgeGaps: string[];
  targetPosition: string;
  targetLevel: string;
}

export interface SkillData {
  name: string;
  proficiency: 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED' | 'EXPERT';
  years: number;
}

// models/StudyProgram.model.ts
export interface StudyProgram {
  id: number;
  title: string;
  description: string;
  programType: 'STUDY' | 'INTERVIEW_PREP';
  targetPosition: string;
  difficultyLevel: string;
  status: 'ACTIVE' | 'COMPLETED' | 'ARCHIVED';
  totalTopics: number;
  completedTopics: number;
  topics: StudyTopic[];
}

export interface StudyTopic {
  id: number;
  title: string;
  description: string;
  difficulty: 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED';
  skillTag: string;
  totalQuestions: number;
  completedQuestions: number;
}

// models/Interview.model.ts
export interface InterviewSession {
  id: number;
  sessionType: 'PRACTICE' | 'FULL_SIMULATION';
  targetPosition: string;
  totalQuestions: number;
  status: 'NOT_STARTED' | 'IN_PROGRESS' | 'COMPLETED';
  overallScore: number;
  evaluation: EvaluationReport;
}

export interface EvaluationReport {
  totalScore: number;
  accuracyScore: number;
  eloquenceScore: number;
  technicalDepthScore: number;
  communicationScore: number;
  weakSkills: string[];
  strongSkills: string[];
  improvementSuggestions: string;
}

// models/Question.model.ts
export interface Question {
  id: number;
  skillTag: string;
  difficulty: 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED';
  questionType: 'MCQ' | 'SHORT_ANSWER' | 'CODE' | 'AUDIO';
  questionText: string;
  options?: QuestionOption[];
}

export interface QuestionOption {
  key: string;
  text: string;
}
```

---

## 8. User Flow

### 8.1 Flow chi tiết

```
┌─────────────────────────────────────────────────────────────────┐
│                        USER JOURNEY                              │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  BƯỚC 1: DASHBOARD                                              │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  "Welcome back, {name}!"                                 │   │
│  │                                                          │   │
│  │  ┌─────────────────┐  ┌─────────────────┐               │   │
│  │  │  📄 Upload CV   │  │  🎯 Quick Start │               │   │
│  │  │  Start your     │  │  Jump into an   │               │   │
│  │  │  journey here   │  │  interview      │               │   │
│  │  └─────────────────┘  └─────────────────┘               │   │
│  │                                                          │   │
│  │  Recent Activity:                                        │   │
│  │  - Interview Session #5: Score 8.2/10                    │   │
│  │  - Study Topic "Java Basics": 80% complete               │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  BƯỚC 2: UPLOAD CV                                              │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  Upload Your CV                                          │   │
│  │                                                          │   │
│  │  ┌─────────────────────────────────────────────┐        │   │
│  │  │  📁 Drag & Drop PDF here                    │        │   │
│  │  │  or                                          │        │   │
│  │  │  📋 Paste your CV text below                │        │   │
│  │  │  ┌─────────────────────────────────────┐    │        │   │
│  │  │  │                                     │    │        │   │
│  │  │  │  [Text area for CV content]         │    │        │   │
│  │  │  │                                     │    │        │   │
│  │  │  └─────────────────────────────────────┘    │        │   │
│  │  └─────────────────────────────────────────────┘        │   │
│  │                                                          │   │
│  │  [Parse CV with AI] → Loading...                         │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  BƯỚC 3: CV PARSED RESULTS                                      │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  CV Analysis Results                                     │   │
│  │                                                          │   │
│  │  Skills Detected:                                        │   │
│  │  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐    │   │
│  │  │ Java (ADV)   │ │ Spring (INT) │ │ MySQL (INT)  │    │   │
│  │  │ 3 years      │ │ 2 years      │ │ 2 years      │    │   │
│  │  └──────────────┘ └──────────────┘ └──────────────┘    │   │
│  │                                                          │   │
│  │  Suggested Positions:                                    │   │
│  │  ○ Java Backend Developer ✓ (selected)                  │   │
│  │  ○ Full Stack Developer                                 │   │
│  │  ○ Software Engineer                                    │   │
│  │                                                          │   │
│  │  Target Level: ○ Junior ○ Mid-Level ✓ Senior            │   │
│  │                                                          │   │
│  │  Knowledge Gaps: Cloud Computing, System Design          │   │
│  │                                                          │   │
│  │  [Confirm & Continue]                                    │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  BƯỚC 4: CHOOSE PATH                                            │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  What would you like to do?                              │   │
│  │                                                          │   │
│  │  ┌─────────────────────────┐ ┌─────────────────────────┐│   │
│  │  │     📚 STUDY PATH       │ │   🎤 INTERVIEW PATH    ││   │
│  │  │                         │ │                         ││   │
│  │  │  Structured learning    │ │  Simulated interview    ││   │
│  │  │  by topic & difficulty  │ │  with AI evaluation     ││   │
│  │  │                         │ │                         ││   │
│  │  │  ✓ Bite-sized topics    │ │  ✓ Time-limited         ││   │
│  │  │  ✓ Instant feedback     │ │  ✓ Audio recording      ││   │
│  │  │  ✓ Progress tracking    │ │  ✓ Detailed report      ││   │
│  │  │                         │ │                         ││   │
│  │  │  [Start Learning]       │ │  [Start Interview]      ││   │
│  │  └─────────────────────────┘ └─────────────────────────┘│   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  BƯỚC 5A: STUDY PROGRAM                                         │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  Study Program: Java Backend Developer                   │   │
│  │                                                          │   │
│  │  Topics:                                                 │   │
│  │  ┌─────────────────────────────────────────────────┐    │   │
│  │  │ 1. Java Fundamentals        [ADVANCED] ⬤⬤⬤◯◯  │    │   │
│  │  │    Skills: Java Core                           │    │   │
│  │  │    15/20 questions completed                   │    │   │
│  │  │    [Continue]                                  │    │   │
│  │  └─────────────────────────────────────────────────┘    │   │
│  │  ┌─────────────────────────────────────────────────┐    │   │
│  │  │ 2. Spring Boot              [INTERMEDIATE] ⬤◯◯◯◯│    │   │
│  │  │    Skills: Spring Boot, REST API               │    │   │
│  │  │    0/15 questions completed                     │    │   │
│  │  │    [Start]                                     │    │   │
│  │  └─────────────────────────────────────────────────┘    │   │
│  │  ... more topics                                        │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  BƯỚC 5B: INTERVIEW SETUP                                       │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  Configure Your Interview Session                        │   │
│  │                                                          │   │
│  │  Position: Java Backend Developer (from CV)             │   │
│  │  Level: Senior                                          │   │
│  │                                                          │   │
│  │  Number of Questions: [20] (10-100)                     │   │
│  │                                                          │   │
│  │  Difficulty Distribution:                                │   │
│  │  Beginner:    [====20%====]                              │   │
│  │  Intermediate:[==========50%==========]                  │   │
│  │  Advanced:    [========30%========]                      │   │
│  │                                                          │   │
│  │  Time Limit: [60] minutes                               │   │
│  │                                                          │   │
│  │  Session Type: ○ Practice  ● Full Simulation            │   │
│  │                                                          │   │
│  │  [Start Interview]                                       │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  BƯỚC 6: INTERVIEW SESSION                                      │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  Interview Session                    ⏱ 45:32 remaining │   │
│  │  Question 5/20                      ████████░░░░ 25%    │   │
│  │  Position: Java Backend Developer                        │   │
│  │                                                          │   │
│  │  ┌─────────────────────────────────────────────────┐    │   │
│  │  │  [INTERMEDIATE] Java Core                        │    │   │
│  │  │                                                  │    │   │
│  │  │  What is the difference between HashMap and      │    │   │
│  │  │  ConcurrentHashMap in Java?                      │    │   │
│  │  │                                                  │    │   │
│  │  │  A) ConcurrentHashMap is synchronized            │    │   │
│  │  │  B) HashMap is faster                            │    │   │
│  │  │  C) Both are the same                            │    │   │
│  │  │  D) ConcurrentHashMap doesn't allow null keys    │    │   │
│  │  └─────────────────────────────────────────────────┘    │   │
│  │                                                          │   │
│  │  Your Answer:                                            │   │
│  │  ┌─────────────────────────────────────────────────┐    │   │
│  │  │  [Selected: A]                                   │    │   │
│  │  │                                                  │    │   │
│  │  │  Additional explanation (optional):              │    │   │
│  │  │  ┌─────────────────────────────────────────┐    │    │   │
│  │  │  │                                         │    │    │   │
│  │  │  │  [Text area]                            │    │    │   │
│  │  │  │                                         │    │    │   │
│  │  │  └─────────────────────────────────────────┘    │    │   │
│  │  └─────────────────────────────────────────────────┘    │   │
│  │                                                          │   │
│  │  🎤 Audio Recording:                                     │   │
│  │  ┌─────────────────────────────────────────────────┐    │   │
│  │  │  🔴 [Record] ⏸ [Pause] ▶ [Play] 🔄 [Retake]    │    │   │
│  │  │  Duration: 00:45                                 │    │   │
│  │  │  ████████████░░░░░░░░░░░░░░░░ (waveform)        │    │   │
│  │  └─────────────────────────────────────────────────┘    │   │
│  │                                                          │   │
│  │  [← Previous]  [Submit & Next →]                         │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  BƯỚC 7: RESULTS                                                │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  Interview Results                                       │   │
│  │                                                          │   │
│  │  Overall Score: 8.2/10  ⭐⭐⭐⭐                          │   │
│  │                                                          │   │
│  │  Category Scores:                                        │   │
│  │  ┌─────────────────────────────────────────────┐        │   │
│  │  │  Accuracy:        8.5/10  ████████████░░    │        │   │
│  │  │  Eloquence:       7.8/10  ██████████░░░░    │        │   │
│  │  │  Technical Depth:  8.0/10  ███████████░░░    │        │   │
│  │  │  Communication:   8.2/10  ████████████░░    │        │   │
│  │  └─────────────────────────────────────────────┘        │   │
│  │                                                          │   │
│  │  Strong Skills: Java, Spring Boot, REST API              │   │
│  │  Weak Skills: System Design, Database Optimization       │   │
│  │                                                          │   │
│  │  Suggestions:                                            │   │
│  │  "Focus on learning about distributed systems and        │   │
│  │   database indexing strategies. Practice explaining      │   │
│  │   your answers more clearly."                            │   │
│  │                                                          │   │
│  │  [View Detailed Report]  [Try Again]  [Study Weak Areas]│   │
│  └─────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

---

## 9. Implementation Phases

### Phase 1: Foundation (Tuần 1-2)

| Task          | Chi tiết                                         | Output                             |
|---------------|--------------------------------------------------|------------------------------------| 
| AI Config     | Setup OpenAI API key management, AIConfig class  | `AIConfig.java`, `application.yml` |
| New Enums     | DifficultyLevel, QuestionType, ProgramType, etc. | 5 enum files                       |
| CV Entity     | UserCv + UserSkill entities + repositories       | 4 files                            |
| CV Upload     | Upload PDF + text, Apache PDFBox extract text    | `CvController`, `CvService`        |
| AI CV Parsing | Call GPT-4o-mini to parse CV, save results        | `AIQuestionService`                |
| DB Migration  | SQL for new tables                               | `V6_interview_tables.sql`          |

### Phase 2: Question System (Tuần 2-3)

| Task | Chi tiết | Output |
|------|----------|--------|
| Question Entity | Question + AiQuestionCache entities | 2 entities + repos |
| AI Question Gen | Generate questions via GPT-4o-mini | `AIQuestionService` |
| Cache System | Cache key generation, dedup logic | Cache service |
| Study Program Entity | StudyProgram + StudyTopic entities | 2 entities + repos |
| Study API | Generate program, get topics, get questions | `StudyProgramController` |

### Phase 3: Interview Session (Tuần 3-4) ✅ Backend hoàn thành

| Task | Chi tiết | Output | Trạng thái |
|------|----------|--------|-----------|
| Interview Entity | InterviewSession + InterviewAnswer + Evaluation | 3 entities + repos | ✅ |
| Session Flow | Start → Get Next → Submit → Finish | `InterviewService` | ✅ |
| Text Evaluation | AI evaluate text answers | Evaluation logic (tái dùng `evaluateAnswer`) | ✅ |
| Audio Processing | Whisper transcription + analysis | `AudioAnalysisService` | ✅ |
| Overall Evaluation | Generate overall report from all answers | `finishSession` → `interview_evaluation` | ✅ |

> **Chi tiết triển khai đợt 1 (chưa có OpenAI key, test nhánh lỗi):**
> - Entities: `InterviewSession` (+ cột `question_pool JSON`), `InterviewAnswer`, `InterviewEvaluation`.
> - `InterviewServiceImpl.startSession`: yêu cầu CV → build pool theo distribution (`interview.difficulty-distribution`) → persist câu hỏi → lưu session `NOT_STARTED`. Thiếu key → **502** + rollback (không tạo session).
> - `getNextQuestion`: trả câu hỏi theo `currentQuestionIndex`, tự chuyển `NOT_STARTED → IN_PROGRESS`; hết pool → 404 "gọi /finish".
> - `submitAnswer`: verify câu hỏi thuộc pool → AI `evaluateAnswer` (text) + (optional) Whisper + AI eloquence/coherence → lưu `interview_answer`.
> - `finishSession`: gộp Q&A → AI overall report → lưu `interview_evaluation` + session `COMPLETED`; AI fail → **502**, giữ `IN_PROGRESS` retry.
> - Migration: `V8__interview_session_tables.sql` (Hibernate tự tạo bảng, file là tài liệu tham chiếu).

### Phase 4: Frontend (Tuần 4-5) ✅ Hoàn thành

| Task | Chi tiết | Output |
|------|----------|--------|
| CV Upload Page | Drag & drop, text input, parsed preview | `CVUploadPage.tsx` |
| Study Pages | Program list, topic list, question cards | 3 pages |
| Interview Setup | Config form, CV preview | `InterviewSetupPage.tsx` |
| Interview Session | Question display, audio recorder, answer input | `InterviewSessionPage.tsx` |
| Results Page | Score charts, feedback, suggestions | `InterviewResultsPage.tsx` |
| API Services | cv.service, study.service, interview.service | 3 service files |

> **Đã implement** (bản Phase 4 thực tế): thư mục `frontend/src/models/{Cv,Study,Interview}.model.ts`; `services/{cv,study,interview}.service.ts`; `controllers/use{Cv,Study,StudyTopic,Interview}Controller.ts`; `views/pages/{CVUploadPage, StudyProgramPage, StudyTopicPage, InterviewSetupPage, InterviewSessionPage, InterviewResultsPage}.tsx`; `views/components/{AppLayout, AudioRecorder}.tsx`; dependency `recharts ^2.12` (RadarChart + BarChart). Build thành công (CRA+TS), image Docker mới `springboot-frontend`. Smoke qua nginx proxy: login + `/api/v1/cv | study/programs | interview/history` đều OK; `/interview/start` thiếu `OPENAI_API_KEY` → **502** message đúng.

### Phase 5: Polish & Testing (Tuần 5-6)

| Task | Chi tiết | Output |
|------|----------|--------|
| Error Handling | Graceful AI failures, file upload errors | Error boundaries |
| Loading States | Skeleton loaders, progress indicators | UX polish |
| Edge Cases | Empty CV, no questions generated, audio fail | Edge case handling |
| Testing | Unit tests for services, integration tests | Test files |
| Performance | Cache optimization, query optimization | Performance tuning |

---

## 10. Technical Decisions

| Decision | Choice | Lý do | Alternative |
|----------|--------|-------|-------------|
| **PDF Parsing** | Apache PDFBox | Mature, no extra dependency | iText (paid), pdf.js (frontend) |
| **AI Service** | OpenAI GPT-4o-mini | Cost-effective, đủ chất lượng cho generate + evaluate | Gemini 1.5 Flash, Claude 3.5 |
| **Audio Transcription** | OpenAI Whisper | Consistent ecosystem | Google Speech-to-Text |
| **Audio Storage** | Local filesystem | Simple, start small | AWS S3, MinIO |
| **Question Cache** | MySQL + hash | No extra infra needed | Redis (faster but extra setup) |
| **Frontend Charts** | Recharts | React-native, easy | Chart.js, D3.js |
| **Code Editor** | Monaco Editor | VS Code in browser | CodeMirror, Ace |
| **Audio Recording** | Web Audio API | Native browser API | RecordRTC, MediaRecorder |

---

## 11. Configuration

### 11.1 application.yml

```yaml
# AI Configuration
ai:
  provider: openai
  api-key: ${OPENAI_API_KEY:}
  model: ${AI_MODEL:gpt-4o-mini}
  eval-model: ${AI_EVAL_MODEL:gpt-4o-mini}
  whisper-model: whisper-1
  max-tokens: 4096
  temperature: 0.7
  base-url: https://api.openai.com/v1

# File Storage
storage:
  cv-upload-dir: ${CV_UPLOAD_DIR:./uploads/cv}
  audio-upload-dir: ${AUDIO_UPLOAD_DIR:./uploads/audio}
  max-file-size: 10MB
  allowed-file-types: application/pdf,text/plain

# Interview Defaults
interview:
  default-question-count: 20
  max-question-count: 100
  min-question-count: 5
  default-time-limit-minutes: 60
  difficulty-distribution:
    beginner: 20
    intermediate: 50
    advanced: 30

# Study Program
study:
  max-topics-per-program: 10
  questions-per-topic: 15
  auto-generate-on-cv-upload: true
```

### 11.2 pom.xml Dependencies (New)

```xml
<!-- PDF Parsing -->
<dependency>
    <groupId>org.apache.pdfbox</groupId>
    <artifactId>pdfbox</artifactId>
    <version>3.0.1</version>
</dependency>

<!-- AI HTTP Client -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>

<!-- JSON Processing -->
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
</dependency>
```

---

## 12. API Reference

### 12.1 CV APIs

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|--------------|----------|
| POST | `/api/v1/cv/upload` | Upload CV file | `multipart/form-data` (file) | `CvParseResponse` |
| POST | `/api/v1/cv/parse-text` | Parse CV from text | `{cvText: string}` | `CvParseResponse` |
| GET | `/api/v1/cv` | Get current CV | - | `CvParseResponse` |
| PUT | `/api/v1/cv/position` | Set target position | `{position, level}` | `UserCv` |
| GET | `/api/v1/cv/skills` | Get CV skills | - | `List<UserSkill>` |

### 12.2 Study Program APIs

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|--------------|----------|
| POST | `/api/v1/study/generate` | Generate study program | `GenerateProgramRequest` | `StudyProgramResponse` |
| GET | `/api/v1/study/programs` | List user's programs | - | `List<StudyProgramResponse>` |
| GET | `/api/v1/study/programs/{id}` | Get program detail | - | `StudyProgramResponse` |
| GET | `/api/v1/study/programs/{id}/topics` | Get program topics | - | `List<TopicResponse>` |
| GET | `/api/v1/study/topics/{id}/questions` | Get topic questions | `?page=0&size=10` | `Page<QuestionResponse>` |
| POST | `/api/v1/study/topics/{id}/answer` | Submit study answer | `SubmitAnswerRequest` | `AnswerEvaluationResponse` |

### 12.3 Interview APIs

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|--------------|----------|
| POST | `/api/v1/interview/start` | Start interview session | `StartInterviewRequest` | `InterviewSessionResponse` |
| GET | `/api/v1/interview/{sessionId}/next` | Get next question | - | `QuestionResponse` |
| POST | `/api/v1/interview/{sessionId}/answer` | Submit answer | `SubmitAnswerRequest` | `AnswerEvaluationResponse` |
| POST | `/api/v1/interview/{sessionId}/finish` | Finish & evaluate | - | `EvaluationReportResponse` |
| GET | `/api/v1/interview/history` | List past sessions | - | `List<InterviewSessionResponse>` |
| GET | `/api/v1/interview/{sessionId}/report` | Get detailed report | - | `EvaluationReportResponse` |

> ✅ 6 endpoint trên đã implement trong `InterviewController` / `InterviewServiceImpl` (backend). Request body thực tế dùng **`SubmitInterviewAnswerRequest`** (thêm `audioBase64` + `audioFileName`), DTO schema bổ sung bên dưới.

### 12.4 DTOs

#### CvParseResponse
```json
{
  "cvId": 1,
  "fileName": "resume.pdf",
  "skills": [
    {"name": "Java", "proficiency": "ADVANCED", "years": 3}
  ],
  "experience": [
    {
      "company": "ABC Corp",
      "role": "Backend Developer",
      "duration": "2022-2024",
      "achievements": ["Built microservices"]
    }
  ],
  "education": [
    {"institution": "University XYZ", "degree": "BSc CS", "year": 2022}
  ],
  "suggestedPositions": ["Java Backend Developer", "Full Stack Developer"],
  "knowledgeGaps": ["Cloud Computing", "System Design"]
}
```

#### GenerateProgramRequest
```json
{
  "programType": "STUDY",
  "targetPosition": "Java Backend Developer",
  "targetLevel": "SENIOR",
  "focusSkills": ["Java", "Spring Boot", "MySQL"],
  "questionCount": 50
}
```

#### StartInterviewRequest
```json
{
  "sessionType": "FULL_SIMULATION",
  "totalQuestions": 20,
  "timeLimitMinutes": 60,
  "questionDistribution": {
    "BEGINNER": 20,
    "INTERMEDIATE": 50,
    "ADVANCED": 30
  },
  "questionTypes": ["MCQ", "SHORT_ANSWER", "CODE"]
}
```

#### SubmitAnswerRequest
```json
{
  "questionId": 1,
  "textAnswer": "ConcurrentHashMap is synchronized...",
  "selectedOption": "A",
  "audioFile": "base64_encoded_audio_or_path"
}
```

#### EvaluationReportResponse
```json
{
  "sessionId": 1,
  "totalScore": 8.2,
  "accuracyScore": 8.5,
  "eloquenceScore": 7.8,
  "technicalDepthScore": 8.0,
  "communicationScore": 8.2,
  "strongSkills": ["Java", "Spring Boot", "REST API"],
  "weakSkills": ["System Design", "Database Optimization"],
  "improvementSuggestions": "Focus on distributed systems...",
  "detailedFeedback": "Overall strong performance...",
  "answers": [
    {
      "questionId": 1,
      "score": 9.0,
      "feedback": "Excellent answer...",
      "isCorrect": true
    }
  ]
}
```

#### SubmitInterviewAnswerRequest (Phase 3 — đã implement)
```json
{
  "questionId": 1,
  "textAnswer": "ConcurrentHashMap is synchronized...",
  "selectedOption": "A",
  "audioBase64": "base64_encoded_audio",
  "audioFileName": "answer.webm"
}
```

#### InterviewSessionResponse (Phase 3 — đã implement)
```json
{
  "id": 1,
  "sessionType": "FULL_SIMULATION",
  "targetPosition": "Java Backend Developer",
  "totalQuestions": 20,
  "timeLimitMinutes": 60,
  "status": "NOT_STARTED",
  "currentQuestionIndex": 0,
  "startedAt": null,
  "completedAt": null,
  "overallScore": null,
  "overallFeedback": null,
  "questionDistribution": {"BEGINNER": 20, "INTERMEDIATE": 50, "ADVANCED": 30}
}
```

#### AudioAnalysisResult (Phase 3 — đã implement)
```json
{
  "transcription": "transcribed text",
  "eloquenceScore": 7.5,
  "coherenceScore": 8.0,
  "durationSeconds": 45,
  "detectedLanguage": null,
  "audioFilePath": "C:/.../uploads/audio/answer_123.webm"
}
```

---

## Appendix A: Environment Variables

```bash
# .env additions
OPENAI_API_KEY=sk-your-api-key-here
CV_UPLOAD_DIR=./uploads/cv
AUDIO_UPLOAD_DIR=./uploads/audio
```

## Appendix B: Testing Strategy

| Test Type | Scope | Tool |
|-----------|-------|------|
| Unit Test | Service methods, AI prompt building | JUnit 5 + Mockito |
| Integration Test | API endpoints, database operations | Spring Boot Test + H2 |
| E2E Test | Full user flow (CV → Interview → Results) | Cypress or Playwright |
| AI Mock Test | AI service responses | WireMock to mock OpenAI |

## Appendix C: Security Considerations

1. **API Key Management:** Store OpenAI key in env vars, never in code
2. **File Upload:** Validate file type, limit size, scan for malware
3. **Audio Files:** Store outside web root, serve via signed URLs
4. **Rate Limiting:** Limit AI API calls per user (cost control)
5. **Data Privacy:** CV data is sensitive, encrypt at rest if needed
6. **JWT:** Existing auth system covers all new endpoints

---

## Appendix D: 5W1H — Phase 1 (Foundation)

### What — Làm gì?
Xây dựng **nền tảng (Foundation)** cho AI Interview Platform:
- **AI Config**: `AIConfig.java` + `application.yml` — OpenAI, model `GPT-4o-mini` (generate câu hỏi + đánh giá), `whisper-1` (voice→text), `tts-1` (text→voice), cho phép override bằng biến môi trường.
- **Enums**: `DifficultyLevel`, `QuestionType`, `ProgramType`, `SessionStatus`, `ProficiencyLevel`.
- **CV Module**: entity `UserCv`/`UserSkill` + repositories, upload CV (PDF/TXT), trích text bằng Apache PDFBox, gọi AI parse ra JSON (skills/experience/education/knowledge gaps), lưu DB, API `/api/v1/cv`.
- **DB Migration**: `V6__interview_tables.sql`.

### Why — Tại sao phải làm?
- Là nền tảng cho **Phase 2 (Study Program)** và **Phase 3 (Interview)**: CV giúp cá nhân hóa câu hỏi + đánh giá.
- Team **chưa có OpenAI API key** → thiết kế *"kết nối trước, key sau"*: app vẫn khởi động, thiếu key chỉ trả 502 với message rõ ràng.
- Chọn `GPT-4o-mini`: **tiết kiệm chi phí** (~1/20 so với gpt-4o), đủ chất lượng cho generate + đánh giá tự luận.

### Who — Ai làm / cho ai?
- **Thực hiện**: AI agent — code + smoke test; user duyệt kiến trúc và lựa chọn mô hình.
- **Review/Hướng dẫn**: đồng đội (bản guide chạy project từng được soạn riêng).
- **Người dùng cuối**: admin (seed sẵn `admin@aiinterview.com`) + học viên upload CV (sau khi FE nối xong).

### When — Khi nào?
- Theo plan: **Tuần 1–2**. Đã hoàn thành code + verify (compile/package `BUILD SUCCESS`, login OK, `GET /api/v1/cv`, `parse-text` trả 502 có kiểm soát).
- Chưa commit (18 file, branch `interview_program`). Test AI thật khi có key: điền `OPENAI_API_KEY` vào `.env` → `docker compose up -d backend`.

### Where — Ở đâu?
- **Branch**: `interview_program` · **Backend**: `backend/` · **Package**: `com.aiinterview.platform.*` · **Migration**: `backend/src/main/resources/db/migration/V6__interview_tables.sql` · **Tài liệu**: file này.

### How — Thực hiện thế nào?
1. Parse CV: Upload (PDF/TXT) → **PDFBox** trích text → **GPT-4o-mini** (system prompt + JSON mode, retry tối đa 2 lần) → lưu `user_cv`/`user_skill`.
2. Robustness: strip markdown fences, parse Jackson, validate giới hạn (skills≤30, experience≤20, education≤10), lỗi → `AIServiceException` → HTTP **502** kèm message rõ ràng.
3. Bảo mật: `@PreAuthorize("isAuthenticated()")` trên `CvController`; user xác thực qua JWT.
4. Tùy chỉnh model: `AI_MODEL` / `AI_EVAL_MODEL` / `OPENAI_API_KEY` qua `.env` — không cần sửa code.

---

## Appendix E: 5W1H — Phase 3 (Interview Session)

### What — Làm gì?
Xây **backend phiên phỏng vấn mô phỏng** (mock interview): bắt đầu phiên → lần lượt câu hỏi → trả lời text (+ audio tùy chọn) → AI đánh giá từng câu → AI đánh giá tổng quan. Gồm:
- **Entities/Repos**: `InterviewSession` (+ `question_pool JSON`), `InterviewAnswer`, `InterviewEvaluation` + 3 repository.
- **Services**: `InterviewService` (start/next/submit/finish/history/report), `AudioAnalysisService` (lưu file + Whisper + eloquence/coherence).
- **Controller**: `InterviewController` `/api/v1/interview` (6 endpoint).
- **AI**: câu hỏi cache-first + AI (`AIQuestionService`), đánh giá text (`evaluateAnswer`), transcription (Whisper), overall report (GPT-4o-mini, JSON mode).
- **Migration**: `V8__interview_session_tables.sql`.

### Why — Tại sao phải làm?
- Hoàn thiện vòng đời "**CV → Study → Interview → Results**"; là tính năng cốt lõi **P0** (phiên phỏng vấn giúp luyện trả lời + đo lường tiến bộ).
- Team **chưa có OpenAI key** → tiếp tục triết lý *"kết nối trước, key sau"*: app vẫn chạy, thiếu key chỉ trả **502** message rõ; test trọn vòng khi điền `OPENAI_API_KEY`.
- Tái dùng tối đa Phase 2 (cache câu hỏi, `evaluateAnswer`, DTO, pattern ownership) → ít code duplicate, nhất quán.

### When — Khi nào?
- Theo plan: **Tuần 3–4**. Đợt 1 (backend) đã xong + verify: `mvn compile` OK; smoke test không-key: `start` không CV → **400**, `start` có CV → **502** và **rollback** (history = 0), `next`/`report` sai session → **404**, `history` → rỗng; 3 bảng `interview_*` đã tạo.
- Còn lại **làm sau (đợt 2)**: test trọn vòng với key thật, frontend (Phase 4), design **Phase 5 Polish & Testing**.

### Where — Ở đâu?
- **Branch**: `interview_program` · **Backend**: `backend/` · **Package**: `com.aiinterview.platform.*` · **Migration**: `backend/src/main/resources/db/migration/V8__interview_session_tables.sql`.
- File chính: `InterviewController.java`, `InterviewService(+Impl)`, `AudioAnalysisService(+Impl)`, entity/repo `Interview*`, `OpenAiClient.transcribeAudio`.

### Who — Ai làm / cho ai?
- **Thực hiện**: AI agent — code + compile + smoke test; user duyệt (đã chốt 3 quyết định thiết kế: cache-first+AI, audio đủ bộ khung, finish giữ `IN_PROGRESS` khi AI fail).
- **Người dùng cuối**: học viên có CV — luyện phỏng vấn, nhận điểm + feedback (text/audio).

### How — Thực hiện thế nào?
1. **Start**: cần CV → build pool: mỗi skill (≤6) gọi `AIQuestionService.generateQuestions` theo distribution (BEGINNER/INTERMEDIATE/ADVANCED) → persist câu hỏi → lưu `question_pool` + session `NOT_STARTED`.
2. **Next**: đọc `question_pool[current_question_index]`, tăng index, tự chuyển `IN_PROGRESS`; hết pool → 404 nhắc `/finish`.
3. **Answer**: verify câu hỏi thuộc pool → `evaluateAnswer` (text) → (optional) base64 → lưu file → Whisper transcribe → AI chấm eloquence/coherence → lưu `interview_answer` (answerOrder tăng dần).
4. **Finish**: gộp các Q&A + score → prompt overall → `chatJson` → lưu `interview_evaluation` + session `COMPLETED` (started/completed timestamps); AI fail → **502**, rollback giữ `IN_PROGRESS`.
5. **An toàn**: mọi endpoint `isAuthenticated()` + `findByIdAndUserId` + verify pool → cross-user trả **404**; audio ≤ 8MB, ext whitelist, lưu ngoài web root.
6. **Model**: `whisper-1` (transcribe), GPT-4o-mini (generate/eval/overall); override qua `.env` (`AI_MODEL`, `AI_EVAL_MODEL`).

---

## Appendix F: 5W1H — Phase 4 (Frontend)

### What — Làm gì?
Xây toàn bộ giao diện React (CRA + TypeScript) nối vào các API Phase 1-3: upload/parse CV, học theo chương trình (generate → topics → question → đánh giá), phỏng vấn (setup → session → báo cáo Recharts). Không dùng UI framework — inline styles theo chuẩn DashboardPage có sẵn.

### Why — Tại sao phải làm?
Backend đã đủ 6 API interview, nhưng chưa có UI để dùng. Lộ trình học: trang CV cung cấp `targetPosition` → Study generate theo vị trí → Interview hỏi theo `focusSkills` từ CV. Báo cáo trực quan (Radar/Bar) giúp học viên thấy điểm mạnh/yếu.

### When — Khi nào?
Sau khi Phase 1-3 backend verified (branch `interview_program`). Không cần `OPENAI_API_KEY` để phát triển — UI test được qua nhánh lỗi (502 message); key thật chỉ cần khi chạy AI thành công.

### Where — Ở đâu?
`frontend/src/`:
- `models/{Cv,Study,Interview}.model.ts` — type map đúng DTO backend (`PagedResponse<T>` tái dùng từ `User.model`).
- `services/{cv,study,interview}.service.ts` — gọi `http.service.ts` (JWT interceptor + refresh), endpoint `/api/v1/*`.
- `controllers/use{Cv,Study,StudyTopic,Interview}Controller.ts` — hook: state + loading + error; trang KHÔNG gọi axios trực tiếp.
- `views/components/{AppLayout,AudioRecorder}.tsx` — nav toàn app + ghi âm `MediaRecorder` → base64 webm.
- `views/pages/` 6 trang (routes trong `App.tsx` bọc `ProtectedRoute` → `AppLayout`).

### Who — Ai làm / cho ai?
- Thực hiện: AI agent. Duyệt: user. Học viên (user) dùng; Admin thêm mục "Quản lý user".
- Image: `frontend` (node:18**alpine** build → nginx serve) — lưu ý `npm ci` đòi `package-lock.json` **đồng bộ npm 10** (node:18), đã regenerate lockfile bằng `npx npm@10`.

### How — Thực hiện thế nào?
1. **Models** → **Services** → **Controllers (hooks)** — làm nền trước, mỗi trang chỉ "view + gọi hook".
2. **CVUploadPage**: dropzone/pdf/paste-text → `uploadCv/parseText` → preview skills/exp/edu/gaps + `position` selector (suggestedPositions).
3. **StudyProgramPage**: list + generate card (`programType`, `targetPosition`, gợi ý từ CV). **StudyTopicPage**: steps topics → câu hỏi paged → trả lời → `AnswerEvaluation` (điểm/feedback/đáp án mẫu).
4. **InterviewSetupPage**: config (sessionType, số câu, thời gian, types, focusSkills) + lịch sử. **InterviewSessionPage**: bấm bắt đầu → `/next` từng câu → nộp (text bắt buộc + audio tùy chọn) → khi đủ câu → `/finish`. **InterviewResultsPage**: `/report` → Radar (4 khía cạnh) + Bar (từng câu) + strong/weak + suggestions.
5. **Luồng dữ liệu chuẩn** (1 ví dụ học pattern): `Page → hook → service → http.service(JWT) → nginx /api → Controller → Service → OpenAi/DB → ApiResponse{success,message,data} → hook setState → render`.

---

**Hết tài liệu.**
