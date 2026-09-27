package com.accessorchestrator.service;

import com.accessorchestrator.domain.Entitlement;
import com.accessorchestrator.dto.DtoMapper;
import com.accessorchestrator.dto.EntitlementDto;
import com.accessorchestrator.exception.ResourceNotFoundException;
import com.accessorchestrator.repository.EntitlementRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class EntitlementService {

    private final EntitlementRepository entitlementRepository;

    public EntitlementService(EntitlementRepository entitlementRepository) {
        this.entitlementRepository = entitlementRepository;
    }

    public List<EntitlementDto> listEntitlements() {
        return entitlementRepository.findAll(Sort.by("application", "entitlementCode")).stream()
                .map(DtoMapper::toDto)
                .toList();
    }

    public EntitlementDto getByCode(String entitlementCode) {
        return DtoMapper.toDto(findByCode(entitlementCode));
    }

    public Entitlement findByCode(String entitlementCode) {
        return entitlementRepository.findByEntitlementCode(entitlementCode)
                .orElseThrow(() -> new ResourceNotFoundException("Entitlement", entitlementCode));
    }
}
