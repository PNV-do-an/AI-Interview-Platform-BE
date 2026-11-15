import React, { useEffect } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useStudyTopicController } from '../../controllers/useStudyTopicController';
import {
  AnswerEvaluation,
  DIFFICULTY_LABELS,
  StudyQuestion,
  progressPercent,
} from '../../models/Study.model';

const Chip: React.FC<{ className?: string; children: React.ReactNode }> = ({
  className = '',
  children,
}) => (
  <span
    className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full font-label-sm text-label-sm ${className}`}
  >
    {children}
  </span>
);

const EvaluationPanel: React.FC<{ evaluation: AnswerEvaluation }> = ({ evaluation }) => {
  const strengths = evaluation.strengths ?? [];
  const improvements = evaluation.improvements ?? [];
  const score = typeof evaluation.score === 'number' ? Math.round(evaluation.score * 10) / 10 : 0;

  return (
    <div className="rounded-lg bg-surface-container-low p-4 flex flex-col gap-3">
      <div className="flex flex-wrap items-center gap-2">
        <Chip
          className={
            evaluation.isCorrect
              ? 'bg-[#dcfce7] text-[#166534]'
              : 'bg-error-container text-on-error-container'
          }
        >
          <span className="material-symbols-outlined text-xs leading-none">
            {evaluation.isCorrect ? 'check_circle' : 'cancel'}
          </span>
          {evaluation.isCorrect ? 'Đạt yêu cầu' : 'Chưa đạt'}
        </Chip>
        <Chip className="bg-primary-fixed text-on-primary-fixed">Điểm {score}</Chip>
      </div>

      {evaluation.feedback && (
        <p className="font-body-md text-body-md text-on-surface whitespace-pre-wrap">
          {evaluation.feedback}
        </p>
      )}

      {strengths.length > 0 && (
        <div>
          <div className="font-label-md text-label-md text-on-surface-variant uppercase tracking-wider mb-1">
            Điểm mạnh
          </div>
          <ul className="list-disc pl-5 font-body-sm text-body-sm text-on-surface">
            {strengths.map((item, i) => (
              <li key={i}>{item}</li>
            ))}
          </ul>
        </div>
      )}

      {improvements.length > 0 && (
        <div>
          <div className="font-label-md text-label-md text-on-surface-variant uppercase tracking-wider mb-1">
            Cần cải thiện
          </div>
          <ul className="list-disc pl-5 font-body-sm text-body-sm text-on-surface">
            {improvements.map((item, i) => (
              <li key={i}>{item}</li>
            ))}
          </ul>
        </div>
      )}

      {evaluation.modelAnswer && (
        <details className="font-body-sm text-body-sm text-on-surface-variant">
          <summary className="cursor-pointer font-label-md text-label-md text-primary">
            Xem câu trả lời mẫu
          </summary>
          <p className="mt-2 whitespace-pre-wrap">{evaluation.modelAnswer}</p>
        </details>
      )}
    </div>
  );
};

interface QuestionCardProps {
  question: StudyQuestion;
  index: number;
  answer: string;
  submitting: boolean;
  onAnswerChange: (value: string) => void;
  onSubmit: () => void;
  evaluation?: AnswerEvaluation;
}

const QuestionCard: React.FC<QuestionCardProps> = ({
  question,
  index,
  answer,
  submitting,
  onAnswerChange,
  onSubmit,
  evaluation,
}) => (
  <div className="bg-surface-container-lowest rounded-xl p-5 border border-outline-variant flex flex-col gap-3">
    <div className="flex items-start gap-3">
      <span className="w-7 h-7 flex-shrink-0 rounded-full bg-surface-container text-on-surface-variant flex items-center justify-center font-label-md text-label-md">
        {index}
      </span>
      <div className="min-w-0 flex flex-col gap-2">
        <p className="font-body-lg text-body-lg text-on-surface whitespace-pre-wrap">
          {question.questionText}
        </p>
        <div className="flex flex-wrap gap-2">
          {question.skillTag && (
            <Chip className="bg-secondary-container text-on-secondary">{question.skillTag}</Chip>
          )}
          <Chip className="bg-surface-container text-on-surface-variant">
            {DIFFICULTY_LABELS[question.difficulty]}
          </Chip>
        </div>
      </div>
    </div>

    {question.options.length > 0 && (
      <ul className="flex flex-col gap-1 pl-10">
        {question.options.map((option) => (
          <li key={option.key} className="font-body-md text-body-md text-on-surface-variant">
            <span className="font-label-md text-label-md text-on-surface">{option.key}.</span>{' '}
            {option.text}
          </li>
        ))}
      </ul>
    )}

    <label className="flex flex-col gap-1">
      <span className="font-label-md text-label-md text-on-surface-variant">Câu trả lời của bạn</span>
      <textarea
        rows={3}
        value={answer}
        onChange={(event) => onAnswerChange(event.target.value)}
        placeholder="Trình bày ngắn gọn, nêu ý chính và ví dụ nếu cần..."
        className="px-3 py-2 rounded-lg border border-outline-variant bg-surface-container-lowest font-body-md text-body-md text-on-surface focus:outline-none focus:border-primary resize-y"
      />
    </label>

    <div className="flex items-center justify-between gap-3">
      <span className="font-body-sm text-body-sm text-on-surface-variant">
        {evaluation ? 'Bạn có thể nộp lại để AI chấm lại.' : 'AI sẽ chấm điểm và góp ý ngay sau khi nộp.'}
      </span>
      <button
        type="button"
        onClick={onSubmit}
        disabled={submitting}
        className="inline-flex items-center gap-2 px-4 py-2 rounded-lg bg-primary text-on-primary font-label-lg text-label-lg hover:bg-primary-container transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
      >
        <span className="material-symbols-outlined text-base leading-none">send</span>
        {submitting ? 'Đang chấm...' : 'Nộp câu trả lời'}
      </button>
    </div>

    {evaluation && <EvaluationPanel evaluation={evaluation} />}
  </div>
);

const StudyTopicPage: React.FC = () => {
  const params = useParams<{ programId?: string; topicId?: string }>();
  const parsedProgramId = params.programId ? Number(params.programId) : NaN;
  const parsedTopicId = params.topicId ? Number(params.topicId) : NaN;
  const programId = Number.isFinite(parsedProgramId) && parsedProgramId > 0 ? parsedProgramId : null;
  const topicId = Number.isFinite(parsedTopicId) && parsedTopicId > 0 ? parsedTopicId : null;

  const ctrl = useStudyTopicController(programId, topicId);
  const {
    topic,
    questions,
    pageIndex,
    totalPages,
    totalElements,
    pageSize,
    answers,
    evaluations,
    loading,
    submittingId,
    error,
    resolveTopic,
    loadQuestions,
    setAnswer,
    submitAnswer,
  } = ctrl;

  useEffect(() => {
    resolveTopic();
  }, [resolveTopic]);

  useEffect(() => {
    loadQuestions(0);
  }, [loadQuestions]);

  const backTo = programId ? `/study/programs/${programId}` : '/study';
  const percent = progressPercent(topic?.completedQuestions, topic?.totalQuestions);

  if (topicId === null) {
    return (
      <div className="bg-surface-container-lowest rounded-xl p-6 border border-outline-variant">
        <p className="font-body-md text-body-md text-on-surface-variant">Chủ đề không hợp lệ.</p>
        <Link
          to="/study"
          className="inline-flex items-center gap-1 mt-3 font-label-lg text-label-lg text-primary hover:underline"
        >
          <span className="material-symbols-outlined text-base leading-none">arrow_back</span>
          Về danh sách chương trình
        </Link>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-6">
      <Link
        to={backTo}
        className="inline-flex items-center gap-1 w-fit font-label-lg text-label-lg text-on-surface-variant hover:text-primary"
      >
        <span className="material-symbols-outlined text-base leading-none">arrow_back</span>
        Về chương trình
      </Link>

      {error && (
        <div className="flex items-start gap-2 p-4 rounded-lg bg-error-container text-on-error-container font-body-md text-body-md">
          <span className="material-symbols-outlined text-lg leading-none">error</span>
          <span>{error}</span>
        </div>
      )}

      <div className="bg-surface-container-lowest rounded-xl p-6 border border-outline-variant flex flex-col gap-4">
        <div>
          <h2 className="font-headline-md text-headline-md text-on-surface">
            {topic ? topic.title : `Chủ đề #${topicId}`}
          </h2>
          {topic?.description && (
            <p className="font-body-md text-body-md text-on-surface-variant mt-1">{topic.description}</p>
          )}
        </div>

        <div className="flex flex-wrap gap-2">
          {topic?.skillTag && (
            <Chip className="bg-secondary-container text-on-secondary">{topic.skillTag}</Chip>
          )}
          {topic && (
            <Chip className="bg-surface-container text-on-surface-variant">
              {DIFFICULTY_LABELS[topic.difficulty]}
            </Chip>
          )}
          <Chip className="bg-surface-container text-on-surface-variant">
            {totalElements} câu hỏi
          </Chip>
        </div>

        {topic && (
          <div>
            <div className="flex items-center justify-between font-label-sm text-label-sm text-on-surface-variant mb-1">
              <span>
                Đã trả lời {topic.completedQuestions}/{topic.totalQuestions}
              </span>
              <span>{percent}%</span>
            </div>
            <div className="w-full h-2 rounded-full bg-surface-container overflow-hidden">
              <div
                className="h-full rounded-full bg-primary transition-all"
                style={{ width: `${percent}%` }}
              />
            </div>
          </div>
        )}
      </div>

      {loading ? (
        <div className="bg-surface-container-lowest rounded-xl p-6 border border-outline-variant">
          <p className="font-body-md text-body-md text-on-surface-variant">Đang tải câu hỏi...</p>
        </div>
      ) : questions.length === 0 ? (
        <div className="bg-surface-container-lowest rounded-xl p-8 border border-outline-variant flex flex-col items-center text-center gap-2">
          <span className="material-symbols-outlined text-4xl text-outline leading-none">
            quiz
          </span>
          <p className="font-title-md text-title-md text-on-surface">Chủ đề này chưa có câu hỏi</p>
          <p className="font-body-sm text-body-sm text-on-surface-variant">
            Hãy tạo lại chương trình để AI sinh câu hỏi cho chủ đề này.
          </p>
        </div>
      ) : (
        <div className="flex flex-col gap-4">
          {questions.map((question, i) => {
            const evaluation = evaluations[question.id];
            return (
              <QuestionCard
                key={question.id}
                question={question}
                index={pageIndex * pageSize + i + 1}
                answer={answers[question.id] || ''}
                submitting={submittingId === question.id}
                onAnswerChange={(value) => setAnswer(question.id, value)}
                onSubmit={() => submitAnswer(question.id)}
                evaluation={evaluation}
              />
            );
          })}
        </div>
      )}

      {totalPages > 1 && (
        <div className="flex items-center justify-between">
          <button
            type="button"
            disabled={loading || pageIndex <= 0}
            onClick={() => loadQuestions(pageIndex - 1)}
            className="inline-flex items-center gap-1 px-3 py-2 rounded-lg border border-outline-variant font-label-lg text-label-lg text-on-surface-variant hover:border-primary hover:text-primary transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
          >
            <span className="material-symbols-outlined text-base leading-none">chevron_left</span>
            Trang trước
          </button>

          <span className="font-label-md text-label-md text-on-surface-variant">
            Trang {pageIndex + 1}/{totalPages}
          </span>

          <button
            type="button"
            disabled={loading || pageIndex + 1 >= totalPages}
            onClick={() => loadQuestions(pageIndex + 1)}
            className="inline-flex items-center gap-1 px-3 py-2 rounded-lg border border-outline-variant font-label-lg text-label-lg text-on-surface-variant hover:border-primary hover:text-primary transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
          >
            Trang sau
            <span className="material-symbols-outlined text-base leading-none">chevron_right</span>
          </button>
        </div>
      )}

      <p className="font-body-sm text-body-sm text-on-surface-variant">
        Tiến độ chủ đề tăng mỗi lần bạn nộp câu trả lời. Chương trình tự chuyển sang trạng thái hoàn
        thành khi mọi chủ đề đều đạt 100%.
      </p>
    </div>
  );
};

export default StudyTopicPage;
