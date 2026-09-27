package com.accessorchestrator.service;

import com.accessorchestrator.domain.Project;
import com.accessorchestrator.dto.DtoMapper;
import com.accessorchestrator.dto.ProjectAccessDto;
import com.accessorchestrator.dto.ProjectDto;
import com.accessorchestrator.exception.BusinessRuleException;
import com.accessorchestrator.exception.ResourceNotFoundException;
import com.accessorchestrator.repository.ProjectAccessRepository;
import com.accessorchestrator.repository.ProjectRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectAccessRepository projectAccessRepository;

    public ProjectService(ProjectRepository projectRepository, ProjectAccessRepository projectAccessRepository) {
        this.projectRepository = projectRepository;
        this.projectAccessRepository = projectAccessRepository;
    }

    public List<ProjectDto> listProjects() {
        return projectRepository.findAll(Sort.by("projectName")).stream().map(DtoMapper::toDto).toList();
    }

    public ProjectDto getProject(Long projectId) {
        return DtoMapper.toDto(findProject(projectId));
    }

    /** Full standard access profile for the project (required and optional entries). */
    public List<ProjectAccessDto> getProjectAccess(Long projectId) {
        findProject(projectId);
        return projectAccessRepository.findByProject_Id(projectId).stream()
                .map(DtoMapper::toDto)
                .sorted(AccessOrdering.PROJECT_ACCESS)
                .toList();
    }

    /**
     * Resolves free text such as "NovaTech", "novatech" or "NOVATECH" to one project: exact match on name or
     * code (ignoring case, spaces and punctuation) first, then a unique partial match.
     */
    public ProjectDto findByNameOrCode(String query) {
        String wanted = normalize(query);
        if (wanted.isEmpty()) {
            throw new BusinessRuleException("A project name is required");
        }
        List<Project> all = projectRepository.findAll(Sort.by("projectName"));
        List<Project> exact = all.stream()
                .filter(p -> normalize(p.getProjectName()).equals(wanted) || normalize(p.getProjectCode()).equals(wanted))
                .toList();
        List<Project> matches = !exact.isEmpty() ? exact : all.stream()
                .filter(p -> normalize(p.getProjectName()).contains(wanted) || wanted.contains(normalize(p.getProjectName())))
                .toList();
        if (matches.size() == 1) {
            return DtoMapper.toDto(matches.getFirst());
        }
        String known = all.stream().map(Project::getProjectName).toList().toString();
        if (matches.isEmpty()) {
            throw new ResourceNotFoundException("Project", query + " (known projects: " + known + ")");
        }
        throw new BusinessRuleException("'" + query + "' matches several projects: "
                + matches.stream().map(Project::getProjectName).toList());
    }

    private static String normalize(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    public Project findProject(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
    }
}
