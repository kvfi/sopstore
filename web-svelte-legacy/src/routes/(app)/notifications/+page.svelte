<script lang="ts">
	import { onMount } from 'svelte';
	import { api } from '$lib/api';
	import { unreadCount, toast } from '$lib/stores';
	import { dt } from '$lib/ui';
	import { Button, Spinner } from 'flowbite-svelte';

	type Msg = {
		id: string;
		title: string;
		body: string;
		link: string | null;
		createdAt: string;
		unread: boolean;
	};

	let items = $state<Msg[]>([]);
	let unread = $state(0);
	let loading = $state(true);

	async function load() {
		loading = true;
		try {
			const inbox = await api.get<{ unread: number; items: Msg[] }>('/api/v1/notifications');
			items = inbox.items;
			unread = inbox.unread;
			unreadCount.set(inbox.unread);
		} catch (e) {
			toast((e as Error).message, 'err');
		} finally {
			loading = false;
		}
	}

	async function markRead(m: Msg) {
		try {
			await api.post(`/api/v1/notifications/${m.id}/read`);
			await load();
		} catch (e) {
			toast((e as Error).message, 'err');
		}
	}

	onMount(load);
</script>

<p class="text-sm text-gray-500 mb-4">{unread > 0 ? `${unread} unread` : 'All caught up'}</p>

{#if loading}
	<div class="center-screen"><Spinner color="green" /></div>
{:else}
	<div class="overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm">
		{#each items as m (m.id)}
			<div class="flex items-start gap-3.5 border-b border-gray-100 p-4 last:border-0">
				<div class="mt-1.5 size-2.5 flex-none rounded-full {m.unread ? 'bg-green-500' : 'bg-gray-300'}"></div>
				<div class="min-w-0 flex-1">
					<div class="text-sm {m.unread ? 'font-semibold text-gray-900' : 'font-medium text-gray-700'}">{m.title}</div>
					<div class="mt-0.5 text-sm text-gray-500">{m.body}</div>
					{#if m.link}<a href={m.link} class="mt-1 inline-block font-mono text-xs text-green-700 hover:underline">Open →</a>{/if}
				</div>
				<div class="whitespace-nowrap text-right">
					<div class="font-mono text-xs text-gray-400">{dt(m.createdAt)}</div>
					{#if m.unread}<Button size="xs" color="alternative" class="mt-1.5" onclick={() => markRead(m)}>Mark read</Button>{/if}
				</div>
			</div>
		{:else}
			<div class="p-8 text-center text-sm text-gray-400">No notifications.</div>
		{/each}
	</div>
{/if}
