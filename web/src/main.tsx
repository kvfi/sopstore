import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { FocusStyleManager } from '@blueprintjs/core';
import { QueryClient, QueryClientProvider, QueryCache } from '@tanstack/react-query';

import 'normalize.css';
import '@blueprintjs/icons/lib/css/blueprint-icons.css';
import '@blueprintjs/core/lib/css/blueprint.css';
import '@blueprintjs/select/lib/css/blueprint-select.css';
import './app.css';

import App from './App';
import { ApiError } from './lib/api';
import { toast } from './lib/toaster';

FocusStyleManager.onlyShowFocusOnTabs();

const queryClient = new QueryClient({
	// Surface read failures once, centrally. 401s are handled by the api layer (redirect to /signin),
	// so we don't toast those. Mutations report their own errors at the call site.
	queryCache: new QueryCache({
		onError: (error) => {
			if (error instanceof ApiError && error.status === 401) return;
			toast((error as Error).message || 'Request failed', 'danger');
		}
	}),
	defaultOptions: {
		queries: {
			retry: false,
			refetchOnWindowFocus: false,
			staleTime: 15_000
		}
	}
});

createRoot(document.getElementById('root')!).render(
	<StrictMode>
		<QueryClientProvider client={queryClient}>
			<App />
		</QueryClientProvider>
	</StrictMode>
);
