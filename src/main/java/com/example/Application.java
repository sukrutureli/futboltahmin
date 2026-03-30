package com.example;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.example.algo.BettingAlgorithm;
import com.example.algo.EnsembleModel;
import com.example.algo.FormMomentumModel;
import com.example.algo.PoissonGoalModel;
import com.example.algo.SimpleHeuristicModel;
import com.example.model.Match;
import com.example.model.MatchInfo;
import com.example.model.PredictionData;
import com.example.model.PredictionResult;
import com.example.model.RealScores;
import com.example.model.TeamMatchHistory;
import com.example.prediction.JsonReader;
import com.example.prediction.JsonStorage;
import com.example.prediction.PredictionUpdater;
import com.example.report.CombinedHtmlReportGenerator;
import com.example.scraper.ControlScraper;
import com.example.scraper.MatchScraper;

public class Application {

	public static void main(String[] args) throws IOException {
		ZoneId istanbulZone = ZoneId.of("Europe/Istanbul");

		String mode = args.length > 0 ? args[0].toLowerCase() : "futbol";
		System.out.println("Çalışma modu: " + mode.toUpperCase());

		switch (mode) {
			case "futbol":
				runFutbolPrediction();
				break;
			case "kontrol":
				runKontrol();
				break;
			default:
				System.out.println("⚠️ Geçersiz argüman: " + mode);
				System.out.println("Kullanım: java -jar prediction.jar [futbol | kontrol]");
				break;
		}

		System.out.println("\nTamamlandı: " + LocalDateTime.now(istanbulZone));
	}

	private static void runFutbolPrediction() {
		MatchScraper scraper = null;
		MatchHistoryManager historyManager = new MatchHistoryManager();
		List<MatchInfo> matches = null;
		List<Match> matchStats = new ArrayList<>();
		ZoneId istanbulZone = ZoneId.of("Europe/Istanbul");
		List<PredictionResult> results = new ArrayList<>();

		try {
			System.out.println("=== İddaa Scraper Başlatılıyor ===");
			System.out.println("Zaman: " + LocalDateTime.now(istanbulZone));

			scraper = new MatchScraper();

			System.out.println("\n1. Ana sayfa maçları çekiliyor...");
			matches = scraper.fetchMatches();

			System.out.println("Ana sayfadan " + matches.size() + " maç çekildi");

			for (int i = 0; i < matches.size(); i++) {
				MatchInfo match = matches.get(i);
				TeamMatchHistory teamHistory = null;

				if (match.hasDetailUrl()) {
					System.out.println("Geçmiş çekiliyor " + (i + 1) + "/" + matches.size() + ": " + match.getName());

					try {
						String url = match.getDetailUrl();
						if (url != null && url.startsWith("http")) {
							teamHistory = scraper.scrapeTeamHistory(match.getDetailUrl(), match.getName());
						} else {
							System.out.println("⚠️ Geçersiz URL: " + url);
						}

						Thread.sleep(1500);
						if ((i + 1) % 5 == 0) System.gc();

					} catch (Exception e) {
						System.out.println("Geçmiş çekme hatası: " + e.getMessage());
					}
				}

				if (teamHistory == null) {
					teamHistory = new TeamMatchHistory(match.getName(), "-", "-", match.getDetailUrl());
				}

				historyManager.addTeamHistory(teamHistory);

				try {
					matchStats.add(teamHistory.createMatch(match));
				} catch (Exception e) {
					System.out.println("⚠️ Match oluşturulamadı: " + match.getName() + " | " + e.getMessage());
				}

				if ((i + 1) % 20 == 0) {
					System.out.println("İşlendi: " + (i + 1) + "/" + matches.size());
				}
			}

			BettingAlgorithm poisson = new PoissonGoalModel();
			BettingAlgorithm heur = new SimpleHeuristicModel();
			BettingAlgorithm formMomentum = new FormMomentumModel();
			EnsembleModel ensemble = new EnsembleModel(List.of(poisson, heur, formMomentum));

			for (Match m : matchStats) {
				results.add(ensemble.predict(m, Optional.ofNullable(m.getOdds())));
			}

			System.out.println("MATCHES SIZE = " + matches.size());
			System.out.println("HISTORY SIZE = " + historyManager.getTeamHistories().size());
			System.out.println("MATCHSTATS SIZE = " + matchStats.size());
			System.out.println("RESULTS SIZE = " + results.size());

			LastPredictionManager lastPredictionManager = new LastPredictionManager(historyManager, results, matches);
			lastPredictionManager.fillPredictions();

			CombinedHtmlReportGenerator.generateCombinedHtml(
					lastPredictionManager.getLastPrediction(),
					matches,
					historyManager,
					matchStats,
					results,
					lastPredictionManager.getPredictionData(),
					"futbol.html",
					getStringDay(false),
					null
			);
			System.out.println("futbol.html oluşturuldu.");

			JsonStorage.save("futbol", "PredictionData", getStringDay(false), lastPredictionManager.getPredictionData());
			JsonStorage.save("futbol", "LastPrediction", getStringDay(false), lastPredictionManager.getLastPrediction());
			JsonStorage.save("futbol", "MatchInfo", getStringDay(false), matches);
			JsonStorage.save("futbol", "TeamMatchHistory", getStringDay(false), historyManager.getTeamHistories());
			JsonStorage.save("futbol", "Match", getStringDay(false), matchStats);
			JsonStorage.save("futbol", "PredictionResult", getStringDay(false), results);

		} catch (Exception e) {
			System.out.println("GENEL HATA: " + e.getMessage());
			e.printStackTrace();
		} finally {
			if (scraper != null) {
				scraper.close();
			}
		}
	}

