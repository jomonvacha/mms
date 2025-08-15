# MMS UI (React + Vite)

A production-ready React 18 UI for the MMS backend featuring Bootstrap 5 styling, local and OAuth sign-in, protected
routes, a dev proxy for CORS, and Docker/Nginx deployment.

## Prerequisites

- Node.js 18+
- NPM 9+

## Configuration

Create an environment file to configure the API base URL and dev proxy target.

```
cd ui
cp .env.example .env
# .env contents example:
# VITE_API_BASE_URL=
# VITE_PROXY_TARGET=http://localhost:8080
```

- `VITE_API_BASE_URL`: Optional. If set, client requests go to this absolute base (e.g., `http://localhost:8080`). If
  empty, requests use relative paths like `/api` (recommended with dev proxy).
- `VITE_PROXY_TARGET`: Dev-only proxy target for Vite. Defaults to `http://localhost:8080`.
- `VITE_SIGNIN_PATH`: Defaults to `/api/auth/signin` (matches `AuthController`).
- `VITE_REGISTER_PATH`: Defaults to `/api/auth/signup` (matches `AuthController`).
- `VITE_SIGNOUT_PATH`: Optional; leave empty if your backend has no signout endpoint.
- `VITE_REFRESH_PATH`: Defaults to `/api/auth/refresh`.
- `VITE_AUTO_SIGNOUT_MINUTES`: Optional idle timeout in minutes (e.g., `30`). When set, the UI signs the user out after
  inactivity.
- `VITE_IDLE_WARNING_SECONDS`: Seconds to show the confirmation dialog before auto-signout (default 60).

Make sure your MMS backend is configured for CORS when accessed cross-origin and allows credentials.

## Development

Start the Vite dev server on port 3000 with API proxying to your MMS backend.

```
cd ui
npm install
npm run dev
```

- App: http://localhost:3000
- Dev proxy: Requests to `/api` are proxied to `VITE_PROXY_TARGET`.
- Session: The backend should set an httpOnly session cookie after successful auth.

## Build and Preview

```
cd ui
npm run build
npm run preview   # serves dist/ on port 3000
```

## Linting & Formatting

- Install tools (once):
  ```
  cd ui
  npm i -D prettier eslint @eslint/js eslint-plugin-react eslint-plugin-react-hooks globals
  ```
- Format code:
  ```
  npm run format
  ```
- Lint JS/JSX in `ui/`:
  ```
  npx eslint . --ext .js,.jsx
  ```
- Lint TS/TSX in `src/` (TypeScript components):
  ```
  npm i -D @typescript-eslint/parser @typescript-eslint/eslint-plugin eslint-plugin-react eslint-plugin-react-hooks @eslint/js globals
  npx eslint ../src --ext .ts,.tsx
  ```

## Docker

Build and run the UI with Docker and Nginx (port 3000). You can optionally pass build-time env vars to bake in an
absolute API base.

```
cd ui
# Build and run via compose
VITE_API_BASE_URL=http://backend:8080 \
VITE_PROXY_TARGET=http://backend:8080 \
docker compose up --build ui
```

- Image uses a multi-stage build (Node for build, Nginx for runtime).
- Nginx serves the static app on port 3000 with SPA fallback.
- For multi-service setups, ensure your backend is reachable from the UI container (`backend` hostname in the same
  compose network or a public URL), and set `VITE_API_BASE_URL` accordingly at build time.

## OAuth Notes

- Buttons on the Sign In page redirect to backend-managed routes:
  - Google: `/oauth2/authorization/google`
  - Apple: `/oauth2/authorization/apple`
- After OAuth, the backend should redirect back to the UI (e.g., `http://localhost:3000/`).
  - Configure in MMS backend: `app.oauth2.authorized-redirect-uri=http://localhost:3000/` (or your deployed UI URL).
  - The backend success handler appends `?token=...&refreshToken=...` to the redirect. The UI captures these once and
    stores them, then calls `/api/users/me` and auto-redirects to `/members`.
- Configure OAuth redirect URIs in your identity providers to match your backend’s endpoints and UI origin.

## Auth Model

- On app start, the UI calls `/api/users/me` with `credentials: include` to determine auth state.
- Local signin: `POST /api/auth/signin` by default (username + password); override via `VITE_SIGNIN_PATH`. If unset,
  local sign-in is hidden.
- Local register: `POST /api/auth/signup` by default (username, email, firstName, lastName, password, optional
  phoneNumber); override via `VITE_REGISTER_PATH`. If unset, sign-up is hidden.
- Signout: optional endpoint via `VITE_SIGNOUT_PATH` (if unset, Sign Out skips server call and only clears client
  state).
- Members (protected): `GET /api/members?page=0&size=25`, UI unwraps `content` from Page if present.
- Token refresh: Automatically calls `POST /api/auth/refresh?refreshToken=...` (configurable via `VITE_REFRESH_PATH`) on
  401s, updates tokens, and retries once.
- Auto signout: When `VITE_AUTO_SIGNOUT_MINUTES` is set (> 0), the UI logs the user out after that period of inactivity
  and redirects to `/signin?autoSignedOut=1`.
  - A confirmation dialog appears for `VITE_IDLE_WARNING_SECONDS` allowing users to Stay Signed In or Sign Out now.

## Project Structure

```
ui/
  index.html              # Bootstrap 5 CDN + root div
  vite.config.js          # Vite on port 3000 + /api proxy
  .env.example            # Example env vars
  package.json            # dev/build/preview scripts
  Dockerfile              # Multi-stage build, Nginx runtime
  nginx.conf              # SPA fallback to index.html
  docker-compose.yml      # ui service on port 3000
  src/
    main.jsx              # App bootstrap with Router + AuthProvider
    App.jsx               # Layout + footer
    routes.jsx            # Route table
    api/client.js         # Fetch wrapper + helpers
    context/AuthProvider.jsx
    hooks/useAuth.js
    components/
      Navbar.jsx
      ProtectedRoute.jsx
      SessionTimeoutModal.jsx
    pages/
      Home.jsx
      SignIn.jsx
      SignUp.jsx
      Members.jsx
      SignOut.jsx
```

## MMS Backend CORS

If you are not using the dev proxy or you set `VITE_API_BASE_URL` to a different origin, configure MMS backend CORS to:

- Allow the UI origin (e.g., `http://localhost:3000`).
- Allow credentials.
- Expose necessary headers.

## Acceptance Checklist

- `npm run dev` serves on port 3000 and proxies `/api` to MMS.
- If local auth endpoints provided, successful signin sets session cookie; navbar shows the user’s name.
- Members page loads data when authenticated.
- Google/Apple buttons redirect to backend OAuth routes.
- `docker compose up --build ui` serves the built app on port 3000 with SPA fallback.
