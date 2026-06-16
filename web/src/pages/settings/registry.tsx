import type { ReactNode } from 'react';
import type { IconName } from '@blueprintjs/core';
import ConfidentialityLevels from './ConfidentialityLevels';
import PrerequisiteTypes from './PrerequisiteTypes';
import Prerequisites from './Prerequisites';
import ProcedureFormSettings from './ProcedureForm';
import ExportTemplates from './ExportTemplates';
import ScriptBundleSettingsPage from './ScriptBundleSettings';
import SessionPolicy from './SessionPolicy';

export type SettingsSection = {
	/** Path segment under /settings (also the hub link target). */
	path: string;
	label: string;
	icon: IconName;
	/** Short description shown on the hub card. */
	desc: string;
	element: ReactNode;
};

/**
 * Single source of truth for the settings area. Each entry becomes a hub card
 * AND a dedicated /settings/<path> page — add a configuration by adding a row.
 */
export const SETTINGS_SECTIONS: SettingsSection[] = [
	{
		path: 'confidentiality-levels',
		label: 'Confidentiality levels',
		icon: 'shield',
		desc: 'Classification levels authors assign to documents, marked on exported PDFs.',
		element: <ConfidentialityLevels />
	},
	{
		path: 'prerequisite-types',
		label: 'Prerequisite types',
		icon: 'tag',
		desc: 'The categories authors choose from when adding procedure prerequisites.',
		element: <PrerequisiteTypes />
	},
	{
		path: 'prerequisites',
		label: 'Prerequisites',
		icon: 'list',
		desc: 'A reusable library of prerequisites authors can attach to procedures.',
		element: <Prerequisites />
	},
	{
		path: 'procedure-form',
		label: 'Procedure form',
		icon: 'form',
		desc: 'Custom sections and fields added to every procedure’s authoring form.',
		element: <ProcedureFormSettings />
	},
	{
		path: 'export-templates',
		label: 'Export templates',
		icon: 'document',
		desc: 'PDF export themes: accent colour, logo, fonts, cover pages, and custom CSS.',
		element: <ExportTemplates />
	},
	{
		path: 'script-bundles',
		label: 'Script bundles',
		icon: 'archive',
		desc: 'How Run-script steps are named and linked inside exported SOP bundles.',
		element: <ScriptBundleSettingsPage />
	},
	{
		path: 'session-policy',
		label: 'Session policy',
		icon: 'time',
		desc: 'How long signed-in sessions last before idle and absolute timeouts sign users out.',
		element: <SessionPolicy />
	}
];
