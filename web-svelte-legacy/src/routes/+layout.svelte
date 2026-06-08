<script lang="ts">
	import './layout.css';
	import favicon from '$lib/assets/favicon.svg';
	import { toasts } from '$lib/stores';
	import { Toast } from 'flowbite-svelte';

	let { children } = $props();

	const color = (k: string) => (k === 'err' ? 'red' : k === 'ok' ? 'green' : 'primary');
</script>

<svelte:head>
	<link rel="icon" href={favicon} />
	<title>sopstore</title>
</svelte:head>

{@render children()}

<div class="fixed bottom-5 right-5 z-[80] flex flex-col gap-3">
	{#each $toasts as t (t.id)}
		<Toast color={color(t.kind)} dismissable={false}>{t.msg}</Toast>
	{/each}
</div>
