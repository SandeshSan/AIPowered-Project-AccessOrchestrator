import { TextFieldModule } from '@angular/cdk/text-field';
import { DatePipe } from '@angular/common';
import { Component, ElementRef, computed, effect, inject, input, signal, untracked, viewChild } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { catchError, of, switchMap } from 'rxjs';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink } from '@angular/router';

import { ApiService } from '../../core/api.service';
import { CurrentUserService } from '../../core/current-user.service';
import { AgentAccessItem, Capabilities } from '../../core/models';
import { RiskBadgeComponent } from '../../shared/risk-badge.component';
import { StatusChipComponent } from '../../shared/status-chip.component';
import { AssistantStore, ChatMessage } from './assistant.store';

@Component({
  selector: 'app-assistant',
  standalone: true,
  imports: [
    DatePipe, FormsModule, RouterLink, TextFieldModule, MatButtonModule, MatCheckboxModule, MatIconModule,
    MatProgressSpinnerModule, MatTooltipModule, StatusChipComponent, RiskBadgeComponent,
  ],
  templateUrl: './assistant.component.html',
  styleUrl: './assistant.component.scss',
})
export class AssistantComponent {
  protected readonly store = inject(AssistantStore);
  protected readonly currentUser = inject(CurrentUserService);
  private readonly api = inject(ApiService);

  /** Optional prefilled question, e.g. from the dashboard or a project page (?q=...). */
  readonly q = input<string | undefined>();

  protected readonly draft = signal('');
  protected readonly expandedTools = signal<Set<number>>(new Set());

  /** Entitlements ticked in the open offer; defaults to everything requestable. */
  protected readonly selected = signal<Set<number>>(new Set());
  protected readonly requestable = computed(() => this.store.openOffer()?.analysis.requestableEntitlementIds ?? []);
  protected readonly selectedCount = computed(() => this.requestable().filter(id => this.selected().has(id)).length);
  protected readonly allSelected = computed(() => this.selectedCount() === this.requestable().length);

  private readonly scroller = viewChild<ElementRef<HTMLElement>>('scroller');

  /** People who report to the user; drives the manager suggestion. */
  private readonly capabilities = toSignal(
    toObservable(this.currentUser.userId).pipe(switchMap(() => this.api.capabilities().pipe(
      catchError(() => of({ admin: false, reports: [] } as Capabilities))))),
    { initialValue: { admin: false, reports: [] } as Capabilities },
  );

  protected readonly suggestions = computed(() => {
    const base = [
      'I just joined the Novatech project. Can you get me the access I need?',
      'What access do I currently have?',
      "I'm leaving a project. Please remove me and my access.",
    ];
    const report = this.capabilities().reports.find(r => r.level === 1 && r.projects.length);
    if (report) {
      base.push(`Remove ${report.user.name} from the ${report.projects[0].projectName} project`);
    }
    if (this.capabilities().reports.length) {
      base.push('Who is in my team and which projects are they on?');
      base.push('Add a team member to a project');
    }
    return base;
  });

  /** Reason typed into the removal confirmation box. */
  protected readonly removalReason = signal('');

  constructor() {
    effect(() => {
      const q = this.q();
      if (q) {
        this.draft.set(q);
      }
    }, { allowSignalWrites: true });

    // A new offer starts with everything ticked
    effect(() => {
      const ids = this.requestable();
      untracked(() => this.selected.set(new Set(ids)));
    }, { allowSignalWrites: true });

    // A new removal preview starts with an empty reason
    effect(() => {
      this.store.openRemoval();
      untracked(() => this.removalReason.set(''));
    }, { allowSignalWrites: true });

    // Keep the latest message in view, after the new message and its cards have rendered
    effect(onCleanup => {
      this.store.messages();
      this.store.pending();
      const handle = setTimeout(() => {
        const el = this.scroller()?.nativeElement;
        el?.scrollTo({ top: el.scrollHeight, behavior: 'smooth' });
      }, 50);
      onCleanup(() => clearTimeout(handle));
    });
  }

  protected send(text = this.draft()): void {
    if (!text.trim()) {
      return;
    }
    this.store.send(text);
    this.draft.set('');
  }

  /** Confirm the ticked entitlements; the backend narrows the proposal to exactly these ids. */
  protected submitSelection(): void {
    const offer = this.store.openOffer();
    if (!offer || this.selectedCount() === 0) {
      return;
    }
    const ids = this.requestable().filter(id => this.selected().has(id));
    if (this.allSelected()) {
      this.store.send('Yes, submit the missing access requests', ids);
      return;
    }
    const codes = offer.analysis.missing.filter(i => ids.includes(i.entitlementId)).map(i => i.entitlementCode);
    this.store.send(`Yes, submit only the selected access: ${codes.join(', ')}`, ids);
  }

  /**
   * Confirm the pending removal with the typed reason. The reason goes to the backend as a separate field, so a
   * reason such as "No longer on the team" is not mistaken for a "no".
   */
  protected confirmRemoval(): void {
    const removal = this.store.openRemoval();
    const reason = this.removalReason().trim();
    if (!removal || reason.length < 3) {
      return;
    }
    const p = removal.preview;
    const who = p.selfRemoval ? 'me' : p.employeeName;
    this.store.send(`Yes, remove ${who} from ${p.projectName}. Reason: ${reason}`, undefined, reason);
  }

  /** Confirm the pending addition with the button (sent as a flag, so wording cannot be misread). */
  protected confirmAddition(): void {
    const addition = this.store.openAddition();
    if (!addition) {
      return;
    }
    const p = addition.preview;
    this.store.send(`Yes, add ${p.employeeName} to ${p.projectName}`, undefined, undefined, true);
  }

  protected isSelectable(messageId: number, item: AgentAccessItem): boolean {
    return this.store.openOffer()?.messageId === messageId && this.requestable().includes(item.entitlementId);
  }

  protected toggle(id: number, checked: boolean): void {
    this.selected.update(s => {
      const next = new Set(s);
      checked ? next.add(id) : next.delete(id);
      return next;
    });
  }

  protected selectAll(all: boolean): void {
    this.selected.set(new Set(all ? this.requestable() : []));
  }

  protected onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.send();
    }
  }

  protected toggleTools(id: number): void {
    this.expandedTools.update(s => {
      const next = new Set(s);
      next.has(id) ? next.delete(id) : next.add(id);
      return next;
    });
  }

  protected isLast(m: ChatMessage): boolean {
    return this.store.messages().at(-1)?.id === m.id;
  }
}
