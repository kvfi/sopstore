import type { ReactNode } from 'react';
import { Icon, type IconName } from '@blueprintjs/core';

type Props = {
	title?: ReactNode;
	icon?: IconName;
	count?: number;
	actions?: ReactNode;
	/** Remove body padding (e.g. when the body is a full-bleed table). */
	flush?: boolean;
	/** Cap body height and scroll overflow (for work queues). */
	scroll?: boolean;
	className?: string;
	children: ReactNode;
};

/** Titled surface card used across the app for grouped content. */
export default function Panel({
	title,
	icon,
	count,
	actions,
	flush,
	scroll,
	className,
	children
}: Props) {
	const body = <div className={`panel-body${flush ? ' flush' : ''}`}>{children}</div>;
	return (
		<section className={`panel${className ? ` ${className}` : ''}`}>
			{title || actions ? (
				<header className="panel-head">
					{title ? (
						<span className="ph-title">
							{icon ? <Icon className="ph-icon" icon={icon} size={15} /> : null}
							{title}
							{count != null ? <span className="ph-count">{count}</span> : null}
						</span>
					) : null}
					{actions ? <span className="ph-actions">{actions}</span> : null}
				</header>
			) : null}
			{scroll ? <div className="panel-scroll">{body}</div> : body}
		</section>
	);
}
