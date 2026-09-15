package com.example.scraper;

import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.example.model.MatchInfo;
import com.example.model.PredictionData;
import com.example.model.RealScores;

import java.time.*;
import java.util.*;

public class ControlScraper {

	private WebDriver driver;
	private WebDriverWait wait;
	private List<RealScores> results;

	public ControlScraper() {
		setupDriver();
		results = new ArrayList<RealScores>();
	}

	private void setupDriver() {
		System.setProperty("webdriver.chrome.driver", "/usr/bin/chromedriver");
		ChromeOptions options = new ChromeOptions();
		options.addArguments("--headless=new", "--no-sandbox", "--disable-dev-shm-usage", "--disable-gpu",
				"--window-size=1920,1080", "--disable-blink-features=AutomationControlled", "--disable-cache",
				"--incognito");
		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, Duration.ofSeconds(15));
	}

	// Tahmin verilen maçları kendi Nesine istatistik URL'lerinden kontrol eder.
	// /p1/{eventId} alternatif görünümünde ana skor alanı ve MS bilgisi birlikte bulunuyor.
	public Map<String, String> fetchFinishedScoresFromDetails(List<RealScores> rsList,
			List<MatchInfo> matches, List<PredictionData> predictions) {
		Map<String, String> scores = new HashMap<>();
		if (rsList != null && !rsList.isEmpty()) results.addAll(rsList);
		if (matches == null || predictions == null) return scores;

		for (PredictionData prediction : predictions) {
			String predictionName = prediction.getHomeTeam() + " - " + prediction.getAwayTeam();
			MatchInfo matchInfo = findMatchInfo(matches, predictionName);
			if (matchInfo == null || !matchInfo.hasDetailUrl()) {
				System.out.println("⚠️ Detail URL bulunamadı: " + predictionName);
				continue;
			}

			try {
				String url = toAlternativeDetailUrl(matchInfo.getDetailUrl());
				System.out.println("🔎 Skor kontrol: " + predictionName + " | " + url);
				driver.get(url);
				waitForPageLoad(driver, 15);

				WebElement scoreboard = wait.until(ExpectedConditions.presenceOfElementLocated(
						By.cssSelector(".broadage-score-container")));
				String scoreboardText = safeText(scoreboard, driver);

				// Skor canlıyken de görünebilir; yalnızca MS varsa kesin sonuç olarak kaydet.
				if (scoreboardText == null || !scoreboardText.matches("(?s).*\\bMS\\b.*")) {
					System.out.println("⏳ Maç henüz bitmemiş: " + predictionName + " | " + scoreboardText);
					continue;
				}

				String homeScore = safeText(scoreboard.findElement(By.cssSelector(".broadage-home-team-score")), driver);
				String awayScore = safeText(scoreboard.findElement(By.cssSelector(".broadage-away-team-score")), driver);
				if (!homeScore.matches("\\d+") || !awayScore.matches("\\d+")) {
					System.out.println("⚠️ Geçersiz skor: " + predictionName + " | " + homeScore + "-" + awayScore);
					continue;
				}

				String score = homeScore + "-" + awayScore;
				scores.put(predictionName, score);
				upsertRealScore(prediction.getHomeTeam(), prediction.getAwayTeam(), score);
				System.out.println("✅ DETAIL " + predictionName + " → " + score);
			} catch (TimeoutException e) {
				System.out.println("⚠️ Detail skor alanı bulunamadı: " + predictionName);
			} catch (Exception e) {
				System.out.println("⚠️ Detail skor hatası: " + predictionName + " | " + e.getMessage());
			}
		}

		System.out.println("⚽ Detail URL'den bitmiş tahmin maçı sayısı: " + scores.size());
		return scores;
	}

	private MatchInfo findMatchInfo(List<MatchInfo> matches, String predictionName) {
		for (MatchInfo match : matches) {
			if (match != null && match.getName() != null && match.getName().trim().equals(predictionName.trim())) {
				return match;
			}
		}
		return null;
	}

	private void upsertRealScore(String home, String away, String score) {
		for (RealScores rs : results) {
			if (Objects.equals(rs.getHomeTeam(), home) && Objects.equals(rs.getAwayTeam(), away)) {
				rs.setScore(score);
				return;
			}
		}
		RealScores rs = new RealScores();
		rs.setHomeTeam(home);
		rs.setAwayTeam(away);
		rs.setScore(score);
		results.add(rs);
	}

	private String toAlternativeDetailUrl(String detailUrl) {
		if (detailUrl == null || detailUrl.isBlank()) return detailUrl;
		if (detailUrl.contains("istatistik.nesine.com/p1/")) return detailUrl;
		return detailUrl.replace("istatistik.nesine.com/", "istatistik.nesine.com/p1/");
	}

	// Eski canlı skor yöntemi şimdilik fallback/test amacıyla korunuyor.
	public Map<String, String> fetchFinishedScores(List<RealScores> rsList) {
		Map<String, String> scores = new HashMap<>();
		if (rsList != null && !rsList.isEmpty()) results.addAll(rsList);
		try {
			String url = "https://www.nesine.com/iddaa/canli-skor/futbol";
			driver.get(url);
			waitForPageLoad(driver, 15);
			Thread.sleep(1500);
			clickYesterdayTabIfNeeded(driver);

			JavascriptExecutor js = (JavascriptExecutor) driver;
			for (int i = 0; i < 4; i++) {
				js.executeScript("window.scrollTo(0, document.body.scrollHeight);");
				Thread.sleep(1200);
			}

			String selector = "li[class*='match'], li[class*='extra-time']";
			wait.until(ExpectedConditions.presenceOfAllElementsLocatedBy(By.cssSelector(selector)));
			List<WebElement> matches = driver.findElements(By.cssSelector(selector));
			System.out.println("Toplam maç bulundu: " + matches.size());

			for (WebElement match : matches) {
				try {
					String cls = match.getAttribute("class");
					if (cls == null) continue;
					if (!(cls.contains("finished") || cls.contains("unlive") || cls.contains("not-play")
							|| cls.contains("extra-time"))) continue;

					WebElement board = match.findElement(By.cssSelector(".teams-score-content .board"));
					String home = safeText(match.findElement(By.cssSelector(".home-team span[aria-hidden='true']")), driver);
					String away = safeText(match.findElement(By.cssSelector(".away-team span[aria-hidden='true']")), driver);
					String homeScore = safeText(board.findElement(By.cssSelector(".home-score")), driver);
					String awayScore = safeText(board.findElement(By.cssSelector(".away-score")), driver);
					String score = homeScore + "-" + awayScore;
					upsertRealScore(home, away, score);
					scores.put(home + " - " + away, score);
					System.out.println("✅ " + home + " - " + away + " → " + score);
				} catch (Exception e) {
					System.out.println("⚠️ Tekil maç hatası: " + e.getMessage());
				}
			}
		} catch (Exception e) {
			System.out.println("fetchFinishedScores hata: " + e.getMessage());
		}
		return scores;
	}

	private void clickYesterdayTabIfNeeded(WebDriver driver) {
		try {
			WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));
			JavascriptExecutor js = (JavascriptExecutor) driver;
			wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(".live-result-menu")));
			Thread.sleep(1000);
			LocalTime now = LocalTime.now(ZoneId.of("Europe/Istanbul"));
			if (now.isAfter(LocalTime.MIDNIGHT) && now.isBefore(LocalTime.of(6, 0))) {
				List<WebElement> tabs = driver.findElements(By.xpath("//span[contains(@class,'menu-item') and contains(@class,'tab')]"));
				WebElement yesterdayTab = null;
				for (int i = 0; i < tabs.size(); i++) {
					if (tabs.get(i).getText().contains("Bugün") && i > 0) {
						yesterdayTab = tabs.get(i - 1);
						break;
					}
				}
				if (yesterdayTab != null) {
					js.executeScript("arguments[0].classList.remove('disabled');", yesterdayTab);
					js.executeScript("arguments[0].scrollIntoView({block:'center'});", yesterdayTab);
					Thread.sleep(1000);
					js.executeScript("arguments[0].click();", yesterdayTab);
					Thread.sleep(1500);
				}
			}
		} catch (Exception e) {
			System.out.println("⚠️ Dün sekmesine geçilemedi: " + e.getMessage());
		}
	}

	public void close() {
		try { driver.quit(); } catch (Exception ignore) {}
	}

	private String safeText(WebElement el, WebDriver driver) {
		try {
			String text = el.getAttribute("textContent");
			if (text == null || text.trim().isEmpty()) text = el.getText();
			return text == null ? "-" : text.trim();
		} catch (Exception e) {
			try {
				return ((JavascriptExecutor) driver)
						.executeScript("return arguments[0].innerText || arguments[0].textContent;", el).toString().trim();
			} catch (Exception inner) { return "-"; }
		}
	}

	public void waitForPageLoad(WebDriver driver, int timeoutSeconds) {
		new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds))
				.until(webDriver -> ((JavascriptExecutor) webDriver).executeScript("return document.readyState").equals("complete"));
	}

	public List<RealScores> getResults() {
		return results;
	}
}
