import { TestBed } from '@angular/core/testing';

import { LifecycleStage } from '../core/models';
import { LifecycleStepperComponent } from './lifecycle-stepper.component';

describe('LifecycleStepperComponent', () => {
  const stages: LifecycleStage[] = [
    { stage: 'REQUEST_CREATED', label: 'Request Created', state: 'COMPLETED' },
    { stage: 'PENDING_APPROVAL', label: 'Pending Approval', state: 'COMPLETED' },
    { stage: 'APPROVED', label: 'Approved', state: 'CURRENT' },
    { stage: 'PROVISIONING', label: 'Provisioning', state: 'UPCOMING' },
    { stage: 'PROVISIONED', label: 'Provisioned', state: 'UPCOMING' },
  ];

  it('renders each stage with its state and marks the current step', () => {
    const fixture = TestBed.createComponent(LifecycleStepperComponent);
    fixture.componentRef.setInput('stages', stages);
    fixture.detectChanges();

    const steps = Array.from(fixture.nativeElement.querySelectorAll('li.step') as NodeListOf<HTMLElement>);
    expect(steps.map(s => s.querySelector('.label')?.textContent?.trim())).toEqual(
      ['Request Created', 'Pending Approval', 'Approved', 'Provisioning', 'Provisioned']);
    expect(steps.map(s => s.dataset['state'])).toEqual(['COMPLETED', 'COMPLETED', 'CURRENT', 'UPCOMING', 'UPCOMING']);
    expect(steps[2].getAttribute('aria-current')).toBe('step');
  });
});
