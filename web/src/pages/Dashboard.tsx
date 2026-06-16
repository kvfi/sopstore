import { useNavigate, Link } from 'react-router-dom';
import { HTMLTable, Tag, Spinner, NonIdealState, Icon } from '@blueprintjs/core';
import { statusIntent, statusColor, tally, dt } from '../lib/ui';
import { useDashboard } from '../lib/queries';
import PageHeader from '../components/PageHeader';
import Panel from '../components/Panel';
import StatCard from '../components/StatCard';
import { DonutChart, CategoryBars, type Slice } from '../components/charts';

const SERIES = ['var(--chart-1)', 'var(--chart-2)', 'var(--chart-3)', 'var(--chart-4)', 'var(--chart-5)', 'var(--chart-6)'];

function ViewAll({ to }: { to: string }) {
	return (
		<Link className="panel-link" to={to}>
			View all <Icon icon="arrow-right" size={12} />
		</Link>
	);
}

function Empty({ icon, text }: { icon: 'tick-circle' | 'inbox' | 'clean'; text: string }) {
	return (
		<div className="panel-empty">
			<Icon icon={icon} size={26} />
			<div>{text}</div>
		</div>
	);
}

export default function Dashboard() {
	const navigate = useNavigate();
	const { data: d, isError, error } = useDashboard();

	if (isError)
		return (
			<NonIdealState icon="error" title="Couldn't load the dashboard" description={(error as Error).message} />
		);
	if (!d)
		return (
			<div className="center-screen">
				<Spinner />
			</div>
		);

	const kpi = (label: string) => d.kpis.find((k) => k.label === label)?.value ?? 0;
	const total = kpi('Total procedures');
	const effective = kpi('Effective');
	const overdue = kpi('Overdue reviews');
	const underReview = kpi('Under review');
	const pending = kpi('Pending approvals');

	// ---- compliance posture donut (derived from KPI snapshot) -------------
	const goodStanding = Math.max(0, effective - overdue);
	const otherStates = Math.max(0, total - effective - underReview);
	const postureData: Slice[] = [
		{ name: 'In good standing', value: goodStanding, color: 'var(--chart-2)' },
		{ name: 'Overdue review', value: overdue, color: 'var(--danger)' },
		{ name: 'Under review', value: underReview, color: 'var(--warning)' },
		{ name: 'Draft / other', value: otherStates, color: 'var(--slate-300)' }
	].filter((s) => s.value > 0);
	const complianceRate = total > 0 ? Math.round((goodStanding / total) * 100) : 0;

	// ---- change requests by status (from the open-CR list) ----------------
	const crData: Slice[] = tally(d.changeRequests.map((c) => c.status)).map((s) => ({
		...s,
		color: statusColor(s.name)
	}));

	// ---- deviations by category (ranked) ----------------------------------
	const devData: Slice[] = tally(d.deviations.map((dv) => dv.category))
		.sort((a, b) => b.value - a.value)
		.map((s, i) => ({ ...s, color: SERIES[i % SERIES.length] }));

	return (
		<>
			<PageHeader
				title="Compliance dashboard"
				subtitle="Live posture across authoring, change control, and execution."
			/>

			<div className="stat-row">
				<StatCard label="Total procedures" value={total} icon="projects" />
				<StatCard label="Effective" value={effective} icon="endorsed" tone="success" />
				<StatCard
					label="Overdue reviews"
					value={overdue}
					icon="time"
					tone={overdue > 0 ? 'danger' : 'neutral'}
					flag={overdue > 0 ? 'action needed' : undefined}
					onClick={() => navigate('/procedures')}
				/>
				<StatCard
					label="Pending approvals"
					value={pending}
					icon="inbox-update"
					tone={pending > 0 ? 'warning' : 'neutral'}
					flag={pending > 0 ? 'awaiting sign-off' : undefined}
					onClick={() => navigate('/approvals')}
				/>
			</div>

			<div className="dash-band">
				<span className="db-title">Compliance overview</span>
				<span className="db-rule" />
			</div>

			<div className="chart-grid">
				<Panel title="Procedure posture" icon="doughnut-chart">
					{total > 0 ? (
						<DonutChart data={postureData} centerValue={`${complianceRate}%`} centerLabel="Compliant" />
					) : (
						<Empty icon="clean" text="No procedures yet." />
					)}
				</Panel>

				<Panel title="Open change requests" icon="git-branch">
					{crData.length > 0 ? (
						<DonutChart data={crData} centerValue={d.changeRequests.length} centerLabel="Open" />
					) : (
						<Empty icon="tick-circle" text="No open change requests." />
					)}
				</Panel>

				<Panel title="Deviations by category" icon="comparison">
					{devData.length > 0 ? (
						<CategoryBars data={devData} />
					) : (
						<Empty icon="tick-circle" text="No deviations logged." />
					)}
				</Panel>
			</div>

			<div className="dash-band">
				<span className="db-title">Work queues</span>
				<span className="db-rule" />
			</div>

			<div className="dash-queues">
				<Panel
					title="Overdue periodic reviews"
					icon="time"
					count={d.overdueReviews.length}
					actions={<ViewAll to="/procedures" />}
					flush
					scroll
				>
					{d.overdueReviews.length === 0 ? (
						<Empty icon="tick-circle" text="Nothing overdue." />
					) : (
						<HTMLTable className="data-table full">
							<thead>
								<tr>
									<th>Document</th>
									<th>Title</th>
									<th>Review due</th>
								</tr>
							</thead>
							<tbody>
								{d.overdueReviews.map((r) => (
									<tr
										key={r.procedureId}
										className="click"
										onClick={() => navigate(`/procedures/${r.procedureId}`)}
									>
										<td className="mono">{r.documentNumber}</td>
										<td>{r.title}</td>
										<td>
											<Tag intent="danger" minimal>
												{r.due}
											</Tag>
										</td>
									</tr>
								))}
							</tbody>
						</HTMLTable>
					)}
				</Panel>

				<Panel
					title="Pending approvals"
					icon="endorsed"
					count={d.approvals.length}
					actions={<ViewAll to="/approvals" />}
					flush
					scroll
				>
					{d.approvals.length === 0 ? (
						<Empty icon="inbox" text="No pending approvals." />
					) : (
						<HTMLTable className="data-table full">
							<thead>
								<tr>
									<th>Procedure</th>
									<th>Stage</th>
									<th>Role</th>
								</tr>
							</thead>
							<tbody>
								{d.approvals.map((a) => (
									<tr
										key={a.procedureId + a.stage + a.role}
										className="click"
										onClick={() => navigate('/approvals')}
									>
										<td>{a.procedureTitle}</td>
										<td>{a.stage}</td>
										<td>
											<Tag intent="warning" minimal>
												{a.role}
											</Tag>
										</td>
									</tr>
								))}
							</tbody>
						</HTMLTable>
					)}
				</Panel>

				<Panel
					title="Open change requests"
					icon="git-branch"
					count={d.changeRequests.length}
					actions={<ViewAll to="/procedures" />}
					flush
					scroll
				>
					{d.changeRequests.length === 0 ? (
						<Empty icon="tick-circle" text="No open change requests." />
					) : (
						<HTMLTable className="data-table full">
							<thead>
								<tr>
									<th>Procedure</th>
									<th>Change</th>
									<th>Class</th>
									<th>Status</th>
								</tr>
							</thead>
							<tbody>
								{d.changeRequests.map((c) => (
									<tr
										key={c.procedureId + c.title}
										className="click"
										onClick={() => navigate(`/procedures/${c.procedureId}`)}
									>
										<td>{c.procedureTitle}</td>
										<td>{c.title}</td>
										<td className="mono">{c.classification}</td>
										<td>
											<Tag intent={statusIntent(c.status)} minimal>
												{c.status}
											</Tag>
										</td>
									</tr>
								))}
							</tbody>
						</HTMLTable>
					)}
				</Panel>

				<Panel
					title="Recent deviations"
					icon="warning-sign"
					count={d.deviations.length}
					actions={<ViewAll to="/runs" />}
					flush
					scroll
				>
					{d.deviations.length === 0 ? (
						<Empty icon="tick-circle" text="No deviations logged." />
					) : (
						<HTMLTable className="data-table full">
							<thead>
								<tr>
									<th>Logged</th>
									<th>Category</th>
									<th>Description</th>
									<th>State</th>
								</tr>
							</thead>
							<tbody>
								{d.deviations.map((dv) => (
									<tr key={dv.id}>
										<td className="mono nowrap">{dt(dv.loggedAt)}</td>
										<td className="mono">{dv.category}</td>
										<td>{dv.description}</td>
										<td>
											<Tag intent={dv.open ? 'danger' : 'success'} minimal>
												{dv.open ? 'open' : 'closed'}
											</Tag>
										</td>
									</tr>
								))}
							</tbody>
						</HTMLTable>
					)}
				</Panel>
			</div>
		</>
	);
}
