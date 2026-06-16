import { useEffect, useState } from 'react';
import { Button, Callout, Card, Code, FormGroup, InputGroup, Spinner } from '@blueprintjs/core';
import { toast } from '../../lib/toaster';
import {
	type ScriptBundleSettings as Settings,
	useScriptBundleSettings,
	useSaveScriptBundleSettings
} from '../../lib/queries';

// A representative RUN_SCRIPT step + document, used to render the live preview below.
const SAMPLE = { code: 'k3', name: 'Load Dataset', version: 4, language: 'sql', document: 'SOP-001' };

const EXT: Record<string, string> = {
	sql: 'sql', python: 'py', py: 'py', javascript: 'js', js: 'js', typescript: 'ts', ts: 'ts',
	bash: 'sh', sh: 'sh', shell: 'sh', zsh: 'sh', powershell: 'ps1', ps1: 'ps1', java: 'java',
	kotlin: 'kt', kt: 'kt', go: 'go', golang: 'go', ruby: 'rb', rb: 'rb', rust: 'rs', rs: 'rs',
	csharp: 'cs', cs: 'cs', 'c#': 'cs', c: 'c', cpp: 'cpp', 'c++': 'cpp', yaml: 'yml', yml: 'yml',
	json: 'json', xml: 'xml', html: 'html', css: 'css', r: 'r', php: 'php', perl: 'pl', pl: 'pl'
};

const slug = (s: string) =>
	s.trim().toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/(^-+|-+$)/g, '').slice(0, 60);

// Mirror of the backend ScriptBundleNaming token engine, so the preview matches the export exactly.
function tidy(s: string) {
	return s
		.replace(/ /g, '_')
		.replace(/[_-]{2,}/g, '_')
		.replace(/[_-]+\./g, '.')
		.replace(/\.[_-]+/g, '.')
		.replace(/(^[_-]+|[_-]+$)/g, '');
}

function fileName(pattern: string) {
	const ext = EXT[SAMPLE.language] ?? 'txt';
	const hasExt = pattern.includes('{ext}');
	let s = pattern
		.replace(/{code}/g, slug(SAMPLE.code))
		.replace(/{name}/g, slug(SAMPLE.name))
		.replace(/{version}/g, SAMPLE.version > 0 ? `v${SAMPLE.version}` : '')
		.replace(/{ext}/g, 'EXTSENTINEL');
	s = tidy(s);
	if (s === '' || s.startsWith('.') || s.startsWith('EXTSENTINEL')) s = `script${s}`;
	s = s.replace('EXTSENTINEL', ext);
	return hasExt ? s : `${s}.${ext}`;
}

function folder(f: string) {
	const t = f.trim().replace(/^\/+/, '');
	if (t === '') return '';
	return t.endsWith('/') ? t : `${t}/`;
}

function scriptPath(s: Settings) {
	return folder(s.bundleFolder) + fileName(s.filenamePattern);
}

function scriptHref(s: Settings) {
	const path = scriptPath(s);
	const base = (s.linkBaseUrl ?? '').trim();
	return base === '' ? path : `${base.replace(/\/+$/, '')}/${path}`;
}

function bundleFileName(s: Settings) {
	const name = s.bundleName.replace(/{document}/g, SAMPLE.document).replace(/[^A-Za-z0-9._-]/g, '_').replace(/^[_-]+/, '');
	return name === '' ? 'bundle.zip' : name;
}

const DEFAULTS: Settings = {
	bundleFolder: 'scripts/',
	filenamePattern: '{code}_{name}_{version}.{ext}',
	bundleName: '{document}-bundle.zip',
	linkBaseUrl: ''
};

export default function ScriptBundleSettingsPage() {
	const { data, isLoading } = useScriptBundleSettings();
	const saveMut = useSaveScriptBundleSettings();
	const [draft, setDraft] = useState<Settings | null>(null);

	useEffect(() => {
		if (data && !draft) setDraft(data);
	}, [data, draft]);

	if (isLoading || !draft) {
		return (
			<div className="center-screen">
				<Spinner />
			</div>
		);
	}

	const set = (patch: Partial<Settings>) => setDraft((d) => ({ ...(d as Settings), ...patch }));
	const dirty = data ? JSON.stringify(draft) !== JSON.stringify(data) : true;

	async function save() {
		try {
			await saveMut.mutateAsync(draft as Settings);
			toast('Saved', 'success');
		} catch (e) {
			toast((e as Error).message, 'danger');
		}
	}

	return (
		<>
			<p className="page-sub">
				Controls how each <strong>Run script</strong> step's script is named and linked inside the
				exported SOP bundle (the ZIP) and the PDF. The PDF links each script to its file in the
				bundle, so it opens with one click once the bundle is unzipped. Leave a field blank to use
				its default.
			</p>

			<Card>
				<FormGroup
					label="Bundle folder"
					helperText="Folder the script files are placed in inside the ZIP."
				>
					<InputGroup
						value={draft.bundleFolder}
						placeholder={DEFAULTS.bundleFolder}
						onChange={(e) => set({ bundleFolder: e.currentTarget.value })}
					/>
				</FormGroup>

				<FormGroup
					label="Filename pattern"
					helperText="Tokens: {code} {name} {version} {ext}. {version} already includes the “v” (e.g. v4)."
				>
					<InputGroup
						value={draft.filenamePattern}
						placeholder={DEFAULTS.filenamePattern}
						onChange={(e) => set({ filenamePattern: e.currentTarget.value })}
					/>
				</FormGroup>

				<FormGroup label="Bundle (ZIP) name" helperText="Token: {document} (the document number).">
					<InputGroup
						value={draft.bundleName}
						placeholder={DEFAULTS.bundleName}
						onChange={(e) => set({ bundleName: e.currentTarget.value })}
					/>
				</FormGroup>

				<FormGroup
					label="Link base URL (optional)"
					helperText="Leave blank to keep PDF links relative to the bundle. Set an absolute URL to make them open on their own."
				>
					<InputGroup
						value={draft.linkBaseUrl}
						placeholder="https://sop.example.com"
						onChange={(e) => set({ linkBaseUrl: e.currentTarget.value })}
					/>
				</FormGroup>

				<Callout intent="primary" icon="eye-open" title="Preview" style={{ marginTop: 6 }}>
					<div className="muted" style={{ marginBottom: 6 }}>
						For a <Code>{SAMPLE.language}</Code> script “{SAMPLE.name}” (ref {SAMPLE.code}, v
						{SAMPLE.version}) in document {SAMPLE.document}:
					</div>
					<div style={{ display: 'grid', gridTemplateColumns: 'auto 1fr', gap: '4px 12px' }}>
						<span className="muted">Bundle file</span>
						<Code>{scriptPath(draft)}</Code>
						<span className="muted">PDF link</span>
						<Code>{scriptHref(draft)}</Code>
						<span className="muted">ZIP name</span>
						<Code>{bundleFileName(draft)}</Code>
					</div>
				</Callout>

				<div style={{ display: 'flex', gap: 8, marginTop: 16 }}>
					<Button
						intent="primary"
						icon="floppy-disk"
						text="Save"
						loading={saveMut.isPending}
						disabled={!dirty}
						onClick={save}
					/>
					<Button minimal text="Reset to defaults" onClick={() => setDraft({ ...DEFAULTS })} />
				</div>
			</Card>
		</>
	);
}
