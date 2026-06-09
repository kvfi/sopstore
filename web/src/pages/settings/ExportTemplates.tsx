import { useRef, useState, type ChangeEvent } from 'react';
import {
	Button,
	Callout,
	Card,
	Code,
	Dialog,
	DialogBody,
	DialogFooter,
	HTMLTable,
	InputGroup,
	Spinner,
	TextArea
} from '@blueprintjs/core';
import { ApiError, postBlob } from '../../lib/api';
import { toast } from '../../lib/toaster';
import {
	type ExportTemplate,
	useExportTemplates,
	useCreateTemplate,
	useUpdateTemplate,
	useDeleteTemplate,
	useUploadTemplateLogo
} from '../../lib/queries';

type AssetKind = 'css' | 'html';

type Draft = {
	name: string;
	accentColor: string;
	footerText: string;
	fontPt: number;
	headingPt: number;
	tablePt: number;
};

const hex = (c: string) => (c.startsWith('#') ? c : `#${c}`);
const errMsg = (e: unknown, dup: string) =>
	e instanceof ApiError && e.status === 409 ? dup : (e as Error).message;

export default function ExportTemplates() {
	const { data, isLoading } = useExportTemplates();
	const templates = data ?? [];
	const create = useCreateTemplate();
	const update = useUpdateTemplate();
	const del = useDeleteTemplate();
	const uploadLogo = useUploadTemplateLogo();

	const [drafts, setDrafts] = useState<Record<string, Draft>>({});
	const [neu, setNeu] = useState<Draft>({ name: '', accentColor: '#215db0', footerText: '', fontPt: 10, headingPt: 12, tablePt: 9.5 });
	const [logoVer, setLogoVer] = useState(0); // cache-busts the logo <img> after re-upload
	const fileInput = useRef<HTMLInputElement>(null);
	const [logoTarget, setLogoTarget] = useState<string | null>(null);
	const [editing, setEditing] = useState<{ id: string; kind: AssetKind } | null>(null);
	const editTemplate = editing ? templates.find((t) => t.id === editing.id) : undefined;

	const draftFor = (t: ExportTemplate): Draft =>
		drafts[t.id] ?? {
			name: t.name,
			accentColor: hex(t.accentColor),
			footerText: t.footerText ?? '',
			fontPt: t.bodyFontPt,
			headingPt: t.headingFontPt,
			tablePt: t.tableFontPt
		};
	const setDraft = (id: string, patch: Partial<Draft>, base: Draft) =>
		setDrafts((d) => ({ ...d, [id]: { ...base, ...patch } }));

	async function add() {
		const name = neu.name.trim();
		if (!name) return;
		try {
			await create.mutateAsync({
				name,
				accentColor: neu.accentColor,
				footerText: neu.footerText.trim(),
				bodyFontPt: neu.fontPt,
				headingFontPt: neu.headingPt,
				tableFontPt: neu.tablePt
			});
			toast(`Added "${name}"`, 'success');
			setNeu({ name: '', accentColor: '#215db0', footerText: '', fontPt: 10, headingPt: 12, tablePt: 9.5 });
		} catch (e) {
			toast(errMsg(e, 'That template name already exists.'), 'danger');
		}
	}

	async function save(t: ExportTemplate) {
		const d = draftFor(t);
		if (!d.name.trim()) return;
		try {
			await update.mutateAsync({
				id: t.id,
				name: d.name.trim(),
				accentColor: d.accentColor,
				footerText: d.footerText.trim(),
				bodyFontPt: d.fontPt,
				headingFontPt: d.headingPt,
				tableFontPt: d.tablePt
			});
			toast('Saved', 'success');
		} catch (e) {
			toast(errMsg(e, 'That template name already exists.'), 'danger');
		}
	}

	async function remove(t: ExportTemplate) {
		if (!window.confirm(`Delete "${t.name}"? Procedures using it fall back to the default style.`)) return;
		try {
			await del.mutateAsync(t.id);
			toast('Deleted', 'success');
		} catch (e) {
			toast((e as Error).message, 'danger');
		}
	}

	function pickLogo(id: string) {
		setLogoTarget(id);
		fileInput.current?.click();
	}
	async function onLogoFile(e: ChangeEvent<HTMLInputElement>) {
		const file = e.target.files?.[0];
		const id = logoTarget;
		if (file && id) {
			try {
				await uploadLogo.mutateAsync({ id, file });
				setLogoVer((v) => v + 1);
				toast('Logo updated', 'success');
			} catch (err) {
				toast((err as Error).message, 'danger');
			}
		}
		setLogoTarget(null);
		if (fileInput.current) fileInput.current.value = '';
	}

	return (
		<>
			<p className="page-sub">
				PDF-export themes: a name, an accent colour (title + headings), optional footer text, and a
				logo. Per template you can also add <strong>custom CSS</strong> (restyles the built-in
				layout) and a full <strong>custom HTML</strong> page template — use the{' '}
				<strong>style</strong> and <strong>code</strong> buttons on each row, then{' '}
				<strong>Preview PDF</strong>. Authors pick a template per procedure for its{' '}
				<strong>Download PDF</strong> output.
			</p>

			<input ref={fileInput} type="file" accept="image/*" style={{ display: 'none' }} onChange={onLogoFile} />

			{isLoading ? (
				<div className="center-screen">
					<Spinner />
				</div>
			) : (
				<Card style={{ maxWidth: 860 }}>
					<HTMLTable className="full">
						<thead>
							<tr>
								<th>Name</th>
								<th style={{ width: 90 }}>Accent</th>
								<th style={{ width: 64 }}>Body</th>
								<th style={{ width: 64 }}>Heading</th>
								<th style={{ width: 64 }}>Table</th>
								<th>Footer</th>
								<th style={{ width: 120 }}>Logo</th>
								<th className="right">Actions</th>
							</tr>
						</thead>
						<tbody>
							{templates.length === 0 ? (
								<tr>
									<td colSpan={8} className="muted">No templates yet — add your first below.</td>
								</tr>
							) : (
								templates.map((t) => {
									const d = draftFor(t);
									const dirty =
										d.name.trim().length > 0 &&
										(d.name !== t.name ||
											d.accentColor !== hex(t.accentColor) ||
											d.footerText !== (t.footerText ?? '') ||
											d.fontPt !== t.bodyFontPt ||
							d.headingPt !== t.headingFontPt ||
							d.tablePt !== t.tableFontPt);
									return (
										<tr key={t.id}>
											<td>
												<InputGroup fill value={d.name} onChange={(e) => setDraft(t.id, { name: e.currentTarget.value }, d)} />
											</td>
											<td>
												<input
													type="color"
													value={d.accentColor}
													onChange={(e) => setDraft(t.id, { accentColor: e.currentTarget.value }, d)}
													style={{ width: 44, height: 30, border: 'none', background: 'none', cursor: 'pointer' }}
												/>
											</td>
											<td>
												<input
													type="number"
													min={7}
													max={16}
													step={0.5}
													value={d.fontPt}
													onChange={(e) => setDraft(t.id, { fontPt: Number(e.currentTarget.value) }, d)}
													className="bp6-input"
													style={{ width: 54 }}
													title="Body font size (pt)"
												/>
											</td>
											<td>
												<input
													type="number"
													min={8}
													max={28}
													step={0.5}
													value={d.headingPt}
													onChange={(e) => setDraft(t.id, { headingPt: Number(e.currentTarget.value) }, d)}
													className="bp6-input"
													style={{ width: 54 }}
													title="Heading font size (pt)"
												/>
											</td>
											<td>
												<input
													type="number"
													min={6}
													max={14}
													step={0.5}
													value={d.tablePt}
													onChange={(e) => setDraft(t.id, { tablePt: Number(e.currentTarget.value) }, d)}
													className="bp6-input"
													style={{ width: 54 }}
													title="Table font size (pt)"
												/>
											</td>
											<td>
												<InputGroup fill value={d.footerText} placeholder="e.g. Confidential" onChange={(e) => setDraft(t.id, { footerText: e.currentTarget.value }, d)} />
											</td>
											<td>
												{t.hasLogo ? (
													<img
														src={`/api/v1/export-templates/${t.id}/logo?v=${logoVer}`}
														alt="logo"
														style={{ height: 28, maxWidth: 100, objectFit: 'contain', verticalAlign: 'middle' }}
													/>
												) : (
													<span className="muted" style={{ fontSize: 12 }}>none</span>
												)}{' '}
												<Button small minimal icon="upload" onClick={() => pickLogo(t.id)} aria-label="Upload logo" />
											</td>
											<td className="right nowrap">
												<Button
													small
													minimal
													icon="style"
													intent={t.customCss ? 'primary' : 'none'}
													title="Custom CSS"
													onClick={() => setEditing({ id: t.id, kind: 'css' })}
												/>
												<Button
													small
													minimal
													icon="code"
													intent={t.customHtml ? 'primary' : 'none'}
													title="Custom HTML template"
													onClick={() => setEditing({ id: t.id, kind: 'html' })}
												/>{' '}
												<Button small minimal intent="primary" disabled={!dirty} onClick={() => save(t)} text="Save" />{' '}
												<Button small minimal intent="danger" icon="trash" onClick={() => remove(t)} />
											</td>
										</tr>
									);
								})
							)}
						</tbody>
					</HTMLTable>

					<div style={{ display: 'flex', gap: 8, marginTop: 14, alignItems: 'center' }}>
						<div style={{ flex: 1 }}>
							<InputGroup fill placeholder="New template name (e.g. RightCrowd GMP)" value={neu.name} onChange={(e) => setNeu({ ...neu, name: e.currentTarget.value })} />
						</div>
						<input
							type="color"
							value={neu.accentColor}
							onChange={(e) => setNeu({ ...neu, accentColor: e.currentTarget.value })}
							style={{ width: 44, height: 30, border: 'none', background: 'none', cursor: 'pointer' }}
							aria-label="Accent colour"
						/>
						<input
							type="number"
							min={7}
							max={16}
							step={0.5}
							value={neu.fontPt}
							onChange={(e) => setNeu({ ...neu, fontPt: Number(e.currentTarget.value) })}
							className="bp6-input"
							style={{ width: 54 }}
							aria-label="Body font size (pt)"
							title="Body font size (pt)"
						/>
						<input
							type="number"
							min={8}
							max={28}
							step={0.5}
							value={neu.headingPt}
							onChange={(e) => setNeu({ ...neu, headingPt: Number(e.currentTarget.value) })}
							className="bp6-input"
							style={{ width: 54 }}
							aria-label="Heading font size (pt)"
							title="Heading font size (pt)"
						/>
						<input
							type="number"
							min={6}
							max={14}
							step={0.5}
							value={neu.tablePt}
							onChange={(e) => setNeu({ ...neu, tablePt: Number(e.currentTarget.value) })}
							className="bp6-input"
							style={{ width: 54 }}
							aria-label="Table font size (pt)"
							title="Table font size (pt)"
						/>
						<div style={{ flex: 1 }}>
							<InputGroup fill placeholder="Footer (optional)" value={neu.footerText} onChange={(e) => setNeu({ ...neu, footerText: e.currentTarget.value })} />
						</div>
						<Button intent="primary" icon="add" loading={create.isPending} disabled={!neu.name.trim()} onClick={add} text="Add" />
					</div>
					<p className="muted" style={{ fontSize: 12, marginTop: 8 }}>
						Add a template, then upload its logo with the <strong>↑</strong> button on its row.
					</p>
				</Card>
			)}

			{editing && editTemplate && (
				<AssetDialog
					template={editTemplate}
					kind={editing.kind}
					onClose={() => setEditing(null)}
				/>
			)}
		</>
	);
}

