import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, effect, inject, signal, untracked } from '@angular/core';

import { ApiService } from '../../core/api.service';
import { CurrentUserService } from '../../core/current-user.service';
import { errorMessage } from '../../core/format';
import { ChatResponse, PendingAction } from '../../core/models';

export interface ChatMessage {
  id: number;
  role: 'user' | 'assistant' | 'error';
  text: string;
  at: Date;
  response?: ChatResponse;
}

interface Conversation {
  conversationId: string | null;
  messages: ChatMessage[];
}

const EMPTY: Conversation = { conversationId: null, messages: [] };

/** Keeps one conversation per demo user for the lifetime of the page, so navigation doesn't lose the chat. */
@Injectable({ providedIn: 'root' })
export class AssistantStore {
  private readonly api = inject(ApiService);
  private readonly currentUser = inject(CurrentUserService);

  private readonly conversations = signal<Record<string, Conversation>>({});
  private nextId = 1;

  constructor() {
    // Signing out forgets every conversation held in this browser tab
    effect(() => {
      if (!this.currentUser.signedIn()) {
        untracked(() => this.conversations.set({}));
      }
    }, { allowSignalWrites: true });
  }

  readonly pending = signal(false);
  /** Set when the backend reports the AI model is not configured (HTTP 503). */
  readonly notConfigured = signal<string | null>(null);

  readonly conversation = computed(() => this.conversations()[this.currentUser.userId()] ?? EMPTY);
  readonly messages = computed(() => this.conversation().messages);

  /** The assistant is waiting for a yes/no on the latest message. */
  readonly awaitingConfirmation = computed(() => {
    const last = this.messages().at(-1);
    return !this.pending() && last?.role === 'assistant' && !!last.response?.awaitingConfirmation;
  });

  /** Everything awaiting confirmation after the latest reply (a "move" is a removal and an addition). */
  readonly pendingActions = computed<PendingAction[]>(() => {
    const last = this.messages().at(-1);
    if (this.pending() || last?.role !== 'assistant' || !last.response?.awaitingConfirmation) {
      return [];
    }
    const r = last.response;
    return r.pendingActions ?? (r.pendingAction ? [r.pendingAction] : ['ACCESS_REQUEST']);
  });

  /** The most recent removal preview, while it is waiting for confirmation. */
  readonly openRemoval = computed(() => {
    if (!this.pendingActions().includes('PROJECT_REMOVAL')) {
      return null;
    }
    const withPreview = [...this.messages()].reverse().find(m => m.response?.removalPreview);
    return withPreview ? { messageId: withPreview.id, preview: withPreview.response!.removalPreview! } : null;
  });

  /** The most recent addition preview, while it is waiting for confirmation. */
  readonly openAddition = computed(() => {
    if (!this.pendingActions().includes('PROJECT_ADDITION')) {
      return null;
    }
    const withPreview = [...this.messages()].reverse().find(m => m.response?.additionPreview);
    return withPreview ? { messageId: withPreview.id, preview: withPreview.response!.additionPreview! } : null;
  });

  /** The most recent gap analysis, while its proposal is still waiting for a yes/no. */
  readonly openOffer = computed(() => {
    if (!this.pendingActions().includes('ACCESS_REQUEST')) {
      return null;
    }
    const withAnalysis = [...this.messages()].reverse().find(m => m.response?.analysis);
    return withAnalysis ? { messageId: withAnalysis.id, analysis: withAnalysis.response!.analysis! } : null;
  });

  send(text: string, selectedEntitlementIds?: number[], removalReason?: string, confirmAddition?: boolean): void {
    const message = text.trim();
    if (!message || this.pending()) {
      return;
    }
    const userId = this.currentUser.userId();
    this.append(userId, { role: 'user', text: message });
    this.pending.set(true);

    const options = {
      ...(selectedEntitlementIds ? { selectedEntitlementIds } : {}),
      ...(removalReason ? { removalReason } : {}),
      ...(confirmAddition ? { confirmAddition } : {}),
    };
    this.api.chat(message, this.conversations()[userId]?.conversationId ?? null, options).subscribe({
      next: response => {
        this.notConfigured.set(null);
        this.update(userId, c => ({ ...c, conversationId: response.conversationId }));
        this.append(userId, { role: 'assistant', text: response.reply, response });
        this.pending.set(false);
      },
      error: (err: unknown) => {
        if (err instanceof HttpErrorResponse && err.status === 503) {
          this.notConfigured.set(errorMessage(err)); // shown once as a banner, not repeated per message
        } else {
          this.append(userId, { role: 'error', text: errorMessage(err) });
        }
        this.pending.set(false);
      },
    });
  }

  reset(): void {
    const userId = this.currentUser.userId();
    this.conversations.update(all => ({ ...all, [userId]: EMPTY }));
  }

  private append(userId: string, m: Omit<ChatMessage, 'id' | 'at'>): void {
    this.update(userId, c => ({ ...c, messages: [...c.messages, { ...m, id: this.nextId++, at: new Date() }] }));
  }

  private update(userId: string, fn: (c: Conversation) => Conversation): void {
    this.conversations.update(all => ({ ...all, [userId]: fn(all[userId] ?? EMPTY) }));
  }
}
