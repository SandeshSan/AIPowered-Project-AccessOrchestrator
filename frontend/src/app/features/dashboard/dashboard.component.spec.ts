import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';

import { Dashboard } from '../../core/models';
import { DashboardComponent } from './dashboard.component';

describe('DashboardComponent', () => {
  const dashboard: Dashboard = {
    user: { userId: 'NT10036', name: 'John', email: 'john@example.com', role: 'Developer', department: 'Technology', status: 'ACTIVE', admin: false },
    activeAccessCount: 12,
    pendingRequestCount: 2,
    projectCount: 3,
    missingAccessCount: 3,
    missingAlreadyRequestedCount: 0,
    projects: [{
      project: { id: 1, projectCode: 'NOVATECH', projectName: 'Novatech', description: '', status: 'ACTIVE' },
      projectRole: 'Developer', joinedDate: '2026-09-25',
      access: { requiredCount: 5, grantedCount: 2, missingCount: 3, additionalCount: 0, coveragePercent: 40, fullyProvisioned: false },
    }],
    activeAccess: [],
    pendingRequests: [],
    recentlyProvisioned: [],
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(), provideNoopAnimations()],
    });
  });

  it('shows the four summary cards and a call to action for missing access', () => {
    const fixture = TestBed.createComponent(DashboardComponent);
    fixture.detectChanges();
    TestBed.inject(HttpTestingController).expectOne(r => r.url.endsWith('/dashboard')).flush(dashboard);
    fixture.detectChanges();

    const cards = Array.from(fixture.nativeElement.querySelectorAll('app-stat-card') as NodeListOf<HTMLElement>)
      .map(c => [c.querySelector('.label')?.textContent?.trim(), c.querySelector('.value')?.textContent?.trim()]);
    expect(cards).toEqual([
      ['Active Access', '12'], ['Pending Requests', '2'], ['Projects', '3'], ['Missing Access', '3'],
    ]);
    expect(fixture.nativeElement.querySelector('.callout').textContent)
      .toContain('missing 3 of 5 required entitlements for Novatech');
  });
});
