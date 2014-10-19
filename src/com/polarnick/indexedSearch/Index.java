package com.polarnick.indexedSearch;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Polyarnyi Nikolay - PolarNick239
 */
public class Index implements Serializable {

    private static final long serialVersionUID = 2391;

    private final List<Language> langs;
    private transient ConcurrentMap<String, ConcurrentMap<String, List<Integer>>> resultsByNormForm;
    private transient ThreadLocal<Map<String, Set<String>>> normalFormsCache;

    public Index(List<Language> langs) {
        this.langs = langs;
        this.resultsByNormForm = new ConcurrentHashMap<>();
        initCache();
    }

    private void initCache() {
        normalFormsCache = new ThreadLocal<Map<String, Set<String>>>() {
            @Override
            protected Map<String, Set<String>> initialValue() {
                return new HashMap<>();
            }
        };
    }

    public void put(String token, String file, int position) {
        token = token.toLowerCase();
        Set<String> normForms = getAllNormForms(token);

        for (String normForm : normForms) {
            ConcurrentMap<String, List<Integer>> values = resultsByNormForm.get(normForm);
            if (values == null) {
                values = new ConcurrentHashMap<>();
                ConcurrentMap<String, List<Integer>> oldSet = resultsByNormForm.putIfAbsent(normForm, values);
                if (oldSet != null) {
                    values = oldSet;
                }
            }
            List<Integer> indexes = values.get(file);
            if(indexes == null) {
                indexes = Collections.synchronizedList(new ArrayList<Integer>());
                List<Integer> oldList = values.putIfAbsent(file, indexes);
                if (oldList != null) {
                    indexes = oldList;
                }
            }
            indexes.add(position);
        }
    }

    public Map<String, List<Integer>> get(String word) {
        for (int i = 0; i < word.length(); i++) {
            if (!isCorrectLetter(word.charAt(i))) {
                return Collections.emptyMap();
            }
        }

        word = word.toLowerCase();
        Set<String> normForms = getAllNormForms(word);

        Map<String, List<Integer>> res = new HashMap<>();
        for (String normForm : normForms) {
            Map<String, List<Integer>> values = resultsByNormForm.get(normForm);
            if (values != null) {
                res.putAll(values);
            }
        }
        return res;
    }

    public boolean isCorrectLetter(char c) {
        for (Language lang : langs) {
            if (lang.isCorrectLetter(c)) {
                return true;
            }
        }
        return false;
    }

    private static AtomicInteger cacheMisses = new AtomicInteger(0);
    private static AtomicInteger cacheHits = new AtomicInteger(0);
    private static final boolean CACHE_DISABLED = false;
    private static final boolean CACHE_TRASING_DISABLED = true;

    private Set<String> getAllNormForms(String token) {
        Set<String> normForms;
        if (!CACHE_DISABLED) {
            normForms = normalFormsCache.get().get(token);
            if (normForms != null) {
                if (!CACHE_TRASING_DISABLED) {
                    cacheHits.incrementAndGet();
                }
                return normForms;
            } else {
                if (!CACHE_TRASING_DISABLED) {
                    cacheMisses.incrementAndGet();
                }
            }
        }

        normForms = new HashSet<>();
        for (Language lang : langs) {
            normForms.addAll(lang.getNormalForms(token));
        }
        if (!CACHE_DISABLED) {
            normalFormsCache.get().put(token, normForms);
        }
        return normForms;
    }

    protected static void traceCacheHits() {
        if (!CACHE_TRASING_DISABLED) {
            int misses = cacheMisses.get();
            int hits = cacheHits.get();
            synchronized (System.out) {
                if (misses + hits != 0) {
                    System.out.println("Cache misses/hits: " + misses + "/" + hits + ". Hits: " + (hits * 100 / (hits + misses)) + "%");
                    System.out.flush();
                } else {
                    System.out.println("Cache misses/hits: 0/0. Hits: 0%");
                }
            }
        }
    }

    public void saveToFile(String fileName) throws IOException {
        ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(new File(fileName))));
        oos.writeObject(this);
        oos.close();
    }

    public static Index loadFromFile(String fileName) throws IOException, ClassNotFoundException {
        ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(new FileInputStream(new File(fileName))));
        Index index = (Index) ois.readObject();
        ois.close();
        return index;
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        long startTime = System.currentTimeMillis();
        System.out.println("Reading index...");
        in.defaultReadObject();
        initCache();
        List<String> files = (List<String>) in.readObject();
        System.out.println("Read values count: " + files.size());

        int normsCount = in.readInt();
        System.out.println("Reading norm forms (" + normsCount + ")...");

        final int PROCENT_STEP = 10;
        int nextProcent = PROCENT_STEP;
        this.resultsByNormForm = new ConcurrentHashMap<>(normsCount);
        for (int i = 1; i <= normsCount; i++) {
            String normForm = (String) in.readObject();
            int valuesCount = in.readInt();

            ConcurrentMap<String, List<Integer>> occurs = this.resultsByNormForm.get(normForm);
            if (occurs == null) {
                occurs = new ConcurrentHashMap<>(valuesCount);
            }
            for (int j = 0; j < valuesCount; j++) {
                occurs.put(files.get(in.readInt()), (List<Integer>) in.readObject());
            }
            this.resultsByNormForm.put(normForm, occurs);

            int procent = i * 100 / normsCount;
            if (procent >= nextProcent) {
                System.out.println("Reading norm forms... (" + i + "/" + normsCount + ") " + procent + "%");
                nextProcent += PROCENT_STEP;
            }
        }
        System.out.println("Index reading was proceed for " + (System.currentTimeMillis() - startTime) + " ms!");
    }

    private void writeObject(ObjectOutputStream out) throws IOException, ClassNotFoundException {
        long startTime = System.currentTimeMillis();
        System.out.println("Writing index...");
        HashMap<String, Integer> filesIndexes = new HashMap<>();
        {
            List<String> values = new ArrayList<>();
            for (String normalForm : resultsByNormForm.keySet()) {
                for (String file : resultsByNormForm.get(normalForm).keySet()) {
                    if (!filesIndexes.containsKey(file)) {
                        filesIndexes.put(file, values.size());
                        values.add(file);
                    }
                }
            }

            out.defaultWriteObject();
            System.out.println("Writing different values... (count: " + values.size() + ")");
            out.writeObject(values);
        }
        System.out.println("Writing normal forms... (count: " + this.resultsByNormForm.size() + ")");
        final int PROCENT_STEP = 10;
        int nextProcentToTrace = PROCENT_STEP;
        int nextNorm = 1;
        out.writeInt(resultsByNormForm.size());
        for (String normalForm : resultsByNormForm.keySet()) {
            out.writeObject(normalForm);
            Map<String, List<Integer>> occurences = resultsByNormForm.get(normalForm);
            out.writeInt(occurences.size());
            for (Map.Entry<String, List<Integer>> occur : occurences.entrySet()) {
                out.writeInt(filesIndexes.get(occur.getKey()));
                out.writeObject(occur.getValue());
            }
            int currentProcent = nextNorm * 100 / resultsByNormForm.size();
            if (currentProcent >= nextProcentToTrace) {
                nextProcentToTrace += PROCENT_STEP;
                System.out.println("Writing normal forms processed: " + currentProcent + "% (" + nextNorm + "/" + resultsByNormForm.size() + ")");
            }
            nextNorm++;
        }
        System.out.println("Writing index was finished for " + (System.currentTimeMillis() - startTime) + " ms!");
    }

}
