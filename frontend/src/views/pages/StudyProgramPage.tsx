import React, { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useStudyController } from '../../controllers/useStudyController';
import {
  DIFFICULTY_LABELS,
  PROGRAM_TYPE_LABELS,
  ProgramType,
  STATUS_CLASSES,
  STATUS_LABELS,
  StudyProgram,
  StudyTopic,
  formatDate,
  progressPercent,
} from '../../models/Study.model';

const DEFAULT_QUESTIONS_PER_TOPIC = 10;

const ProgressBar: React.FC<{ percent: number }> = ({ percent }) => (
  <div className="w-full h-2 rounded-full bg-surface-container overflow-hidden">
    <div
      className="h-full rounded-full bg-primary transition-all"
      style={{ width: `${percent}%` }}
    />
  </div>
);

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

const ProgramCard: React.FC<{ program: StudyProgram }> = ({ program }) => {
  const percent = progressPercent(program.completedTopics, program.totalTopics);

  return (
    <Link
      to={`/study/programs/${program.id}`}
      className="bg-surface-container-lowest rounded-xl p-5 border border-outline-variant hover:border-primary hover:shadow-sm transition-all flex flex-col gap-3"
    >
      <div className="flex items-start justify-between gap-3">
        <h3 className="font-title-md text-title-md text-on-surface leading-snug">{program.title}</h3>
        <Chip className={STATUS_CLASSES[program.status]}>{STATUS_LABELS[program.status]}</Chip>
      </div>

      {program.description && (
        <p className="font-body-sm text-body-sm text-on-surface-variant line-clamp-2">
          {program.description}
        </p>
      )}

      <div className="flex flex-wrap gap-2">
        {program.targetPosition && (
          <Chip className="bg-secondary-container text-on-secondary">
            <span className="material-symbols-outlined text-xs leading-none">military_tech</span>
            {program.targetPosition}
          </Chip>
        )}
        <Chip className="bg-surface-container text-on-surface-variant">
          {PROGRAM_TYPE_LABELS[program.programType]}
        </Chip>
      </div>

      <div className="mt-auto">
        <div className="flex items-center justify-between font-label-sm text-label-sm text-on-surface-variant mb-1">
          <span>
            {program.completedTopics}/{program.totalTopics} chủ đề
          </span>
          <span>{percent}%</span>
        </div>
        <ProgressBar percent={percent} />
      </div>

      <div className="flex items-center justify-between font-label-sm text-label-sm text-on-surface-variant">
        <span>Tạo ngày {formatDate(program.createdAt)}</span>
        <span className="material-symbols-outlined text-base leading-none text-primary">
          arrow_forward
        </span>
      </div>
    </Link>
  );
};

const TopicCard: React.FC<{ topic: StudyTopic; programId: number }> = ({ topic, programId }) => {
  const percent = progressPercent(topic.completedQuestions, topic.totalQuestions);

  return (
    <div className="bg-surface-container-lowest rounded-xl p-5 border border-outline-variant flex flex-col gap-3">
      <div className="flex items-start gap-3">
        <span className="w-8 h-8 flex-shrink-0 rounded-full bg-primary-fixed text-on-primary-fixed flex items-center justify-center font-label-lg text-label-lg">
          {topic.topicOrder}
        </span>
        <div className="min-w-0">
          <h3 className="font-title-md text-title-md text-on-surface leading-snug">{topic.title}</h3>
          {topic.description && (
            <p className="font-body-sm text-body-sm text-on-surface-variant mt-1">
              {topic.description}
            </p>
          )}
        </div>
      </div>

      <div className="flex flex-wrap gap-2">
        {topic.skillTag && (
          <Chip className="bg-secondary-container text-on-secondary">
            {topic.skillTag}
          </Chip>
        )}
        <Chip className="bg-surface-container text-on-surface-variant">
          {DIFFICULTY_LABELS[topic.difficulty]}
        </Chip>
        <Chip className="bg-surface-container text-on-surface-variant">
          {topic.totalQuestions} câu hỏi
        </Chip>
      </div>

      <div>
        <div className="flex items-center justify-between font-label-sm text-label-sm text-on-surface-variant mb-1">
          <span>
            Đã trả lời {topic.completedQuestions}/{topic.totalQuestions}
          </span>
          <span>{percent}%</span>
        </div>
        <ProgressBar percent={percent} />
      </div>

      <Link
        to={`/study/programs/${programId}/topics/${topic.id}`}
        className="inline-flex items-center justify-center gap-2 px-4 py-2 rounded-lg bg-primary text-on-primary font-label-lg text-label-lg hover:bg-primary-container transition-colors"
      >
        <span className="material-symbols-outlined text-base leading-none">play_arrow</span>
        Luyện tập
      </Link>
    </div>
  );
};

