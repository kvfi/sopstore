import { Outlet } from 'react-router-dom';
import { NonIdealState, Spinner } from '@blueprintjs/core';
import { useMe } from '../lib/queries';

/**
 * Settings guard: gates the whole /settings area behind the admin role, then
 * renders the matched child route (hub or a dedicated section page).
 */
export default function SettingsLayout() {
	const { data: me } = useMe();

	if (!me) {
		return (
			<div className="center-screen">
				<Spinner />
			</div>
		);
	}

	const isAdmin = me.roles.includes('TENANT_ADMIN') || me.roles.includes('SUPER_ADMIN');
	if (!isAdmin) {
		return (
			<NonIdealState
				icon="lock"
				title="Administrators only"
				description="Settings require the Tenant Admin role."
			/>
		);
	}

	return <Outlet />;
}
