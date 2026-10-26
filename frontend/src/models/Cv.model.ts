// CV Models & DTOs matching backend

export enum ProficiencyLevel {
  BEGINNER = 'BEGINNER',
  INTERMEDIATE = 'INTERMEDIATE',
  ADVANCED = 'ADVANCED',
  EXPERT = 'EXPERT',
}

export interface SkillData {
  name: string;
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
