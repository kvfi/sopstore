<script lang="ts">
	import { onMount } from 'svelte';
	import { page } from '$app/state';
	import { api, upload } from '$lib/api';
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
		Button,
		Modal,
		Label,
		Select,
		Textarea,
		Spinner
	} from 'flowbite-svelte';

	// A step as authored in the structured body document.
	type StepDraft = {
		title: string;
		instruction: string;
		expectedOutcome: string;
		warning: string;
	};
	// Entity step shape still returned by the detail endpoint (unused by the editor now).
	type Step = {
		id: string;
		order: number;
		title: string;
		instruction: string;
		expectedOutcome: string | null;
		warning: string | null;
		evidenceSpec: string | null;
	};
	type Version = { id: string; label: string; createdAt: string; changeRequestId: string | null };
	type Detail = {
		id: string;
		documentNumber: string;
		title: string;
		state: string;
		currentVersionId: string | null;
		steps: Step[];
		versions: Version[];
	};
	type Cr = {
		id: string;
		title: string;
		status: string;
		classification: string;
		trainingImpact: boolean;
		createdAt: string;
	};
	type Task = { id: string; stage: string; role: string; meaning: string; status: string; due: string | null };
	type Cc = { changeRequests: Cr[]; tasks: Task[] };
	type Att = {
		id: string;
		filename: string;
		mime: string;
		size: number;
		sha256: string;
		uploadedAt: string;
	};

	let id = $derived(page.params.id);
	let d = $state<Detail | null>(null);
	let cc = $state<Cc | null>(null);
	let atts = $state<Att[]>([]);
	let uploading = $state(false);
	let fileInput = $state<HTMLInputElement>();
	let error = $state('');

	const fmtSize = (n: number) =>
		n < 1024 ? `${n} B` : n < 1048576 ? `${(n / 1024).toFixed(1)} KB` : `${(n / 1048576).toFixed(1)} MB`;

	// Predefined change-request reasons; each carries classification + training impact
	// so the approval workflow routes correctly (MAJOR / training adds a QA Director stage).
	type Reason = { label: string; classification: string; trainingImpact: boolean };
	const REASONS: Reason[] = [
		{ label: 'Periodic review', classification: 'MINOR', trainingImpact: false },
		{ label: 'Editorial / content correction', classification: 'MINOR', trainingImpact: false },
		{ label: 'Process improvement', classification: 'MINOR', trainingImpact: false },
		{ label: 'Major revision', classification: 'MAJOR', trainingImpact: true },
		{ label: 'Regulatory / compliance update', classification: 'MAJOR', trainingImpact: true },
		{ label: 'Corrective action (CAPA)', classification: 'MAJOR', trainingImpact: true }
	];
	const reasonItems = REASONS.map((r, i) => ({ value: i, name: r.label }));

	let showCr = $state(false);
	let crReason = $state(0);
	let crComment = $state('');
	let saving = $state(false);

	// Structured procedure document (persisted as the version body JSON).
	let purpose = $state('');
	let scope = $state('');
	let prerequisites = $state<string[]>([]);
	let steps = $state<StepDraft[]>([]);
	let bodyReady = $state(false);
	let bodyDirty = $state(false);
	let savingBody = $state(false);
	let editable = $derived(d?.state === 'DRAFT');

	const markDirty = () => {
		bodyDirty = true;
	};
	const emptyStep = (): StepDraft => ({ title: '', instruction: '', expectedOutcome: '', warning: '' });

	function addPrerequisite() {
		prerequisites.push('');
		markDirty();
	}
	function removePrerequisite(i: number) {
		prerequisites.splice(i, 1);
		markDirty();
	}
	function addStep() {
		steps.push(emptyStep());
		markDirty();
	}
	function removeStep(i: number) {
		steps.splice(i, 1);
		markDirty();
	}
	function moveStep(i: number, delta: number) {
		const j = i + delta;
		if (j < 0 || j >= steps.length) return;
		[steps[i], steps[j]] = [steps[j], steps[i]];
		markDirty();
	}

	async function load() {
		try {
			const [detail, control, body, attachments] = await Promise.all([
				api.get<Detail>(`/api/v1/procedures/${id}/detail`),
				api.get<Cc>(`/api/v1/procedures/${id}/change-requests`),
				api.get<{ body: string }>(`/api/v1/procedures/${id}/body`),
				api.get<Att[]>(`/api/v1/procedures/${id}/attachments`)
			]);
			d = detail;
			cc = control;
			atts = attachments;
			let doc: Record<string, unknown> = {};
			try {
				doc = body.body ? JSON.parse(body.body) : {};
			} catch {
				doc = {};
			}
			purpose = typeof doc.purpose === 'string' ? doc.purpose : '';
			scope = typeof doc.scope === 'string' ? doc.scope : '';
			prerequisites = Array.isArray(doc.prerequisites) ? doc.prerequisites.map(String) : [];
			steps = Array.isArray(doc.steps)
				? (doc.steps as Record<string, unknown>[]).map((s) => ({
						title: typeof s.title === 'string' ? s.title : '',
						instruction: typeof s.instruction === 'string' ? s.instruction : '',
						expectedOutcome: typeof s.expectedOutcome === 'string' ? s.expectedOutcome : '',
						warning: typeof s.warning === 'string' ? s.warning : ''
					}))
				: [];
			bodyReady = true;
		} catch (e) {
			error = (e as Error).message;
		}
	}

	async function saveBody() {
		savingBody = true;
		try {
			const doc = {
				purpose: purpose.trim(),
				scope: scope.trim(),
				prerequisites: prerequisites.map((p) => p.trim()).filter(Boolean),
				steps: steps
					.map((s) => ({
						title: s.title.trim(),
						instruction: s.instruction.trim(),
						expectedOutcome: s.expectedOutcome.trim(),
						warning: s.warning.trim()
					}))
					.filter((s) => s.title || s.instruction || s.expectedOutcome || s.warning)
			};
			await api.put(`/api/v1/procedures/${id}/body`, { body: JSON.stringify(doc) });
			bodyDirty = false;
			toast('Content saved', 'ok');
		} catch (e) {
			toast((e as Error).message, 'err');
		} finally {
			savingBody = false;
		}
	}

	async function openCr() {
		saving = true;
		try {
			const reason = REASONS[crReason];
			await api.post(`/api/v1/procedures/${id}/change-requests`, {
				title: reason.label,
				reason: crComment.trim() || reason.label,
				classification: reason.classification,
				trainingImpact: reason.trainingImpact
			});
			toast('Change request opened — approval workflow started', 'ok');
			showCr = false;
			crReason = 0;
			crComment = '';
			await load();
		} catch (err) {
			toast((err as Error).message, 'err');
		} finally {
			saving = false;
		}
	}

	async function refreshAtts() {
		atts = await api.get<Att[]>(`/api/v1/procedures/${id}/attachments`);
	}

	async function onFile(e: Event) {
		const f = (e.target as HTMLInputElement).files?.[0];
		if (!f) return;
		uploading = true;
		try {
			await upload(`/api/v1/procedures/${id}/attachments`, f);
			toast(`Attached ${f.name}`, 'ok');
			await refreshAtts();
		} catch (err) {
			toast((err as Error).message, 'err');
		} finally {
			uploading = false;
			if (fileInput) fileInput.value = '';
		}
	}

	async function removeAtt(a: Att) {
		const ok = window.confirm(`Delete "${a.filename}"? This cannot be undone.`);
		if (!ok) return;
		try {
			await api.del(`/api/v1/procedures/${id}/attachments/${a.id}`);
			toast(`Removed ${a.filename}`, 'ok');
			await refreshAtts();
		} catch (err) {
			toast((err as Error).message, 'err');
		}
	}

	onMount(load);
