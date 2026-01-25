package com.example.scraper;

import com.example.PageWaitUtils;
import com.example.model.MatchInfo;
import com.example.model.MatchResult;
import com.example.model.Odds;
import com.example.model.TeamMatchHistory;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class MatchScraper {

	private WebDriver driver;
	private JavascriptExecutor js;
	private WebDriverWait wait;

	public MatchScraper() {
		setupDriver();
	}

	// =============================================================
	// WEBDRIVER AYARI
	// =============================================================
	private void setupDriver() {
		ChromeOptions options = new ChromeOptions();
		options.addArguments("--headless=new", "--no-sandbox", "--disable-dev-shm-usage", "--disable-gpu",
				"--window-size=1920,1080", "--disable-blink-features=AutomationControlled",
				"user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119 Safari/537.36");
		driver = new ChromeDriver(options);
		js = (JavascriptExecutor) driver;
		wait = new WebDriverWait(driver, Duration.ofSeconds(20));
	}

	// =============================================================
	// GÜNLÜK MAÇLARI ÇEK (YENİ NESİNE)
	// =============================================================
	public List<MatchInfo> fetchMatches() {
		List<MatchInfo> list = new ArrayList<>();
		try {
			String date = LocalDate.now(ZoneId.of("Europe/Istanbul")).format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
			String url = "https://www.nesine.com/iddaa?et=1&le=1&dt=" + date;

			System.out.println("🔗 URL açılıyor: " + url);
			driver.manage().deleteAllCookies();
			driver.get(url);
			PageWaitUtils.safeWaitForLoad(driver, 25);

			wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("[data-test-id^='r_']")));
			List<Map<String, String>> rawData = scrollAndCollectMatchData();
			System.out.println("✅ Toplam benzersiz maç: " + rawData.size());

			int index = 0;
			for (Map<String, String> data : rawData) {
				try {
					String name = data.getOrDefault("name", "-");
					String href = data.getOrDefault("url", "-");
					String time = data.getOrDefault("time", "-");

					Odds odds = new Odds(toDouble(data.get("ms1")), toDouble(data.get("ms0")),
							toDouble(data.get("ms2")), toDouble(data.get("ust")), toDouble(data.get("alt")),
							toDouble(data.get("var")), toDouble(data.get("yok")),
							Integer.parseInt(data.getOrDefault("mbs", "-1")));

					list.add(new MatchInfo(name, time, href, odds, index++));
				} catch (Exception e) {
					System.out.println("⚠️ MatchInfo oluşturulamadı: " + e.getMessage());
				}
			}

		} catch (Exception e) {
			System.out.println("fetchMatches hata: " + e.getMessage());
		}
		return list;
	}

	// =============================================================
	// MAÇ SATIRLARINI YENİ YAPIYA GÖRE TOPLA
	// =============================================================
	private List<Map<String, String>> scrollAndCollectMatchData() throws InterruptedException {
		By eventSelector = By.cssSelector("div[data-test-id^='r_'][data-sport-id='1']");
		Set<String> seen = new HashSet<>();
		List<Map<String, String>> collected = new ArrayList<>();

		int stable = 0, prevCount = 0;
		int maxScroll = 80;
		int scrollAmount = 800;

		int waitTry = 0;
		while (driver.findElements(eventSelector).isEmpty() && waitTry < 20) {
			Thread.sleep(500);
			waitTry++;
		}
		System.out.println("⏳ Yeni yapı algılandı (" + waitTry + "sn sonra) - scroll başlıyor...");

		long startTime = System.currentTimeMillis();
		long maxWaitTime = 240000; // 4 dakika

		for (int i = 0; i < maxScroll; i++) {
			if (System.currentTimeMillis() - startTime > maxWaitTime)
				break;

			Thread.sleep(400);
			List<WebElement> matches = driver.findElements(eventSelector);

			for (WebElement el : matches) {
				try {
					WebElement nameEl = el.findElement(By.cssSelector("[data-test-id='matchName']"));
					String name = nameEl.getText().trim();
					if (name.isEmpty() || seen.contains(name))
						continue;
					seen.add(name);

					Map<String, String> map = new HashMap<>();
					map.put("name", name);
					map.put("url", nameEl.getAttribute("href"));

					// Saat
					try {
						String time = el.findElement(By.cssSelector("span[data-testid^='time']")).getText().trim();
						map.put("time", time);
					} catch (Exception ex) {
						map.put("time", "-");
					}

					// MBS
					try {
						WebElement mbsEl = el.findElement(By.cssSelector("[data-test-id='event_mbs'] span"));
						map.put("mbs", mbsEl.getText().trim());
					} catch (Exception ex) {
						map.put("mbs", "-1");
					}

					// --- Maç Sonucu (MS1, MS0, MS2)
					map.put("ms1", getOdd(el, "odd_Maç Sonucu_1"));
					map.put("ms0", getOdd(el, "odd_Maç Sonucu_X"));
					map.put("ms2", getOdd(el, "odd_Maç Sonucu_2"));

					// --- 2,5 Gol Alt / Üst
					map.put("alt", getOdd(el, "odd_2,5 Gol_Alt"));
					map.put("ust", getOdd(el, "odd_2,5 Gol_Üst"));

					// --- Karşılıklı Gol Var / Yok
					map.put("var", getOdd(el, "odd_Karş. Gol_Var"));
					map.put("yok", getOdd(el, "odd_Karş. Gol_Yok"));

					collected.add(map);
					System.out.println("✅ " + name + " (" + map.get("time") + ") eklendi.");

				} catch (Exception ignore) {
				}
			}

			if (seen.size() == prevCount) {
				stable++;
				System.out.println("  ⚠️ Stabilite sayacı: " + stable + "/3 (toplam: " + seen.size() + ")");
			} else {
				stable = 0;
				System.out.println("  ✓ Maç sayısı: " + seen.size() + " (+yeni " + (seen.size() - prevCount) + ")");
			}
			prevCount = seen.size();

			if (stable >= 10) {
				System.out.println("✅ Scroll tamamlandı (sabitliğe ulaşıldı)");
				break;
			}

			js.executeScript("window.scrollBy(0, " + scrollAmount + ");");
			Thread.sleep(800);
		}

		System.out.println("🧩 TOPLAM MAÇ: " + seen.size());
		return collected;
	}

	private String getOdd(WebElement matchEl, String testId) {
		try {
			return matchEl.findElement(By.cssSelector("button[data-testid='" + testId + "']")).getText().trim();
		} catch (Exception e) {
			return "-";
		}
	}

	private double toDouble(String s) {
		try {
			if (s == null || s.equals("-") || s.isEmpty())
				return 0.0;
			return Double.parseDouble(s.replace(",", "."));
		} catch (Exception e) {
			return 0.0;
		}
	}

	// =============================================================
	// GEÇMİŞ MAÇLAR (REKABET + SON MAÇLAR)
	// =============================================================
	public TeamMatchHistory scrapeTeamHistory(String detailUrl, String name) {
		if (detailUrl == null || !detailUrl.startsWith("http"))
			return null;

		String[] teams = extractTeamsFromHeader(detailUrl);
		String home = teams[0];
		String away = teams[1];
		String title = teams[2];

		TeamMatchHistory th = new TeamMatchHistory(title, home, away, detailUrl);
		try {
			String summaryUrl = detailUrl + "/ozet";
			driver.get(summaryUrl);
			PageWaitUtils.safeWaitForLoad(driver, 15);
			Thread.sleep(1000);

			// --- REKABET GEÇMİŞİ ---

			try {
				List<WebElement> rows = driver
						.findElements(By.cssSelector("div[data-test-id='CompitionHistoryTableItem']"));
				System.out.println("🔹 Rekabet geçmişi satır sayısı: " + rows.size());

				for (WebElement r : rows) {
					try {
						String date = safeText(r,
								"[data-test-id='CompitionTableItemSeason'], [data-test-id='TableBodyDate']");
						String league = safeText(r,
								"[data-test-id='CompitionTableItemLeague'], [data-test-id='TableBodyTournament']");
						String homeTeam = extractTeamName(
								r.findElement(By.cssSelector("div[data-test-id='HomeTeam']")));
						String awayTeam = extractTeamName(
								r.findElement(By.cssSelector("div[data-test-id='AwayTeam']")));
						String score = extractScore(r);
						int[] sc = parseScore(score);

						th.addRekabetGecmisiMatch(new MatchResult(homeTeam, awayTeam, sc[0], sc[1], date, league,
								"rekabet-gecmisi", summaryUrl));
					} catch (Exception ex) {
						System.out.println("⚠️ Rekabet satırı hatası: " + ex.getMessage());
					}
				}
			} catch (Exception e) {
				System.out.println("extractCompetitionHistoryResults hata: " + e.getMessage());
			}

			// --- SON MAÇLAR (Ev / Dep) ---
			try {
				List<WebElement> tables = driver.findElements(By.cssSelector("div[data-test-id^='LastMatchesTable']"));
				for (WebElement table : tables) {
					int currentSide = 0;
					try {
						WebElement titleEl = table
								.findElement(By.cssSelector("h3, [data-test-id='LastMatchesTableTitle']"));
						String titleText = titleEl.getText().toLowerCase(Locale.ROOT);
						if (titleText.contains("ev") || titleText.contains("home"))
							currentSide = 1;
						else if (titleText.contains("deplasman") || titleText.contains("away"))
							currentSide = 2;
					} catch (Exception e) {
						currentSide = (tables.indexOf(table) == 0) ? 1 : 2;
					}

					List<WebElement> rows = table.findElements(By.cssSelector("tbody tr"));
					for (WebElement r : rows) {
						try {
							// 🔹 Lig ve tarih aynı hücrede (ör: V-L / 31 Eki)
							String league = "-";
							String date = "-";
							try {
								WebElement leagueTd = r
										.findElement(By.cssSelector("td[data-test-id='TableBodyLeague']"));
								List<WebElement> spans = leagueTd.findElements(By.tagName("span"));
								if (spans.size() >= 1)
									league = spans.get(0).getText().trim();
								if (spans.size() >= 2)
									date = spans.get(1).getText().trim();
							} catch (Exception ignore) {
							}

							String homeTeam = extractTeamName(
									r.findElement(By.cssSelector("div[data-test-id='HomeTeam']")));
							String awayTeam = extractTeamName(
									r.findElement(By.cssSelector("div[data-test-id='AwayTeam']")));
							String score = extractScore(r);
							int[] sc = parseScore(score);

							th.addSonMacMatch(new MatchResult(homeTeam, awayTeam, sc[0], sc[1], date, league,
									"son-maclari", summaryUrl), currentSide);

						} catch (Exception ex) {
							System.out.println("⚠️ Satır hatası: " + ex.getMessage());
						}
					}
				}
			} catch (Exception e) {
				System.out.println("extractMatchResults hata: " + e.getMessage());
			}

			System.out.println("✅ " + title + " için geçmiş verisi: " + th.getRekabetGecmisi().size() + " rekabet, "
					+ th.getSonMaclarHome().size() + "+" + th.getSonMaclarAway().size() + " son maç");
		} catch (Exception e) {
			System.out.println("⚠️ Geçmiş verisi hatası: " + e.getMessage());
		}
		return th;
	}

	private String extractScore(WebElement row) {
		try {
			// 1) Öncelikle normal tablo skor alanlarını ara
			List<WebElement> direct = row
					.findElements(By.cssSelector("[data-test-id='Score'] span, td[data-test-id='Score']"));
			for (WebElement s : direct) {
				String t = s.getText().trim().replaceAll("\\(.*?\\)", "");
				if (t.matches("\\d+\\s*-\\s*\\d+"))
					return t;
			}

			// 2) Eğer bulunamadıysa, buton veya tooltip içindeki skorları ara
			List<WebElement> buttons = row.findElements(By.cssSelector("button[data-test-id='NsnButton'] span"));
			for (WebElement b : buttons) {
				String t = b.getText().trim().replaceAll("\\(.*?\\)", "");
				if (t.matches("\\d+\\s*-\\s*\\d+"))
					return t;
			}

			// 3) Alternatif: direkt <span> içinde "X-Y" formu
			List<WebElement> spans = row.findElements(By.cssSelector("span"));
			for (WebElement s : spans) {
				String t = s.getText().trim().replaceAll("\\(.*?\\)", "");
				if (t.matches("\\d+\\s*-\\s*\\d+"))
					return t;
			}
		} catch (Exception e) {
			// ignore
		}
		return "-";
	}

	private int[] parseScore(String s) {
		try {
			String[] p = s.split("-");
			return new int[] { Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()) };
		} catch (Exception e) {
			return new int[] { -1, -1 };
		}
	}

	private String[] extractTeamsFromHeader(String url) {
		String home = "-", away = "-", name = "";
		try {
			driver.get(url);
			PageWaitUtils.waitForPageLoad(driver, 12);
			wait.until(
					ExpectedConditions.visibilityOfElementLocated(By.cssSelector("div[data-test-id='HeaderTeams']")));

			WebElement header = driver.findElement(By.cssSelector("div[data-test-id='HeaderTeams']"));
			List<WebElement> teams = header
					.findElements(By.cssSelector("a[data-test-id='TeamLink'] span[data-test-id='HeaderTeams']"));

			if (teams.size() >= 2) {
				home = teams.get(0).getText().trim();
				away = teams.get(1).getText().trim();
			}
		} catch (Exception e) {
			System.out.println("Takım adları çekilemedi: " + e.getMessage());
		}
		name = home + " - " + away;
		return new String[] { home, away, name };
	}

	private String extractTeamName(WebElement el) {
		try {
			return el.getText().trim();
		} catch (Exception e) {
			return "-";
		}
	}

	private String safeText(WebElement parent, String css) {
		try {
			WebElement el = parent.findElement(By.cssSelector(css));
			String t = el.getText().trim();
			return t.isEmpty() ? "-" : t;
		} catch (Exception e) {
			return "-";
		}
	}

	public void close() {
		try {
			driver.quit();
		} catch (Exception ignore) {
		}
	}
}

