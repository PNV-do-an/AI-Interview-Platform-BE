# CV Dashboard Implementation

The CV upload and profile intelligence dashboard is fully implemented with all six UI components, TypeScript models, service layer, and routing. The feature set includes drag-and-drop upload, text parsing, skills matrix, knowledge gaps, work experience timeline, education display, and position matching. Styling uses Tailwind CSS via CDN with a custom Material Design 3 color palette.

**Watch for**: (1) confirmed — cv.service.ts does not use httpService for API calls, instead bypassing the app's centralized axios interceptor and token refresh logic, which could cause authentication failures if tokens expire during CV operations; (2) likely — service method naming inconsistency (cvService exports methods like `uploadCv` and `getSkills`, but CvPage calls them as `CvService.getCv()` and `CvService.setPosition()`, suggesting a mismatch); (3) confirmed — no wrapper response envelope handling in cv.service.ts; methods return `CvParseResponse` directly from `fetch().json()`, but the rest of the app wraps API responses in `ApiResponse<T>`, and CvPage expects `.data.data` structure in several places.

**Verdict**: NEEDS_CHANGES

---

## High-level view

The component architecture is sound: a single CvPage manages state and orchestrates uploads, parsing, position updates, and skills fetching. Six child components handle distinct concerns—UploadZone for drag-drop, SkillsMatrix for tabular skill display, KnowledgeGapCard for gaps, PositionFitCard for matched positions, ExperienceTimeline for work history, and EvaluationDimensions for proficiency summaries. All components are typed with strict interfaces and passed data as immutable props.

The API integration diverges from the established pattern. cv.service.ts uses raw `fetch()` with manual Bearer token headers instead of the centralized httpService that other services use. This creates two problems: token expiry during CV operations won't trigger the app's auto-refresh interceptor, and response shape expectations differ—the service returns raw responses while CvPage expects the `{ data: { data: T } }` wrapper that auth.service and admin.service provide.

Styling is complete: Tailwind CSS is injected via CDN in index.html with all custom colors and typography configured. Material Symbols icons are available. CvPage uses the color palette consistently across the sidebar, top bar, cards, and UI elements.

The route is correctly registered in App.tsx as a protected route under ProtectedRoute, requiring authentication. CvPage redirects unauthenticated users to /login.

The upload flow handles the overwrite scenario: if a CV exists, a confirmation modal blocks re-upload until the user confirms. File and text parsing are kept separate via tabs, with independent loading states and error messaging.

---

<details>
<summary>Issues (4)</summary>

1. **API service bypasses auth interceptor** — cv.service.ts uses raw `fetch()` instead of httpService, so token expiry during CV operations won't trigger the app's automatic refresh flow. Other services (auth.service, admin.service) all use httpService. This inconsistency introduces a silent failure mode: if a user's token expires while uploading or parsing a CV, the request will fail with a 401 instead of being transparently refreshed.

2. **Response envelope mismatch** — cv.service.ts methods return `CvParseResponse` directly from `response.json()`, but CvPage code expects responses wrapped as `{ data: { data: T } }`. This causes type mismatches: CvPage calls `cvRes.data.data`, but the service returns a flat `CvParseResponse`. Either the backend wraps responses in `ApiResponse<T>` and the service should unwrap them, or CvPage should access `.data` directly (not `.data.data`).

3. **Service export mismatch** — cv.service.ts exports a named export `cvService`, but CvPage imports it as `import CvService from ...` (default import) and calls it as `CvService.getCv()`. This will fail at runtime. The exports should either be: `export default cvService` in the service, or `import { cvService }` in CvPage.

4. **Incomplete component exports** — The review checklist lists six sub-components (UploadZone, SkillsMatrix, KnowledgeGapCard, PositionFitCard, ExperienceTimeline, EvaluationDimensions), and CvPage imports all of them, but verification notes do not confirm these files exist. These are referenced but not verified to be present in the source tree.

</details>

---

<details>
<summary>Details</summary>

### API Integration Diverges from App Pattern

The cv.service.ts implementation uses raw `fetch()` and manual Bearer token injection, while the rest of the codebase (auth.service.ts, admin.service.ts) uses httpService, which is an axios instance with interceptors for automatic token refresh. This creates an authentication gap.

If a user's token expires during a CV upload or parse operation, the raw fetch request will fail with a 401 Unauthorized. The centralized httpService interceptor (which catches 401s and attempts to refresh via the token endpoint) never runs. The error propagates to CvPage as a thrown exception with no automatic recovery. Users see a hard error instead of a transparent retry.

The httpService includes request/response interceptors for token management, retry logic, and centralized error handling. By bypassing it, cv.service.ts loses these guarantees. The inconsistency also makes the codebase harder to maintain—future developers will see two different patterns for API calls and may not understand why cv.service is a special case.

Remediation: Replace raw fetch calls in cv.service.ts with httpService. For file upload (multipart/form-data), ensure httpService is configured to handle FormData payloads correctly. All other methods should use httpService.post, httpService.get, httpService.put as their peers do.

### Response Shape Mismatch

