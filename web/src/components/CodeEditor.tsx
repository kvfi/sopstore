import { useMemo } from 'react';
import CodeMirror, { EditorView } from '@uiw/react-codemirror';
import { StreamLanguage } from '@codemirror/language';
import { sql } from '@codemirror/lang-sql';
import { python } from '@codemirror/lang-python';
import { javascript } from '@codemirror/lang-javascript';
import { shell } from '@codemirror/legacy-modes/mode/shell';
import { powerShell } from '@codemirror/legacy-modes/mode/powershell';

/** Maps a script's stored language keyword to a CodeMirror language extension (none = plain text). */
function languageExtension(language: string) {
	switch (language) {
		case 'sql':
			return [sql()];
		case 'python':
			return [python()];
		case 'javascript':
			return [javascript()];
		case 'bash':
		case 'shell':
			return [StreamLanguage.define(shell)];
		case 'powershell':
			return [StreamLanguage.define(powerShell)];
		default:
			return [];
	}
}

type Props = {
	value: string;
	language: string;
	onChange?: (value: string) => void;
	readOnly?: boolean;
	minHeight?: string;
	maxHeight?: string;
};

/**
 * A CodeMirror 6 editor wrapper with line numbers, syntax highlighting per language, and find
 * (Ctrl/Cmd-F). Used read-write to author scripts and read-only to view a pinned version.
 */
export default function CodeEditor({
	value,
	language,
	onChange,
	readOnly = false,
	minHeight = '320px',
	maxHeight = '60vh'
}: Props) {
	const extensions = useMemo(
		() => [...languageExtension(language), EditorView.lineWrapping],
		[language]
	);
	return (
		<div style={{ border: '1px solid var(--bp-border, #d3d8de)', borderRadius: 4, overflow: 'hidden' }}>
			<CodeMirror
				value={value}
				onChange={onChange}
				editable={!readOnly}
				readOnly={readOnly}
				extensions={extensions}
				minHeight={minHeight}
				maxHeight={maxHeight}
				theme="light"
				basicSetup={{
					lineNumbers: true,
					foldGutter: true,
					highlightActiveLine: !readOnly,
					highlightActiveLineGutter: !readOnly,
					autocompletion: false
				}}
				style={{ fontSize: 13 }}
			/>
		</div>
	);
}
