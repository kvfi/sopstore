import { Node, mergeAttributes } from '@tiptap/core';

/**
 * Block TipTap node for an inline callout / note. Carries a {@code noteType}
 * (info | warning | critical) and holds rich block content. Inserted from the editor toolbar
 * (wrapIn 'callout') and serialized as `{ type: 'callout', attrs: { noteType }, content: [...] }`.
 * The PDF exporter renders it as a `.note .note-<type>` box.
 */
export const Callout = Node.create({
	name: 'callout',
	group: 'block',
	content: 'block+',
	defining: true,

	addAttributes() {
		return {
			noteType: {
				default: 'info',
				parseHTML: (el) => el.getAttribute('data-note-type') || 'info',
				renderHTML: (attrs) => ({ 'data-note-type': attrs.noteType })
			}
		};
	},

	parseHTML() {
		return [{ tag: 'div[data-callout]' }];
	},

	renderHTML({ node, HTMLAttributes }) {
		const type = (node.attrs.noteType as string) || 'info';
		return [
			'div',
			mergeAttributes(HTMLAttributes, { 'data-callout': '', class: `rte-callout rte-callout--${type}` }),
			0
		];
	}
});
