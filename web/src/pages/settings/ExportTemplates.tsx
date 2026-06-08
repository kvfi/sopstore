import { useRef, useState, type ChangeEvent } from 'react';
import { Button, Card, HTMLTable, InputGroup, Spinner } from '@blueprintjs/core';
import { ApiError } from '../../lib/api';
import { toast } from '../../lib/toaster';
import {
	type ExportTemplate,
	useExportTemplates,
	useCreateTemplate,
	useUpdateTemplate,
	useDeleteTemplate,
	useUploadTemplateLogo
} from '../../lib/queries';

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
				logo. Authors pick a template per procedure for its <strong>Download PDF</strong> output.
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
		</>
	);
}
