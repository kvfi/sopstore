import { Intent } from '@blueprintjs/core';

/** Maps a domain status to a Blueprint Intent for Tags/Buttons. */
export function statusIntent(status: string): Intent {
	switch (status.toUpperCase()) {
		case 'EFFECTIVE':
		case 'COMPLETED':
			return Intent.SUCCESS;
		case 'APPROVED':
		case 'UNDER_REVIEW':
			return Intent.PRIMARY;
		case 'IN_REVIEW':
		case 'OPEN':
		case 'PENDING':
		case 'IN_PROGRESS':
			return Intent.WARNING;
		case 'RETIRED':
		case 'REJECTED':
		case 'ABANDONED':
			return Intent.DANGER;
		default:
			return Intent.NONE;
	}
}

/** Trims an ISO timestamp to "YYYY-MM-DD HH:MM". */
export function dt(iso: string | null | undefined): string {
	return iso ? iso.slice(0, 16).replace('T', ' ') : '—';
}

/** Human-friendly duration from seconds. */
export function dur(s: number | null): string {
	return s == null ? '—' : s < 60 ? `${s}s` : `${Math.round(s / 60)}m`;
}
