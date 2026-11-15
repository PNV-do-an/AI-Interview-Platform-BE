// Study Program models & DTOs matching backend /api/v1/study

export type ProgramType = 'STUDY' | 'INTERVIEW_PREP';
export type ProgramStatus = 'ACTIVE' | 'COMPLETED' | 'ARCHIVED';
export type DifficultyLevel = 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED' | 'MIXED';
export type QuestionType = 'MCQ' | 'SHORT_ANSWER' | 'CODE' | 'AUDIO';

export interface StudyProgram {
  id: number;
  title: string;
  description?: string | null;
  programType: ProgramType;
  targetPosition?: string | null;
  difficultyLevel: DifficultyLevel;
  status: ProgramStatus;
  totalTopics: number;
  completedTopics: number;
  createdAt?: string | null;
  completedAt?: string | null;
}

export interface StudyTopic {
  id: number;
  title: string;
  description?: string | null;
  topicOrder: number;
  difficulty: DifficultyLevel;
  skillTag?: string | null;
  totalQuestions: number;
  completedQuestions: number;
}

export interface QuestionOption {
  key: string;
  text: string;
}

export interface StudyQuestion {
  id: number;
  skillTag?: string | null;
  difficulty: DifficultyLevel;
  questionType: QuestionType;
  questionText: string;
  options: QuestionOption[];
}

export interface GenerateProgramRequest {
  programType?: ProgramType;
  targetPosition?: string;
  questionsPerTopic?: number;
}

export interface SubmitStudyAnswerRequest {
  questionId: number;
  answer: string;
}

export interface AnswerEvaluation {
  score: number;
  isCorrect: boolean;
  feedback?: string | null;
  modelAnswer?: string | null;
  explanation?: string | null;
  strengths: string[];
  improvements: string[];
}

/** Shape returned by Spring Data when the backend wraps a Page in ApiResponse. */
export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export const DIFFICULTY_LABELS: Record<DifficultyLevel, string> = {
  BEGINNER: 'Cơ bản',
  INTERMEDIATE: 'Trung cấp',
  ADVANCED: 'Nâng cao',
  MIXED: 'Tổng hợp',
};

export const STATUS_LABELS: Record<ProgramStatus, string> = {
  ACTIVE: 'Đang học',
  COMPLETED: 'Hoàn thành',
  ARCHIVED: 'Đã lưu trữ',
};

export const STATUS_CLASSES: Record<ProgramStatus, string> = {
  ACTIVE: 'bg-primary-fixed text-on-primary-fixed',
  COMPLETED: 'bg-[#dcfce7] text-[#16a34a]',
  ARCHIVED: 'bg-surface-container text-on-surface-variant',
};

export const PROGRAM_TYPE_LABELS: Record<ProgramType, string> = {
  STUDY: 'Ôn tập kỹ năng',
  INTERVIEW_PREP: 'Luyện phỏng vấn',
};

const toDate = (value: string | number[] | null | undefined): Date | null => {
  if (!value) return null;
  if (Array.isArray(value)) {
    const [year, month, day, hour = 0, minute = 0, second = 0] = value;
    const fromParts = new Date(year, month - 1, day, hour, minute, second);
    return Number.isNaN(fromParts.getTime()) ? null : fromParts;
  }
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? null : parsed;
};

export const formatDate = (value: string | number[] | null | undefined): string => {
  const date = toDate(value);
  if (!date) return '—';
  return date.toLocaleDateString('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  });
};

export const progressPercent = (
  done: number | null | undefined,
  total: number | null | undefined,
): number => {
  if (!total || total <= 0) return 0;
  const safeDone = done && done > 0 ? done : 0;
  return Math.min(100, Math.round((safeDone / total) * 100));
};
