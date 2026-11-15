import { useCallback, useState } from 'react';
import AdminPositionService from '../services/adminPosition.service';
import { AdminPosition, PositionPayload } from '../models/Position.model';
import { PagedResponse } from '../models/User.model';

interface Filters {
  search: string;
  active: string;
}

const PAGE_SIZE = 10;
const EMPTY_PAGE: PagedResponse<AdminPosition> = {
  content: [],
  totalElements: 0,
  totalPages: 0,
  number: 0,
  size: PAGE_SIZE,
};

export const useAdminPositionController = () => {
  const [positions, setPositions] = useState<PagedResponse<AdminPosition> | null>(null);
  const [filters, setFilters] = useState<Filters>({ search: '', active: '' });
  const [currentPage, setCurrentPage] = useState(0);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [extracting, setExtracting] = useState(false);
  const [error, setError] = useState('');
  const [successMsg, setSuccessMsg] = useState('');

  const clearMessages = useCallback(() => {
    setError('');
    setSuccessMsg('');
  }, []);

  const fetchPositions = useCallback(async (page = 0, f: Filters = filters) => {
    setLoading(true);
    try {
      const params = {
        page,
        size: PAGE_SIZE,
        ...(f.search ? { search: f.search } : {}),
        ...(f.active ? { active: f.active === 'true' } : {}),
      };
      const res = await AdminPositionService.getPositions(params);
      setPositions(res.data.data ?? EMPTY_PAGE);
      setCurrentPage(page);
    } catch (e: any) {
      setError(e.response?.data?.message || 'Không thể tải danh sách vị trí');
    } finally {
      setLoading(false);
    }
  }, [filters]);

  const applyFilters = useCallback((next: Filters) => {
    setFilters(next);
    clearMessages();
    fetchPositions(0, next);
  }, [fetchPositions, clearMessages]);

  const createPosition = useCallback(async (payload: PositionPayload): Promise<boolean> => {
    setSaving(true);
    clearMessages();
    try {
      await AdminPositionService.createPosition(payload);
      setSuccessMsg('Đã thêm vị trí và dữ liệu JD');
      await fetchPositions(0);
      return true;
    } catch (e: any) {
      setError(e.response?.data?.message || 'Không thể lưu vị trí');
      return false;
    } finally {
      setSaving(false);
    }
  }, [fetchPositions, clearMessages]);

  const updatePosition = useCallback(async (id: number, payload: PositionPayload): Promise<boolean> => {
    setSaving(true);
    clearMessages();
    try {
      await AdminPositionService.updatePosition(id, payload);
      setSuccessMsg('Đã cập nhật vị trí');
      await fetchPositions(currentPage);
      return true;
    } catch (e: any) {
      setError(e.response?.data?.message || 'Không thể cập nhật vị trí');
      return false;
    } finally {
      setSaving(false);
    }
  }, [fetchPositions, currentPage, clearMessages]);

  const changeStatus = useCallback(async (id: number, active: boolean) => {
    clearMessages();
    try {
      await AdminPositionService.changeStatus(id, active);
      setSuccessMsg(active ? 'Vị trí đã được bật' : 'Vị trí đã được tắt');
      await fetchPositions(currentPage);
    } catch (e: any) {
      setError(e.response?.data?.message || 'Không thể đổi trạng thái vị trí');
    }
  }, [fetchPositions, currentPage, clearMessages]);

  const extractSkills = useCallback(async (jdText: string): Promise<string[]> => {
    setExtracting(true);
    clearMessages();
    try {
      const res = await AdminPositionService.extractSkills(jdText);
      return res.data.data ?? [];
    } catch (e: any) {
      setError(e.response?.data?.message || 'AI không trích xuất được kỹ năng từ JD');
      return [];
    } finally {
      setExtracting(false);
    }
  }, [clearMessages]);

  return {
    positions,
    filters,
    currentPage,
    loading,
    saving,
    extracting,
    error,
    successMsg,
    fetchPositions,
    applyFilters,
    createPosition,
    updatePosition,
    changeStatus,
    extractSkills,
    clearMessages,
  };
};
