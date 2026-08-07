import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'
import tsconfigPaths from "vite-tsconfig-paths";

// https://vite.dev/config/
export default defineConfig({
  plugins: [tsconfigPaths(), react()],
  server: {
    host: '0.0.0.0',
    port: 5123,
  },
  test: {
    include: ['src/**/*.test.ts'],
  },
})
