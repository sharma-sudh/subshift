import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

// In development the browser talks to Vite (5173) and Vite forwards /api/* to Spring Boot (8080).
// Same-origin requests mean no CORS setup is needed on the backend while developing.
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const target = env.VITE_PROXY_TARGET || 'http://localhost:8080';
  return {
    plugins: [react()],
    server: {
      port: 5173,
      proxy: {
        '/api': { target, changeOrigin: true },
      },
    },
  };
});
