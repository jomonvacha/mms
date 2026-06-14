# MMS UI

React and TypeScript administration UI for the standalone Member Management System.

```bash
npm ci
npm run dev
npm run build
```

The development server runs on `http://localhost:3001` and proxies `/api` requests to `http://localhost:8081` by default. Override the API target with `VITE_PROXY_TARGET`.

The production Docker image serves the built SPA through nginx and reverse-proxies API and OAuth requests to the `mms-service` Compose service.
