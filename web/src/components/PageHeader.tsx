import type { ReactNode } from 'react';

type Props = {
	title: string;
	subtitle?: ReactNode;
	actions?: ReactNode;
};

/** Standard page heading: title + optional subtitle, with right-aligned actions. */
export default function PageHeader({ title, subtitle, actions }: Props) {
	return (
		<div className="page-header">
			<div className="ph-text">
				<h1>{title}</h1>
				{subtitle ? <p className="sub">{subtitle}</p> : null}
			</div>
			{actions ? <div className="ph-actions">{actions}</div> : null}
		</div>
	);
}
