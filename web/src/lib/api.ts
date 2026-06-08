/** Error carrying the HTTP status so callers can branch (e.g. 403 wrong password). */
export class ApiError extends Error {
	constructor(
		public status: number,
		message: string
	) {
		super(message);
	}
}

function cookie(name: string): string | null {
	const m = document.cookie.match(new RegExp('(?:^|; )' + name + '=([^;]*)'));
	return m ? decodeURIComponent(m[1]) : null;
}

/** Send the unauthenticated user to the sign-in page (hard redirect; SPA reloads). */
function toSignIn(): void {
	if (window.location.pathname !== '/signin') window.location.assign('/signin');
}

/** Ensure the XSRF-TOKEN cookie exists before a mutation (a GET primes it server-side). */
async function primeCsrf(): Promise<void> {
	if (cookie('XSRF-TOKEN')) return;
	await fetch('/api/v1/me', {
		headers: { 'X-Requested-With': 'XMLHttpRequest' },
		credentials: 'same-origin'
	}).catch(() => {});
}

type Opts = { method?: string; json?: unknown };

async function request<T>(path: string, opts: Opts = {}): Promise<T> {
	const method = (opts.method ?? (opts.json !== undefined ? 'POST' : 'GET')).toUpperCase();
	const headers: Record<string, string> = { 'X-Requested-With': 'XMLHttpRequest' };
	let body: string | undefined;
	if (opts.json !== undefined) {
		headers['Content-Type'] = 'application/json';
		body = JSON.stringify(opts.json);
	}
	if (method !== 'GET' && method !== 'HEAD') {
		await primeCsrf();
		const t = cookie('XSRF-TOKEN');
		if (t) headers['X-XSRF-TOKEN'] = t;
	}
	const res = await fetch(path, { method, headers, body, credentials: 'same-origin' });
	if (res.status === 401) {
		if (!path.endsWith('/me')) toSignIn();
		throw new ApiError(401, 'Not authenticated');
	}
	if (!res.ok) {
		let msg = res.statusText;
		try {
			const text = await res.text();
			if (text) msg = text;
		} catch {
			/* ignore */
		}
		throw new ApiError(res.status, msg);
	}
	if (res.status === 204) return undefined as T;
	const ct = res.headers.get('content-type') ?? '';
	return ct.includes('application/json') ? ((await res.json()) as T) : ((await res.text()) as T);
}

export const api = {
	get: <T>(path: string) => request<T>(path),
	post: <T>(path: string, json?: unknown) => request<T>(path, { method: 'POST', json }),
	put: <T>(path: string, json?: unknown) => request<T>(path, { method: 'PUT', json }),
	del: <T>(path: string) => request<T>(path, { method: 'DELETE' })
};

/** Uploads a file via multipart/form-data (browser sets the boundary; we add CSRF). */
export async function upload<T>(path: string, file: File): Promise<T> {
	await primeCsrf();
	const t = cookie('XSRF-TOKEN');
	const form = new FormData();
	form.append('file', file);
	const res = await fetch(path, {
		method: 'POST',
		headers: { 'X-Requested-With': 'XMLHttpRequest', ...(t ? { 'X-XSRF-TOKEN': t } : {}) },
		body: form,
		credentials: 'same-origin'
	});
	if (res.status === 401) {
		toSignIn();
		throw new ApiError(401, 'Not authenticated');
	}
	if (!res.ok) {
		let msg = res.statusText;
		try {
			msg = (await res.text()) || msg;
		} catch {
			/* ignore */
		}
		throw new ApiError(res.status, msg);
	}
	return (await res.json()) as T;
}

/** Form-login against Spring, returning clean status (handler sends 204/401 for XHR). */
export async function login(username: string, password: string): Promise<void> {
	await primeCsrf();
	const t = cookie('XSRF-TOKEN');
	const res = await fetch('/login', {
		method: 'POST',
		headers: {
			'X-Requested-With': 'XMLHttpRequest',
			'Content-Type': 'application/x-www-form-urlencoded',
			...(t ? { 'X-XSRF-TOKEN': t } : {})
		},
		body: new URLSearchParams({ username, password }),
		credentials: 'same-origin'
	});
	if (!res.ok) throw new ApiError(res.status, 'Invalid email or password');
}

export async function logout(): Promise<void> {
	try {
		await request('/logout', { method: 'POST' });
	} catch {
		/* already gone */
	}
}
