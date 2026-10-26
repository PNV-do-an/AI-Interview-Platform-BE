# CV & Profile Intelligence Dashboard - Implementation Plan

**Project**: React 18 + TypeScript frontend (c:\Users\LENOVO\Downloads\SpringBoot\SpringBoot\frontend)

**Deliverables**: CV upload/parse page with profile intelligence dashboard, skill matrix, knowledge gaps, work experience, and education display.

---

## Key Design Decisions

### 1. Styling Approach
**Decision**: Use Tailwind CSS via CDN in `public/index.html` with fallback inline styles.
**Rationale**: CDN avoids npm package additions and build config changes. Existing codebase uses inline styles; Tailwind classes add modern theming without breaking them. Material Symbols icons already present in mockup.

### 2. Cv.model.ts Structure
**Decision**: Mirror backend DTOs exactly (CvParseResponse, SkillResponse) plus UI-specific helper types.
**Rationale**: Type safety for API responses; reduces runtime bugs. Add enums for ProficiencyLevel and helpers for UI categorization.

### 3. Service Pattern
**Decision**: Follow existing auth.service.ts pattern—cv.service.ts exports object with named methods, uses httpService (auto-token via Bearer interceptor).
**Rationale**: Consistent with codebase. httpService already handles auth; no manual token handling needed in cv.service.

### 4. Component Architecture
**Decision**: One main CVUploadPage component with child subcomponents (CvUploadForm, SkillsMatrix, KnowledgeGaps, etc.) for readability.
**Rationale**: Large page; compositional design prevents prop drilling and enables isolated testing.

### 5. Route Location
**Decision**: Add `/cv` route in App.tsx as a protected route (any authenticated user).
**Rationale**: Sidebar shows "My CV" link in mockup. Protect with ProtectedRoute like /dashboard.

### 6. Empty State Handling
**Decision**: Show empty state card until CV is first uploaded or fetched. Disable certain UI sections until cvId is set.
**Rationale**: Graceful degradation; prevents API errors on initial load.

---

## Implementation Items

### Phase 1: Models & Services

- [ ] 1. Implement Cv.model.ts with all interfaces.
      Create enums for ProficiencyLevel, add SkillCategory helper type (BEGINNER→'Foundational', etc.), define CvParseResponse and SkillResponse per backend DTOs.
      Files: src/models/Cv.model.ts
      Verify: `npm run build` compiles without errors; TypeScript type-check passes.

- [ ] 2. Implement cv.service.ts with methods for upload, parse, get, updatePosition, getSkills.
      Use httpService for auto-token; handle multipart/form-data for file upload.
      Files: src/services/cv.service.ts
      Verify: Service methods compile; axios multipart FormData logic is correct.

- [ ] 3. Implement useCvController.ts hook with state management (cv, skills, loading, errors) and action handlers.
      Fetch CV on mount, handle upload/parse/updatePosition flows.
      Files: src/controllers/useCvController.ts
      Verify: Hook compiles; no TypeScript errors.

### Phase 2: Add Tailwind CSS & Layout Scaffolding

- [ ] 4. Add Tailwind CSS CDN link and custom color variables to public/index.html.
      Include Material Symbols icon link. Verify head section has both stylesheet links and tailwind config.
      Files: public/index.html
      Verify: Page loads in browser; no console errors about missing stylesheets.

- [ ] 5. Create src/views/components/Sidebar.tsx (desktop left sidebar with nav links).
      Show active route indicator on "My CV" link when on /cv. Links: Dashboard, My CV, AI Interview, Study Programs, Interview History.
      Files: src/views/components/Sidebar.tsx
      Verify: Component renders without errors; active link highlighting works.

- [ ] 6. Create src/views/components/PageLayout.tsx wrapper component.
      Layout: fixed sidebar (256px) + flex main area. Use Tailwind grid layout.
      Files: src/views/components/PageLayout.tsx
      Verify: Layout renders; sidebar + main content area display correctly.

### Phase 3: Main CV Page & Sub-Components

- [ ] 7. Create src/views/pages/CVUploadPage.tsx (main page component).
      Render PageLayout with CVUploadForm, ActiveCvStatus, CvAnalysis, SkillsMatrix, KnowledgeGaps, WorkExperience, Education.
      Files: src/views/pages/CVUploadPage.tsx
      Verify: Page compiles; all child components mount.

