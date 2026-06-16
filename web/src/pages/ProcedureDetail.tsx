import { useEffect, useMemo, useRef, useState, type ChangeEvent, type DragEvent as ReactDragEvent } from 'react';
import { useParams, Link } from 'react-router-dom';
import {
	AnchorButton,
	Button,
	Callout,
	Card,
	Checkbox,
	Dialog,
	DialogBody,
	DialogFooter,
	FormGroup,
	HTMLSelect,
	HTMLTable,
	Icon,
	type IconName,
	InputGroup,
	type Intent,
	NonIdealState,
	Spinner,
	Tag,
	TextArea
} from '@blueprintjs/core';
import type { Editor } from '@tiptap/react';
import { toast } from '../lib/toaster';
import { statusIntent, dt } from '../lib/ui';
import Combobox from '../components/Combobox';
import RichTextEditor, { type RefItem, type RichTextHandle } from '../components/RichTextEditor';
import ScriptViewer from '../components/ScriptViewer';
import { PrerequisiteRef } from '../components/prerequisite-ref-node';

/** MIME used to carry a picked prerequisite from the picker chip onto the editor via drag-and-drop. */
const PREREQ_MIME = 'application/x-prereq';
const PREREQ_EXTENSIONS = [PrerequisiteRef];
import {
	type Att,
	type FormField,
	useProcedureForm,
	useProcedureDetail,
	useChangeRequests,
	useProcedureBody,
	useAttachments,
	usePrereqTypes,
	usePrereqLib,
	useExportTemplates,
	useScripts,
	useSaveBody,
	useSetTitle,
	useSetVersionLabel,
	useSetDocumentNumber,
	useConfidentialityLevels,
	useSetConfidentiality,
	useMe,
	useOpenChangeRequest,
	useUploadAttachment,
	useDeleteAttachment
} from '../lib/queries';

/**
 * A step in the authored body: a stable React key id, a title, a built-in type (icon/formatting),
 * an optional linked-script attachment ref (for RUN_SCRIPT), and a rich-text description.
 */
type StepDraft = {
	id: string;
	title: string;
	stepType: string;
	scriptRefId: string;
	// RUN_SCRIPT may link a repository script pinned to a version (script-service)
	scriptId: string;
	scriptVersionNo: number;
	scriptName: string;
	scriptLanguage: string; // script's language; drives the bundled script file's extension
	scriptRefCode: string; // random short id assigned when the script is referenced
	description: unknown;
};

/** Built-in step types: each gives the step an icon + intent; RUN_SCRIPT links a script attachment. */
const STEP_TYPES: { value: string; label: string; icon: IconName; intent: Intent }[] = [
	{ value: 'ACTION', label: 'Action', icon: 'dot', intent: 'none' },
	{ value: 'RUN_SCRIPT', label: 'Run script', icon: 'play', intent: 'primary' },
	{ value: 'VERIFICATION', label: 'Verification', icon: 'tick-circle', intent: 'success' },
	{ value: 'WARNING', label: 'Warning', icon: 'warning-sign', intent: 'warning' },
	{ value: 'NOTE', label: 'Note', icon: 'annotation', intent: 'none' }
];
const stepType = (v: string) => STEP_TYPES.find((t) => t.value === v) ?? STEP_TYPES[0];

type Reason = { label: string; classification: string; trainingImpact: boolean };
const REASONS: Reason[] = [
	{ label: 'Periodic review', classification: 'MINOR', trainingImpact: false },
	{ label: 'Editorial / content correction', classification: 'MINOR', trainingImpact: false },
	{ label: 'Process improvement', classification: 'MINOR', trainingImpact: false },
	{ label: 'Major revision', classification: 'MAJOR', trainingImpact: true },
	{ label: 'Regulatory / compliance update', classification: 'MAJOR', trainingImpact: true },
	{ label: 'Corrective action (CAPA)', classification: 'MAJOR', trainingImpact: true }
];

const fmtSize = (n: number) =>
	n < 1024 ? `${n} B` : n < 1048576 ? `${(n / 1024).toFixed(1)} KB` : `${(n / 1048576).toFixed(1)} MB`;

const uid = () =>
	typeof crypto !== 'undefined' && crypto.randomUUID ? crypto.randomUUID() : `s-${Math.random().toString(36).slice(2)}`;

// Short random reference id (letters + digits, ambiguous chars dropped) for attachments / script refs.
const REF_ALPHABET = 'abcdefghijkmnpqrstuvwxyz23456789';
function randomRefId(taken: Set<string>): string {
	for (let t = 0; t < 50; t++) {
		let s = '';
		for (let i = 0; i < 4; i++) s += REF_ALPHABET[Math.floor(Math.random() * REF_ALPHABET.length)];
		if (!taken.has(s)) return s;
	}
	return `r${Math.random().toString(36).slice(2, 6)}`;
}

/** Coerces a stored field (TipTap JSON object, legacy plain string, or empty) into editor content. */
function asRich(v: unknown): object | null {
	if (v && typeof v === 'object') return v as object;
	if (typeof v === 'string' && v.trim()) {
		return {
			type: 'doc',
			content: v.split('\n').map((line) => ({
				type: 'paragraph',
				content: line ? [{ type: 'text', text: line }] : []
			}))
		};
	}
	return null;
}

/** A TipTap doc counts as content if it has any text or an attachment reference. */
function hasContent(node: unknown): boolean {
	if (!node || typeof node !== 'object') return false;
	const n = node as { type?: string; text?: string; content?: unknown[] };
	if (n.type === 'attachmentRef') return true;
	if (typeof n.text === 'string' && n.text.trim()) return true;
	return Array.isArray(n.content) && n.content.some(hasContent);
}

