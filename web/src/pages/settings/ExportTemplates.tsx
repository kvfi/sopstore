import { useRef, useState, type ChangeEvent } from 'react';
import {
	Button,
	Callout,
	Card,
	Checkbox,
	Code,
	Dialog,
	DialogBody,
	DialogFooter,
	FormGroup,
	HTMLSelect,
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
	useUploadTemplateLogo,
	useUploadTemplateFont,
	useClearTemplateFont
} from '../../lib/queries';

type AssetKind = 'css' | 'html' | 'cover';

// Starter the cover-page editor prefills when a template has no custom cover HTML yet — it mirrors
// the built-in cover so it can be modified rather than written from scratch. Keep the `.cover`
// wrapper (it carries the page break + one-page height) and the `.cover-cell` (it applies the
// chosen vertical alignment). Every {{var}} below is available; see the dialog callout.
const DEFAULT_COVER_TEMPLATE = `<div class="cover">
  <div class="cover-cell">
    {{{logo}}}
    <h5>{{{coverTextHtml}}}</h5>
    <!-- The disclaimer h5 is uppercase, a bit larger than body text, normal weight.
         {{{logo}}} renders the logo <img> at the chosen Logo size (small/medium/large/xlarge);
         for full control use {{{logoDataUri}}} as an <img src> instead.
         Other variables: {{title}}  {{documentNumber}}  {{version}}  {{state}}
         {{confidentiality}}  {{accent}}  {{footer}} -->
  </div>
</div>`;

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
	const [setupId, setSetupId] = useState<string | null>(null);
	const setupTemplate = setupId ? templates.find((t) => t.id === setupId) : undefined;

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
				logo. Per template you can also add <strong>custom CSS</strong> (full control of headers,
					text, code, and lists), a chosen <strong>font</strong>, a <strong>cover page</strong> as the
					last page, or even a full custom HTML page. Use the <strong>style</strong>,{' '}
					<strong>code</strong>, and <strong>page-layout</strong> buttons on each row, then{' '}
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
												/>
												<Button
													small
													minimal
													icon="page-layout"
													intent={t.coverEnabled || t.hasBodyFont ? 'primary' : 'none'}
													title="Font & cover page"
													onClick={() => setSetupId(t.id)}
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

			{setupTemplate && (
				<PageSetupDialog
					template={setupTemplate}
					onClose={() => setSetupId(null)}
					onEditCover={() => {
						const id = setupTemplate.id;
						setSetupId(null);
						setEditing({ id, kind: 'cover' });
					}}
				/>
			)}
		</>
	);
}

