package ru.vlsu.myng.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@ToString
@Entity
@Table(name = "dev_application", uniqueConstraints = @UniqueConstraint(columnNames = "fk_user"))
public class DevApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private Instant createdAt;

    @Column(name = "github_username")
    private String githubUsername;

    @Column(columnDefinition = "TEXT")
    private String text;

    @ToString.Exclude
    @OneToOne(optional = false)
    @JoinColumn(name = "fk_user")
    private User user;
}