/** Normalise a stored instruction (TipTap JSON object, or a legacy plain string) for the editor. */
const toEditorContent = (instruction: unknown): object | string | null =>
	instruction && typeof instruction === 'object' ? (instruction as object) : typeof instruction === 'string' ? instruction : null;

/**
 * Normalise stored prerequisites for the rich-text editor. New content is a TipTap doc; legacy
 * content was an array of {type,text} (or plain strings) — migrate that to a bullet list of
 * prerequisite chips so existing procedures keep their prerequisites.
 */
function prereqsToDoc(value: unknown): object | null {
	if (value && typeof value === 'object' && !Array.isArray(value)) return value as object;
	if (Array.isArray(value) && value.length) {
		const items = value.map((p) => {
			const o = p as { type?: unknown; text?: unknown };
			const ptype = typeof o?.type === 'string' ? o.type : '';
			const text = typeof o?.text === 'string' ? o.text : typeof p === 'string' ? p : '';
			return {
				type: 'listItem',
				content: [
					{ type: 'paragraph', content: [{ type: 'prerequisiteRef', attrs: { ptype, text } }] }
				]
			};
		});
		return { type: 'doc', content: [{ type: 'bulletList', content: items }] };
	}
	return null;
}

export default function ProcedureDetail() {
	const { id = '' } = useParams();
	const detailQ = useProcedureDetail(id);
	const ccQ = useChangeRequests(id);
	const bodyQ = useProcedureBody(id);
	const attQ = useAttachments(id);

	const d = detailQ.data ?? null;
	const cc = ccQ.data ?? null;
	const atts = useMemo(() => attQ.data ?? [], [attQ.data]);
	const prereqTypes = (usePrereqTypes().data ?? []).map((t) => t.name);
	const prereqLib = usePrereqLib().data ?? [];
	const templates = useExportTemplates().data ?? [];
	const repoScripts = useScripts().data ?? [];
	const customForm = useProcedureForm().data?.fields ?? [];

	const isAdmin = (useMe().data?.roles ?? []).includes('TENANT_ADMIN');

	const confLevels = useConfidentialityLevels().data ?? [];

	const saveBodyM = useSaveBody(id);
	const setTitleM = useSetTitle(id);
	const setVersionM = useSetVersionLabel(id);
	const setDocNumM = useSetDocumentNumber(id);
	const setConfM = useSetConfidentiality(id);
	const openCrM = useOpenChangeRequest(id);
	const uploadM = useUploadAttachment(id);
	const deleteAttM = useDeleteAttachment(id);

	// current version label (editable). versionDraft null = showing the saved label.
	const currentLabel = d?.versions.find((v) => v.id === d.currentVersionId)?.label ?? '';
	const [versionDraft, setVersionDraft] = useState<string | null>(null);
	const versionValue = versionDraft ?? currentLabel;

	// document number is admin-editable. docNumDraft null = showing the saved number.
	const [docNumDraft, setDocNumDraft] = useState<string | null>(null);
	const docNumValue = docNumDraft ?? d?.documentNumber ?? '';

	// title (procedure name) is editable while DRAFT. titleDraft null = not editing.
	const [titleDraft, setTitleDraft] = useState<string | null>(null);

	// structured document (editable locally, hydrated once from the body)
	const [purpose, setPurpose] = useState<object | null>(null);
	const [scope, setScope] = useState<object | null>(null);
	const [prereqDoc, setPrereqDoc] = useState<object | null>(null);
	const [steps, setSteps] = useState<StepDraft[]>([]);
	const [templateId, setTemplateId] = useState('');
	// custom-field values keyed by field id (definitions come from the tenant's procedure-form schema)
	const [customValues, setCustomValues] = useState<Record<string, unknown>>({});
	const [viewScript, setViewScript] = useState<{ id: string; name: string; version: number; code: string } | null>(null);

	// prerequisites picker: choose a type + requirement, then drag it into the text (or Insert)
	const prereqEditor = useRef<RichTextHandle>(null);
	const [pickType, setPickType] = useState('');
	const [pickText, setPickText] = useState('');
	// stable short reference ids (A1, A2…) for procedure-level attachments, keyed by attachment id
	const [attRefs, setAttRefs] = useState<Record<string, string>>({});
	const [bodyReady, setBodyReady] = useState(false);
	const [bodyDirty, setBodyDirty] = useState(false);

	// change request dialog
	const [showCr, setShowCr] = useState(false);
	const [crReason, setCrReason] = useState(0);
	const [crComment, setCrComment] = useState('');

	const fileInput = useRef<HTMLInputElement>(null);

	const editable = d?.state === 'DRAFT';

	// Hydrate the editable document from the body once per procedure, so that refetches after a
	// save don't clobber in-progress edits or remount the step editors (which would lose the caret).
	const hydratedId = useRef<string | null>(null);
	useEffect(() => {
		if (!bodyQ.data || hydratedId.current === id) return;
		hydratedId.current = id;
		let doc: Record<string, unknown> = {};
		try {
			doc = bodyQ.data.body ? JSON.parse(bodyQ.data.body) : {};
		} catch {
			doc = {};
		}
		setPurpose(asRich(doc.purpose));
		setScope(asRich(doc.scope));
		setPrereqDoc(prereqsToDoc(doc.prerequisites));
		setSteps(
			Array.isArray(doc.steps)
				? (doc.steps as Record<string, unknown>[]).map((s) => ({
						id: uid(),
						title: typeof s.title === 'string' ? s.title : '',
						stepType: typeof s.type === 'string' ? s.type : 'ACTION',
						scriptRefId: typeof s.scriptRefId === 'string' ? s.scriptRefId : '',
						scriptId: typeof s.scriptId === 'string' ? s.scriptId : '',
						scriptVersionNo: typeof s.scriptVersionNo === 'number' ? s.scriptVersionNo : 0,
						scriptName: typeof s.scriptName === 'string' ? s.scriptName : '',
						scriptLanguage: typeof s.scriptLanguage === 'string' ? s.scriptLanguage : '',
						scriptRefCode: typeof s.scriptRefCode === 'string' ? s.scriptRefCode : '',
						// new schema: description (TipTap JSON); legacy steps stored "instruction"
						description: s.description ?? s.instruction ?? null
					}))
				: []
		);
		setAttRefs(
			doc.attachmentRefs && typeof doc.attachmentRefs === 'object'
				? (doc.attachmentRefs as Record<string, string>)
				: {}
		);
		setTemplateId(typeof doc.templateId === 'string' ? doc.templateId : '');
		// Custom-field values live in the body as a self-contained snapshot; index them by field key
		// so the live schema can prefill its inputs. Read the flat `customFields` and (for older
		// procedures) the nested `customSections`.
		const cv: Record<string, unknown> = {};
		const indexFields = (arr: unknown) => {
			if (Array.isArray(arr)) {
				for (const f of arr as Record<string, unknown>[]) {
					if (f && typeof f.key === 'string') cv[f.key] = f.value;
				}
			}
		};
		indexFields(doc.customFields);
		if (Array.isArray(doc.customSections)) {
			for (const sec of doc.customSections as Record<string, unknown>[]) indexFields(sec?.fields);
		}
		setCustomValues(cv);
		setBodyReady(true);
	}, [bodyQ.data, id]);

	// Assign a stable random short ref id (letters+digits) to any attachment that lacks one.
	useEffect(() => {
		setAttRefs((prev) => {
			const next = { ...prev };
			const taken = new Set(Object.values(next));
			let changed = false;
			for (const a of atts) {
				if (!next[a.id]) {
					const id = randomRefId(taken);
					next[a.id] = id;
					taken.add(id);
					changed = true;
				}
			}
			return changed ? next : prev;
		});
	}, [atts]);

	// Attachments that can be cited from a step, in ref-id order.
	const stepRefs = useMemo<RefItem[]>(
		() =>
			atts
				.filter((a) => attRefs[a.id])
				.map((a) => ({ refId: attRefs[a.id], filename: a.filename }))
				.sort((x, y) => x.refId.localeCompare(y.refId, undefined, { numeric: true })),
		[atts, attRefs]
	);

	// Library requirements for the picker's chosen type — drives its autocomplete.
	const pickItems = useMemo(() => {
		const t = pickType.trim().toLowerCase();
		const seen = new Set<string>();
		return prereqLib
			.filter((p) => (t ? p.type.toLowerCase() === t : true))
			.map((p) => p.text)
			.filter((text) => (seen.has(text) ? false : (seen.add(text), true)));
	}, [prereqLib, pickType]);

	// mutators
	const dirty = () => setBodyDirty(true);

	const setPrereqContent = (json: object) => {
		setPrereqDoc(json);
		dirty();
	};
	function onPickDragStart(e: ReactDragEvent<HTMLSpanElement>) {
		const text = pickText.trim();
		if (!text) return;
		e.dataTransfer.setData(PREREQ_MIME, JSON.stringify({ type: pickType.trim(), text }));
		e.dataTransfer.effectAllowed = 'copy';
	}
	function insertPick() {
		const text = pickText.trim();
		if (!text) return;
		prereqEditor.current?.insert({ type: 'prerequisiteRef', attrs: { ptype: pickType.trim(), text } });
		setPickText('');
	}
	function handlePrereqDrop(editor: Editor, event: DragEvent): boolean {
		const raw = event.dataTransfer?.getData(PREREQ_MIME);
		if (!raw) return false;
		let payload: { type?: string; text?: string };
		try {
			payload = JSON.parse(raw);
		} catch {
			return false;
		}
		const at = editor.view.posAtCoords({ left: event.clientX, top: event.clientY });
		const chain = editor.chain().focus();
		if (at) chain.setTextSelection(at.pos);
		chain
			.insertContent({ type: 'prerequisiteRef', attrs: { ptype: payload.type ?? '', text: payload.text ?? '' } })
			.insertContent(' ')
			.run();
		return true;
	}
	const addStep = () => {
		setSteps((s) => [
			...s,
			{ id: uid(), title: '', stepType: 'ACTION', scriptRefId: '', scriptId: '', scriptVersionNo: 0, scriptName: '', scriptLanguage: '', scriptRefCode: '', description: null }
		]);
		dirty();
	};
	const patchStep = (i: number, patch: Partial<StepDraft>) => {
		setSteps((s) => s.map((x, j) => (j === i ? { ...x, ...patch } : x)));
		dirty();
	};
	// Link (or clear) a repository script on a step; assigns a random ref code on first link.
	const linkScript = (i: number, sid: string) => {
		const picked = repoScripts.find((x) => x.id === sid);
		setSteps((arr) => {
			const taken = new Set<string>(Object.values(attRefs));
			arr.forEach((x, j) => {
				if (j !== i && x.scriptRefCode) taken.add(x.scriptRefCode);
			});
			return arr.map((x, j) => {
				if (j !== i) return x;
				if (!sid) return { ...x, scriptId: '', scriptName: '', scriptLanguage: '', scriptVersionNo: 0, scriptRefCode: '' };
				return {
					...x,
					scriptId: sid,
					scriptName: picked ? picked.name : '',
					scriptLanguage: picked ? picked.language : '',
					scriptVersionNo: picked ? picked.currentVersion : 0,
					scriptRefCode: x.scriptRefCode || randomRefId(taken)
				};
			});
		});
		dirty();
	};
	const removeStep = (i: number) => {
		setSteps((s) => s.filter((_, j) => j !== i));
		dirty();
	};
	const moveStep = (i: number, delta: number) => {
		setSteps((s) => {
			const j = i + delta;
			if (j < 0 || j >= s.length) return s;
			const copy = [...s];
			[copy[i], copy[j]] = [copy[j], copy[i]];
			return copy;
		});
		dirty();
	};

	async function saveBody() {
		try {
			// Persist ref ids only for attachments that still exist (drops stale entries).
			const attachmentRefs: Record<string, string> = {};
			for (const a of atts) if (attRefs[a.id]) attachmentRefs[a.id] = attRefs[a.id];
			const doc = {
				purpose,
				scope,
				prerequisites: prereqDoc,
				steps: steps
					.map((s) => {
						const run = s.stepType === 'RUN_SCRIPT';
						return {
							title: s.title.trim(),
							type: s.stepType,
							scriptRefId: run ? s.scriptRefId : '',
							scriptId: run ? s.scriptId : '',
							scriptVersionNo: run ? s.scriptVersionNo : 0,
							scriptName: run ? s.scriptName : '',
							scriptLanguage: run ? s.scriptLanguage : '',
							scriptRefCode: run ? s.scriptRefCode : '',
							description: s.description
						};
					})
					.filter((s) => s.title || hasContent(s.description) || s.scriptRefId || s.scriptId),
				attachmentRefs,
				templateId,
				// Snapshot the custom-field definitions + values so each saved version is self-contained
				// (immune to later schema edits) and renders identically in the PDF.
				customFields: customForm.map((f) => ({
					key: f.id,
					label: f.label,
					type: f.type,
					value:
						customValues[f.id] ??
						(f.type === 'CHECKBOX' ? false : f.type === 'RICHTEXT' ? null : '')
				}))
			};
			await saveBodyM.mutateAsync(JSON.stringify(doc));
			setBodyDirty(false);
			toast('Content saved', 'success');
		} catch (e) {
			toast((e as Error).message, 'danger');
		}
	}

	const setCustomVal = (fieldId: string, value: unknown) => {
		setCustomValues((m) => ({ ...m, [fieldId]: value }));
		dirty();
	};

	/** Renders the input for a non-checkbox custom field, driven by its type. */
	function renderCustomField(f: FormField) {
		const v = customValues[f.id];
		switch (f.type) {
			case 'RICHTEXT':
				return (
					<RichTextEditor
						value={v && typeof v === 'object' ? (v as object) : null}
						onChange={(json) => setCustomVal(f.id, json)}
						disabled={!editable}
					/>
				);
			case 'NUMBER':
				return (
					<input
						type="number"
						className="bp6-input"
						disabled={!editable}
						value={typeof v === 'number' || typeof v === 'string' ? String(v) : ''}
						onChange={(e) => setCustomVal(f.id, e.currentTarget.value)}
					/>
				);
			case 'DATE':
				return (
					<input
						type="date"
						className="bp6-input"
						disabled={!editable}
						value={typeof v === 'string' ? v : ''}
						onChange={(e) => setCustomVal(f.id, e.currentTarget.value)}
					/>
				);
			case 'SELECT': {
				const opts = f.options.split('\n').map((o) => o.trim()).filter(Boolean);
				return (
					<HTMLSelect
						disabled={!editable}
						value={typeof v === 'string' ? v : ''}
						onChange={(e) => setCustomVal(f.id, e.currentTarget.value)}
						options={[{ value: '', label: '— select —' }, ...opts.map((o) => ({ value: o, label: o }))]}
					/>
				);
			}
			default:
				return (
					<InputGroup
						fill
						disabled={!editable}
						value={typeof v === 'string' ? v : ''}
						onChange={(e) => setCustomVal(f.id, e.currentTarget.value)}
					/>
				);
		}
	}

	async function saveVersion() {
		try {
			await setVersionM.mutateAsync(versionValue.trim());
			setVersionDraft(null);
			toast('Version updated', 'success');
		} catch (e) {
			toast((e as Error).message, 'danger');
		}
	}

	async function saveDocNum() {
		try {
			await setDocNumM.mutateAsync(docNumValue.trim());
			setDocNumDraft(null);
			toast('Document number updated', 'success');
		} catch (e) {
			toast((e as Error).message, 'danger');
		}
	}

	async function saveTitle() {
		const next = (titleDraft ?? '').trim();
		if (!next || next === d?.title) {
			setTitleDraft(null);
			return;
		}
		try {
			await setTitleM.mutateAsync(next);
			setTitleDraft(null);
			toast('Name updated', 'success');
		} catch (e) {
			toast((e as Error).message, 'danger');
		}
	}

	async function saveConf(levelId: string | null) {
		try {
			await setConfM.mutateAsync(levelId);
			toast('Confidentiality updated', 'success');
		} catch (e) {
			toast((e as Error).message, 'danger');
		}
	}

	async function openCr() {
		try {
			const reason = REASONS[crReason];
			await openCrM.mutateAsync({
				title: reason.label,
				reason: crComment.trim() || reason.label,
				classification: reason.classification,
				trainingImpact: reason.trainingImpact
			});
			toast('Change request opened — approval workflow started', 'success');
			setShowCr(false);
			setCrReason(0);
			setCrComment('');
		} catch (e) {
			toast((e as Error).message, 'danger');
		}
	}

	async function onFile(e: ChangeEvent<HTMLInputElement>) {
		const f = e.target.files?.[0];
		if (!f) return;
		try {
			await uploadM.mutateAsync(f);
			toast(`Attached ${f.name}`, 'success');
		} catch (err) {
			toast((err as Error).message, 'danger');
		} finally {
			if (fileInput.current) fileInput.current.value = '';
		}
	}

	async function removeAtt(a: Att) {
		if (!window.confirm(`Delete "${a.filename}"? This cannot be undone.`)) return;
		try {
			await deleteAttM.mutateAsync(a.id);
			toast(`Removed ${a.filename}`, 'success');
		} catch (err) {
			toast((err as Error).message, 'danger');
		}
	}

	const reasonItems = useMemo(
		() => REASONS.map((r, i) => ({ value: String(i), label: r.label })),
		[]
	);

	if (detailQ.isError)
		return (
			<NonIdealState
				icon="error"
				title="Couldn't load this procedure"
				description={(detailQ.error as Error).message}
			/>
		);
	if (!d)
		return (
			<div className="center-screen">
				<Spinner />
			</div>
		);

	return (
		<>
			<Link to="/procedures" className="muted" style={{ fontSize: 13 }}>
				← Procedures
			</Link>
			<div style={{ display: 'flex', alignItems: 'center', gap: 12, margin: '8px 0 2px' }}>
				{titleDraft !== null ? (
					<>
						<InputGroup
							large
							autoFocus
							value={titleDraft}
							style={{ flex: 1, maxWidth: 560 }}
							onChange={(e) => setTitleDraft(e.currentTarget.value)}
							onKeyDown={(e) => {
								if (e.key === 'Enter') saveTitle();
								if (e.key === 'Escape') setTitleDraft(null);
							}}
						/>
						<Button
							intent="primary"
							loading={setTitleM.isPending}
							disabled={!titleDraft.trim() || titleDraft.trim() === d.title}
							onClick={saveTitle}
							text="Save"
						/>
						<Button minimal onClick={() => setTitleDraft(null)} text="Cancel" />
					</>
				) : (
					<>
						<h1 style={{ margin: 0, fontSize: 24, fontWeight: 700, letterSpacing: '-0.01em' }}>{d.title}</h1>
						{editable ? (
							<Button
								minimal
								small
								icon="edit"
								aria-label="Edit name"
								title="Edit name"
								onClick={() => setTitleDraft(d.title)}
							/>
						) : null}
						<Tag large intent={statusIntent(d.state)}>{d.state}</Tag>
					</>
				)}
			</div>
			<div style={{ display: 'flex', alignItems: 'center', gap: 10, marginTop: 2 }}>
				{isAdmin ? (
					<>
						<div style={{ width: 140 }}>
							<InputGroup
								small
								className="mono"
								value={docNumValue}
								onChange={(e) => setDocNumDraft(e.currentTarget.value)}
								placeholder="e.g. SOP_AB12CD"
							/>
						</div>
						<Button
							small
							minimal
							intent="primary"
							disabled={
								setDocNumM.isPending ||
								docNumDraft === null ||
								docNumValue.trim() === '' ||
								docNumValue.trim() === d.documentNumber
							}
							loading={setDocNumM.isPending}
							onClick={saveDocNum}
							text="Save"
						/>
					</>
				) : (
					<span className="mono muted">{d.documentNumber}</span>
				)}
				<span className="muted" style={{ fontSize: 13 }}>·</span>
				<span className="muted" style={{ fontSize: 13 }}>Version</span>
				{editable ? (
					<>
						<div style={{ width: 110 }}>
							<InputGroup
								small
								value={versionValue}
								onChange={(e) => setVersionDraft(e.currentTarget.value)}
								placeholder="e.g. 2.0"
							/>
						</div>
						<Button
							small
							minimal
							intent="primary"
							disabled={setVersionM.isPending || versionDraft === null || versionValue.trim() === currentLabel}
							loading={setVersionM.isPending}
							onClick={saveVersion}
							text="Save"
						/>
					</>
				) : (
					<Tag minimal>{currentLabel || '—'}</Tag>
				)}
			</div>

			<div style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 6 }}>
				<span className="muted" style={{ fontSize: 13 }}>Confidentiality</span>
				<HTMLSelect
					value={d.confidentialityLevelId ?? ''}
					disabled={setConfM.isPending}
					onChange={(e) => saveConf(e.currentTarget.value || null)}
					options={[
						{ label: 'Unclassified', value: '' },
						...confLevels.map((l) => ({ label: l.name, value: l.id }))
					]}
				/>
				{confLevels.length === 0 && (
					<span className="muted" style={{ fontSize: 12 }}>
						No levels yet — add them in Settings → Confidentiality levels.
					</span>
				)}
			</div>

			<div style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 8, marginTop: 14 }}>
				<Button intent="primary" icon="git-pull" disabled={d.state !== 'DRAFT'} onClick={() => setShowCr(true)} text="Open change request" />
				<AnchorButton icon="download" href={`/api/v1/procedures/${id}/export.pdf`} text="Download PDF" />
				{templates.length > 0 && (
					<HTMLSelect
						value={templateId}
						disabled={!editable}
						title="PDF export template"
						onChange={(e) => {
							setTemplateId(e.currentTarget.value);
							dirty();
						}}
						options={[
							{ value: '', label: 'Default style' },
							...templates.map((t) => ({ value: t.id, label: t.name }))
						]}
					/>
				)}
				<AnchorButton icon="compressed" href={`/api/v1/procedures/${id}/bundle.zip`} text="Download bundle (.zip)" />
				{d.state !== 'DRAFT' && (
					<span className="muted" style={{ alignSelf: 'center', fontSize: 13 }}>
						Editing a controlled procedure requires a new draft revision.
					</span>
				)}
			</div>
			{templates.length > 0 && editable && (
				<p className="muted" style={{ fontSize: 12, marginTop: 6 }}>
					Save content after changing the template for it to apply to the PDF export.
				</p>
			)}

			{/* ---- procedure document ---- */}
			<div style={{ display: 'flex', alignItems: 'center', gap: 12, margin: '26px 0 10px' }}>
				<div className="section-h" style={{ margin: 0 }}>Procedure document</div>
				{editable ? (
					<Button
						small
						intent="primary"
						style={{ marginLeft: 'auto' }}
						disabled={saveBodyM.isPending || !bodyDirty}
						loading={saveBodyM.isPending}
						onClick={saveBody}
						text={bodyDirty ? 'Save content' : 'Saved'}
					/>
				) : (
					<span className="muted" style={{ marginLeft: 'auto', fontSize: 13 }}>
						Read-only — open a draft revision to edit
					</span>
				)}
			</div>

			{!bodyReady ? (
				<Spinner />
			) : (
				<Card>
					<div className="doc-block">
						<div className="doc-eyebrow">
							<Tag round minimal intent="primary">01</Tag> Purpose
						</div>
						<p className="doc-hint">Why this procedure exists and what it sets out to achieve.</p>
						<RichTextEditor
							value={purpose}
							onChange={(json) => {
								setPurpose(json);
								dirty();
							}}
							disabled={!editable}
						/>
					</div>

					<div className="doc-block">
						<div className="doc-eyebrow">
							<Tag round minimal intent="primary">02</Tag> Scope
						</div>
						<p className="doc-hint">Who and what this applies to — roles, sites, systems, and any exclusions.</p>
						<RichTextEditor
							value={scope}
							onChange={(json) => {
								setScope(json);
								dirty();
							}}
							disabled={!editable}
						/>
					</div>

					<div className="doc-block">
						<div className="doc-eyebrow">
							<Tag round minimal intent="primary">03</Tag> Prerequisites
						</div>
						<p className="doc-hint">
							Write the prerequisites freely. Pick one from the library below and{' '}
							<strong>drag it into the text</strong> (or press Insert) to embed it as a reference.
						</p>
						{editable && prereqTypes.length === 0 && (
							<Callout intent="primary" icon="info-sign" style={{ marginBottom: 12 }}>
								No prerequisite types configured yet — add them in{' '}
								<Link to="/settings">Settings</Link>.
							</Callout>
						)}
						{editable && (
							<div className="prq-picker">
								<HTMLSelect
									value={pickType}
									onChange={(e) => setPickType(e.currentTarget.value)}
									options={[
										{ value: '', label: '— type —' },
										...prereqTypes.map((o) => ({ value: o, label: o }))
									]}
								/>
								<div style={{ flex: 1 }}>
									<Combobox
										fill
										items={pickItems}
										value={pickText}
										onChange={setPickText}
										placeholder={
											pickType
												? `Pick a ${pickType.toLowerCase()} or type one…`
												: 'Pick a prerequisite or type one…'
										}
									/>
								</div>
								<span
									className="prq-drag"
									draggable={!!pickText.trim()}
									aria-disabled={!pickText.trim()}
									onDragStart={onPickDragStart}
									title="Drag into the prerequisites text"
								>
									<Icon icon="drag-handle-vertical" size={12} />
									{pickText.trim() ? (pickType ? `${pickType}: ${pickText.trim()}` : pickText.trim()) : 'nothing picked'}
								</span>
								<Button icon="insert" text="Insert" disabled={!pickText.trim()} onClick={insertPick} />
							</div>
						)}
						<RichTextEditor
							ref={prereqEditor}
							value={prereqDoc}
							onChange={setPrereqContent}
							disabled={!editable}
							extraExtensions={PREREQ_EXTENSIONS}
							onDrop={handlePrereqDrop}
						/>
					</div>

					<div className="doc-block">
						<div className="doc-eyebrow">
							<Tag round minimal intent="primary">04</Tag> Steps
						</div>
						<p className="doc-hint">
							Each step has a type, a title, and a rich-text description. Pick{' '}
							<strong>Run script</strong> to link a script attachment; use{' '}
							<strong>Insert reference</strong> in the description to cite an attachment (e.g. [A1]).
						</p>
						{steps.length === 0 && <p className="muted">No steps yet.</p>}
						{steps.map((s, i) => {
							const tt = stepType(s.stepType);
							return (
								<Card key={s.id} compact className="step-card">
									<div className="step-head">
										<Tag round intent={tt.intent === 'none' ? 'primary' : tt.intent}>{i + 1}</Tag>
										{editable ? (
											<HTMLSelect
												value={s.stepType}
												onChange={(e) => patchStep(i, { stepType: e.currentTarget.value })}
												options={STEP_TYPES.map((t) => ({ value: t.value, label: t.label }))}
											/>
										) : (
											<Tag minimal intent={tt.intent} icon={tt.icon}>{tt.label}</Tag>
										)}
										<div className="grow">
											<InputGroup
												fill
												large
												leftIcon={tt.icon}
												disabled={!editable}
												value={s.title}
												onChange={(e) => patchStep(i, { title: e.currentTarget.value })}
												placeholder="Step title"
											/>
										</div>
										{editable && (
											<div className="step-actions">
												<Button minimal small icon="chevron-up" disabled={i === 0} onClick={() => moveStep(i, -1)} aria-label="Move up" />
												<Button minimal small icon="chevron-down" disabled={i === steps.length - 1} onClick={() => moveStep(i, 1)} aria-label="Move down" />
												<Button minimal small intent="danger" icon="trash" onClick={() => removeStep(i)} aria-label="Remove step" />
											</div>
										)}
									</div>
									{s.stepType === 'RUN_SCRIPT' && (
										<div style={{ display: 'flex', flexWrap: 'wrap', gap: 10, marginBottom: 8 }}>
											<FormGroup label="Repository script" inline style={{ marginBottom: 0 }}>
												<div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
													<HTMLSelect
														disabled={!editable}
														value={s.scriptId}
														onChange={(e) => linkScript(i, e.currentTarget.value)}
														options={[
															{ value: '', label: repoScripts.length ? '— none —' : 'No scripts yet' },
															...repoScripts.map((x) => ({ value: x.id, label: x.name }))
														]}
													/>
													{s.scriptId && s.scriptRefCode && (
														<Tag minimal className="prereq-ref">{s.scriptRefCode}</Tag>
													)}
													{s.scriptId && (
														<Button
															small
															minimal
															icon="eye-open"
															title="Browse script content"
															onClick={() =>
																setViewScript({
																	id: s.scriptId,
																	name: s.scriptName || 'Script',
																	version: s.scriptVersionNo || 1,
																	code: s.scriptRefCode
																})
															}
														/>
													)}
												</div>
											</FormGroup>
											{s.scriptId &&
												(() => {
													const picked = repoScripts.find((x) => x.id === s.scriptId);
													const max = picked ? picked.currentVersion : s.scriptVersionNo;
													return (
														<FormGroup label="Version" inline style={{ marginBottom: 0 }}>
															<HTMLSelect
																disabled={!editable}
																value={String(s.scriptVersionNo || max)}
																onChange={(e) => patchStep(i, { scriptVersionNo: Number(e.currentTarget.value) })}
																options={Array.from({ length: Math.max(max, 1) }, (_, k) => {
																	const no = max - k;
																	return { value: String(no), label: `v${no}${no === max ? ' (latest)' : ''}` };
																})}
															/>
														</FormGroup>
													);
												})()}
											<FormGroup label="or attachment" inline style={{ marginBottom: 0 }}>
												<HTMLSelect
													disabled={!editable}
													value={s.scriptRefId}
													onChange={(e) => patchStep(i, { scriptRefId: e.currentTarget.value })}
													options={[
														{ value: '', label: stepRefs.length ? '— none —' : 'No attachments' },
														...stepRefs.map((r) => ({ value: r.refId, label: `[${r.refId}] ${r.filename}` }))
													]}
												/>
											</FormGroup>
										</div>
									)}
									<RichTextEditor
										value={toEditorContent(s.description)}
										onChange={(json) => patchStep(i, { description: json })}
										disabled={!editable}
										refs={stepRefs}
									/>
								</Card>
							);
						})}
						{editable && (
							<Button minimal intent="primary" icon="add" onClick={addStep} text="Add step" style={{ marginTop: 4 }} />
						)}
					</div>

					{/* ---- custom elements (tenant-defined in Settings → Procedure form) ---- */}
					{customForm.map((f, i) => (
						<div className="doc-block" key={f.id}>
							<div className="doc-eyebrow">
								<Tag round minimal intent="primary">{String(5 + i).padStart(2, '0')}</Tag> {f.label}
								{f.required ? (
									<span className="doc-hint" style={{ margin: 0 }}>(required)</span>
								) : null}
							</div>
							{f.type === 'CHECKBOX' ? (
								<Checkbox
									disabled={!editable}
									checked={customValues[f.id] === true}
									onChange={(e) => setCustomVal(f.id, e.currentTarget.checked)}
									label="Yes"
								/>
							) : (
								renderCustomField(f)
							)}
						</div>
					))}
				</Card>
			)}

			{/* ---- attachments ---- */}
			<div style={{ display: 'flex', alignItems: 'center', gap: 12, margin: '26px 0 10px' }}>
				<div className="section-h" style={{ margin: 0 }}>Attachments ({atts.length})</div>
				<input ref={fileInput} type="file" style={{ display: 'none' }} onChange={onFile} />
				<Button
					small
					intent="primary"
					icon="paperclip"
					style={{ marginLeft: 'auto' }}
					loading={uploadM.isPending}
					onClick={() => fileInput.current?.click()}
					text="Attach file"
				/>
			</div>
			<HTMLTable striped className="full">
				<thead>
					<tr>
						<th>Ref</th>
						<th>File</th>
						<th>Type</th>
						<th>Size</th>
						<th>Uploaded</th>
						<th className="right">Actions</th>
					</tr>
				</thead>
				<tbody>
					{atts.length === 0 ? (
						<tr>
							<td colSpan={6} className="muted">
								No attachments. Bundle any file with this SOP, then cite it from a step with [A1].
							</td>
						</tr>
					) : (
						atts.map((a) => (
							<tr key={a.id}>
								<td>{attRefs[a.id] ? <Tag minimal className="att-ref">{attRefs[a.id]}</Tag> : '—'}</td>
								<td style={{ fontWeight: 500 }}>{a.filename}</td>
								<td className="mono muted">{a.mime}</td>
								<td className="mono">{fmtSize(a.size)}</td>
								<td className="mono muted">{dt(a.uploadedAt)}</td>
								<td className="right nowrap">
									<AnchorButton minimal small icon="download" href={`/api/v1/procedures/${id}/attachments/${a.id}/download`} text="Download" />{' '}
									<Button minimal small intent="danger" icon="trash" onClick={() => removeAtt(a)} text="Delete" />
								</td>
							</tr>
						))
					)}
				</tbody>
			</HTMLTable>

			{/* ---- change requests ---- */}
			<div className="section-h">Change requests</div>
			<HTMLTable striped className="full">
				<thead>
					<tr>
						<th>Change</th>
						<th>Class</th>
						<th>Training</th>
						<th>Status</th>
						<th>Opened</th>
					</tr>
				</thead>
				<tbody>
					{(cc?.changeRequests ?? []).length === 0 ? (
						<tr>
							<td colSpan={5} className="muted">
								No change requests.
							</td>
						</tr>
					) : (
						cc!.changeRequests.map((c) => (
							<tr key={c.id}>
								<td>{c.title}</td>
								<td className="mono">{c.classification}</td>
								<td>{c.trainingImpact ? 'yes' : 'no'}</td>
								<td>
									<Tag intent={statusIntent(c.status)} minimal>
										{c.status}
									</Tag>
								</td>
								<td className="mono muted">{dt(c.createdAt)}</td>
							</tr>
						))
					)}
				</tbody>
			</HTMLTable>

			{/* ---- approval tasks ---- */}
			<div className="section-h">Approval tasks</div>
			<HTMLTable striped className="full">
				<thead>
					<tr>
						<th>Stage</th>
						<th>Role</th>
						<th>Sign as</th>
						<th>Status</th>
					</tr>
				</thead>
				<tbody>
					{(cc?.tasks ?? []).length === 0 ? (
						<tr>
							<td colSpan={4} className="muted">
								No approval tasks yet.
							</td>
						</tr>
					) : (
						cc!.tasks.map((t) => (
							<tr key={t.id}>
								<td>{t.stage}</td>
								<td className="mono">{t.role}</td>
								<td>
									<Tag intent="primary" minimal>
										{t.meaning}
									</Tag>
								</td>
								<td>
									<Tag intent={statusIntent(t.status)} minimal>
										{t.status}
									</Tag>
								</td>
							</tr>
						))
					)}
				</tbody>
			</HTMLTable>

			{/* ---- version history ---- */}
			<div className="section-h">Version history</div>
			<HTMLTable striped className="full">
				<thead>
					<tr>
						<th>Version</th>
						<th>Created</th>
						<th>Change request</th>
					</tr>
				</thead>
				<tbody>
					{d.versions.length === 0 ? (
						<tr>
							<td colSpan={3} className="muted">
								No versions.
							</td>
						</tr>
					) : (
						d.versions.map((v) => (
							<tr key={v.id}>
								<td>
									<Tag minimal>{v.label}</Tag>
								</td>
								<td className="mono muted">{dt(v.createdAt)}</td>
								<td className="mono">{v.changeRequestId ?? '—'}</td>
							</tr>
						))
					)}
				</tbody>
			</HTMLTable>

			{/* ---- change request dialog ---- */}
			<Dialog isOpen={showCr} onClose={() => setShowCr(false)} title="Open change request" icon="git-pull">
				<DialogBody>
					<p className="muted" style={{ marginTop: 0 }}>
						Submits the draft for review and starts the approval workflow.
					</p>
					<FormGroup label="Reason for change">
						<HTMLSelect
							fill
							options={reasonItems}
							value={String(crReason)}
							onChange={(e) => setCrReason(Number(e.currentTarget.value))}
						/>
					</FormGroup>
					<Callout intent={REASONS[crReason].trainingImpact ? 'warning' : 'primary'} style={{ marginBottom: 12 }}>
						Routed as <strong>{REASONS[crReason].classification}</strong>
						{REASONS[crReason].trainingImpact ? ' · requires re-training' : ''}.
					</Callout>
					<FormGroup label="Comment (optional)">
						<TextArea
							fill
							rows={3}
							value={crComment}
							onChange={(e) => setCrComment(e.currentTarget.value)}
							placeholder="Add any detail for reviewers…"
						/>
					</FormGroup>
				</DialogBody>
				<DialogFooter
					actions={
						<>
							<Button onClick={() => setShowCr(false)} text="Cancel" />
							<Button intent="primary" loading={openCrM.isPending} onClick={openCr} text="Open & start workflow" />
						</>
					}
				/>
			</Dialog>

			{viewScript && (
				<ScriptViewer
					scriptId={viewScript.id}
					scriptName={viewScript.name}
					initialVersion={viewScript.version}
					refCode={viewScript.code}
					onClose={() => setViewScript(null)}
				/>
			)}
		</>
	);
}
