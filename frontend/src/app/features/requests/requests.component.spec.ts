import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';

import { CurrentUserService } from '../../core/current-user.service';
import { AccessRequest, RequestStatus, User } from '../../core/models';
import { RequestsComponent } from './requests.component';

describe('RequestsComponent', () => {
  let fixture: ComponentFixture<RequestsComponent>;

  function request(id: string, status: RequestStatus): AccessRequest {
    return {
      requestId: id, type: 'GRANT', userId: 'NT10036', projectId: 1, projectName: 'Novatech', status,
      igaRequestId: null, createdAt: '2026-09-27T09:00:00Z', updatedAt: '2026-09-27T09:00:00Z', createdBy: 'NT10036',
      justification: null, items: [], lifecycle: [],
    };
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RequestsComponent],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(), provideNoopAnimations()],
    }).compileComponents();
    TestBed.inject(CurrentUserService).signIn({ userId: 'NT10036', name: 'John' } as User, 'token');

    fixture = TestBed.createComponent(RequestsComponent);
    fixture.detectChanges();
    const http = TestBed.inject(HttpTestingController);
    http.expectOne(r => r.url === '/api/access/requests' && r.params.get('userId') === 'NT10036').flush([
      request('REQ-1', 'PROVISIONED'),
      request('REQ-2', 'REJECTED'),
      request('REQ-3', 'CANCELLED'),
      request('REQ-4', 'PENDING_APPROVAL'),
      request('REQ-5', 'DRAFT'),
    ]);
    http.expectOne(r => r.url === '/api/access/requests' && r.params.get('createdBy') === 'NT10036').flush([]);
    fixture.detectChanges();
  });

  afterEach(() => {
    fixture.destroy(); // stops the live polling started by the open request
    TestBed.inject(CurrentUserService).signOut();
  });

  function toggles(): string[] {
    return Array.from(fixture.nativeElement.querySelectorAll('mat-button-toggle') as NodeListOf<HTMLElement>)
      .map(t => t.textContent!.trim());
  }

  function show(label: string): string[] {
    const toggle = Array.from(fixture.nativeElement.querySelectorAll('mat-button-toggle button') as NodeListOf<HTMLElement>)
      .find(b => b.textContent!.includes(label))!;
    toggle.click();
    fixture.detectChanges();
    return Array.from(fixture.nativeElement.querySelectorAll('article.request h2') as NodeListOf<HTMLElement>)
      .map(h => h.textContent!.trim());
  }

  it('shows a count on every filter, and In progress + Completed adds up to All', () => {
    expect(toggles()).toEqual(['All (5)', 'In progress (2)', 'Completed (3)']);
  });

  it('each count matches the requests its filter shows', () => {
    expect(show('In progress')).toEqual(['REQ-4', 'REQ-5']); // drafts are not finished either
    expect(show('Completed')).toEqual(['REQ-1', 'REQ-2', 'REQ-3']);
    expect(show('All').length).toBe(5);
  });
});
