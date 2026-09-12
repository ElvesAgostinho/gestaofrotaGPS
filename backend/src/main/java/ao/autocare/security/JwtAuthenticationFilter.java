package ao.autocare.security;

import ao.autocare.domain.Membership;
import ao.autocare.domain.User;
import ao.autocare.repo.MembershipRepository;
import ao.autocare.repo.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository users;
    private final MembershipRepository memberships;

    public JwtAuthenticationFilter(
            JwtService jwtService, UserRepository users, MembershipRepository memberships) {
        this.jwtService = jwtService;
        this.users = users;
        this.memberships = memberships;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")
                && SecurityContextHolder.getContext().getAuthentication() == null) {

            String token = header.substring(7);
            String userId = jwtService.parseAccessSubject(token);
            if (userId != null) {
                users.findById(userId)
                        .filter(User::isActive)
                        .ifPresent(user -> authenticate(user, request));
            }
        }
        chain.doFilter(request, response);
    }

    private void authenticate(User user, HttpServletRequest request) {
        // Adesões suspensas não contam: o membro deixa de agir em nome da empresa.
        Membership membership = memberships
                .findFirstByUserIdAndSuspendedAtIsNullOrderByCreatedAtAsc(user.getId())
                .orElse(null);
        String organizationId = membership != null ? membership.getOrganization().getId() : null;
        String role = membership != null ? membership.getRole().name() : null;

        AuthPrincipal principal = new AuthPrincipal(
                user.getId(), user.getEmail(), user.isAdmin(), organizationId, role,
                membership != null ? membership.effectivePermissions()
                        : java.util.EnumSet.noneOf(Permission.class));

        var authorities = user.isAdmin()
                ? List.of(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("ROLE_ADMIN"))
                : List.of(new SimpleGrantedAuthority("ROLE_USER"));
        var auth = new UsernamePasswordAuthenticationToken(principal, null, authorities);
        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
