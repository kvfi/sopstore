import { useEffect, useState } from 'react';
import {
	Button,
	Dialog,
	DialogBody,
	DialogFooter,
	FormGroup,
	HTMLSelect,
	HTMLTable,
	InputGroup,
	Spinner,
	Tag,
	TextArea
} from '@blueprintjs/core';
import { api } from '../lib/api';
import { toast } from '../lib/toaster';
import { dt } from '../lib/ui';
import {
	type ScriptRow,
	useScripts,
	useScriptVersions,
	useCreateScript,
	useUpdateScriptMeta,
	useSaveScriptContent,
	useRestoreScriptVersion,
	useDeleteScript
} from '../lib/queries';

const LANGUAGES = ['bash', 'python', 'sql', 'javascript', 'powershell', 'text'];

/** The edit dialog for a single script: metadata, content (saves a new version), and history. */
function EditScript({ script, onClose }: { script: ScriptRow; onClose: () => void }) {
	const versionsQ = useScriptVersions(script.id);
	const updateMeta = useUpdateScriptMeta();
	const saveContent = useSaveScriptContent(script.id);
	const restore = useRestoreScriptVersion(script.id);

	const [name, setName] = useState(script.name);
	const [language, setLanguage] = useState(script.language);
	const [description, setDescription] = useState(script.description ?? '');
	const [content, setContent] = useState<string | null>(null); // null = loading
	const [note, setNote] = useState('');

	// Lazy-load the content of a version into the editor.
	async function load(no: number) {
		try {
			const v = await api.get<{ content: string }>(`/api/v1/scripts/${script.id}/versions/${no}`);
			setContent(v.content);
		} catch (e) {
			toast((e as Error).message, 'danger');
			setContent('');
		}
	}
	useEffect(() => {
		void load(script.currentVersion);
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, []);

	async function saveDetails() {
		try {
			await updateMeta.mutateAsync({ id: script.id, name: name.trim(), language, description: description.trim() });
			toast('Details saved', 'success');
		} catch (e) {
			toast((e as Error).message, 'danger');
		}
	}
	async function commit() {
		try {
			await saveContent.mutateAsync({ content: content ?? '', note: note.trim() });
			setNote('');
			toast('New version saved', 'success');
		} catch (e) {
			toast((e as Error).message, 'danger');
		}
	}

	return (
		<Dialog isOpen onClose={onClose} title={`Edit ${script.name}`} icon="code" style={{ width: 760 }}>
			<DialogBody>
				<div style={{ display: 'flex', gap: 8 }}>
					<FormGroup label="Name" style={{ flex: 1 }}>
						<InputGroup fill value={name} onChange={(e) => setName(e.currentTarget.value)} />
					</FormGroup>
					<FormGroup label="Language">
						<HTMLSelect value={language} options={LANGUAGES} onChange={(e) => setLanguage(e.currentTarget.value)} />
					</FormGroup>
					<Button style={{ marginTop: 24 }} onClick={saveDetails} loading={updateMeta.isPending} text="Save details" />
				</div>
				<FormGroup label="Description">
					<InputGroup fill value={description} onChange={(e) => setDescription(e.currentTarget.value)} />
				</FormGroup>

				<FormGroup label="Content" labelInfo="(saving creates a new version)">
					{content === null ? (
						<Spinner size={20} />
					) : (
						<TextArea
							fill
							value={content}
							onChange={(e) => setContent(e.currentTarget.value)}
							style={{ fontFamily: 'monospace', minHeight: 220, whiteSpace: 'pre' }}
						/>
					)}
				</FormGroup>
				<div style={{ display: 'flex', gap: 8, alignItems: 'flex-end' }}>
					<FormGroup label="Change note (optional)" style={{ flex: 1, marginBottom: 0 }}>
						<InputGroup fill value={note} onChange={(e) => setNote(e.currentTarget.value)} placeholder="What changed" />
					</FormGroup>
					<Button intent="primary" icon="floppy-disk" onClick={commit} loading={saveContent.isPending} text="Save new version" />
				</div>

				<div className="section-h" style={{ marginTop: 18 }}>Version history</div>
				<HTMLTable striped className="full">
					<thead>
						<tr>
							<th>Version</th>
							<th>Note</th>
							<th>By</th>
							<th>When</th>
							<th className="right">Actions</th>
						</tr>
					</thead>
					<tbody>
						{(versionsQ.data ?? []).map((v) => (
							<tr key={v.versionNo}>
								<td>
									<Tag minimal intent={v.versionNo === script.currentVersion ? 'primary' : 'none'}>
										v{v.versionNo}
									</Tag>
								</td>
								<td>{v.note ?? '—'}</td>
								<td className="muted">{v.createdBy ?? '—'}</td>
								<td className="mono muted">{dt(v.createdAt)}</td>
								<td className="right nowrap">
									<Button small minimal text="View" onClick={() => load(v.versionNo)} />{' '}
									<Button
										small
										minimal
										intent="warning"
										text="Restore"
										onClick={() => restore.mutateAsync(v.versionNo).then(() => load(v.versionNo + 0))}
									/>
								</td>
							</tr>
						))}
					</tbody>
				</HTMLTable>
			</DialogBody>
			<DialogFooter actions={<Button onClick={onClose} text="Close" />} />
		</Dialog>
	);
}

export default function Scripts() {
	const { data, isLoading } = useScripts();
	const scripts = data ?? [];
	const create = useCreateScript();
	const del = useDeleteScript();

	const [editing, setEditing] = useState<ScriptRow | null>(null);
	const [showNew, setShowNew] = useState(false);
	const [neu, setNeu] = useState({ name: '', language: 'bash', description: '', content: '' });

	async function add() {
		if (!neu.name.trim()) return;
		try {
			await create.mutateAsync({ ...neu, name: neu.name.trim() });
			toast(`Created "${neu.name.trim()}"`, 'success');
			setShowNew(false);
			setNeu({ name: '', language: 'bash', description: '', content: '' });
		} catch (e) {
			toast((e as Error).message, 'danger');
		}
	}
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
				<Button intent="primary" icon="add" onClick={() => setShowNew(true)} text="New script" />
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
								<tr key={s.id} className="click" onClick={() => setEditing(s)}>
									<td style={{ fontWeight: 500 }}>{s.name}</td>
									<td className="mono">{s.language}</td>
									<td><Tag minimal>v{s.currentVersion}</Tag></td>
									<td className="mono muted">{dt(s.updatedAt)}</td>
									<td className="right nowrap" onClick={(e) => e.stopPropagation()}>
										<Button small minimal text="Edit" onClick={() => setEditing(s)} />{' '}
										<Button small minimal intent="danger" icon="trash" onClick={() => remove(s)} />
									</td>
								</tr>
							))
						)}
					</tbody>
				</HTMLTable>
			)}

			{editing && <EditScript script={editing} onClose={() => setEditing(null)} />}

			<Dialog isOpen={showNew} onClose={() => setShowNew(false)} title="New script" icon="code">
				<DialogBody>
					<div style={{ display: 'flex', gap: 8 }}>
						<FormGroup label="Name" style={{ flex: 1 }}>
							<InputGroup fill value={neu.name} onChange={(e) => setNeu({ ...neu, name: e.currentTarget.value })} placeholder="e.g. deploy.sh" />
						</FormGroup>
						<FormGroup label="Language">
							<HTMLSelect value={neu.language} options={LANGUAGES} onChange={(e) => setNeu({ ...neu, language: e.currentTarget.value })} />
						</FormGroup>
					</div>
					<FormGroup label="Description">
						<InputGroup fill value={neu.description} onChange={(e) => setNeu({ ...neu, description: e.currentTarget.value })} />
					</FormGroup>
					<FormGroup label="Content">
						<TextArea
							fill
							value={neu.content}
							onChange={(e) => setNeu({ ...neu, content: e.currentTarget.value })}
							style={{ fontFamily: 'monospace', minHeight: 180, whiteSpace: 'pre' }}
						/>
					</FormGroup>
				</DialogBody>
				<DialogFooter
					actions={
						<>
							<Button onClick={() => setShowNew(false)} text="Cancel" />
							<Button intent="primary" onClick={add} loading={create.isPending} disabled={!neu.name.trim()} text="Create" />
						</>
					}
				/>
			</Dialog>
		</>
	);
}
