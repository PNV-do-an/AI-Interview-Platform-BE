import { useCallback, useState } from 'react';
import StudyService from '../services/study.service';
import CvService from '../services/cv.service';
import { GenerateProgramRequest, StudyProgram, StudyTopic } from '../models/Study.model';

const messageOf = (err: any, fallback: string): string =>
  err?.response?.data?.message || fallback;

export const useStudyController = () => {
  const [programs, setPrograms] = useState<StudyProgram[]>([]);
  const [program, setProgram] = useState<StudyProgram | null>(null);
  const [topics, setTopics] = useState<StudyTopic[]>([]);
  const [hasCv, setHasCv] = useState(false);
  const [cvTargetPosition, setCvTargetPosition] = useState('');
  const [loadingProfile, setLoadingProfile] = useState(true);
  const [loadingPrograms, setLoadingPrograms] = useState(false);
  const [loadingDetail, setLoadingDetail] = useState(false);
  const [generating, setGenerating] = useState(false);
  const [error, setError] = useState('');
  const [successMsg, setSuccessMsg] = useState('');

  const clearMessages = useCallback(() => {
    setError('');
    setSuccessMsg('');
  }, []);

  // A program can only be generated from an already parsed CV, so read that first.
  const fetchCvProfile = useCallback(async () => {
    setLoadingProfile(true);
    try {
      const res = await CvService.getCv();
      const cv = res.data.data;
      setHasCv(Boolean(cv?.cvId));
      setCvTargetPosition(cv?.targetPosition || '');
    } catch {
      // 404 means no CV yet — a normal state for a fresh account.
      setHasCv(false);
      setCvTargetPosition('');
    } finally {
      setLoadingProfile(false);
    }
  }, []);

  const fetchPrograms = useCallback(async () => {
    setLoadingPrograms(true);
    setError('');
    try {
      const res = await StudyService.getPrograms();
      setPrograms(res.data.data ?? []);
    } catch (err: any) {
      setPrograms([]);
      setError(messageOf(err, 'Không thể tải danh sách chương trình ôn tập'));
    } finally {
      setLoadingPrograms(false);
    }
  }, []);

  const fetchProgramDetail = useCallback(async (programId: number) => {
    setLoadingDetail(true);
    setError('');
    try {
      const [programRes, topicsRes] = await Promise.all([
        StudyService.getProgram(programId),
        StudyService.getTopics(programId),
      ]);
      setProgram(programRes.data.data ?? null);
      setTopics(topicsRes.data.data ?? []);
    } catch (err: any) {
      setProgram(null);
      setTopics([]);
      setError(messageOf(err, 'Không thể tải chương trình ôn tập'));
    } finally {
      setLoadingDetail(false);
    }
  }, []);

  const generateProgram = useCallback(
    async (req: GenerateProgramRequest): Promise<StudyProgram | null> => {
      setGenerating(true);
      clearMessages();
      try {
        const res = await StudyService.generateProgram(req);
        const created = res.data.data ?? null;
        if (created) {
          setSuccessMsg(res.data.message || 'Đã tạo chương trình ôn tập');
        }
        return created;
      } catch (err: any) {
        setError(messageOf(err, 'Không thể tạo chương trình ôn tập. Vui lòng thử lại.'));
        return null;
      } finally {
        setGenerating(false);
      }
    },
    [clearMessages],
  );

  return {
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
    clearMessages,
    fetchCvProfile,
    fetchPrograms,
    fetchProgramDetail,
    generateProgram,
  };
};
