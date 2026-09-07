package com.example.algo;

import java.util.Optional;

import com.example.model.Match;
import com.example.model.Odds;
import com.example.model.PredictionResult;
import com.example.model.TeamStats;

/**
 * Conservative model that combines a statistical goal model with de-vigged
 * bookmaker probabilities. The statistical signal is shrunk when team data is
 * thin, so a short hot/cold streak cannot create an artificial 75-80% signal.
 *
 * This model deliberately keeps market information as a stabilizer rather than
 * treating it as ground truth. Missing/invalid odds simply remove that signal.
 */
public class EvidenceWeightedModel implements BettingAlgorithm {

    private static final double STAT_WEIGHT_GOALS = 0.60;
    private static final double MARKET_WEIGHT_GOALS = 0.40;
    private static final double STAT_WEIGHT_1X2 = 0.55;
    private static final double MARKET_WEIGHT_1X2 = 0.45;

    private static final double MIN_RELIABILITY = 0.35;
    private static final double MAX_RELIABILITY = 1.00;

    private final PoissonGoalModel poisson = new PoissonGoalModel();

    @Override
    public String name() {
        return "EvidenceWeightedModel";
    }

    @Override
    public double[] weight() {
        return new double[] { 1.0, 1.0 };
    }

    @Override
    public PredictionResult predict(Match match, Optional<Odds> oddsOpt) {
        PredictionResult stat = poisson.predict(match, oddsOpt);
        TeamStats home = match.getHomeStats();
        TeamStats away = match.getAwayStats();

        if (home == null || away == null || home.isEmpty() || away.isEmpty()) {
            return stat.withAlgorithm(name());
        }

        double reliability = dataReliability(home, away);

        // Thin data should pull model-only probabilities toward neutral.
        double statHome = shrink(stat.getpHome(), 1.0 / 3.0, reliability);
        double statDraw = shrink(stat.getpDraw(), 1.0 / 3.0, reliability);
        double statAway = shrink(stat.getpAway(), 1.0 / 3.0, reliability);
        double statOver = shrink(stat.getpOver25(), 0.50, reliability);
        double statBtts = shrink(stat.getpBttsYes(), 0.50, reliability);

        double pHome = statHome;
        double pDraw = statDraw;
        double pAway = statAway;
        double pOver = statOver;
        double pBtts = statBtts;

        if (oddsOpt.isPresent()) {
            Odds odds = oddsOpt.get();

            double[] market1x2 = deVig3(odds.getMs1(), odds.getMsX(), odds.getMs2());
            if (market1x2 != null) {
                pHome = blend(statHome, market1x2[0], STAT_WEIGHT_1X2, MARKET_WEIGHT_1X2);
                pDraw = blend(statDraw, market1x2[1], STAT_WEIGHT_1X2, MARKET_WEIGHT_1X2);
                pAway = blend(statAway, market1x2[2], STAT_WEIGHT_1X2, MARKET_WEIGHT_1X2);
            }

            Double marketOver = deVigSelected(odds.getOver25(), odds.getUnder25());
            if (marketOver != null) {
                pOver = blend(statOver, marketOver, STAT_WEIGHT_GOALS, MARKET_WEIGHT_GOALS);
            }

            Double marketBtts = deVigSelected(odds.getBttsYes(), odds.getBttsNo());
            if (marketBtts != null) {
                pBtts = blend(statBtts, marketBtts, STAT_WEIGHT_GOALS, MARKET_WEIGHT_GOALS);
            }
        }

        double[] normalized1x2 = normalize3(pHome, pDraw, pAway);
        pHome = normalized1x2[0];
        pDraw = normalized1x2[1];
        pAway = normalized1x2[2];

        pOver = safeProb(pOver);
        pBtts = safeProb(pBtts);

        double max1x2 = Math.max(pHome, Math.max(pDraw, pAway));
        String pick = max1x2 == pHome ? "MS1" : (max1x2 == pDraw ? "MSX" : "MS2");

        // Confidence reflects both probability separation and data reliability.
        // It is intentionally conservative and is not used as another probability.
        double separation = max1x2 - secondLargest(pHome, pDraw, pAway);
        double confidence = clamp(0.50 + 0.35 * reliability + 0.60 * separation, 0.50, 0.90);

        return new PredictionResult(
                name(),
                match.getHomeTeam(),
                match.getAwayTeam(),
                pHome,
                pDraw,
                pAway,
                pOver,
                pBtts,
                pick,
                confidence,
                stat.getScoreline());
    }

    private double dataReliability(TeamStats home, TeamStats away) {
        double homeGames = clamp(home.getLast5Count() / 5.0, 0.0, 1.0);
        double awayGames = clamp(away.getLast5Count() / 5.0, 0.0, 1.0);

        double goalCompleteness = completeness(home.getAvgGF(), home.getAvgGA())
                * completeness(away.getAvgGF(), away.getAvgGA());
        double ratingCompleteness = (home.getRating100() > 0.0 && away.getRating100() > 0.0) ? 1.0 : 0.60;

        double reliability = 0.45 * ((homeGames + awayGames) / 2.0)
                + 0.40 * goalCompleteness
                + 0.15 * ratingCompleteness;

        return clamp(reliability, MIN_RELIABILITY, MAX_RELIABILITY);
    }

    private double completeness(double gf, double ga) {
        return (gf > 0.0 || ga > 0.0) ? 1.0 : 0.0;
    }

    private double shrink(double probability, double neutral, double reliability) {
        return neutral + reliability * (safeProb(probability) - neutral);
    }

    private Double deVigSelected(Double selectedOdd, Double oppositeOdd) {
        if (!validOdd(selectedOdd) || !validOdd(oppositeOdd)) {
            return null;
        }
        double selectedRaw = 1.0 / selectedOdd;
        double oppositeRaw = 1.0 / oppositeOdd;
        double sum = selectedRaw + oppositeRaw;
        return sum > 0.0 && Double.isFinite(sum) ? selectedRaw / sum : null;
    }

    private double[] deVig3(Double homeOdd, Double drawOdd, Double awayOdd) {
        if (!validOdd(homeOdd) || !validOdd(drawOdd) || !validOdd(awayOdd)) {
            return null;
        }
        double h = 1.0 / homeOdd;
        double d = 1.0 / drawOdd;
        double a = 1.0 / awayOdd;
        double sum = h + d + a;
        if (!(sum > 0.0) || !Double.isFinite(sum)) {
            return null;
        }
        return new double[] { h / sum, d / sum, a / sum };
    }

    private boolean validOdd(Double odd) {
        return odd != null && Double.isFinite(odd) && odd > 1.0;
    }

    private double blend(double model, double market, double modelWeight, double marketWeight) {
        return safeProb(modelWeight * model + marketWeight * market);
    }

    private double[] normalize3(double p1, double px, double p2) {
        p1 = safeProb(p1);
        px = safeProb(px);
        p2 = safeProb(p2);
        double sum = p1 + px + p2;
        if (!(sum > 0.0) || !Double.isFinite(sum)) {
            return new double[] { 0.33, 0.34, 0.33 };
        }
        return new double[] { p1 / sum, px / sum, p2 / sum };
    }

    private double secondLargest(double a, double b, double c) {
        return a + b + c - Math.max(a, Math.max(b, c)) - Math.min(a, Math.min(b, c));
    }

    private double safeProb(double value) {
        if (!Double.isFinite(value)) {
            return 0.50;
        }
        return clamp(value, 0.01, 0.99);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
