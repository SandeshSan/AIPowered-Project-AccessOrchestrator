/** TypeScript mirrors of the backend DTOs. */

export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH';
export type RequestStatus =
  | 'DRAFT' | 'SUBMITTED' | 'PENDING_APPROVAL' | 'APPROVED' | 'REJECTED'
  | 'PROVISIONING' | 'PROVISIONED' | 'FAILED' | 'CANCELLED';
export type RequestItemStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'PROVISIONED' | 'REVOKED' | 'CANCELLED' | 'FAILED';
export type StageState = 'COMPLETED' | 'CURRENT' | 'UPCOMING' | 'REJECTED' | 'CANCELLED' | 'FAILED';
/** GRANT provisions access; REVOKE removes it (e.g. when someone leaves a project). */
export type RequestType = 'GRANT' | 'REVOKE';

export interface User {
  userId: string;
  name: string;
  email: string;
  role: string;
  department: string;
  status: 'ACTIVE' | 'INACTIVE';
  /** Application administrator (can browse the project access catalog). */
  admin: boolean;
  /** Line manager in the reporting line (null at the top). */
  managerId?: string | null;
  managerName?: string | null;
}

export interface Project {
  id: number;
  projectCode: string;
  projectName: string;
  description: string;
  status: 'ACTIVE' | 'CLOSED';
}

export interface Entitlement {
  id: number;
  application: string;
  entitlementCode: string;
  entitlementName: string;
  description: string;
  environment: string;
  riskLevel: RiskLevel;
}

export interface ProjectAccess {
  entitlement: Entitlement;
  required: boolean;
  reason: string;
}

export interface UserAccess {
  entitlement: Entitlement;
  status: 'ACTIVE' | 'REVOKED' | 'EXPIRED';
  grantedDate: string | null;
  source: string;
}

export interface AccessSummary {
  requiredCount: number;
  grantedCount: number;
  missingCount: number;
  additionalCount: number;
  coveragePercent: number;
  fullyProvisioned: boolean;
}

export interface ProjectMembership {
  project: Project;
  projectRole: string;
  joinedDate: string;
  access: AccessSummary;
}

export interface LifecycleStage {
  stage: string;
  label: string;
  state: StageState;
}

export interface AccessRequestItem {
  entitlement: Entitlement;
  status: RequestItemStatus;
  reason: string;
}

export interface AccessRequest {
  requestId: string;
  type: RequestType;
  userId: string;
  projectId: number;
  projectName: string;
  status: RequestStatus;
  igaRequestId: string | null;
  createdAt: string;
  updatedAt: string;
  createdBy: string;
  justification: string | null;
  items: AccessRequestItem[];
  lifecycle: LifecycleStage[];
}

export interface Dashboard {
  user: User;
  activeAccessCount: number;
  pendingRequestCount: number;
  projectCount: number;
  missingAccessCount: number;
  missingAlreadyRequestedCount: number;
  projects: ProjectMembership[];
  activeAccess: UserAccess[];
  pendingRequests: AccessRequest[];
  recentlyProvisioned: UserAccess[];
}

export interface ComparisonItem {
  entitlement: Entitlement;
  reason: string;
  status: 'GRANTED' | 'MISSING';
  grantedDate: string | null;
  source: string | null;
}

export interface AccessComparison {
  userId: string;
  userName: string;
  projectId: number;
  project: string;
  summary: AccessSummary;
  items: ComparisonItem[];
  missingAccess: ProjectAccess[];
  additionalAccess: UserAccess[];
  verification: { rule: string; expression: string; verified: boolean };
}

// --- Access Agent -------------------------------------------------------------------------------------

export interface AgentAccessItem {
  entitlementId: number;
  application: string;
  entitlementCode: string;
  entitlementName: string;
  riskLevel: RiskLevel;
  reason: string | null;
}

export interface MissingAccessView {
  userId: string;
  userName: string;
  role: string;
  projectId: number;
  projectName: string;
  alreadyHave: AgentAccessItem[];
  missing: AgentAccessItem[];
  alreadyRequested: { entitlementCode: string; requestId: string; status: RequestStatus }[];
  requestableEntitlementIds: number[];
  verification: string;
  nextStep: string;
}

export interface AgentRequestView {
  requestId: string;
  orchestratorReference: string;
  type: RequestType;
  projectName: string;
  status: RequestStatus;
  statusLabel: string;
  requested: AgentAccessItem[];
}

/** What removing someone from a project would do (computed by the backend). */
export interface RemovalPreviewView {
  userId: string;
  employeeName: string;
  projectId: number;
  projectName: string;
  projectRole: string;
  selfRemoval: boolean;
  toRevoke: AgentAccessItem[];
  keptDefault: AgentAccessItem[];
  requestsToCancel: string[];
  blockers: string[];
  canProceed: boolean;
  nextStep: string;
}

export interface RemovalResultView {
  userId: string;
  employeeName: string;
  projectName: string;
  reason: string;
  revocationRequestId: string | null;
  revocationStatusLabel: string | null;
  revoking: AgentAccessItem[];
  keptDefault: AgentAccessItem[];
  cancelledRequests: string[];
}

/** Adding someone to a project as a member (no access is requested). */
export interface AdditionPreviewView {
  userId: string;
  employeeName: string;
  projectId: number;
  projectName: string;
  projectRole: string;
  authority: string;
  alreadyHave: AgentAccessItem[];
  /** Information only: nothing is requested; the person asks for it themselves. */
  missingAccess: AgentAccessItem[];
  alreadyRequested: { entitlementCode: string; requestId: string; status: RequestStatus }[];
  blockers: string[];
  canProceed: boolean;
  nextStep: string;
}

export interface AdditionResultView {
  userId: string;
  employeeName: string;
  projectName: string;
  projectRole: string;
  missingAccess: AgentAccessItem[];
}

export type PendingAction = 'ACCESS_REQUEST' | 'PROJECT_REMOVAL' | 'PROJECT_ADDITION';

export interface ChatResponse {
  conversationId: string;
  userId: string;
  reply: string;
  awaitingConfirmation: boolean;
  analysis: MissingAccessView | null;
  accessRequest: AgentRequestView | null;
  toolCalls: { tool: string; success: boolean; error: string | null }[];
  /** What a "yes" would do next (the first of pendingActions). */
  pendingAction?: PendingAction | null;
  /** Everything awaiting confirmation; a "move" is a removal and an addition. */
  pendingActions?: PendingAction[];
  removalPreview?: RemovalPreviewView | null;
  removal?: RemovalResultView | null;
  additionPreview?: AdditionPreviewView | null;
  addition?: AdditionResultView | null;
}

/** Someone who reports to the signed-in user; level 1 = direct report. */
export interface Report {
  user: User;
  level: number;
  projects: Project[];
}

/** A reportee on the manager's "My Team" page; level 1 = direct report. */
export interface TeamMember {
  user: User;
  level: number;
  projects: Project[];
  activeAccess: UserAccess[];
}

/** What the signed-in user may do beyond their own access. */
export interface Capabilities {
  admin: boolean;
  reports: Report[];
}

export interface ProblemDetail {
  title?: string;
  detail?: string;
  status?: number;
}
