import httpService from './http.service';
import { ApiResponse } from '../models/Api.model';
import { CvParseResponse, SkillResponse, SetPositionRequest, CvTextRequest } from '../models/Cv.model';

const CvService = {
  getCv: () => httpService.get<ApiResponse<CvParseResponse>>('/api/v1/cv'),
  
  getSkills: () => httpService.get<ApiResponse<SkillResponse[]>>('/api/v1/cv/skills'),
  
  uploadCv: (file: File) => {
    const formData = new FormData();
    formData.append('file', file);
    return httpService.post<ApiResponse<CvParseResponse>>('/api/v1/cv/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  },
  
  parseCvText: (cvText: string) =>
    httpService.post<ApiResponse<CvParseResponse>>('/api/v1/cv/parse-text', { cvText } as CvTextRequest),
  
  setPosition: (req: SetPositionRequest) =>
    httpService.put<ApiResponse<CvParseResponse>>('/api/v1/cv/position', req),
};

export default CvService;
