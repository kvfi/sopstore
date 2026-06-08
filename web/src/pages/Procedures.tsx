import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
	Button,
	HTMLTable,
	HTMLSelect,
	InputGroup,
	Tag,
	Dialog,
	DialogBody,
	DialogFooter,
	FormGroup
} from '@blueprintjs/core';
import { toast } from '../lib/toaster';
import { statusIntent } from '../lib/ui';
import Combobox from '../components/Combobox';
import {
	type Proc,
	usePrereqLib,
	usePrereqTypes,
	useProcedures,
	useCreateProcedure,
	useCreatePrereqType,
	useCreatePrereq
} from '../lib/queries';

const STATE_ITEMS = [
	{ value: 'ALL', label: 'All statuses' },
	{ value: 'DRAFT', label: 'Draft' },
	{ value: 'IN_REVIEW', label: 'In review' },
	{ value: 'APPROVED', label: 'Approved' },
	{ value: 'EFFECTIVE', label: 'Effective' },
	{ value: 'UNDER_REVIEW', label: 'Under review' },
	{ value: 'RETIRED', label: 'Retired' }
];

const TYPE_ITEMS = [
	{ value: 'SOP', label: 'SOP' },
	{ value: 'POLICY', label: 'Policy' },
	{ value: 'WORK_INSTRUCTION', label: 'Work instruction' },
	{ value: 'FORM', label: 'Form' },
	{ value: 'JOB_AID', label: 'Job aid' }
];

type SortKey = keyof Proc;
type PrereqLine = { type: string; text: string };

const prereqLabel = (p: { type: string; text: string }) => (p.type ? `${p.type}: ${p.text}` : p.text);

