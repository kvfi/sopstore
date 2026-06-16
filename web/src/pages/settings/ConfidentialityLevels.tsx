import { useState } from 'react';
import { Button, HTMLTable, Icon, InputGroup, Spinner } from '@blueprintjs/core';
import Panel from '../../components/Panel';
import { ApiError } from '../../lib/api';
import { toast } from '../../lib/toaster';
import {
	type ConfLevel,
	useConfidentialityLevels,
	useCreateConfLevel,
	useUpdateConfLevel,
	useDeleteConfLevel
} from '../../lib/queries';

const errMsg = (e: unknown, dup: string) =>
	e instanceof ApiError && e.status === 409 ? dup : (e as Error).message;

export default function ConfidentialityLevels() {
	const { data, isLoading } = useConfidentialityLevels();
	// Server order (rank ascending: least sensitive first).
	const serverLevels = [...(data ?? [])].sort((a, b) => a.rank - b.rank);
	const serverIds = serverLevels.map((l) => l.id);
	const byId = new Map(serverLevels.map((l) => [l.id, l]));

	const create = useCreateConfLevel();
	const update = useUpdateConfLevel();
	const del = useDeleteConfLevel();

	const [names, setNames] = useState<Record<string, string>>({});
	const [newName, setNewName] = useState('');
	// Pending (unsaved) drag order — null means "in sync with the server".
	const [working, setWorking] = useState<string[] | null>(null);
	const [dragId, setDragId] = useState<string | null>(null);
	const [savingOrder, setSavingOrder] = useState(false);

	const nameFor = (l: ConfLevel) => names[l.id] ?? l.name;

	// Use the pending order only while it still describes exactly the server's rows.
	const inSync = !working || working.length !== serverIds.length || working.some((id) => !byId.has(id));
	const orderIds = inSync ? serverIds : (working as string[]);
	const ordered = orderIds.map((id) => byId.get(id)).filter((l): l is ConfLevel => Boolean(l));
	const orderDirty = !inSync && orderIds.join() !== serverIds.join();

	async function add() {
		const name = newName.trim();
		if (!name) return;
		try {
			await create.mutateAsync({ name, rank: serverLevels.length });
			toast(`Added "${name}"`, 'success');
			setNewName('');
		} catch (e) {
			toast(errMsg(e, 'That level already exists.'), 'danger');
		}
	}

	async function saveName(l: ConfLevel) {
		const name = nameFor(l).trim();
		if (!name || name === l.name) return;
		try {
			await update.mutateAsync({ id: l.id, name, rank: l.rank });
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

	// Live preview: rearrange the working order as the dragged row passes over another.
	function dragOverRow(targetId: string) {
		if (!dragId || dragId === targetId) return;
		const base = [...orderIds];
		const from = base.indexOf(dragId);
		const to = base.indexOf(targetId);
		if (from < 0 || to < 0 || from === to) return;
		base.splice(from, 1);
		base.splice(to, 0, dragId);
		setWorking(base);
	}

	async function saveOrder() {
		const changed = ordered
			.map((l, rank) => ({ l, rank }))
			.filter(({ l, rank }) => rank !== l.rank);
		if (changed.length === 0) {
			setWorking(null);
			return;
		}
		setSavingOrder(true);
		try {
			await Promise.all(
				changed.map(({ l, rank }) => update.mutateAsync({ id: l.id, name: l.name, rank }))
			);
			setWorking(null);
			toast('Order saved', 'success');
		} catch (e) {
			toast((e as Error).message, 'danger');
		} finally {
			setSavingOrder(false);
		}
	}

	const orderActions = orderDirty ? (
		<>
			<Button small minimal onClick={() => setWorking(null)} text="Discard" />
			<Button
				small
				intent="primary"
				icon="floppy-disk"
				loading={savingOrder}
				onClick={saveOrder}
				text="Save order"
			/>
		</>
	) : (
		<span className="muted" style={{ fontSize: 12 }}>
			Drag rows to reorder
		</span>
	);

	return (
		<>
			<p className="page-sub">
				Confidentiality levels authors can assign to a document, ordered from least to most
				sensitive. <strong>Drag a row by its handle</strong> to reorder, then{' '}
				<strong>Save order</strong>. The chosen level is marked on the exported PDF. Deleting a level
				leaves affected documents unclassified.
			</p>

			{isLoading ? (
				<div className="center-screen">
					<Spinner />
				</div>
			) : (
				<div>
					<Panel title="Levels" actions={orderActions} flush>
						<HTMLTable className="data-table full">
							<thead>
								<tr>
									<th style={{ width: 44 }} aria-label="Reorder" />
									<th style={{ width: 64 }}>Order</th>
									<th>Level</th>
									<th className="right">Actions</th>
								</tr>
							</thead>
							<tbody>
								{ordered.length === 0 ? (
									<tr>
										<td colSpan={4} className="muted">No levels yet — add your first below.</td>
									</tr>
								) : (
									ordered.map((l, i) => {
										const name = nameFor(l);
										const dirty = name.trim().length > 0 && name !== l.name;
										return (
											<tr
												key={l.id}
												className={`dnd-row${dragId === l.id ? ' dragging' : ''}`}
												draggable
												onDragStart={() => setDragId(l.id)}
												onDragEnd={() => setDragId(null)}
												onDragOver={(e) => {
													e.preventDefault();
													dragOverRow(l.id);
												}}
												onDrop={(e) => {
													e.preventDefault();
													setDragId(null);
												}}
											>
												<td className="drag-handle" title="Drag to reorder">
													<Icon icon="drag-handle-vertical" />
												</td>
												<td className="mono muted">{i + 1}</td>
												<td>
													<InputGroup
														fill
														value={name}
														onChange={(e) =>
															setNames((m) => ({ ...m, [l.id]: e.currentTarget.value }))
														}
													/>
												</td>
												<td className="right nowrap">
													<Button small minimal intent="primary" disabled={!dirty} onClick={() => saveName(l)} text="Save" />{' '}
													<Button small minimal intent="danger" icon="trash" onClick={() => remove(l)} />
												</td>
											</tr>
										);
									})
								)}
							</tbody>
						</HTMLTable>

						<div
							className="panel-body"
							style={{ display: 'flex', gap: 8, alignItems: 'center', borderTop: '1px solid var(--border)' }}
						>
							<div style={{ flex: 1 }}>
								<InputGroup
									fill
									placeholder="New level (e.g. Confidential)"
									value={newName}
									onChange={(e) => setNewName(e.currentTarget.value)}
									onKeyDown={(e) => {
										if (e.key === 'Enter') add();
									}}
								/>
							</div>
							<Button intent="primary" icon="add" loading={create.isPending} disabled={!newName.trim()} onClick={add} text="Add level" />
						</div>
					</Panel>
				</div>
			)}
		</>
	);
}
