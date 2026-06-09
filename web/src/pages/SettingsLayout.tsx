import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { Collapse, Icon, Menu, MenuItem, NonIdealState, Spinner, type IconName } from '@blueprintjs/core';
import { useMe } from '../lib/queries';
import { useCollapsedGroups } from '../lib/useCollapsedGroups';

type Entry = { to: string; label: string; icon: IconName };
type Group = { title: string; entries: Entry[] };

// Settings sub-navigation. Add new sections/items here as configuration grows.
const GROUPS: Group[] = [
	{
		title: 'Configuration',
		entries: [
			{ to: '/settings/configuration/prerequisite-types', label: 'Prerequisite types', icon: 'tag' },
			{ to: '/settings/configuration/prerequisites', label: 'Prerequisites', icon: 'list' },
			{ to: '/settings/configuration/export-templates', label: 'Export templates', icon: 'document' },
			{ to: '/settings/configuration/script-bundles', label: 'Script bundles', icon: 'archive' },
			{ to: '/settings/configuration/confidentiality-levels', label: 'Confidentiality levels', icon: 'shield' }
		]
	}
];

export default function SettingsLayout() {
	const { data: me } = useMe();
	const navigate = useNavigate();
	const { pathname } = useLocation();
	const { isOpen, toggle } = useCollapsedGroups('settingsNavCollapsed');

	if (!me) {
		return (
			<div className="center-screen">
				<Spinner />
			</div>
		);
	}

	const isAdmin = me.roles.includes('TENANT_ADMIN') || me.roles.includes('SUPER_ADMIN');

	if (!isAdmin) {
		return (
			<NonIdealState
				icon="lock"
				title="Administrators only"
				description="Settings require the Tenant Admin role."
			/>
		);
	}

	return (
		<div className="settings-shell">
			<aside className="settings-nav">
				{GROUPS.map((g) => {
					const open = isOpen(g.title);
					return (
						<div key={g.title} className="nav-group">
							<button
								type="button"
								className="nav-group-h"
								aria-expanded={open}
								onClick={() => toggle(g.title)}
							>
								<Icon icon={open ? 'chevron-down' : 'chevron-right'} size={14} />
								<span>{g.title}</span>
							</button>
							<Collapse isOpen={open} keepChildrenMounted>
								<Menu>
									{g.entries.map((e) => (
										<MenuItem
											key={e.to}
											icon={e.icon}
											text={e.label}
											active={pathname.startsWith(e.to)}
											onClick={() => navigate(e.to)}
										/>
									))}
								</Menu>
							</Collapse>
						</div>
					);
				})}
			</aside>
			<section className="settings-body">
				<Outlet />
			</section>
		</div>
	);
}
