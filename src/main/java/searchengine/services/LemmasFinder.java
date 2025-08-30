package searchengine.services;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LemmasFinder {
    private final LuceneMorphology luceneMorphology;

    public LemmasFinder() throws IOException {
        this.luceneMorphology = new RussianLuceneMorphology();
    }

    public Map<String, Integer> findLemmas(String text) {
        Map<String, Integer> lemmas = new HashMap<>();
        Pattern pattern = Pattern.compile("[А-Яа-я]+");
        Matcher matcher = pattern.matcher(text);

        while (matcher.find()) {
            String word = matcher.group().toLowerCase();
            List<String> wordBaseForms = luceneMorphology.getMorphInfo(word);
            if (isServiceWord(wordBaseForms.get(0))) {
                continue;
            }
            List<String> normalForms = luceneMorphology.getNormalForms(word);
            if (normalForms.isEmpty()) {
                continue;
            }
            String lemma = normalForms.get(0);
            lemmas.put(lemma, lemmas.getOrDefault(lemma, 0) + 1);
        }
        return lemmas;
    }

    private boolean isServiceWord(String wordInfo) {
        String[] parts = wordInfo.split("\\|");
        if (parts.length > 1) {
            return parts[1].contains("МЕЖД") || parts[1].contains("СОЮЗ") ||
                    parts[1].contains("ПРЕДЛ") || parts[1].contains("ЧАСТ");
        }
        return false;
    }
}