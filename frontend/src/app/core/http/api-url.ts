import { APP_CONFIG } from '../config/app-config';

export function apiUrl(path: string): string {
  return `${APP_CONFIG.apiBaseUrl}${path.startsWith('/') ? path : `/${path}`}`;
}
