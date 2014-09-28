package com.polarnick.indexedSearch;

import java.io.*;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Polyarnyi Nikolay - PolarNick239
 */
public class Indexer {

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.out.println("Usage arguments: [-jN] [-f indexFileName] [dirs]*");
            System.out.println("Where '-jN' - count of threads to build index. For example to index in 4 threads: -j4");
            System.out.println("Where '-f indexFileName' - count of threads to build index. For example to index in 4 threads: -j4");
            System.out.println("Where 'dirs' - directories or files to be indexed");
            return;
        }

        final Map<File, String> files = new HashMap<>();
        int threadsCount = 2;
        String indexFilename = "index.ser";

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];

            if (arg.startsWith("-j")) {
                threadsCount = Integer.parseInt(arg.substring(2));
                continue;
            }

            if (arg.equals("-f")) {
                if (i == args.length - 1) {
                    System.out.println("Flag '-f' should be followed by output index filename!");
                    return;
                }
                i++;
                indexFilename = args[i];
                continue;
            }

            File file = new File(arg);
            if (!file.exists()) {
                System.out.println("There are no '" + arg + "' was found!");
            } else if (file.isFile()) {
                files.put(file, arg);
            } else if (file.isDirectory()) {
                Files.walkFileTree(file.toPath(), new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        files.put(file.toFile(), file.toString());
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
        }
        System.out.println("Count of threads to be used: " + threadsCount);
        System.out.println("Index will be saved to file: '" + indexFilename + "'");

        Indexer indexer = new Indexer();
        Index<String> index;
        try {
            long startTime = System.currentTimeMillis();
            index = indexer.index(files, Arrays.asList(Language.RU, Language.EN), threadsCount);
            long time = System.currentTimeMillis() - startTime;
            System.out.println("Index was build for "
                    + time + " ms = "
                    + (time / 1000) + " seconds = "
                    + (time / (60 * 1000)) + " minutes " + ((time / 1000) % 60) + " seconds!");
        } catch (InterruptedException e) {
            System.out.println("Execution was interrupted!");
            return;
        }

        index.saveToFile(indexFilename);
        System.out.println("Index was saved to file: " + indexFilename);

        Searcher<String> searcher = new Searcher<>(index);
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        System.out.println("You can use english letters 'a'-'z', 'A'-'Z', russian letters 'а'-'я', 'А'-'Я'," +
                " brackets '(' and ')', and logical operators ' AND ', ' OR '.");
        System.out.println("Enter query:");
        String line = in.readLine();
        while (line != null && !line.isEmpty()) {
            Set<String> matches = searcher.find(line);
            if (matches.size() == 0) {
                System.out.println("No matches!");
            } else {
                System.out.println(matches.size() + " matches: " + matches);
            }
            System.out.println("Enter query:");
            line = in.readLine();
        }
    }

    public <Value> Index<Value> index(Map<File, Value> files, final List<Language> langs, int threadsCount) throws IOException, InterruptedException {
        final Index<Value> index = new Index<>(langs);
        final AtomicInteger fileProcessed = new AtomicInteger(0);
        final AtomicLong sizeProcessed = new AtomicLong(0);
        long totalSize = 0;
        for (File file : files.keySet()) {
            totalSize += file.length();
        }

        List<Callable<Void>> tasks = new ArrayList<>();
        final long startTime = System.currentTimeMillis();
        for (final Map.Entry<File, Value> fileEntry : files.entrySet()) {
            final int filesCount = files.size();
            final long finalTotalSize = totalSize;
            tasks.add(new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    Index.traceCacheHits();
                    File file = fileEntry.getKey();
                    Value value = fileEntry.getValue();

                    IOException exception = null;
                    try {
                        BufferedReader in = new BufferedReader(new FileReader(file));
                        String line = in.readLine();
                        while (line != null) {
                            Set<String> tokens = new HashSet<>(getWords(line, langs));
                            for (String token : tokens) {
                                index.put(token, value);
                            }
                            line = in.readLine();
                        }
                        in.close();
                    } catch (IOException e) {
                        exception = e;
                    }
                    synchronized (System.out) {
                        if (exception != null) {
                            System.out.println("Exception occurred, while processing file: " + file + "\n" + exception);
                        }
                        System.out.println("Finished file: '" + file + "'");
                        System.out.println("Finished files: " + fileProcessed.incrementAndGet() + "/" + filesCount + " files"
                                + " (" + sizeProcessed.addAndGet(file.length()) / 1024 / 1024 + "/" + (finalTotalSize / 1024 / 1024) + " mb - " + (sizeProcessed.get() * 100 / finalTotalSize) + "%)."
                                + " Time passed: " + (System.currentTimeMillis() - startTime) / 1000 + " seconds");
                        System.out.flush();
                    }
                    return null;
                }
            });
        }

        ExecutorService executor = Executors.newFixedThreadPool(threadsCount);
        executor.invokeAll(tasks);
        return index;
    }

    private static List<String> getWords(String line, List<Language> langs) {
        List<String> words = new ArrayList<>();
        StringBuilder curWord = new StringBuilder();
        for (int charI = 0; charI < line.length(); charI++) {
            char c = line.charAt(charI);
            boolean isLetter = false;
            for (Language lang : langs) {
                if (lang.isCorrectLetter(c)) {
                    isLetter = true;
                }
            }
            if (isLetter) {
                curWord.append(c);
            } else {
                if (curWord.length() != 0) {
                    words.add(curWord.toString());
                    curWord = new StringBuilder();
                }
            }
        }
        if (curWord.length() != 0) {
            words.add(curWord.toString());
        }
        return words;
    }
}
