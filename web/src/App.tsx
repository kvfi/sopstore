import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import AppLayout from './components/AppLayout';
import SignIn from './pages/SignIn';
import Dashboard from './pages/Dashboard';
import Procedures from './pages/Procedures';
import ProcedureDetail from './pages/ProcedureDetail';
import Approvals from './pages/Approvals';
import Notifications from './pages/Notifications';
import Runs from './pages/Runs';
import Scripts from './pages/Scripts';
import ScriptDetail from './pages/ScriptDetail';
import Profile from './pages/Profile';
import SettingsLayout from './pages/SettingsLayout';
import SettingsHome from './pages/SettingsHome';
import SettingsPage from './components/SettingsPage';
import { SETTINGS_SECTIONS } from './pages/settings/registry';

export default function App() {
	return (
		<BrowserRouter>
			<Routes>
				<Route path="/signin" element={<SignIn />} />
				<Route element={<AppLayout />}>
					<Route path="/" element={<Dashboard />} />
					<Route path="/procedures" element={<Procedures />} />
					<Route path="/procedures/:id" element={<ProcedureDetail />} />
					<Route path="/approvals" element={<Approvals />} />
					<Route path="/notifications" element={<Notifications />} />
					<Route path="/runs" element={<Runs />} />
					<Route path="/scripts" element={<Scripts />} />
					<Route path="/scripts/new" element={<ScriptDetail />} />
					<Route path="/scripts/:id" element={<ScriptDetail />} />
					<Route path="/profile" element={<Profile />} />
					<Route path="/settings" element={<SettingsLayout />}>
						<Route index element={<SettingsHome />} />
						{SETTINGS_SECTIONS.map((s) => (
							<Route
								key={s.path}
								path={s.path}
								element={<SettingsPage title={s.label}>{s.element}</SettingsPage>}
							/>
						))}
					</Route>
				</Route>
				<Route path="*" element={<Navigate to="/" replace />} />
			</Routes>
		</BrowserRouter>
	);
}
