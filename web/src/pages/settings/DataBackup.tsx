import { useRef, useState } from 'react';
import { Button, Callout, InputGroup } from '@blueprintjs/core';
import Panel from '../../components/Panel';
import { toast } from '../../lib/toaster';
import { ApiError } from '../../lib/api';
import { useImportTenantData } from '../../lib/queries';

export default function DataBackup() {
	const importM = useImportTenantData();
	const fileInput = useRef<HTMLInputElement>(null);
	const [file, setFile] = useState<File | null>(null);
	const [confirm, setConfirm] = useState('');
	const [exporting, setExporting] = useState(false);

	async function onExport() {
		setExporting(true);
		try {
			const res = await fetch('/api/v1/tenant-data/export', { credentials: 'same-origin' });
			if (!res.ok) throw new Error(res.status === 403 ? 'Administrators only.' : `Export failed (${res.status})`);
			const blob = await res.blob();
			const url = URL.createObjectURL(blob);
			const a = document.createElement('a');
			const stamp = new Date().toISOString().slice(0, 10);
			a.href = url;
			a.download = `tenant-data-${stamp}.json`;
			a.click();
			URL.revokeObjectURL(url);
			toast('Export downloaded', 'success');
		} catch (e) {
			toast((e as Error).message, 'danger');
		} finally {
			setExporting(false);
		}
	}

	async function onImport() {
		if (!file || confirm !== 'REPLACE') return;
		if (!window.confirm('This permanently REPLACES this tenant’s data with the uploaded bundle. Continue?'))
			return;
		try {
			const r = await importM.mutateAsync(file);
			toast(`Imported ${r.rows} rows across ${r.tables} tables`, 'success');
			setFile(null);
			setConfirm('');
			if (fileInput.current) fileInput.current.value = '';
		} catch (e) {
			const msg =
				e instanceof ApiError && e.status === 400
					? 'That file is not a valid export bundle.'
					: `Import failed: ${(e as Error).message}`;
			toast(msg, 'danger');
		}
	}

	return (
		<>
			<p className="page-sub">
				Export this tenant&rsquo;s data as a JSON bundle, or restore it from a previous export.
				Covers procedures, runs, and configuration. <strong>Excludes</strong> the audit trail,
				user accounts, and attachment file contents (those live in object storage).
			</p>

			<div style={{ display: 'grid', gap: 16, maxWidth: 760 }}>
				<Panel title="Export" icon="export">
					<p style={{ marginTop: 0, color: 'var(--text-muted)', fontSize: 'var(--fs-sm)' }}>
						Downloads a JSON snapshot of this tenant&rsquo;s data. Read-only and safe.
					</p>
					<Button intent="primary" icon="download" loading={exporting} onClick={onExport} text="Export data" />
				</Panel>

				<Panel title="Import (restore)" icon="import">
					<Callout intent="danger" icon="warning-sign" style={{ marginBottom: 14 }}>
						Importing <strong>permanently replaces</strong> this tenant&rsquo;s current data with
						the bundle&rsquo;s contents. It runs in one transaction (all-or-nothing) and works best
						restoring into the same deployment the export came from.
					</Callout>

					<input
						ref={fileInput}
						type="file"
						accept="application/json,.json"
						onChange={(e) => setFile(e.currentTarget.files?.[0] ?? null)}
						style={{ marginBottom: 12 }}
					/>

					<div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
						<div style={{ width: 200 }}>
							<InputGroup
								placeholder="Type REPLACE to confirm"
								value={confirm}
								onChange={(e) => setConfirm(e.currentTarget.value)}
							/>
						</div>
						<Button
							intent="danger"
							icon="import"
							loading={importM.isPending}
							disabled={!file || confirm !== 'REPLACE'}
							onClick={onImport}
							text="Import & replace"
						/>
					</div>
				</Panel>
			</div>
		</>
	);
}
