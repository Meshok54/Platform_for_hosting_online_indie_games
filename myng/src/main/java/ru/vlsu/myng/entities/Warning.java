package ru.vlsu.myng.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@ToString
@Entity
@Table(name = "warning")
public class Warning {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private String reason;

    @ToString.Exclude
    @ManyToOne(optional = false)
    @JoinColumn(name = "fk_mod")
    private User moderator;

    @ToString.Exclude
    @ManyToOne(optional = false)
    @JoinColumn(name = "fk_user")
    private User user;
}