import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import {
  AccessComparison, AccessRequest, Capabilities, ChatResponse, Dashboard, Project, ProjectAccess, ProjectMembership,
  TeamMember, User, UserAccess,
} from './models';

/** Thin typed client for the backend REST API (proxied under /api in development). */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  /** Checks a user ID and password (HTTP Basic); resolves to the user, or errors with 401. */
  login(token: string): Observable<User> {
    return this.http.get<User>('/api/me', { headers: { Authorization: `Basic ${token}` } });
  }

  me(): Observable<User> {
    return this.http.get<User>('/api/me');
  }

  users(): Observable<User[]> {
    return this.http.get<User[]>('/api/users');
  }

  dashboard(userId: string): Observable<Dashboard> {
    return this.http.get<Dashboard>(`/api/users/${enc(userId)}/dashboard`);
  }

  myAccess(userId: string): Observable<UserAccess[]> {
    return this.http.get<UserAccess[]>(`/api/users/${enc(userId)}/access`);
  }

  myProjects(userId: string): Observable<ProjectMembership[]> {
    return this.http.get<ProjectMembership[]>(`/api/users/${enc(userId)}/projects`);
  }

  projects(): Observable<Project[]> {
    return this.http.get<Project[]>('/api/projects');
  }

  project(id: number): Observable<Project> {
    return this.http.get<Project>(`/api/projects/${id}`);
  }

  projectAccessProfile(id: number): Observable<ProjectAccess[]> {
    return this.http.get<ProjectAccess[]>(`/api/projects/${id}/access`);
  }

  compare(userId: string, projectId: number): Observable<AccessComparison> {
    return this.http.post<AccessComparison>('/api/access/compare', { userId, projectId });
  }

  requests(userId: string): Observable<AccessRequest[]> {
    return this.http.get<AccessRequest[]>('/api/access/requests', { params: { userId } });
  }

  /** Requests the user started for other people (e.g. removals as a manager). */
  requestsCreatedBy(userId: string): Observable<AccessRequest[]> {
    return this.http.get<AccessRequest[]>('/api/access/requests', { params: { createdBy: userId } });
  }

  /** The signed-in user's reportees with their projects and active access. */
  team(): Observable<TeamMember[]> {
    return this.http.get<TeamMember[]>('/api/team');
  }

  capabilities(): Observable<Capabilities> {
    return this.http.get<Capabilities>('/api/me/capabilities');
  }

  syncRequest(requestId: string): Observable<AccessRequest> {
    return this.http.post<AccessRequest>(`/api/access/requests/${enc(requestId)}/sync`, null);
  }

  submitRequest(requestId: string): Observable<AccessRequest> {
    return this.http.post<AccessRequest>(`/api/access/requests/${enc(requestId)}/submit`, null);
  }

  /**
   * @param options.selectedEntitlementIds when confirming access, the subset of proposed entitlements ticked
   * @param options.removalReason          when confirming a removal with the button, the reason typed
   * @param options.confirmAddition        when confirming an addition with the button
   */
  chat(message: string, conversationId: string | null,
       options: { selectedEntitlementIds?: number[]; removalReason?: string; confirmAddition?: boolean } = {},
  ): Observable<ChatResponse> {
    return this.http.post<ChatResponse>('/api/agent/chat', { message, conversationId, ...options });
  }
}

function enc(value: string): string {
  return encodeURIComponent(value);
}
