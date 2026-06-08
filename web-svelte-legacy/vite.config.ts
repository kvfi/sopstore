import tailwindcss from '@tailwindcss/vite';
import { sveltekit } from '@sveltejs/kit/vite';
import { defineConfig } from 'vite';

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
	plugins: [tailwindcss(), sveltekit()],
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
