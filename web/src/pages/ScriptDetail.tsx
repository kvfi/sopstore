import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
	Button,
	Callout,
	Card,
	FormGroup,
	HTMLSelect,
	HTMLTable,
	InputGroup,
	Spinner,
	Tag
} from '@blueprintjs/core';
import CodeEditor from '../components/CodeEditor';
import { api } from '../lib/api';
import { toast } from '../lib/toaster';
import { dt } from '../lib/ui';
import {
	useScript,
	useScriptVersions,
	useCreateScript,
	useUpdateScriptMeta,
	useSaveScriptContent,
	useRestoreScriptVersion,
	useDeleteScript
} from '../lib/queries';

const LANGUAGES = ['bash', 'python', 'sql', 'javascript', 'powershell', 'text'];

/** Routes /scripts/new (create) and /scripts/:id (edit) to a full-page, non-modal code editor. */
export default function ScriptDetail() {
	const { id } = useParams();
	return id ? <EditScriptPage id={id} /> : <NewScriptPage />;
}

/** A back-to-list link shown at the top of both the create and edit pages. */
function BackLink() {
	const nav = useNavigate();
	return (
		<Button minimal small icon="arrow-left" text="All scripts" onClick={() => nav('/scripts')} style={{ marginBottom: 10 }} />
	);
}

/** Create page: metadata + an initial content editor; on save it opens the new script's editor. */
function NewScriptPage() {
	const nav = useNavigate();
	const create = useCreateScript();
	const [name, setName] = useState('');
	const [language, setLanguage] = useState('bash');
	const [description, setDescription] = useState('');
	const [content, setContent] = useState('');

	async function add() {
		if (!name.trim()) return;
		try {
			const s = await create.mutateAsync({ name: name.trim(), language, description: description.trim(), content });
			toast(`Created "${name.trim()}"`, 'success');
			nav(`/scripts/${s.id}`);
		} catch (e) {
			toast((e as Error).message, 'danger');
		}
	}

	return (
		<>
			<BackLink />
			<Card>
				<div style={{ display: 'flex', gap: 8, alignItems: 'flex-end' }}>
					<FormGroup label="Name" style={{ flex: 1, marginBottom: 8 }}>
						<InputGroup fill value={name} placeholder="e.g. deploy.sh" onChange={(e) => setName(e.currentTarget.value)} />
					</FormGroup>
					<FormGroup label="Language" style={{ marginBottom: 8 }}>
						<HTMLSelect value={language} options={LANGUAGES} onChange={(e) => setLanguage(e.currentTarget.value)} />
					</FormGroup>
				</div>
				<FormGroup label="Description">
					<InputGroup fill value={description} onChange={(e) => setDescription(e.currentTarget.value)} />
				</FormGroup>
				<FormGroup label="Content">
					<CodeEditor value={content} language={language} onChange={setContent} minHeight="50vh" />
				</FormGroup>
				<div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
					<Button text="Cancel" onClick={() => nav('/scripts')} />
					<Button intent="primary" icon="floppy-disk" text="Create script" loading={create.isPending} disabled={!name.trim()} onClick={add} />
				</div>
			</Card>
		</>
	);
}

