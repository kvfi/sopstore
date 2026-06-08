/** Maps a domain status to a Flowbite Badge colour. */
export function badgeColor(status: string): string {
	switch (status.toUpperCase()) {
		case 'EFFECTIVE':
		case 'COMPLETED':
			return 'green';
		case 'APPROVED':
			return 'indigo';
		case 'IN_REVIEW':
		case 'OPEN':
		case 'PENDING':
		case 'IN_PROGRESS':
			return 'yellow';
		case 'UNDER_REVIEW':
			return 'purple';
		case 'RETIRED':
		case 'REJECTED':
		case 'ABANDONED':
			return 'red';
		default:
			return 'gray';
	}
}

/** Trims an ISO timestamp to "YYYY-MM-DD HH:MM". */
export function dt(iso: string | null | undefined): string {
	return iso ? iso.slice(0, 16).replace('T', ' ') : '—';
}
