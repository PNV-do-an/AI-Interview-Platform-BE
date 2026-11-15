import React, { useCallback, useEffect, useState } from 'react';
import { useAdminPositionController } from '../../controllers/useAdminPositionController';
import { AdminPosition, POSITION_LEVELS, PositionPayload } from '../../models/Position.model';

interface FormState {
  title: string;
  level: string;
  description: string;
  jdText: string;
}

const EMPTY_FORM: FormState = { title: '', level: '', description: '', jdText: '' };
const MAX_JD_LENGTH = 20000;

const formatDate = (value: string | null): string => {
  if (!value) return '—';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? '—' : date.toLocaleDateString('vi-VN');
};

const AdminPositionPage: React.FC = () => {
  const ctrl = useAdminPositionController();
  const {
    positions,
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
  } = ctrl;

  const [searchInput, setSearchInput] = useState('');
  const [activeFilter, setActiveFilter] = useState('');
  const [showForm, setShowForm] = useState(false);
  const [editing, setEditing] = useState<AdminPosition | null>(null);
  const [form, setForm] = useState<FormState>(EMPTY_FORM);
  const [skills, setSkills] = useState<string[]>([]);
  const [skillInput, setSkillInput] = useState('');
  const [notice, setNotice] = useState('');

  useEffect(() => {
    fetchPositions(0);
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const openCreate = () => {
    setEditing(null);
    setForm(EMPTY_FORM);
    setSkills([]);
    setSkillInput('');
    setNotice('');
    setShowForm(true);
  };

  const openEdit = (position: AdminPosition) => {
    setEditing(position);
    setForm({
      title: position.title,
      level: position.level ?? '',
      description: position.description ?? '',
      jdText: position.jdText ?? '',
    });
    setSkills(position.requiredSkills);
    setSkillInput('');
    setNotice('');
    setShowForm(true);
  };

  const closeForm = () => {
    setShowForm(false);
    setEditing(null);
    setSkillInput('');
    setNotice('');
  };

  const updateField = (key: keyof FormState, value: string) =>
    setForm((current) => ({ ...current, [key]: value }));

  const addSkill = (raw: string) => {
    const name = raw.trim().replace(/\s+/g, ' ');
    if (!name) return;
    setSkills((current) =>
      current.some((skill) => skill.toLowerCase() === name.toLowerCase())
        ? current
        : [...current, name],
    );
  };

  const removeSkill = (name: string) =>
    setSkills((current) => current.filter((skill) => skill !== name));

  const handleSkillKeyDown = (event: React.KeyboardEvent<HTMLInputElement>) => {
    if (event.key !== 'Enter' && event.key !== ',') return;
    event.preventDefault();
    addSkill(skillInput);
    setSkillInput('');
  };

  const handleExtract = useCallback(async () => {
    const jd = form.jdText.trim();
    if (!jd || extracting) return;
    setNotice('');
    const extracted = await extractSkills(jd);
    if (extracted.length === 0) return;

    const merged = [...skills];
    let added = 0;
    extracted.forEach((name) => {
      if (!merged.some((skill) => skill.toLowerCase() === name.toLowerCase())) {
        merged.push(name);
        added += 1;
      }
    });
    setSkills(merged);
    setNotice(
      added > 0
        ? `AI đã trích xuất ${extracted.length} kỹ năng từ JD. Bạn có thể sửa lại trước khi lưu.`
        : 'Các kỹ năng AI trích xuất đã có trong danh sách.',
    );
  }, [form.jdText, extracting, extractSkills, skills]);

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (saving) return;
    if (!form.title.trim()) {
      setNotice('Vui lòng nhập tên vị trí trước khi lưu.');
      return;
    }

    const payload: PositionPayload = {
      title: form.title.trim(),
      ...(form.level.trim() ? { level: form.level.trim() } : {}),
      ...(form.description.trim() ? { description: form.description.trim() } : {}),
      ...(form.jdText.trim() ? { jdText: form.jdText.trim() } : {}),
      ...(skills.length > 0 ? { skills } : {}),
    };

    const saved = editing
      ? await updatePosition(editing.id, payload)
      : await createPosition(payload);
    if (saved) closeForm();
  };

  const handleSearch = () => applyFilters({ search: searchInput, active: activeFilter });
  const handleActiveChange = (value: string) => {
    setActiveFilter(value);
    applyFilters({ search: searchInput, active: value });
  };

  const banners = (
    <>
      {error && (
        <div className="flex items-start gap-2 p-4 rounded-lg bg-error-container text-on-error-container font-body-md text-body-md">
          <span className="material-symbols-outlined text-lg leading-none">error</span>
          <span>{error}</span>
        </div>
      )}
      {successMsg && (
        <div className="flex items-start gap-2 p-4 rounded-lg bg-[#dcfce7] text-[#166534] font-body-md text-body-md">
          <span className="material-symbols-outlined text-lg leading-none">check_circle</span>
          <span>{successMsg}</span>
        </div>
      )}
    </>
  );

  return (
    <div className="flex flex-col gap-6">
      <div className="bg-surface-container-lowest rounded-xl p-6 border border-outline-variant flex flex-wrap items-center justify-between gap-4">
        <div>
          <h2 className="font-headline-md text-headline-md text-on-surface">Vị trí &amp; dữ liệu JD</h2>
          <p className="font-body-md text-body-md text-on-surface-variant mt-1">
            Mỗi vị trí kèm JD sẽ được dùng để so khớp kỹ năng trong CV và gợi ý việc làm phù hợp cho
            người dùng.
          </p>
        </div>
        <button
          type="button"
          onClick={() => (showForm ? closeForm() : openCreate())}
          className="inline-flex items-center gap-2 px-4 py-2.5 rounded-lg bg-primary text-on-primary font-label-lg text-label-lg hover:bg-primary-container transition-colors"
        >
          <span className="material-symbols-outlined text-base leading-none">
            {showForm ? 'close' : 'add'}
          </span>
          {showForm ? 'Đóng' : 'Thêm vị trí / JD'}
        </button>
      </div>

      {banners}

      {showForm && (
        <form
          onSubmit={handleSubmit}
          className="bg-surface-container-lowest rounded-xl p-6 border border-outline-variant flex flex-col gap-4"
        >
          <div className="flex items-center justify-between gap-3">
            <h3 className="font-title-md text-title-md text-on-surface">
              {editing ? `Sửa vị trí: ${editing.title}` : 'Thêm vị trí mới từ JD'}
            </h3>
            {editing && (
              <span className="font-label-sm text-label-sm text-on-surface-variant">
                slug: {editing.slug}
              </span>
            )}
          </div>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <label className="flex flex-col gap-1 md:col-span-1">
              <span className="font-label-md text-label-md text-on-surface-variant">Tên vị trí *</span>
              <input
                type="text"
                value={form.title}
                onChange={(event) => updateField('title', event.target.value)}
                placeholder="VD: Backend Java Engineer"
                maxLength={100}
                className="px-3 py-2 rounded-lg border border-outline-variant bg-surface-container-lowest font-body-md text-body-md text-on-surface focus:outline-none focus:border-primary"
              />
            </label>

            <label className="flex flex-col gap-1">
              <span className="font-label-md text-label-md text-on-surface-variant">Level</span>
              <input
                type="text"
                list="admin-position-levels"
                value={form.level}
                onChange={(event) => updateField('level', event.target.value)}
                placeholder="VD: MID"
                maxLength={50}
                className="px-3 py-2 rounded-lg border border-outline-variant bg-surface-container-lowest font-body-md text-body-md text-on-surface focus:outline-none focus:border-primary"
              />
              <datalist id="admin-position-levels">
                {POSITION_LEVELS.map((level) => (
                  <option key={level} value={level} />
                ))}
              </datalist>
            </label>

            <label className="flex flex-col gap-1">
              <span className="font-label-md text-label-md text-on-surface-variant">Mô tả ngắn</span>
              <input
                type="text"
                value={form.description}
                onChange={(event) => updateField('description', event.target.value)}
                placeholder="VD: Phát triển API cho hệ thống tuyển dụng"
                maxLength={4000}
                className="px-3 py-2 rounded-lg border border-outline-variant bg-surface-container-lowest font-body-md text-body-md text-on-surface focus:outline-none focus:border-primary"
              />
            </label>
          </div>

          <label className="flex flex-col gap-1">
            <span className="font-label-md text-label-md text-on-surface-variant">Nội dung JD</span>
            <textarea
              value={form.jdText}
              onChange={(event) => updateField('jdText', event.target.value)}
              rows={10}
              maxLength={MAX_JD_LENGTH}
              placeholder="Dán toàn bộ JD vào đây. AI sẽ đọc JD và gợi ý danh sách kỹ năng yêu cầu."
              className="px-3 py-2 rounded-lg border border-outline-variant bg-surface-container-lowest font-body-md text-body-md text-on-surface focus:outline-none focus:border-primary resize-y"
            />
            <span className="font-label-sm text-label-sm text-on-surface-variant self-end">
              {form.jdText.length}/{MAX_JD_LENGTH}
            </span>
          </label>

          <div className="flex flex-wrap items-center gap-3">
            <button
              type="button"
              onClick={handleExtract}
              disabled={extracting || !form.jdText.trim()}
              className="inline-flex items-center gap-2 px-4 py-2 rounded-lg border border-primary text-primary font-label-lg text-label-lg hover:bg-primary-fixed transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
            >
              <span className="material-symbols-outlined text-base leading-none">
                {extracting ? 'progress_activity' : 'auto_awesome'}
              </span>
              {extracting ? 'AI đang đọc JD...' : 'Trích xuất kỹ năng bằng AI'}
            </button>
            <span className="font-body-sm text-body-sm text-on-surface-variant">
              Không bắt buộc: nếu bỏ trống, hệ thống tự trích xuất kỹ năng từ JD khi lưu.
            </span>
          </div>

          <div className="flex flex-col gap-2">
            <span className="font-label-md text-label-md text-on-surface-variant">
              Kỹ năng yêu cầu ({skills.length})
            </span>
            <div className="flex flex-wrap gap-2 min-h-[2rem]">
              {skills.length === 0 && (
                <span className="font-body-sm text-body-sm text-on-surface-variant">
                  Chưa có kỹ năng nào. Bấm "Trích xuất kỹ năng bằng AI" hoặc thêm thủ công bên dưới.
                </span>
              )}
              {skills.map((skill) => (
                <span
                  key={skill}
                  className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full bg-surface-container text-on-surface-variant font-label-sm text-label-sm"
                >
                  {skill}
                  <button
                    type="button"
                    onClick={() => removeSkill(skill)}
                    className="material-symbols-outlined text-xs leading-none hover:text-error"
                    aria-label={`Xóa kỹ năng ${skill}`}
                  >
                    close
                  </button>
                </span>
              ))}
            </div>
            <input
              type="text"
              value={skillInput}
              onChange={(event) => setSkillInput(event.target.value)}
              onKeyDown={handleSkillKeyDown}
              onBlur={() => {
                addSkill(skillInput);
                setSkillInput('');
              }}
              placeholder="Thêm kỹ năng rồi nhấn Enter"
              maxLength={100}
              className="px-3 py-2 rounded-lg border border-outline-variant bg-surface-container-lowest font-body-md text-body-md text-on-surface focus:outline-none focus:border-primary"
            />
            {notice && (
              <span className="font-body-sm text-body-sm text-on-surface-variant">{notice}</span>
            )}
          </div>

          <div className="flex justify-end gap-3">
            <button
              type="button"
              onClick={closeForm}
              className="px-4 py-2.5 rounded-lg border border-outline-variant font-label-lg text-label-lg text-on-surface-variant hover:bg-surface-container transition-colors"
            >
              Hủy
            </button>
            <button
              type="submit"
              disabled={saving}
              className="inline-flex items-center gap-2 px-4 py-2.5 rounded-lg bg-primary text-on-primary font-label-lg text-label-lg hover:bg-primary-container transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
            >
              <span className="material-symbols-outlined text-base leading-none">save</span>
              {saving ? 'Đang lưu...' : editing ? 'Cập nhật vị trí' : 'Lưu vị trí'}
            </button>
          </div>
        </form>
      )}

      <div className="flex flex-wrap items-center gap-3">
        <input
          type="text"
          value={searchInput}
          onChange={(event) => setSearchInput(event.target.value)}
          onKeyDown={(event) => event.key === 'Enter' && handleSearch()}
          placeholder="Tìm theo tên vị trí..."
          className="flex-1 min-w-[220px] px-3 py-2 rounded-lg border border-outline-variant bg-surface-container-lowest font-body-md text-body-md text-on-surface focus:outline-none focus:border-primary"
        />
        <button
          type="button"
          onClick={handleSearch}
          className="px-4 py-2 rounded-lg bg-primary text-on-primary font-label-lg text-label-lg hover:bg-primary-container transition-colors"
        >
          Tìm
        </button>
        <select
          value={activeFilter}
          onChange={(event) => handleActiveChange(event.target.value)}
          className="px-3 py-2 rounded-lg border border-outline-variant bg-surface-container-lowest font-body-md text-body-md text-on-surface focus:outline-none focus:border-primary"
        >
          <option value="">Tất cả trạng thái</option>
          <option value="true">Đang bật</option>
          <option value="false">Đã tắt</option>
        </select>
      </div>

      {loading ? (
        <div className="bg-surface-container-lowest rounded-xl p-6 border border-outline-variant">
          <p className="font-body-md text-body-md text-on-surface-variant">Đang tải danh sách vị trí...</p>
        </div>
      ) : !positions || positions.content.length === 0 ? (
        <div className="bg-surface-container-lowest rounded-xl p-8 border border-outline-variant flex flex-col items-center text-center gap-3">
          <span className="material-symbols-outlined text-4xl text-outline leading-none">work</span>
          <div>
            <p className="font-title-md text-title-md text-on-surface">Chưa có vị trí nào</p>
            <p className="font-body-sm text-body-sm text-on-surface-variant mt-1">
              Thêm vị trí kèm JD để hệ thống có dữ liệu gợi ý việc làm phù hợp cho người dùng.
            </p>
          </div>
          <button
            type="button"
            onClick={openCreate}
            className="inline-flex items-center gap-2 px-4 py-2 rounded-lg border border-primary text-primary font-label-lg text-label-lg hover:bg-primary-fixed transition-colors"
          >
            <span className="material-symbols-outlined text-base leading-none">add</span>
            Thêm vị trí
          </button>
        </div>
      ) : (
        <>
          <div className="overflow-x-auto bg-surface-container-lowest rounded-xl border border-outline-variant">
            <table className="w-full text-left">
              <thead className="bg-surface-container-low">
                <tr>
                  {['Vị trí', 'Kỹ năng yêu cầu', 'JD', 'Trạng thái', 'Cập nhật', 'Hành động'].map((header) => (
                    <th
                      key={header}
                      className="px-4 py-3 font-label-md text-label-md text-on-surface-variant whitespace-nowrap"
                    >
                      {header}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {positions.content.map((position) => (
                  <tr key={position.id} className="border-t border-outline-variant align-top">
                    <td className="px-4 py-3">
                      <div className="font-title-md text-title-md text-on-surface">{position.title}</div>
                      <div className="font-label-sm text-label-sm text-on-surface-variant">
                        {position.level ? `${position.level} · ` : ''}
                        {position.slug}
                      </div>
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex flex-wrap gap-1 max-w-[320px]">
                        {position.requiredSkills.slice(0, 4).map((skill) => (
                          <span
                            key={skill}
                            className="px-2 py-0.5 rounded-full bg-surface-container font-label-sm text-label-sm text-on-surface-variant"
                          >
                            {skill}
                          </span>
                        ))}
                        {position.requiredSkills.length > 4 && (
                          <span className="px-2 py-0.5 rounded-full bg-surface-container font-label-sm text-label-sm text-on-surface-variant">
                            +{position.requiredSkills.length - 4}
                          </span>
                        )}
                        {position.requiredSkills.length === 0 && (
                          <span className="font-body-sm text-body-sm text-on-surface-variant">—</span>
                        )}
                      </div>
                    </td>
                    <td className="px-4 py-3 max-w-[240px]">
                      {position.jdText ? (
                        <span
                          className="font-body-sm text-body-sm text-on-surface-variant line-clamp-2"
                          title={position.jdText}
                        >
                          {position.jdText}
                        </span>
                      ) : (
                        <span className="font-body-sm text-body-sm text-on-surface-variant">—</span>
                      )}
                    </td>
                    <td className="px-4 py-3">
                      <span
                        className={`inline-flex items-center px-2.5 py-0.5 rounded-full font-label-sm text-label-sm ${
                          position.active
                            ? 'bg-[#dcfce7] text-[#166534]'
                            : 'bg-surface-container text-on-surface-variant'
                        }`}
                      >
                        {position.active ? 'Đang bật' : 'Đã tắt'}
                      </span>
                    </td>
                    <td className="px-4 py-3 font-body-sm text-body-sm text-on-surface-variant whitespace-nowrap">
                      {formatDate(position.updatedAt)}
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex gap-2">
                        <button
                          type="button"
                          onClick={() => openEdit(position)}
                          className="px-3 py-1 rounded-lg border border-outline-variant font-label-lg text-label-lg text-on-surface-variant hover:border-primary hover:text-primary transition-colors"
                        >
                          Sửa
                        </button>
                        <button
                          type="button"
                          onClick={() => changeStatus(position.id, !position.active)}
                          className={`px-3 py-1 rounded-lg font-label-lg text-label-lg transition-colors ${
                            position.active
                              ? 'bg-surface-container text-on-surface-variant hover:bg-error-container hover:text-on-error-container'
                              : 'bg-primary text-on-primary hover:bg-primary-container'
                          }`}
                        >
                          {position.active ? 'Tắt' : 'Bật'}
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="flex flex-wrap items-center justify-between gap-3">
            <span className="font-body-sm text-body-sm text-on-surface-variant">
              Tổng: {positions.totalElements} vị trí
            </span>
            {positions.totalPages > 1 && (
              <div className="flex items-center gap-1">
                <button
                  type="button"
                  disabled={currentPage === 0}
                  onClick={() => fetchPositions(currentPage - 1)}
                  className="px-3 py-1.5 rounded-lg border border-outline-variant font-label-lg text-label-lg disabled:opacity-40 disabled:cursor-not-allowed"
                >
                  ‹
                </button>
                {Array.from({ length: positions.totalPages }, (_, index) => index)
                  .filter((page) => Math.abs(page - currentPage) <= 2)
                  .map((page) => (
                    <button
                      key={page}
                      type="button"
                      onClick={() => fetchPositions(page)}
                      className={`px-3 py-1.5 rounded-lg font-label-lg text-label-lg ${
                        page === currentPage
                          ? 'bg-primary text-on-primary'
                          : 'border border-outline-variant text-on-surface-variant'
                      }`}
                    >
                      {page + 1}
                    </button>
                  ))}
                <button
                  type="button"
                  disabled={currentPage >= positions.totalPages - 1}
                  onClick={() => fetchPositions(currentPage + 1)}
                  className="px-3 py-1.5 rounded-lg border border-outline-variant font-label-lg text-label-lg disabled:opacity-40 disabled:cursor-not-allowed"
                >
                  ›
                </button>
              </div>
            )}
          </div>
        </>
      )}
    </div>
  );
};

export default AdminPositionPage;
