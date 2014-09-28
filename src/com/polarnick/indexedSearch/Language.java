package com.polarnick.indexedSearch;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.WrongCharaterException;
import org.apache.lucene.morphology.english.EnglishLuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author Polyarnyi Nikolay - PolarNick239
 */
public enum Language {

    RU(Arrays.asList('а', 'А'), Arrays.asList('я', 'Я'), createRussianMorphology()),
    EN(Arrays.asList('a', 'A'), Arrays.asList('z', 'Z'), createEnglishMorphology());

    private final List<Character> mins;
    private final List<Character> maxs;
    private final LuceneMorphology morphology;

    private Language(List<Character> mins, List<Character> maxs, LuceneMorphology morphology) {
        if (mins.size() != maxs.size()) {
            throw new IllegalArgumentException("Mins list must corresponds to maxs! Character mins[i] must has corresponding upper limit maxs[i]!");
        }
        this.mins = mins;
        this.maxs = maxs;
        this.morphology = morphology;
    }

    public boolean isCorrectLetter(char c) {
        for (int i = 0; i < mins.size(); i++) {
            if (c >= mins.get(i) && c <= maxs.get(i)) {
                return true;
            }
        }
        return false;
    }

    public List<String> getNormalForms(String word) {
        try {
            return morphology.getNormalForms(word);
        } catch (WrongCharaterException e) {
            return Collections.emptyList();
        }
    }

    private static LuceneMorphology createRussianMorphology() {
        try {
            return new RussianLuceneMorphology();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static LuceneMorphology createEnglishMorphology() {
        try {
            return new EnglishLuceneMorphology();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
