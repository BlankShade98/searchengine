package searchengine.services;

import org.apache.lucene.morphology.LuceneMorphology;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record LemmasFinder(LuceneMorphology russianMorphology, LuceneMorphology englishMorphology) {
    private static final Pattern WORD_PATTERN = Pattern.compile("[a-zA-Zа-яА-Я]+");

    public Map<String, Integer> findLemmas(String text) {
        Map<String, Integer> lemmas = new HashMap<>();
        Matcher matcher = WORD_PATTERN.matcher(text.toLowerCase());

        while (matcher.find()) {
            String word = matcher.group();

            LuceneMorphology currentMorphology;
            if (isRussian(word)) {
                currentMorphology = russianMorphology;
            } else {
                currentMorphology = englishMorphology;
            }

            List<String> wordBaseForms = currentMorphology.getMorphInfo(word);
            if (!wordBaseForms.isEmpty() && isServiceWord(wordBaseForms.get(0))) {
                continue;
            }

            List<String> normalForms = currentMorphology.getNormalForms(word);
            if (!normalForms.isEmpty()) {
                String lemma = normalForms.get(0);
                lemmas.put(lemma, lemmas.getOrDefault(lemma, 0) + 1);
            }
        }
        return lemmas;
    }

    private boolean isRussian(String word) {
        return word.chars().anyMatch(c -> c >= 'а' && c <= 'я');
    }

    private boolean isServiceWord(String wordInfo) {
        return wordInfo.matches(".*(PREP|CONJ|PART|INT|PN|ARTICLE)$");
    }
}