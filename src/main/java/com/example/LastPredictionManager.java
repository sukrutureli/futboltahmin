package com.example;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import com.example.model.LastPrediction;
import com.example.model.MatchInfo;
import com.example.model.PredictionData;
import com.example.model.PredictionResult;
import com.example.model.TeamMatchHistory;
import com.example.util.MathUtils;

public class LastPredictionManager {
	private static final double MIN_MS_CONFIDENCE = 0.65;
	private static final double MIN_OVER_CONFIDENCE = 0.65;
	private static final double MIN_BTTS_CONFIDENCE = 0.65;

	private List<LastPrediction> lastPrediction;
	private MatchHistoryManager historyManager;
	private List<PredictionResult> predictionResults;
	private List<MatchInfo> matchInfo;
	private List<PredictionData> predictionData;

	public LastPredictionManager(MatchHistoryManager historyManager, List<PredictionResult> predictionResults,
			List<MatchInfo> matchInfo) {
		this.lastPrediction = new ArrayList<LastPrediction>();
		this.historyManager = historyManager;
		this.predictionResults = predictionResults;
		this.matchInfo = matchInfo;
		this.predictionData = new ArrayList<PredictionData>();
	}

	public void fillPredictions() {
		for (int i = 0; i < historyManager.getTeamHistories().size(); i++) {
			TeamMatchHistory th = historyManager.getTeamHistories().get(i);
			PredictionResult predictionResult = predictionResults.get(i);
			MatchInfo currentMatchInfo = matchInfo.get(i);

			LastPrediction tempLastPrediction = new LastPrediction(currentMatchInfo.getName(),
					currentMatchInfo.getTime());

			tempLastPrediction.setScore(predictionResult.getScoreline());
			tempLastPrediction.setMbs(currentMatchInfo.getOdds().getMbs());

			List<String> candidates = new ArrayList<>();

			if (predictionResult.getpHome() >= MIN_MS_CONFIDENCE
					&& predictionResult.getpHome() > predictionResult.getpAway()
					&& predictionResult.getpHome() > predictionResult.getpDraw()) {
				candidates.add("MS1");
			} else if (predictionResult.getpAway() >= MIN_MS_CONFIDENCE
					&& predictionResult.getpAway() > predictionResult.getpHome()
					&& predictionResult.getpAway() > predictionResult.getpDraw()) {
				candidates.add("MS2");
			}

			if (predictionResult.getpOver25() >= MIN_OVER_CONFIDENCE) {
				candidates.add("Üst");
			} else if (predictionResult.getpOver25() < 0.5) {
				candidates.add("Alt");
			}

			if (predictionResult.getpBttsYes() >= MIN_BTTS_CONFIDENCE) {
				candidates.add("Var");
			}

			for (String candidate : candidates) {
				if (calculatePrediction(th, predictionResult, currentMatchInfo, candidate) != null) {
					String withOdd = candidate + getOddsAndPercentage(candidate, currentMatchInfo, predictionResult);
					tempLastPrediction.getPredictions().add(withOdd);
				}
			}

			if (!tempLastPrediction.getPredictions().isEmpty()) {
				lastPrediction.add(tempLastPrediction);

				String homeTeam = tempLastPrediction.getName().split("-")[0].trim();
				String awayTeam = tempLastPrediction.getName().split("-")[1].trim();
				PredictionData tempPredictionData = new PredictionData(homeTeam, awayTeam,
						tempLastPrediction.getPredictions());
				predictionData.add(tempPredictionData);
				for (String prediction : tempLastPrediction.getPredictions()) {
					predictionData.get(predictionData.size() - 1).getStatuses().put(prediction, "pending");
				}
			}
		}
	}

