package com.polarnick.indexedSearch;

import com.sun.istack.internal.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Polyarnyi Nikolay - PolarNick239
 */
public class Searcher {

    private final Index index;

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage arguments: indexFile");
            return;
        }
        try {
            Searcher searcher = new Searcher(Index.<String>loadFromFile(args[0]));
            BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
            System.out.println("You can use english letters 'a'-'z', 'A'-'Z', russian letters 'а'-'я', 'А'-'Я'," +
                    " brackets '(' and ')', logical operators ' AND ', ' OR ' and distance operator ' /N ', ' /+N ', ' /-N '.");
            System.out.println("Enter query:");
            String line = in.readLine();
            while (line != null && !line.isEmpty()) {
                try {
                    Set<Occurance> matches = searcher.find(line);
                    if (matches.size() == 0) {
                        System.out.println("No matches!");
                    } else {
                        System.out.println(matches.size() + " matches: " + matches);
                    }
                } catch (IllegalArgumentException e) {
                    System.out.println("Incorrect query!");
                }
                System.out.println("Enter query:");
                line = in.readLine();
            }
        } catch (IOException e) {
            System.out.println("Error while reading file: " + e);
        } catch (ClassNotFoundException e) {
            System.out.println("Error while interpreting file: " + e);
        }
    }

    public Searcher(Index index) {
        this.index = index;
    }

    //Expr = AndExpr | OrExpr | (Expr) | Term | DistExpr
    //AndExpr = Expr AND Expr
    //OrExpr = Expr OR Expr
    //DistExpr = Expr /N Expr | Expr /+N Expr | Expr /-N Expr
    private static final String AND_OPERAND = " AND ";
    private static final String OR_OPERAND = " OR ";
    private static final String DIST_OPERATOR = " /";

    public Set<Occurance> find(String expression) {
        int[] pairBracket = new int[expression.length()];
        int[] opens = new int[expression.length()];
        int lastOpen = -1;
        for (int i = 0; i < expression.length(); i++) {
            char curC = expression.charAt(i);
            if (curC == '(') {
                opens[++lastOpen] = i;
            } else if (curC == ')') {
                if (lastOpen < 0) {
                    throw new IllegalArgumentException("No matching opening bracket!");
                }
                pairBracket[opens[lastOpen]] = i;
                pairBracket[i] = opens[lastOpen];
                opens[lastOpen] = -1;
                --lastOpen;
            } else if (AND_OPERAND.indexOf(curC) == -1
                    && OR_OPERAND.indexOf(curC) == -1
                    && "/+-0123456789".indexOf(curC) == -1//distance operator
                    && "()".indexOf(curC) == -1
                    && !index.isCorrectLetter(curC)) {
                throw new IllegalArgumentException("Incorrect symbol '" + curC + "'at position " + (i + 1));
            }
        }
        return find(pairBracket, expression, 0, expression.length());
    }

    private Set<Occurance> find(int[] pairBracket, String str, int from, int to) {
        Set<Occurance> result = findAnd(pairBracket, str, from, to);
        if (result != null) {
            return result;
        }
        result = findOr(pairBracket, str, from, to);
        if (result != null) {
            return result;
        }
        result = findDist(pairBracket, str, from, to);
        if (result != null) {
            return result;
        }
        if (str.charAt(from) == '(' && str.charAt(to - 1) == ')') {
            result = find(pairBracket, str, from + 1, to - 1);
        } else {
            for (int i = from; i < to; i++) {
                char curC = str.charAt(i);
                if (!index.isCorrectLetter(curC)) {
                    throw new IllegalArgumentException("Incorrect symbol '" + curC + "'at position " + (i + 1));
                }
            }
            Map<String, List<Integer>> filePoses = index.get(str.substring(from, to));
            result = new HashSet<>();
            for (String file : filePoses.keySet()) {
                for (int index : filePoses.get(file)) {
                    result.add(new Occurance(file, index, index));
                }
            }
        }
        return result;
    }

    @Nullable
    private Set<Occurance> findAnd(int[] pairBracket, String str, int from, int to) {
        Set<Occurance> result = null;
        int curFrom = from;
        for (int i = from; i < to; i++) {
            char curC = str.charAt(i);
            if (curC == '(') {
                i = pairBracket[i];
            } else if (isContainSubstringAt(str, i, AND_OPERAND)) {
                Set<Occurance> set = find(pairBracket, str, curFrom, i);
                if (result == null) {
                    result = set;
                } else {
                    Set<Occurance> retained = new HashSet<>();
                    for (Occurance old : result) {
                        for (Occurance that : set) {
                            if (old.file.equals(that.file)) {
                                retained.add(new Occurance(old.file, Math.min(old.from, that.from), Math.max(old.to, that.to)));
                            }
                        }
                    }
                    result = retained;
                }
                if (result.size() == 0) {
                    return result;
                }
                curFrom = i + AND_OPERAND.length();
                i = curFrom - 1;
            }
        }
        if (result != null) {
            Set<Occurance> lastSet = find(pairBracket, str, curFrom, to);
            Set<Occurance> retained = new HashSet<>();
            for (Occurance old : result) {
                for (Occurance that : lastSet) {
                    if (old.file.equals(that.file)) {
                        retained.add(new Occurance(old.file, Math.min(old.from, that.from), Math.max(old.to, that.to)));
                    }
                }
            }
            result = retained;
        }
        return result;
    }

    @Nullable
    private Set<Occurance> findOr(int[] pairBracket, String str, int from, int to) {
        Set<Occurance> result = null;
        int curFrom = from;
        for (int i = from; i < to; i++) {
            char curC = str.charAt(i);
            if (curC == '(') {
                i = pairBracket[i];
            } else if (isContainSubstringAt(str, i, OR_OPERAND)) {
                Set<Occurance> set = find(pairBracket, str, curFrom, i);
                if (result == null) {
                    result = set;
                } else {
                    result.addAll(set);
                }
                curFrom = i + OR_OPERAND.length();
                i = curFrom - 1;
            }
        }
        if (result != null) {
            Set<Occurance> lastSet = find(pairBracket, str, curFrom, to);
            result.addAll(lastSet);
        }
        return result;
    }

    @Nullable
    private Set<Occurance> findDist(int[] pairBracket, String str, int from, int to) {
        Set<Occurance> result = null;

        int curFrom = from;
        int prevDif = 0;
        boolean prevBothWays = false;
        for (int i = from; i <= to; i++) {
            if (i < to && str.charAt(i) == '(') {
                i = pairBracket[i];
            } else if (isContainSubstringAt(str, i, DIST_OPERATOR) || i >= to - 1) {
                if (i >= to - 1) {
                    i = to;
                    if(result == null) {
                        return null;
                    }
                }
                Set<Occurance> left = find(pairBracket, str, curFrom, i);
                if (result == null) {
                    result = left;
                } else {
                    Set<Occurance> newOc = new HashSet<>();
                    for (Occurance inPrev : result) {
                        for (Occurance inNew : left) {
                            if (inNew.file.equals(inPrev.file) && (inNew.from == inPrev.to + prevDif || (prevBothWays && inNew.from == inPrev.to - prevDif))) {
                                newOc.add(new Occurance(inPrev.file, Math.min(inPrev.from, inNew.from), Math.max(inPrev.to, inNew.to)));
                            }
                        }
                    }
                    result = newOc;
                }
                if (i != to) {
                    int dif = parseIntFromPosition(str, i + DIST_OPERATOR.length());
                    boolean bothWays = "0123456789".contains(str.charAt(i + DIST_OPERATOR.length()) + "");
                    curFrom = i + DIST_OPERATOR.length();
                    while (str.charAt(curFrom) != ' ') {
                        curFrom++;
                    }
                    curFrom++;
                    i = curFrom - 1;
                    prevDif = dif;
                    prevBothWays = bothWays;
                }
            }
        }
        return result;
    }

    private static int parseIntFromPosition(String str, int from) {
        int to = from;
        while (to < str.length() && "+-0123456789".indexOf(str.charAt(to)) != -1) {
            to++;
        }
        return Integer.parseInt(str.substring(from, to));
    }

    private static boolean isContainSubstringAt(String str, int from, String subStr) {
        if (from + subStr.length() >= str.length()) {
            return false;
        }
        for (int i = 0; i < subStr.length(); i++) {
            if (str.charAt(from + i) != subStr.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private static class Occurance {
        private String file;
        private int from;
        private int to;

        public Occurance(String file, int from, int to) {
            this.file = file;
            this.from = from;
            this.to = to;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Occurance occurance = (Occurance) o;

            if (from != occurance.from) return false;
            if (to != occurance.to) return false;
            if (file != null ? !file.equals(occurance.file) : occurance.file != null) return false;

            return true;
        }

        @Override
        public int hashCode() {
            int result = from;
            result = 239 * result + to;
            result = 239 * result + (file != null ? file.hashCode() : 0);
            return result;
        }

        @Override
        public String toString() {
            return "In file '" + file + "' at word " + (from == to ? from : from + " to word " + to);
        }
    }

}
