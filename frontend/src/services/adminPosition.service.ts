import httpService from './http.service';
import { ApiResponse } from '../models/Api.model';
import { PagedResponse } from '../models/User.model';
import { AdminPosition, PositionPayload } from '../models/Position.model';

const BASE = '/api/v1/admin/positions';

const AdminPositionService = {
  getPositions: (params: {
    page?: number;
    size?: number;
    search?: string;
    active?: boolean;
  }) => httpService.get<ApiResponse<PagedResponse<AdminPosition>>>(BASE, { params }),

  getPosition: (id: number) =>
    httpService.get<ApiResponse<AdminPosition>>(`${BASE}/${id}`),

  createPosition: (payload: PositionPayload) =>
    httpService.post<ApiResponse<AdminPosition>>(BASE, payload),

  updatePosition: (id: number, payload: PositionPayload) =>
    httpService.put<ApiResponse<AdminPosition>>(`${BASE}/${id}`, payload),

  changeStatus: (id: number, active: boolean) =>
    httpService.patch<ApiResponse<AdminPosition>>(`${BASE}/${id}/status`, {}, { params: { active } }),

  /** Preview: AI doc JD va tra ve ky nang yeu cau, chua luu vi tri. */
  extractSkills: (jdText: string) =>
    httpService.post<ApiResponse<string[]>>(`${BASE}/extract-skills`, { jdText }),
};

export default AdminPositionService;
