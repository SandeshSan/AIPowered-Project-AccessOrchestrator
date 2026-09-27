package com.accessorchestrator.security;

import com.accessorchestrator.domain.User;
import com.accessorchestrator.domain.UserStatus;
import com.accessorchestrator.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Signs users in by employee ID (case-insensitive). The principal name is the canonical user ID, which is what
 * {@link com.accessorchestrator.web.CurrentUser} hands to the services. Inactive users and users without a
 * password cannot sign in.
 */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository users;

    public AppUserDetailsService(UserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        User user = users.findByUserIdIgnoreCase(username == null ? "" : username.trim())
                .filter(u -> u.getPasswordHash() != null)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
        List<GrantedAuthority> authorities = new ArrayList<>(List.of(new SimpleGrantedAuthority("ROLE_USER")));
        if (user.isAdmin()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        }
        return org.springframework.security.core.userdetails.User.withUsername(user.getUserId())
                .password(user.getPasswordHash())
                .authorities(authorities)
                .disabled(user.getStatus() != UserStatus.ACTIVE)
                .build();
    }
}
