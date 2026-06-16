import { useState } from 'react';
import { Button, Callout, FormGroup, NumericInput, Spinner } from '@blueprintjs/core';
import Panel from '../../components/Panel';
import { ApiError } from '../../lib/api';
import { toast } from '../../lib/toaster';
import { useSessionPolicy, useSaveSessionPolicy } from '../../lib/queries';

export default function SessionPolicy() {
	const { data, isLoading } = useSessionPolicy();
	const save = useSaveSessionPolicy();

	const [idleMin, setIdleMin] = useState<number | null>(null);
	const [absHours, setAbsHours] = useState<number | null>(null);

	const savedIdleMin = data ? Math.round(data.idleTimeoutSeconds / 60) : 30;
	const savedAbsHours = data ? Math.round(data.absoluteTimeoutSeconds / 3600) : 12;
	const idle = idleMin ?? savedIdleMin;
	const abs = absHours ?? savedAbsHours;

	const tooShort = abs * 60 < idle; // absolute must be at least the idle window
	const dirty = idle !== savedIdleMin || abs !== savedAbsHours;

	async function onSave() {
		try {
			await save.mutateAsync({ idleTimeoutSeconds: idle * 60, absoluteTimeoutSeconds: abs * 3600 });
			setIdleMin(null);
			setAbsHours(null);
			toast('Session policy saved', 'success');
		} catch (e) {
			const msg =
				e instanceof ApiError && e.status === 400
					? 'Those values are out of the allowed range.'
					: (e as Error).message;
			toast(msg, 'danger');
		}
	}

	if (isLoading) {
		return (
			<div className="center-screen">
				<Spinner />
			</div>
		);
	}

	return (
		<>
			<p className="page-sub">
				How long a signed-in session lasts. The <strong>idle timeout</strong> signs a user out
				after a period of inactivity; the <strong>absolute timeout</strong> caps the total session
				length regardless of activity. Changes apply to sessions started after saving.
			</p>

			<Panel title="Session timeouts">
				<div style={{ display: 'flex', gap: 32, flexWrap: 'wrap' }}>
					<FormGroup
						label="Idle timeout"
						labelInfo="(minutes)"
						helperText="Signed out after this long with no activity. 1 min – 24 h."
					>
						<NumericInput
							min={1}
							max={1440}
							value={idle}
							onValueChange={(v) => setIdleMin(Number.isNaN(v) ? 1 : v)}
							style={{ width: 96 }}
						/>
					</FormGroup>

					<FormGroup
						label="Absolute timeout"
						labelInfo="(hours)"
						helperText="Maximum session length, even when active. Up to 720 h (30 days)."
					>
						<NumericInput
							min={1}
							max={720}
							value={abs}
							onValueChange={(v) => setAbsHours(Number.isNaN(v) ? 1 : v)}
							style={{ width: 96 }}
						/>
					</FormGroup>
				</div>

				{tooShort ? (
					<Callout intent="warning" icon="warning-sign" style={{ marginBottom: 14 }}>
						The absolute timeout must be at least as long as the idle timeout.
					</Callout>
				) : null}

				<Button
					intent="primary"
					icon="floppy-disk"
					loading={save.isPending}
					disabled={!dirty || tooShort}
					onClick={onSave}
					text="Save"
				/>
			</Panel>
		</>
	);
}
