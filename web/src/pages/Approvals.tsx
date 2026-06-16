import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import {
	Button,
	HTMLTable,
	InputGroup,
	TextArea,
	Tag,
	Dialog,
	DialogBody,
	DialogFooter,
	FormGroup
} from '@blueprintjs/core';
import { toast } from '../lib/toaster';
import { dt } from '../lib/ui';
import { type ApprovalTask as Task, useApprovals, useDecideApproval } from '../lib/queries';

export default function Approvals() {
	const { data, isLoading: loading } = useApprovals();
	const decide = useDecideApproval();
	const tasks = useMemo(() => data ?? [], [data]);
	const [q, setQ] = useState('');

	const [active, setActive] = useState<Task | null>(null);
	const [open, setOpen] = useState(false);
	const [mode, setMode] = useState<'approve' | 'reject'>('approve');
	const [password, setPassword] = useState('');
	const [reason, setReason] = useState('');
	const [busy, setBusy] = useState(false);

	const filtered = useMemo(() => {
		const s = q.trim().toLowerCase();
		return tasks.filter(
			(t) =>
				!s ||
				t.procedureTitle.toLowerCase().includes(s) ||
				t.stage.toLowerCase().includes(s) ||
				t.role.toLowerCase().includes(s)
		);
	}, [tasks, q]);

	function start(t: Task, m: 'approve' | 'reject') {
		setActive(t);
		setMode(m);
		setPassword('');
		setReason('');
		setOpen(true);
	}

	async function submit() {
		if (!active) return;
		setBusy(true);
		try {
			const res = await decide.mutateAsync({
				taskId: active.id,
				approve: mode === 'approve',
				password: mode === 'approve' ? password : null,
				reason: mode === 'reject' ? reason : null
			});
			if (mode === 'reject') {
				toast('Change request rejected', 'success');
			} else if (res.outcome === 'COMPLETE') {
				toast('Procedure fully approved & signed ✓', 'success');
			} else if (res.outcome === 'ADVANCED') {
				toast(
					`Signed — now awaiting ${res.nextStageName} (stage ${res.nextStageNumber} of ${res.totalStages})`,
					'success'
				);
			} else {
				toast('Signed — awaiting the other approvers in this stage', 'success');
			}
			setOpen(false);
		} catch (err) {
			const msg = (err as Error).message || 'Decision failed';
			toast(msg.includes('re-authentication') ? 'Password incorrect — not signed' : msg, 'danger');
		} finally {
			setBusy(false);
		}
	}

	return (
		<>
			<p className="page-sub">
				Tasks assigned to roles you hold. Approving signs a 21 CFR Part 11 electronic signature.
			</p>

			<div className="toolbar">
				<div style={{ maxWidth: 320, flex: '0 1 320px' }}>
					<InputGroup
						leftIcon="search"
						placeholder="Filter by procedure, stage, role…"
						value={q}
						onChange={(e) => setQ(e.currentTarget.value)}
					/>
				</div>
				<span className="muted" style={{ fontSize: 12 }}>
					{filtered.length} pending
				</span>
			</div>

			<HTMLTable striped className="full">
				<thead>
					<tr>
						<th>Procedure</th>
						<th>Stage</th>
						<th>Sign as</th>
						<th>Due</th>
						<th className="right">Decision</th>
					</tr>
				</thead>
				<tbody>
					{loading ? (
						<tr>
							<td colSpan={5} className="muted">
								Loading…
							</td>
						</tr>
					) : filtered.length === 0 ? (
						<tr>
							<td colSpan={5} className="muted">
								Your approval queue is empty. 🎉
							</td>
						</tr>
					) : (
						filtered.map((t) => (
							<tr key={t.id}>
								<td>
									<Link to={`/procedures/${t.procedureId}`}>{t.procedureTitle}</Link>
								</td>
								<td>
									{t.stage}
									{t.totalStages > 1 && (
										<span className="mono muted" style={{ fontSize: 11, marginLeft: 6 }}>
											stage {t.stageNumber} of {t.totalStages}
										</span>
									)}
								</td>
								<td>
									<Tag intent="primary" minimal>
										{t.meaning}
									</Tag>{' '}
									<span className="mono muted" style={{ fontSize: 11 }}>
										{t.role}
									</span>
								</td>
								<td className="mono muted">{dt(t.due)}</td>
								<td className="right nowrap">
									<Button small intent="success" onClick={() => start(t, 'approve')} text="Approve" />{' '}
									<Button small intent="danger" minimal onClick={() => start(t, 'reject')} text="Reject" />
								</td>
							</tr>
						))
					)}
				</tbody>
			</HTMLTable>

			<Dialog
				isOpen={open}
				onClose={() => setOpen(false)}
				title={mode === 'approve' ? 'Approve & sign' : 'Reject change'}
				icon={mode === 'approve' ? 'endorsed' : 'cross'}
			>
				{active && (
					<>
						<DialogBody>
							<p className="muted" style={{ marginTop: 0 }}>
								{active.procedureTitle} · {active.stage}
							</p>
							{mode === 'approve' ? (
								<FormGroup label={`Re-enter your password to sign (${active.meaning})`}>
									<InputGroup
										type="password"
										value={password}
										onChange={(e) => setPassword(e.currentTarget.value)}
										autoComplete="current-password"
										fill
									/>
								</FormGroup>
							) : (
								<FormGroup label="Reason for rejection">
									<TextArea
										value={reason}
										onChange={(e) => setReason(e.currentTarget.value)}
										placeholder="Sent back to draft"
										fill
										rows={3}
									/>
								</FormGroup>
							)}
						</DialogBody>
						<DialogFooter
							actions={
								<>
									<Button onClick={() => setOpen(false)} text="Cancel" />
									<Button
										intent={mode === 'approve' ? 'success' : 'danger'}
										onClick={submit}
										loading={busy}
										text={mode === 'approve' ? 'Approve & sign' : 'Reject'}
									/>
								</>
							}
						/>
					</>
				)}
			</Dialog>
		</>
	);
}
