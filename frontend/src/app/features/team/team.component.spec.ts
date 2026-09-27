import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';

import { TeamMember } from '../../core/models';
import { TeamComponent } from './team.component';

describe('TeamComponent', () => {
  const asha = {
    user: { userId: 'NT10042', name: 'Asha Rao', role: 'QA Engineer', email: '', department: 'Engineering',
      status: 'ACTIVE', admin: false, managerId: 'NT10020', managerName: 'Mei Lin' },
    level: 1,
    projects: [{ id: 1, projectCode: 'NOVATECH', projectName: 'Novatech' }],
    activeAccess: [{ entitlement: { id: 7, entitlementCode: 'NOVATECH_JIRA', entitlementName: 'Novatech Jira',
      application: 'Jira', riskLevel: 'LOW' }, source: 'IGA', status: 'ACTIVE', grantedDate: '2026-09-01' }],
  } as unknown as TeamMember;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TeamComponent],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(), provideNoopAnimations()],
    }).compileComponents();
  });

  it('lists reportees with their projects and access', () => {
    const fixture = TestBed.createComponent(TeamComponent);
    fixture.detectChanges();
    TestBed.inject(HttpTestingController).expectOne('/api/team').flush([asha]);
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Asha Rao');
    expect(text).toContain('Direct report');
    expect(text).toContain('Novatech');
    expect(text).toContain('NOVATECH_JIRA');
  });

  it('says so when nobody reports to you', () => {
    const fixture = TestBed.createComponent(TeamComponent);
    fixture.detectChanges();
    TestBed.inject(HttpTestingController).expectOne('/api/team').flush([]);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Nobody reports to you.');
  });
});
