import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { HTMLTable, InputGroup, Tag, Spinner } from '@blueprintjs/core';
import { statusIntent, dt, dur } from '../lib/ui';
import { useRuns } from '../lib/queries';

export default function Runs() {
	const navigate = useNavigate();
	const { data, isLoading: loading } = useRuns();
	const history = useMemo(() => data?.history ?? [], [data]);
	const analytics = useMemo(() => data?.analytics ?? [], [data]);
	const [q, setQ] = useState('');

	const t = q.trim().toLowerCase();
	const fAnalytics = useMemo(
		() => analytics.filter((a) => a.procedureTitle.toLowerCase().includes(t)),
		[analytics, t]
	);
	const fHistory = useMemo(
		() => history.filter((r) => r.procedureTitle.toLowerCase().includes(t)),
		[history, t]
	);

	if (loading)
		return (
			<div className="center-screen">
				<Spinner />
			</div>
		);

	return (
		<>
			<p className="page-sub">Execution analytics and recent runs across all procedures.</p>

			<div className="toolbar">
				<div style={{ maxWidth: 320, flex: '0 1 320px' }}>
					<InputGroup
						leftIcon="search"
						placeholder="Filter by procedure…"
						value={q}
						onChange={(e) => setQ(e.currentTarget.value)}
					/>
				</div>
				<span className="muted" style={{ fontSize: 12 }}>
					{fHistory.length} run(s)
				</span>
			</div>

			<div className="section-h">Per-procedure analytics</div>
			<HTMLTable striped interactive className="full">
				<thead>
					<tr>
						<th>Procedure</th>
						<th>Runs</th>
						<th>Completion</th>
						<th>Avg duration</th>
						<th>Deviations</th>
					</tr>
				</thead>
				<tbody>
					{fAnalytics.length === 0 ? (
						<tr>
							<td colSpan={5} className="muted">
								No runs yet.
							</td>
						</tr>
					) : (
						fAnalytics.map((a) => (
							<tr
								key={a.procedureId}
								className="click"
								onClick={() => navigate(`/procedures/${a.procedureId}`)}
							>
								<td>{a.procedureTitle}</td>
								<td className="mono">{a.runCount}</td>
								<td>
									<Tag intent={a.completionPct === 100 ? 'success' : 'warning'} minimal>
										{a.completionPct}%
									</Tag>
								</td>
								<td className="mono">{dur(a.avgDurationSeconds)}</td>
								<td className="mono">{a.deviationCount}</td>
							</tr>
						))
					)}
				</tbody>
			</HTMLTable>

			<div className="section-h">Recent runs</div>
			<HTMLTable striped className="full">
				<thead>
					<tr>
						<th>Procedure</th>
						<th>State</th>
						<th>Started</th>
						<th>Duration</th>
						<th>Deviations</th>
					</tr>
				</thead>
				<tbody>
					{fHistory.length === 0 ? (
						<tr>
							<td colSpan={5} className="muted">
								No runs yet.
							</td>
						</tr>
					) : (
						fHistory.map((r) => (
							<tr key={r.runId}>
								<td>{r.procedureTitle}</td>
								<td>
									<Tag intent={statusIntent(r.state)} minimal>
										{r.state}
									</Tag>
								</td>
								<td className="mono muted">{dt(r.startedAt)}</td>
								<td className="mono">{dur(r.durationSeconds)}</td>
								<td className="mono">{r.deviationCount}</td>
							</tr>
						))
					)}
				</tbody>
			</HTMLTable>
		</>
	);
}
