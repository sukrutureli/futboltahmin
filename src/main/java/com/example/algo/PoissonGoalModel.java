package com.example.algo;

import java.util.Optional;
import com.example.model.*;
import com.example.util.MathUtils;

public class PoissonGoalModel implements BettingAlgorithm {

	@Override
	public String name() {
		return "PoissonGoalModel";
	}

	public double[] weight() {
		return new double[] { 0.2, 0.7 };
	}

	@Override
	public PredictionResult predict(Match match, Optional<Odds> oddsOpt) {
		TeamStats h = match.getHomeStats();
		TeamStats a = match.getAwayStats();

		// veri eksikse nötr sonuç
		if (h == null || a == null || h.isEmpty() || a.isEmpty())
			return neutralResult(match);

		try {
			// --- 1. Ev avantajı (form + rating farkıyla ayarlanır) ---
			double baseHomeAdv = 1.10;
			double ratingAdj = clamp(1.0 + safeDiv((h.getRating100() - a.getRating100()), 600.0), 0.85, 1.15);
			double formAdj = clamp(1.0 + safeDiv((h.getAvgPointsPerMatch() - a.getAvgPointsPerMatch()), 5.0), 0.75, 1.25);
			double homeAdv = clamp(baseHomeAdv * ratingAdj * formAdj, 0.85, 1.35);

			// --- 2. Son maç formuna göre çarpan ---
			double formFactorH = clamp(1.0 + 0.1 * (h.getAvgPointsPerMatch() - 1.0), 0.85, 1.15);
			double formFactorA = clamp(1.0 + 0.1 * (a.getAvgPointsPerMatch() - 1.0), 0.85, 1.15);

			// --- 3. Beklenen goller (atak + savunma) ---
			double lambdaH = homeAdv * (0.55 * h.getAvgGF() * formFactorH + 0.45 * a.getAvgGA());
			double lambdaA = (0.55 * a.getAvgGF() * formFactorA + 0.45 * h.getAvgGA());
			lambdaH = Math.max(0.1, lambdaH);
			lambdaA = Math.max(0.1, lambdaA);

			// --- 4. Olasılık dağılımları ---
			int maxG = 6;
			double[] pH = MathUtils.poissonDist(lambdaH, maxG);
			double[] pA = MathUtils.poissonDist(lambdaA, maxG);

			double pHome = 0, pDraw = 0, pAway = 0;
			double pOver25 = 0, pBttsYes = 0;
			double bestP = -1;
			String bestScore = "";

			for (int i = 0; i <= maxG; i++) {
				for (int j = 0; j <= maxG; j++) {
					double pij = pH[i] * pA[j];
					if (i > j)
						pHome += pij;
					else if (i == j)
						pDraw += pij;
					else
						pAway += pij;

					if (i + j >= 3)
						pOver25 += pij;
					if (i > 0 && j > 0)
						pBttsYes += pij;

					if (pij > bestP) {
						bestP = pij;
						bestScore = i + "-" + j;
					}
				}
			}

			// --- 6. Nihai karar ---
			double maxRes = Math.max(pHome, Math.max(pDraw, pAway));
			String pick = (maxRes == pHome) ? "MS1" : (maxRes == pDraw ? "MSX" : "MS2");
			double confidence = Math.round(maxRes * 100.0) / 100.0;

			return new PredictionResult(name(), match.getHomeTeam(), match.getAwayTeam(), safeProb(pHome),
					safeProb(pDraw), safeProb(pAway), safeProb(pOver25), safeProb(pBttsYes), pick, confidence,
					bestScore);

		} catch (Exception e) {
			System.out.println("PoissonGoalModel hata: " + e.getMessage());
			return neutralResult(match);
		}
	}

	// --- Yardımcı metotlar ---
	private double clamp(double v, double lo, double hi) {
		return Math.max(lo, Math.min(hi, v));
	}

	private double safeDiv(double a, double b) {
		return (b == 0) ? 0 : a / b;
	}

	private double safeProb(double v) {
		return Double.isFinite(v) ? Math.min(0.99, Math.max(0.01, v)) : 0.33;
	}

	private PredictionResult neutralResult(Match m) {
		return new PredictionResult(name(), m.getHomeTeam(), m.getAwayTeam(), 0.33, 0.34, 0.33, 0.50, 0.50, "MSX", 0.33,
				"");
	}
}