- [ ] 8. Create src/views/components/CVUploadForm.tsx (tabs: File Upload + Paste Text).
      File upload: drag-drop zone, file input (PDF/TXT). Paste tab: textarea + Parse button.
      Show confirmation modal if CV exists (cvId != null) before upload/parse.
      Files: src/views/components/CVUploadForm.tsx
      Verify: Form renders tabs, drag-drop accepts files, confirmation modal appears on second upload.

- [ ] 9. Create src/views/components/ActiveCvStatus.tsx (card showing file name, parse status, action buttons).
      Display fileName from CV data. Action buttons: "View Details", "Delete" (stub), etc. Show loading spinner during parse.
      Files: src/views/components/ActiveCvStatus.tsx
      Verify: Component renders CV data; displays correctly with loading/error states.

- [ ] 10. Create src/views/components/TargetPositionForm.tsx (form to set target position + level).
       Input: position text field + select for level (Junior/Mid/Senior/Lead/Principal).
       On Save: call PUT /api/v1/cv/position, refresh CV state.
       Files: src/views/components/TargetPositionForm.tsx
       Verify: Form submits; position and level update in CV state; toast or alert shows success.

- [ ] 11. Create src/views/components/AiProfileCard.tsx (right sidebar card).
        Display: candidate name, suggested positions list, skill summary by proficiency tier.
        Responsive: hide on mobile, show on desktop (md breakpoint).
        Files: src/views/components/AiProfileCard.tsx
        Verify: Card renders; skill counts update when CV data changes.

- [ ] 12. Create src/views/components/SkillsMatrix.tsx (table with columns: Skill Name, Category, Experience, Proficiency).
        Category derived from proficiency level (BEGINNER→'Foundational', INTERMEDIATE→'Growing', etc.).
        Rows: one per skill. Sort by proficiency descending.
        Files: src/views/components/SkillsMatrix.tsx
        Verify: Table renders all skills from CV.skills; category mapping is correct.

- [ ] 13. Create src/views/components/KnowledgeGaps.tsx (list of gap cards).
        Each card: gap name + priority label ('High Priority', 'Medium Priority').
        Button: "Add to Study Plan" (stub for now).
        Files: src/views/components/KnowledgeGaps.tsx
        Verify: Cards render; count matches CV.knowledgeGaps length.

- [ ] 14. Create src/views/components/WorkExperienceTimeline.tsx (timeline of experience items).
        Display: company, role, duration string, achievements list (bullets).
        Timeline visual: vertical line with company logos or initials on left.
        Files: src/views/components/WorkExperienceTimeline.tsx
        Verify: Timeline renders all experience items; layout is vertical.

- [ ] 15. Create src/views/components/EducationCards.tsx (grid of education items).
        Card: institution, degree, year. 2 column layout on desktop, 1 on mobile.
        Files: src/views/components/EducationCards.tsx
        Verify: Cards render; grid layout is responsive.

- [ ] 16. Create src/views/components/CvAnalysis.tsx (suggested positions & executive summary).
        Display: suggestedPositions as cards, each with match % indicator. Show sample executive summary text.
        Files: src/views/components/CvAnalysis.tsx
        Verify: Position cards render; match % displays correctly.

### Phase 4: Confirmation Modal & Toast

- [ ] 17. Create src/views/components/ConfirmationModal.tsx (reusable modal for upload/parse confirmation).
        Props: title, message, onConfirm, onCancel. Show when CV already exists.
        Files: src/views/components/ConfirmationModal.tsx
        Verify: Modal displays; buttons trigger callbacks.

- [ ] 18. Create src/views/components/Toast.tsx (simple notification component).
        Props: message, type ('success'|'error'|'info'), onClose.
        Display at top-right, auto-dismiss after 3 seconds.
        Files: src/views/components/Toast.tsx
        Verify: Toast displays; auto-dismisses; can be closed manually.

### Phase 5: Routing & Integration

- [ ] 19. Update App.tsx to add /cv protected route.
        Add route: `<Route element={<ProtectedRoute />}><Route path="/cv" element={<CVUploadPage />} /></Route>`.
        Verify: Route compiles; `/cv` path accessible when authenticated.
        Files: src/App.tsx
        Verify: `npm run build` succeeds; route is accessible in browser (after login mock).

