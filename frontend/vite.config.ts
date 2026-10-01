import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// En desarrollo, /api se envia al backend de Spring Boot (puerto 8080).
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: { '/api': 'http://localhost:8080' },
  },
});
