<script lang="ts">
	import { onMount } from 'svelte';
	import { api } from '$lib/api';
	import { toast, approvalCount } from '$lib/stores';
	import { dt } from '$lib/ui';
	import {
		Table,
		TableHead,
		TableHeadCell,
		TableBody,
		TableBodyRow,
		TableBodyCell,
		Badge,
		Button,
		Modal,
		Label,
		Input,
		Textarea
	} from 'flowbite-svelte';

	type Task = {
		id: string;
		procedureId: string;
		procedureTitle: string;
		stage: string;
		role: string;
		meaning: string;
		status: string;
		due: string | null;
	};

	let tasks = $state<Task[]>([]);
	let loading = $state(true);
	let q = $state('');

	let filtered = $derived(
		tasks.filter((t) => {
			const s = q.trim().toLowerCase();
			return (
				!s ||
				t.procedureTitle.toLowerCase().includes(s) ||
				t.stage.toLowerCase().includes(s) ||
				t.role.toLowerCase().includes(s)
			);
		})
	);

	let active = $state<Task | null>(null);
	let open = $state(false);
	let mode = $state<'approve' | 'reject'>('approve');
	let password = $state('');
	let reason = $state('');
	let busy = $state(false);

	async function load() {
		loading = true;
		try {
			tasks = await api.get<Task[]>('/api/v1/approvals');
			approvalCount.set(tasks.length);
		} catch (e) {
			toast((e as Error).message, 'err');
		} finally {
			loading = false;
		}
	}

	function start(t: Task, m: 'approve' | 'reject') {
		active = t;
		mode = m;
		password = '';
		reason = '';
		open = true;
	}

	async function submit() {
		if (!active) return;
		busy = true;
		try {
			await api.post(`/api/v1/approvals/${active.id}/decide`, {
				approve: mode === 'approve',
				password: mode === 'approve' ? password : null,
				reason: mode === 'reject' ? reason : null
			});
			toast(mode === 'approve' ? 'Approved & signed' : 'Change request rejected', 'ok');
			open = false;
			await load();
		} catch (err) {
			const msg = (err as Error).message || 'Decision failed';
			toast(msg.includes('re-authentication') ? 'Password incorrect — not signed' : msg, 'err');
		} finally {
			busy = false;
		}
	}

	onMount(load);
</script>

<p class="text-sm text-gray-500 mb-3">
	Tasks assigned to roles you hold. Approving signs a 21 CFR Part 11 electronic signature.
</p>

<div class="toolbar">
	<div class="grow" style="max-width: 360px"><Input placeholder="Filter by procedure, stage, role…" bind:value={q} /></div>
	<span class="text-xs text-gray-400">{filtered.length} pending</span>
</div>

<Table hoverable shadow>
	<TableHead>
		<TableHeadCell>Procedure</TableHeadCell>
		<TableHeadCell>Stage</TableHeadCell>
		<TableHeadCell>Sign as</TableHeadCell>
		<TableHeadCell>Due</TableHeadCell>
		<TableHeadCell class="text-right">Decision</TableHeadCell>
	</TableHead>
	<TableBody>
		{#if loading}
			<TableBodyRow><TableBodyCell colspan={5} class="text-center text-gray-400">Loading…</TableBodyCell></TableBodyRow>
		{:else}
			{#each filtered as t (t.id)}
				<TableBodyRow>
					<TableBodyCell class="font-normal"><a class="text-primary-700 hover:underline" href={`/procedures/${t.procedureId}`}>{t.procedureTitle}</a></TableBodyCell>
					<TableBodyCell class="font-normal">{t.stage}</TableBodyCell>
					<TableBodyCell class="font-normal"><Badge color="indigo">{t.meaning}</Badge> <span class="font-mono text-xs text-gray-500">{t.role}</span></TableBodyCell>
					<TableBodyCell class="font-mono text-gray-500">{dt(t.due)}</TableBodyCell>
					<TableBodyCell class="text-right whitespace-nowrap">
						<Button size="xs" color="green" onclick={() => start(t, 'approve')}>Approve</Button>
						<Button size="xs" color="red" outline onclick={() => start(t, 'reject')}>Reject</Button>
					</TableBodyCell>
				</TableBodyRow>
			{:else}
				<TableBodyRow><TableBodyCell colspan={5} class="text-center text-gray-400">Your approval queue is empty. 🎉</TableBodyCell></TableBodyRow>
			{/each}
		{/if}
	</TableBody>
</Table>

<Modal title={mode === 'approve' ? 'Approve & sign' : 'Reject change'} bind:open size="sm">
	{#if active}
		<p class="text-sm text-gray-500 -mt-2">{active.procedureTitle} · {active.stage}</p>
		{#if mode === 'approve'}
			<div>
				<Label class="mb-2">Re-enter your password to sign ({active.meaning})</Label>
				<Input type="password" bind:value={password} autocomplete="current-password" />
			</div>
		{:else}
			<div>
				<Label class="mb-2">Reason for rejection</Label>
				<Textarea rows={3} bind:value={reason} placeholder="Sent back to draft" />
			</div>
		{/if}
	{/if}
	{#snippet footer()}
		<div class="flex w-full justify-end gap-2">
			<Button color="alternative" onclick={() => (open = false)}>Cancel</Button>
			<Button color={mode === 'approve' ? 'green' : 'red'} disabled={busy} onclick={submit}>
				{busy ? 'Working…' : mode === 'approve' ? 'Approve & sign' : 'Reject'}
			</Button>
		</div>
	{/snippet}
</Modal>