	private static void runKontrol() throws IOException {
		ControlScraper scraper = null;
		MatchHistoryManager historyManager = new MatchHistoryManager();

		List<MatchInfo> matches = JsonReader.readFromGithub("futbol", "MatchInfo", JsonReader.getToday(), MatchInfo.class);
		List<Match> matchStats = JsonReader.readFromGithub("futbol", "Match", JsonReader.getToday(), Match.class);
		ZoneId istanbulZone = ZoneId.of("Europe/Istanbul");
		List<PredictionResult> results = JsonReader.readFromGithub("futbol", "PredictionResult", JsonReader.getToday(), PredictionResult.class);
		List<TeamMatchHistory> teamHistoryList = JsonReader.readFromGithub("futbol", "TeamMatchHistory", JsonReader.getToday(), TeamMatchHistory.class);
		List<RealScores> rsList = JsonReader.readFromGithub("futbol", "RealScores", JsonReader.getToday(), RealScores.class);

		try {
			System.out.println("Zaman: " + LocalDateTime.now(istanbulZone));
			scraper = new ControlScraper();

			Map<String, String> updatedScores = scraper.fetchFinishedScores(rsList);
			List<PredictionData> predictions = PredictionUpdater.updateFromGithub(updatedScores, "PredictionData-");

			for (int i = 0; i < matches.size(); i++) {
				if (i < teamHistoryList.size()) {
					historyManager.addTeamHistory(teamHistoryList.get(i));
				} else {
					MatchInfo match = matches.get(i);
					historyManager.addTeamHistory(new TeamMatchHistory(match.getName(), "-", "-", match.getDetailUrl()));
				}
			}

			LastPredictionManager lastPredictionManager = new LastPredictionManager(historyManager, results, matches);
			lastPredictionManager.fillPredictions();

			CombinedHtmlReportGenerator.generateCombinedHtml(
					lastPredictionManager.getLastPrediction(),
					matches,
					historyManager,
					matchStats,
					results,
					predictions,
					"futbol.html",
					getStringDay(true),
					scraper.getResults()
			);
			System.out.println("futbol.html oluşturuldu.");

			JsonStorage.save("futbol", "RealScores", JsonReader.getToday(), scraper.getResults());

		} catch (Exception e) {
			System.out.println("GENEL HATA: " + e.getMessage());
			e.printStackTrace();
		} finally {
			if (scraper != null) {
				scraper.close();
			}
		}
	}

	public static String getStringDay(boolean minusDay) {
		LocalTime now = LocalTime.now(ZoneId.of("Europe/Istanbul"));
		String day = LocalDate.now(ZoneId.of("Europe/Istanbul")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

		if (minusDay) {
			if (now.isAfter(LocalTime.MIDNIGHT) && now.isBefore(LocalTime.of(6, 0))) {
				day = LocalDate.now(ZoneId.of("Europe/Istanbul")).minusDays(1)
						.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
			} else {
				day = LocalDate.now(ZoneId.of("Europe/Istanbul")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
			}
		}

		return day;
	}
}