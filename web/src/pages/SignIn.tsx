import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { Card, FormGroup, InputGroup, Button, Callout } from '@blueprintjs/core';
import { useLogin } from '../lib/queries';

export default function SignIn() {
	const navigate = useNavigate();
	const login = useLogin();
	const [email, setEmail] = useState('admin@example.com');
	const [password, setPassword] = useState('admin');
	const [error, setError] = useState('');
	const busy = login.isPending;

	async function submit(e: FormEvent) {
		e.preventDefault();
		setError('');
		try {
			await login.mutateAsync({ username: email, password });
			navigate('/');
		} catch {
			setError('Invalid email or password.');
		}
	}

	return (
		<div className="auth">
			<Card className="auth-card" elevation={2}>
				<div className="auth-brand">
					<span className="mark">
						<span />
					</span>
					sopstore
				</div>
				<p className="page-sub" style={{ marginTop: 6 }}>
					Quality &amp; SOP control platform
				</p>

				{error && (
					<Callout intent="danger" style={{ marginBottom: 14 }}>
						{error}
					</Callout>
				)}

				<form onSubmit={submit}>
					<FormGroup label="Email" labelFor="email">
						<InputGroup
							id="email"
							type="email"
							value={email}
							onChange={(e) => setEmail(e.currentTarget.value)}
							autoComplete="username"
							large
							fill
						/>
					</FormGroup>
					<FormGroup label="Password" labelFor="password">
						<InputGroup
							id="password"
							type="password"
							value={password}
							onChange={(e) => setPassword(e.currentTarget.value)}
							autoComplete="current-password"
							large
							fill
						/>
					</FormGroup>
					<Button type="submit" intent="primary" large fill loading={busy} text="Sign in" />
				</form>

				<p className="page-sub" style={{ textAlign: 'center', marginTop: 16, marginBottom: 0 }}>
					Dev login is pre-filled. SSO available on the server login page.
				</p>
			</Card>
		</div>
	);
}
