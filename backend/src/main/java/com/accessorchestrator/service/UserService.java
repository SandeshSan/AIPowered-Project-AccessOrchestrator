package com.accessorchestrator.service;

import com.accessorchestrator.domain.User;
import com.accessorchestrator.dto.DtoMapper;
import com.accessorchestrator.dto.UserDto;
import com.accessorchestrator.exception.ResourceNotFoundException;
import com.accessorchestrator.repository.UserRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<UserDto> listUsers() {
        return userRepository.findAll(Sort.by("name")).stream().map(DtoMapper::toDto).toList();
    }

    /** Directory lookup by exact user id or part of the name (case-insensitive), at most 10 matches. */
    public List<UserDto> search(String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            return List.of();
        }
        return userRepository.findAll(Sort.by("name")).stream()
                .filter(u -> u.getUserId().equalsIgnoreCase(q) || u.getName().toLowerCase(Locale.ROOT).contains(q))
                .limit(10)
                .map(DtoMapper::toDto)
                .toList();
    }

    public UserDto getUser(String userId) {
        return DtoMapper.toDto(findUser(userId));
    }

    /** Entity lookup for other services. */
    public User findUser(String userId) {
        return userRepository.findByUserIdIgnoreCase(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }
}
