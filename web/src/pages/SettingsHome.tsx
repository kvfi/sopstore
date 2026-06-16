import { Link } from 'react-router-dom';
import { Icon } from '@blueprintjs/core';
import PageHeader from '../components/PageHeader';
import { SETTINGS_SECTIONS } from './settings/registry';

/** Settings landing page — a card grid linking to each configuration's own page. */
export default function SettingsHome() {
	return (
		<>
			<PageHeader
				title="Settings"
				subtitle="Configure how procedures are authored, classified, and exported."
			/>
			<div className="settings-hub">
				{SETTINGS_SECTIONS.map((s) => (
					<Link key={s.path} to={s.path} className="settings-card">
						<span className="sc-ic">
							<Icon icon={s.icon} size={18} />
						</span>
						<span className="sc-tx">
							<span className="sc-t">{s.label}</span>
							<span className="sc-d">{s.desc}</span>
						</span>
						<Icon className="sc-arrow" icon="chevron-right" size={16} />
					</Link>
				))}
			</div>
		</>
	);
}
