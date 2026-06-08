import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, upload, login } from './api';

export type ScriptRow = {
	id: string;
	name: string;
	language: string;
	description: string | null;
	currentVersion: number;
	updatedAt: string;
};
export type ScriptVersionRow = {
	versionNo: number;
	note: string | null;
	createdBy: string | null;
	createdAt: string;
};

export type ExportTemplate = {
	id: string;
	name: string;
	accentColor: string;
	footerText: string | null;
	bodyFontPt: number;
	headingFontPt: number;
	tableFontPt: number;
	hasLogo: boolean;
};

/*
 * Central data layer. Every read is a useQuery and every write a useMutation, all wrapping the
 * api.ts transport. Mutations invalidate the query keys they affect so the UI stays consistent
 * without manual refetching. Query keys live in `qk` so invalidation and fetching never drift.
 */

// ---------------------------------------------------------------- types (API shapes)
export type Me = { id: string; email: string; displayName: string; roles: string[] };

export type Kpi = { label: string; value: number };
export type DashOverdue = { procedureId: string; documentNumber: string; title: string; due: string };
export type DashApproval = { procedureId: string; procedureTitle: string; stage: string; role: string };
export type DashCr = {
	procedureId: string;
	procedureTitle: string;
	title: string;
	status: string;
	classification: string;
};
export type DashDev = { id: string; category: string; description: string; loggedAt: string; open: boolean };
export type Dash = {
	kpis: Kpi[];
	overdueReviews: DashOverdue[];
	approvals: DashApproval[];
	changeRequests: DashCr[];
	deviations: DashDev[];
};

export type Proc = {
	id: string;
	documentNumber: string;
	title: string;
	type: string;
	owner: string;
	state: string;
	effectiveDate: string | null;
	nextReviewDate: string | null;
};
export type LibPrereq = { id: string; type: string; text: string };
export type PType = { id: string; name: string };

export type Version = { id: string; label: string; createdAt: string; changeRequestId: string | null };
export type Detail = {
	id: string;
	documentNumber: string;
	title: string;
	state: string;
	currentVersionId: string | null;
	confidentialityLevelId: string | null;
	versions: Version[];
};

export type ConfLevel = { id: string; name: string; rank: number };
export type DetailCr = {
	id: string;
	title: string;
	status: string;
	classification: string;
	trainingImpact: boolean;
	createdAt: string;
};
export type DetailTask = {
	id: string;
	stage: string;
	role: string;
	meaning: string;
	status: string;
	due: string | null;
};
export type ChangeControl = { changeRequests: DetailCr[]; tasks: DetailTask[] };
export type Att = {
	id: string;
	filename: string;
	mime: string;
	size: number;
	sha256: string;
	uploadedAt: string;
};

export type ApprovalTask = {
	id: string;
	procedureId: string;
	procedureTitle: string;
	stage: string;
	role: string;
	meaning: string;
	status: string;
	due: string | null;
};

export type RunRow = {
	runId: string;
	procedureId: string;
	procedureTitle: string;
	state: string;
	startedAt: string;
	durationSeconds: number | null;
	deviationCount: number;
};
export type RunStats = {
	procedureId: string;
	procedureTitle: string;
	runCount: number;
	completionPct: number;
	avgDurationSeconds: number | null;
	deviationCount: number;
};
export type RunsOverview = { history: RunRow[]; analytics: RunStats[] };

export type Msg = {
	id: string;
	title: string;
	body: string;
	link: string | null;
	createdAt: string;
	unread: boolean;
};
export type Inbox = { unread: number; items: Msg[] };