const StudyProgramPage: React.FC = () => {
  const params = useParams<{ programId?: string }>();
  const navigate = useNavigate();
  const parsedId = params.programId ? Number(params.programId) : NaN;
  const programId = Number.isFinite(parsedId) && parsedId > 0 ? parsedId : null;
  const isDetailMode = Boolean(params.programId);

  const ctrl = useStudyController();
  const {
    programs,
    program,
    topics,
    hasCv,
    cvTargetPosition,
    loadingProfile,
    loadingPrograms,
    loadingDetail,
    generating,
    error,
    successMsg,
    fetchCvProfile,
    fetchPrograms,
    fetchProgramDetail,
    generateProgram,
  } = ctrl;

  const [showForm, setShowForm] = useState(false);
  const [position, setPosition] = useState<string | null>(null);
  const [programType, setProgramType] = useState<ProgramType>('STUDY');
  const [questionsPerTopic, setQuestionsPerTopic] = useState(DEFAULT_QUESTIONS_PER_TOPIC);

  // Fall back to the CV target position until the user types something.
  const positionValue = position !== null ? position : cvTargetPosition;

  useEffect(() => {
    if (programId === null) return;
    fetchProgramDetail(programId);
  }, [programId, fetchProgramDetail]);

  useEffect(() => {
    if (isDetailMode) return;
    fetchPrograms();
    fetchCvProfile();
  }, [isDetailMode, fetchPrograms, fetchCvProfile]);

  const handleGenerate = async (event: React.FormEvent) => {
    event.preventDefault();
    if (generating) return;

    const created = await generateProgram({
      programType,
      questionsPerTopic,
      ...(positionValue.trim() ? { targetPosition: positionValue.trim() } : {}),
    });

    if (created) {
      setShowForm(false);
      navigate(`/study/programs/${created.id}`);
    }
  };

  const banners = (
    <>
      {error && (
        <div className="flex items-start gap-2 p-4 rounded-lg bg-error-container text-on-error-container font-body-md text-body-md">
          <span className="material-symbols-outlined text-lg leading-none">error</span>
          <span>{error}</span>
        </div>
      )}
      {successMsg && (
        <div className="flex items-start gap-2 p-4 rounded-lg bg-[#dcfce7] text-[#166534] font-body-md text-body-md">
          <span className="material-symbols-outlined text-lg leading-none">check_circle</span>
          <span>{successMsg}</span>
        </div>
      )}
    </>
  );

  /* ---------------------------------------------------------------- detail */

  if (isDetailMode) {
    if (programId === null) {
      return (
        <div className="bg-surface-container-lowest rounded-xl p-6 border border-outline-variant">
          <p className="font-body-md text-body-md text-on-surface-variant">
            Chương trình ôn tập không hợp lệ.
          </p>
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
          to="/study"
          className="inline-flex items-center gap-1 w-fit font-label-lg text-label-lg text-on-surface-variant hover:text-primary"
        >
          <span className="material-symbols-outlined text-base leading-none">arrow_back</span>
          Danh sách chương trình
        </Link>

        {banners}

        {loadingDetail && (
          <div className="bg-surface-container-lowest rounded-xl p-6 border border-outline-variant">
            <p className="font-body-md text-body-md text-on-surface-variant">Đang tải chương trình...</p>
          </div>
        )}

        {!loadingDetail && !program && !error && (
          <div className="bg-surface-container-lowest rounded-xl p-6 border border-outline-variant">
            <p className="font-body-md text-body-md text-on-surface-variant">
              Không tìm thấy chương trình ôn tập này.
            </p>
          </div>
        )}

        {program && (
          <>
            <div className="bg-surface-container-lowest rounded-xl p-6 border border-outline-variant flex flex-col gap-4">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <h2 className="font-headline-md text-headline-md text-on-surface">{program.title}</h2>
                  {program.description && (
                    <p className="font-body-md text-body-md text-on-surface-variant mt-1">
                      {program.description}
                    </p>
                  )}
                </div>
                <Chip className={STATUS_CLASSES[program.status]}>{STATUS_LABELS[program.status]}</Chip>
              </div>

              <div className="flex flex-wrap gap-2">
                {program.targetPosition && (
                  <Chip className="bg-secondary-container text-on-secondary">
                    <span className="material-symbols-outlined text-xs leading-none">military_tech</span>
                    {program.targetPosition}
                  </Chip>
                )}
                <Chip className="bg-surface-container text-on-surface-variant">
                  {PROGRAM_TYPE_LABELS[program.programType]}
                </Chip>
                <Chip className="bg-surface-container text-on-surface-variant">
                  {DIFFICULTY_LABELS[program.difficultyLevel]}
                </Chip>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                <div className="p-4 rounded-lg bg-surface-container-low">
                  <div className="font-label-sm text-label-sm text-on-surface-variant uppercase tracking-wider mb-1">
                    Chủ đề
                  </div>
                  <div className="font-headline-sm text-headline-sm text-on-surface">
                    {program.completedTopics}/{program.totalTopics}
                  </div>
                </div>
                <div className="p-4 rounded-lg bg-surface-container-low">
                  <div className="font-label-sm text-label-sm text-on-surface-variant uppercase tracking-wider mb-1">
                    Tiến độ
                  </div>
                  <div className="font-headline-sm text-headline-sm text-on-surface">
                    {progressPercent(program.completedTopics, program.totalTopics)}%
                  </div>
                </div>
                <div className="p-4 rounded-lg bg-surface-container-low">
                  <div className="font-label-sm text-label-sm text-on-surface-variant uppercase tracking-wider mb-1">
                    {program.status === 'COMPLETED' ? 'Hoàn thành ngày' : 'Ngày tạo'}
                  </div>
                  <div className="font-title-md text-title-md text-on-surface truncate">
                    {formatDate(program.status === 'COMPLETED' ? program.completedAt : program.createdAt)}
                  </div>
                </div>
              </div>

              <ProgressBar percent={progressPercent(program.completedTopics, program.totalTopics)} />
            </div>

            <div>
              <div className="flex items-center justify-between mb-3">
                <h3 className="font-title-md text-title-md text-on-surface">Lộ trình chủ đề</h3>
                <span className="font-label-md text-label-md text-on-surface-variant">
                  {topics.length} chủ đề
                </span>
              </div>

              {topics.length === 0 ? (
                <div className="bg-surface-container-lowest rounded-xl p-6 border border-outline-variant">
                  <p className="font-body-md text-body-md text-on-surface-variant">
                    Chương trình này chưa có chủ đề nào.
                  </p>
                </div>
              ) : (
                <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
                  {topics.map((topic) => (
                    <TopicCard key={topic.id} topic={topic} programId={program.id} />
                  ))}
                </div>
              )}
            </div>
          </>
        )}
      </div>
    );
  }

  /* ------------------------------------------------------------------ list */

  return (
    <div className="flex flex-col gap-6">
      <div className="bg-surface-container-lowest rounded-xl p-6 border border-outline-variant flex flex-wrap items-center justify-between gap-4">
        <div>
          <h2 className="font-headline-md text-headline-md text-on-surface">Chương trình ôn tập</h2>
          <p className="font-body-md text-body-md text-on-surface-variant mt-1">
            Lộ trình học cá nhân hoá, sinh từ kỹ năng và khoảng trống kiến thức trong CV của bạn.
          </p>
        </div>
        <button
          type="button"
          onClick={() => setShowForm((open) => !open)}
          className="inline-flex items-center gap-2 px-4 py-2.5 rounded-lg bg-primary text-on-primary font-label-lg text-label-lg hover:bg-primary-container transition-colors"
        >
          <span className="material-symbols-outlined text-base leading-none">
            {showForm ? 'close' : 'auto_awesome'}
          </span>
          {showForm ? 'Đóng' : 'Tạo chương trình mới'}
        </button>
      </div>

      {banners}

      {showForm && (
        <form
          onSubmit={handleGenerate}
          className="bg-surface-container-lowest rounded-xl p-6 border border-outline-variant flex flex-col gap-4"
        >
          <h3 className="font-title-md text-title-md text-on-surface">Tạo chương trình từ CV</h3>

          {!loadingProfile && !hasCv && (
            <div className="flex items-start gap-3 p-4 rounded-lg bg-error-container text-on-error-container">
              <span className="material-symbols-outlined text-lg leading-none">info</span>
              <div>
                <p className="font-body-md text-body-md">
                  Bạn cần tải CV lên và để hệ thống phân tích trước khi tạo chương trình ôn tập.
                </p>
                <Link to="/cv" className="inline-block mt-2 font-label-lg text-label-lg underline">
                  Đi tới trang CV
                </Link>
              </div>
            </div>
          )}

          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <label className="flex flex-col gap-1">
              <span className="font-label-md text-label-md text-on-surface-variant">Vị trí mục tiêu</span>
              <input
                type="text"
                value={positionValue}
                onChange={(event) => setPosition(event.target.value)}
                placeholder="VD: Java Backend Developer"
                className="px-3 py-2 rounded-lg border border-outline-variant bg-surface-container-lowest font-body-md text-body-md text-on-surface focus:outline-none focus:border-primary"
              />
            </label>

            <label className="flex flex-col gap-1">
              <span className="font-label-md text-label-md text-on-surface-variant">Loại chương trình</span>
              <select
                value={programType}
                onChange={(event) => setProgramType(event.target.value as ProgramType)}
                className="px-3 py-2 rounded-lg border border-outline-variant bg-surface-container-lowest font-body-md text-body-md text-on-surface focus:outline-none focus:border-primary"
              >
                <option value="STUDY">Ôn tập kỹ năng</option>
                <option value="INTERVIEW_PREP">Luyện phỏng vấn</option>
              </select>
            </label>

            <label className="flex flex-col gap-1">
              <span className="font-label-md text-label-md text-on-surface-variant">
                Số câu hỏi mỗi chủ đề
              </span>
              <input
                type="number"
                min={1}
                max={50}
                value={questionsPerTopic}
                onChange={(event) => setQuestionsPerTopic(Number(event.target.value) || 1)}
                className="px-3 py-2 rounded-lg border border-outline-variant bg-surface-container-lowest font-body-md text-body-md text-on-surface focus:outline-none focus:border-primary"
              />
            </label>
          </div>

          <p className="font-body-sm text-body-sm text-on-surface-variant">
            Để trống vị trí mục tiêu thì hệ thống dùng vị trí đã lưu trong CV. Mỗi chủ đề tối đa 50 câu.
          </p>

          {generating && (
            <div className="flex items-start gap-3 p-4 rounded-lg bg-primary-fixed text-on-primary-fixed">
              <span className="material-symbols-outlined text-lg leading-none animate-spin">
                progress_activity
              </span>
              <p className="font-body-md text-body-md">
                AI đang sinh câu hỏi cho từng chủ đề, quá trình này có thể mất 1–2 phút. Vui lòng không
                đóng trang.
              </p>
            </div>
          )}

          <div className="flex justify-end">
            <button
              type="submit"
              disabled={generating || loadingProfile || !hasCv}
              className="inline-flex items-center gap-2 px-4 py-2.5 rounded-lg bg-primary text-on-primary font-label-lg text-label-lg hover:bg-primary-container transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
            >
              <span className="material-symbols-outlined text-base leading-none">bolt</span>
              {generating ? 'Đang tạo...' : 'Tạo chương trình'}
            </button>
          </div>
        </form>
      )}

      {loadingPrograms ? (
        <div className="bg-surface-container-lowest rounded-xl p-6 border border-outline-variant">
          <p className="font-body-md text-body-md text-on-surface-variant">
            Đang tải danh sách chương trình...
          </p>
        </div>
      ) : programs.length === 0 ? (
        <div className="bg-surface-container-lowest rounded-xl p-8 border border-outline-variant flex flex-col items-center text-center gap-3">
          <span className="material-symbols-outlined text-4xl text-outline leading-none">school</span>
          <div>
            <p className="font-title-md text-title-md text-on-surface">Chưa có chương trình nào</p>
            <p className="font-body-sm text-body-sm text-on-surface-variant mt-1">
              Tạo chương trình đầu tiên để bắt đầu ôn tập theo khoảng trống kiến thức trong CV.
            </p>
          </div>
          <button
            type="button"
            onClick={() => setShowForm(true)}
            className="inline-flex items-center gap-2 px-4 py-2 rounded-lg border border-primary text-primary font-label-lg text-label-lg hover:bg-primary-fixed transition-colors"
          >
            <span className="material-symbols-outlined text-base leading-none">add</span>
            Tạo chương trình
          </button>
        </div>
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
          {programs.map((item) => (
            <ProgramCard key={item.id} program={item} />
          ))}
        </div>
      )}
    </div>
  );
};

export default StudyProgramPage;
