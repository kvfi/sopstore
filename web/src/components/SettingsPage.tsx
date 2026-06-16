import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { Icon } from '@blueprintjs/core';
import PageHeader from './PageHeader';

type Props = {
	title: string;
	subtitle?: ReactNode;
	actions?: ReactNode;
	children: ReactNode;
};

/** Chrome for a dedicated settings section page: back link + standard header. */
export default function SettingsPage({ title, subtitle, actions, children }: Props) {
	return (
		<>
			<Link to="/settings" className="back-link">
				<Icon icon="chevron-left" size={14} />
				Settings
			</Link>
			<PageHeader title={title} subtitle={subtitle} actions={actions} />
			{children}
		</>
	);
}
