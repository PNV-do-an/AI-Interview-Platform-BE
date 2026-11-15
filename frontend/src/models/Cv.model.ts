// CV Models & DTOs matching backend

export enum ProficiencyLevel {
  BEGINNER = 'BEGINNER',
  INTERMEDIATE = 'INTERMEDIATE',
  ADVANCED = 'ADVANCED',
  EXPERT = 'EXPERT',
}

export interface PositionFit {
  position: string;
  matchPercentage: number;
  description?: string;
}

/** Vi tri phu hop do backend tinh bang cach so khop ky nang CV voi catalog vi tri. */
export interface PositionRecommendation {
  positionId: number;
  title: string;
  slug: string;
  level?: string | null;
  description?: string | null;
  requiredSkillCount: number;
  matchedSkillCount: number;
  matchPercentage: number;
  matchedSkills: string[];
  missingSkills: string[];
}

export interface SkillData {
  id?: number;
  name: string;
  category?: string;
  proficiency: ProficiencyLevel;
  years: number;
}

export interface ExperienceData {
  company: string;
  role: string;
  duration: string;
  achievements: string[];
}

export interface EducationData {
  institution: string;
  degree: string;
  year: number;
}

export interface KnowledgeGap {
  title: string;
  description: string;
  priority: 'HIGH' | 'MEDIUM' | 'LOW';
  suggestedResources?: string[];
}

export interface CvParseResponse {
  cvId?: number | null;
  fileName?: string | null;
  skills: SkillData[];
  experience: ExperienceData[];
  education: EducationData[];
  suggestedPositions: string[];
  knowledgeGaps: string[];
  recommendedPositions?: PositionRecommendation[];
  targetPosition?: string | null;
  targetLevel?: string | null;
}

export interface SkillResponse {
  id: number;
  name: string;
  proficiency: ProficiencyLevel;
  years: number;
}

export interface CvUpdateRequest {
  position: string;
  level: string;
}

export interface SetPositionRequest {
  position: string;
  level: string;
}

export interface CvTextRequest {
  cvText: string;
}

export interface EvaluationDimension {
  name: string;
  score: number;
}
