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
@Table(name = "review", uniqueConstraints = @UniqueConstraint(columnNames = { "fk_user", "fk_game" }))
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, columnDefinition = "TINYINT UNSIGNED")
    private Byte rating;

    @Column(columnDefinition = "TEXT")
    private String text;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(nullable = false, columnDefinition = "SMALLINT UNSIGNED")
    private Integer reportCount = 0;

    @ToString.Exclude
    @ManyToOne
    @JoinColumn(name = "fk_user", nullable = false)
    private User user;

    @ToString.Exclude
    @ManyToOne
    @JoinColumn(name = "fk_game", nullable = false)
    private Game game;
}