package com.polarnick.gain;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Polyarnyi Nikolay - PolarNick239
 */
public class CalculateGain {

    public static void main(String[] args) throws IOException {
        System.out.println("Enter scores (space or comma-separated):");
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        String line = in.readLine();
        String[] tokens = line.split("[ ,]");
        List<Integer> scores = new ArrayList<>();
        for (String token : tokens) {
            if (!token.isEmpty()) {
                scores.add(Integer.parseInt(token));
            }
        }
        System.out.println("DCG: " + calcDCG(scores));
        System.out.println("NDCG: " + calcNDCG(scores));
        System.out.println("PFound: " + calcPFound(scores));
    }

    private static double log2(double a) {
        return Math.log(a) / Math.log(2);
    }

    private static double calcDCG(List<Integer> scores) {
        double res = scores.get(0);
        for (int i = 1; i < scores.size(); i++) {
            res += scores.get(i) / log2(i + 1);
        }
        return res;
    }

    private static double calcIDCG(List<Integer> scores) {
        List<Integer> sorted = new ArrayList<>(scores);
        Collections.sort(sorted, Collections.reverseOrder());
        return calcDCG(sorted);
    }

    private static double calcNDCG(List<Integer> scores) {
        return calcDCG(scores) / calcIDCG(scores);
    }

    private static double calcPFound(List<Integer> scores) {
        final double pBreak = 0.15;

        double vitalThreshold = Collections.max(scores) * 0.90;

        double prevPLook = 1.0;
        double prevPRel = scores.get(0) >= vitalThreshold ? 0.40 : 0.00;
        double res = prevPLook * prevPRel;
        for (int i = 1; i < scores.size(); i++) {
            double curPLook = prevPLook * (1 - prevPRel) * (1 - pBreak);
            double curPRel = scores.get(i) >= vitalThreshold ? 0.40 : 0.00;
            res += curPLook * curPRel;
            prevPLook = curPLook;
            prevPRel = curPRel;
        }
        return res;
    }

}
