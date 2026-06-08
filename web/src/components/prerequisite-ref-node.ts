import { Node, mergeAttributes } from '@tiptap/core';

/**
 * Inline, atomic TipTap node that embeds a prerequisite (type + text) as a chip inside the
 * prerequisites rich-text field. Authored by dragging a picked prerequisite from the type/autocomplete
 * picker into the text (or via the Insert button). Serialized as
 * `{ type: 'prerequisiteRef', attrs: { ptype, text } }` and rendered as "type: text".
 */
export const PrerequisiteRef = Node.create({
	name: 'prerequisiteRef',
	inline: true,
	group: 'inline',
	atom: true,
	selectable: true,
	draggable: true,

	addAttributes() {
		return {
			ptype: {
				default: null,
				parseHTML: (el) => el.getAttribute('data-ptype'),
				renderHTML: () => ({})
			},
			text: {
				default: null,
				parseHTML: (el) => el.getAttribute('data-text'),
				renderHTML: () => ({})
			}
		};
	},

	parseHTML() {
		return [{ tag: 'span[data-prerequisite-ref]' }];
	},

	renderHTML({ node, HTMLAttributes }) {
		const ptype = (node.attrs.ptype as string) ?? '';
		const text = (node.attrs.text as string) ?? '';
		const label = ptype ? `${ptype}: ${text}` : text;
		return [
			'span',
			mergeAttributes(HTMLAttributes, {
				'data-prerequisite-ref': '',
				'data-ptype': ptype,
				'data-text': text,
				class: 'prereq-ref'
			}),
			label
		];
	},

	renderText({ node }) {
		const ptype = (node.attrs.ptype as string) ?? '';
		const text = (node.attrs.text as string) ?? '';
		return ptype ? `${ptype}: ${text}` : text;
	}
});
