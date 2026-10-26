import React, { useState, useEffect } from 'react';
import { Navigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import CvService from '../../services/cv.service';
import { CvParseResponse, SkillData } from '../../models/Cv.model';
import UploadZone from '../components/cv/UploadZone';
import SkillsMatrix from '../components/cv/SkillsMatrix';
import KnowledgeGapCard from '../components/cv/KnowledgeGapCard';
import PositionFitCard from '../components/cv/PositionFitCard';
import ExperienceTimeline from '../components/cv/ExperienceTimeline';
import EvaluationDimensions from '../components/cv/EvaluationDimensions';

const CvPage: React.FC = () => {
  const { user, isAuthenticated } = useAuth();

  // State management
  const [cvData, setCvData] = useState<CvParseResponse | null>(null);
  const [skills, setSkills] = useState<SkillData[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [uploadTab, setUploadTab] = useState<'file' | 'text'>('file');
  const [cvText, setCvText] = useState('');
  const [targetPosition, setTargetPosition] = useState('');
  const [targetLevel, setTargetLevel] = useState('');
  const [showOverwriteModal, setShowOverwriteModal] = useState(false);
  const [pendingFile, setPendingFile] = useState<File | null>(null);
  const [uploadLoading, setUploadLoading] = useState(false);
  const [parseLoading, setParseLoading] = useState(false);
  const [positionLoading, setPositionLoading] = useState(false);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  // Auto-dismiss toasts
  useEffect(() => {
    if (successMsg) {
      const timer = setTimeout(() => setSuccessMsg(null), 4000);
      return () => clearTimeout(timer);
    }
  }, [successMsg]);

  useEffect(() => {
    if (errorMsg) {
      const timer = setTimeout(() => setErrorMsg(null), 4000);
      return () => clearTimeout(timer);
    }
  }, [errorMsg]);

  // Load CV data on mount
  useEffect(() => {
    const loadCvData = async () => {
      try {
        setLoading(true);
        setError(null);

        // Try to get existing CV
        try {
          const cvRes = await CvService.getCv();
          if (cvRes.data?.data) {
            setCvData(cvRes.data.data);
            setTargetPosition(cvRes.data.data.targetPosition || '');
            setTargetLevel(cvRes.data.data.targetLevel || '');
          }
        } catch (cvErr: any) {
          // 404 means no CV yet, which is fine
          if (cvErr.response?.status !== 404) {
            throw cvErr;
          }
        }

        // Try to get skills
        try {
          const skillsRes = await CvService.getSkills();
          if (skillsRes.data?.data && Array.isArray(skillsRes.data.data)) {
            setSkills(skillsRes.data.data);
          }
        } catch (skillsErr: any) {
          // Skills may not exist without CV
          if (skillsErr.response?.status !== 404) {
            throw skillsErr;
          }
        }
      } catch (err: any) {
        console.error('Error loading CV data:', err);
        setError(err.response?.data?.message || 'Failed to load CV data');
      } finally {
        setLoading(false);
      }
    };

    if (isAuthenticated) {
      loadCvData();
    }
  }, [isAuthenticated]);

  // Handlers
  const handleFileSelected = async (file: File) => {
    if (cvData?.cvId) {
      setPendingFile(file);
      setShowOverwriteModal(true);
    } else {
      await uploadFile(file);
    }
  };

  const uploadFile = async (file: File) => {
    try {
      setUploadLoading(true);
      setErrorMsg(null);
      const res = await CvService.uploadCv(file);
      if (res.data?.data) {
        setCvData(res.data.data);
        setTargetPosition(res.data.data.targetPosition || '');
        setTargetLevel(res.data.data.targetLevel || '');
        setSuccessMsg('CV uploaded and parsed successfully');
        setCvText('');
      }
    } catch (err: any) {
      const msg = err.response?.data?.message || 'Failed to upload CV';
      setErrorMsg(msg);
      console.error('Upload error:', err);
    } finally {
      setUploadLoading(false);
      setPendingFile(null);
      setShowOverwriteModal(false);
    }
  };

  const handleParseText = async () => {
    if (!cvText.trim()) {
      setErrorMsg('Please paste resume text');
      return;
    }

    if (cvData?.cvId) {
      setPendingFile(null); // Mark as text parse
      setShowOverwriteModal(true);
    } else {
      await parseText();
    }
  };

  const parseText = async () => {
    try {
      setParseLoading(true);
      setErrorMsg(null);
      const res = await CvService.parseCvText(cvText);
      if (res.data?.data) {
        setCvData(res.data.data);
        setTargetPosition(res.data.data.targetPosition || '');
        setTargetLevel(res.data.data.targetLevel || '');
        setSuccessMsg('Resume text parsed successfully');
        setCvText('');
      }
    } catch (err: any) {
      const msg = err.response?.data?.message || 'Failed to parse resume';
      setErrorMsg(msg);
      console.error('Parse error:', err);
    } finally {
      setParseLoading(false);
      setShowOverwriteModal(false);
    }
  };

  const handleConfirmOverwrite = () => {
    if (pendingFile) {
      uploadFile(pendingFile);
    } else {
      parseText();
    }
  };

  const handleSavePosition = async () => {
    if (!targetPosition.trim() || !targetLevel) {
      setErrorMsg('Please fill in both position and level');
      return;
    }

    try {
      setPositionLoading(true);
      setErrorMsg(null);
      const res = await CvService.setPosition({ position: targetPosition, level: targetLevel });
      if (res.data?.data) {
        setCvData(res.data.data);
        setSuccessMsg('Target position saved successfully');
      }
    } catch (err: any) {
      const msg = err.response?.data?.message || 'Failed to save position';
      setErrorMsg(msg);
      console.error('Position error:', err);
    } finally {
      setPositionLoading(false);
    }
  };

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  // Calculate profile match score (simple calculation based on available data)
  const profileMatchScore = cvData
    ? Math.min(100, 50 + cvData.skills.length * 2 + (cvData.experience.length > 0 ? 20 : 0) + (cvData.education.length > 0 ? 10 : 0))
    : 0;

  // Get displayed skills: prefer API skills, fall back to CV data skills
  const displayedSkills = skills.length > 0 ? skills : cvData?.skills || [];

  // Generate match percentages for positions
  const positionMatches = cvData?.suggestedPositions.map((pos, idx) => ({
    position: pos,
    matchPercent: Math.max(60, 95 - idx * 7),
    rank: idx + 1,
  })) || [];

  return (
    <div className="flex h-screen bg-background">
      {/* Sidebar */}
      <aside className="fixed left-0 top-0 h-full w-64 bg-surface-container-lowest shadow-[0_1px_8px_rgba(0,0,0,0.04)] z-50 flex flex-col justify-between">
        <div className="flex flex-col">
          {/* Logo */}
          <div className="h-16 px-4 flex items-center gap-3 bg-surface-container-lowest">
            <div className="w-8 h-8 rounded-full bg-primary flex items-center justify-center flex-shrink-0">
              <span className="material-symbols-outlined text-white text-lg">school</span>
            </div>
            <div className="flex flex-col">
              <span className="font-headline-sm text-title-md text-on-surface tracking-tight leading-none">TalentAI</span>
              <span className="font-label-sm text-label-sm text-on-surface-variant">Career Intelligence</span>
            </div>
          </div>

          {/* Navigation */}
          <div className="px-4 py-3">
            <nav className="flex flex-col gap-1">
              <a
                href="/dashboard"
                className="flex items-center gap-3 px-3 py-2.5 rounded-lg text-on-surface-variant hover:bg-surface-container hover:text-on-surface transition-colors font-label-lg text-label-lg"
              >
                <span className="material-symbols-outlined text-lg leading-none">dashboard</span>
                <span>Dashboard</span>
              </a>
              <a
                href="/cv"
                className="flex items-center gap-3 px-3 py-2.5 transition-colors bg-primary-container text-on-primary font-title-md rounded-lg"
              >
                <span className="material-symbols-outlined text-lg leading-none">description</span>
                <span>My CV</span>
              </a>
              <a
                href="#"
                className="flex items-center gap-3 px-3 py-2.5 rounded-lg text-on-surface-variant hover:bg-surface-container hover:text-on-surface transition-colors font-label-lg text-label-lg"
              >
                <span className="material-symbols-outlined text-lg leading-none">smart_toy</span>
                <span>AI Interview</span>
              </a>
              <a
                href="#"
                className="flex items-center gap-3 px-3 py-2.5 rounded-lg text-on-surface-variant hover:bg-surface-container hover:text-on-surface transition-colors font-label-lg text-label-lg"
              >
                <span className="material-symbols-outlined text-lg leading-none">school</span>
                <span>Study Programs</span>
              </a>
              <a
                href="#"
                className="flex items-center gap-3 px-3 py-2.5 rounded-lg text-on-surface-variant hover:bg-surface-container hover:text-on-surface transition-colors font-label-lg text-label-lg"
              >
                <span className="material-symbols-outlined text-lg leading-none">history</span>
                <span>Interview History</span>
              </a>
            </nav>
          </div>
        </div>

        {/* Candidate Track */}
        {targetPosition && (
          <div className="p-4 bg-surface-container-lowest flex flex-col gap-3">
            <div className="p-3 rounded-lg bg-surface-container-low">
              <div className="font-label-sm text-label-sm text-on-surface-variant uppercase tracking-wider mb-1">Candidate Track</div>
              <div className="flex items-center gap-1.5 font-label-md text-label-md text-primary font-semibold">
                <span className="material-symbols-outlined text-sm leading-none">military_tech</span>
                <span>Target: {targetPosition}</span>
              </div>
            </div>
          </div>
        )}
      </aside>

      {/* Main content */}
      <div className="flex-1 flex flex-col overflow-hidden ml-64">
        {/* TopBar */}
        <div className="h-16 bg-surface-container-lowest shadow-[0_1px_8px_rgba(0,0,0,0.04)] px-6 flex items-center justify-between border-b border-outline-variant">
          <h1 className="font-headline-md text-headline-md text-on-surface">CV & Profile Intelligence</h1>
          <div className="flex items-center gap-4">
            <button className="px-4 py-2 text-label-lg font-label-lg text-on-primary bg-primary rounded-lg hover:opacity-90 transition-opacity">
              Upload CV
            </button>
            <div className="w-10 h-10 rounded-full bg-primary-container flex items-center justify-center">
              <span className="text-on-primary font-label-lg">{user?.fullName?.charAt(0) || '?'}</span>
            </div>
          </div>
        </div>

        {/* Main content area */}
        <main className="flex-1 overflow-y-auto p-6">
          {loading ? (
            <div className="flex items-center justify-center h-full">
              <div className="text-center">
                <div className="animate-spin inline-block">
                  <span className="material-symbols-outlined text-6xl text-primary">hourglass_empty</span>
                </div>
                <p className="mt-4 text-on-surface-variant">Loading CV data...</p>
              </div>
            </div>
          ) : (
            <>
              {/* Toast notifications */}
              {successMsg && (
                <div className="mb-4 p-4 rounded-lg bg-primary-fixed text-on-primary border border-primary flex items-center gap-3">
                  <span className="material-symbols-outlined">check_circle</span>
                  <span className="font-label-md">{successMsg}</span>
                </div>
              )}

              {errorMsg && (
                <div className="mb-4 p-4 rounded-lg bg-error-container text-on-error-container border border-error flex items-center gap-3">
                  <span className="material-symbols-outlined">error</span>
                  <span className="font-label-md">{errorMsg}</span>
                </div>
              )}

              {/* Header with match score */}
              <div className="flex items-center justify-between mb-6">
                <div>
                  <h2 className="font-headline-sm text-headline-sm text-on-surface mb-1">Profile Analysis</h2>
                  <p className="text-body-md text-on-surface-variant">AI-powered analysis of your professional profile</p>
                </div>
                {cvData && (
                  <div className="flex flex-col items-center justify-center w-32 h-32 rounded-full bg-primary-container border-4 border-primary">
                    <span className="font-headline-sm text-headline-sm text-on-primary">{profileMatchScore.toFixed(1)}</span>
                    <span className="text-label-sm text-on-primary">/100</span>
                    <span className="text-label-sm text-on-primary mt-1 text-center">Match Score</span>
                  </div>
                )}
              </div>

              {/* Target Position form */}
              <div className="card bg-surface-container-lowest rounded-lg p-6 mb-6 border border-outline-variant">
                <h3 className="font-title-md text-title-md text-on-surface mb-4">Target Position & Seniority Level</h3>
                <div className="flex gap-4 items-end">
                  <div className="flex-1">
                    <label className="block text-label-md text-on-surface-variant mb-2">Position</label>
                    <input
                      type="text"
                      placeholder="e.g. Full Stack Engineer"
                      value={targetPosition}
                      onChange={(e) => setTargetPosition(e.target.value)}
                      className="w-full px-4 py-2 rounded-lg border border-outline-variant bg-surface text-on-surface placeholder-on-surface-variant focus:outline-none focus:border-primary transition-colors"
                    />
                  </div>
                  <div className="flex-1">
                    <label className="block text-label-md text-on-surface-variant mb-2">Level</label>
                    <select
                      value={targetLevel}
                      onChange={(e) => setTargetLevel(e.target.value)}
                      className="w-full px-4 py-2 rounded-lg border border-outline-variant bg-surface text-on-surface focus:outline-none focus:border-primary transition-colors"
                    >
                      <option value="">Select Level</option>
                      <option value="JUNIOR">Junior</option>
                      <option value="MID">Mid-Level</option>
                      <option value="SENIOR">Senior</option>
                      <option value="LEAD">Lead</option>
                      <option value="PRINCIPAL">Principal</option>
                    </select>
                  </div>
                  <button
                    onClick={handleSavePosition}
                    disabled={positionLoading || !targetPosition || !targetLevel}
                    className="px-6 py-2 bg-primary text-on-primary rounded-lg font-label-lg hover:opacity-90 disabled:opacity-50 transition-opacity"
                  >
                    {positionLoading ? 'Saving...' : 'Save Position'}
                  </button>
                </div>
              </div>

              {/* Upload mechanism */}
              <div className="bg-surface-container-lowest rounded-lg p-6 mb-6 border border-outline-variant">
                <h3 className="font-title-md text-title-md text-on-surface mb-4">Upload or Paste Resume</h3>

                {/* Tabs */}
                <div className="flex gap-2 mb-4 border-b border-outline-variant">
                  <button
                    onClick={() => setUploadTab('file')}
                    className={`px-4 py-2 font-label-lg ${
                      uploadTab === 'file'
                        ? 'text-primary border-b-2 border-primary'
                        : 'text-on-surface-variant hover:text-on-surface'
                    }`}
                  >
                    File Upload
                  </button>
                  <button
                    onClick={() => setUploadTab('text')}
                    className={`px-4 py-2 font-label-lg ${
                      uploadTab === 'text'
                        ? 'text-primary border-b-2 border-primary'
                        : 'text-on-surface-variant hover:text-on-surface'
                    }`}
                  >
                    Paste Resume Text
                  </button>
                </div>

                {/* Tab content */}
                {uploadTab === 'file' ? (
                  <UploadZone onFile={handleFileSelected} loading={uploadLoading} />
                ) : (
                  <div className="space-y-4">
                    <textarea
                      rows={10}
                      value={cvText}
                      onChange={(e) => setCvText(e.target.value)}
                      placeholder="Paste your resume text here..."
                      className="w-full px-4 py-3 rounded-lg border border-outline-variant bg-surface text-on-surface placeholder-on-surface-variant focus:outline-none focus:border-primary transition-colors font-code text-code"
                    />
                    <button
                      onClick={handleParseText}
                      disabled={parseLoading || !cvText.trim()}
                      className="px-6 py-2 bg-primary text-on-primary rounded-lg font-label-lg hover:opacity-90 disabled:opacity-50 transition-opacity"
                    >
                      {parseLoading ? 'Analyzing...' : 'Analyze CV'}
                    </button>
                  </div>
                )}
              </div>

              {/* Active CV status */}
              {cvData && (
                <div className="bg-surface-container-lowest rounded-lg p-6 mb-6 border border-outline-variant">
                  <h3 className="font-title-md text-title-md text-on-surface mb-4">Active CV</h3>
                  <div className="flex items-center gap-4">
                    <span className="material-symbols-outlined text-3xl text-primary">description</span>
                    <div className="flex-1">
                      <p className="text-body-md text-on-surface font-semibold">{cvData.fileName || 'Resume'}</p>
                      <p className="text-body-sm text-on-surface-variant">{cvData.skills.length} skills extracted</p>
                    </div>
                    <span className="px-3 py-1 bg-primary-fixed text-on-primary rounded-full text-label-sm font-label-sm">
                      Parsed Successfully
                    </span>
                  </div>
                </div>
              )}

              {/* Two-column layout */}
              {cvData ? (
                <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                  {/* Left column */}
                  <div className="lg:col-span-2 space-y-6">
                    {/* Suggested Positions */}
                    {positionMatches.length > 0 && (
                      <div className="bg-surface-container-lowest rounded-lg p-6 border border-outline-variant">
                        <h3 className="font-title-md text-title-md text-on-surface mb-4">Suggested Positions</h3>
                        <div className="space-y-3">
                          {positionMatches.map((match, idx) => (
                            <PositionFitCard
                              key={idx}
                              position={match.position}
                              matchPercent={match.matchPercent}
                              rank={match.rank}
                            />
                          ))}
                        </div>
                      </div>
                    )}

                    {/* Technical Skills Matrix */}
                    <div className="bg-surface-container-lowest rounded-lg p-6 border border-outline-variant">
                      <h3 className="font-title-md text-title-md text-on-surface mb-4">Technical Skills Matrix</h3>
                      <SkillsMatrix skills={displayedSkills} />
                    </div>

                    {/* Knowledge Gaps */}
                    {cvData.knowledgeGaps.length > 0 && (
                      <div className="bg-surface-container-lowest rounded-lg p-6 border border-outline-variant">
                        <h3 className="font-title-md text-title-md text-on-surface mb-4">Knowledge Gaps</h3>
                        <div className="space-y-3">
                          {cvData.knowledgeGaps.map((gap, idx) => (
                            <KnowledgeGapCard key={idx} gap={gap} index={idx} />
                          ))}
                        </div>
                      </div>
                    )}

                    {/* Work Experience */}
                    {cvData.experience.length > 0 && (
                      <div className="bg-surface-container-lowest rounded-lg p-6 border border-outline-variant">
                        <h3 className="font-title-md text-title-md text-on-surface mb-4">Work Experience</h3>
                        <ExperienceTimeline experience={cvData.experience} />
                      </div>
                    )}

                    {/* Education */}
                    {cvData.education.length > 0 && (
                      <div className="bg-surface-container-lowest rounded-lg p-6 border border-outline-variant">
                        <h3 className="font-title-md text-title-md text-on-surface mb-4">Education</h3>
                        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                          {cvData.education.map((edu, idx) => (
                            <div key={idx} className="p-4 rounded-lg bg-surface-container-low border border-outline-variant">
                              <h4 className="text-title-md font-title-md text-on-surface mb-1">{edu.degree}</h4>
                              <p className="text-label-md text-on-surface-variant mb-1">{edu.institution}</p>
                              <p className="text-label-sm text-on-surface-variant">{edu.year}</p>
                            </div>
                          ))}
                        </div>
                      </div>
                    )}
                  </div>

                  {/* Right column - AI Profile Card */}
                  <div className="bg-surface-container-lowest rounded-lg p-6 border border-outline-variant h-fit lg:sticky lg:top-6">
                    <h3 className="font-title-md text-title-md text-on-surface mb-4">AI Profile Card</h3>
                    <div className="mb-6 pb-6 border-b border-outline-variant">
                      <p className="text-label-md text-on-surface-variant mb-1">Candidate</p>
                      <p className="text-title-md font-title-md text-on-surface">{user?.fullName}</p>
                    </div>

                    {targetPosition && (
                      <div className="mb-6 pb-6 border-b border-outline-variant">
                        <p className="text-label-md text-on-surface-variant mb-2">Target Position</p>
                        <span className="inline-block px-3 py-1 bg-primary-container text-on-primary rounded-full text-label-md font-label-md">
                          {targetPosition}
                        </span>
                      </div>
                    )}

                    <div className="mb-6 pb-6 border-b border-outline-variant">
                      <EvaluationDimensions skills={displayedSkills} />
                    </div>

                    {positionMatches.length > 0 && (
                      <div>
                        <p className="text-label-md text-on-surface-variant mb-3">Top Matches</p>
                        <div className="space-y-2">
                          {positionMatches.slice(0, 3).map((match, idx) => (
                            <div
                              key={idx}
                              className="p-2 rounded bg-surface-container-low text-label-sm text-on-surface hover:bg-surface-container transition-colors"
                            >
                              {match.position}
                            </div>
                          ))}
                        </div>
                      </div>
                    )}
                  </div>
                </div>
              ) : (
                /* Empty state */
                <div className="flex flex-col items-center justify-center py-20 text-center">
                  <span className="material-symbols-outlined text-6xl text-outline mb-4">description</span>
                  <h3 className="text-headline-sm font-headline-sm text-on-surface mb-2">No CV uploaded yet</h3>
                  <p className="text-body-md text-on-surface-variant">Upload your CV or paste resume text to get started</p>
                </div>
              )}
            </>
          )}
        </main>
      </div>

      {/* Overwrite confirmation modal */}
      {showOverwriteModal && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-[1000]">
          <div className="bg-surface-container-lowest rounded-lg p-6 w-full max-w-md shadow-lg border border-outline-variant">
            <h3 className="text-headline-sm font-headline-sm text-on-surface mb-2">Overwrite Existing CV?</h3>
            <p className="text-body-md text-on-surface-variant mb-6">
              You already have a CV. Uploading a new one will replace the existing data.
            </p>
            <div className="flex gap-3 justify-end">
              <button
                onClick={() => {
                  setShowOverwriteModal(false);
                  setPendingFile(null);
                }}
                className="px-4 py-2 rounded-lg border border-outline-variant text-on-surface hover:bg-surface-container transition-colors font-label-lg"
              >
                Cancel
              </button>
              <button
                onClick={handleConfirmOverwrite}
                disabled={uploadLoading || parseLoading}
                className="px-4 py-2 rounded-lg bg-primary text-on-primary hover:opacity-90 disabled:opacity-50 transition-opacity font-label-lg"
              >
                {uploadLoading || parseLoading ? 'Processing...' : 'Confirm Upload'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default CvPage;