// ---------------------------------------------------------------- query keys
export const qk = {
	me: ['me'] as const,
	dashboard: ['dashboard'] as const,
	proceduresAll: ['procedures'] as const,
	procedures: (state: string) => ['procedures', state] as const,
	procedureRoot: ['procedure'] as const,
	procedure: (id: string) => ['procedure', id] as const,
	detail: (id: string) => ['procedure', id, 'detail'] as const,
	body: (id: string) => ['procedure', id, 'body'] as const,
	changeRequests: (id: string) => ['procedure', id, 'changeRequests'] as const,
	attachments: (id: string) => ['procedure', id, 'attachments'] as const,
	prereqTypes: ['prereqTypes'] as const,
	confidentialityLevels: ['confidentialityLevels'] as const,
	prereqs: ['prereqs'] as const,
	approvals: ['approvals'] as const,
	runs: ['runs'] as const,
	notifications: ['notifications'] as const,
	exportTemplates: ['exportTemplates'] as const,
	notificationPrefs: ['notificationPrefs'] as const,
	scripts: ['scripts'] as const,
	script: (id: string) => ['scripts', id] as const,
	scriptVersions: (id: string) => ['scripts', id, 'versions'] as const
};

// ---------------------------------------------------------------- queries
export const useMe = () => useQuery({ queryKey: qk.me, queryFn: () => api.get<Me>('/api/v1/me') });

export const useDashboard = () =>
	useQuery({ queryKey: qk.dashboard, queryFn: () => api.get<Dash>('/api/v1/dashboard') });

export const useProcedures = (state: string) =>
	useQuery({ queryKey: qk.procedures(state), queryFn: () => api.get<Proc[]>(`/api/v1/procedures?state=${state}`) });

export const useProcedureDetail = (id: string) =>
	useQuery({ queryKey: qk.detail(id), queryFn: () => api.get<Detail>(`/api/v1/procedures/${id}/detail`), enabled: !!id });

export const useProcedureBody = (id: string) =>
	useQuery({ queryKey: qk.body(id), queryFn: () => api.get<{ body: string }>(`/api/v1/procedures/${id}/body`), enabled: !!id });

export const useChangeRequests = (id: string) =>
	useQuery({ queryKey: qk.changeRequests(id), queryFn: () => api.get<ChangeControl>(`/api/v1/procedures/${id}/change-requests`), enabled: !!id });

export const useAttachments = (id: string) =>
	useQuery({ queryKey: qk.attachments(id), queryFn: () => api.get<Att[]>(`/api/v1/procedures/${id}/attachments`), enabled: !!id });

export const usePrereqTypes = () =>
	useQuery({ queryKey: qk.prereqTypes, queryFn: () => api.get<PType[]>('/api/v1/prerequisite-types') });

export const usePrereqLib = () =>
	useQuery({ queryKey: qk.prereqs, queryFn: () => api.get<LibPrereq[]>('/api/v1/prerequisites') });

export const useApprovals = () =>
	useQuery({ queryKey: qk.approvals, queryFn: () => api.get<ApprovalTask[]>('/api/v1/approvals') });

export const useRuns = () =>
	useQuery({ queryKey: qk.runs, queryFn: () => api.get<RunsOverview>('/api/v1/runs') });

export const useNotifications = () =>
	useQuery({ queryKey: qk.notifications, queryFn: () => api.get<Inbox>('/api/v1/notifications') });

export const useExportTemplates = () =>
	useQuery({ queryKey: qk.exportTemplates, queryFn: () => api.get<ExportTemplate[]>('/api/v1/export-templates') });

// ---- scripts (proxied to the standalone script-service) ----
export const useScripts = () =>
	useQuery({ queryKey: qk.scripts, queryFn: () => api.get<ScriptRow[]>('/api/v1/scripts') });

export const useScriptVersions = (id: string) =>
	useQuery({
		queryKey: qk.scriptVersions(id),
		queryFn: () => api.get<ScriptVersionRow[]>(`/api/v1/scripts/${id}/versions`),
		enabled: !!id
	});

export const useScriptVersionContent = (id: string, no: number | null) =>
	useQuery({
		queryKey: [...qk.script(id), 'content', no] as const,
		queryFn: () => api.get<{ versionNo: number; content: string }>(`/api/v1/scripts/${id}/versions/${no}`),
		enabled: !!id && no != null
	});

