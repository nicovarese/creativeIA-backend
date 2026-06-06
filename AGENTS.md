y# CreativeIA Backend — AGENTS.md


## Goal (MVP)
Finish end-to-end MVP:
- user registration + login with proper security
- users can own multiple projects
- authenticated user can create an image generation job (ComfyUI local), poll status, and see generated images in a project library
- DB starts empty; schema created via JPA + migrations if present

## Source of truth (IMPORTANT)
- Do NOT trust README.
- Use the existing code as source of truth:
  - Controllers/Routes in `src/main/java/**`
  - DTOs in `src/main/java/**/dto`
  - Security config in `src/main/java/**/security`
  - Comfy integration in `src/main/java/**/comfy` (or similar)
- The frontend contract is determined by the frontend code (services/models). Backend must align to it.

## Workspace
Backend: `./backend`
Frontend: `../frontend`

## Local dev assumptions
- Backend runs on `http://localhost:8080` (confirm in code/config)
- Frontend runs on `http://localhost:4200`
- ComfyUI runs locally (confirm host/port from backend config or env vars)

## Deliverables
1) Confirm current API base path (e.g., `/api` or `/v1`) from code.
2) Ensure auth flows exist (register/login) and are secure.
3) Ensure project ownership enforcement on all endpoints.
4) Ensure job creation + polling works end-to-end with ComfyUI local.
5) Ensure generated images are persisted and appear in "library" by project.

## Assets serving policy (MVP decision)
- Decide based on frontend implementation:
  - If frontend uses `<img src="...">` directly, either serve assets publicly under a static path OR implement signed URLs / blob loading.
- Implement the simplest safe approach for MVP and document it here.

## Rules for Codex changes
- Small, reviewable changes; avoid large refactors.
- Add/adjust endpoints to match frontend contract, not README.
- Run build/tests before finishing.
- Never commit secrets.