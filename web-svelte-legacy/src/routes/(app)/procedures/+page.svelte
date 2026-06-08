<script lang="ts">
	import { onMount } from 'svelte';
	import { goto } from '$app/navigation';
	import { api } from '$lib/api';
	import { toast } from '$lib/stores';
	import { badgeColor } from '$lib/ui';
	import {
		Table,
		TableHead,
		TableHeadCell,
		TableBody,
		TableBodyRow,
		TableBodyCell,
		Badge,
		Button,
		ButtonGroup,
		Modal,
		Input,
		Label,
		Select
	} from 'flowbite-svelte';

	type Proc = {
		id: string;
		documentNumber: string;
		title: string;
		type: string;
		owner: string;
		state: string;
		effectiveDate: string | null;
		nextReviewDate: string | null;
	};

	const TYPE_ITEMS = [
		{ value: 'SOP', name: 'SOP' },
		{ value: 'POLICY', name: 'Policy' },
		{ value: 'WORK_INSTRUCTION', name: 'Work instruction' },
		{ value: 'FORM', name: 'Form' },
		{ value: 'JOB_AID', name: 'Job aid' }
	];

	const STATE_ITEMS = [
		{ value: 'ALL', name: 'All statuses' },
		{ value: 'DRAFT', name: 'Draft' },
		{ value: 'IN_REVIEW', name: 'In review' },
		{ value: 'APPROVED', name: 'Approved' },
		{ value: 'EFFECTIVE', name: 'Effective' },
		{ value: 'UNDER_REVIEW', name: 'Under review' },
		{ value: 'RETIRED', name: 'Retired' }
	];

	const COLUMNS: { key: keyof Proc; label: string }[] = [
		{ key: 'documentNumber', label: 'Document' },
		{ key: 'title', label: 'Title' },
		{ key: 'type', label: 'Type' },
		{ key: 'owner', label: 'Owner' },
		{ key: 'state', label: 'Status' },
		{ key: 'effectiveDate', label: 'Effective' },
		{ key: 'nextReviewDate', label: 'Next review' }
	];

	let rows = $state<Proc[]>([]);
	let loading = $state(true);
	let state = $state('ALL');
	let q = $state('');
	let sortKey = $state<keyof Proc>('documentNumber');
	let sortDir = $state<1 | -1>(1);

	function sortBy(k: keyof Proc) {
		if (sortKey === k) sortDir = (sortDir * -1) as 1 | -1;
		else {
			sortKey = k;
			sortDir = 1;
		}
	}
	let showNew = $state(false);
	let docType = $state('SOP');
	let title = $state('');
	let saving = $state(false);

	let filtered = $derived(
		rows.filter((r) => {
			const t = q.trim().toLowerCase();
			return (
				!t ||
				r.title.toLowerCase().includes(t) ||
				r.documentNumber.toLowerCase().includes(t) ||
				r.owner.toLowerCase().includes(t)
			);
		})
	);

	let sorted = $derived(
		[...filtered].sort((a, b) => {
			const x = (a[sortKey] ?? '') as string;
			const y = (b[sortKey] ?? '') as string;
			return x.localeCompare(y) * sortDir;
		})
	);

	async function load() {
		loading = true;
		try {
			rows = await api.get<Proc[]>(`/api/v1/procedures?state=${state}`);
		} catch (e) {
			toast((e as Error).message, 'err');
		} finally {
			loading = false;
		}
	}

	async function create() {
		saving = true;
		try {
			const p = await api.post<{ id: string; documentNumber: string }>('/api/v1/procedures', {
				title,
				type: docType
			});
			toast(`Created ${p.documentNumber}`, 'ok');
			showNew = false;
			docType = 'SOP';
			title = '';
			await goto(`/procedures/${p.id}`);
		} catch (err) {
			toast((err as Error).message, 'err');
		} finally {
			saving = false;
		}
	}

	onMount(load);
</script>

<div class="toolbar">
	<div class="grow" style="max-width: 360px">
		<Input placeholder="Search title, document #, owner…" bind:value={q} />
	</div>
	<div style="width: 180px">
		<Select items={STATE_ITEMS} bind:value={state} onchange={load} />
	</div>
	<span class="text-xs text-gray-400">{filtered.length} of {rows.length}</span>
	<div class="grow"></div>
	<ButtonGroup class="actions">
		<Button color="green" onclick={() => (showNew = true)}>
			<svg class="me-1.5 size-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 5v14M5 12h14" /></svg>
			New procedure
		</Button>
	</ButtonGroup>
</div>

<Table hoverable shadow>
	<TableHead>
		{#each COLUMNS as c (c.key)}
			<TableHeadCell class="sortable" onclick={() => sortBy(c.key)}>
				{c.label}<span class="sort-ind">{sortKey === c.key ? (sortDir === 1 ? '▲' : '▼') : '↕'}</span>
			</TableHeadCell>
		{/each}
	</TableHead>
	<TableBody>
		{#if loading}
			<TableBodyRow><TableBodyCell colspan={7} class="text-center text-gray-400">Loading…</TableBodyCell></TableBodyRow>
		{:else}
			{#each sorted as p (p.id)}
				<TableBodyRow class="cursor-pointer" onclick={() => goto(`/procedures/${p.id}`)}>
					<TableBodyCell class="font-mono text-gray-600">{p.documentNumber}</TableBodyCell>
					<TableBodyCell class="font-medium text-gray-900">{p.title}</TableBodyCell>
					<TableBodyCell class="font-mono text-gray-500">{p.type}</TableBodyCell>
					<TableBodyCell class="font-normal text-gray-600">{p.owner}</TableBodyCell>
					<TableBodyCell><Badge color={badgeColor(p.state)}>{p.state}</Badge></TableBodyCell>
					<TableBodyCell class="font-mono text-gray-500">{p.effectiveDate ?? '—'}</TableBodyCell>
					<TableBodyCell class="font-mono text-gray-500">{p.nextReviewDate ?? '—'}</TableBodyCell>
				</TableBodyRow>
			{:else}
				<TableBodyRow><TableBodyCell colspan={7} class="text-center text-gray-400">No procedures match.</TableBodyCell></TableBodyRow>
			{/each}
		{/if}
	</TableBody>
</Table>

<Modal title="New procedure" bind:open={showNew} size="sm">
	<p class="-mt-2 text-sm text-gray-500">Creates a draft you can author and submit for approval.</p>
	<div>
		<Label class="mb-2">Type</Label>
		<Select items={TYPE_ITEMS} bind:value={docType} />
		<p class="mt-1 text-xs text-gray-400">
			A document number is assigned automatically (e.g. SOP-0001).
		</p>
	</div>
	<div>
		<Label class="mb-2">Title</Label>
		<Input bind:value={title} placeholder="Procedure title" required />
	</div>
	{#snippet footer()}
		<div class="flex w-full justify-end gap-2">
			<Button color="alternative" onclick={() => (showNew = false)}>Cancel</Button>
			<Button color="green" disabled={saving || !title} onclick={create}>
				{saving ? 'Creating…' : 'Create'}
			</Button>
		</div>
	{/snippet}
</Modal>
