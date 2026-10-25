package ru.vlsu.myng.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@ToString
@Entity
@Table(name = "user", uniqueConstraints = {
        @UniqueConstraint(columnNames = "username"),
        @UniqueConstraint(columnNames = "github_username")
})
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 20)
    private String username;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @ToString.Exclude
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(length = 255)
    private String bio;

    @ToString.Exclude
    @Column(nullable = true, columnDefinition = "LONGBLOB")
    private byte[] profilePic;

    @Column(name = "registered_at")
    private Instant registeredAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.user;

    @Column(name = "github_username")
    private String githubUsername;

    @ToString.Exclude
    @OneToMany(mappedBy = "developer")
    private List<Game> games = new ArrayList<>();

    @ToString.Exclude
    @OneToMany(mappedBy = "user")
    private List<Review> reviews = new ArrayList<>();

    public enum Role {
        user, dev, mod, admin
    }
}