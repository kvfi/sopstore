import { OverlayToaster, Position, type Intent, type Toaster } from '@blueprintjs/core';

// Lazily create a single app-wide toaster (React 18 createRoot path).
let toasterPromise: Promise<Toaster> | null = null;
function getToaster(): Promise<Toaster> {
	if (!toasterPromise) {
		toasterPromise = OverlayToaster.createAsync({ position: Position.TOP });
	}
	return toasterPromise;
}

export async function toast(message: string, intent: Intent = 'none'): Promise<void> {
	(await getToaster()).show({ message, intent, timeout: 3800 });
}
