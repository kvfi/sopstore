import { Button, Card, Spinner, NonIdealState } from '@blueprintjs/core';
import { dt } from '../lib/ui';
import { useNotifications, useMarkRead } from '../lib/queries';

export default function Notifications() {
	const { data, isLoading } = useNotifications();
	const markRead = useMarkRead();
	const items = data?.items ?? [];
	const unread = data?.unread ?? 0;

	if (isLoading)
		return (
			<div className="center-screen">
				<Spinner />
			</div>
		);

	return (
		<>
			<p className="page-sub">{unread > 0 ? `${unread} unread` : 'All caught up'}</p>

			{items.length === 0 ? (
				<NonIdealState icon="notifications" title="No notifications" />
			) : (
				<Card compact style={{ padding: 0 }}>
					{items.map((m, i) => (
						<div
							key={m.id}
							style={{
								display: 'flex',
								alignItems: 'flex-start',
								gap: 14,
								padding: 16,
								borderTop: i === 0 ? 'none' : '1px solid var(--bp-border)'
							}}
						>
							<span
								style={{
									marginTop: 6,
									width: 9,
									height: 9,
									flex: 'none',
									borderRadius: 999,
									background: m.unread ? '#215db0' : 'rgba(17,20,24,0.2)'
								}}
							/>
							<div style={{ minWidth: 0, flex: 1 }}>
								<div style={{ fontWeight: m.unread ? 600 : 500 }}>{m.title}</div>
								<div className="muted" style={{ marginTop: 2 }}>
									{m.body}
								</div>
								{m.link && (
									<a className="mono" style={{ fontSize: 12 }} href={m.link}>
										Open →
									</a>
								)}
							</div>
							<div className="right nowrap">
								<div className="mono muted" style={{ fontSize: 12 }}>
									{dt(m.createdAt)}
								</div>
								{m.unread && (
									<Button small minimal style={{ marginTop: 6 }} onClick={() => markRead.mutate(m.id)} text="Mark read" />
								)}
							</div>
						</div>
					))}
				</Card>
			)}
		</>
	);
}
