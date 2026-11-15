import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import CvService from '../../services/cv.service';
import { CvParseResponse } from '../../models/Cv.model';

const ROLE_LABELS: Record<string, string> = {
  ROLE_USER: 'Người dùng',
  ROLE_STAFF: 'Nhân viên',
  ROLE_ADMIN: 'Quản trị viên',
  ROLE_INTERVIEWER: 'Phỏng vấn viên',
};

interface QuickAction {
  label: string;
  description: string;
  icon: string;
  to?: string;
}

const DashboardPage: React.FC = () => {
  const { user } = useAuth();
  const isAdmin = user?.role === 'ROLE_ADMIN';
  const [cv, setCv] = useState<CvParseResponse | null>(null);
  const [skillCount, setSkillCount] = useState(0);
  const [cvLoading, setCvLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;

    const load = async () => {
      try {
        const [cvRes, skillsRes] = await Promise.all([
          CvService.getCv(),
          CvService.getSkills(),
        ]);
        if (cancelled) return;
        if (cvRes.data?.data) setCv(cvRes.data.data);
        if (Array.isArray(skillsRes.data?.data)) setSkillCount(skillsRes.data.data.length);
      } catch {
        // No CV yet is a normal state for a fresh account — keep the empty card.
      } finally {
        if (!cancelled) setCvLoading(false);
      }
    };

    load();
    return () => {
      cancelled = true;
    };
  }, []);

  const hasCv = Boolean(cv?.cvId);

  const quickActions: QuickAction[] = [
    {
      label: hasCv ? 'Xem phân tích CV' : 'Tải CV lên',
      description: hasCv
        ? 'Kỹ năng, khoảng trống kiến thức và vị trí phù hợp'
        : 'PDF hoặc TXT, tối đa 10MB',
      icon: 'description',
      to: '/cv',
    },
    {
      label: 'Luyện phỏng vấn AI',
      description: 'Sinh câu hỏi theo CV và vị trí mục tiêu',
      icon: 'smart_toy',
    },
    {
      label: 'Chương trình ôn tập',
      description: 'Lộ trình học dựa trên khoảng trống kiến thức',
      icon: 'school',
      to: '/study',
    },
  ];

  return (
    <div className="flex flex-col gap-6">
      {/* Greeting */}
      <div className="bg-surface-container-lowest rounded-xl p-6 border border-outline-variant flex flex-wrap items-center justify-between gap-4">
        <div>
          <h2 className="font-headline-md text-headline-md text-on-surface">
            Xin chào, {user?.fullName || 'bạn'}!
          </h2>
          <p className="font-body-md text-body-md text-on-surface-variant mt-1">
            {hasCv
              ? 'CV của bạn đã được phân tích. Tiếp tục luyện phỏng vấn hoặc xem lại hồ sơ.'
              : 'Bắt đầu bằng việc tải CV lên để hệ thống phân tích kỹ năng của bạn.'}
          </p>
        </div>
        <div className="flex flex-col items-end gap-1">
          <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-primary-fixed text-on-primary-fixed font-label-md text-label-md">
            <span className="material-symbols-outlined text-sm leading-none">badge</span>
            {ROLE_LABELS[user?.role ?? ''] ?? user?.role}
          </span>
          <span className="font-body-sm text-body-sm text-on-surface-variant">{user?.email}</span>
        </div>
      </div>

      {/* CV status */}
      <div className="bg-surface-container-lowest rounded-xl p-6 border border-outline-variant">
        <div className="flex items-center justify-between mb-4">
          <h3 className="font-title-md text-title-md text-on-surface">Hồ sơ của bạn</h3>
          <Link
            to="/cv"
            className="font-label-lg text-label-lg text-primary hover:underline flex items-center gap-1"
          >
            <span>{hasCv ? 'Mở CV' : 'Tải CV'}</span>
            <span className="material-symbols-outlined text-base leading-none">arrow_forward</span>
          </Link>
        </div>

        {cvLoading ? (
          <p className="font-body-md text-body-md text-on-surface-variant">Đang tải dữ liệu CV...</p>
        ) : hasCv ? (
          <>
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
              <div className="p-4 rounded-lg bg-surface-container-low">
                <div className="font-label-sm text-label-sm text-on-surface-variant uppercase tracking-wider mb-1">
                  Kỹ năng
                </div>
                <div className="font-headline-sm text-headline-sm text-on-surface">{skillCount}</div>
              </div>
              <div className="p-4 rounded-lg bg-surface-container-low">
                <div className="font-label-sm text-label-sm text-on-surface-variant uppercase tracking-wider mb-1">
                  Vị trí mục tiêu
                </div>
                <div className="font-title-md text-title-md text-on-surface truncate">
                  {cv?.targetPosition || 'Chưa chọn'}
                </div>
              </div>
              <div className="p-4 rounded-lg bg-surface-container-low">
                <div className="font-label-sm text-label-sm text-on-surface-variant uppercase tracking-wider mb-1">
                  Cấp độ
                </div>
                <div className="font-title-md text-title-md text-on-surface truncate">
                  {cv?.targetLevel || 'Chưa chọn'}
                </div>
              </div>
            </div>
            {cv?.fileName && (
              <p className="font-body-sm text-body-sm text-on-surface-variant mt-4">
                File: {cv.fileName}
              </p>
            )}
          </>
        ) : (
          <div className="flex items-center gap-3 p-4 rounded-lg bg-surface-container-low">
            <span className="material-symbols-outlined text-primary">info</span>
            <p className="font-body-md text-body-md text-on-surface-variant">
              Chưa có CV nào được phân tích. Hãy tải lên PDF/TXT hoặc dán nội dung CV.
            </p>
          </div>
        )}
      </div>

      {/* Quick actions */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        {quickActions.map((action) => {
          const content = (
            <>
              <span
                className={`material-symbols-outlined text-2xl leading-none ${
                  action.to ? 'text-primary' : 'text-outline'
                }`}
              >
                {action.icon}
              </span>
              <div className="mt-3">
                <div
                  className={`font-title-md text-title-md ${
                    action.to ? 'text-on-surface' : 'text-outline'
                  }`}
                >
                  {action.label}
                </div>
                <p className="font-body-sm text-body-sm text-on-surface-variant mt-1">
                  {action.description}
                </p>
              </div>
            </>
          );

          return action.to ? (
            <Link
              key={action.label}
              to={action.to}
              className="bg-surface-container-lowest rounded-xl p-5 border border-outline-variant hover:border-primary hover:shadow-sm transition-all"
            >
              {content}
            </Link>
          ) : (
            <div
              key={action.label}
              aria-disabled="true"
              title="Chưa khả dụng"
              className="bg-surface-container-lowest rounded-xl p-5 border border-outline-variant opacity-70 cursor-not-allowed"
            >
              {content}
              <span className="inline-block mt-3 px-2 py-0.5 rounded bg-surface-container font-label-sm text-label-sm text-on-surface-variant">
                Sắp ra mắt
              </span>
            </div>
          );
        })}
      </div>

      {/* Admin entry */}
      {isAdmin && (
        <Link
          to="/admin/users"
          className="bg-surface-container-lowest rounded-xl p-5 border border-outline-variant hover:border-primary hover:shadow-sm transition-all flex items-center gap-3"
        >
          <span className="material-symbols-outlined text-2xl text-primary leading-none">group</span>
          <div>
            <div className="font-title-md text-title-md text-on-surface">Quản lý người dùng</div>
            <p className="font-body-sm text-body-sm text-on-surface-variant mt-1">
              Xem danh sách, vai trò và trạng thái tài khoản
            </p>
          </div>
        </Link>
      )}
    </div>
  );
};

export default DashboardPage;