	private String calculatePrediction(TeamMatchHistory h, PredictionResult pr, MatchInfo matchInfo, String tahmin) {
		if (!h.isInfoEnough() && !h.isInfoEnoughWithoutRekabet()) {
			return null;
		}

		if (tahmin.equals("MS1")) {
			if (matchInfo.getOdds().getMs1() > 1.29 && matchInfo.getOdds().getMs1() < 1.9 && h.getMax().equals(tahmin)
					&& isScoreOk(pr.getScoreline(), tahmin)) {
				if (pr.getpHome() > pr.getpAway() && pr.getpHome() > pr.getpDraw()) {
					return tahmin;
				}
			}
		} else if (tahmin.equals("MS2")) {
			if (matchInfo.getOdds().getMs2() > 1.29 && matchInfo.getOdds().getMs2() < 1.9 && h.getMax().equals(tahmin)
					&& isScoreOk(pr.getScoreline(), tahmin)) {
				if (pr.getpHome() < pr.getpAway() && pr.getpAway() > pr.getpDraw()) {
					return tahmin;
				}
			}
		} else if (tahmin.equals("Üst")) {
			if (matchInfo.getOdds().getOver25() > 1.29 && matchInfo.getOdds().getOver25() < 1.6
					&& h.getMax().equals(tahmin) && isScoreOk(pr.getScoreline(), tahmin)) {
				if (pr.getpOver25() > 0.5) {
					return tahmin;
				}
			}
		} else if (tahmin.equals("Alt")) {
			if (matchInfo.getOdds().getUnder25() > 1.29 && matchInfo.getOdds().getUnder25() < 1.6
					&& h.getMax().equals(tahmin) && isScoreOk(pr.getScoreline(), tahmin)) {
				if (pr.getpOver25() < 0.5) {
					return tahmin;
				}
			}
		} else if (tahmin.equals("Var")) {
			if (matchInfo.getOdds().getBttsYes() > 1.29 && matchInfo.getOdds().getBttsYes() < 1.6
					&& h.getMax().equals(tahmin) && isScoreOk(pr.getScoreline(), tahmin)
					&& matchInfo.getOdds().getMs1() > 1.69 && matchInfo.getOdds().getMs2() > 1.69) {
				if (pr.getpBttsYes() > 0.5) {
					return tahmin;
				}
			}
		} else if (tahmin.equals("Yok")) {
			if (matchInfo.getOdds().getBttsNo() > 1.29 && matchInfo.getOdds().getBttsNo() < 1.6
					&& h.getMax().equals(tahmin) && isScoreOk(pr.getScoreline(), tahmin)) {
				if (pr.getpBttsYes() < 0.5) {
					return tahmin;
				}
			}
		}

		return null;
	}

	private boolean isScoreOk(String score, String tahmin) {
		String[] splitScore = score.split("-");
		int home = Integer.valueOf(splitScore[0].trim());
		int away = Integer.valueOf(splitScore[1].trim());

		if (tahmin.equals("MS1") && home > away) {
			return true;
		} else if (tahmin.equals("MS2") && home < away) {
			return true;
		} else if (tahmin.equals("Alt") && (home + away) < 3) {
			return true;
		} else if (tahmin.equals("Üst") && (home + away) > 2) {
			return true;
		} else if (tahmin.equals("Var") && home > 0 && away > 0) {
			return true;
		} else if (tahmin.equals("Yok") && (home == 0 || away == 0)) {
			return true;
		} else {
			return false;
		}
	}

	private String getOddsAndPercentage(String tahmin, MatchInfo match, PredictionResult pr) {
		if (tahmin.equals("MS1")) {
			return String.format(" (%s - %s)", String.valueOf(match.getOdds().getMs1()),
					MathUtils.fmtPct(pr.getpHome()));
		} else if (tahmin.equals("MSX")) {
			return String.format(" (%s - %s)", String.valueOf(match.getOdds().getMsX()),
					MathUtils.fmtPct(pr.getpDraw()));
		} else if (tahmin.equals("MS2")) {
			return String.format(" (%s - %s)", String.valueOf(match.getOdds().getMs2()),
					MathUtils.fmtPct(pr.getpAway()));
		} else if (tahmin.equals("Alt")) {
			return String.format(" (%s - %s)", String.valueOf(match.getOdds().getUnder25()),
					MathUtils.fmtPct(1 - pr.getpOver25()));
		} else if (tahmin.equals("Üst")) {
			return String.format(" (%s - %s)", String.valueOf(match.getOdds().getOver25()),
					MathUtils.fmtPct(pr.getpOver25()));
		} else if (tahmin.equals("Var")) {
			return String.format(" (%s - %s)", String.valueOf(match.getOdds().getBttsYes()),
					MathUtils.fmtPct(pr.getpBttsYes()));
		} else if (tahmin.equals("Yok")) {
			return String.format(" (%s - %s)", String.valueOf(match.getOdds().getBttsNo()),
					MathUtils.fmtPct(1 - pr.getpBttsYes()));
		}

		return null;
	}

	public List<LastPrediction> getLastPrediction() {
		return lastPrediction;
	}

	public List<PredictionData> getPredictionData() {
		return predictionData;
	}
}
