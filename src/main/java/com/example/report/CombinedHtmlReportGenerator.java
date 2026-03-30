package com.example.report;

import com.example.MatchHistoryManager;
import com.example.model.LastPrediction;
import com.example.model.Match;
import com.example.model.MatchInfo;
import com.example.model.PredictionData;
import com.example.model.PredictionResult;
import com.example.model.RealScores;
import com.example.model.TeamMatchHistory;
import com.example.util.MathUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

public class CombinedHtmlReportGenerator {

	public static void generateCombinedHtml(List<LastPrediction> sublistPredictions, List<MatchInfo> matches,
			MatchHistoryManager historyManager, List<Match> matchStats,
			List<PredictionResult> results, List<PredictionData> sublistPredictionData, String fileName, String day,
			List<RealScores> realScores) {

		ZoneId istanbulZone = ZoneId.of("Europe/Istanbul");

		List<TeamMatchHistory> histories = historyManager != null ? historyManager.getTeamHistories() : List.of();

		StringBuilder html = new StringBuilder();
		html.append("<!DOCTYPE html><html lang='tr'><head><meta charset='UTF-8'>");
		html.append("<meta name='viewport' content='width=device-width, initial-scale=1.0'>");
		html.append("<title>⚽ Futbol Tahminleri + 💰 Hazır Kupon</title>");

		html.append("<style>");
		html.append("body{font-family:'Segoe UI',Roboto,Arial,sans-serif;margin:0;padding:0;")
				.append("background:#f3f6fa;color:#222;}");
		html.append(".page{max-width:1200px;margin:0 auto;padding:16px 10px;}");
		html.append(".day-title{text-align:center;color:#004d80;margin:12px 0 16px;font-size:24px;font-weight:700;}");
		html.append(".section{margin:20px 0;}");
		html.append(".section-title{text-align:center;color:#333;font-size:22px;margin-bottom:8px;}");
		html.append(".divider{border:none;border-top:3px solid #0077cc;margin:24px 0;}");

		html.append(".table-wrapper{width:100%;overflow-x:auto;-webkit-overflow-scrolling:touch;margin-top:10px;}");
		html.append("table{width:100%;border-collapse:collapse;background:#fff;border-radius:8px;")
				.append("box-shadow:0 2px 8px rgba(0,0,0,0.1);min-width:650px;}");
		html.append("th,td{padding:10px 12px;text-align:center;border-bottom:1px solid #ddd;font-size:0.9rem;}");
		html.append("th{background:#0077cc;color:white;font-size:0.95rem;}");
		html.append("tr:nth-child(even){background:#f3f6fa;}");
		html.append("tr:hover{background:#eaf3ff;}");
		html.append(
				".match-mbs{font-weight:bold;border-radius:6px;padding:3px 8px;font-size:0.85em;min-width:40px;display:inline-block;}");
		html.append(".match-mbs-1{background:#dc3545;color:#fff;}");
		html.append(".match-mbs-2{background:#fd7e14;color:#fff;}");
		html.append(".match-mbs-3{background:#28a745;color:#fff;}");
		html.append(".status-icon{font-size:1.2em;}");
		html.append(".won{color:#28a745;}");
		html.append(".lost{color:#dc3545;}");
		html.append(".pending{color:#999;}");

		html.append(".match{background:#fff;border:1px solid #dce3ec;margin:18px 0;padding:18px;")
				.append("border-radius:12px;box-shadow:0 2px 8px rgba(0,0,0,0.08);")
				.append("transition:transform 0.2s,box-shadow 0.2s;}");
		html.append(".match:hover{transform:translateY(-3px);box-shadow:0 4px 12px rgba(0,0,0,0.12);}");
		html.append(".match.insufficient{background-color:#fff1f1;border-left:4px solid #dc3545;}");

		html.append(".match-header{display:flex;justify-content:space-between;align-items:center;")
				.append("flex-wrap:wrap;margin-bottom:10px;}");
		html.append(".match-info{display:flex;align-items:center;gap:10px;flex-wrap:wrap;}");
		html.append(".match-separator{color:#aaa;margin:0 5px;}");
		html.append(".match-name{font-weight:700;color:#003366;font-size:1.5em;line-height:1.2;}");
		html.append(".match-time{color:#004d80;font-size:1.3em;font-weight:600;}");
		html.append(
				".match-mbs-box{font-weight:bold;border-radius:6px;padding:3px 7px;font-size:0.85em;min-width:55px;text-align:center;}");

		html.append(".odds-mini{background:#f9fbfd;border:1px solid #dbe2ea;border-radius:10px;")
				.append("padding:10px 14px;margin:12px 0;font-size:0.9em;box-shadow:0 1px 3px rgba(0,0,0,0.05);}");
		html.append(".odds-mini h4{margin:0 0 6px 0;color:#004d80;font-size:0.95em;font-weight:600;}");
		html.append(
				".odds-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(100px,1fr));gap:6px;text-align:center;}");
		html.append(".odds-cell{border:1px solid #e0e6ec;border-radius:6px;padding:6px 4px;background:#fff;}");
		html.append(".odds-label{font-weight:700;color:#004080;}");
		html.append(
				".odds-line{display:flex;justify-content:center;align-items:center;gap:4px;font-weight:600;color:#222;}");
		html.append(".odds-value{color:#000;font-weight:700;}");
		html.append(".odds-pct{display:block;color:#777;margin-top:2px;}");

		html.append(".quick-summary{margin-top:10px;overflow-x:auto;-webkit-overflow-scrolling:touch;}");
		html.append(
				".quick-summary table.qs{width:100%;border-collapse:collapse;background:#fff;border:1px solid #ccd6e0;")
				.append("border-radius:8px;overflow:hidden;min-width:650px;}");
		html.append(".quick-summary th,.quick-summary td{padding:8px;text-align:center;border:1px solid #e1e7ef;}");
		html.append(".quick-summary th{background:#f0f5fb;color:#003366;font-weight:600;font-size:0.9rem;}");
		html.append(".quick-summary tr:nth-child(even){background:#f9fbfd;}");
		html.append(".qs-odd{font-variant-numeric:tabular-nums;color:#333;font-size:0.9rem;}");
		html.append(
				".qs-pick .pick{display:inline-block;padding:3px 10px;border-radius:12px;background:#e7f1ff;color:#004d80;font-weight:700;font-size:0.9rem;}");
		html.append(".qs-score{color:#111;font-weight:600;}");

		html.append(".team-stats{background:#e3f2fd;color:#0c5460;padding:10px 12px;border-radius:6px;")
				.append("margin:8px 0;font-size:0.9em;}");
		html.append(".stats{background:#fff;border:1px solid #dbe2ea;padding:18px;margin:20px 0;")
				.append("border-radius:10px;box-shadow:0 1px 4px rgba(0,0,0,0.05);}");
		html.append(".stats h3{color:#004d80;margin-top:0;font-size:1.1rem;}");

		html.append(".no-data{color:#999;font-style:italic;padding:20px;text-align:center;")
				.append("background:#fff;border:1px dashed #ccc;border-radius:8px;}");

		html.append("@media(max-width:600px){");
		html.append("body{padding:0;}");
		html.append(".page{padding:12px 8px;}");
		html.append(".match{padding:12px;}");
		html.append(".match-header{flex-direction:column;align-items:flex-start;gap:6px;}");
		html.append(".match-name{font-size:1.2em;}");
		html.append(".match-time{font-size:1.1em;}");
		html.append(".table-wrapper table, .quick-summary table.qs{min-width:100%;}");
		html.append("}");
		html.append("</style>");
		html.append("</head><body>");
		html.append("<div class='page'>");

		html.append("<div class='day-title'>").append(day).append("</div>");

		html.append("<section id='coupon' class='section'>");
		html.append("<h2 class='section-title'>💰 Futbol Kuponu</h2>");
		html.append("<p style='text-align:center;color:#555;'>Sistemin oluşturduğu öneri kuponu</p>");

		html.append("<div class='table-wrapper'>");
		html.append("<table><thead><tr>");
		html.append("<th>🕒 Saat</th><th>MBS</th><th>⚽ Maç</th><th>🎯 Tahmin</th>");
		html.append("<th>📊 Skor Tahmini</th><th>📈 Gerçek Skor</th><th>Durum</th>");
		html.append("</tr></thead><tbody>");

		for (int i = 0; i < sublistPredictions.size(); i++) {
			LastPrediction p = sublistPredictions.get(i);
			PredictionData d = (sublistPredictionData != null && i < sublistPredictionData.size())
					? sublistPredictionData.get(i)
					: null;

			String mbsClass = "match-mbs-" + p.getMbs();
			String actualScore = (d != null && d.getScore() != null) ? d.getScore() : "-";

			StringBuilder statusIcons = new StringBuilder();
			if (d != null && p.getPredictions() != null) {
				for (String pick : p.getPredictions()) {
					String st = d.getStatuses() != null ? d.getStatuses().getOrDefault(pick, "pending") : "pending";
					switch (st) {
						case "won" -> statusIcons.append("<span class='status-icon won'>✅</span>");
						case "lost" -> statusIcons.append("<span class='status-icon lost'>❌</span>");
						default -> statusIcons.append("<span class='status-icon pending'>⏳</span>");
					}
				}
			} else {
				statusIcons.append("<span class='status-icon pending'>⏳</span>");
			}

			html.append("<tr>");
			html.append("<td>").append(p.getTime()).append("</td>");
			html.append("<td><span class='match-mbs ").append(mbsClass).append("'>").append(p.getMbs())
					.append("</span></td>");
			html.append("<td>").append(p.getName()).append("</td>");
			html.append("<td>").append(p.preditionsToString()).append("</td>");
			html.append("<td>").append(p.getScore() != null ? p.getScore() : "-").append("</td>");
			html.append("<td>").append(actualScore).append("</td>");
			html.append("<td>").append(statusIcons).append("</td>");
			html.append("</tr>");
		}

		html.append("</tbody></table>");
		html.append("</div>");

		int won = 0;
		int lost = 0;
		int pending = 0;

		for (int i = 0; i < sublistPredictions.size(); i++) {
			PredictionData d = (sublistPredictionData != null && i < sublistPredictionData.size())
					? sublistPredictionData.get(i)
					: null;

			if (d != null && d.getStatuses() != null) {
				for (String pick : sublistPredictions.get(i).getPredictions()) {
					String st = d.getStatuses().getOrDefault(pick, "pending");
					if (st.equals("won")) won++;
					else if (st.equals("lost")) lost++;
					else pending++;
				}
			} else {
				pending++;
			}
		}

		double winRate = (won + lost) > 0 ? (won * 100.0 / (won + lost)) : 0.0;

		html.append("<div style='margin-top:12px; text-align:center;'>");
		html.append("<div style='display:inline-block; background:#fff; padding:12px 18px; border-radius:10px;");
		html.append("box-shadow:0 2px 8px rgba(0,0,0,0.10); border-left:5px solid #0077cc;'>");
		html.append("<p style='margin:0; font-size:1rem; color:#004d80; font-weight:700;'>Kupon Kazanma Yüzdesi</p>");
		html.append("<p style='margin:4px 0 0 0; font-size:0.95rem; color:#333;'>");
		html.append("Kazanan Tahmin: ").append(won);
		html.append(" • Kaybeden: ").append(lost);
		html.append(" • Bekleyen: ").append(pending);
		html.append("</p>");
		html.append("<p style='margin:6px 0 0 0; font-size:1.1rem; font-weight:700; color:#0077cc;'>");
		html.append(String.format("%.1f%%", winRate)).append("</p>");
		html.append("</div>");
		html.append("</div>");
		html.append("</section>");

		html.append("<hr class='divider'>");

		html.append("<section id='detail' class='section'>");
		html.append("<h2 class='section-title'>⚽ Futbol Tahminleri</h2>");
		html.append("<p style='text-align:center;color:#555;margin-bottom:16px;'>Son güncelleme: ")
				.append(LocalDateTime.now(istanbulZone)).append("</p>");

		int detailUrlCount = 0;
		int processedTeamCount = 0;

		for (MatchInfo match : matches) {
			if (match.hasDetailUrl()) {
				detailUrlCount++;
			}
		}

		html.append("<div class='stats'>");
		html.append("<h3>İstatistikler</h3>");
		html.append("<p>- Toplam maç: ").append(matches.size()).append("</p>");
		html.append("<p>- Detay URL'si olan: ").append(detailUrlCount).append("</p>");
		html.append("<p>- History list boyutu: ").append(histories.size()).append("</p>");
		html.append("<p>- Results boyutu: ").append(results != null ? results.size() : 0).append("</p>");
		html.append("</div>");

		for (int i = 0; i < matches.size(); i++) {
			MatchInfo match = matches.get(i);
			TeamMatchHistory teamHistory = i < histories.size() ? histories.get(i) : null;
			PredictionResult result = (results != null && i < results.size()) ? results.get(i) : null;

			String[] teams = splitMatchName(match.getName());
			String homeStr = teams[0];
			String awayStr = teams[1];

			boolean insufficient = (teamHistory != null && !teamHistory.isInfoEnough()
					&& !teamHistory.isInfoEnoughWithoutRekabet());

			String mbsClassBox = "match-mbs-" + match.getOdds().getMbs();

			html.append("<div class='match").append(insufficient ? " insufficient" : "").append("'>");

			html.append("<div class='match-header'>");
			html.append("<div class='match-info'>");
			html.append("<span class='match-time'>").append(match.getTime()).append("</span>");
			html.append("<span class='match-separator'>•</span>");
			html.append("<span class='match-name'>").append(match.getName())
					.append(getRealScore(realScores, homeStr, awayStr)).append("</span>");
			html.append("<span class='match-separator'>•</span>");
			html.append("<span class='match-mbs-box ").append(mbsClassBox).append("'>").append(match.getOdds().getMbs())
					.append("</span>");
			html.append("</div>");
			html.append("</div>");

			if (teamHistory != null && teamHistory.getTotalMatches() > 0) {
				html.append("<div class='odds-mini'>");
				html.append("<h4>Güncel Oranlar ve Yüzdeler</h4>");
				html.append("<div class='odds-grid'>");

				html.append("<div class='odds-cell' style='")
						.append(teamHistory.getStyle("MS1", match.getOdds().getMs1())).append("'>")
						.append("<div class='odds-line'><span class='odds-label'>MS1:</span>")
						.append("<span class='odds-value'>").append(match.getOdds().getMs1()).append("</span></div>")
						.append("<span class='odds-pct'>").append(MathUtils.fmtPct(teamHistory.getMs1()))
						.append("</span>").append("</div>");

				html.append("<div class='odds-cell' style='")
						.append(teamHistory.getStyle("MSX", match.getOdds().getMsX())).append("'>")
						.append("<div class='odds-line'><span class='odds-label'>MSX:</span>")
						.append("<span class='odds-value'>").append(match.getOdds().getMsX()).append("</span></div>")
						.append("<span class='odds-pct'>").append(MathUtils.fmtPct(teamHistory.getMs0()))
						.append("</span>").append("</div>");

				html.append("<div class='odds-cell' style='")
						.append(teamHistory.getStyle("MS2", match.getOdds().getMs2())).append("'>")
						.append("<div class='odds-line'><span class='odds-label'>MS2:</span>")
						.append("<span class='odds-value'>").append(match.getOdds().getMs2()).append("</span></div>")
						.append("<span class='odds-pct'>").append(MathUtils.fmtPct(teamHistory.getMs2()))
						.append("</span>").append("</div>");

				html.append("</div>");
				html.append("<div class='odds-grid' style='margin-top:8px;'>");

				html.append("<div class='odds-cell' style='")
						.append(teamHistory.getStyle("Alt", match.getOdds().getUnder25())).append("'>")
						.append("<div class='odds-line'><span class='odds-label'>Alt:</span>")
						.append("<span class='odds-value'>").append(match.getOdds().getUnder25())
						.append("</span></div>").append("<span class='odds-pct'>")
						.append(MathUtils.fmtPct(teamHistory.getAlt())).append("</span>").append("</div>");

				html.append("<div class='odds-cell' style='")
						.append(teamHistory.getStyle("Üst", match.getOdds().getOver25())).append("'>")
						.append("<div class='odds-line'><span class='odds-label'>Üst:</span>")
						.append("<span class='odds-value'>").append(match.getOdds().getOver25()).append("</span></div>")
						.append("<span class='odds-pct'>").append(MathUtils.fmtPct(teamHistory.getUst()))
						.append("</span>").append("</div>");

				html.append("<div class='odds-cell' style='")
						.append(teamHistory.getStyle("Var", match.getOdds().getBttsYes())).append("'>")
						.append("<div class='odds-line'><span class='odds-label'>KG Var:</span>")
						.append("<span class='odds-value'>").append(match.getOdds().getBttsYes())
						.append("</span></div>").append("<span class='odds-pct'>")
						.append(MathUtils.fmtPct(teamHistory.getVar())).append("</span>").append("</div>");

				html.append("<div class='odds-cell' style='")
						.append(teamHistory.getStyle("Yok", match.getOdds().getBttsNo())).append("'>")
						.append("<div class='odds-line'><span class='odds-label'>KG Yok:</span>")
						.append("<span class='odds-value'>").append(match.getOdds().getBttsNo()).append("</span></div>")
						.append("<span class='odds-pct'>").append(MathUtils.fmtPct(teamHistory.getYok()))
						.append("</span>").append("</div>");

				html.append("</div>");
				html.append("</div>");

				int rekabetMacCount = teamHistory.getRekabetGecmisi().size();
				int sonMaclarHomeCount = teamHistory.getSonMaclarHome().size();
				int sonMaclarAwayCount = teamHistory.getSonMaclarAway().size();

				if (sonMaclarHomeCount > 0 && sonMaclarAwayCount > 0 && result != null) {
					html.append("<div class='quick-summary'>");
					html.append("<table class='qs'><thead><tr>");
					html.append("<th>MS1</th>");
					html.append("<th>MSX</th>");
					html.append("<th>MS2</th>");
					html.append("<th>Üst 2.5</th>");
					html.append("<th>KG Var</th>");
					html.append("<th>Seçim</th>");
					html.append("<th>Skor</th>");
					html.append("</tr></thead><tbody><tr>");

					html.append("<td class='qs-odd'>").append(MathUtils.fmtPct(result.getpHome()))
							.append("</td>");
					html.append("<td class='qs-odd'>").append(MathUtils.fmtPct(result.getpDraw()))
							.append("</td>");
					html.append("<td class='qs-odd'>").append(MathUtils.fmtPct(result.getpAway()))
							.append("</td>");
					html.append("<td class='qs-odd'>").append(MathUtils.fmtPct(result.getpOver25()))
							.append("</td>");
					html.append("<td class='qs-odd'>").append(MathUtils.fmtPct(result.getpBttsYes()))
							.append("</td>");
					html.append("<td class='qs-pick'><span class='pick'>").append(result.getPick())
							.append("</span></td>");
					html.append("<td class='qs-score'>").append(result.getScoreline()).append("</td>");

					html.append("</tr></tbody></table></div>");
				}

				html.append("<div class='team-stats'>");
				html.append("<p style='margin-top:8px;'>");
				html.append("Bakılan maç sayısı: Rekabet - ").append(rekabetMacCount)
						.append(" | Ev sahibi son maçlar - ").append(sonMaclarHomeCount)
						.append(" | Deplasman son maçlar - ").append(sonMaclarAwayCount);
				html.append("</p>");
				html.append("</div>");

				processedTeamCount++;
			} else {
				html.append("<div class='no-data'>Bu maç için geçmiş veri bulunamadı</div>");
			}

			html.append("</div>");
		}

		html.append("<div class='stats'>");
		html.append("<h3>Final İstatistikleri</h3>");
		html.append("<p>- Toplam maç: ").append(matches.size()).append("</p>");
		html.append("<p>- Detay URL'si olan: ").append(detailUrlCount).append("</p>");
		html.append("<p>- Başarıyla geçmişi çekilen: ").append(processedTeamCount).append("</p>");
		html.append("<p>- Toplam history kaydı: ").append(histories.size()).append("</p>");
		html.append("<p>- Başarı oranı: ").append(
				detailUrlCount > 0 ? String.format("%.1f%%", (processedTeamCount * 100.0 / detailUrlCount)) : "0%")
				.append("</p>");
		html.append("</div>");

		html.append("<p style='text-align:center;color:#666;margin-top:24px;'>");
		html.append("Bu veriler otomatik olarak çekilmiştir - Son güncelleme: ")
				.append(LocalDateTime.now(istanbulZone));
		html.append("</p>");

		html.append("</section>");
		html.append("</div>");
		html.append("</body></html>");

		File dir = new File("public/futbol");
		if (!dir.exists()) {
			dir.mkdirs();
		}
		File output = new File(dir, fileName);

		try (FileWriter fw = new FileWriter(output)) {
			fw.write(html.toString());
		} catch (IOException e) {
			e.printStackTrace();
		}

		System.out.println("✅ Birleşik futbol HTML üretildi: " + output.getAbsolutePath());
	}

	private static String[] splitMatchName(String name) {
		if (name == null || name.isBlank()) {
			return new String[]{"-", "-"};
		}
		String[] parts = name.split(" - ", 2);
		if (parts.length < 2) {
			return new String[]{name, "-"};
		}
		return new String[]{parts[0], parts[1]};
	}

	private static String getRealScore(List<RealScores> rsList, String home, String away) {
		String score = " (⏳)";
		int count = 0;

		if (rsList != null) {
			for (RealScores rs : rsList) {
				if (home.equals(rs.getHomeTeam()) && away.equals(rs.getAwayTeam())) {
					score = " (" + rs.getScore() + ")";
					count = 1;
					break;
				}
				if (home.equals(rs.getHomeTeam()) || away.equals(rs.getAwayTeam())) {
					score = " (" + rs.getScore() + ")";
					count++;
				}
			}
			if (count != 1)
				score = " (⏳)";
		}
		return score;
	}
}