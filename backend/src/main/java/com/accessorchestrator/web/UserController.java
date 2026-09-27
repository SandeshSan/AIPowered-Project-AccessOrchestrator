package com.accessorchestrator.web;

import com.accessorchestrator.dto.DashboardDto;
import com.accessorchestrator.dto.ProjectMembershipDto;
import com.accessorchestrator.dto.UserAccessDto;
import com.accessorchestrator.dto.UserDto;
import com.accessorchestrator.service.AccessAnalysisService;
import com.accessorchestrator.dto.RemovalDtos.CapabilitiesDto;
import com.accessorchestrator.dto.RemovalDtos.TeamMemberDto;
import com.accessorchestrator.service.DashboardService;
import com.accessorchestrator.service.AccessAuthority;
import com.accessorchestrator.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class UserController {

    private final UserService userService;
    private final AccessAnalysisService accessAnalysisService;
    private final DashboardService dashboardService;
    private final CurrentUser currentUser;
    private final AccessAuthority accessAuthority;

    public UserController(UserService userService, AccessAnalysisService accessAnalysisService,
                          DashboardService dashboardService, CurrentUser currentUser,
                          AccessAuthority accessAuthority) {
        this.userService = userService;
        this.accessAnalysisService = accessAnalysisService;
        this.dashboardService = dashboardService;
        this.currentUser = currentUser;
        this.accessAuthority = accessAuthority;
    }

    /** The signed-in user (POC: X-User-Id header or demo user). */
    @GetMapping("/me")
    public UserDto me() {
        return userService.getUser(currentUser.id());
    }

    /** What the signed-in user may do beyond their own access (admin, people who report to them). */
    @GetMapping("/me/capabilities")
    public CapabilitiesDto capabilities() {
        var me = userService.findUser(currentUser.id());
        return new CapabilitiesDto(me.isAdmin(), accessAuthority.reportsOf(me));
    }

    /** The signed-in user's reportees (up to 3 levels down) with their projects and active access. */
    @GetMapping("/team")
    public List<TeamMemberDto> team() {
        var me = userService.findUser(currentUser.id());
        return accessAuthority.reportsOf(me).stream()
                .map(r -> new TeamMemberDto(r.user(), r.level(), r.projects(),
                        accessAnalysisService.getExistingAccess(r.user().userId())))
                .toList();
    }

    /** Demo directory, used by the UI's user switcher. */
    @GetMapping("/users")
    public List<UserDto> listUsers() {
        return userService.listUsers();
    }

    @GetMapping("/users/{userId}")
    public UserDto getUser(@PathVariable String userId) {
        return userService.getUser(userId);
    }

    @GetMapping("/users/{userId}/access")
    public List<UserAccessDto> getUserAccess(@PathVariable String userId) {
        return accessAnalysisService.getExistingAccess(userId);
    }

    @GetMapping("/users/{userId}/projects")
    public List<ProjectMembershipDto> getUserProjects(@PathVariable String userId) {
        return dashboardService.getMyProjects(userId);
    }

    @GetMapping("/users/{userId}/dashboard")
    public DashboardDto getDashboard(@PathVariable String userId) {
        return dashboardService.getDashboard(userId);
    }
}