/** Editor for a template's font (family + uploaded file) and its optional back cover page. */
function PageSetupDialog({
	template,
	onClose,
	onEditCover
}: {
	template: ExportTemplate;
	onClose: () => void;
	onEditCover: () => void;
}) {
	const update = useUpdateTemplate();
	const uploadFont = useUploadTemplateFont();
	const clearFont = useClearTemplateFont();
	const fontRef = useRef<HTMLInputElement>(null);
	const [previewing, setPreviewing] = useState(false);
	const [fontFamily, setFontFamily] = useState(template.fontFamily);
	const [coverEnabled, setCoverEnabled] = useState(template.coverEnabled);
	const [coverAlign, setCoverAlign] = useState(template.coverAlign || 'bottom');
	const [coverText, setCoverText] = useState(template.coverText);
	const [coverLogoSize, setCoverLogoSize] = useState(template.coverLogoSize || 'medium');

	const persist = () =>
		update.mutateAsync({
			id: template.id,
			name: template.name,
			accentColor: hex(template.accentColor),
			footerText: template.footerText ?? '',
			bodyFontPt: template.bodyFontPt,
			headingFontPt: template.headingFontPt,
			tableFontPt: template.tableFontPt,
			customCss: template.customCss,
			customHtml: template.customHtml,
			fontFamily: fontFamily.trim() || 'IBM Plex Sans',
			coverEnabled,
			coverText,
			coverAlign,
			coverLogoSize
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

	async function editCoverHtml() {
		try {
			await persist(); // keep the font/cover settings before switching editors
			onEditCover();
		} catch (e) {
			toast((e as Error).message, 'danger');
		}
	}

	async function onFontFile(e: ChangeEvent<HTMLInputElement>) {
		const f = e.target.files?.[0];
		if (f) {
			try {
				await uploadFont.mutateAsync({ id: template.id, file: f });
				toast('Font uploaded', 'success');
			} catch (err) {
				toast((err as Error).message, 'danger');
			}
		}
		if (fontRef.current) fontRef.current.value = '';
	}

	async function removeFont() {
		try {
			await clearFont.mutateAsync(template.id);
			toast('Font removed', 'success');
		} catch (e) {
			toast((e as Error).message, 'danger');
		}
	}

	async function preview() {
		setPreviewing(true);
		try {
			await persist();
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

	return (
		<Dialog isOpen onClose={onClose} title={`Font & cover page — ${template.name}`} style={{ width: 640 }}>
			<DialogBody>
				<Callout intent="primary" icon="font" style={{ marginBottom: 14 }}>
					The font family applies to body text and headings (code stays monospaced). Upload a{' '}
					<strong>.ttf</strong> font file to embed it; without one the built-in <Code>IBM Plex Sans</Code>{' '}
					is used.
				</Callout>
				<FormGroup
					label="Font family"
					helperText="A name alone does nothing — you must upload the matching .ttf for it to render."
				>
					<InputGroup
						value={fontFamily}
						placeholder="IBM Plex Sans"
						onChange={(e) => setFontFamily(e.currentTarget.value)}
					/>
				</FormGroup>
				<div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 10 }}>
					<Button icon="upload" text="Upload .ttf" loading={uploadFont.isPending} onClick={() => fontRef.current?.click()} />
					{template.hasBodyFont ? (
						<>
							<Code>{template.bodyFontName ?? 'font.ttf'}</Code>
							<Button minimal small intent="danger" icon="trash" title="Remove font" onClick={removeFont} />
						</>
					) : (
						<span className="muted" style={{ fontSize: 12 }}>No font uploaded.</span>
					)}
					<input
						ref={fontRef}
						type="file"
						accept=".ttf,font/ttf,application/x-font-ttf"
						style={{ display: 'none' }}
						onChange={onFontFile}
					/>
				</div>
				{!template.hasBodyFont &&
					fontFamily.trim() !== '' &&
					fontFamily.trim().toLowerCase() !== 'ibm plex sans' && (
						<Callout intent="warning" icon="warning-sign" style={{ marginBottom: 18 }}>
							You've named the font <Code>{fontFamily.trim()}</Code> but haven't uploaded a file.
							The PDF can only embed fonts you upload — there are no system fonts — so it will fall
							back to <Code>IBM Plex Sans</Code>. Click <strong>Upload .ttf</strong> and pick your{' '}
							<Code>{fontFamily.trim()}</Code> file.
						</Callout>
					)}

				<Callout intent="primary" icon="manual" style={{ marginBottom: 14 }}>
					The cover page is added as the <strong>last page</strong> of the export. It shows the
					template logo above your text, aligned to the chosen edge of the page. Style it further via{' '}
					<Code>.cover</Code>, <Code>.cover-logo</Code>, <Code>.cover-text</Code> in custom CSS.
				</Callout>
				<Checkbox
					checked={coverEnabled}
					label="Add a cover page as the last page"
					onChange={(e) => setCoverEnabled(e.currentTarget.checked)}
				/>
				<FormGroup label="Vertical alignment" disabled={!coverEnabled}>
					<HTMLSelect
						value={coverAlign}
						disabled={!coverEnabled}
						onChange={(e) => setCoverAlign(e.currentTarget.value)}
						options={[
							{ value: 'bottom', label: 'Bottom of page' },
							{ value: 'middle', label: 'Middle of page' },
							{ value: 'top', label: 'Top of page' }
						]}
					/>
				</FormGroup>
				<FormGroup
					label="Logo size"
					disabled={!coverEnabled}
					helperText="Size of the {{{logo}}} image on the cover page."
				>
					<HTMLSelect
						value={coverLogoSize}
						disabled={!coverEnabled}
						onChange={(e) => setCoverLogoSize(e.currentTarget.value)}
						options={[
							{ value: 'small', label: 'Small' },
							{ value: 'medium', label: 'Medium' },
							{ value: 'large', label: 'Large' },
							{ value: 'xlarge', label: 'Extra large' }
						]}
					/>
				</FormGroup>
				<FormGroup label="Cover text" disabled={!coverEnabled} helperText="Plain text; line breaks are preserved.">
					<TextArea
						value={coverText}
						disabled={!coverEnabled}
						onChange={(e) => setCoverText(e.currentTarget.value)}
						fill
						style={{ minHeight: 120 }}
						placeholder={'© 2026 RightCrowd\nControlled document — do not distribute.'}
					/>
				</FormGroup>
				<Callout intent="none" icon="code" style={{ marginTop: 4 }}>
					Need full control of the cover markup? Edit its{' '}
					<strong>HTML template</strong> — the text and logo above feed into it.{' '}
					<Button
						minimal
						small
						intent="primary"
						icon="manually-entered-data"
						text={template.coverHtml ? 'Edit cover HTML template' : 'Customize cover HTML template'}
						disabled={!coverEnabled}
						onClick={editCoverHtml}
					/>
				</Callout>
			</DialogBody>
			<DialogFooter
				actions={
					<>
						<Button icon="eye-open" text="Preview PDF" loading={previewing} onClick={preview} />
						<Button intent="primary" icon="floppy-disk" text="Save" loading={update.isPending} onClick={save} />
					</>
				}
			/>
		</Dialog>
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
	const initial =
		kind === 'css'
			? template.customCss
			: kind === 'html'
				? template.customHtml
				: template.coverHtml || DEFAULT_COVER_TEMPLATE;
	const [text, setText] = useState(initial);
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
			customHtml: kind === 'html' ? text : template.customHtml,
			coverHtml: kind === 'cover' ? text : template.coverHtml
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
		a.download = `${template.name || 'template'}-${kind}.${kind === 'css' ? 'css' : 'html'}`;
		a.click();
		URL.revokeObjectURL(url);
	}

	const dialogTitle =
		kind === 'css'
			? 'Custom CSS'
			: kind === 'html'
				? 'Custom HTML template'
				: 'Cover page template';

	return (
		<Dialog
			isOpen
			onClose={onClose}
			title={`${dialogTitle} — ${template.name}`}
			style={{ width: 860 }}
		>
			<DialogBody>
				{kind === 'css' ? (
					<Callout intent="primary" icon="style" style={{ marginBottom: 10 }}>
						Appended after the built-in stylesheet, so your rules win — full control over headers{' '}
							(<Code>h1</Code>/<Code>h2</Code>/<Code>h3</Code>), text (<Code>p</Code>/
							<Code>blockquote</Code>), code (<Code>.code</Code>/<Code>pre</Code>/<Code>code</Code>),
							lists (<Code>ul</Code>/<Code>ol</Code>/<Code>li</Code>), the cover page{' '}
							(<Code>.cover</Code>/<Code>.cover-logo</Code>/<Code>.cover-text</Code>), plus{' '}
							<Code>.toc</Code>, <Code>.script-link</Code>, and <Code>table.hist</Code>. Leave empty
							to keep the defaults.
					</Callout>
				) : kind === 'html' ? (
					<Callout intent="primary" icon="code" style={{ marginBottom: 10 }}>
						Full page template (Mustache). Scalars: <Code>{'{{title}}'}</Code>,{' '}
						<Code>{'{{documentNumber}}'}</Code>, <Code>{'{{version}}'}</Code>,{' '}
						<Code>{'{{state}}'}</Code>, <Code>{'{{confidentiality}}'}</Code>,{' '}
						<Code>{'{{accent}}'}</Code>, <Code>{'{{logoDataUri}}'}</Code>,{' '}
						<Code>{'{{footer}}'}</Code>. Raw blocks: <Code>{'{{{builtinCss}}}'}</Code>,{' '}
						<Code>{'{{{customCss}}}'}</Code>, <Code>{'{{{defaultBody}}}'}</Code>,{' '}
						<Code>{'{{{purposeHtml}}}'}</Code>, <Code>{'{{{scopeHtml}}}'}</Code>,{' '}
						<Code>{'{{{prerequisitesHtml}}}'}</Code>, <Code>{'{{{historyHtml}}}'}</Code>,{' '}
							<Code>{'{{{coverPage}}}'}</Code>. Loop:{' '}
						<Code>{'{{#steps}}'}</Code> … <Code>{'{{/steps}}'}</Code> with <Code>number</Code>,{' '}
						<Code>title</Code>, <Code>typeLabel</Code>, <Code>isRunScript</Code>,{' '}
						<Code>scriptName</Code>, <Code>scriptHref</Code>, <Code>{'{{{descriptionHtml}}}'}</Code>,{' '}
						<Code>{'{{{html}}}'}</Code>. Empty = built-in layout; a broken template falls back
						automatically.
					</Callout>
				) : (
					<Callout intent="primary" icon="manual" style={{ marginBottom: 10 }}>
						Markup for the <strong>cover page</strong> (last page), Mustache. Keep the{' '}
						<Code>.cover</Code> wrapper (it carries the page break + one-page height) and{' '}
						<Code>.cover-cell</Code> (it applies your chosen alignment); style them in custom CSS.
						Wrap the disclaimer in <Code>{'<h5>'}</Code> for the uppercase, slightly-larger,
						normal-weight heading style (<Code>h1</Code>/<Code>h2</Code>/<Code>h3</Code> are styled
						too). Variables: <Code>{'{{title}}'}</Code>, <Code>{'{{documentNumber}}'}</Code>,{' '}
						<Code>{'{{version}}'}</Code>, <Code>{'{{state}}'}</Code>,{' '}
						<Code>{'{{confidentiality}}'}</Code>, <Code>{'{{accent}}'}</Code>,{' '}
						<Code>{'{{coverText}}'}</Code>, <Code>{'{{coverAlign}}'}</Code>,{' '}
						<Code>{'{{footer}}'}</Code>. Raw: <Code>{'{{{logo}}}'}</Code> (the logo{' '}
						<Code>{'<img>'}</Code> at the chosen <strong>Logo size</strong>),{' '}
						<Code>{'{{{logoDataUri}}}'}</Code> (the raw data URI for your own{' '}
						<Code>{'<img src>'}</Code>), <Code>{'{{{coverTextHtml}}}'}</Code> (the cover text with
						line breaks). Section: <Code>{'{{#hasLogo}}'}</Code> … <Code>{'{{/hasLogo}}'}</Code>.
						Empty = built-in cover; a broken template falls back automatically.
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
							: kind === 'html'
								? '<html><head><style>{{{builtinCss}}} {{{customCss}}}</style></head>\n<body>{{{defaultBody}}}</body></html>'
								: DEFAULT_COVER_TEMPLATE
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
