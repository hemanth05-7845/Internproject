import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  server: {
    // Required for Docker on Windows — inotify doesn't work in volume mounts
    watch: {
      usePolling: true,
      interval: 300,
    },
    host: "0.0.0.0",
    port: 5173,
  },
});
