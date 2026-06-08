import { useState } from 'react';

/**
 * Tracks which named nav groups are collapsed, persisted to localStorage so the choice survives
 * navigation and reloads. Groups default to expanded (absent key = open). Shared by the sidebar and
 * the settings sub-nav.
 */
export function useCollapsedGroups(storageKey: string) {
	const [collapsed, setCollapsed] = useState<Record<string, boolean>>(() => {
		try {
			return JSON.parse(localStorage.getItem(storageKey) || '{}');
		} catch {
			return {};
		}
	});

	const toggle = (title: string) =>
		setCollapsed((c) => {
			const next = { ...c, [title]: !c[title] };
			try {
				localStorage.setItem(storageKey, JSON.stringify(next));
			} catch {
				/* storage unavailable — collapse state just won't persist */
			}
			return next;
		});

	const isOpen = (title: string) => !collapsed[title];
	return { isOpen, toggle };
}
