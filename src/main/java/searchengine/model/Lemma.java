package searchengine.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import javax.persistence.*;

@Entity
@Table(name = "lemma", uniqueConstraints = {@UniqueConstraint(columnNames = {"lemma", "site_id"})})
@Getter
@Setter
@NoArgsConstructor
public class Lemma {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id", nullable = false)
    private Site site;

    @Column(columnDefinition = "VARCHAR(255) NOT NULL")
    private String lemma;

    @Column(columnDefinition = "INT NOT NULL")
    private int frequency;

    public Lemma(String lemma, Site site) {
        this.lemma = lemma;
        this.site = site;
        this.frequency = 0;
    }
}