// ---------------------------------------------------------------- mutations
export function useLogin() {
	return useMutation({
		mutationFn: (v: { username: string; password: string }) => login(v.username, v.password)
	});
}

export function useCreateProcedure() {
	const qc = useQueryClient();
	return useMutation({
		mutationFn: (v: { title: string; type: string; prerequisites: { type: string; text: string }[] }) =>
			api.post<{ id: string; documentNumber: string }>('/api/v1/procedures', v),
		onSuccess: () => qc.invalidateQueries({ queryKey: qk.proceduresAll })
	});
}

export function useSaveBody(id: string) {
	const qc = useQueryClient();
	return useMutation({
		mutationFn: (body: string) => api.put(`/api/v1/procedures/${id}/body`, { body }),
		onSuccess: () => qc.invalidateQueries({ queryKey: qk.procedure(id) })
	});
}

export function useSetVersionLabel(id: string) {
	const qc = useQueryClient();
	return useMutation({
		mutationFn: (label: string) => api.put(`/api/v1/procedures/${id}/version`, { label }),
		onSuccess: () => qc.invalidateQueries({ queryKey: qk.procedure(id) })
	});
}

// Admin-only: override the document number (server enforces hasRole('TENANT_ADMIN')).
export function useSetDocumentNumber(id: string) {
	const qc = useQueryClient();
	return useMutation({
		mutationFn: (documentNumber: string) =>
			api.put(`/api/v1/procedures/${id}/document-number`, { documentNumber }),
		onSuccess: () => {
			qc.invalidateQueries({ queryKey: qk.procedure(id) });
			qc.invalidateQueries({ queryKey: qk.proceduresAll });
		}
	});
}

export function useOpenChangeRequest(id: string) {
	const qc = useQueryClient();
	return useMutation({
		mutationFn: (v: { title: string; reason: string; classification: string; trainingImpact: boolean }) =>
			api.post(`/api/v1/procedures/${id}/change-requests`, v),
		onSuccess: () => {
			qc.invalidateQueries({ queryKey: qk.procedure(id) });
			qc.invalidateQueries({ queryKey: qk.approvals });
			qc.invalidateQueries({ queryKey: qk.dashboard });
		}
	});
}

export function useUploadAttachment(id: string) {
	const qc = useQueryClient();
	return useMutation({
		mutationFn: (file: File) => upload(`/api/v1/procedures/${id}/attachments`, file),
		onSuccess: () => qc.invalidateQueries({ queryKey: qk.attachments(id) })
	});
}

export function useDeleteAttachment(id: string) {
	const qc = useQueryClient();
	return useMutation({
		mutationFn: (attId: string) => api.del(`/api/v1/procedures/${id}/attachments/${attId}`),
		onSuccess: () => qc.invalidateQueries({ queryKey: qk.attachments(id) })
	});
}

export function useDecideApproval() {
	const qc = useQueryClient();
	return useMutation({
		mutationFn: (v: { taskId: string; approve: boolean; password: string | null; reason: string | null }) =>
			api.post(`/api/v1/approvals/${v.taskId}/decide`, {
				approve: v.approve,
				password: v.password,
				reason: v.reason
			}),
		onSuccess: () => {
			qc.invalidateQueries({ queryKey: qk.approvals });
			qc.invalidateQueries({ queryKey: qk.dashboard });
			qc.invalidateQueries({ queryKey: qk.procedureRoot });
			qc.invalidateQueries({ queryKey: qk.proceduresAll });
		}
	});
}

export function useMarkRead() {
	const qc = useQueryClient();
	return useMutation({
		mutationFn: (msgId: string) => api.post(`/api/v1/notifications/${msgId}/read`),
		onSuccess: () => qc.invalidateQueries({ queryKey: qk.notifications })
	});
}