</script>

{#if error}
	<div class="empty">Couldn't load this procedure: {error}</div>
{:else if !d}
	<div class="center-screen"><Spinner color="green" /></div>
{:else}
	<a href="/procedures" class="text-sm text-gray-500 hover:underline">← Procedures</a>
	<div class="mt-2 mb-1 flex items-center gap-3">
		<h1 class="m-0 text-2xl font-bold tracking-tight text-gray-900">{d.title}</h1>
		<Badge large color={badgeColor(d.state)}>{d.state}</Badge>
	</div>
	<p class="font-mono text-sm text-gray-500">{d.documentNumber}</p>

	<div class="mt-4 flex flex-wrap items-center gap-2">
		<Button color="green" disabled={d.state !== 'DRAFT'} onclick={() => (showCr = true)}>
			Open change request
		</Button>
		<Button color="alternative" href={`/api/v1/procedures/${id}/export.docx`}>
			<svg class="me-1.5" viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><path d="M7 10l5 5 5-5"/><path d="M12 15V3"/></svg>
			Download Word
		</Button>
		<Button color="alternative" href={`/api/v1/procedures/${id}/bundle.zip`}>
			<svg class="me-1.5" viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M21 8v13H3V8"/><path d="M1 3h22v5H1z"/><path d="M10 12h4"/></svg>
			Download bundle (.zip)
		</Button>
		{#if d.state !== 'DRAFT'}
			<span class="text-sm text-gray-500">Editing a controlled procedure requires a new draft revision.</span>
		{/if}
	</div>

	<div class="mt-8 mb-3 flex items-center gap-3">
		<h2 class="m-0 text-sm font-semibold text-gray-900">Procedure document</h2>
		{#if editable}
			<Button size="xs" color="green" class="ms-auto" disabled={savingBody || !bodyDirty} onclick={saveBody}>
				{savingBody ? 'Saving…' : bodyDirty ? 'Save content' : 'Saved'}
			</Button>
		{:else}
			<span class="ms-auto text-sm text-gray-400">Read-only — open a draft revision to edit</span>
		{/if}
	</div>
	{#if !bodyReady}
		<div class="skeleton h-64 rounded-xl"></div>
	{:else}
		<div class="doc-sheet">
			<!-- Purpose -->
			<section class="doc-block" style="animation-delay: 0ms">
				<div class="doc-eyebrow"><span class="doc-eyebrow-mark">01</span> Purpose</div>
				<p class="doc-hint">Why this procedure exists and what it sets out to achieve.</p>
				<textarea
					class="doc-field"
					rows="3"
					bind:value={purpose}
					disabled={!editable}
					oninput={markDirty}
					placeholder="Describe the objective of this procedure…"
				></textarea>
			</section>

			<div class="doc-rule"></div>

			<!-- Scope -->
			<section class="doc-block" style="animation-delay: 70ms">
				<div class="doc-eyebrow"><span class="doc-eyebrow-mark">02</span> Scope</div>
				<p class="doc-hint">Who and what this applies to — roles, sites, systems, and any exclusions.</p>
				<textarea
					class="doc-field"
					rows="3"
					bind:value={scope}
					disabled={!editable}
					oninput={markDirty}
					placeholder="Define the boundaries of this procedure…"
				></textarea>
			</section>

			<div class="doc-rule"></div>

			<!-- Prerequisites -->
			<section class="doc-block" style="animation-delay: 140ms">
				<div class="doc-eyebrow"><span class="doc-eyebrow-mark">03</span> Prerequisites</div>
				<p class="doc-hint">Conditions, tools, or approvals required before starting.</p>
				<div class="prq-list">
					{#each prerequisites as _prq, i (i)}
						<div class="prq-row">
							<span class="prq-dot">{i + 1}</span>
							<input
								class="doc-field"
								bind:value={prerequisites[i]}
								disabled={!editable}
								oninput={markDirty}
								placeholder="e.g. Calibrated torque wrench available"
							/>
							{#if editable}
								<button type="button" class="ctl rm" title="Remove" aria-label="Remove prerequisite" onclick={() => removePrerequisite(i)}>✕</button>
							{/if}
						</div>
					{:else}
						<p class="doc-muted">None yet.</p>
					{/each}
				</div>
				{#if editable}
					<button type="button" class="doc-add" onclick={addPrerequisite}>
						<span class="plus">+</span> Add prerequisite
					</button>
				{/if}
			</section>

			<div class="doc-rule"></div>

			<!-- Steps -->
			<section class="doc-block" style="animation-delay: 210ms">
				<div class="doc-eyebrow"><span class="doc-eyebrow-mark">04</span> Steps</div>
				<p class="doc-hint">The ordered actions to carry out the procedure.</p>
				<div class="step-list">
					{#each steps as _step, i (i)}
						<div class="step-card">
							<div class="step-head">
								<span class="step-num">{i + 1}</span>
								<input
									class="step-title-input"
									bind:value={steps[i].title}
									disabled={!editable}
									oninput={markDirty}
									placeholder="Step title"
								/>
								{#if editable}
									<div class="step-actions">
										<button type="button" class="ctl" title="Move up" aria-label="Move step up" disabled={i === 0} onclick={() => moveStep(i, -1)}>↑</button>
										<button type="button" class="ctl" title="Move down" aria-label="Move step down" disabled={i === steps.length - 1} onclick={() => moveStep(i, 1)}>↓</button>
										<button type="button" class="ctl rm" title="Remove step" aria-label="Remove step" onclick={() => removeStep(i)}>✕</button>
									</div>
								{/if}
							</div>
							<div class="step-body">
								<label class="flabel">
									Instruction
									<textarea
										class="doc-field"
										rows="2"
										bind:value={steps[i].instruction}
										disabled={!editable}
										oninput={markDirty}
										placeholder="What to do…"
									></textarea>
								</label>
								<div class="step-two">
									<label class="flabel">
										Expected outcome
										<input
											class="doc-field"
											bind:value={steps[i].expectedOutcome}
											disabled={!editable}
											oninput={markDirty}
											placeholder="What success looks like"
										/>
									</label>
									<label class="flabel warn">
										Warning
										<input
											class="doc-field"
											bind:value={steps[i].warning}
											disabled={!editable}
											oninput={markDirty}
											placeholder="Safety / caution note"
										/>
									</label>
								</div>
							</div>
						</div>
					{:else}
						<p class="doc-muted">No steps yet.</p>
					{/each}
				</div>
				{#if editable}
					<button type="button" class="doc-add" onclick={addStep}>
						<span class="plus">+</span> Add step
					</button>
				{/if}
			</section>
		</div>
	{/if}

	<div class="mt-8 mb-3 flex items-center gap-3">
		<h2 class="m-0 text-sm font-semibold text-gray-900">Attachments ({atts.length})</h2>
		<input class="hidden" type="file" bind:this={fileInput} onchange={onFile} />
		<Button size="xs" color="green" class="ms-auto" disabled={uploading} onclick={() => fileInput?.click()}>
			{uploading ? 'Uploading…' : '+ Attach file'}
		</Button>
	</div>
	<Table hoverable shadow>
		<TableHead>
			<TableHeadCell>File</TableHeadCell>
			<TableHeadCell>Type</TableHeadCell>
			<TableHeadCell>Size</TableHeadCell>
			<TableHeadCell>Uploaded</TableHeadCell>
			<TableHeadCell class="text-right">Actions</TableHeadCell>
		</TableHead>
		<TableBody>
			{#each atts as a (a.id)}
				<TableBodyRow>
					<TableBodyCell class="font-medium text-gray-900">{a.filename}</TableBodyCell>
					<TableBodyCell class="font-mono text-gray-500">{a.mime}</TableBodyCell>
					<TableBodyCell class="font-mono">{fmtSize(a.size)}</TableBodyCell>
					<TableBodyCell class="font-mono text-gray-500">{dt(a.uploadedAt)}</TableBodyCell>
					<TableBodyCell class="text-right whitespace-nowrap">
						<a class="text-primary-700 hover:underline" href={`/api/v1/procedures/${id}/attachments/${a.id}/download`}>Download</a>
						<button class="ms-3 text-red-700 hover:underline" onclick={() => removeAtt(a)}>Delete</button>
					</TableBodyCell>
				</TableBodyRow>
			{:else}
				<TableBodyRow><TableBodyCell colspan={5} class="text-center text-gray-400">No attachments. Bundle any file with this SOP.</TableBodyCell></TableBodyRow>
			{/each}
		</TableBody>
	</Table>

	<h2 class="mt-8 mb-3 text-sm font-semibold text-gray-900">Change requests</h2>
	<Table hoverable shadow>
		<TableHead>
			<TableHeadCell>Change</TableHeadCell>
			<TableHeadCell>Class</TableHeadCell>
			<TableHeadCell>Training</TableHeadCell>
			<TableHeadCell>Status</TableHeadCell>
			<TableHeadCell>Opened</TableHeadCell>
		</TableHead>
		<TableBody>
			{#each cc?.changeRequests ?? [] as c (c.id)}
				<TableBodyRow>
					<TableBodyCell class="font-normal">{c.title}</TableBodyCell>
					<TableBodyCell class="font-mono">{c.classification}</TableBodyCell>
					<TableBodyCell>{c.trainingImpact ? 'yes' : 'no'}</TableBodyCell>
					<TableBodyCell><Badge color={badgeColor(c.status)}>{c.status}</Badge></TableBodyCell>
					<TableBodyCell class="font-mono text-gray-500">{dt(c.createdAt)}</TableBodyCell>
				</TableBodyRow>
			{:else}
				<TableBodyRow><TableBodyCell colspan={5} class="text-center text-gray-400">No change requests.</TableBodyCell></TableBodyRow>
			{/each}
		</TableBody>
	</Table>

	<h2 class="mt-8 mb-3 text-sm font-semibold text-gray-900">Approval tasks</h2>
	<Table hoverable shadow>
		<TableHead>
			<TableHeadCell>Stage</TableHeadCell>
			<TableHeadCell>Role</TableHeadCell>
			<TableHeadCell>Sign as</TableHeadCell>
			<TableHeadCell>Status</TableHeadCell>
		</TableHead>
		<TableBody>
			{#each cc?.tasks ?? [] as t (t.id)}
				<TableBodyRow>
					<TableBodyCell class="font-normal">{t.stage}</TableBodyCell>
					<TableBodyCell class="font-mono">{t.role}</TableBodyCell>
					<TableBodyCell><Badge color="indigo">{t.meaning}</Badge></TableBodyCell>
					<TableBodyCell><Badge color={badgeColor(t.status)}>{t.status}</Badge></TableBodyCell>
				</TableBodyRow>
			{:else}
				<TableBodyRow><TableBodyCell colspan={4} class="text-center text-gray-400">No approval tasks yet.</TableBodyCell></TableBodyRow>
			{/each}
		</TableBody>
	</Table>

	<h2 class="mt-8 mb-3 text-sm font-semibold text-gray-900">Version history</h2>
	<Table hoverable shadow>
		<TableHead>
			<TableHeadCell>Version</TableHeadCell>
			<TableHeadCell>Created</TableHeadCell>
			<TableHeadCell>Change request</TableHeadCell>
		</TableHead>
		<TableBody>
			{#each d.versions as v (v.id)}
				<TableBodyRow>
					<TableBodyCell><Badge color="gray">{v.label}</Badge></TableBodyCell>
					<TableBodyCell class="font-mono text-gray-500">{dt(v.createdAt)}</TableBodyCell>
					<TableBodyCell class="font-mono">{v.changeRequestId ?? '—'}</TableBodyCell>
				</TableBodyRow>
			{:else}
				<TableBodyRow><TableBodyCell colspan={3} class="text-center text-gray-400">No versions.</TableBodyCell></TableBodyRow>
			{/each}
		</TableBody>
	</Table>
{/if}

<Modal title="Open change request" bind:open={showCr} size="sm">
	<p class="-mt-2 text-sm text-gray-500">Submits the draft for review and starts the approval workflow.</p>
	<div>
		<Label class="mb-2">Reason for change</Label>
		<Select items={reasonItems} bind:value={crReason} />
	</div>
	<p class="-mt-1 text-sm text-gray-500">
		Routed as <strong>{REASONS[crReason].classification}</strong>{REASONS[crReason].trainingImpact
			? ' · requires re-training'
			: ''}.
	</p>
	<div>
		<Label class="mb-2">Comment <span class="text-gray-400">(optional)</span></Label>
		<Textarea rows={3} bind:value={crComment} placeholder="Add any detail for reviewers…" />
	</div>
	{#snippet footer()}
		<div class="flex w-full justify-end gap-2">
			<Button color="alternative" onclick={() => (showCr = false)}>Cancel</Button>
			<Button color="green" disabled={saving} onclick={openCr}>
				{saving ? 'Opening…' : 'Open & start workflow'}
			</Button>
		</div>
	{/snippet}
</Modal>
