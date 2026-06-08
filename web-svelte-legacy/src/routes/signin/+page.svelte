<script lang="ts">
	import { login } from '$lib/api';
	import { goto } from '$app/navigation';
	import { Button, Input, Label, Alert } from 'flowbite-svelte';

	let email = $state('admin@example.com');
	let password = $state('admin');
	let error = $state('');
	let busy = $state(false);

	async function submit(e: Event) {
		e.preventDefault();
		busy = true;
		error = '';
		try {
			await login(email, password);
			await goto('/');
		} catch {
			error = 'Invalid email or password.';
		} finally {
			busy = false;
		}
	}
</script>

<svelte:head><title>Sign in · sopstore</title></svelte:head>

<div class="auth">
	<form class="auth-card" onsubmit={submit}>
		<div class="brand"><span class="dot"></span> sopstore</div>
		<p class="page-sub" style="margin: 6px 0 22px">Quality &amp; SOP control platform</p>

		{#if error}
			<Alert color="red" class="mb-4">{error}</Alert>
		{/if}

		<div class="mb-4">
			<Label class="mb-2">Email</Label>
			<Input type="email" bind:value={email} autocomplete="username" required />
		</div>
		<div class="mb-5">
			<Label class="mb-2">Password</Label>
			<Input type="password" bind:value={password} autocomplete="current-password" required />
		</div>
		<Button type="submit" color="green" class="w-full" disabled={busy}>
			{busy ? 'Signing in…' : 'Sign in'}
		</Button>
		<p class="page-sub" style="text-align: center; margin-top: 16px">
			Dev login is pre-filled. SSO available on the server login page.
		</p>
	</form>
</div>
