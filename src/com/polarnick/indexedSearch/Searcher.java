package com.polarnick.indexedSearch;

import com.sun.istack.internal.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Set;

/**
 * @author Polyarnyi Nikolay - PolarNick239
 */
public class Searcher<Value> {

    private final Index<Value> index;

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage arguments: indexFile");
            return;
        }
        try {
            Searcher<String> searcher = new Searcher<>(Index.<String>loadFromFile(args[0]));
            BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
            System.out.println("You can use english letters 'a'-'z', 'A'-'Z', russian letters 'а'-'я', 'А'-'Я'," +
                    " brackets '(' and ')', and logical operators ' AND ', ' OR '.");
            System.out.println("Enter query:");
            String line = in.readLine();
            while (line != null && !line.isEmpty()) {
                try {
                    Set<String> matches = searcher.find(line);
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

    public Searcher(Index<Value> index) {
        this.index = index;
    }

    //Expr = AndExpr | OrExpr | (Expr) | Term
    //AndExpr = Expr AND Expr
    //OrExpr = Expr OR Expr
    private static final String AND_OPERAND = " AND ";
    private static final String OR_OPERAND = " OR ";

    public Set<Value> find(String expression) {
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
                    && "()".indexOf(curC) == -1
                    && !index.isCorrectLetter(curC)) {
                throw new IllegalArgumentException("Incorrect symbol '" + curC + "'at position " + (i + 1));
            }
        }
        return find(pairBracket, expression, 0, expression.length());
    }

    private Set<Value> find(int[] pairBracket, String str, int from, int to) {
        Set<Value> result = findAnd(pairBracket, str, from, to);
        if (result != null) {
            return result;
        }
        result = findOr(pairBracket, str, from, to);
        if (result != null) {
            return result;
        }
        if (str.charAt(from) == '(' && str.charAt(to - 1) == ')') {
            return find(pairBracket, str, from + 1, to - 1);
        } else {
            for (int i = from; i < to; i++) {
                char curC = str.charAt(i);
                if (!index.isCorrectLetter(curC)) {
                    throw new IllegalArgumentException("Incorrect symbol '" + curC + "'at position " + (i + 1));
                }
            }
            return index.get(str.substring(from, to));
        }
    }

    @Nullable
    private Set<Value> findAnd(int[] pairBracket, String str, int from, int to) {
        Set<Value> result = null;
        int curFrom = from;
        for (int i = from; i < to; i++) {
            char curC = str.charAt(i);
            if (curC == '(') {
                i = pairBracket[i];
            } else if (isContainSubstringAt(str, i, AND_OPERAND)) {
                Set<Value> set = find(pairBracket, str, curFrom, i);
                if (result == null) {
                    result = set;
                } else {
                    result.retainAll(set);
                }
                if (result.size() == 0) {
                    return result;
                }
                curFrom = i + AND_OPERAND.length();
                i = curFrom - 1;
            }
        }
        if (result != null) {
            Set<Value> lastSet = find(pairBracket, str, curFrom, to);
            result.retainAll(lastSet);
        }
        return result;
    }

    @Nullable
    private Set<Value> findOr(int[] pairBracket, String str, int from, int to) {
        Set<Value> result = null;
        int curFrom = from;
        for (int i = from; i < to; i++) {
            char curC = str.charAt(i);
            if (curC == '(') {
                i = pairBracket[i];
            } else if (isContainSubstringAt(str, i, OR_OPERAND)) {
                Set<Value> set = find(pairBracket, str, curFrom, i);
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
            Set<Value> lastSet = find(pairBracket, str, curFrom, to);
            result.addAll(lastSet);
        }
        return result;
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

}
