package com.aiinterview.platform.model.entity;

import com.aiinterview.platform.model.enums.Role;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String fullName;

    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Role role = Role.ROLE_USER;

    /** Account is enabled (active). false = Inactive */
    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = false;

    /** Account is locked by admin. true = cannot login */
    @Column(nullable = false)
    @Builder.Default
    private boolean locked = false;

    /** Soft delete: null = not deleted, non-null = deleted at this time */
    @Column
    private LocalDateTime deletedAt;

    /** When an admin locked this account; null when never locked or unlocked */
    @Column(name = "locked_at")
    private LocalDateTime lockedAt;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private String verificationToken;
    private LocalDateTime verificationTokenExpiry;

    private String resetToken;
    private LocalDateTime resetTokenExpiry;

    @Builder.Default
    private int tokenVersion = 0;

    @Builder.Default
    private int loginAttempts = 0;

    private LocalDateTime lockUntil;

    /** TOTP second factor is required at login. */
    @Column(name = "totp_enabled", nullable = false)
    @Builder.Default
    private boolean totpEnabled = false;

    /** Base32 shared secret; set at 2FA setup, cleared when 2FA is disabled. */
    @Column(name = "totp_secret", length = 64)
    private String totpSecret;

    /**
     * Ảnh đại diện lưu inline dạng data URL (disk của deployment ephemeral nên không ghi file),
     * null = chưa có ảnh. Cột LONGTEXT vì base64 của ảnh 2 MB dài ~2.8 MB ký tự.
     */
    @Lob
    @Column(name = "avatar", columnDefinition = "LONGTEXT")
    private String avatar;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.name()));
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return enabled && deletedAt == null;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !locked;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }
}
