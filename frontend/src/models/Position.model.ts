export interface AdminPosition {
  id: number;
  title: string;
  slug: string;
  level: string | null;
  description: string | null;
  jdText: string | null;
  active: boolean;
  requiredSkills: string[];
  createdAt: string;
  updatedAt: string;
}

export interface PositionPayload {
  title: string;
  level?: string;
  description?: string;
  jdText?: string;
  skills?: string[];
}

export const POSITION_LEVELS = ['INTERN', 'FRESHER', 'JUNIOR', 'MID', 'SENIOR', 'LEAD'];
