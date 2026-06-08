<script lang="ts">
	import { onMount } from 'svelte';
	import { goto } from '$app/navigation';
	import { api } from '$lib/api';
	import { badgeColor, dt } from '$lib/ui';
	import {
		Table,
		TableHead,
		TableHeadCell,
		TableBody,
		TableBodyRow,
		TableBodyCell,
		Badge,
		Spinner
	} from 'flowbite-svelte';

	type Kpi = { label: string; value: number };
	type Overdue = { procedureId: string; documentNumber: string; title: string; due: string };
	type Approval = { procedureId: string; procedureTitle: string; stage: string; role: string };
	type Cr = {
		procedureId: string;
		procedureTitle: string;
		title: string;
		status: string;
		classification: string;
	};
	type Dev = { id: string; category: string; description: string; loggedAt: string; open: boolean };
	type Dash = {
		kpis: Kpi[];
		overdueReviews: Overdue[];
		approvals: Approval[];
		changeRequests: Cr[];
		deviations: Dev[];
	};

	let d = $state<Dash | null>(null);
	let error = $state('');

	onMount(async () => {
		try {
			d = await api.get<Dash>('/api/v1/dashboard');
		} catch (e) {
			error = (e as Error).message;
		}
	});
</script>

<p class="text-sm text-gray-500 mb-5">Live compliance posture across authoring, change control, and execution.</p>

{#if error}
	<div class="empty">Couldn't load the dashboard: {error}</div>
{:else if !d}
	<div class="center-screen"><Spinner color="green" /></div>
{:else}
	<div class="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-7 mb-2">
		{#each d.kpis as k (k.label)}
			<div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm">
				<div class="text-3xl font-bold tracking-tight text-gray-900 tabular-nums">{k.value}</div>
				<div class="mt-1 text-xs text-gray-500">{k.label}</div>
			</div>
		{/each}
	</div>

	<h2 class="mt-8 mb-3 text-sm font-semibold text-gray-900">Overdue periodic reviews</h2>
	<Table hoverable shadow>
		<TableHead>
			<TableHeadCell>Document</TableHeadCell>
			<TableHeadCell>Title</TableHeadCell>
			<TableHeadCell>Review due</TableHeadCell>
		</TableHead>
		<TableBody>
			{#each d.overdueReviews as r (r.procedureId)}
				<TableBodyRow class="cursor-pointer" onclick={() => goto(`/procedures/${r.procedureId}`)}>
					<TableBodyCell class="font-mono text-gray-600">{r.documentNumber}</TableBodyCell>
					<TableBodyCell class="font-normal">{r.title}</TableBodyCell>
					<TableBodyCell><Badge color="red">{r.due}</Badge></TableBodyCell>
				</TableBodyRow>
			{:else}
				<TableBodyRow><TableBodyCell colspan={3} class="text-center text-gray-400">None overdue.</TableBodyCell></TableBodyRow>
			{/each}
		</TableBody>
	</Table>

	<h2 class="mt-8 mb-3 text-sm font-semibold text-gray-900">Pending approvals</h2>
	<Table hoverable shadow>
		<TableHead>
			<TableHeadCell>Procedure</TableHeadCell>
			<TableHeadCell>Stage</TableHeadCell>
			<TableHeadCell>Role</TableHeadCell>
		</TableHead>
		<TableBody>
			{#each d.approvals as a (a.procedureId + a.stage + a.role)}
				<TableBodyRow class="cursor-pointer" onclick={() => goto('/approvals')}>
					<TableBodyCell class="font-normal">{a.procedureTitle}</TableBodyCell>
					<TableBodyCell class="font-normal">{a.stage}</TableBodyCell>
					<TableBodyCell><Badge color="yellow">{a.role}</Badge></TableBodyCell>
				</TableBodyRow>
			{:else}
				<TableBodyRow><TableBodyCell colspan={3} class="text-center text-gray-400">No pending approvals.</TableBodyCell></TableBodyRow>
			{/each}
		</TableBody>
	</Table>

	<h2 class="mt-8 mb-3 text-sm font-semibold text-gray-900">Open change requests</h2>
	<Table hoverable shadow>
		<TableHead>
			<TableHeadCell>Procedure</TableHeadCell>
			<TableHeadCell>Change</TableHeadCell>
			<TableHeadCell>Class</TableHeadCell>
			<TableHeadCell>Status</TableHeadCell>
		</TableHead>
		<TableBody>
			{#each d.changeRequests as c (c.procedureId + c.title)}
				<TableBodyRow class="cursor-pointer" onclick={() => goto(`/procedures/${c.procedureId}`)}>
					<TableBodyCell class="font-normal">{c.procedureTitle}</TableBodyCell>
					<TableBodyCell class="font-normal">{c.title}</TableBodyCell>
					<TableBodyCell class="font-mono">{c.classification}</TableBodyCell>
					<TableBodyCell><Badge color={badgeColor(c.status)}>{c.status}</Badge></TableBodyCell>
				</TableBodyRow>
			{:else}
				<TableBodyRow><TableBodyCell colspan={4} class="text-center text-gray-400">No open change requests.</TableBodyCell></TableBodyRow>
			{/each}
		</TableBody>
	</Table>

	<h2 class="mt-8 mb-3 text-sm font-semibold text-gray-900">Recent deviations</h2>
	<Table hoverable shadow>
		<TableHead>
			<TableHeadCell>Logged</TableHeadCell>
			<TableHeadCell>Category</TableHeadCell>
			<TableHeadCell>Description</TableHeadCell>
			<TableHeadCell>State</TableHeadCell>
		</TableHead>
		<TableBody>
			{#each d.deviations as dv (dv.id)}
				<TableBodyRow>
					<TableBodyCell class="font-mono text-gray-500">{dt(dv.loggedAt)}</TableBodyCell>
					<TableBodyCell class="font-mono">{dv.category}</TableBodyCell>
					<TableBodyCell class="font-normal">{dv.description}</TableBodyCell>
					<TableBodyCell><Badge color={dv.open ? 'red' : 'green'}>{dv.open ? 'open' : 'closed'}</Badge></TableBodyCell>
				</TableBodyRow>
			{:else}
				<TableBodyRow><TableBodyCell colspan={4} class="text-center text-gray-400">No deviations logged.</TableBodyCell></TableBodyRow>
			{/each}
		</TableBody>
	</Table>
{/if}
