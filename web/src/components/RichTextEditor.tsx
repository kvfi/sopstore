import { forwardRef, useEffect, useImperativeHandle, useReducer, useRef } from 'react';
import { useEditor, EditorContent, type Editor } from '@tiptap/react';
import type { AnyExtension } from '@tiptap/core';
import StarterKit from '@tiptap/starter-kit';
import { Button, ButtonGroup, Menu, MenuItem, Popover, Position } from '@blueprintjs/core';
import { AttachmentRef } from './attachment-ref-node';

/** A procedure attachment exposed to the editor as an insertable reference. */
export type RefItem = { refId: string; filename: string };

/**
 * Languages offered for code blocks. Values mirror the PDF exporter's CodeHighlighter so a snippet
 * highlights consistently in the editor preview and the exported document. null = plain text.
 */
const CODE_LANGUAGES: { label: string; value: string | null }[] = [
	{ label: 'Plain text', value: null },
	{ label: 'Bash', value: 'bash' },
	{ label: 'C', value: 'c' },
	{ label: 'C++', value: 'cpp' },
	{ label: 'C#', value: 'csharp' },
	{ label: 'CSS', value: 'css' },
	{ label: 'Go', value: 'go' },
	{ label: 'Java', value: 'java' },
	{ label: 'JavaScript', value: 'javascript' },
	{ label: 'JSON', value: 'json' },
	{ label: 'Kotlin', value: 'kotlin' },
	{ label: 'Python', value: 'python' },
	{ label: 'Ruby', value: 'ruby' },
	{ label: 'Rust', value: 'rust' },
	{ label: 'SQL', value: 'sql' },
	{ label: 'TypeScript', value: 'typescript' },
	{ label: 'YAML', value: 'yaml' }
];

/** Imperative handle: insert arbitrary TipTap content (e.g. a chip node) at the caret. */
export type RichTextHandle = { insert: (content: object) => void };

type Props = {
	/** Initial content: TipTap JSON (object), a legacy plain string, or null/empty. */
	value: object | string | null;
	/** Fired with the TipTap JSON document on every edit. */
	onChange: (json: object) => void;
	disabled?: boolean;
	/** Attachments that can be cited; when provided, shows the "Insert reference" menu. */
	refs?: RefItem[];
	/** Extra node/mark extensions (e.g. a prerequisiteRef node). */
	extraExtensions?: AnyExtension[];
	/** Handle a native drop onto the editor; return true if consumed. */
	onDrop?: (editor: Editor, event: DragEvent) => boolean;
};

/**
 * WYSIWYG editor used for step instructions and the prerequisites field. Content is TipTap JSON; the
 * toolbar offers basic formatting plus (when {@link Props.refs} is given) an attachment "Insert
 * reference" menu. The editor is created once and uncontrolled after mount, so typing never loses the
 * caret — edits flow out via {@link Props.onChange}. Callers may insert chips imperatively via the
 * ref handle, or by dropping onto the editor (wired through {@link Props.onDrop}).
 */
