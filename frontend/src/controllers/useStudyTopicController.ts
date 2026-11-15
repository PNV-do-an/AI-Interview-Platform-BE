import { useCallback, useState } from 'react';
import StudyService from '../services/study.service';
import { AnswerEvaluation, StudyQuestion, StudyTopic } from '../models/Study.model';

const PAGE_SIZE = 5;

const messageOf = (err: any, fallback: string): string =>
  err?.response?.data?.message || fallback;

/**
 * Drives the topic practice screen: resolve the topic header, page through its
 * questions, and submit one answer at a time for AI evaluation.
 */
export const useStudyTopicController = (
  programId: number | null,
  topicId: number | null,
) => {
  const [topic, setTopic] = useState<StudyTopic | null>(null);
  const [questions, setQuestions] = useState<StudyQuestion[]>([]);
  const [pageIndex, setPageIndex] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [answers, setAnswers] = useState<Record<number, string>>({});
  const [evaluations, setEvaluations] = useState<Record<number, AnswerEvaluation>>({});
  const [loading, setLoading] = useState(false);
  const [submittingId, setSubmittingId] = useState<number | null>(null);
  const [error, setError] = useState('');

  // There is no "get one topic" endpoint, so pick it out of the program's list.
  const resolveTopic = useCallback(async () => {
    if (programId === null || topicId === null) {
      setTopic(null);
      return;
    }
    try {
      const res = await StudyService.getTopics(programId);
      const list = res.data.data ?? [];
      setTopic(list.find((item) => item.id === topicId) ?? null);
    } catch {
      setTopic(null);
    }
  }, [programId, topicId]);

  const loadQuestions = useCallback(
    async (page: number) => {
      if (topicId === null) return;
      setLoading(true);
      setError('');
      try {
        const res = await StudyService.getQuestions(topicId, page, PAGE_SIZE);
        const data = res.data.data;
        const content = data?.content ?? [];
        setQuestions(content);
        setPageIndex(data?.number ?? page);
        setTotalPages(data?.totalPages ?? 0);
        setTotalElements(data?.totalElements ?? content.length);
      } catch (err: any) {
        setQuestions([]);
        setTotalPages(0);
        setTotalElements(0);
        setError(messageOf(err, 'Không thể tải câu hỏi của chủ đề'));
      } finally {
        setLoading(false);
      }
    },
    [topicId],
  );

  const setAnswer = useCallback((questionId: number, value: string) => {
    setAnswers((prev) => ({ ...prev, [questionId]: value }));
  }, []);

  const submitAnswer = useCallback(
    async (questionId: number) => {
      if (topicId === null) return;
      const answer = (answers[questionId] || '').trim();
      if (!answer) {
        setError('Hãy nhập câu trả lời trước khi nộp.');
        return;
      }

      setSubmittingId(questionId);
      setError('');
      try {
        const res = await StudyService.submitAnswer(topicId, { questionId, answer });
        const evaluation = res.data.data;
        if (evaluation) {
          setEvaluations((prev) => ({ ...prev, [questionId]: evaluation }));
        }
        // Submitting increments completed_questions on the server.
        await resolveTopic();
      } catch (err: any) {
        setError(messageOf(err, 'Không thể đánh giá câu trả lời. Vui lòng thử lại.'));
      } finally {
        setSubmittingId(null);
      }
    },
    [answers, topicId, resolveTopic],
  );

  return {
    topic,
    questions,
    pageIndex,
    totalPages,
    totalElements,
    pageSize: PAGE_SIZE,
    answers,
    evaluations,
    loading,
    submittingId,
    error,
    resolveTopic,
    loadQuestions,
    setAnswer,
    submitAnswer,
  };
};
