import { defineConfig } from 'vite';
import { viteSingleFile } from 'vite-plugin-singlefile';

// One self-contained index.html: it is both the offline page (open it from disk) and the file
// Solace serves from the game, so nothing may be loaded from another file or host.
export default defineConfig({
  plugins: [viteSingleFile()],
  build: { target: 'es2020', outDir: 'dist', emptyOutDir: true },
});
