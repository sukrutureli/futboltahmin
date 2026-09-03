package com.example.algo;

import java.util.Optional;
import com.example.model.*;

/**
 * Geliştirilmiş SimpleHeuristicModel: - Lig bağımsız normalize farklar (form +
 * gol + rating) - Piyasa ile %15 karışım (market blend) - Softmax kalibrasyonu
 * (iyi olasılık dağılımı) - Over/Under & BTTS olasılığı dengeli - Tahmini skor:
 * trend bazlı
 */
public class SimpleHeuristicModel implements BettingAlgorithm {

	private static final double OVER_CONFIDENCE_SHRINK = 0.85;

	@Override
	public String name() {
		return "SimpleHeuristicModel";
	}

	@Override
	public double[] weight() {
		// Bu model hem MS hem Alt/Üst sinyali verir, dengeli ağırlık
		return new double[] { 0.5, 0.3 };
	}

	@Override
	public PredictionResult predict(Match match, Optional<Odds> oddsOpt) {
		TeamStats h = match.getHomeStats();
		TeamStats a = match.getAwayStats();
		if (h == null || a == null || h.isEmpty() || a.isEmpty())
			return neutralResult(match);

		try {
			// --- 1. Normalize farklar (ligsiz, 0-1 ölçeğinde tut) ---
			double formDiff = Math.tanh(safeDiv(h.getAvgPointsPerMatch() - a.getAvgPointsPerMatch(), 2.5));
			double goalDiff = Math.tanh(safeDiv((h.getAvgGF() - h.getAvgGA()) - (a.getAvgGF() - a.getAvgGA()), 3.0));
			double ratingDiff = Math.tanh(safeDiv(h.getRating100() - a.getRating100(), 120.0));

			// --- 2. Kombine güç skoru ---
			double s = 0.45 * formDiff + 0.35 * goalDiff + 0.20 * ratingDiff;

			// --- 3. Softmax tabanlı normalize edilmiş olasılıklar ---
			double homeAdv = 0.12; // sabit ev avantajı
			double Lh = s + homeAdv;
			double Ld = -Math.abs(s) * 0.9;
			double La = -s;

			double eh = Math.exp(Lh);
			double ed = Math.exp(Ld);
			double ea = Math.exp(La);
			double Z = eh + ed + ea;
			double pHome = eh / Z;
			double pDraw = ed / Z;
			double pAway = ea / Z;

			// --- 4. Over/Under ve BTTS hesaplamaları ---
			// Hücum gücünü rakibin savunma ortalamasıyla eşleştir. Böylece yalnızca
			// iki takımın toplam gol ortalamasına veya GF çarpımına aşırı güvenmeyiz.
			double expectedHome = Math.max(0.05, (h.getAvgGF() + a.getAvgGA()) / 2.0);
			double expectedAway = Math.max(0.05, (a.getAvgGF() + h.getAvgGA()) / 2.0);
			double expectedTotal = expectedHome + expectedAway;

			// Poisson yaklaşımıyla toplam golün 2.5 üstü olma olasılığı.
			double p0 = Math.exp(-expectedTotal);
			double p1 = p0 * expectedTotal;
			double p2 = p1 * expectedTotal / 2.0;
			double rawPOver25 = clamp(1.0 - (p0 + p1 + p2), 0.10, 0.90);

			// Tek modelin aşırı yüksek/düşük gol güvenini yumuşat. Yönü değiştirmeden
			// olasılığı %50'ye doğru %15 daraltıyoruz; ensemble hâlâ Poisson ağırlığını
			// ve diğer sinyalleri kullanmaya devam ediyor.
			double pOver25 = 0.50 + OVER_CONFIDENCE_SHRINK * (rawPOver25 - 0.50);

			// İki takımın da en az bir gol bulması: rakip savunması doğrudan hesaba
			// katılır ve tek tarafın gol atamama riski doğal olarak cezalandırılır.
			double pHomeScores = 1.0 - Math.exp(-expectedHome);
			double pAwayScores = 1.0 - Math.exp(-expectedAway);
			double pBttsYes = clamp(pHomeScores * pAwayScores, 0.10, 0.90);

			// --- 5. Piyasa karışımı (%15) ---
			if (oddsOpt.isPresent()) {
				Odds o = oddsOpt.get();
				double sH = 1.0 / o.getMs1();
				double sD = 1.0 / o.getMsX();
				double sA = 1.0 / o.getMs2();
				double R = sH + sD + sA;
				double mH = sH / R, mD = sD / R, mA = sA / R;
				double w = 0.15;
				pHome = (1 - w) * pHome + w * mH;
				pDraw = (1 - w) * pDraw + w * mD;
				pAway = (1 - w) * pAway + w * mA;
			}

			// --- 6. Tahmini skor ---
			String score = "";

			// --- 7. Nihai tahmin ---
			double maxRes = Math.max(pHome, Math.max(pDraw, pAway));
			String pick = (maxRes == pHome) ? "MS1" : (maxRes == pDraw ? "MSX" : "MS2");
			double confidence = Math.round(maxRes * 100.0) / 100.0;

			return new PredictionResult(name(), match.getHomeTeam(), match.getAwayTeam(), safeProb(pHome),
					safeProb(pDraw), safeProb(pAway), safeProb(pOver25), safeProb(pBttsYes), pick, confidence, score);

		} catch (Exception e) {
			System.out.println("SimpleHeuristicModel hata: " + e.getMessage());
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
