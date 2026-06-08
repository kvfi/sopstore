import { useState } from 'react';
import { Button, Card, HTMLTable, InputGroup, Spinner } from '@blueprintjs/core';
import { ApiError } from '../../lib/api';
import { toast } from '../../lib/toaster';
import {
	type PType,
	usePrereqTypes,
	useCreatePrereqType,
	useRenamePrereqType,
	useDeletePrereqType
} from '../../lib/queries';

function errMsg(e: unknown, dup: string): string {
	if (e instanceof ApiError && e.status === 409) return dup;
	return (e as Error).message;
}

export default function PrerequisiteTypes() {
	const { data, isLoading: loading } = usePrereqTypes();
	const types = data ?? [];
	const createType = useCreatePrereqType();
	const renameType = useRenamePrereqType();
	const deleteType = useDeletePrereqType();

	const [drafts, setDrafts] = useState<Record<string, string>>({});
	const [newName, setNewName] = useState('');

	async function add() {
		const name = newName.trim();
		if (!name) return;
		try {
			await createType.mutateAsync(name);
			toast(`Added "${name}"`, 'success');
			setNewName('');
		} catch (e) {
			toast(errMsg(e, 'That type already exists.'), 'danger');
		}
	}

	async function rename(t: PType) {
		const name = (drafts[t.id] ?? t.name).trim();
		if (!name || name === t.name) return;
		try {
			await renameType.mutateAsync({ id: t.id, name });
			toast('Renamed', 'success');
		} catch (e) {
			toast(errMsg(e, 'That name is already taken.'), 'danger');
		}
	}

	async function remove(t: PType) {
		if (!window.confirm(`Delete "${t.name}"? Existing procedures keep their saved type.`)) return;
		try {
			await deleteType.mutateAsync(t.id);
			toast(`Deleted "${t.name}"`, 'success');
		} catch (e) {
			toast((e as Error).message, 'danger');
		}
	}

	return (
		<>
			<p className="page-sub">
				Prerequisite types authors can assign to a procedure's prerequisites. Deleting a type
				leaves existing procedures untouched (they keep the saved type name).
			</p>

			{loading ? (
				<div className="center-screen">
					<Spinner />
				</div>
			) : (
				<Card style={{ maxWidth: 640 }}>
					<HTMLTable className="full">
						<thead>
							<tr>
								<th>Type</th>
								<th className="right">Actions</th>
							</tr>
						</thead>
						<tbody>
							{types.length === 0 ? (
								<tr>
									<td colSpan={2} className="muted">
										No types yet — add your first below.
									</td>
								</tr>
							) : (
								types.map((t) => {
									const draft = drafts[t.id] ?? t.name;
									const dirty = draft.trim().length > 0 && draft !== t.name;
									return (
										<tr key={t.id}>
											<td>
												<InputGroup
													fill
													value={draft}
													onChange={(e) => {
														const value = e.currentTarget.value;
														setDrafts((d) => ({ ...d, [t.id]: value }));
													}}
												/>
											</td>
											<td className="right nowrap">
												<Button small minimal intent="primary" disabled={!dirty} onClick={() => rename(t)} text="Save" />{' '}
												<Button small minimal intent="danger" icon="trash" onClick={() => remove(t)} />
											</td>
										</tr>
									);
								})
							)}
						</tbody>
					</HTMLTable>

					<div style={{ display: 'flex', gap: 8, marginTop: 14 }}>
						<div style={{ flex: 1 }}>
							<InputGroup
								fill
								placeholder="New prerequisite type (e.g. Equipment / tool)"
								value={newName}
								onChange={(e) => setNewName(e.currentTarget.value)}
								onKeyDown={(e) => {
									if (e.key === 'Enter') add();
								}}
							/>
						</div>
						<Button intent="primary" icon="add" loading={createType.isPending} disabled={!newName.trim()} onClick={add} text="Add type" />
					</div>
				</Card>
			)}
		</>
	);
}
