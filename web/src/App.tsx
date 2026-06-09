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
import Profile from './pages/Profile';
import SettingsLayout from './pages/SettingsLayout';
import PrerequisiteTypes from './pages/settings/PrerequisiteTypes';
import Prerequisites from './pages/settings/Prerequisites';
import ExportTemplates from './pages/settings/ExportTemplates';
import ConfidentialityLevels from './pages/settings/ConfidentialityLevels';
import ScriptBundleSettingsPage from './pages/settings/ScriptBundleSettings';

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
					<Route path="/profile" element={<Profile />} />
					<Route path="/settings" element={<SettingsLayout />}>
						<Route index element={<Navigate to="configuration/prerequisite-types" replace />} />
						<Route path="configuration/prerequisite-types" element={<PrerequisiteTypes />} />
						<Route path="configuration/prerequisites" element={<Prerequisites />} />
						<Route path="configuration/export-templates" element={<ExportTemplates />} />
						<Route path="configuration/script-bundles" element={<ScriptBundleSettingsPage />} />
						<Route path="configuration/confidentiality-levels" element={<ConfidentialityLevels />} />
					</Route>
				</Route>
				<Route path="*" element={<Navigate to="/" replace />} />
			</Routes>
		</BrowserRouter>
	);
}
