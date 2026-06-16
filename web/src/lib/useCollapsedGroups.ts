import { useState } from 'react';

/**
 * Tracks which named nav groups are open, persisted to localStorage so the choice survives
 * navigation and reloads. Each lookup/toggle takes a per-group `defaultOpen` used when the user
 * hasn't explicitly toggled that group yet (absent key = default). Shared by the sidebar.
 */
export function useCollapsedGroups(storageKey: string) {
	const [open, setOpen] = useState<Record<string, boolean>>(() => {
		try {
			return JSON.parse(localStorage.getItem(storageKey) || '{}');
		} catch {
			return {};
		}
	});

	const isOpen = (title: string, defaultOpen = true) =>
		title in open ? open[title] : defaultOpen;

	const toggle = (title: string, defaultOpen = true) =>
		setOpen((o) => {
			const current = title in o ? o[title] : defaultOpen;
			const next = { ...o, [title]: !current };
			try {
				localStorage.setItem(storageKey, JSON.stringify(next));
			} catch {
				/* storage unavailable — open state just won't persist */
			}
			return next;
		});

	return { isOpen, toggle };
}