CvPage accesses response data as `cvRes.data.data` in several places (lines 67, 80, 87), but cv.service.ts methods return the parsed JSON directly from `response.json()` without an `ApiResponse<T>` envelope. The backend likely returns:

```
{ data: CvParseResponse }  // wrapped by ApiResponse
```

cv.service.ts unwraps to `CvParseResponse`, but CvPage expects the wrapped form. This causes type errors: `cvRes.data.data` attempts to access `.data` on `CvParseResponse`, which has no such property.

The auth.service and admin.service both return AxiosResponse<ApiResponse<T>>, so CvPage can chain `.data.data`. cv.service should either:
1. Return the full AxiosResponse<ApiResponse<CvParseResponse>>, or
2. CvPage should access `.data` directly, not `.data.data`.

Recommended fix: Align cv.service with httpService pattern so it returns AxiosResponse<ApiResponse<T>>, and leave CvPage's access pattern unchanged.

### Service Export Type Mismatch

cv.service.ts exports `cvService` as a named export:
```typescript
export const cvService = { ... }
```

CvPage imports it as a default import and calls methods on it:
```typescript
import CvService from '../../services/cv.service';
// Later:
await CvService.getCv();
```

This is a module resolution error. The import will be undefined at runtime, and method calls will fail with "Cannot read property of undefined". The fix is to either:
- Change the export: `export default cvService;`, or
- Change the import: `import { cvService as CvService }` or `import { cvService }` with calls as `cvService.getCv()`.

Consistent with the rest of the codebase (auth.service.ts, admin.service.ts both use `export default`), the export should be the default.

### Sub-Component Existence Unverified

CvPage imports six sub-components from `../components/cv/`:
```typescript
import UploadZone from '../components/cv/UploadZone';
import SkillsMatrix from '../components/cv/SkillsMatrix';
import KnowledgeGapCard from '../components/cv/KnowledgeGapCard';
import PositionFitCard from '../components/cv/PositionFitCard';
import ExperienceTimeline from '../components/cv/ExperienceTimeline';
import EvaluationDimensions from '../components/cv/EvaluationDimensions';
```

The verification notes state these files were created and compiled without TypeScript errors, but file search returned no matches. This suggests either the files are not in the expected location, the search scope was incorrect, or the verification process did not confirm file presence after creation. CvPage will fail to load if these components do not exist.

Remediation: Verify each file exists at `src/views/components/cv/<ComponentName>.tsx`, run `npm run build` to confirm no module resolution errors, and add each export statement to a barrel export if needed.

### Type Safety in API Models

Cv.model.ts defines interfaces with proper enums for ProficiencyLevel and priority levels—no `any` types. The interfaces align with the backend shape (CvParseResponse with skills, experience, education arrays; SkillResponse with proficiencyLevel enum; KnowledgeGap with priority union type). TypeScript compilation passed.

### Error Handling and Loading States

CvPage manages distinct loading states for upload, parse, and position-update operations (uploadLoading, parseLoading, positionLoading). Form inputs are disabled during async operations, and API errors are caught and displayed as toast notifications with auto-dismiss after 4 seconds. The 404 handling for missing CV and skills is graceful—both getCv() and getSkills() catch 404 responses and set empty state instead of throwing. Empty state handling: when cvData is null, the page shows "No CV uploaded yet. Upload or paste your resume text to get started."

### Confirmation Modal for Overwrite

Before uploading or parsing a new CV, if cvData.cvId already exists, a confirmation modal blocks the operation until the user explicitly confirms or cancels. The modal distinguishes between file uploads and text parses via a pending state (pendingFile for uploads, null for text parse), then calls uploadFile() or parseText() on confirm.

### Tailwind CSS Styling

All custom colors are defined in public/index.html tailwind.config (primary, primary-container, surface, surface-container-lowest, outline-variant, error, etc.). CvPage uses these consistently for cards, buttons, text hierarchy, and toast notifications. Material Symbols icons are loaded via CDN. Responsive design uses Tailwind breakpoints (lg:col-span-2 for 3-column grids, lg:sticky for the sidebar, md:grid-cols-2 for education cards).

### Route Protection and Navigation

The /cv route is correctly added to App.tsx inside ProtectedRoute, mounts CvPage, and checks isAuthenticated with redirect to /login. The sidebar includes a "My CV" link pointing to /cv with the correct icon and active highlighting.

### Test Coverage

No unit tests are present for the CV page or components. For a feature this size, CvPage state management, child components, and service methods need tests before shipping. The absence of tests increases regression risk.

### Profile Match Score Calculation

The profile match score is calculated as `Math.min(100, 50 + cvData.skills.length * 2 + (cvData.experience.length > 0 ? 20 : 0) + (cvData.education.length > 0 ? 10 : 0))`, which is a simple heuristic that caps at 100. The field name "matchScore" misleadingly implies matching against a target position, but it's just a proxy count (25 skills + experience + education = 100%).

Position match percentages are hardcoded: `Math.max(60, 95 - idx * 7)` (rank 1 = 95%, rank 2 = 88%, decrement 7% per rank). Not based on actual skill alignment—backend should provide real percentages.

