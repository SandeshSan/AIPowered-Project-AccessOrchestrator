package com.accessorchestrator.web;

import com.accessorchestrator.dto.ProjectAccessDto;
import com.accessorchestrator.dto.ProjectDto;
import com.accessorchestrator.service.AccessAnalysisService;
import com.accessorchestrator.service.ProjectService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The project access catalog: administrators only. Employees see their own projects via the dashboard. */
@AdminOnly
@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final AccessAnalysisService accessAnalysisService;

    public ProjectController(ProjectService projectService, AccessAnalysisService accessAnalysisService) {
        this.projectService = projectService;
        this.accessAnalysisService = accessAnalysisService;
    }

    @GetMapping
    public List<ProjectDto> listProjects() {
        return projectService.listProjects();
    }

    @GetMapping("/{projectId}")
    public ProjectDto getProject(@PathVariable Long projectId) {
        return projectService.getProject(projectId);
    }

    @GetMapping("/{projectId}/access")
    public List<ProjectAccessDto> getProjectAccess(@PathVariable Long projectId) {
        return projectService.getProjectAccess(projectId);
    }

    /** Mandatory entries only; {@code /access} returns the full profile including optional ones. */
    @GetMapping("/{projectId}/required-access")
    public List<ProjectAccessDto> getRequiredAccess(@PathVariable Long projectId) {
        return accessAnalysisService.getRequiredAccess(projectId);
    }
}
