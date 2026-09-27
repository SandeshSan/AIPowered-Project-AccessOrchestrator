import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';

import { AgentAccessItem, ChatResponse } from '../../core/models';
import { AssistantComponent } from './assistant.component';
import { AssistantStore } from './assistant.store';

describe('AssistantComponent selection', () => {
  let fixture: ComponentFixture<AssistantComponent>;
  let backend: HttpTestingController;

  const item = (id: number, code: string, name: string): AgentAccessItem =>
    ({ entitlementId: id, application: 'App', entitlementCode: code, entitlementName: name, riskLevel: 'LOW', reason: 'needed' });

  const offer: ChatResponse = {
    conversationId: 'conv-1', userId: 'NT10036', reply: 'Would you like me to submit the missing access requests?',
    awaitingConfirmation: true, accessRequest: null, toolCalls: [],
    analysis: {
      userId: 'NT10036', userName: 'John', role: 'Developer', projectId: 1, projectName: 'Novatech',
      alreadyHave: [], alreadyRequested: [], verification: '5 required - 2 already granted = 3 missing', nextStep: '',
      missing: [item(4, 'NOVATECH_DB_READ', 'Database Read access'), item(3, 'NOVATECH_JIRA', 'Jira Novatech access'),
        item(5, 'NOVATECH_VPN', 'Novatech VPN access')],
      requestableEntitlementIds: [4, 3, 5],
    },
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [AssistantComponent],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(), provideNoopAnimations()],
    });
    fixture = TestBed.createComponent(AssistantComponent);
    backend = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    backend.expectOne('/api/me/capabilities').flush({ admin: false, reports: [] });

    (fixture.nativeElement.querySelector('.suggestion') as HTMLButtonElement).click();
    backend.expectOne('/api/agent/chat').flush(offer);
    fixture.detectChanges();
  });

  afterEach(() => backend.verify());

  const checkboxes = () => Array.from(fixture.nativeElement.querySelectorAll('.ent.selectable input[type=checkbox]') as NodeListOf<HTMLInputElement>);
  const submitButton = () => fixture.nativeElement.querySelector('.confirm button') as HTMLButtonElement;

  it('pre-selects every requestable entitlement', () => {
    expect(checkboxes().map(c => c.checked)).toEqual([true, true, true]);
    expect(submitButton().textContent).toContain('Yes, submit all 3');
  });

  it('submits only the ticked entitlements', () => {
    checkboxes()[0].click(); // untick Database Read access
    fixture.detectChanges();
    expect(submitButton().textContent).toContain('Submit 2 selected');

    submitButton().click();
    const req = backend.expectOne('/api/agent/chat');
    expect(req.request.body.selectedEntitlementIds).toEqual([3, 5]);
    expect(req.request.body.message).toBe('Yes, submit only the selected access: NOVATECH_JIRA, NOVATECH_VPN');
    expect(req.request.body.conversationId).toBe('conv-1');
    req.flush({ ...offer, awaitingConfirmation: false, analysis: null });
  });

  it('confirms a removal with the typed reason as a separate field', async () => {
    const preview: ChatResponse = {
      ...offer, analysis: null, pendingAction: 'PROJECT_REMOVAL',
      removalPreview: {
        userId: 'NT10042', employeeName: 'Asha', projectId: 1, projectName: 'Novatech', projectRole: 'QA Engineer',
        selfRemoval: false, toRevoke: [item(1, 'NOVATECH_DEV', 'GitHub Developer')],
        keptDefault: [item(3, 'NOVATECH_JIRA', 'Jira Novatech access')], requestsToCancel: [], blockers: [],
        canProceed: true, nextStep: '',
      },
    };
    TestBed.inject(AssistantStore).send('Remove Asha from Novatech');
    backend.expectOne('/api/agent/chat').flush(preview);
    fixture.detectChanges();
    await fixture.whenStable(); // ngModel inside a <form> registers its control asynchronously

    const card = fixture.nativeElement.querySelector('.removal-card') as HTMLElement;
    expect(card.textContent).toContain('Remove Asha from Novatech');
    expect(card.textContent).toContain('NOVATECH_DEV');
    const confirm = card.querySelector('.removal-confirm button[type=submit]') as HTMLButtonElement;
    expect(confirm.disabled).toBeTrue(); // reason required

    const input = card.querySelector('.removal-confirm input') as HTMLInputElement;
    input.value = 'No longer on the Novatech team';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(confirm.disabled).toBeFalse();
    confirm.click();

    const req = backend.expectOne('/api/agent/chat');
    expect(req.request.body.removalReason).toBe('No longer on the Novatech team');
    expect(req.request.body.message).toBe('Yes, remove Asha from Novatech. Reason: No longer on the Novatech team');
    req.flush({ ...preview, pendingAction: null, awaitingConfirmation: false, removalPreview: null });
  });

  it('confirms an addition with the button flag and requests no access', () => {
    const preview: ChatResponse = {
      ...offer, analysis: null, pendingAction: 'PROJECT_ADDITION',
      additionPreview: {
        userId: 'NT10036', employeeName: 'John', projectId: 2, projectName: 'Atlas Payments', projectRole: 'Developer',
        authority: 'direct report', alreadyHave: [], missingAccess: [item(9, 'ATLAS_DEV', 'Atlas GitHub Developer')],
        alreadyRequested: [], blockers: [], canProceed: true, nextStep: '',
      },
    };
    TestBed.inject(AssistantStore).send('Add John to Atlas');
    backend.expectOne('/api/agent/chat').flush(preview);
    fixture.detectChanges();

    const card = fixture.nativeElement.querySelector('.addition-card') as HTMLElement;
    expect(card.textContent).toContain('Add John to Atlas Payments');
    expect(card.textContent).toContain('No access is requested');
    (card.querySelector('.confirm button') as HTMLButtonElement).click();

    const req = backend.expectOne('/api/agent/chat');
    expect(req.request.body.confirmAddition).toBeTrue();
    expect(req.request.body.message).toBe('Yes, add John to Atlas Payments');
    req.flush({ ...preview, pendingAction: null, awaitingConfirmation: false, additionPreview: null });
  });

  it('cannot submit an empty selection', () => {
    checkboxes().forEach(c => c.click());
    fixture.detectChanges();
    expect(submitButton().disabled).toBeTrue();
    expect(fixture.nativeElement.querySelector('.confirm').textContent).toContain('Select at least one');
  });
});
