package searchengine.model;

import lombok.Getter;
import lombok.Setter;
import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "site")
@Getter
@Setter
public class Site {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "ENUM('INDEXING', 'INDEXED', 'FAILED') NOT NULL")
    private Status status;

    @Column(name = "status_time", columnDefinition = "DATETIME NOT NULL")
    private LocalDateTime statusTime;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(columnDefinition = "VARCHAR(255) NOT NULL")
    private String url;

    @Column(columnDefinition = "VARCHAR(255) NOT NULL")
    private String name;
}