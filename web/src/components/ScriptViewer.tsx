import { useState } from 'react';
import { Button, Dialog, DialogBody, DialogFooter, HTMLSelect, Spinner, Tag } from '@blueprintjs/core';
import { useScriptVersions, useScriptVersionContent } from '../lib/queries';

type Props = {
	scriptId: string;
	scriptName: string;
	/** Version to open at (the pinned version of the step). */
	initialVersion: number;
	refCode?: string;
	onClose: () => void;
};

/** Read-only viewer for a repository script: browse its content across versions. */
export default function ScriptViewer({ scriptId, scriptName, initialVersion, refCode, onClose }: Props) {
	const versions = useScriptVersions(scriptId).data ?? [];
	const [no, setNo] = useState(initialVersion || 1);
	const content = useScriptVersionContent(scriptId, no);

	return (
		<Dialog
			isOpen
			onClose={onClose}
			title={scriptName}
			icon="code"
			style={{ width: 720 }}
		>
			<DialogBody>
				<div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 10 }}>
					{refCode && <Tag minimal className="prereq-ref">{refCode}</Tag>}
					<span className="muted" style={{ fontSize: 13 }}>Version</span>
					<HTMLSelect
						value={String(no)}
						onChange={(e) => setNo(Number(e.currentTarget.value))}
						options={(versions.length ? versions.map((v) => v.versionNo) : [initialVersion || 1]).map((n) => ({
							value: String(n),
							label: `v${n}`
						}))}
					/>
				</div>
				{content.isLoading ? (
					<Spinner size={24} />
				) : (
					<pre
						style={{
							margin: 0,
							maxHeight: 420,
							overflow: 'auto',
							background: '#f6f7f9',
							border: '1px solid var(--bp-border)',
							borderRadius: 4,
							padding: 12,
							fontSize: 13,
							whiteSpace: 'pre-wrap'
						}}
					>
						{content.data?.content ?? ''}
					</pre>
				)}
			</DialogBody>
			<DialogFooter actions={<Button onClick={onClose} text="Close" />} />
		</Dialog>
	);
}