export default function Procedures() {
	const navigate = useNavigate();
	const [state, setState] = useState('ALL');
	const { data: procData, isLoading: loading } = useProcedures(state);
	const rows = useMemo(() => procData ?? [], [procData]);
	const [q, setQ] = useState('');
	const [sortKey, setSortKey] = useState<SortKey>('documentNumber');
	const [sortDir, setSortDir] = useState<1 | -1>(1);

	// create dialog
	const [showNew, setShowNew] = useState(false);
	const [docType, setDocType] = useState('SOP');
	const [title, setTitle] = useState('');

	// prerequisites for the create dialog: pick a type, then pick-or-add the requirement
	const prereqLib = usePrereqLib().data ?? [];
	const prereqTypes = (usePrereqTypes().data ?? []).map((t) => t.name);
	const [selectedPrereqs, setSelectedPrereqs] = useState<PrereqLine[]>([]);
	const [reqType, setReqType] = useState('');
	const [reqText, setReqText] = useState('');

	const createProcedure = useCreateProcedure();
	const createType = useCreatePrereqType();
	const createPrereq = useCreatePrereq();
	const saving = createProcedure.isPending;

	// Library requirements already known for the chosen type — drives the requirement combobox.
	const itemsForType = useMemo(() => {
		const t = reqType.trim().toLowerCase();
		const seen = new Set<string>();
		return prereqLib
			.filter((p) => (t ? p.type.toLowerCase() === t : true))
			.map((p) => p.text)
			.filter((text) => (seen.has(text) ? false : (seen.add(text), true)));
	}, [prereqLib, reqType]);

	/**
	 * Grow the reusable library so this type+requirement is pre-populated next time.
	 * Best-effort: library writes need TENANT_ADMIN, so authors get a 403 — the procedure
	 * keeps its own snapshot regardless, so we just skip persistence silently. 409 = it
	 * already exists, which is also fine. The mutations invalidate the library queries.
	 */
	async function persistToLibrary(type: string, text: string) {
		try {
			if (type && !prereqTypes.some((t) => t.toLowerCase() === type.toLowerCase())) {
				await createType.mutateAsync(type);
			}
			const exists = prereqLib.some(
				(p) =>
					p.type.toLowerCase() === type.toLowerCase() &&
					p.text.toLowerCase() === text.toLowerCase()
			);
			if (!exists) {
				await createPrereq.mutateAsync({ type, text });
			}
		} catch {
			/* not permitted or already exists — the procedure's snapshot still applies */
		}
	}

	function addRequirement() {
		const text = reqText.trim();
		if (!text) return;
		const type = reqType.trim();
		setSelectedPrereqs((s) => [...s, { type, text }]);
		void persistToLibrary(type, text);
		setReqText(''); // keep the type so several requirements of one type add quickly
	}
	function removeSelected(i: number) {
		setSelectedPrereqs((s) => s.filter((_, j) => j !== i));
	}
	function closeNew() {
		setShowNew(false);
		setTitle('');
		setDocType('SOP');
		setSelectedPrereqs([]);
		setReqType('');
		setReqText('');
	}

	function sortBy(k: SortKey) {
		if (k === sortKey) setSortDir((d) => (d * -1) as 1 | -1);
		else {
			setSortKey(k);
			setSortDir(1);
		}
	}

	const view = useMemo(() => {
		const t = q.trim().toLowerCase();
		const filtered = rows.filter(
			(r) =>
				!t ||
				r.title.toLowerCase().includes(t) ||
				r.documentNumber.toLowerCase().includes(t) ||
				r.owner.toLowerCase().includes(t)
		);
		return [...filtered].sort((a, b) => {
			const x = (a[sortKey] ?? '') as string;
			const y = (b[sortKey] ?? '') as string;
			return String(x).localeCompare(String(y)) * sortDir;
		});
	}, [rows, q, sortKey, sortDir]);

	async function create() {
		try {
			const p = await createProcedure.mutateAsync({
				title,
				type: docType,
				prerequisites: selectedPrereqs
			});
			toast(`Created ${p.documentNumber}`, 'success');
			closeNew();
			navigate(`/procedures/${p.id}`);
		} catch (e) {
			toast((e as Error).message, 'danger');
		}
	}

	const columns: { key: SortKey; label: string }[] = [
		{ key: 'documentNumber', label: 'Document' },
		{ key: 'title', label: 'Title' },
		{ key: 'type', label: 'Type' },
		{ key: 'owner', label: 'Owner' },
		{ key: 'state', label: 'Status' },
		{ key: 'effectiveDate', label: 'Effective' },
		{ key: 'nextReviewDate', label: 'Next review' }
	];

	return (
		<>
			<div className="toolbar">
				<div style={{ maxWidth: 320, flex: '0 1 320px' }}>
					<InputGroup
						leftIcon="search"
						placeholder="Search title, document #, owner…"
						value={q}
						onChange={(e) => setQ(e.currentTarget.value)}
					/>
				</div>
				<HTMLSelect
					options={STATE_ITEMS}
					value={state}
					onChange={(e) => setState(e.currentTarget.value)}
				/>
				<span className="muted" style={{ fontSize: 12 }}>
					{view.length} of {rows.length}
				</span>
				<div className="grow" />
				<Button intent="primary" icon="add" onClick={() => setShowNew(true)} text="New procedure" />
			</div>

			<HTMLTable striped interactive className="full">
				<thead>
					<tr>
						{columns.map((c) => (
							<th key={c.key} className="click" onClick={() => sortBy(c.key)}>
								{c.label}
								{sortKey === c.key ? (sortDir === 1 ? ' ▲' : ' ▼') : ''}
							</th>
						))}
					</tr>
				</thead>
				<tbody>
					{loading ? (
						<tr>
							<td colSpan={columns.length} className="muted">
								Loading…
							</td>
						</tr>
					) : view.length === 0 ? (
						<tr>
							<td colSpan={columns.length} className="muted">
								No procedures match.
							</td>
						</tr>
					) : (
						view.map((p) => (
							<tr key={p.id} className="click" onClick={() => navigate(`/procedures/${p.id}`)}>
								<td className="mono">{p.documentNumber}</td>
								<td style={{ fontWeight: 500 }}>{p.title}</td>
								<td className="mono">{p.type}</td>
								<td>{p.owner}</td>
								<td>
									<Tag intent={statusIntent(p.state)} minimal>
										{p.state}
									</Tag>
								</td>
								<td className="mono">{p.effectiveDate ?? '—'}</td>
								<td className="mono">{p.nextReviewDate ?? '—'}</td>
							</tr>
						))
					)}
				</tbody>
			</HTMLTable>

			<Dialog isOpen={showNew} onClose={closeNew} title="New procedure" icon="document">
				<DialogBody>
					<p className="muted" style={{ marginTop: 0 }}>
						Creates a draft you can author and submit for approval.
					</p>
					<FormGroup label="Type">
						<HTMLSelect
							fill
							options={TYPE_ITEMS}
							value={docType}
							onChange={(e) => setDocType(e.currentTarget.value)}
						/>
					</FormGroup>
					<FormGroup
						label="Title"
						helperText="A document number is assigned automatically (e.g. SOP-0001)."
					>
						<InputGroup
							value={title}
							onChange={(e) => setTitle(e.currentTarget.value)}
							placeholder="Procedure title"
							fill
						/>
					</FormGroup>

					<FormGroup label="Prerequisites" labelInfo="(optional)">
						{selectedPrereqs.length > 0 && (
							<div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, marginBottom: 10 }}>
								{selectedPrereqs.map((p, i) => (
									<Tag key={i} minimal onRemove={() => removeSelected(i)}>
										{prereqLabel(p)}
									</Tag>
								))}
							</div>
						)}
						<div style={{ display: 'flex', gap: 6, alignItems: 'flex-start' }}>
							<div style={{ flex: '0 0 38%' }}>
								<Combobox
									fill
									items={prereqTypes}
									value={reqType}
									onChange={setReqType}
									placeholder="Type (e.g. Software)"
								/>
							</div>
							<div style={{ flex: 1 }}>
								<Combobox
									fill
									items={itemsForType}
									value={reqText}
									onChange={setReqText}
									placeholder={
										reqType.trim()
											? `Select or add ${reqType.trim().toLowerCase()}…`
											: 'Requirement — select or add…'
									}
								/>
							</div>
							<Button
								icon="add"
								disabled={!reqText.trim()}
								onClick={addRequirement}
								aria-label="Add requirement"
							/>
						</div>
						<div className="muted" style={{ fontSize: 12, marginTop: 6 }}>
							Pick a type, then choose an existing requirement or type a new one.
						</div>
					</FormGroup>
				</DialogBody>
				<DialogFooter
					actions={
						<>
							<Button onClick={closeNew} text="Cancel" />
							<Button
								intent="primary"
								onClick={create}
								disabled={!title || saving}
								loading={saving}
								text="Create"
							/>
						</>
					}
				/>
			</Dialog>
		</>
	);
}
