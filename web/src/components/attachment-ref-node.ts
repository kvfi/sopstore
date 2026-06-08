import { Node, mergeAttributes } from '@tiptap/core';

/**
 * Inline, atomic TipTap node that references a procedure attachment by its short ref id (e.g. "A1").
 * Serialized in the body JSON as `{ type: 'attachmentRef', attrs: { refId } }`; rendered both in the
 * editor and the Word export as `[A1]`. The id is the stable ref assigned in the attachments list,
 * so the citation survives even if attachments are reordered.
 */
export const AttachmentRef = Node.create({
	name: 'attachmentRef',
	inline: true,
	group: 'inline',
	atom: true,
	selectable: true,

	addAttributes() {
		return {
			refId: {
				default: null,
				parseHTML: (el) => el.getAttribute('data-attachment-ref'),
				// Rendered manually in renderHTML below so we don't also emit a refid="" attribute.
				renderHTML: () => ({})
			}
		};
	},

	parseHTML() {
		return [{ tag: 'span[data-attachment-ref]' }];
	},

	renderHTML({ node, HTMLAttributes }) {
		const ref = (node.attrs.refId as string) ?? '';
		return [
			'span',
			mergeAttributes(HTMLAttributes, { 'data-attachment-ref': ref, class: 'att-ref' }),
			`[${ref}]`
		];
	},

	renderText({ node }) {
		return `[${node.attrs.refId ?? ''}]`;
	}
});
