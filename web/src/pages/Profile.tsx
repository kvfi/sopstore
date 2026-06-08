import { useEffect, useState } from 'react';
import { Button, Callout, Card, FormGroup, InputGroup, Spinner, Switch } from '@blueprintjs/core';
import { ApiError } from '../lib/api';
import { toast } from '../lib/toaster';
import {
	useMe,
	useNotificationPrefs,
	useUpdateDisplayName,
	useChangePassword,
	useUpdateNotificationPrefs
} from '../lib/queries';

// Notification categories the user can mute. The key is the notification template-key prefix the
// backend matches against (e.g. "workflow.task.assigned" → category "workflow").
const CATEGORIES: { key: string; label: string; description: string }[] = [
	{
		key: 'workflow',
		label: 'Approvals & change requests',
		description: 'Approval tasks assigned to you, overdue reminders, and workflow outcomes.'
	}
];

export default function Profile() {
	const meQ = useMe();
	const prefsQ = useNotificationPrefs();
	const updateName = useUpdateDisplayName();
	const changePassword = useChangePassword();
	const updatePrefs = useUpdateNotificationPrefs();

	const [name, setName] = useState('');
	const [curPw, setCurPw] = useState('');
	const [newPw, setNewPw] = useState('');
	const [confirmPw, setConfirmPw] = useState('');

	useEffect(() => {
		if (meQ.data) setName(meQ.data.displayName);
	}, [meQ.data]);

	if (meQ.isLoading || prefsQ.isLoading) {
		return (
			<div className="center-screen">
				<Spinner />
			</div>
		);
	}
	const me = meQ.data;
	const prefs = prefsQ.data ?? { emailNotifications: true, mutedCategories: [] };
	if (!me) return null;

	async function saveName() {
		try {
			await updateName.mutateAsync(name.trim());
			toast('Display name updated', 'success');
		} catch (e) {
			toast((e as Error).message, 'danger');
		}
	}

	async function savePassword() {
		if (newPw.length < 8) {
			toast('New password must be at least 8 characters.', 'warning');
			return;
		}
		if (newPw !== confirmPw) {
			toast('New passwords do not match.', 'warning');
			return;
		}
		try {
			await changePassword.mutateAsync({ currentPassword: curPw, newPassword: newPw });
			setCurPw('');
			setNewPw('');
			setConfirmPw('');
			toast('Password changed', 'success');
		} catch (e) {
			const msg =
				e instanceof ApiError && e.status === 403
					? 'Current password is incorrect.'
					: (e as Error).message;
			toast(msg, 'danger');
		}
	}

	async function savePrefs(next: { emailNotifications: boolean; mutedCategories: string[] }) {
		try {
			await updatePrefs.mutateAsync(next);
		} catch (e) {
			toast((e as Error).message, 'danger');
		}
	}

	const toggleCategory = (key: string, enabled: boolean) => {
		const muted = new Set(prefs.mutedCategories);
		if (enabled) muted.delete(key);
		else muted.add(key);
		savePrefs({ emailNotifications: prefs.emailNotifications, mutedCategories: [...muted] });
	};

	return (
		<>
			<h1 className="page-title">Profile</h1>
			<p className="page-sub">Manage your account details, password, and notification preferences.</p>

			<Card style={{ maxWidth: 560, marginBottom: 16 }}>
				<h3 style={{ marginTop: 0 }}>Account</h3>
				<FormGroup label="Email" helperText="Your email is your sign-in identity and can't be changed here.">
					<InputGroup value={me.email} disabled />
				</FormGroup>
				<FormGroup label="Display name">
					<div style={{ display: 'flex', gap: 8 }}>
						<InputGroup fill value={name} onChange={(e) => setName(e.currentTarget.value)} />
						<Button
							intent="primary"
							onClick={saveName}
							loading={updateName.isPending}
							disabled={!name.trim() || name.trim() === me.displayName}
							text="Save"
						/>
					</div>
				</FormGroup>
			</Card>

			<Card style={{ maxWidth: 560, marginBottom: 16 }}>
				<h3 style={{ marginTop: 0 }}>Change password</h3>
				<FormGroup label="Current password">
					<InputGroup type="password" value={curPw} onChange={(e) => setCurPw(e.currentTarget.value)} />
				</FormGroup>
				<FormGroup label="New password" helperText="At least 8 characters.">
					<InputGroup type="password" value={newPw} onChange={(e) => setNewPw(e.currentTarget.value)} />
				</FormGroup>
				<FormGroup label="Confirm new password">
					<InputGroup type="password" value={confirmPw} onChange={(e) => setConfirmPw(e.currentTarget.value)} />
				</FormGroup>
				<Button
					intent="primary"
					onClick={savePassword}
					loading={changePassword.isPending}
					disabled={!curPw || !newPw || !confirmPw}
					text="Change password"
				/>
			</Card>

			<Card style={{ maxWidth: 560 }}>
				<h3 style={{ marginTop: 0 }}>Notifications</h3>
				<Switch
					checked={prefs.emailNotifications}
					label="Email notifications"
					onChange={(e) =>
						savePrefs({
							emailNotifications: e.currentTarget.checked,
							mutedCategories: prefs.mutedCategories
						})
					}
				/>
				<Callout style={{ margin: '8px 0 12px' }}>
					In-app notifications always appear in your inbox. Email delivery and the categories below
					are opt-out.
				</Callout>
				{CATEGORIES.map((c) => (
					<Switch
						key={c.key}
						checked={!prefs.mutedCategories.includes(c.key)}
						label={c.label}
						onChange={(e) => toggleCategory(c.key, e.currentTarget.checked)}
					/>
				))}
			</Card>
		</>
	);
}
