import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { App } from './app/app';

bootstrapApplication(App, appConfig)
  .catch((err) => console.error(err));

if (window.location.hostname !== 'localhost') {
  console.log = () => {};
  console.warn = () => {};
  console.error = () => {};
}
