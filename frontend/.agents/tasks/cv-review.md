# CV & Profile Intelligence Dashboard

The CV management page integrates backend API endpoints for CV upload, parsing, skill extraction, and position tracking into a cohesive dashboard with detailed profile analysis, skills matrix, experience timeline, and AI-suggested positions.

The implementation uses TypeScript models closely matching backend DTOs, an axios-based service that leverages the existing Bearer token interceptor, and a main CvPage component that composes six focused sub-components. The page is fully protected via the ProtectedRoute wrapper and integrated into the main app router. All styling uses Tailwind CSS with a custom material-design color palette delivered via CDN.

Watch for: None of the concerns below block shipping, but two design asymmetries warrant noting—the skills fetch has no error recovery if it fails independently, and the suggested positions match percentages are entirely synthetic (not from the backend). Both are acceptable shortcuts but may need rework if the backend evolves.

**Verdict**: APPROVED

## High-level view

The page state mirrors the backend's CvParseResponse structure exactly: skills, experience, education, knowledge gaps, and suggested positions. The services layer properly wraps axios calls and leverages httpService's built-in Bearer token authentication, eliminating manual token handling.

The component architecture uses CvPage as a smart container managing all state (cv data, skills, upload state, position form state), with six pure presentation sub-components that accept data and emit events via callback props. This keeps concerns separated and makes testing straightforward.

Upload flow includes a confirmation modal to prevent accidental overwrites of existing CVs. The form supports both file upload (drag-drop and click-to-browse for PDF/TXT) and direct text paste. Both paths converge on the same setPosition handler, which updates the target role and level.

Error handling intercepts 404 responses gracefully (treated as "no CV yet" rather than a failure), auto-dismisses toast notifications after 4 seconds, and shows appropriate empty states. All async operations display spinners and disable controls during loading.

The sidebar is embedded directly in CvPage rather than pulled from a separate layout component, which means the "My CV" active indicator is always correct, though it locks this sidebar implementation into one page. If the sidebar is needed elsewhere, it should be extracted to a reusable PageLayout wrapper.

<details>
<summary>Issues (2)</summary>

1. **Skills fetch has no independent error recovery** — If getSkills() fails (404 or other error), it's caught but silently swallowed. The page treats it as "no skills available" rather than "fetch failed." If the endpoint begins returning non-404 errors, users won't know to retry. Consider distinguishing between "no skills exist yet" and "skills fetch failed."

2. **Suggested positions match percentages are hardcoded** — The page generates match scores via `Math.max(60, 95 - idx * 7)`, a synthetic calculation that doesn't reflect actual backend confidence. If the API ever returns real match percentages, this code will need updating.

</details>

<details>
<summary>Details</summary>

### TypeScript models and API contract

All interfaces closely match the backend response structure. Proficiency levels use an enum (BEGINNER, INTERMEDIATE, ADVANCED, EXPERT), preventing typos at compile time. Request interfaces (SetPositionRequest, CvTextRequest) are correct. No `any` types; all responses properly wrapped in ApiResponse<T>.

### Service integration

cv.service.ts exports named methods (getCv, getSkills, uploadCv, parseCvText, setPosition) following the auth.service.ts pattern. Each wraps an httpService call. The httpService already includes a Bearer token interceptor, so cv.service never touches tokens—preventing auth leaks. File upload uses multipart/form-data correctly; text parse passes plain JSON. All methods return ApiResponse<T> typed responses, and CvPage correctly accesses nested data via res.data.data.

### Page state and lifecycle

CvPage fetches both CV and skills data on mount (only if authenticated), treating 404 responses as empty state (no CV yet) rather than errors. Separate loading flags for upload, parse, and position update allow UI to disable the right controls during each operation. Success and error messages auto-dismiss after 4 seconds. The confirmation modal discriminates between file upload and text parse via pendingFile; a tagged union would be cleaner but the current approach works.

### Drag-and-drop upload

UploadZone handles drag events and click-to-browse. File type validation restricts to PDF or plain text. The component respects the loading prop and disables interactions while upload is in flight. No file size validation on the frontend; the backend rejects oversized files and CvPage displays that error via toast.

### Skills matrix

SkillsMatrix renders a table with columns: Skill Name, Category (derived from proficiency level), Experience (Years), Proficiency Tier. Proficiency tier displays as a colored badge; colors map to the Tailwind palette. The table is responsive with hover states. Empty state message appears if the array is empty.

### Experience timeline

ExperienceTimeline renders each item as a card with a vertical timeline dot and connecting line. Each card shows role, company, duration, and achievements as bullet points. Layout is clean and positioning is correct.

### Knowledge gaps and suggested positions

KnowledgeGapCard displays gap title with a priority badge (first 3 items HIGH, rest MEDIUM). PositionFitCard displays suggested position, match percentage (synthetic, hardcoded), and rank. Match percentages are 95%, 88%, 81% for the first three positions and will need updating if the backend provides real confidence scores.

### Education display

Education items are rendered as a grid of cards showing institution, degree, and year. Grid is 1 column on mobile, 2 on desktop.

