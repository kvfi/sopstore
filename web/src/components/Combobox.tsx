import { MenuItem } from '@blueprintjs/core';
import { Suggest } from '@blueprintjs/select';

type ComboboxProps = {
	/** Suggestions to filter as the user types. */
	items: string[];
	/** Current free-text value (the source of truth). */
	value: string;
	onChange: (value: string) => void;
	placeholder?: string;
	disabled?: boolean;
	fill?: boolean;
	/** Show an explicit "Add ‹query›" item when the text doesn't match a suggestion. */
	allowCreate?: boolean;
};

/**
 * A free-text autocomplete: type to filter {@link ComboboxProps.items}, click a suggestion to fill,
 * or keep typing to use a new value. Built on Blueprint's {@code Suggest} so it stays consistent
 * with the rest of the UI (styled popover, keyboard navigation). The value is fully controlled —
 * both typing ({@code onQueryChange}) and selecting ({@code onItemSelect}) flow through onChange.
 */
export default function Combobox({
	items,
	value,
	onChange,
	placeholder,
	disabled,
	fill,
	allowCreate = true
}: ComboboxProps) {
	return (
		<Suggest<string>
			fill={fill}
			disabled={disabled}
			items={items}
			selectedItem={value || null}
			query={value}
			onQueryChange={onChange}
			onItemSelect={onChange}
			inputValueRenderer={(item) => item}
			itemsEqual={(a, b) => a.toLowerCase() === b.toLowerCase()}
			itemPredicate={(query, item) => item.toLowerCase().includes(query.toLowerCase())}
			itemRenderer={(item, { handleClick, handleFocus, modifiers }) =>
				modifiers.matchesPredicate ? (
					<MenuItem
						key={item}
						text={item}
						roleStructure="listoption"
						active={modifiers.active}
						disabled={modifiers.disabled}
						onClick={handleClick}
						onFocus={handleFocus}
					/>
				) : null
			}
			createNewItemFromQuery={allowCreate ? (query) => query.trim() : undefined}
			createNewItemRenderer={
				allowCreate
					? (query, active, handleClick) => (
							<MenuItem
								key="__create__"
								icon="add"
								text={`Add "${query.trim()}"`}
								roleStructure="listoption"
								active={active}
								onClick={handleClick}
								shouldDismissPopover={false}
							/>
						)
					: undefined
			}
			resetOnSelect={false}
			popoverProps={{ minimal: true, matchTargetWidth: true }}
			inputProps={{ placeholder }}
			noResults={<MenuItem disabled text="No matches" roleStructure="listoption" />}
		/>
	);
}