/** Editor for a template's custom CSS or full HTML page template: edit, upload, download, preview. */
function AssetDialog({
	template,
	kind,
	onClose
}: {
	template: ExportTemplate;
	kind: AssetKind;
	onClose: () => void;
}) {
	const update = useUpdateTemplate();
	const [text, setText] = useState(kind === 'css' ? template.customCss : template.customHtml);
	const fileRef = useRef<HTMLInputElement>(null);
	const [previewing, setPreviewing] = useState(false);

	const persist = () =>
		update.mutateAsync({
			id: template.id,
			name: template.name,
			accentColor: hex(template.accentColor),
			footerText: template.footerText ?? '',
			bodyFontPt: template.bodyFontPt,
			headingFontPt: template.headingFontPt,
			tableFontPt: template.tableFontPt,
			customCss: kind === 'css' ? text : template.customCss,
			customHtml: kind === 'html' ? text : template.customHtml
		});

	async function save() {
		try {
			await persist();
			toast('Saved', 'success');
			onClose();
		} catch (e) {
			toast((e as Error).message, 'danger');
		}
	}

	async function preview() {
		setPreviewing(true);
		try {
			await persist(); // the preview renders the saved template
			const blob = await postBlob(`/api/v1/export-templates/${template.id}/preview`);
			const url = URL.createObjectURL(blob);
			window.open(url, '_blank', 'noopener');
			setTimeout(() => URL.revokeObjectURL(url), 60000);
		} catch (e) {
			toast((e as Error).message, 'danger');
		} finally {
			setPreviewing(false);
		}
	}

	async function onFile(e: ChangeEvent<HTMLInputElement>) {
		const f = e.target.files?.[0];
		if (f) setText(await f.text());
		if (fileRef.current) fileRef.current.value = '';
	}

	function download() {
		const blob = new Blob([text], { type: kind === 'css' ? 'text/css' : 'text/html' });
		const url = URL.createObjectURL(blob);
		const a = document.createElement('a');
		a.href = url;
		a.download = `${template.name || 'template'}.${kind === 'css' ? 'css' : 'html'}`;
		a.click();
		URL.revokeObjectURL(url);
	}

	return (
		<Dialog
			isOpen
			onClose={onClose}
			title={`${kind === 'css' ? 'Custom CSS' : 'Custom HTML template'} — ${template.name}`}
			style={{ width: 860 }}
		>
			<DialogBody>
				{kind === 'css' ? (
					<Callout intent="primary" icon="style" style={{ marginBottom: 10 }}>
						Appended after the built-in stylesheet, so your rules win. Target classes such as{' '}
						<Code>h1</Code>, <Code>h2</Code>, <Code>.toc</Code>, <Code>.script-link</Code>,{' '}
						<Code>table.hist</Code>. Leave empty to keep the defaults.
					</Callout>
				) : (
					<Callout intent="primary" icon="code" style={{ marginBottom: 10 }}>
						Full page template (Mustache). Scalars: <Code>{'{{title}}'}</Code>,{' '}
						<Code>{'{{documentNumber}}'}</Code>, <Code>{'{{version}}'}</Code>,{' '}
						<Code>{'{{state}}'}</Code>, <Code>{'{{confidentiality}}'}</Code>,{' '}
						<Code>{'{{accent}}'}</Code>, <Code>{'{{logoDataUri}}'}</Code>,{' '}
						<Code>{'{{footer}}'}</Code>. Raw blocks: <Code>{'{{{builtinCss}}}'}</Code>,{' '}
						<Code>{'{{{customCss}}}'}</Code>, <Code>{'{{{defaultBody}}}'}</Code>,{' '}
						<Code>{'{{{purposeHtml}}}'}</Code>, <Code>{'{{{scopeHtml}}}'}</Code>,{' '}
						<Code>{'{{{prerequisitesHtml}}}'}</Code>, <Code>{'{{{historyHtml}}}'}</Code>. Loop:{' '}
						<Code>{'{{#steps}}'}</Code> … <Code>{'{{/steps}}'}</Code> with <Code>number</Code>,{' '}
						<Code>title</Code>, <Code>typeLabel</Code>, <Code>isRunScript</Code>,{' '}
						<Code>scriptName</Code>, <Code>scriptHref</Code>, <Code>{'{{{descriptionHtml}}}'}</Code>,{' '}
						<Code>{'{{{html}}}'}</Code>. Empty = built-in layout; a broken template falls back
						automatically.
					</Callout>
				)}
				<TextArea
					value={text}
					onChange={(e) => setText(e.currentTarget.value)}
					fill
					style={{ minHeight: 340, fontFamily: 'monospace', fontSize: 12, whiteSpace: 'pre' }}
					placeholder={
						kind === 'css'
							? '.doc-title { color: #0a7; }\nh2 { border-bottom: 2px solid {{accent}}; }'
							: '<html><head><style>{{{builtinCss}}} {{{customCss}}}</style></head>\n<body>{{{defaultBody}}}</body></html>'
					}
				/>
				<input
					ref={fileRef}
					type="file"
					accept={kind === 'css' ? '.css,text/css' : '.html,.htm,text/html'}
					style={{ display: 'none' }}
					onChange={onFile}
				/>
			</DialogBody>
			<DialogFooter
				actions={
					<>
						<Button icon="upload" text="Upload file" onClick={() => fileRef.current?.click()} />
						<Button icon="download" text="Download" onClick={download} />
						<Button icon="eye-open" text="Preview PDF" loading={previewing} onClick={preview} />
						<Button
							intent="primary"
							icon="floppy-disk"
							text="Save"
							loading={update.isPending}
							onClick={save}
						/>
					</>
				}
			/>
		</Dialog>
	);
}