const RichTextEditor = forwardRef<RichTextHandle, Props>(function RichTextEditor(
	{ value, onChange, disabled, refs, extraExtensions, onDrop },
	ref
) {
	const editorRef = useRef<Editor | null>(null);
	const dropRef = useRef(onDrop);
	dropRef.current = onDrop;

	const editor = useEditor({
		editable: !disabled,
		extensions: [StarterKit, AttachmentRef, ...(extraExtensions ?? [])],
		content: value ?? '',
		onUpdate: ({ editor }) => onChange(editor.getJSON()),
		editorProps: {
			handleDrop: (_view, event) => {
				const ed = editorRef.current;
				const fn = dropRef.current;
				if (!ed || !fn) return false;
				return fn(ed, event as DragEvent);
			}
		}
	});
	editorRef.current = editor;

	useImperativeHandle(
		ref,
		() => ({
			insert: (content: object) =>
				editor?.chain().focus().insertContent(content).insertContent(' ').run()
		}),
		[editor]
	);

	// v3's useEditor doesn't re-render per transaction; force one so toolbar active-states stay live.
	const [, force] = useReducer((x: number) => x + 1, 0);
	useEffect(() => {
		if (!editor) return;
		editor.on('transaction', force);
		editor.on('selectionUpdate', force);
		return () => {
			editor.off('transaction', force);
			editor.off('selectionUpdate', force);
		};
	}, [editor]);

	useEffect(() => {
		editor?.setEditable(!disabled);
	}, [editor, disabled]);

	if (!editor) return null;

	const setLink = () => {
		const prev = editor.getAttributes('link').href as string | undefined;
		const url = window.prompt('Link URL', prev ?? 'https://');
		if (url === null) return;
		if (url === '') editor.chain().focus().unsetLink().run();
		else editor.chain().focus().extendMarkRange('link').setLink({ href: url }).run();
	};

	const insertRef = (refId: string) =>
		editor.chain().focus().insertContent({ type: 'attachmentRef', attrs: { refId } }).insertContent(' ').run();

	// Set the language on the current code block, creating one first if the caret isn't already in one.
	const setCodeLanguage = (language: string | null) => {
		let chain = editor.chain().focus();
		if (!editor.isActive('codeBlock')) chain = chain.toggleCodeBlock();
		chain.updateAttributes('codeBlock', { language }).run();
	};
	const codeLanguage = (editor.getAttributes('codeBlock').language as string | null) ?? null;
	const codeLanguageLabel = CODE_LANGUAGES.find((l) => l.value === codeLanguage)?.label ?? 'Plain text';

	return (
		<div className={`rte ${disabled ? 'rte-disabled' : ''}`}>
			{!disabled && (
				<div className="rte-toolbar">
					<ButtonGroup minimal>
						<Button small icon="bold" active={editor.isActive('bold')} onClick={() => editor.chain().focus().toggleBold().run()} aria-label="Bold" />
						<Button small icon="italic" active={editor.isActive('italic')} onClick={() => editor.chain().focus().toggleItalic().run()} aria-label="Italic" />
						<Button small icon="header" active={editor.isActive('heading', { level: 2 })} onClick={() => editor.chain().focus().toggleHeading({ level: 2 }).run()} aria-label="Heading" />
						<Button small icon="list" active={editor.isActive('bulletList')} onClick={() => editor.chain().focus().toggleBulletList().run()} aria-label="Bullet list" />
						<Button small icon="numbered-list" active={editor.isActive('orderedList')} onClick={() => editor.chain().focus().toggleOrderedList().run()} aria-label="Numbered list" />
						<Button small icon="link" active={editor.isActive('link')} onClick={setLink} aria-label="Link" />
						<Button small icon="code" active={editor.isActive('codeBlock')} onClick={() => editor.chain().focus().toggleCodeBlock().run()} aria-label="Code block" />
					</ButtonGroup>
					{editor.isActive('codeBlock') && (
						<Popover
							position={Position.BOTTOM_LEFT}
							content={
								<Menu>
									{CODE_LANGUAGES.map((l) => (
										<MenuItem
											key={l.value ?? 'plain'}
											text={l.label}
											active={codeLanguage === l.value}
											onClick={() => setCodeLanguage(l.value)}
										/>
									))}
								</Menu>
							}
						>
							<Button small minimal icon="translate" rightIcon="caret-down" text={codeLanguageLabel} />
						</Popover>
					)}
					{refs && (
						<Popover
							position={Position.BOTTOM_LEFT}
							content={
								refs.length ? (
									<Menu>
										{refs.map((r) => (
											<MenuItem key={r.refId} text={`[${r.refId}] ${r.filename}`} onClick={() => insertRef(r.refId)} />
										))}
									</Menu>
								) : (
									<Menu>
										<MenuItem disabled text="No attachments yet — add one below" />
									</Menu>
								)
							}
						>
							<Button small minimal icon="paperclip" text="Insert reference" />
						</Popover>
					)}
				</div>
			)}
			<EditorContent editor={editor} className="rte-content" />
		</div>
	);
});

export default RichTextEditor;
