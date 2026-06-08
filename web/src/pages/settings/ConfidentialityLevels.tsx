import { useState } from 'react';
import { Button, Card, HTMLTable, InputGroup, Spinner } from '@blueprintjs/core';
import { ApiError } from '../../lib/api';
import { toast } from '../../lib/toaster';
import {
	type ConfLevel,
	useConfidentialityLevels,
	useCreateConfLevel,
	useUpdateConfLevel,
	useDeleteConfLevel
} from '../../lib/queries';

type Draft = { name: string; rank: number };

const errMsg = (e: unknown, dup: string) =>
	e instanceof ApiError && e.status === 409 ? dup : (e as Error).message;

export default function ConfidentialityLevels() {
	const { data, isLoading } = useConfidentialityLevels();
	const levels = data ?? [];
	const create = useCreateConfLevel();
	const update = useUpdateConfLevel();
	const del = useDeleteConfLevel();

	const [drafts, setDrafts] = useState<Record<string, Draft>>({});
	const [neu, setNeu] = useState<Draft>({ name: '', rank: levels.length });

	const draftFor = (l: ConfLevel): Draft => drafts[l.id] ?? { name: l.name, rank: l.rank };
	const setDraft = (id: string, patch: Partial<Draft>, base: Draft) =>
		setDrafts((d) => ({ ...d, [id]: { ...base, ...patch } }));

	async function add() {
		const name = neu.name.trim();
		if (!name) return;
		try {
			await create.mutateAsync({ name, rank: neu.rank });
			toast(`Added "${name}"`, 'success');
			setNeu({ name: '', rank: levels.length + 1 });
		} catch (e) {
			toast(errMsg(e, 'That level already exists.'), 'danger');
		}
	}

	async function save(l: ConfLevel) {
		const d = draftFor(l);
		if (!d.name.trim()) return;
		try {
			await update.mutateAsync({ id: l.id, name: d.name.trim(), rank: d.rank });
			toast('Saved', 'success');
		} catch (e) {
			toast(errMsg(e, 'That level already exists.'), 'danger');
		}
	}

	async function remove(l: ConfLevel) {
		if (!window.confirm(`Delete "${l.name}"? Documents using it become unclassified.`)) return;
		try {
			await del.mutateAsync(l.id);
			toast(`Deleted "${l.name}"`, 'success');
		} catch (e) {
			toast((e as Error).message, 'danger');
		}
	}

	return (
		<>
			<p className="page-sub">
				Confidentiality levels authors can assign to a document, ordered by rank (lower is less
				sensitive). The chosen level is marked on the exported PDF. Deleting a level leaves affected
				documents unclassified.
			</p>

			{isLoading ? (
				<div className="center-screen">
					<Spinner />
				</div>
			) : (
				<Card style={{ maxWidth: 560 }}>
					<HTMLTable className="full">
						<thead>
							<tr>
								<th style={{ width: 80 }}>Rank</th>
								<th>Level</th>
								<th className="right">Actions</th>
							</tr>
						</thead>
						<tbody>
							{levels.length === 0 ? (
								<tr>
									<td colSpan={3} className="muted">No levels yet — add your first below.</td>
								</tr>
							) : (
								levels.map((l) => {
									const d = draftFor(l);
									const dirty = d.name.trim().length > 0 && (d.name !== l.name || d.rank !== l.rank);
									return (
										<tr key={l.id}>
											<td>
												<input
													type="number"
													value={d.rank}
													onChange={(e) => setDraft(l.id, { rank: Number(e.currentTarget.value) }, d)}
													className="bp6-input"
													style={{ width: 60 }}
												/>
											</td>
											<td>
												<InputGroup fill value={d.name} onChange={(e) => setDraft(l.id, { name: e.currentTarget.value }, d)} />
											</td>
											<td className="right nowrap">
												<Button small minimal intent="primary" disabled={!dirty} onClick={() => save(l)} text="Save" />{' '}
												<Button small minimal intent="danger" icon="trash" onClick={() => remove(l)} />
											</td>
										</tr>
									);
								})
							)}
						</tbody>
					</HTMLTable>

					<div style={{ display: 'flex', gap: 8, marginTop: 14, alignItems: 'center' }}>
						<input
							type="number"
							value={neu.rank}
							onChange={(e) => setNeu({ ...neu, rank: Number(e.currentTarget.value) })}
							className="bp6-input"
							style={{ width: 60 }}
							aria-label="Rank"
							title="Rank (lower is less sensitive)"
						/>
						<div style={{ flex: 1 }}>
							<InputGroup
								fill
								placeholder="New level (e.g. Confidential)"
								value={neu.name}
								onChange={(e) => setNeu({ ...neu, name: e.currentTarget.value })}
								onKeyDown={(e) => {
									if (e.key === 'Enter') add();
								}}
							/>
						</div>
						<Button intent="primary" icon="add" loading={create.isPending} disabled={!neu.name.trim()} onClick={add} text="Add level" />
					</div>
				</Card>
			)}
		</>
	);
}
