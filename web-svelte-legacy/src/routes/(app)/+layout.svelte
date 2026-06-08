<script lang="ts">
	import { onMount } from 'svelte';
	import { page } from '$app/state';
	import { goto } from '$app/navigation';
	import { api, logout } from '$lib/api';
	import { me, unreadCount, approvalCount } from '$lib/stores';
	import {
		Sidebar,
		SidebarGroup,
		SidebarItem,
		Avatar,
		Badge,
		Button,
		Spinner
	} from 'flowbite-svelte';

	let { children } = $props();
	let ready = $state(false);
	let drawerOpen = $state(false);

	const ICONS: Record<string, string> = {
		dashboard:
			'<path d="M3 3h7v7H3z"/><path d="M14 3h7v7h-7z"/><path d="M14 14h7v7h-7z"/><path d="M3 14h7v7H3z"/>',
		procedures:
			'<path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><path d="M14 2v6h6"/><path d="M8 13h8"/><path d="M8 17h8"/>',
		approvals: '<path d="M22 11.1V12a10 10 0 1 1-5.9-9.1"/><path d="M22 4 12 14.01l-3-3"/>',
		notifications:
			'<path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.7 21a2 2 0 0 1-3.4 0"/>',
		runs: '<path d="M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20z"/><path d="m10 8 6 4-6 4z"/>'
	};

	const NAV = [
		{ href: '/', label: 'Dashboard', icon: 'dashboard' },
		{ href: '/procedures', label: 'Procedures', icon: 'procedures' },
		{ href: '/approvals', label: 'Approvals', icon: 'approvals', badge: true },
		{ href: '/notifications', label: 'Notifications', icon: 'notifications' },
		{ href: '/runs', label: 'Runs', icon: 'runs' }
	];

	const TITLES: Record<string, string> = {
		'/': 'Dashboard',
		'/procedures': 'Procedures',
		'/approvals': 'Approval queue',
		'/notifications': 'Notifications',
		'/runs': 'Run history'
	};

	let path = $derived(page.url.pathname);
	let title = $derived(TITLES[path] ?? (path.startsWith('/procedures/') ? 'Procedure' : 'sopstore'));

	function isActive(href: string): boolean {
		return href === '/' ? path === '/' : path.startsWith(href);
	}

	async function refreshBadges() {
		try {
			const inbox = await api.get<{ unread: number }>('/api/v1/notifications');
			unreadCount.set(inbox.unread);
		} catch {
			/* ignore */
		}
		try {
			const queue = await api.get<unknown[]>('/api/v1/approvals');
			approvalCount.set(queue.length);
		} catch {
			/* ignore */
		}
	}

	async function doLogout() {
		await logout();
		me.set(null);
		await goto('/signin');
	}

	onMount(async () => {
		try {
			me.set(await api.get('/api/v1/me'));
			await refreshBadges();
			ready = true;
		} catch {
			await goto('/signin');
		}
	});

	let initials = $derived(
		($me?.displayName ?? '?')
			.split(' ')
			.map((s) => s[0])
			.slice(0, 2)
			.join('')
			.toUpperCase()
	);

	const navClasses = {
		div: 'flex h-full flex-col gap-1 overflow-y-auto bg-white px-3 py-4',
		active: 'gap-3 rounded-lg p-2.5 text-sm bg-primary-50 text-primary-700 font-semibold',
		nonactive: 'gap-3 rounded-lg p-2.5 text-sm font-medium text-gray-600 hover:bg-gray-100'
	};
</script>

{#if !ready}
	<div class="flex min-h-screen items-center justify-center"><Spinner color="green" /></div>
{:else}
	<Sidebar
		isOpen={drawerOpen}
		closeSidebar={() => (drawerOpen = false)}
		breakpoint="md"
		position="fixed"
		class="h-screen w-64 border-r border-gray-200 bg-white"
		classes={navClasses}
	>
		<a href="/" class="mb-4 flex items-center gap-3 px-1">
			<span class="grid size-8 place-items-center rounded-lg bg-primary-600">
				<span class="size-2.5 rounded-full bg-white"></span>
			</span>
			<span class="text-base font-bold tracking-tight text-gray-900">sopstore<br /><span class="text-[10px] font-medium uppercase tracking-wide text-gray-400">Quality control</span></span>
		</a>

		<SidebarGroup class="flex-1 space-y-1">
			{#each NAV as item (item.href)}
				<SidebarItem label={item.label} href={item.href} active={isActive(item.href)}>
					{#snippet icon()}
						<svg viewBox="0 0 24 24" class="size-[18px]" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">{@html ICONS[item.icon]}</svg>
					{/snippet}
					{#snippet subtext()}
						{#if item.badge && $approvalCount > 0}
							<Badge color="green" class="ms-auto">{$approvalCount}</Badge>
						{/if}
					{/snippet}
				</SidebarItem>
			{/each}
		</SidebarGroup>

		<div class="mt-auto flex items-center gap-3 border-t border-gray-200 pt-3">
			<Avatar size="sm">{initials}</Avatar>
			<div class="min-w-0">
				<div class="truncate text-sm font-semibold text-gray-900">{$me?.displayName}</div>
				<div class="truncate text-xs text-gray-400">{$me?.email}</div>
			</div>
		</div>
	</Sidebar>

	<div class="flex min-h-screen flex-col bg-gray-50 md:ml-64">
		<header class="sticky top-0 z-20 flex h-16 items-center gap-3 border-b border-gray-200 bg-white px-5 sm:px-7">
			<button class="text-gray-500 md:hidden" aria-label="Open menu" onclick={() => (drawerOpen = true)}>
				<svg viewBox="0 0 24 24" class="size-6" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><path d="M4 6h16M4 12h16M4 18h16" /></svg>
			</button>
			<h1 class="text-lg font-bold tracking-tight text-gray-900">{title}</h1>
			<div class="flex-1"></div>
			<a href="/notifications" aria-label="Notifications" class="relative grid size-9 place-items-center rounded-lg border border-gray-200 text-gray-500 hover:bg-gray-50">
				<svg viewBox="0 0 24 24" class="size-[17px]" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">{@html ICONS.notifications}</svg>
				{#if $unreadCount > 0}
					<span class="absolute -right-1.5 -top-1.5 grid min-w-4 place-items-center rounded-full bg-red-500 px-1 text-[10px] font-bold text-white">{$unreadCount}</span>
				{/if}
			</a>
			<Button size="xs" color="alternative" onclick={doLogout}>Sign out</Button>
		</header>
		<main class="mx-auto w-full max-w-[1600px] flex-1 px-5 py-6 sm:px-7">
			{@render children()}
		</main>
	</div>
{/if}