/** Edit page: live code editor on the left, version history on the right. */
function EditScriptPage({ id }: { id: string }) {
	const nav = useNavigate();
	const scriptQ = useScript(id);
	const versionsQ = useScriptVersions(id);
	const updateMeta = useUpdateScriptMeta();
	const saveContent = useSaveScriptContent(id);
	const restore = useRestoreScriptVersion(id);
	const del = useDeleteScript();

	const script = scriptQ.data;
	const current = script?.currentVersion ?? 0;

	const [name, setName] = useState('');
	const [language, setLanguage] = useState('text');
	const [description, setDescription] = useState('');
	const [metaSeeded, setMetaSeeded] = useState(false);

	const [content, setContent] = useState<string | null>(null); // null = not loaded yet
	const [loaded, setLoaded] = useState(''); // the content as loaded (to detect edits)
	const [viewing, setViewing] = useState<number | null>(null); // version currently in the editor
	const [note, setNote] = useState('');

	// Seed the metadata fields once the script loads (don't clobber edits on later refetches).
	useEffect(() => {
		if (script && !metaSeeded) {
			setName(script.name);
			setLanguage(script.language);
			setDescription(script.description ?? '');
			setMetaSeeded(true);
		}
	}, [script, metaSeeded]);

	// Load the current version's content once, when the script first arrives.
	useEffect(() => {
		if (script && content === null && viewing === null) {
			void loadVersion(current);
		}
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [script]);

	async function loadVersion(no: number) {
		try {
			const v = await api.get<{ versionNo: number; content: string }>(`/api/v1/scripts/${id}/versions/${no}`);
			setContent(v.content);
			setLoaded(v.content);
			setViewing(no);
		} catch (e) {
			toast((e as Error).message, 'danger');
			setContent('');
			setLoaded('');
			setViewing(no);
		}
	}

	const metaDirty =
		!!script &&
		(name.trim() !== script.name ||
			language !== script.language ||
			description.trim() !== (script.description ?? ''));
	const contentDirty = content !== null && content !== loaded;

	async function saveDetails() {
		if (!name.trim()) return;
		try {
			await updateMeta.mutateAsync({ id, name: name.trim(), language, description: description.trim() });
			toast('Details saved', 'success');
		} catch (e) {
			toast((e as Error).message, 'danger');
		}
	}

	async function saveNewVersion() {
		try {
			const r = await saveContent.mutateAsync({ content: content ?? '', note: note.trim() });
			setLoaded(content ?? '');
			setViewing(r.versionNo);
			setNote('');
			toast(`Saved v${r.versionNo}`, 'success');
		} catch (e) {
			toast((e as Error).message, 'danger');
		}
	}

	async function restoreVersion(no: number) {
		if (!window.confirm(`Restore v${no}? This creates a new current version from its content.`)) return;
		try {
			const r = await restore.mutateAsync(no);
			await loadVersion(r.versionNo);
			toast(`Restored v${no} as v${r.versionNo}`, 'success');
		} catch (e) {
			toast((e as Error).message, 'danger');
		}
	}

	async function remove() {
		if (!script) return;
		if (!window.confirm(`Delete "${script.name}" and all its versions? This cannot be undone.`)) return;
		try {
			await del.mutateAsync(id);
			toast('Deleted', 'success');
			nav('/scripts');
		} catch (e) {
			toast((e as Error).message, 'danger');
		}
	}

	if (scriptQ.isLoading) {
		return (
			<div className="center-screen">
				<Spinner />
			</div>
		);
	}
	if (!script) {
		return (
			<>
				<BackLink />
				<Callout intent="warning" icon="error">Script not found.</Callout>
			</>
		);
	}

	const versions = versionsQ.data ?? [];

	return (
		<>
			<BackLink />
			<div className="toolbar">
				<div style={{ display: 'flex', gap: 8, alignItems: 'flex-end', flexWrap: 'wrap', flex: 1 }}>
					<FormGroup label="Name" style={{ marginBottom: 0, minWidth: 240, flex: 1 }}>
						<InputGroup fill value={name} onChange={(e) => setName(e.currentTarget.value)} />
					</FormGroup>
					<FormGroup label="Language" style={{ marginBottom: 0 }}>
						<HTMLSelect value={language} options={LANGUAGES} onChange={(e) => setLanguage(e.currentTarget.value)} />
					</FormGroup>
					<Button text="Save details" icon="floppy-disk" disabled={!metaDirty || !name.trim()} loading={updateMeta.isPending} onClick={saveDetails} />
					<Button intent="danger" minimal icon="trash" text="Delete" onClick={remove} />
				</div>
			</div>
			<FormGroup label="Description">
				<InputGroup fill value={description} onChange={(e) => setDescription(e.currentTarget.value)} />
			</FormGroup>

			<div style={{ display: 'flex', gap: 16, alignItems: 'flex-start' }}>
				<div style={{ flex: 1, minWidth: 0 }}>
					<div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 6 }}>
						<span className="section-h" style={{ margin: 0 }}>Content</span>
						{viewing !== null && viewing !== current && (
							<Tag intent="warning" minimal>Viewing v{viewing} (not current)</Tag>
						)}
						{contentDirty && <Tag intent="primary" minimal>Unsaved changes</Tag>}
					</div>
					{content === null ? (
						<Spinner size={24} />
					) : (
						<CodeEditor value={content} language={language} onChange={setContent} minHeight="50vh" />
					)}
					<div style={{ display: 'flex', gap: 8, alignItems: 'flex-end', marginTop: 10 }}>
						<FormGroup label="Change note (optional)" style={{ flex: 1, marginBottom: 0 }}>
							<InputGroup fill value={note} placeholder="What changed" onChange={(e) => setNote(e.currentTarget.value)} />
						</FormGroup>
						<Button intent="primary" icon="git-commit" text="Save new version" disabled={!contentDirty} loading={saveContent.isPending} onClick={saveNewVersion} />
					</div>
				</div>

				<Card style={{ width: 300, flexShrink: 0, padding: 0 }}>
					<div className="section-h" style={{ padding: '10px 12px', margin: 0 }}>Version history</div>
					<HTMLTable className="full" style={{ fontSize: 13 }}>
						<tbody>
							{versions.length === 0 ? (
								<tr><td className="muted" style={{ padding: 12 }}>No versions yet.</td></tr>
							) : (
								versions.map((v) => (
									<tr key={v.versionNo} style={v.versionNo === viewing ? { background: 'var(--bp-bg-hover, #f0f3f7)' } : undefined}>
										<td>
											<Tag minimal intent={v.versionNo === current ? 'primary' : 'none'}>v{v.versionNo}</Tag>
											<div className="muted" style={{ fontSize: 12, marginTop: 2 }}>{v.note ?? '—'}</div>
											<div className="muted mono" style={{ fontSize: 11 }}>{dt(v.createdAt)}{v.createdBy ? ` · ${v.createdBy}` : ''}</div>
										</td>
										<td className="right nowrap">
											<Button small minimal text="View" onClick={() => loadVersion(v.versionNo)} />
											{v.versionNo !== current && (
												<Button small minimal intent="warning" text="Restore" onClick={() => restoreVersion(v.versionNo)} />
											)}
										</td>
									</tr>
								))
							)}
						</tbody>
					</HTMLTable>
				</Card>
			</div>
		</>
	);
}
