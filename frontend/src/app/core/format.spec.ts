import { HttpErrorResponse } from '@angular/common/http';

import { errorMessage, isOpen, statusLabel, statusTone } from './format';

describe('format helpers', () => {
  it('maps statuses to tones', () => {
    expect(statusTone('PROVISIONED')).toBe('ok');
    expect(statusTone('PENDING_APPROVAL')).toBe('warn');
    expect(statusTone('PROVISIONING')).toBe('info');
    expect(statusTone('REJECTED')).toBe('bad');
    expect(statusTone('DRAFT')).toBe('neutral');
  });

  it('labels statuses for people', () => {
    expect(statusLabel('PENDING_APPROVAL')).toBe('Pending Approval');
    expect(statusLabel('MISSING')).toBe('Missing');
    expect(statusLabel('SOME_NEW_STATE')).toBe('Some new state');
  });

  it('knows which requests are still in progress at the IGA', () => {
    expect(isOpen('PENDING_APPROVAL')).toBeTrue();
    expect(isOpen('PROVISIONING')).toBeTrue();
    expect(isOpen('DRAFT')).toBeFalse();
    expect(isOpen('PROVISIONED')).toBeFalse();
  });

  it('prefers the ProblemDetail detail in error messages', () => {
    const err = new HttpErrorResponse({ status: 422, error: { title: 'Business rule violation', detail: 'Already requested' } });
    expect(errorMessage(err)).toBe('Already requested');
    expect(errorMessage(new HttpErrorResponse({ status: 0 }))).toContain('Cannot reach the server');
  });
});
