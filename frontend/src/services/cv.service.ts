import { CvParseResponse, SkillResponse, CvUpdateRequest } from '../models/Cv.model';
import { API_BASE_URL } from '../config/api.config';

const CV_API_BASE = `${API_BASE_URL}/api/v1/cv`;

// Helper to get auth token
const getAuthToken = (): string => {
  return localStorage.getItem('token') || '';
};

// Helper to build headers with auth
const buildHeaders = (includeAuth = true): HeadersInit => {
  const headers: HeadersInit = {
    'Content-Type': 'application/json',
  };
  if (includeAuth) {
    headers['Authorization'] = `Bearer ${getAuthToken()}`;
  }
  return headers;
};

export const cvService = {
  /**
   * Upload CV file (PDF or TXT)
   */
  async uploadCv(file: File): Promise<CvParseResponse> {
    const formData = new FormData();
    formData.append('file', file);

    const response = await fetch(`${CV_API_BASE}/upload`, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${getAuthToken()}`,
      },
      body: formData,
    });

    if (!response.ok) {
      const error = await response.json();
      throw new Error(error.message || 'Failed to upload CV');
    }

    return response.json();
  },

  /**
   * Parse raw resume text
   */
  async parseCvText(text: string): Promise<CvParseResponse> {
    const response = await fetch(`${CV_API_BASE}/parse-text`, {
      method: 'POST',
      headers: buildHeaders(),
      body: JSON.stringify({ cvText: text }),
    });

    if (!response.ok) {
      const error = await response.json();
      throw new Error(error.message || 'Failed to parse CV text');
    }

    return response.json();
  },

  /**
   * Get user's CV data
   */
  async getCv(): Promise<CvParseResponse> {
    const response = await fetch(CV_API_BASE, {
      method: 'GET',
      headers: buildHeaders(),
    });

    if (!response.ok) {
      if (response.status === 404) {
        return {} as CvParseResponse;
      }
      const error = await response.json();
      throw new Error(error.message || 'Failed to fetch CV');
    }

    return response.json();
  },

  /**
   * Update target position and seniority level
   */
  async setCvPosition(position: string, level: string): Promise<CvParseResponse> {
    const response = await fetch(`${CV_API_BASE}/position`, {
      method: 'PUT',
      headers: buildHeaders(),
      body: JSON.stringify({ position, level }),
    });

    if (!response.ok) {
      const error = await response.json();
      throw new Error(error.message || 'Failed to update position');
    }

    return response.json();
  },

  /**
   * Get extracted skills for the user's CV
   */
  async getCvSkills(): Promise<SkillResponse[]> {
    const response = await fetch(`${CV_API_BASE}/skills`, {
      method: 'GET',
      headers: buildHeaders(),
    });

    if (!response.ok) {
      if (response.status === 404) {
        return [];
      }
      const error = await response.json();
      throw new Error(error.message || 'Failed to fetch skills');
    }

    return response.json();
  },
};
