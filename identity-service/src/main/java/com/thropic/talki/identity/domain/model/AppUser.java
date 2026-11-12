package com.thropic.talki.identity.domain.model;

import jakarta.persistence.*;

@Entity
@Table(name = "app_users")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private String academicSegment;

    @Enumerated(EnumType.STRING)
    private UserRole role = UserRole.STUDENT;

    /** False al registrar; true al consumir el token de verificación de correo. */
    @Column(nullable = false)
    private boolean emailVerified = false;

    public AppUser() {}

    public AppUser(String email, String passwordHash, String username, String academicSegment) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.username = username;
        this.academicSegment = academicSegment;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String p) { this.passwordHash = p; }
    public String getUsername() { return username; }
    public void setUsername(String u) { this.username = u; }
    public String getAcademicSegment() { return academicSegment; }
    public void setAcademicSegment(String a) { this.academicSegment = a; }
    public UserRole getRole() { return role; }
    public void setRole(UserRole r) { this.role = r; }
    public boolean isEmailVerified() { return emailVerified; }
    public void setEmailVerified(boolean v) { this.emailVerified = v; }
}
