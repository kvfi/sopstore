import { useNavigate } from 'react-router-dom';
import { Card, HTMLTable, Tag, Spinner, NonIdealState } from '@blueprintjs/core';
import { statusIntent, dt } from '../lib/ui';
import { useDashboard } from '../lib/queries';

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

	return (
		<>
			<p className="page-sub">
				Live compliance posture across authoring, change control, and execution.
			</p>

			<div className="kpis">
				{d.kpis.map((k) => (
					<Card key={k.label} compact className="kpi">
						<div className="v">{k.value}</div>
						<div className="l">{k.label}</div>
					</Card>
				))}
			</div>

			<div className="section-h">Overdue periodic reviews</div>
			<HTMLTable striped interactive className="full">
				<thead>
					<tr>
						<th>Document</th>
						<th>Title</th>
						<th>Review due</th>
					</tr>
				</thead>
				<tbody>
					{d.overdueReviews.length === 0 ? (
						<tr>
							<td colSpan={3} className="muted">
								None overdue.
							</td>
						</tr>
					) : (
						d.overdueReviews.map((r) => (
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
						))
					)}
				</tbody>
			</HTMLTable>

			<div className="section-h">Pending approvals</div>
			<HTMLTable striped interactive className="full">
				<thead>
					<tr>
						<th>Procedure</th>
						<th>Stage</th>
						<th>Role</th>
					</tr>
				</thead>
				<tbody>
					{d.approvals.length === 0 ? (
						<tr>
							<td colSpan={3} className="muted">
								No pending approvals.
							</td>
						</tr>
					) : (
						d.approvals.map((a) => (
							<tr key={a.procedureId + a.stage + a.role} className="click" onClick={() => navigate('/approvals')}>
								<td>{a.procedureTitle}</td>
								<td>{a.stage}</td>
								<td>
									<Tag intent="warning" minimal>
										{a.role}
									</Tag>
								</td>
							</tr>
						))
					)}
				</tbody>
			</HTMLTable>

			<div className="section-h">Open change requests</div>
			<HTMLTable striped interactive className="full">
				<thead>
					<tr>
						<th>Procedure</th>
						<th>Change</th>
						<th>Class</th>
						<th>Status</th>
					</tr>
				</thead>
				<tbody>
					{d.changeRequests.length === 0 ? (
						<tr>
							<td colSpan={4} className="muted">
								No open change requests.
							</td>
						</tr>
					) : (
						d.changeRequests.map((c) => (
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
						))
					)}
				</tbody>
			</HTMLTable>

			<div className="section-h">Recent deviations</div>
			<HTMLTable striped className="full">
				<thead>
					<tr>
						<th>Logged</th>
						<th>Category</th>
						<th>Description</th>
						<th>State</th>
					</tr>
				</thead>
				<tbody>
					{d.deviations.length === 0 ? (
						<tr>
							<td colSpan={4} className="muted">
								No deviations logged.
							</td>
						</tr>
					) : (
						d.deviations.map((dv) => (
							<tr key={dv.id}>
								<td className="mono">{dt(dv.loggedAt)}</td>
								<td className="mono">{dv.category}</td>
								<td>{dv.description}</td>
								<td>
									<Tag intent={dv.open ? 'danger' : 'success'} minimal>
										{dv.open ? 'open' : 'closed'}
									</Tag>
								</td>
							</tr>
						))
					)}
				</tbody>
			</HTMLTable>
		</>
	);
}
