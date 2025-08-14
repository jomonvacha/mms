import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Vite dev server on port 3000 with proxy for /api
const proxyTarget = process.env.VITE_PROXY_TARGET || 'http://localhost:8080';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: proxyTarget,
        changeOrigin: true,
        secure: false,
      },
    },
  },
});