export function useCreatePrereqType() {
	const qc = useQueryClient();
	return useMutation({
		mutationFn: (name: string) => api.post('/api/v1/prerequisite-types', { name }),
		onSuccess: () => qc.invalidateQueries({ queryKey: qk.prereqTypes })
	});
}

export function useRenamePrereqType() {
	const qc = useQueryClient();
	return useMutation({
		mutationFn: (v: { id: string; name: string }) => api.put(`/api/v1/prerequisite-types/${v.id}`, { name: v.name }),
		onSuccess: () => qc.invalidateQueries({ queryKey: qk.prereqTypes })
	});
}

export function useDeletePrereqType() {
	const qc = useQueryClient();
	return useMutation({
		mutationFn: (id: string) => api.del(`/api/v1/prerequisite-types/${id}`),
		onSuccess: () => qc.invalidateQueries({ queryKey: qk.prereqTypes })
	});
}

export const useConfidentialityLevels = () =>
	useQuery({
		queryKey: qk.confidentialityLevels,
		queryFn: () => api.get<ConfLevel[]>('/api/v1/confidentiality-levels')
	});

export function useCreateConfLevel() {
	const qc = useQueryClient();
	return useMutation({
		mutationFn: (v: { name: string; rank: number }) => api.post('/api/v1/confidentiality-levels', v),
		onSuccess: () => qc.invalidateQueries({ queryKey: qk.confidentialityLevels })
	});
}

export function useUpdateConfLevel() {
	const qc = useQueryClient();
	return useMutation({
		mutationFn: (v: { id: string; name: string; rank: number }) =>
			api.put(`/api/v1/confidentiality-levels/${v.id}`, { name: v.name, rank: v.rank }),
		onSuccess: () => qc.invalidateQueries({ queryKey: qk.confidentialityLevels })
	});
}

export function useDeleteConfLevel() {
	const qc = useQueryClient();
	return useMutation({
		mutationFn: (id: string) => api.del(`/api/v1/confidentiality-levels/${id}`),
		onSuccess: () => qc.invalidateQueries({ queryKey: qk.confidentialityLevels })
	});
}

export type NotificationPrefs = { emailNotifications: boolean; mutedCategories: string[] };

export const useNotificationPrefs = () =>
	useQuery({
		queryKey: qk.notificationPrefs,
		queryFn: () => api.get<NotificationPrefs>('/api/v1/me/notification-preferences')
	});

export function useUpdateDisplayName() {
	const qc = useQueryClient();
	return useMutation({
		mutationFn: (displayName: string) => api.put('/api/v1/me/display-name', { displayName }),
		onSuccess: () => qc.invalidateQueries({ queryKey: qk.me })
	});
}

export function useChangePassword() {
	return useMutation({
		mutationFn: (v: { currentPassword: string; newPassword: string }) =>
			api.put('/api/v1/me/password', v)
	});
}

export function useUpdateNotificationPrefs() {
	const qc = useQueryClient();
	return useMutation({
		mutationFn: (v: NotificationPrefs) => api.put('/api/v1/me/notification-preferences', v),
		onSuccess: () => qc.invalidateQueries({ queryKey: qk.notificationPrefs })
	});
}

// Classify a document with a confidentiality level (null clears it).
export function useSetConfidentiality(id: string) {
	const qc = useQueryClient();
	return useMutation({
		mutationFn: (levelId: string | null) =>
			api.put(`/api/v1/procedures/${id}/confidentiality`, { levelId }),
		onSuccess: () => qc.invalidateQueries({ queryKey: qk.procedure(id) })
	});
}

export function useCreatePrereq() {
	const qc = useQueryClient();
	return useMutation({
		mutationFn: (v: { type: string; text: string }) => api.post('/api/v1/prerequisites', v),
		onSuccess: () => qc.invalidateQueries({ queryKey: qk.prereqs })
	});
}

