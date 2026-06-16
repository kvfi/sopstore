import { useState } from 'react';
import { Button, Card, Checkbox, HTMLSelect, InputGroup, Spinner, TextArea } from '@blueprintjs/core';
import { toast } from '../../lib/toaster';
import {
	type FormField,
	useProcedureForm,
	useCreateField,
	useUpdateField,
	useDeleteField
} from '../../lib/queries';

const FIELD_TYPES = [
	{ value: 'TEXT', label: 'Short text' },
	{ value: 'RICHTEXT', label: 'Rich text' },
	{ value: 'NUMBER', label: 'Number' },
	{ value: 'DATE', label: 'Date' },
	{ value: 'SELECT', label: 'Dropdown' },
	{ value: 'CHECKBOX', label: 'Checkbox' }
];

export default function ProcedureForm() {
	const { data, isLoading } = useProcedureForm();
	const elements = data?.fields ?? [];
	const createField = useCreateField();

	const [newLabel, setNewLabel] = useState('');
	const [newType, setNewType] = useState('TEXT');

	async function add() {
		const label = newLabel.trim();
		if (!label) return;
		try {
			await createField.mutateAsync({ label, type: newType });
			setNewLabel('');
			setNewType('TEXT');
			toast(`Added "${label}"`, 'success');
		} catch (e) {
			toast((e as Error).message, 'danger');
		}
	}

	return (
		<>
			<p className="page-sub">
				Custom elements added to every procedure's form, shown <strong>after</strong> the built-in
				Purpose, Scope, Prerequisites, and Steps. Each is a labelled field authors fill in per
				procedure; it renders as its own section in the PDF export. Deleting one leaves existing
				procedures untouched (they keep their saved values).
			</p>

			{isLoading ? (
				<div className="center-screen">
					<Spinner />
				</div>
			) : (
				<div>
					{elements.length === 0 && (
						<p className="muted">No custom elements yet — add your first below.</p>
					)}
					{elements.map((f, i) => (
						<ElementRow
							key={f.id}
							field={f}
							isFirst={i === 0}
							isLast={i === elements.length - 1}
							siblings={elements}
							index={i}
						/>
					))}

					<Card style={{ display: 'flex', gap: 8, alignItems: 'center', marginTop: 14 }}>
						<div style={{ flex: 1 }}>
							<InputGroup
								fill
								placeholder="New element label (e.g. Required PPE)"
								value={newLabel}
								onChange={(e) => setNewLabel(e.currentTarget.value)}
								onKeyDown={(e) => {
									if (e.key === 'Enter') add();
								}}
							/>
						</div>
						<HTMLSelect value={newType} options={FIELD_TYPES} onChange={(e) => setNewType(e.currentTarget.value)} />
						<Button intent="primary" icon="add" loading={createField.isPending} disabled={!newLabel.trim()} onClick={add} text="Add element" />
					</Card>
				</div>
			)}
		</>
	);
}

function ElementRow({
	field,
	isFirst,
	isLast,
	siblings,
	index
}: {
	field: FormField;
	isFirst: boolean;
	isLast: boolean;
	siblings: FormField[];
	index: number;
}) {
	const update = useUpdateField();
	const del = useDeleteField();
	const [label, setLabel] = useState(field.label);
	const [type, setType] = useState(field.type);
	const [options, setOptions] = useState(field.options);
	const [required, setRequired] = useState(field.required);

	const dirty =
		label.trim() !== field.label ||
		type !== field.type ||
		options !== field.options ||
		required !== field.required;

	async function save() {
		if (!label.trim()) return;
		try {
			await update.mutateAsync({ id: field.id, label: label.trim(), type, options, required });
			toast('Saved', 'success');
		} catch (e) {
			toast((e as Error).message, 'danger');
		}
	}

	async function move(dir: -1 | 1) {
		const j = index + dir;
		if (j < 0 || j >= siblings.length) return;
		const other = siblings[j];
		try {
			await Promise.all([
				update.mutateAsync({ id: field.id, sortOrder: other.sortOrder }),
				update.mutateAsync({ id: other.id, sortOrder: field.sortOrder })
			]);
		} catch (e) {
			toast((e as Error).message, 'danger');
		}
	}

	async function remove() {
		if (!window.confirm(`Delete "${field.label}"? Existing procedures keep their saved value.`)) return;
		try {
			await del.mutateAsync(field.id);
			toast('Deleted', 'success');
		} catch (e) {
			toast((e as Error).message, 'danger');
		}
	}

	return (
		<Card style={{ marginBottom: 12 }}>
			<div style={{ display: 'flex', gap: 8, alignItems: 'flex-start' }}>
				<div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: 6 }}>
					<InputGroup fill value={label} onChange={(e) => setLabel(e.currentTarget.value)} placeholder="Element label" />
					{type === 'SELECT' && (
						<TextArea
							fill
							value={options}
							onChange={(e) => setOptions(e.currentTarget.value)}
							placeholder="One option per line"
							style={{ minHeight: 60, fontSize: 13 }}
						/>
					)}
				</div>
				<HTMLSelect value={type} options={FIELD_TYPES} onChange={(e) => setType(e.currentTarget.value)} />
				<Checkbox checked={required} label="Required" style={{ marginTop: 6, whiteSpace: 'nowrap' }} onChange={(e) => setRequired(e.currentTarget.checked)} />
				<Button small minimal intent="primary" disabled={!dirty} onClick={save} text="Save" />
				<Button minimal small icon="chevron-up" disabled={isFirst} onClick={() => move(-1)} aria-label="Move up" />
				<Button minimal small icon="chevron-down" disabled={isLast} onClick={() => move(1)} aria-label="Move down" />
				<Button minimal small intent="danger" icon="trash" onClick={remove} aria-label="Delete" />
			</div>
		</Card>
	);
}
