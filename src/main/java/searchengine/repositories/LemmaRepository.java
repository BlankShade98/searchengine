package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import searchengine.model.Lemma;
import searchengine.model.Site;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface LemmaRepository extends JpaRepository<Lemma, Integer> {
    Optional<Lemma> findByLemmaAndSite(String lemma, Site site);
    List<Lemma> findByLemmaIn(List<String> lemmas);

    @Query("SELECT COUNT(l) FROM Lemma l WHERE l.lemma = ?1 AND l.site IN ?2")
    int countByLemmaAndSiteIn(String lemma, List<Site> sites);

    List<Lemma> findAllBySiteAndLemmaIn(Site site, Collection<String> lemmas);

    long countBySite_Id(int siteId);
}