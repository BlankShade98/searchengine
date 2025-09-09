package searchengine.repositories;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.SearchIndex;
import searchengine.model.Page;
import searchengine.model.Site;

import java.util.List;

@Repository
public interface IndexRepository extends JpaRepository<SearchIndex, Integer> {
    @Query("SELECT i.page FROM SearchIndex i WHERE i.lemma.lemma = ?1 AND i.page.site IN ?2")
    List<Page> findPagesByLemmaAndSiteIn(String lemmaText, List<Site> sites);

    @Query("SELECT i.rank FROM SearchIndex i WHERE i.page.id = ?1 AND i.lemma.id = ?2")
    Float findRankByPageAndLemma(int pageId, int lemmaId);

    List<SearchIndex> findAllByPage(Page page);
}