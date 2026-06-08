<script lang="ts">
	import { onMount, onDestroy } from 'svelte';

	let {
		content = null,
		editable = true,
		onchange = (_json: unknown) => {},
		attachments = []
	}: {
		content?: unknown;
		editable?: boolean;
		onchange?: (json: unknown) => void;
		attachments?: { filename: string; href: string }[];
	} = $props();

	let el: HTMLDivElement;
	// eslint-disable-next-line @typescript-eslint/no-explicit-any
	let editor: any = null;
	let active = $state<Record<string, boolean>>({});

	function refresh() {
		if (!editor) return;
		active = {
			bold: editor.isActive('bold'),
			italic: editor.isActive('italic'),
			strike: editor.isActive('strike'),
			code: editor.isActive('code'),
			codeBlock: editor.isActive('codeBlock'),
			link: editor.isActive('link'),
			h1: editor.isActive('heading', { level: 1 }),
			h2: editor.isActive('heading', { level: 2 }),
			h3: editor.isActive('heading', { level: 3 }),
			bullet: editor.isActive('bulletList'),
			ordered: editor.isActive('orderedList'),
			quote: editor.isActive('blockquote')
		};
	}

	onMount(async () => {
		const { Editor } = await import('@tiptap/core');
		const StarterKit = (await import('@tiptap/starter-kit')).default;
		const Link = (await import('@tiptap/extension-link')).default;
		editor = new Editor({
			element: el,
			editable,
			extensions: [
				StarterKit,
				Link.configure({ openOnClick: false, autolink: true, HTMLAttributes: { rel: 'noopener' } })
			],
			content: content ?? { type: 'doc', content: [{ type: 'paragraph' }] },
			onUpdate: ({ editor: e }) => {
				onchange(e.getJSON());
				refresh();
			},
			onSelectionUpdate: refresh,
			editorProps: { attributes: { class: 'tiptap' } }
		});
		refresh();
	});

	onDestroy(() => editor?.destroy());

	// eslint-disable-next-line @typescript-eslint/no-explicit-any
	const run = (fn: (chain: any) => any) => () => fn(editor.chain().focus()).run();

	function toggleLink() {
		if (!editor) return;
		if (editor.isActive('link')) {
			editor.chain().focus().unsetLink().run();
			return;
		}
		const prev = editor.getAttributes('link').href ?? 'https://';
		const url = window.prompt('Link URL', prev);
		if (url === null) return;
		if (url === '') {
			editor.chain().focus().unsetLink().run();
			return;
		}
		editor.chain().focus().extendMarkRange('link').setLink({ href: url }).run();
	}

	function insertAttachment(e: Event) {
		const sel = e.target as HTMLSelectElement;
		const href = sel.value;
		const filename = sel.selectedOptions[0]?.dataset.name ?? sel.selectedOptions[0]?.text ?? '';
		sel.value = '';
		if (!href || !editor) return;
		editor
			.chain()
			.focus()
			.insertContent([
				{ type: 'text', text: filename, marks: [{ type: 'link', attrs: { href } }] },
				{ type: 'text', text: ' ' }
			])
			.run();
	}
</script>

<div class="tt">
	{#if editable}
		<div class="tt-toolbar">
			<button type="button" class:on={active.h1} onclick={run((c) => c.toggleHeading({ level: 1 }))}>H1</button>
			<button type="button" class:on={active.h2} onclick={run((c) => c.toggleHeading({ level: 2 }))}>H2</button>
			<button type="button" class:on={active.h3} onclick={run((c) => c.toggleHeading({ level: 3 }))}>H3</button>
			<span class="sep"></span>
			<button type="button" class:on={active.bold} onclick={run((c) => c.toggleBold())} style="font-weight:700">B</button>
			<button type="button" class:on={active.italic} onclick={run((c) => c.toggleItalic())} style="font-style:italic">I</button>
			<button type="button" class:on={active.strike} onclick={run((c) => c.toggleStrike())} style="text-decoration:line-through">S</button>
			<button type="button" class:on={active.code} onclick={run((c) => c.toggleCode())} title="Inline code"><code>`</code></button>
			<button type="button" class:on={active.link} onclick={toggleLink} title="Link">🔗</button>
			<span class="sep"></span>
			<button type="button" class:on={active.bullet} onclick={run((c) => c.toggleBulletList())}>• List</button>
			<button type="button" class:on={active.ordered} onclick={run((c) => c.toggleOrderedList())}>1. List</button>
			<button type="button" class:on={active.quote} onclick={run((c) => c.toggleBlockquote())}>" Quote</button>
			<button type="button" class:on={active.codeBlock} onclick={run((c) => c.toggleCodeBlock())} title="Code block">{'</>'}</button>
			<button type="button" onclick={run((c) => c.setHorizontalRule())} title="Divider">―</button>
			<span class="sep"></span>
			<button type="button" onclick={run((c) => c.undo())} title="Undo">↶</button>
			<button type="button" onclick={run((c) => c.redo())} title="Redo">↷</button>
			{#if attachments.length}
				<span class="sep"></span>
				<select class="tt-attach" onchange={insertAttachment} title="Reference an attachment">
					<option value="">📎 Attachment…</option>
					{#each attachments as a (a.href)}
						<option value={a.href} data-name={a.filename}>{a.filename}</option>
					{/each}
				</select>
			{/if}
		</div>
	{/if}
	<div class="tt-surface" class:readonly={!editable} bind:this={el}></div>
</div>
