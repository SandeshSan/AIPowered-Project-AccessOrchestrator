import { HttpErrorResponse } from '@angular/common/http';

import { ProblemDetail, RequestStatus } from './models';

export const STATUS_LABELS: Record<RequestStatus, string> = {
  DRAFT: 'Draft',
  SUBMITTED: 'Submitted',
  PENDING_APPROVAL: 'Pending Approval',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
  PROVISIONING: 'Provisioning',
  PROVISIONED: 'Provisioned',
  FAILED: 'Failed',
  CANCELLED: 'Cancelled',
};

export type Tone = 'ok' | 'warn' | 'bad' | 'info' | 'neutral';

export function statusTone(status: string): Tone {
  switch (status) {
    case 'PROVISIONED':
    case 'GRANTED':
    case 'ACTIVE':
      return 'ok';
    case 'PENDING_APPROVAL':
    case 'SUBMITTED':
    case 'PENDING':
    case 'MISSING':
      return 'warn';
    case 'APPROVED':
    case 'PROVISIONING':
      return 'info';
    case 'REJECTED':
    case 'FAILED':
    case 'REVOKED':
    case 'EXPIRED':
      return 'bad';
    default:
      return 'neutral';
  }
}

/** Revocations use "Revoking" / "Revoked" for the provisioning stages. */
export function requestStatusLabel(status: RequestStatus, type: 'GRANT' | 'REVOKE'): string {
  if (type === 'REVOKE' && status === 'PROVISIONING') {
    return 'Revoking';
  }
  if (type === 'REVOKE' && status === 'PROVISIONED') {
    return 'Revoked';
  }
  return STATUS_LABELS[status];
}

export function statusLabel(status: string): string {
  return (STATUS_LABELS as Record<string, string>)[status]
    ?? status.charAt(0) + status.slice(1).toLowerCase().replace(/_/g, ' ');
}

export function isOpen(status: RequestStatus): boolean {
  return status === 'SUBMITTED' || status === 'PENDING_APPROVAL' || status === 'APPROVED'
    || status === 'PROVISIONING';
}

/** Human-readable message from an API error (ProblemDetail body when present). */
export function errorMessage(err: unknown): string {
  if (err instanceof HttpErrorResponse) {
    const body = err.error as ProblemDetail | null;
    if (body && typeof body === 'object' && (body.detail || body.title)) {
      return body.detail ?? body.title ?? err.message;
    }
    if (err.status === 0) {
      return 'Cannot reach the server. Is the backend running on port 8080?';
    }
    return `${err.status} ${err.statusText}`;
  }
  return err instanceof Error ? err.message : String(err);
}
