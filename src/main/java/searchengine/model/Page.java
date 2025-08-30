package searchengine.model;

import lombok.Getter;
import lombok.Setter;
import javax.persistence.*;

@Entity
@Table(name = "page", indexes = { @javax.persistence.Index(name = "path_index", columnList = "path") })
@Getter
@Setter
public class Page {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id", nullable = false)
    private Site site;

    @Column(columnDefinition = "VARCHAR(255) NOT NULL")
    private String path;

    @Column(columnDefinition = "INT NOT NULL")
    private int code;

    @Column(columnDefinition = "MEDIUMTEXT NOT NULL")
    private String content;
}