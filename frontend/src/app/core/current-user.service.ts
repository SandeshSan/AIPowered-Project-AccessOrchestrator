import { Injectable, computed, signal } from '@angular/core';

import { User } from './models';

const STORAGE_KEY = 'pao.session';

/** A signed-in user plus the HTTP Basic token sent with every API call. */
export interface Session {
  user: User;
  /** base64(userId:password), sent as `Authorization: Basic <token>`. */
  token: string;
}

/**
 * The signed-in user. Kept in sessionStorage so a page reload keeps you signed in, while closing the tab
 * signs you out. Deliberately has no HTTP dependency: the HTTP interceptor reads it.
 */
@Injectable({ providedIn: 'root' })
export class CurrentUserService {
  readonly session = signal<Session | null>(readStored());
  readonly user = computed(() => this.session()?.user ?? null);
  /** Empty when signed out. */
  readonly userId = computed(() => this.session()?.user.userId ?? '');
  readonly signedIn = computed(() => this.session() !== null);
  /** UI hint only; the backend enforces admin-only endpoints itself. */
  readonly isAdmin = computed(() => this.user()?.admin ?? false);

  signIn(user: User, token: string): void {
    this.store({ user, token });
  }

  signOut(): void {
    this.store(null);
  }

  private store(session: Session | null): void {
    this.session.set(session);
    try {
      if (session) {
        sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session));
      } else {
        sessionStorage.removeItem(STORAGE_KEY);
      }
    } catch {
      /* storage unavailable (private mode): you stay signed in until the page reloads */
    }
  }
}

/** The Basic credential for a user ID and password (UTF-8 safe). */
export function basicToken(userId: string, password: string): string {
  const bytes = new TextEncoder().encode(`${userId}:${password}`);
  return btoa(String.fromCharCode(...bytes));
}

function readStored(): Session | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    return raw ? (JSON.parse(raw) as Session) : null;
  } catch {
    return null;
  }
}