- [ ] 20. Update src/views/components/Sidebar.tsx (create if not done in Phase 2) to include "My CV" link.
        Link text: "My CV", icon: material-symbols-outlined "description", data-path="my-cv".
        Active state: highlight when location.pathname === '/cv'.
        Files: src/views/components/Sidebar.tsx
        Verify: Sidebar shows all 5 nav links; "My CV" is highlighted on /cv route.

### Phase 6: Edge Cases & Error Handling

- [ ] 21. Add empty CV state handling and error boundaries.
        When cvId is null (no CV uploaded yet), show placeholder card: "No CV uploaded yet. Upload or paste your resume above."
        Add try-catch in useCvController for API errors; store error messages in state.
        Files: src/controllers/useCvController.ts, src/views/pages/CVUploadPage.tsx
        Verify: Empty state displays correctly; API errors show toast messages.

- [ ] 22. Add file validation for upload (PDF/TXT only, size < 5MB).
        Validate in CVUploadForm before calling upload service.
        Show error toast if file fails validation.
        Files: src/views/components/CVUploadForm.tsx
        Verify: Upload rejects non-PDF/TXT files; shows error message.

- [ ] 23. Add loading spinners to async operations.
        Show spinner in: file upload, parse, position update flows. Disable form inputs during loading.
        Files: src/views/components/CVUploadForm.tsx, TargetPositionForm.tsx
        Verify: Spinners display during API calls; inputs are disabled.

---

## File Summary

### New Files to Create (23 items total)

**Models:**
- src/models/Cv.model.ts

**Services:**
- src/services/cv.service.ts

**Controllers:**
- src/controllers/useCvController.ts

**Components (reusable):**
- src/views/components/Sidebar.tsx
- src/views/components/PageLayout.tsx
- src/views/components/CVUploadForm.tsx
- src/views/components/ActiveCvStatus.tsx
- src/views/components/TargetPositionForm.tsx
- src/views/components/AiProfileCard.tsx
- src/views/components/SkillsMatrix.tsx
- src/views/components/KnowledgeGaps.tsx
- src/views/components/WorkExperienceTimeline.tsx
- src/views/components/EducationCards.tsx
- src/views/components/CvAnalysis.tsx
- src/views/components/ConfirmationModal.tsx
- src/views/components/Toast.tsx

**Pages:**
- src/views/pages/CVUploadPage.tsx

**Public:**
- public/index.html (modified to add Tailwind CDN + Material Symbols)

**App:**
- src/App.tsx (modified to add /cv route)

---

## Color Palette (Tailwind Custom Colors)

From mockup, defined in Tailwind config via CDN:
- Primary: #3525cd, Primary Container: #4f46e5
- Secondary: #006591, Secondary Container: #39b8fd
- Surface: #f8f9ff, Surface Container: #e5eeff
- On-Surface: #0b1c30, On-Surface Variant: #464555
- Error: #ba1a1a, Error Container: #ffdad6
- Outline: #777587

---

## API Endpoints (Do Not Modify Backend)

All handled by cv.service.ts:
- POST /api/v1/cv/upload (multipart/form-data, field: 'file')
- POST /api/v1/cv/parse-text (JSON: { cvText: string })
- GET /api/v1/cv
- PUT /api/v1/cv/position (JSON: { position: string, level: string })
- GET /api/v1/cv/skills

All use httpService for auto-token Bearer auth.

---

## Build & Test

**Build command**: `npm run build`
**Test command** (if tests added): `npm run test`
**Verify full workflow**:
1. Run `npm start`, navigate to `/login`
2. Login with valid credentials (or mock)
3. Navigate to `/cv` via sidebar
4. Upload a sample PDF/TXT CV
5. Confirm modal appears
6. Verify CV data displays in all sections
7. Update target position and verify PUT request succeeds
8. Check all components render without console errors

---

## Notes

- **Token Handling**: httpService interceptor handles Bearer auth automatically. CV service methods need no manual token injection.
- **Tailwind Setup**: CDN script in index.html; custom colors via tailwind.config object in script tag.
- **Component State**: useCvController manages fetch, upload, parse, position update via separate action methods.
- **Error Toasts**: Toast component auto-dismisses; can be extended to a context provider if needed app-wide.
- **Responsive Design**: Sidebar hidden on mobile (via Tailwind md: breakpoint); main content full-width below md.
- **Empty State**: Friendly message when no CV uploaded; form enabled for new upload.
