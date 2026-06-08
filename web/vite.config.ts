import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// The Spring backend (JSON API + auth) the portal talks to.
const BACKEND = process.env.SOPSTORE_API ?? 'http://localhost:8080';

// Same-origin dev proxy: the browser only ever talks to :5173, so the Spring
// session cookie + CSRF token behave as first-party — no CORS dance in dev.
const proxy = {
	target: BACKEND,
	changeOrigin: true,
	cookieDomainRewrite: 'localhost'
};

export default defineConfig({
	plugins: [react()],
	server: {
		port: 5173,
		strictPort: true,
		proxy: {
			'/api': proxy,
			'/login': proxy,
			'/logout': proxy,
			'/actuator': proxy
		}
	}
});
