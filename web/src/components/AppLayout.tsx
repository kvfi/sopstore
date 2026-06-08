import { useEffect } from 'react';
import { Outlet, useLocation, useNavigate, Link } from 'react-router-dom';
import {
	Navbar,
	Alignment,
	Button,
	Collapse,
	Icon,
	Menu,
	MenuItem,
	Spinner,
	Tag,
	type IconName
} from '@blueprintjs/core';
import { useQueryClient } from '@tanstack/react-query';
import { logout } from '../lib/api';
import { useMe, useNotifications, useApprovals } from '../lib/queries';
import { useCollapsedGroups } from '../lib/useCollapsedGroups';

type NavEntry = {
	href: string;
	label: string;
	icon: IconName;
	title: string;
	badge?: boolean;
};
type NavGroup = { title: string; admin?: boolean; entries: NavEntry[] };

const NAV_GROUPS: NavGroup[] = [
	{
		title: 'Workspace',
		entries: [
			{ href: '/', label: 'Dashboard', icon: 'dashboard', title: 'Dashboard' },
			{ href: '/procedures', label: 'Procedures', icon: 'document', title: 'Procedures' },
			{ href: '/runs', label: 'Runs', icon: 'play', title: 'Run history' },
			{ href: '/scripts', label: 'Scripts', icon: 'code', title: 'Scripts' }
		]
	},
	{
		title: 'Tasks',
		entries: [
			{ href: '/approvals', label: 'Approvals', icon: 'endorsed', title: 'Approval queue', badge: true },
			{ href: '/notifications', label: 'Notifications', icon: 'notifications', title: 'Notifications' }
		]
	},
	{
		title: 'Admin',
		admin: true,
		entries: [{ href: '/settings', label: 'Settings', icon: 'cog', title: 'Settings' }]
	}
];

const ALL_ENTRIES = NAV_GROUPS.flatMap((g) => g.entries);

function initials(name: string): string {
	return (name || '?')
		.split(' ')
		.map((s) => s[0])
		.slice(0, 2)
		.join('')
		.toUpperCase();
}

export default function AppLayout() {
	const navigate = useNavigate();
	const location = useLocation();
	const qc = useQueryClient();

	const meQ = useMe();
	const unread = useNotifications().data?.unread ?? 0;
	const approvals = useApprovals().data?.length ?? 0;
	const { isOpen, toggle } = useCollapsedGroups('sidebarNavCollapsed');

	useEffect(() => {
		if (meQ.isError) navigate('/signin', { replace: true });
	}, [meQ.isError, navigate]);

	async function doLogout() {
		await logout();
		qc.clear();
		navigate('/signin', { replace: true });
	}

	const me = meQ.data;
	if (!me) {
		return (
			<div className="center-screen">
				<Spinner />
			</div>
		);
	}

	const path = location.pathname;
	const isActive = (href: string) => (href === '/' ? path === '/' : path.startsWith(href));
	const current = ALL_ENTRIES.find((n) => isActive(n.href));
	const title = current?.title ?? (path.startsWith('/procedures/') ? 'Procedure' : 'sopstore');
	const isAdmin = me.roles.includes('TENANT_ADMIN') || me.roles.includes('SUPER_ADMIN');
	const visibleGroups = NAV_GROUPS.filter((g) => !g.admin || isAdmin);

	return (
		<div className="app-shell">
			<aside className="sidebar">
				<Link to="/" className="brand" style={{ color: 'inherit', textDecoration: 'none' }}>
					<span className="mark">
						<span />
					</span>
					<span className="name">
						sopstore
						<small>Quality control</small>
					</span>
				</Link>

				<nav>
					{visibleGroups.map((g) => {
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
									<Menu large>
										{g.entries.map((item) => (
											<MenuItem
												key={item.href}
												icon={item.icon}
												text={item.label}
												active={isActive(item.href)}
												onClick={() => navigate(item.href)}
												labelElement={
													item.badge && approvals > 0 ? (
														<Tag intent="primary" round minimal>
															{approvals}
														</Tag>
													) : undefined
												}
											/>
										))}
									</Menu>
								</Collapse>
							</div>
						);
					})}
				</nav>

				<div className="user">
					<Tag round large minimal>
						{initials(me.displayName)}
					</Tag>
					<div className="who">
						<div className="n">{me.displayName}</div>
						<div className="e">{me.email}</div>
					</div>
				</div>
			</aside>

			<div className="main">
				<Navbar className="topbar">
					<Navbar.Group align={Alignment.LEFT}>
						<span className="bp-title">{title}</span>
					</Navbar.Group>
					<Navbar.Group align={Alignment.RIGHT}>
						<Button
							minimal
							icon="notifications"
							onClick={() => navigate('/notifications')}
							aria-label="Notifications"
						>
							{unread > 0 ? (
								<Tag intent="danger" round>
									{unread}
								</Tag>
							) : null}
						</Button>
						<Navbar.Divider />
						<Button minimal icon="user" onClick={() => navigate('/profile')}>
							Profile
						</Button>
						<Button minimal icon="log-out" onClick={doLogout}>
							Sign out
						</Button>
					</Navbar.Group>
				</Navbar>

				<main className="content">
					<Outlet />
				</main>
			</div>
		</div>
	);
}
