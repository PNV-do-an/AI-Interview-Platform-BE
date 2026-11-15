import httpService from './http.service';
import { ApiResponse } from '../models/Api.model';
import {
  AnswerEvaluation,
  GenerateProgramRequest,
  PageResponse,
  StudyProgram,
  StudyQuestion,
  StudyTopic,
  SubmitStudyAnswerRequest,
} from '../models/Study.model';

// AI generation and answer evaluation call OpenAI server-side, so allow a long wait.
const AI_TIMEOUT_MS = 180000;

const StudyService = {
  // POST /api/v1/study/generate — build a program from the parsed CV
  generateProgram: (req: GenerateProgramRequest) =>
    httpService.post<ApiResponse<StudyProgram>>('/api/v1/study/generate', req, {
      timeout: AI_TIMEOUT_MS,
    }),

  // GET /api/v1/study/programs — programs of the signed-in user
  getPrograms: () => httpService.get<ApiResponse<StudyProgram[]>>('/api/v1/study/programs'),

  // GET /api/v1/study/programs/{id} — one program (ownership checked server-side)
  getProgram: (programId: number) =>
    httpService.get<ApiResponse<StudyProgram>>(`/api/v1/study/programs/${programId}`),

  // GET /api/v1/study/programs/{id}/topics — ordered topics of a program
  getTopics: (programId: number) =>
    httpService.get<ApiResponse<StudyTopic[]>>(`/api/v1/study/programs/${programId}/topics`),

  // GET /api/v1/study/topics/{topicId}/questions — paged questions of a topic
  getQuestions: (topicId: number, page = 0, size = 5) =>
    httpService.get<ApiResponse<PageResponse<StudyQuestion>>>(
      `/api/v1/study/topics/${topicId}/questions`,
      { params: { page, size } },
    ),

  // POST /api/v1/study/topics/{topicId}/answer — AI feedback + progress increment
  submitAnswer: (topicId: number, req: SubmitStudyAnswerRequest) =>
    httpService.post<ApiResponse<AnswerEvaluation>>(
      `/api/v1/study/topics/${topicId}/answer`,
      req,
      { timeout: AI_TIMEOUT_MS },
    ),
};

export default StudyService;
