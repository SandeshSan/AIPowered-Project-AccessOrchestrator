import { Injectable, inject, signal } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { RouterStateSnapshot, TitleStrategy } from '@angular/router';

/** Sets "<Page> · Access Orchestrator" as the tab title and exposes the page name for the toolbar. */
@Injectable({ providedIn: 'root' })
export class AppTitleStrategy extends TitleStrategy {
  private readonly title = inject(Title);
  readonly page = signal('');

  override updateTitle(snapshot: RouterStateSnapshot): void {
    const page = this.buildTitle(snapshot) ?? '';
    this.page.set(page);
    this.title.setTitle(page ? `${page} · Access Orchestrator` : 'Access Orchestrator');
  }
}
