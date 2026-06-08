import { writable } from 'svelte/store';

export type Me = { id: string; email: string; displayName: string; roles: string[] };

export const me = writable<Me | null>(null);
export const unreadCount = writable<number>(0);
export const approvalCount = writable<number>(0);

export type ToastKind = 'ok' | 'err' | 'info';
export type Toast = { id: number; msg: string; kind: ToastKind };

export const toasts = writable<Toast[]>([]);
let seq = 0;

export function toast(msg: string, kind: ToastKind = 'info'): void {
	const id = ++seq;
	toasts.update((t) => [...t, { id, msg, kind }]);
	setTimeout(() => toasts.update((t) => t.filter((x) => x.id !== id)), 3800);
}