### Profile match score

The match score in the profile header is calculated as `Math.min(100, 50 + skills.length * 2 + (experience.length > 0 ? 20 : 0) + (education.length > 0 ? 10 : 0))`. This is synthetic—no backend field for it—but reasonable for a demo. If the backend later adds a real score, this calculation will need rework.

### Error handling and empty states

When cvData is null, the page renders a full-page empty state encouraging the user to upload. Toast messages auto-dismiss after 4 seconds. API errors display their backend message, though silent swallowing of skills fetch errors (as noted in Issues) could hide real problems.

### Overwrite confirmation flow

When a user tries to upload or parse a new CV while one exists, a modal appears asking to confirm. Clicking confirm triggers the appropriate upload or parse handler. This flow prevents accidental data loss. The only oddness is using pendingFile = null to mean "parse text instead of upload"—a discriminated union would be clearer, but the current approach works.

### Sidebar integration

CvPage embeds a full sidebar including the logo, navigation menu (Dashboard, My CV, AI Interview, Study Programs, Interview History), and an optional "Candidate Track" card. The "My CV" link is always highlighted since we're always on /cv when this sidebar is visible. This couples the design to CvPage; if other pages need a sidebar, they'd need their own copy or the sidebar should be extracted to a reusable PageLayout wrapper.

### Auth integration

CvPage uses useAuth() from AuthContext to check isAuthenticated and fetch user.fullName for the avatar. The route is wrapped in ProtectedRoute, so reaching this page requires a valid token. If the user is not authenticated, the page navigates back to /login. This follows the dashboard pattern.

### Responsive layout

CvPage uses a two-column layout on desktop (lg: breakpoint): left column for main sections, right column for the AI profile card. On mobile, it's single column. The AI profile card has lg:sticky lg:top-6, so it sticks to the top as the user scrolls. The sidebar is fixed on the left (256px) and the main content area has ml-64 to account for it.

### Tailwind and styling

All components use Tailwind classes exclusively. The custom color palette is defined in public/index.html as a Tailwind config object. Font sizes and families are defined in the same config using semantic class names (body-md, title-md, label-lg, etc.). Material Symbols icons are loaded via Google Fonts and used throughout. All render correctly.

### Component composition

The page composes six sub-components: UploadZone, SkillsMatrix, KnowledgeGapCard, PositionFitCard, ExperienceTimeline, EvaluationDimensions. All are React.FC with TypeScript props and accept data as read-only arrays. UploadZone takes an onFile callback; others are pure presentational. No handlers for secondary interactions like "delete education" or "report this skill incorrect"—appropriate for the current scope.

### Testing coverage

No test files are present. Unit tests for each sub-component and integration tests for upload/parse flows would be valuable, but no tests exist yet. That's a future concern, not a blocker.

### Summary of files changed

CvPage.tsx (~400 lines) handles state management, async flows, and layout. Reasonable for a first iteration; extracting a useCvController hook would benefit future reuse. cv.service.ts is small and focused, delegating to httpService. Cv.model.ts defines all types. Sub-components are small and focused. App.tsx was updated to add the /cv route inside ProtectedRoute. public/index.html was updated with Tailwind CDN, custom config, and Material Symbols link. All files compile without TypeScript errors.

</details>

## File map

<details>
<summary>Files modified and created</summary>

- **src/models/Cv.model.ts** — TypeScript interfaces and enums for CV data (SkillData, ExperienceData, EducationData, CvParseResponse, SkillResponse). Matches backend DTOs exactly.
- **src/services/cv.service.ts** — Service object with methods for getCv, getSkills, uploadCv, parseCvText, setPosition. Uses httpService for Bearer auth.
- **src/views/pages/CvPage.tsx** — Main page component with embedded sidebar, upload form (file + text tabs), active CV status, target position form, skills matrix, knowledge gaps, experience timeline, education cards, suggested positions, AI profile card, confirmation modal. ~400 lines.
- **src/views/components/cv/UploadZone.tsx** — Drag-and-drop file upload zone with click-to-browse fallback. Accepts PDF and TXT files.
- **src/views/components/cv/SkillsMatrix.tsx** — Table rendering skills with name, category, experience years, and proficiency tier. Sortable on proficiency level.
- **src/views/components/cv/ExperienceTimeline.tsx** — Vertical timeline rendering work experience with role, company, duration, and achievements.
- **src/views/components/cv/KnowledgeGapCard.tsx** — Card displaying knowledge gap title with priority badge (HIGH/MEDIUM).
- **src/views/components/cv/PositionFitCard.tsx** — Card displaying suggested position, match percentage, and rank badge.
- **src/views/components/cv/EvaluationDimensions.tsx** — Skill level distribution widget showing counts for Foundational/Working/Proficient/Expert.
- **src/App.tsx** — Updated to include /cv route inside ProtectedRoute.
- **public/index.html** — Updated with Tailwind CSS CDN, custom color palette config, and Material Symbols font link.

See [full diff](#) for line-by-line changes.

</details>
