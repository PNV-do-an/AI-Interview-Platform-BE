import React from 'react';
import { NavLink, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';

interface NavItem {
  label: string;
  icon: string;
  to?: string;
}

const NAV_ITEMS: NavItem[] = [
  { label: 'Dashboard', icon: 'dashboard', to: '/dashboard' },
  { label: 'My CV', icon: 'description', to: '/cv' },
  { label: 'AI Interview', icon: 'smart_toy' },
  { label: 'Study Programs', icon: 'school', to: '/study' },
  { label: 'Interview History', icon: 'history' },
];

const ROLE_LABELS: Record<string, string> = {
  ROLE_USER: 'Người dùng',
  ROLE_STAFF: 'Nhân viên',
  ROLE_ADMIN: 'Quản trị viên',
  ROLE_INTERVIEWER: 'Phỏng vấn viên',
};

const PAGE_TITLES: Record<string, string> = {
  '/dashboard': 'Dashboard',
  '/study': 'Study Programs',
  '/admin/users': 'User Management',
  '/admin/positions': 'Vị trí & JD',
};

// Detail routes such as /study/programs/1 have no exact entry, so fall back to the longest
// known prefix.
const resolvePageTitle = (pathname: string): string => {
  if (PAGE_TITLES[pathname]) return PAGE_TITLES[pathname];
  const prefix = Object.keys(PAGE_TITLES).find((path) => pathname.startsWith(path));
  return prefix ? PAGE_TITLES[prefix] : 'TalentAI';
};

const navItemClass = ({ isActive }: { isActive: boolean }) =>
  `flex items-center gap-3 px-3 py-2.5 rounded-lg transition-colors ${
    isActive
      ? 'bg-primary-container text-on-primary font-title-md text-title-md'
      : 'text-on-surface-variant hover:bg-surface-container hover:text-on-surface font-label-lg text-label-lg'
  }`;

const AppLayout: React.FC = () => {
  const { user, logout } = useAuth();
  const { pathname } = useLocation();

  const isAdmin = user?.role === 'ROLE_ADMIN';
  const title = resolvePageTitle(pathname);
  const roleLabel = ROLE_LABELS[user?.role ?? ''] ?? user?.role;

  return (
    <div className="flex h-screen bg-background">
      {/* Sidebar */}
      <aside className="flex fixed left-0 top-0 h-full w-64 bg-surface-container-lowest shadow-[0_1px_8px_rgba(0,0,0,0.04)] z-50 flex-col justify-between">
        <div className="flex flex-col">
          {/* Logo */}
          <div className="h-16 px-4 flex items-center gap-3 bg-surface-container-lowest">
            <div className="w-8 h-8 rounded-full bg-primary flex items-center justify-center flex-shrink-0">
              <span className="material-symbols-outlined text-white text-lg">school</span>
            </div>
            <div className="flex flex-col">
              <span className="font-headline-sm text-title-md text-on-surface tracking-tight leading-none">
                TalentAI
              </span>
              <span className="font-label-sm text-label-sm text-on-surface-variant">
                Career Intelligence
              </span>
            </div>
          </div>

          {/* Navigation */}
          <div className="px-4 py-3">
            <nav className="flex flex-col gap-1">
              {NAV_ITEMS.map((item) =>
                item.to ? (
                  <NavLink key={item.label} to={item.to} className={navItemClass}>
                    <span className="material-symbols-outlined text-lg leading-none">{item.icon}</span>
                    <span>{item.label}</span>
                  </NavLink>
                ) : (
                  <span
                    key={item.label}
                    aria-disabled="true"
                    title="Chưa khả dụng"
                    className="flex items-center gap-3 px-3 py-2.5 rounded-lg font-label-lg text-label-lg text-outline cursor-not-allowed select-none"
                  >
                    <span className="material-symbols-outlined text-lg leading-none">{item.icon}</span>
                    <span>{item.label}</span>
                    <span className="ml-auto px-1.5 py-0.5 rounded bg-surface-container font-label-sm text-label-sm text-on-surface-variant">
                      Soon
                    </span>
                  </span>
                )
              )}

              {isAdmin && (
                <>
                  <NavLink to="/admin/users" className={navItemClass}>
                    <span className="material-symbols-outlined text-lg leading-none">group</span>
                    <span>Users</span>
                  </NavLink>
                  <NavLink to="/admin/positions" className={navItemClass}>
                    <span className="material-symbols-outlined text-lg leading-none">work</span>
                    <span>Positions &amp; JD</span>
                  </NavLink>
                </>
              )}
            </nav>
          </div>
        </div>

        {/* Signed-in user */}
        <div className="p-4 flex flex-col gap-3">
          <div className="p-3 rounded-lg bg-surface-container-low">
            <div className="font-label-sm text-label-sm text-on-surface-variant uppercase tracking-wider mb-1">
              Signed in as
            </div>
            <div className="font-title-md text-title-md text-on-surface truncate">
              {user?.fullName || 'Unknown'}
            </div>
            <div className="font-body-sm text-body-sm text-on-surface-variant truncate">
              {user?.email}
            </div>
            <div className="mt-2 inline-flex items-center gap-1.5 px-2 py-1 rounded-full bg-primary-fixed text-on-primary-fixed font-label-sm text-label-sm">
              <span className="material-symbols-outlined text-xs leading-none">badge</span>
              <span>{roleLabel}</span>
            </div>
          </div>

          <button
            type="button"
            onClick={logout}
            className="flex items-center gap-3 px-3 py-2.5 rounded-lg text-on-surface-variant hover:bg-error-container hover:text-on-error-container transition-colors font-label-lg text-label-lg"
          >
            <span className="material-symbols-outlined text-lg leading-none">logout</span>
            <span>Đăng xuất</span>
          </button>
        </div>
      </aside>

      {/* Main content */}
      <div className="flex-1 flex flex-col overflow-hidden ml-64">
        {/* TopBar */}
        <div className="h-16 bg-surface-container-lowest shadow-[0_1px_8px_rgba(0,0,0,0.04)] px-6 flex items-center justify-between border-b border-outline-variant">
          <h1 className="font-headline-md text-headline-md text-on-surface">{title}</h1>
          <div className="flex items-center gap-4">
            <div className="w-10 h-10 rounded-full bg-primary-container flex items-center justify-center">
              <span className="text-on-primary font-label-lg">
                {user?.fullName?.charAt(0) || '?'}
              </span>
            </div>
          </div>
        </div>

        {/* Page */}
        <main className="flex-1 overflow-y-auto p-6">
          <Outlet />
        </main>
      </div>
    </div>
  );
};

export default AppLayout;
