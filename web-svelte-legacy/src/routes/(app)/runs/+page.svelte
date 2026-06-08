<script lang="ts">
	import { onMount } from 'svelte';
	import { goto } from '$app/navigation';
	import { api } from '$lib/api';
	import { toast } from '$lib/stores';
	import { badgeColor, dt } from '$lib/ui';
	import {
		Table,
		TableHead,
		TableHeadCell,
		TableBody,
		TableBodyRow,
		TableBodyCell,
		Badge,
		Spinner,
		Input
	} from 'flowbite-svelte';

	type RunRow = {
		runId: string;
		procedureId: string;
		procedureTitle: string;
		state: string;
		startedAt: string;
		durationSeconds: number | null;
		deviationCount: number;
	};
	type Stats = {
		procedureId: string;
		procedureTitle: string;
		runCount: number;
		completionPct: number;
		avgDurationSeconds: number | null;
		deviationCount: number;
	};

	let history = $state<RunRow[]>([]);
	let analytics = $state<Stats[]>([]);
	let loading = $state(true);
	let q = $state('');

	let fAnalytics = $derived(
		analytics.filter((a) => a.procedureTitle.toLowerCase().includes(q.trim().toLowerCase()))
	);
	let fHistory = $derived(
		history.filter((r) => r.procedureTitle.toLowerCase().includes(q.trim().toLowerCase()))
	);

	const dur = (s: number | null) => (s == null ? '—' : s < 60 ? `${s}s` : `${Math.round(s / 60)}m`);

	onMount(async () => {
		try {
			const d = await api.get<{ history: RunRow[]; analytics: Stats[] }>('/api/v1/runs');
			history = d.history;
			analytics = d.analytics;
		} catch (e) {
			toast((e as Error).message, 'err');
		} finally {
			loading = false;
		}
	});
</script>

<p class="text-sm text-gray-500 mb-3">Execution analytics and recent runs across all procedures.</p>

{#if loading}
	<div class="center-screen"><Spinner color="green" /></div>
{:else}
	<div class="toolbar">
		<div class="grow" style="max-width: 360px"><Input placeholder="Filter by procedure…" bind:value={q} /></div>
		<span class="text-xs text-gray-400">{fHistory.length} run(s)</span>
	</div>
	<h2 class="mb-3 text-sm font-semibold text-gray-900">Per-procedure analytics</h2>
	<Table hoverable shadow>
		<TableHead>
			<TableHeadCell>Procedure</TableHeadCell>
			<TableHeadCell>Runs</TableHeadCell>
			<TableHeadCell>Completion</TableHeadCell>
			<TableHeadCell>Avg duration</TableHeadCell>
			<TableHeadCell>Deviations</TableHeadCell>
		</TableHead>
		<TableBody>
			{#each fAnalytics as a (a.procedureId)}
				<TableBodyRow class="cursor-pointer" onclick={() => goto(`/procedures/${a.procedureId}`)}>
					<TableBodyCell class="font-normal">{a.procedureTitle}</TableBodyCell>
					<TableBodyCell class="font-mono">{a.runCount}</TableBodyCell>
					<TableBodyCell><Badge color={a.completionPct === 100 ? 'green' : 'yellow'}>{a.completionPct}%</Badge></TableBodyCell>
					<TableBodyCell class="font-mono">{dur(a.avgDurationSeconds)}</TableBodyCell>
					<TableBodyCell class="font-mono">{a.deviationCount}</TableBodyCell>
				</TableBodyRow>
			{:else}
				<TableBodyRow><TableBodyCell colspan={5} class="text-center text-gray-400">No runs yet.</TableBodyCell></TableBodyRow>
			{/each}
		</TableBody>
	</Table>

	<h2 class="mt-8 mb-3 text-sm font-semibold text-gray-900">Recent runs</h2>
	<Table hoverable shadow>
		<TableHead>
			<TableHeadCell>Procedure</TableHeadCell>
			<TableHeadCell>State</TableHeadCell>
			<TableHeadCell>Started</TableHeadCell>
			<TableHeadCell>Duration</TableHeadCell>
			<TableHeadCell>Deviations</TableHeadCell>
		</TableHead>
		<TableBody>
			{#each fHistory as r (r.runId)}
				<TableBodyRow>
					<TableBodyCell class="font-normal">{r.procedureTitle}</TableBodyCell>
					<TableBodyCell><Badge color={badgeColor(r.state)}>{r.state}</Badge></TableBodyCell>
					<TableBodyCell class="font-mono text-gray-500">{dt(r.startedAt)}</TableBodyCell>
					<TableBodyCell class="font-mono">{dur(r.durationSeconds)}</TableBodyCell>
					<TableBodyCell class="font-mono">{r.deviationCount}</TableBodyCell>
				</TableBodyRow>
			{:else}
				<TableBodyRow><TableBodyCell colspan={5} class="text-center text-gray-400">No runs yet.</TableBodyCell></TableBodyRow>
			{/each}
		</TableBody>
	</Table>
{/if}