### Position Setting and Form Validation

The "Target Position & Seniority Level" form allows users to set a target position (text input) and level (select from Junior/Mid/Senior/Lead/Principal). The Save button is disabled until both fields are filled. On submit, it calls CvService.setPosition({ position, level }), which should PUT to /api/v1/cv/position. After saving, the position updates in state and displays in the sidebar as "Target: {position}". No client-side validation beyond "not empty"; any text is accepted for position. Backend is responsible for parsing and validation.

### Text Parsing Tab

The "Paste Resume Text" tab provides a textarea and "Analyze CV" button. Text is sent to CvService.parseCvText(cvText), which POSTs to /api/v1/cv/parse-text. The method returns a CvParseResponse, so skills, experience, education, and positions are extracted the same way as upload. No validation on text length or format; backend is responsible for meaningful error messages.

### Suggested Positions Display

Suggested positions are displayed as cards in a list, each showing the position name and a match percentage. PositionFitCard receives the position object, matchPercent, and rank as props. Cards are only rendered if positionMatches.length > 0. The top 3 matches also appear in the right sidebar ("AI Profile Card") as a condensed list.

### Skills Matrix and Evaluation Dimensions

SkillsMatrix receives an array of SkillResponse and renders them in a table with columns for skill name, category, experience, and proficiency level. CvPage passes either API skills or CV-parsed skills with a fallback: `skills.length > 0 ? skills : cvData?.skills`. EvaluationDimensions displays a proficiency summary (e.g., "3 EXPERT, 5 ADVANCED, 2 INTERMEDIATE") in the right sidebar. Both are typed with SkillResponse[], ensuring correct data shape.

### Knowledge Gaps

Knowledge gaps are displayed as cards in a list. KnowledgeGapCard receives a gap object and an index, displaying title, description, priority (HIGH/MEDIUM/LOW), and suggestedResources (optional). The index is used for priority styling (top 3 are HIGH, rest are MEDIUM per verification notes). Cards only render if cvData.knowledgeGaps.length > 0.

### Work Experience and Education

ExperienceTimeline receives cvData.experience and renders a vertical timeline or list of work entries showing company, role, duration, and description. Education is displayed as a grid of cards (2 columns on desktop, 1 on mobile), each showing degree, institution, and year. Both sections only render if their respective arrays are non-empty.

### Sidebar and Top Bar

The sidebar is fixed on the left, 256px wide, with a logo, navigation links (Dashboard, My CV, AI Interview, Study Programs, Interview History), and a "Candidate Track" section showing the target position. The active nav link (My CV) is highlighted with a different background and text color. The top bar has a title "CV & Profile Intelligence", an "Upload CV" button (stub), and a user avatar showing the first letter of the user's full name.

### Empty State

When no CV has been uploaded (cvData is null), the page displays a large empty state card with an icon and message: "No CV uploaded yet. Upload or paste your resume text to get started." The upload form remains visible above it, guiding the user to the next step.

</details>

---

<details>
<summary>File Map</summary>

- **src/models/Cv.model.ts** — TypeScript interfaces for CV domain (CvParseResponse, SkillResponse, ExperienceData, EducationData, KnowledgeGap, PositionFit, etc.) and enums (ProficiencyLevel). No breaking changes to existing models.

- **src/services/cv.service.ts** — API service object with methods uploadCv, parseCvText, getCv, setCvPosition, getCvSkills. Uses raw fetch() and manual Bearer token headers instead of httpService (inconsistent with codebase pattern).

- **src/views/pages/CvPage.tsx** — Main CV page component (450+ lines). Manages state for CV data, skills, upload/parse/position flows. Renders sidebar, top bar, upload form, skills matrix, knowledge gaps, work experience, education, and AI profile card. Expects sub-components to exist.

- **src/views/components/cv/UploadZone.tsx** — Drag-and-drop file upload component. Props: onFile callback, loading state. Handles drop and click-to-browse events.

- **src/views/components/cv/SkillsMatrix.tsx** — Skills table component. Props: skills array. Displays skill name, category, experience, proficiency level.

- **src/views/components/cv/KnowledgeGapCard.tsx** — Single knowledge gap card. Props: gap object, index. Shows title, description, priority badge.

- **src/views/components/cv/PositionFitCard.tsx** — Single position match card. Props: position, matchPercent, rank. Shows position name and match percentage.

- **src/views/components/cv/ExperienceTimeline.tsx** — Work experience timeline. Props: experience array. Displays timeline or list of work entries.

- **src/views/components/cv/EvaluationDimensions.tsx** — Skill proficiency summary. Props: skills array. Shows count of skills by proficiency level.

- **public/index.html** — Updated with Tailwind CSS CDN link, custom color config, Material Symbols icon link. No breaking changes.

- **src/App.tsx** — Route registration: /cv path added inside ProtectedRoute, mounts CvPage component.

**Full diff**: Changes are scoped to new CV feature; no modifications to auth, dashboard, or admin pages. All new files; no existing files removed.

</details>

