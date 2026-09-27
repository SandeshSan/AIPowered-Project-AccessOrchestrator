package com.accessorchestrator.seed;

import com.accessorchestrator.domain.AccessRequest;
import com.accessorchestrator.domain.AccessRequestItem;
import com.accessorchestrator.domain.AccessSource;
import com.accessorchestrator.domain.AccessStatus;
import com.accessorchestrator.domain.Entitlement;
import com.accessorchestrator.domain.Project;
import com.accessorchestrator.domain.ProjectAccess;
import com.accessorchestrator.domain.ProjectMember;
import com.accessorchestrator.domain.ProjectStatus;
import com.accessorchestrator.domain.RequestItemStatus;
import com.accessorchestrator.domain.RequestStatus;
import com.accessorchestrator.domain.RiskLevel;
import com.accessorchestrator.domain.User;
import com.accessorchestrator.domain.UserAccess;
import com.accessorchestrator.domain.UserStatus;
import com.accessorchestrator.repository.AccessRequestRepository;
import com.accessorchestrator.repository.EntitlementRepository;
import com.accessorchestrator.repository.ProjectAccessRepository;
import com.accessorchestrator.repository.ProjectMemberRepository;
import com.accessorchestrator.repository.ProjectRepository;
import com.accessorchestrator.repository.UserAccessRepository;
import com.accessorchestrator.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Seeds the demo scenarios. Idempotent: skipped when the core demo user already exists.
 *
 * <h2>Core scenario (used by the main demo and most tests; keep stable)</h2>
 * <ul>
 *   <li>NT10036 John, Developer on Novatech: missing NOVATECH_DB_READ, NOVATECH_JIRA, NOVATECH_VPN</li>
 *   <li>NT10042 Asha, QA on Novatech + Orion: Novatech DB_READ expired and VPN revoked (so missing); holds
 *       ORION_DEV; missing GCP_ORION_BQ_READ</li>
 *   <li>NOVATECH_WIKI is optional (required=false) and never counts as missing</li>
 * </ul>
 *
 * <h2>Extended scenarios</h2>
 * <ul>
 *   <li>NT10051 Priya, Senior Developer: Atlas Payments fully provisioned (incl. a past PROVISIONED request);
 *       Helios Mobile missing HELIOS_FIREBASE, HELIOS_APP_STORE, HELIOS_FIGMA</li>
 *   <li>NT10058 Marco, Mobile Developer: new on Helios with no access (0%); a past HELIOS_APP_STORE request
 *       was REJECTED, so it is still missing</li>
 *   <li>NT10063 Elena, Data Analyst: Orion fully provisioned; access from the CLOSED Zephyr project revoked</li>
 *   <li>NT10070 David, contractor, INACTIVE: requests for him must be refused</li>
 *   <li>Company-wide DEFAULT entitlements have no project prefix: CORP_VPN and JIRA_USER are required by both
 *       Atlas and Helios, and Priya holds them as DEFAULT access (kept if she leaves either project)</li>
 *   <li>Zephyr Data Migration is a CLOSED project</li>
 *   <li>All demo users sign in (HTTP Basic) with their user ID and {@code app.security.demo-password}</li>
 *   <li>NT10001 Grace, Access Administrator: the only admin (can browse the project catalog)</li>
 *   <li>Reporting line: Grace (admin) &lt;- Mei &lt;- John, Asha, Marco, Elena, Priya; Priya &lt;- David. A manager
 *       may remove people up to 3 levels below them from any project</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true", matchIfMissing = true)
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final UserRepository users;
    private final ProjectRepository projects;
    private final EntitlementRepository entitlements;
    private final ProjectAccessRepository projectAccess;
    private final UserAccessRepository userAccess;
    private final ProjectMemberRepository members;
    private final AccessRequestRepository requests;
    private final PasswordEncoder passwordEncoder;
    private final String demoPassword;

    public DemoDataSeeder(UserRepository users, ProjectRepository projects, EntitlementRepository entitlements,
                          ProjectAccessRepository projectAccess, UserAccessRepository userAccess,
                          ProjectMemberRepository members, AccessRequestRepository requests,
                          PasswordEncoder passwordEncoder,
                          @Value("${app.security.demo-password}") String demoPassword) {
        this.passwordEncoder = passwordEncoder;
        this.demoPassword = demoPassword;
        this.users = users;
        this.projects = projects;
        this.entitlements = entitlements;
        this.projectAccess = projectAccess;
        this.userAccess = userAccess;
        this.members = members;
        this.requests = requests;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.findByUserIdIgnoreCase("NT10036").isPresent()) {
            log.info("Demo data already present; skipping seed");
            giveDemoPasswords();
            return;
        }
        seedCoreScenario();
        seedExtendedScenarios();
        giveDemoPasswords();
        log.info("Seeded demo data: {} users, {} projects, {} entitlements",
                users.count(), projects.count(), entitlements.count());
    }

    // --- core: Novatech + Orion, John + Asha ---------------------------------------------------------

    private void seedCoreScenario() {
        User john = user("NT10036", "John", "john@example.com", "Developer", "Technology", UserStatus.ACTIVE);
        User asha = user("NT10042", "Asha", "asha@example.com", "QA Engineer", "Technology", UserStatus.ACTIVE);

        Project novatech = project("NOVATECH", "Novatech", "Novatech platform development", ProjectStatus.ACTIVE);
        Project orion = project("ORION", "Orion", "Orion analytics data platform", ProjectStatus.ACTIVE);

        Entitlement github = ent("GitHub", "NOVATECH_DEV", "GitHub Developer",
                "Write access to Novatech repositories", "DEV", RiskLevel.LOW);
        Entitlement gcp = ent("GCP", "GCP_NOVATECH_DEV", "GCP Developer",
                "Developer role on the Novatech DEV GCP project", "DEV", RiskLevel.MEDIUM);
        Entitlement jira = ent("Jira", "NOVATECH_JIRA", "Jira Novatech access",
                "Member of the Novatech Jira project", "ALL", RiskLevel.LOW);
        Entitlement dbRead = ent("Database", "NOVATECH_DB_READ", "Database Read access",
                "Read-only access to the Novatech application database", "DEV", RiskLevel.MEDIUM);
        Entitlement vpn = ent("VPN", "NOVATECH_VPN", "Novatech VPN access",
                "VPN profile for the Novatech network segment", "ALL", RiskLevel.HIGH);
        Entitlement wiki = ent("Confluence", "NOVATECH_WIKI", "Novatech Wiki Reader",
                "Read access to the Novatech Confluence space", "ALL", RiskLevel.LOW);
        Entitlement orionGithub = ent("GitHub", "ORION_DEV", "Orion GitHub Developer",
                "Write access to Orion repositories", "DEV", RiskLevel.LOW);
        Entitlement orionBq = ent("GCP", "GCP_ORION_BQ_READ", "Orion BigQuery Reader",
                "Read access to Orion BigQuery datasets", "DEV", RiskLevel.MEDIUM);

        required(novatech, github, "Required for source code development");
        required(novatech, gcp, "Required to deploy and debug services in DEV");
        required(novatech, jira, "Required to track sprint work and defects");
        required(novatech, dbRead, "Required to investigate data issues in DEV");
        required(novatech, vpn, "Required to reach Novatech internal network resources");
        optional(novatech, wiki, "Optional: project documentation");
        required(orion, orionGithub, "Source code collaboration");
        required(orion, orionBq, "Query analytics datasets");

        LocalDate granted = daysAgo(7);
        grant(john, github, AccessStatus.ACTIVE, granted, AccessSource.IGA);
        grant(john, gcp, AccessStatus.ACTIVE, granted, AccessSource.IGA);

        grant(asha, github, AccessStatus.ACTIVE, granted, AccessSource.IGA);
        grant(asha, gcp, AccessStatus.ACTIVE, granted, AccessSource.IGA);
        grant(asha, jira, AccessStatus.ACTIVE, granted, AccessSource.IGA);
        grant(asha, dbRead, AccessStatus.EXPIRED, granted.minusDays(90), AccessSource.IGA);
        grant(asha, vpn, AccessStatus.REVOKED, granted.minusDays(60), AccessSource.MANUAL);
        grant(asha, orionGithub, AccessStatus.ACTIVE, granted, AccessSource.IGA);

        member(john, novatech, "Developer", 1);
        member(asha, novatech, "QA Engineer", 30);
        member(asha, orion, "QA Engineer", 90);
    }

    // --- extended: Atlas, Helios, Zephyr; Priya, Marco, Elena, David -----------------------------------

    private void seedExtendedScenarios() {
        User priya = user("NT10051", "Priya", "priya@example.com", "Senior Developer", "Payments", UserStatus.ACTIVE);
        User marco = user("NT10058", "Marco", "marco@example.com", "Mobile Developer", "Digital Channels",
                UserStatus.ACTIVE);
        User elena = user("NT10063", "Elena", "elena@example.com", "Data Analyst", "Analytics", UserStatus.ACTIVE);
        User david = user("NT10070", "David", "david@example.com", "Contractor Developer", "Payments",
                UserStatus.INACTIVE);
        User grace = user("NT10001", "Grace", "grace@example.com", "Access Administrator",
                "Identity & Access Management", UserStatus.ACTIVE);
        grace.setAdmin(true);
        User mei = user("NT10020", "Mei", "mei@example.com", "Engineering Manager", "Technology", UserStatus.ACTIVE);

        Project atlas = project("ATLAS", "Atlas Payments",
                "Payments platform modernisation: card processing and settlement", ProjectStatus.ACTIVE);
        Project helios = project("HELIOS", "Helios Mobile",
                "Customer mobile banking app for iOS and Android", ProjectStatus.ACTIVE);
        Project zephyr = project("ZEPHYR", "Zephyr Data Migration",
                "Legacy warehouse migration to Snowflake (completed)", ProjectStatus.CLOSED);

        // Company-wide entitlements (no project prefix): usable in several access profiles, often held as DEFAULT
        Entitlement corpVpn = ent("VPN", "CORP_VPN", "Corporate VPN",
                "Corporate VPN profile for internal networks", "ALL", RiskLevel.MEDIUM);
        Entitlement jiraUser = ent("Jira", "JIRA_USER", "Jira Software User",
                "Company-wide Jira Software licence", "ALL", RiskLevel.LOW);

        Entitlement atlasGithub = ent("GitHub", "ATLAS_DEV", "Atlas GitHub Developer",
                "Write access to Atlas repositories", "DEV", RiskLevel.LOW);
        Entitlement atlasAws = ent("AWS", "AWS_ATLAS_DEV", "Atlas AWS Developer",
                "Developer role in the Atlas DEV AWS account", "DEV", RiskLevel.MEDIUM);
        Entitlement atlasDb = ent("Database", "ATLAS_DB_READ", "Atlas Ledger DB Read",
                "Read-only access to the Atlas ledger database", "DEV", RiskLevel.MEDIUM);
        Entitlement atlasVault = ent("HashiCorp Vault", "ATLAS_VAULT_READ", "Atlas Vault Secrets Reader",
                "Read service credentials from the Atlas Vault namespace", "DEV", RiskLevel.HIGH);
        Entitlement atlasDatadog = ent("Datadog", "ATLAS_DATADOG", "Atlas Datadog Viewer",
                "View Atlas dashboards and monitors", "PROD", RiskLevel.LOW);

        Entitlement heliosGithub = ent("GitHub", "HELIOS_DEV", "Helios GitHub Developer",
                "Write access to Helios app repositories", "DEV", RiskLevel.LOW);
        Entitlement heliosFirebase = ent("Firebase", "HELIOS_FIREBASE", "Helios Firebase Editor",
                "Editor on the Helios DEV Firebase project", "DEV", RiskLevel.MEDIUM);
        Entitlement heliosAppStore = ent("App Store Connect", "HELIOS_APP_STORE", "App Store Connect Developer",
                "Developer role for the Helios app in App Store Connect", "PROD", RiskLevel.HIGH);
        Entitlement heliosFigma = ent("Figma", "HELIOS_FIGMA", "Helios Figma Viewer",
                "View Helios design files", "ALL", RiskLevel.LOW);
        Entitlement heliosSentry = ent("Sentry", "HELIOS_SENTRY", "Helios Sentry Member",
                "View Helios crash reports", "PROD", RiskLevel.LOW);

        Entitlement zephyrGithub = ent("GitHub", "ZEPHYR_DEV", "Zephyr GitHub Developer",
                "Write access to Zephyr migration repositories", "DEV", RiskLevel.LOW);
        Entitlement zephyrSnowflake = ent("Snowflake", "ZEPHYR_SNOWFLAKE_READ", "Zephyr Snowflake Reader",
                "Read access to migrated Zephyr schemas", "PROD", RiskLevel.MEDIUM);

        required(atlas, atlasGithub, "Required for source code development");
        required(atlas, atlasAws, "Required to deploy payment services to the DEV account");
        required(atlas, jiraUser, "Required to track sprint work and defects");
        required(atlas, atlasDb, "Required to investigate settlement data in DEV");
        required(atlas, atlasVault, "Required to read service credentials in DEV");
        required(atlas, corpVpn, "Required to reach internal payment networks");
        optional(atlas, atlasDatadog, "Optional: production dashboards for on-call support");

        required(helios, heliosGithub, "Required for source code development");
        required(helios, heliosFirebase, "Required to manage push notifications and remote config in DEV");
        required(helios, jiraUser, "Required to track sprint work and defects");
        required(helios, heliosAppStore, "Required to manage TestFlight builds");
        required(helios, heliosFigma, "Required to view design specifications");
        required(helios, corpVpn, "Required to reach internal APIs from test devices");
        optional(helios, heliosSentry, "Optional: crash reports for release support");

        required(zephyr, zephyrGithub, "Required for migration scripts");
        required(zephyr, zephyrSnowflake, "Required to validate migrated data");

        // Priya: Atlas complete (Vault came through an access request 20 days ago), Helios partial
        grant(priya, atlasGithub, AccessStatus.ACTIVE, daysAgo(180), AccessSource.IGA);
        grant(priya, atlasAws, AccessStatus.ACTIVE, daysAgo(180), AccessSource.IGA);
        grant(priya, jiraUser, AccessStatus.ACTIVE, daysAgo(400), AccessSource.DEFAULT);
        grant(priya, atlasDb, AccessStatus.ACTIVE, daysAgo(150), AccessSource.IGA);
        grant(priya, atlasVault, AccessStatus.ACTIVE, daysAgo(20), AccessSource.IGA);
        grant(priya, corpVpn, AccessStatus.ACTIVE, daysAgo(400), AccessSource.DEFAULT);
        grant(priya, atlasDatadog, AccessStatus.ACTIVE, daysAgo(90), AccessSource.IGA);
        grant(priya, heliosGithub, AccessStatus.ACTIVE, daysAgo(3), AccessSource.IGA);
        member(priya, atlas, "Tech Lead", 180);
        member(priya, helios, "Developer", 3);
        history(priya, atlas, "REQ-09870", RequestStatus.PROVISIONED, 21, 20, atlasVault);

        // Marco: joined Helios with nothing; his App Store request was rejected
        member(marco, helios, "Mobile Developer", 5);
        history(marco, helios, "REQ-09912", RequestStatus.REJECTED, 3, 2, heliosAppStore);

        // Elena: Orion complete; Zephyr access revoked when the project closed
        grant(elena, orionGithub(), AccessStatus.ACTIVE, daysAgo(60), AccessSource.IGA);
        grant(elena, orionBq(), AccessStatus.ACTIVE, daysAgo(60), AccessSource.IGA);
        grant(elena, zephyrSnowflake, AccessStatus.REVOKED, daysAgo(400), AccessSource.IGA);
        grant(elena, zephyrGithub, AccessStatus.REVOKED, daysAgo(400), AccessSource.IGA);
        member(elena, projects.findByProjectCodeIgnoreCase("ORION").orElseThrow(), "Data Analyst", 60);

        // Mei: manages Novatech and Orion
        Project novatech = projects.findByProjectCodeIgnoreCase("NOVATECH").orElseThrow();
        Project orionProject = projects.findByProjectCodeIgnoreCase("ORION").orElseThrow();
        grant(mei, entitlements.findByEntitlementCode("NOVATECH_JIRA").orElseThrow(), AccessStatus.ACTIVE,
                daysAgo(300), AccessSource.IGA);
        grant(mei, entitlements.findByEntitlementCode("NOVATECH_WIKI").orElseThrow(), AccessStatus.ACTIVE,
                daysAgo(300), AccessSource.IGA);
        member(mei, novatech, "Engineering Manager", 300);
        member(mei, orionProject, "Engineering Manager", 300);

        // Reporting line: Grace <- Mei <- (John, Asha, Marco, Elena, Priya); Priya <- David
        mei.setManager(grace);
        for (String id : List.of("NT10036", "NT10042", "NT10058", "NT10063")) {
            users.findByUserIdIgnoreCase(id).orElseThrow().setManager(mei);
        }
        priya.setManager(mei);
        david.setManager(priya);

        // David: inactive contractor; his Atlas access was revoked
        grant(david, atlasGithub, AccessStatus.REVOKED, daysAgo(200), AccessSource.MANUAL);
        member(david, atlas, "Contractor Developer", 200);
    }

    // --- helpers -------------------------------------------------------------------------------------

    /** Every user without a password gets the demo password, including databases seeded before sign-in existed. */
    private void giveDemoPasswords() {
        if (demoPassword == null || demoPassword.isBlank()) {
            log.warn("app.security.demo-password is blank; demo users get no password and cannot sign in");
            return;
        }
        String hash = passwordEncoder.encode(demoPassword);
        users.findAll().stream().filter(u -> u.getPasswordHash() == null).forEach(u -> u.setPasswordHash(hash));
    }

    private User user(String id, String name, String email, String role, String department, UserStatus status) {
        return users.save(new User(id, name, email, role, department, status));
    }

    private Project project(String code, String name, String description, ProjectStatus status) {
        return projects.save(new Project(code, name, description, status));
    }

    private Entitlement ent(String app, String code, String name, String description, String env, RiskLevel risk) {
        return entitlements.save(new Entitlement(app, code, name, description, env, risk));
    }

    private void required(Project project, Entitlement entitlement, String reason) {
        projectAccess.save(new ProjectAccess(project, entitlement, true, reason));
    }

    private void optional(Project project, Entitlement entitlement, String reason) {
        projectAccess.save(new ProjectAccess(project, entitlement, false, reason));
    }

    private void grant(User user, Entitlement entitlement, AccessStatus status, LocalDate on, AccessSource source) {
        userAccess.save(new UserAccess(user, entitlement, status, on, source));
    }

    private ProjectMember member(User user, Project project, String role, int joinedDaysAgo) {
        return members.save(new ProjectMember(user, project, role, daysAgo(joinedDaysAgo)));
    }

    /**
     * A finished request (PROVISIONED or REJECTED) with a fixed IGA id below the Mock IGA's range, so the
     * status poller never asks the IGA about it.
     */
    private void history(User user, Project project, String igaId, RequestStatus status, int createdDaysAgo,
                         int closedDaysAgo, Entitlement... items) {
        AccessRequest request = new AccessRequest("REQ-HIST-" + igaId.substring(4), user, project, status,
                user.getUserId());
        request.setIgaRequestId(igaId);
        request.backdate(Instant.now().minus(createdDaysAgo, ChronoUnit.DAYS),
                Instant.now().minus(closedDaysAgo, ChronoUnit.DAYS));
        RequestItemStatus itemStatus = status == RequestStatus.PROVISIONED
                ? RequestItemStatus.PROVISIONED : RequestItemStatus.REJECTED;
        for (Entitlement e : items) {
            String reason = projectAccess.findByProject_Id(project.getId()).stream()
                    .filter(pa -> pa.getEntitlement().getId().equals(e.getId()))
                    .map(ProjectAccess::getReason).findFirst().orElse(null);
            request.addItem(new AccessRequestItem(e, itemStatus, reason));
        }
        requests.save(request);
    }

    private Entitlement orionGithub() {
        return entitlements.findByEntitlementCode("ORION_DEV").orElseThrow();
    }

    private Entitlement orionBq() {
        return entitlements.findByEntitlementCode("GCP_ORION_BQ_READ").orElseThrow();
    }

    private static LocalDate daysAgo(int days) {
        return LocalDate.now().minusDays(days);
    }
}
