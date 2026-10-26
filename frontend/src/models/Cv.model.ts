// CV Models & DTOs matching backend

export enum ProficiencyLevel {
  BEGINNER = 'BEGINNER',
  INTERMEDIATE = 'INTERMEDIATE',
  ADVANCED = 'ADVANCED',
  EXPERT = 'EXPERT',
}

export interface SkillData {
  id?: number;
  name: string;
  category?: string;
  proficiency: ProficiencyLevel;
  years?: number;
}

export interface ExperienceData {
  id?: number;
  position: string;
  company: string;
  location?: string;
  startDate: string;
  endDate?: string;
  description?: string;
  technologies?: string[];
}

export interface EducationData {
  id?: number;
  degree: string;
  institution: string;
  field?: string;
  graduationYear?: number;
  honors?: string;
}

export interface PositionFit {
  position: string;
  matchPercentage: number;
  description?: string;
}

export interface KnowledgeGap {
  title: string;
  description: string;
  priority: 'HIGH' | 'MEDIUM' | 'LOW';
  suggestedResources?: string[];
}

export interface CvParseResponse {
  cvId?: number;
  fileName?: string;
  filePath?: string;
  skills?: SkillData[];
  experience?: ExperienceData[];
  education?: EducationData[];
  suggestedPositions?: PositionFit[];
  knowledgeGaps?: KnowledgeGap[];
  targetPosition?: string;
  targetLevel?: string;
  summary?: string;
  matchScore?: number;
}

export interface SkillResponse {
  id: number;
  skillName: string;
  proficiencyLevel: ProficiencyLevel;
  yearsExperience?: number;
}

export interface CvUpdateRequest {
  position: string;
  level: string;
}

export interface EvaluationDimension {
  name: string;
  score: number;
}
