import { useState } from 'react';
import { Link } from 'react-router-dom';
import {
	Button,
	Callout,
	Card,
	HTMLSelect,
	HTMLTable,
	InputGroup,
	Spinner
} from '@blueprintjs/core';
import { ApiError } from '../../lib/api';
import { toast } from '../../lib/toaster';
import {
	type LibPrereq as Item,
	usePrereqLib,
	usePrereqTypes,
	useCreatePrereq,
	useUpdatePrereq,
	useDeletePrereq
} from '../../lib/queries';

type Draft = { type: string; text: string };

function typeOptions(types: string[], current: string) {
	const opts = Array.from(new Set([current, ...types].filter(Boolean)));
	return [{ value: '', label: '— type —' }, ...opts.map((o) => ({ value: o, label: o }))];
}

export default function Prerequisites() {
	const libQ = usePrereqLib();
	const typesQ = usePrereqTypes();
	const items = libQ.data ?? [];
	const types = (typesQ.data ?? []).map((x) => x.name);
	const loading = libQ.isLoading || typesQ.isLoading;

	const createPrereq = useCreatePrereq();
	const updatePrereq = useUpdatePrereq();
	const deletePrereq = useDeletePrereq();

	const [drafts, setDrafts] = useState<Record<string, Draft>>({});
	const [newType, setNewType] = useState('');
	const [newText, setNewText] = useState('');
	// Fall back to the first available type until the user picks one.
	const newTypeValue = newType || types[0] || '';

	const errMsg = (e: unknown, dup: string) =>
		e instanceof ApiError && e.status === 409 ? dup : (e as Error).message;

	async function add() {
		const text = newText.trim();
		if (!text) return;
		try {
			await createPrereq.mutateAsync({ type: newTypeValue, text });
			toast('Added', 'success');
			setNewText('');
		} catch (e) {
			toast(errMsg(e, 'That prerequisite already exists.'), 'danger');
		}
	}

	async function save(it: Item) {
		const d = drafts[it.id];
		if (!d || !d.text.trim()) return;
		try {
			await updatePrereq.mutateAsync({ id: it.id, type: d.type, text: d.text.trim() });
			toast('Saved', 'success');
		} catch (e) {
			toast(errMsg(e, 'That prerequisite already exists.'), 'danger');
		}
	}

	async function remove(it: Item) {
		if (!window.confirm(`Delete "${it.text}"? Existing procedures keep their saved prerequisites.`))
			return;
		try {
			await deletePrereq.mutateAsync(it.id);
			toast('Deleted', 'success');
		} catch (e) {
			toast((e as Error).message, 'danger');
		}
	}

	if (loading) {
		return (
			<div className="center-screen">
				<Spinner />
			</div>
		);
	}

	return (
		<>
			<p className="page-sub">
				A reusable library of prerequisites authors can attach to procedures. Each pairs a type with
				its text. Deleting one leaves existing procedures untouched.
			</p>

			{types.length === 0 && (
				<Callout intent="primary" icon="info-sign" style={{ marginBottom: 12, maxWidth: 720 }}>
					No prerequisite types yet — add some under{' '}
					<Link to="/settings/configuration/prerequisite-types">Prerequisite types</Link> first.
				</Callout>
			)}

			<Card style={{ maxWidth: 760 }}>
				<HTMLTable className="full">
					<thead>
						<tr>
							<th style={{ width: 200 }}>Type</th>
							<th>Prerequisite</th>
							<th className="right">Actions</th>
						</tr>
					</thead>
					<tbody>
						{items.length === 0 ? (
							<tr>
								<td colSpan={3} className="muted">
									No prerequisites yet — add your first below.
								</td>
							</tr>
						) : (
							items.map((it) => {
								const d = drafts[it.id] ?? { type: it.type, text: it.text };
								const dirty = d.text.trim().length > 0 && (d.text !== it.text || d.type !== it.type);
								return (
									<tr key={it.id}>
										<td>
											<HTMLSelect
												fill
												value={d.type}
												options={typeOptions(types, it.type)}
												onChange={(e) => {
													const type = e.currentTarget.value;
													setDrafts((m) => ({ ...m, [it.id]: { ...d, type } }));
												}}
											/>
										</td>
										<td>
											<InputGroup
												fill
												value={d.text}
												onChange={(e) => {
													const text = e.currentTarget.value;
													setDrafts((m) => ({ ...m, [it.id]: { ...d, text } }));
												}}
											/>
										</td>
										<td className="right nowrap">
											<Button small minimal intent="primary" disabled={!dirty} onClick={() => save(it)} text="Save" />{' '}
											<Button small minimal intent="danger" icon="trash" onClick={() => remove(it)} />
										</td>
									</tr>
								);
							})
						)}
					</tbody>
				</HTMLTable>

				<div style={{ display: 'flex', gap: 8, marginTop: 14, alignItems: 'center' }}>
					<HTMLSelect
						value={newTypeValue}
						options={typeOptions(types, newTypeValue)}
						onChange={(e) => setNewType(e.currentTarget.value)}
					/>
					<div style={{ flex: 1 }}>
						<InputGroup
							fill
							placeholder="New prerequisite (e.g. Calibrated torque wrench available)"
							value={newText}
							onChange={(e) => setNewText(e.currentTarget.value)}
							onKeyDown={(e) => {
								if (e.key === 'Enter') add();
							}}
						/>
					</div>
					<Button intent="primary" icon="add" loading={createPrereq.isPending} disabled={!newText.trim()} onClick={add} text="Add" />
				</div>
			</Card>
		</>
	);
}
