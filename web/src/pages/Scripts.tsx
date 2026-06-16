import { Button, HTMLTable, Spinner, Tag } from '@blueprintjs/core';
import { useNavigate } from 'react-router-dom';
import { toast } from '../lib/toaster';
import { dt } from '../lib/ui';
import { type ScriptRow, useScripts, useDeleteScript } from '../lib/queries';

export default function Scripts() {
	const nav = useNavigate();
	const { data, isLoading } = useScripts();
	const scripts = data ?? [];
	const del = useDeleteScript();

	async function remove(s: ScriptRow) {
		if (!window.confirm(`Delete "${s.name}" and all its versions? This cannot be undone.`)) return;
		try {
			await del.mutateAsync(s.id);
			toast('Deleted', 'success');
		} catch (e) {
			toast((e as Error).message, 'danger');
		}
	}

	return (
		<>
			<div className="toolbar">
				<p className="page-sub" style={{ margin: 0 }}>
					Versioned repository of scripts (stored in the standalone script-service). Procedures link a
					script + pinned version in a <strong>Run script</strong> step.
				</p>
				<div className="grow" />
				<Button intent="primary" icon="add" onClick={() => nav('/scripts/new')} text="New script" />
			</div>

			{isLoading ? (
				<div className="center-screen">
					<Spinner />
				</div>
			) : (
				<HTMLTable striped interactive className="full">
					<thead>
						<tr>
							<th>Name</th>
							<th>Language</th>
							<th>Version</th>
							<th>Updated</th>
							<th className="right">Actions</th>
						</tr>
					</thead>
					<tbody>
						{scripts.length === 0 ? (
							<tr>
								<td colSpan={5} className="muted">No scripts yet — add your first.</td>
							</tr>
						) : (
							scripts.map((s) => (
								<tr key={s.id} className="click" onClick={() => nav(`/scripts/${s.id}`)}>
									<td style={{ fontWeight: 500 }}>{s.name}</td>
									<td className="mono">{s.language}</td>
									<td><Tag minimal>v{s.currentVersion}</Tag></td>
									<td className="mono muted">{dt(s.updatedAt)}</td>
									<td className="right nowrap" onClick={(e) => e.stopPropagation()}>
										<Button small minimal text="Open" onClick={() => nav(`/scripts/${s.id}`)} />{' '}
										<Button small minimal intent="danger" icon="trash" onClick={() => remove(s)} />
									</td>
								</tr>
							))
						)}
					</tbody>
				</HTMLTable>
			)}
		</>
	);
}
