package ru.vlsu.myng.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@ToString
@Entity
@Table(name = "collection", uniqueConstraints = @UniqueConstraint(columnNames = { "name", "fk_user" }))
public class Collection {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Integer id;

        @Column(nullable = false)
        private String name;

        @ToString.Exclude
        @ManyToOne(optional = false)
        @JoinColumn(name = "fk_user")
        private User user;

        @ToString.Exclude
        @ManyToMany
        @JoinTable(name = "game_collection", joinColumns = @JoinColumn(name = "fk_collection"), inverseJoinColumns = @JoinColumn(name = "fk_game"))
        private Set<Game> games = new HashSet<>();
}