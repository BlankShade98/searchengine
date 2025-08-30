package searchengine.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import javax.persistence.*;

@Entity
@Table(name = "`index`", indexes = { @javax.persistence.Index(name = "page_lemma_index", columnList = "page_id, lemma_id") })
@Getter
@Setter
@NoArgsConstructor
public class SearchIndex {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "page_id", nullable = false)
    private Page page;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lemma_id", nullable = false)
    private Lemma lemma;

    @Column(name = "`rank`", columnDefinition = "FLOAT NOT NULL")
    private float rank;

    public SearchIndex(Page page, Lemma lemma, float rank) {
        this.page = page;
        this.lemma = lemma;
        this.rank = rank;
    }
}