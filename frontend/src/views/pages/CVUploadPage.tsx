import React, { useEffect, useState } from 'react';
import { useAuth } from '../../context/AuthContext';
import { cvService } from '../../services/cv.service';
import { CvParseResponse, SkillData } from '../../models/Cv.model';
import { CVUploadZone } from '../components/CVUploadZone';
import { SkillsMatrix } from '../components/SkillsMatrix';
import { KnowledgeGapCard } from '../components/KnowledgeGapCard';
import { PositionFitCard } from '../components/PositionFitCard';
import { ExperienceTimeline } from '../components/ExperienceTimeline';

const CVUploadPage: React.FC = () => {
  const { user } = useAuth();
  const [cv, setCv] = useState<CvParseResponse | null>(null);
  const [skills, setSkills] = useState<SkillData[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<'file' | 'text'>('file');
  const [rawText, setRawText] = useState('');
  const [position, setPosition] = useState('');
  const [level, setLevel] = useState('senior');
  const [showOverwriteModal, setShowOverwriteModal] = useState(false);

  // Load CV data on mount
  useEffect(() => {
    loadCvData();
  }, []);

  const loadCvData = async () => {
    try {
      setLoading(true);
      const cvData = await cvService.getCv();
      if (cvData && Object.keys(cvData).length > 0) {
        setCv(cvData);
        setPosition(cvData.targetPosition || '');
        setLevel(cvData.targetLevel || 'senior');
        
        // Load skills if available
        const skillsData = await cvService.getCvSkills();
        if (skillsData) {
          setSkills(skillsData as SkillData[]);
        }
      }
    } catch (err) {
      console.error('Failed to load CV:', err);
    } finally {
      setLoading(false);
    }
  };

  const handleFileUpload = async (file: File) => {
    if (cv) {
      setShowOverwriteModal(true);
      return;
    }
    await performFileUpload(file);
  };

  const performFileUpload = async (file: File) => {
    try {
      setLoading(true);
      setError(null);
      const result = await cvService.uploadCv(file);
      setCv(result);
      setSuccess(`CV uploaded successfully: ${file.name}`);
      setTimeout(() => setSuccess(null), 5000);
      await loadCvData();
    } catch (err: any) {
      setError(err.message || 'Failed to upload CV');
    } finally {
      setLoading(false);
      setShowOverwriteModal(false);
    }
  };

  const handleTextParse = async () => {
    if (!rawText.trim()) {
      setError('Please paste resume text before parsing');
      return;
    }

    try {
      setLoading(true);
      setError(null);
      const result = await cvService.parseCvText(rawText);
      setCv(result);
      setSuccess('CV parsed successfully');
      setRawText('');
      setTimeout(() => setSuccess(null), 5000);
      await loadCvData();
    } catch (err: any) {
      setError(err.message || 'Failed to parse CV text');
    } finally {
      setLoading(false);
    }
  };

  const handlePositionUpdate = async () => {
    if (!position.trim()) {
      setError('Please enter a target position');
      return;
    }

    try {
      setLoading(true);
      setError(null);
      const result = await cvService.setCvPosition(position, level);
      setCv(result);
      setSuccess('Target position updated successfully');
      setTimeout(() => setSuccess(null), 5000);
    } catch (err: any) {
      setError(err.message || 'Failed to update position');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-surface">
      {/* Header */}
      <div className="bg-surface-container-lowest shadow-sm p-6 mb-8">
        <div className="max-w-7xl mx-auto">
          <div className="flex items-center gap-2 mb-2">
            <svg className="w-5 h-5 text-primary" fill="currentColor" viewBox="0 0 20 20">
              <path d="M10 3.5a6.5 6.5 0 100 13 6.5 6.5 0 000-13zM2 10a8 8 0 1116 0 8 8 0 01-16 0z" />
            </svg>
            <span className="font-code text-code text-primary uppercase tracking-wider">Candidate Intelligence Engine</span>
          </div>
          <h1 className="font-headline-lg text-headline-lg text-on-surface tracking-tight mb-2">CV & Profile Intelligence</h1>
          <p className="font-body-md text-body-md text-on-surface-variant max-w-2xl">
            Manage your verified resume artifact, calibrate target seniority heuristics, and examine real-time AI-extracted skill vector matches.
          </p>
        </div>
      </div>

      {/* Alerts */}
      {error && (
        <div className="max-w-7xl mx-auto mb-4 p-4 bg-error-container text-on-error-container rounded-lg flex items-center gap-2">
          <svg className="w-5 h-5 flex-shrink-0" fill="currentColor" viewBox="0 0 20 20">
            <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" clipRule="evenodd" />
          </svg>
          <span>{error}</span>
        </div>
      )}

      {success && (
        <div className="max-w-7xl mx-auto mb-4 p-4 bg-secondary-container text-on-secondary-container rounded-lg flex items-center gap-2">
          <svg className="w-5 h-5 flex-shrink-0" fill="currentColor" viewBox="0 0 20 20">
            <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clipRule="evenodd" />
          </svg>
          <span>{success}</span>
        </div>
      )}

      <div className="max-w-7xl mx-auto px-4 pb-8">
        {/* Target Position Form */}
        <div className="bg-surface-container-lowest rounded-xl p-6 shadow-sm mb-8">
          <form
            onSubmit={(e) => {
              e.preventDefault();
              handlePositionUpdate();
            }}
            className="flex flex-col lg:flex-row items-stretch lg:items-center justify-between gap-4"
          >
            <div className="flex flex-col sm:flex-row items-stretch sm:items-center gap-4 flex-1">
              <div className="flex-1 flex flex-col gap-1.5">
                <label className="font-label-sm text-label-sm text-on-surface-variant font-medium flex items-center gap-1.5">
                  <svg className="w-5 h-5 text-secondary" fill="currentColor" viewBox="0 0 20 20">
                    <path d="M10 4a3 3 0 110 6 3 3 0 010-6z" />
                    <path fillRule="evenodd" d="M10 9c-5.228 0-9.228 2.143-9.228 4.786 0 1.524 1.657 2.958 4.228 3.662V18H4a1 1 0 100 2h12a1 1 0 100-2h-1v-1.752c2.571-.704 4.228-2.138 4.228-3.662 0-2.643-4-4.786-9.228-4.786z" clipRule="evenodd" />
                  </svg>
                  Target Role Calibration
                </label>
                <input
                  type="text"
                  value={position}
                  onChange={(e) => setPosition(e.target.value)}
                  placeholder="e.g. Staff Distributed Systems Engineer"
                  className="w-full h-11 px-3.5 rounded-lg bg-surface-container-low font-body-md text-body-md text-on-surface focus:outline-none focus:bg-surface-container transition-all"
                />
              </div>

              <div className="w-full sm:w-64 flex flex-col gap-1.5">
                <label className="font-label-sm text-label-sm text-on-surface-variant font-medium flex items-center gap-1.5">
                  <svg className="w-5 h-5 text-secondary" fill="currentColor" viewBox="0 0 20 20">
                    <path d="M9.049 2.927c.3-.921 1.603-.921 1.902 0l1.07 3.292a1 1 0 00.95.69h3.462c.969 0 1.371 1.24.588 1.81l-2.8 2.034a1 1 0 00-.364 1.118l1.07 3.292c.3.921-.755 1.688-1.54 1.118l-2.8-2.034a1 1 0 00-1.175 0l-2.8 2.034c-.784.57-1.838-.197-1.539-1.118l1.07-3.292a1 1 0 00-.364-1.118L2.98 8.72c-.783-.57-.38-1.81.588-1.81h3.461a1 1 0 00.951-.69l1.07-3.292z" />
                  </svg>
                  Seniority Benchmark
                </label>
                <select
                  value={level}
                  onChange={(e) => setLevel(e.target.value)}
                  className="w-full h-11 px-3.5 pr-9 rounded-lg bg-surface-container-low font-body-md text-body-md text-on-surface appearance-none focus:outline-none focus:bg-surface-container transition-all cursor-pointer"
                >
                  <option value="junior">Junior (0-2 YOE)</option>
                  <option value="mid">Mid-Level (3-5 YOE)</option>
                  <option value="senior">Senior (5-8 YOE)</option>
                  <option value="lead-staff">Lead / Staff (8-12 YOE)</option>
                  <option value="principal">Principal / Architect (12+ YOE)</option>
                </select>
              </div>
            </div>

            <button
              type="submit"
              disabled={loading}
              className="w-full lg:w-auto h-11 px-5 rounded-lg bg-primary-container text-on-primary hover:opacity-95 disabled:opacity-50 font-label-lg text-label-lg flex items-center justify-center gap-2 transition-all shadow-sm"
            >
              <svg className="w-5 h-5" fill="currentColor" viewBox="0 0 20 20">
                <path fillRule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clipRule="evenodd" />
              </svg>
              <span>{loading ? 'Updating...' : 'Save Target Role'}</span>
            </button>
          </form>
        </div>

        {/* Upload Section */}
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-8 mb-8">
          <div className="lg:col-span-2 space-y-4">
            {/* Tab Switcher */}
            <div className="inline-flex p-1 bg-surface-container-low rounded-xl">
              <button
                onClick={() => setActiveTab('file')}
                className={`px-4 py-2 rounded-lg font-label-md text-label-md transition-all flex items-center gap-2 ${
                  activeTab === 'file'
                    ? 'bg-surface-container-lowest text-on-surface shadow-sm'
                    : 'text-on-surface-variant hover:text-on-surface'
                }`}
              >
                <svg className="w-5 h-5" fill="currentColor" viewBox="0 0 20 20">
                  <path d="M4 3a2 2 0 00-2 2v10a2 2 0 002 2h12a2 2 0 002-2V5a2 2 0 00-2-2H4zm12 12H4l4-8 3 6 2-4 3 6z" />
                </svg>
                File Upload (.PDF, .TXT)
              </button>
              <button
                onClick={() => setActiveTab('text')}
                className={`px-4 py-2 rounded-lg font-label-md text-label-md transition-all flex items-center gap-2 ${
                  activeTab === 'text'
                    ? 'bg-surface-container-lowest text-on-surface shadow-sm'
                    : 'text-on-surface-variant hover:text-on-surface'
                }`}
              >
                <svg className="w-5 h-5" fill="currentColor" viewBox="0 0 20 20">
                  <path d="M4 4a2 2 0 012-2h4l4 4v8a2 2 0 01-2 2H6a2 2 0 01-2-2V4z" />
                </svg>
                Paste Resume Text
              </button>
            </div>

            {/* File Upload Tab */}
            {activeTab === 'file' && (
              <div>
                <CVUploadZone onFileSelected={handleFileUpload} isLoading={loading} />
              </div>
            )}

            {/* Text Paste Tab */}
            {activeTab === 'text' && (
              <div className="flex flex-col gap-4">
                <div className="flex flex-col gap-1.5">
                  <div className="flex justify-between items-center">
                    <label className="font-label-md text-label-md text-on-surface font-semibold">
                      Raw Unstructured Resume Copy
                    </label>
                    <span className="font-code text-code text-on-surface-variant">
                      {rawText.length} / 25,000 chars
                    </span>
                  </div>
                  <textarea
                    value={rawText}
                    onChange={(e) => setRawText(e.target.value)}
                    placeholder="Paste full markdown, plaintext, or ASCII formatted resume contents here..."
                    rows={9}
                    className="w-full p-4 rounded-xl bg-surface-container-low font-code text-code text-on-surface focus:outline-none focus:bg-surface-container transition-all resize-none"
                  />
                </div>

                <div className="flex items-center justify-between">
                  <span className="font-body-sm text-body-sm text-on-surface-variant">
                    Invokes POST /api/v1/cv/parse-text via Transformer NLP Pipeline
                  </span>
                  <button
                    onClick={handleTextParse}
                    disabled={loading || !rawText.trim()}
                    className="px-5 py-2.5 rounded-lg bg-primary text-on-primary disabled:opacity-50 font-label-lg text-label-lg hover:bg-primary-container transition-colors flex items-center gap-2 shadow-sm"
                  >
                    <svg className="w-5 h-5" fill="currentColor" viewBox="0 0 20 20">
                      <path fillRule="evenodd" d="M4 2a1 1 0 011 1v2.101a7.002 7.002 0 1119.414 5.414a1 1 0 11-1.414-1.414A5.002 5.002 0 004.059 4.059V3a1 1 0 01-1-1z" clipRule="evenodd" />
                    </svg>
                    <span>{loading ? 'Parsing...' : 'Parse Raw Text'}</span>
                  </button>
                </div>

                <div className="rounded-xl bg-surface-container p-4 flex items-start gap-3">
                  <svg className="w-6 h-6 text-secondary mt-0.5 flex-shrink-0" fill="currentColor" viewBox="0 0 20 20">
                    <path fillRule="evenodd" d="M18 5v8a2 2 0 01-2 2h-5l-5 4v-4H4a2 2 0 01-2-2V5a2 2 0 012-2h12a2 2 0 012 2zm-11-1a1 1 0 11-2 0 1 1 0 012 0z" clipRule="evenodd" />
                  </svg>
                  <div className="flex flex-col">
                    <span className="font-label-md text-label-md text-on-surface font-semibold">Immutable Versioning & Safe Overwrite</span>
                    <p className="font-body-sm text-body-sm text-on-surface-variant mt-1">
                      Uploading a new CV will recalibrate your AI mock interview prompts, technical competency radar, and tailored study curricula.
                    </p>
                  </div>
                </div>
              </div>
            )}
          </div>

          {/* Current CV Status */}
          {cv && (
            <div className="bg-surface-container-lowest rounded-xl p-6 shadow-sm h-fit">
              <h3 className="font-title-md text-title-md text-on-surface font-bold mb-4">Current CV</h3>
              {cv.fileName && (
                <div className="rounded-xl bg-surface-container-low p-4 flex items-start gap-3 mb-4">
                  <div className="w-10 h-10 rounded-lg bg-primary-container text-on-primary flex items-center justify-center flex-shrink-0">
                    <svg className="w-6 h-6" fill="currentColor" viewBox="0 0 20 20">
                      <path d="M4 3a2 2 0 00-2 2v10a2 2 0 002 2h12a2 2 0 002-2V5a2 2 0 00-2-2H4z" />
                    </svg>
                  </div>
                  <div>
                    <p className="font-label-lg text-label-lg text-on-surface font-semibold">{cv.fileName}</p>
                    <p className="font-body-sm text-body-sm text-on-surface-variant mt-1">
                      Parsed & Indexed
                    </p>
                  </div>
                </div>
              )}

              <div className="space-y-3">
                <div>
                  <p className="font-label-sm text-label-sm text-on-surface-variant mb-2">Match Score</p>
                  <div className="flex items-center gap-3">
                    <div className="flex-1 h-2 bg-surface-container rounded-full overflow-hidden">
                      <div className="h-full bg-primary-container rounded-full" style={{ width: `${cv.matchScore || 92}%` }}></div>
                    </div>
                    <span className="font-headline-sm text-headline-sm text-primary font-bold">
                      {cv.matchScore || 92}<span className="font-body-sm text-on-surface-variant">/100</span>
                    </span>
                  </div>
                </div>
              </div>
            </div>
          )}
        </div>

        {/* Skills Matrix */}
        {skills.length > 0 && (
          <div className="mb-8">
            <SkillsMatrix skills={skills} />
          </div>
        )}

        {/* Knowledge Gaps */}
        {cv?.knowledgeGaps && cv.knowledgeGaps.length > 0 && (
          <div className="mb-8">
            <div className="flex items-center gap-2 mb-4">
              <svg className="w-6 h-6 text-error" fill="currentColor" viewBox="0 0 20 20">
                <path fillRule="evenodd" d="M8.257 3.099c.765-1.36 2.722-1.36 3.486 0l5.58 9.92c.75 1.334-.213 2.98-1.742 2.98H4.42c-1.53 0-2.493-1.646-1.743-2.98l5.58-9.92zM11 13a1 1 0 11-2 0 1 1 0 012 0zm-1-8a1 1 0 00-1 1v3a1 1 0 002 0V6a1 1 0 00-1-1z" clipRule="evenodd" />
              </svg>
              <h3 className="font-title-lg text-title-lg text-on-surface font-bold">Identified Knowledge Gaps</h3>
            </div>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {cv.knowledgeGaps.map((gap, idx) => (
                <KnowledgeGapCard key={idx} gap={gap} />
              ))}
            </div>
          </div>
        )}

        {/* Experience Timeline */}
        {cv?.experience && cv.experience.length > 0 && (
          <div className="mb-8">
            <ExperienceTimeline experiences={cv.experience} />
          </div>
        )}

        {/* Suggested Positions */}
        {cv?.suggestedPositions && cv.suggestedPositions.length > 0 && (
          <div className="bg-surface-container-lowest rounded-xl p-space-lg shadow-sm mb-8">
            <div className="flex items-center gap-2 mb-4">
              <svg className="w-6 h-6 text-secondary" fill="currentColor" viewBox="0 0 20 20">
                <path d="M2 11a1 1 0 011-1h2a1 1 0 011 1v5a1 1 0 01-1 1H3a1 1 0 01-1-1v-5zM8 7a1 1 0 011-1h2a1 1 0 011 1v9a1 1 0 01-1 1H9a1 1 0 01-1-1V7zM14 4a1 1 0 011-1h2a1 1 0 011 1v12a1 1 0 01-1 1h-2a1 1 0 01-1-1V4z" />
              </svg>
              <h3 className="font-title-lg text-title-lg text-on-surface font-bold">AI-Suggested Position Fits</h3>
            </div>
            <div className="flex flex-col gap-3">
              {cv.suggestedPositions.map((fit, idx) => (
                <PositionFitCard key={idx} fit={fit} />
              ))}
            </div>
          </div>
        )}
      </div>

      {/* Overwrite Modal */}
      {showOverwriteModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm p-4">
          <div className="bg-surface-container-lowest rounded-2xl max-w-lg w-full p-6 shadow-xl flex flex-col gap-4">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-full bg-error-container text-on-error-container flex items-center justify-center flex-shrink-0">
                <svg className="w-6 h-6" fill="currentColor" viewBox="0 0 20 20">
                  <path fillRule="evenodd" d="M8.257 3.099c.765-1.36 2.722-1.36 3.486 0l5.58 9.92c.75 1.334-.213 2.98-1.742 2.98H4.42c-1.53 0-2.493-1.646-1.743-2.98l5.58-9.92zM11 13a1 1 0 11-2 0 1 1 0 012 0zm-1-8a1 1 0 00-1 1v3a1 1 0 002 0V6a1 1 0 00-1-1z" clipRule="evenodd" />
                </svg>
              </div>
              <div>
                <h3 className="font-title-lg text-title-lg text-on-surface font-bold">Overwrite Active CV?</h3>
                <p className="font-label-sm text-label-sm text-on-surface-variant">This action will replace your current CV</p>
              </div>
            </div>

            <p className="font-body-md text-body-md text-on-surface-variant">
              Uploading a new resume will permanently archive your current skill vectors and recalibrate your AI assessments.
            </p>

            <div className="flex items-center justify-end gap-3 pt-2">
              <button
                onClick={() => setShowOverwriteModal(false)}
                className="px-4 py-2 rounded-lg bg-surface-container hover:bg-surface-container-high text-on-surface font-label-md text-label-md transition-colors"
              >
                Cancel
              </button>
              <input
                type="file"
                id="fileInputModal"
                accept=".pdf,.txt"
                className="hidden"
                onChange={(e) => {
                  if (e.target.files?.[0]) {
                    performFileUpload(e.target.files[0]);
                  }
                }}
              />
              <button
                onClick={() => document.getElementById('fileInputModal')?.click()}
                className="px-4 py-2 rounded-lg bg-primary-container text-on-primary font-label-md text-label-md hover:bg-primary transition-colors flex items-center gap-1.5"
              >
                <svg className="w-5 h-5" fill="currentColor" viewBox="0 0 20 20">
                  <path d="M4 3a2 2 0 00-2 2v10a2 2 0 002 2h12a2 2 0 002-2V5a2 2 0 00-2-2H4zm12 12H4l4-8 3 6 2-4 3 6z" />
                </svg>
                Replace CV
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default CVUploadPage;