export function useUpdatePrereq() {
	const qc = useQueryClient();
	return useMutation({
		mutationFn: (v: { id: string; type: string; text: string }) =>
			api.put(`/api/v1/prerequisites/${v.id}`, { type: v.type, text: v.text }),
		onSuccess: () => qc.invalidateQueries({ queryKey: qk.prereqs })
	});
}

export function useDeletePrereq() {
	const qc = useQueryClient();
	return useMutation({
		mutationFn: (id: string) => api.del(`/api/v1/prerequisites/${id}`),
		onSuccess: () => qc.invalidateQueries({ queryKey: qk.prereqs })
	});
}

type TemplateInput = {
	name: string;
	accentColor: string;
	footerText: string;
	bodyFontPt: number;
	headingFontPt: number;
	tableFontPt: number;
};

export function useCreateTemplate() {
	const qc = useQueryClient();
	return useMutation({
		mutationFn: (v: TemplateInput) => api.post<ExportTemplate>('/api/v1/export-templates', v),
		onSuccess: () => qc.invalidateQueries({ queryKey: qk.exportTemplates })
	});
}

export function useUpdateTemplate() {
	const qc = useQueryClient();
	return useMutation({
		mutationFn: (v: { id: string } & TemplateInput) =>
			api.put(`/api/v1/export-templates/${v.id}`, {
				name: v.name,
				accentColor: v.accentColor,
				footerText: v.footerText,
				bodyFontPt: v.bodyFontPt,
				headingFontPt: v.headingFontPt,
				tableFontPt: v.tableFontPt
			}),
		onSuccess: () => qc.invalidateQueries({ queryKey: qk.exportTemplates })
	});
}

export function useDeleteTemplate() {
	const qc = useQueryClient();
	return useMutation({
		mutationFn: (id: string) => api.del(`/api/v1/export-templates/${id}`),
		onSuccess: () => qc.invalidateQueries({ queryKey: qk.exportTemplates })
	});
}

export function useUploadTemplateLogo() {
	const qc = useQueryClient();
	return useMutation({
		mutationFn: (v: { id: string; file: File }) =>
			upload<ExportTemplate>(`/api/v1/export-templates/${v.id}/logo`, v.file),
		onSuccess: () => qc.invalidateQueries({ queryKey: qk.exportTemplates })
	});
}

export function useCreateScript() {
	const qc = useQueryClient();
	return useMutation({
		mutationFn: (v: { name: string; language: string; description: string; content: string }) =>
			api.post<ScriptRow>('/api/v1/scripts', v),
		onSuccess: () => qc.invalidateQueries({ queryKey: qk.scripts })
	});
}

export function useUpdateScriptMeta() {
	const qc = useQueryClient();
	return useMutation({
		mutationFn: (v: { id: string; name: string; language: string; description: string }) =>
			api.put(`/api/v1/scripts/${v.id}`, { name: v.name, language: v.language, description: v.description }),
		onSuccess: () => qc.invalidateQueries({ queryKey: qk.scripts })
	});
}

export function useSaveScriptContent(id: string) {
	const qc = useQueryClient();
	return useMutation({
		mutationFn: (v: { content: string; note: string }) => api.put(`/api/v1/scripts/${id}/content`, v),
		onSuccess: () => {
			qc.invalidateQueries({ queryKey: qk.scripts });
			qc.invalidateQueries({ queryKey: qk.script(id) });
		}
	});
}

export function useRestoreScriptVersion(id: string) {
	const qc = useQueryClient();
	return useMutation({
		mutationFn: (no: number) => api.post(`/api/v1/scripts/${id}/restore/${no}`),
		onSuccess: () => {
			qc.invalidateQueries({ queryKey: qk.scripts });
			qc.invalidateQueries({ queryKey: qk.script(id) });
		}
	});
}

export function useDeleteScript() {
	const qc = useQueryClient();
	return useMutation({
		mutationFn: (id: string) => api.del(`/api/v1/scripts/${id}`),
		onSuccess: () => qc.invalidateQueries({ queryKey: qk.scripts })
	});
}
