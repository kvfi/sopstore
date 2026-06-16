import { Icon, type IconName } from '@blueprintjs/core';

export type StatTone = 'neutral' | 'success' | 'warning' | 'danger';

type Props = {
	label: string;
	value: number | string;
	icon: IconName;
	tone?: StatTone;
	/** Small uppercase note under the value (e.g. "needs attention"). */
	flag?: string;
	onClick?: () => void;
};

/** Headline metric card: icon, large value, label, optional attention flag. */
export default function StatCard({ label, value, icon, tone = 'neutral', flag, onClick }: Props) {
	const toneClass = tone === 'neutral' ? '' : ` is-${tone}`;
	return (
		<div
			className={`stat-card${toneClass}${onClick ? ' click' : ''}`}
			onClick={onClick}
			role={onClick ? 'button' : undefined}
			tabIndex={onClick ? 0 : undefined}
			onKeyDown={onClick ? (e) => (e.key === 'Enter' ? onClick() : undefined) : undefined}
		>
			<div className="sc-top">
				<span className="sc-icon">
					<Icon icon={icon} size={18} />
				</span>
				{flag ? <span className="sc-flag">{flag}</span> : null}
			</div>
			<div>
				<div className="sc-value">{value}</div>
				<div className="sc-label">{label}</div>
			</div>
		</div>
	);
}
