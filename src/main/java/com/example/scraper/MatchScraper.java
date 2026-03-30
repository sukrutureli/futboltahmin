package com.example.scraper;

import com.example.PageWaitUtils;
import com.example.model.MatchInfo;
import com.example.model.MatchResult;
import com.example.model.Odds;
import com.example.model.TeamMatchHistory;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.devtools.DevTools;
import org.openqa.selenium.devtools.HasDevTools;
import org.openqa.selenium.devtools.v118.network.Network;
import org.openqa.selenium.devtools.v118.network.model.RequestId;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

public class MatchScraper {

	private WebDriver driver;
	private JavascriptExecutor js;
	private WebDriverWait wait;

	// DevTools / Network
	private DevTools devTools;
	private final Map<String, String> capturedResponses = new ConcurrentHashMap<>();
	private final AtomicInteger responseCounter = new AtomicInteger(0);

	public MatchScraper() {
		setupDriver();
	}

	// =============================================================
	// WEBDRIVER AYARI
	// =============================================================
	private void setupDriver() {
		ChromeOptions options = new ChromeOptions();
		options.addArguments(
				"--headless=new",
				"--no-sandbox",
				"--disable-dev-shm-usage",
				"--disable-gpu",
				"--window-size=1920,1080",
				"--disable-blink-features=AutomationControlled",
				"user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119 Safari/537.36"
		);

		ChromeDriver chromeDriver = new ChromeDriver(options);
		driver = chromeDriver;
		js = (JavascriptExecutor) driver;
		wait = new WebDriverWait(driver, Duration.ofSeconds(20));

		try {
			devTools = ((HasDevTools) chromeDriver).getDevTools();
			devTools.createSession();
			devTools.send(Network.enable(Optional.empty(), Optional.empty(), Optional.empty()));
			System.out.println("✅ DevTools Network aktif");
		} catch (Exception e) {
			System.out.println("⚠️ DevTools başlatılamadı: " + e.getMessage());
		}
	}

	// =============================================================
	// NETWORK CAPTURE
	// =============================================================
	private void startRequestLogging() {
		if (devTools == null) {
			System.out.println("⚠️ DevTools yok, request log başlatılamadı");
			return;
		}

		devTools.addListener(Network.requestWillBeSent(), req -> {
			try {
				String url = req.getRequest().getUrl();
				String u = url.toLowerCase(Locale.ROOT);

				if (u.contains("iddaa")
						|| u.contains("match")
						|| u.contains("odd")
						|| u.contains("event")
						|| u.contains("program")
						|| u.contains("bet")
						|| u.contains("sports")
						|| u.contains("coupon")
						|| u.contains("graphql")) {
					System.out.println("➡️ Request: " + req.getRequest().getMethod() + " " + url);
				}
			} catch (Exception e) {
				System.out.println("⚠️ request log hata: " + e.getMessage());
			}
		});
	}

	private void startNetworkCapture() {
		if (devTools == null) {
			System.out.println("⚠️ DevTools yok, network capture başlatılamadı");
			return;
		}

		Predicate<String> interestingUrl = url -> {
			String u = url.toLowerCase(Locale.ROOT);
			return u.contains("iddaa")
					|| u.contains("event")
					|| u.contains("match")
					|| u.contains("odd")
					|| u.contains("program")
					|| u.contains("bet")
					|| u.contains("sports")
					|| u.contains("coupon")
					|| u.contains("graphql");
		};

		devTools.addListener(Network.responseReceived(), response -> {
			try {
				String url = response.getResponse().getUrl();
				int status = response.getResponse().getStatus().intValue();
				String mimeType = String.valueOf(response.getResponse().getMimeType());

				if (!interestingUrl.test(url)) {
					return;
				}

				System.out.println("🌐 Response: [" + status + "] " + url + " | mime=" + mimeType);

				boolean maybeUseful =
						mimeType.contains("json")
								|| mimeType.contains("javascript")
								|| mimeType.contains("text")
								|| url.toLowerCase(Locale.ROOT).contains("api")
								|| url.toLowerCase(Locale.ROOT).contains("graphql");

				if (!maybeUseful) {
					return;
				}

				RequestId requestId = response.getRequestId();

				try {
					Network.GetResponseBodyResponse bodyResponse =
							devTools.send(Network.getResponseBody(requestId));

					String body = bodyResponse.getBody();
					if (body == null || body.isBlank()) {
						return;
					}

					capturedResponses.put(url, body);

					int no = responseCounter.incrementAndGet();
					String shortBody = body.length() > 400 ? body.substring(0, 400) : body;

					System.out.println("📦 BODY #" + no + ": " + url);
					System.out.println(shortBody.replace("\n", " ").replace("\r", " "));
					saveCapturedResponse(no, url, body);

				} catch (Exception ex) {
					System.out.println("⚠️ Body alınamadı: " + url + " | " + ex.getMessage());
				}

			} catch (Exception e) {
				System.out.println("⚠️ response listener hata: " + e.getMessage());
			}
		});
	}

	private void saveCapturedResponse(int no, String url, String body) {
		try {
			Path dir = Path.of("debug-network");
			Files.createDirectories(dir);

			String safeName = url
					.replace("https://", "")
					.replace("http://", "")
					.replaceAll("[^a-zA-Z0-9._-]", "_");

			if (safeName.length() > 120) {
				safeName = safeName.substring(0, 120);
			}

			Path file = dir.resolve(String.format("%03d_%s.txt", no, safeName));
			String content = "URL:\n" + url + "\n\nBODY:\n" + body;
			Files.writeString(file, content, StandardCharsets.UTF_8);

			System.out.println("💾 Network response kaydedildi: " + file.toAbsolutePath());
		} catch (IOException e) {
			System.out.println("⚠️ Response dosyaya yazılamadı: " + e.getMessage());
		}
	}

	private void dumpInterestingCapturedResponses() {
		System.out.println("=========== CAPTURED RESPONSES ===========");
		System.out.println("Toplam yakalanan response sayısı: " + capturedResponses.size());

		capturedResponses.forEach((url, body) -> {
			String preview = body == null ? "" : body.substring(0, Math.min(body.length(), 250))
					.replace("\n", " ")
					.replace("\r", " ");
			System.out.println("URL: " + url);
			System.out.println("PREVIEW: " + preview);
			System.out.println("-----------------------------------------");
		});
	}

	// =============================================================
	// GÜNLÜK MAÇLARI ÇEK
	// =============================================================
	public List<MatchInfo> fetchMatches() {
		List<MatchInfo> list = new ArrayList<>();
		try {
			String date = LocalDate.now(ZoneId.of("Europe/Istanbul"))
					.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));

			// le=1 kaldırıldı
			String url = "https://www.nesine.com/iddaa?et=1&dt=" + date;

			System.out.println("🔗 URL açılıyor: " + url);
			driver.manage().deleteAllCookies();

			startRequestLogging();
			startNetworkCapture();

			driver.get(url);
			PageWaitUtils.safeWaitForLoad(driver, 25);

			Thread.sleep(3000); // ilk request/response'lar düşsün

			wait.until(ExpectedConditions.presenceOfElementLocated(
					By.cssSelector("[data-test-id^='r_'], a[data-test-id='matchName']")));

			List<Map<String, String>> rawData = scrollAndCollectMatchData();
			System.out.println("✅ Toplam benzersiz maç: " + rawData.size());

			dumpInterestingCapturedResponses();

			int index = 0;
			for (Map<String, String> data : rawData) {
				try {
					String name = data.getOrDefault("name", "-");
					String href = data.getOrDefault("url", "-");
					String time = data.getOrDefault("time", "-");

					Odds odds = new Odds(
							toDouble(data.get("ms1")),
							toDouble(data.get("ms0")),
							toDouble(data.get("ms2")),
							toDouble(data.get("ust")),
							toDouble(data.get("alt")),
							toDouble(data.get("var")),
							toDouble(data.get("yok")),
							Integer.parseInt(data.getOrDefault("mbs", "-1"))
					);

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
	// MAÇ SATIRLARINI TOPLA
	// =============================================================
	private List<Map<String, String>> scrollAndCollectMatchData() throws InterruptedException {
		By matchLinkSelector = By.cssSelector("a[data-test-id='matchName']");
		Set<String> seen = new HashSet<>();
		List<Map<String, String>> collected = new ArrayList<>();

		int stable = 0;
		int maxScroll = 180;
		int prevSeen = 0;

		long startTime = System.currentTimeMillis();
		long maxWaitTime = 360000;

		WebElement scrollContainer = findScrollableContainer();

		int waitTry = 0;
		while (driver.findElements(matchLinkSelector).isEmpty() && waitTry < 30) {
			Thread.sleep(500);
			waitTry++;
		}

		System.out.println("⏳ Match linkleri algılandı (" + waitTry + "sn sonra) - scroll başlıyor...");

		for (int i = 0; i < maxScroll; i++) {
			if (System.currentTimeMillis() - startTime > maxWaitTime) {
				System.out.println("⏰ Max süre doldu");
				break;
			}

			List<WebElement> links = driver.findElements(matchLinkSelector);

			for (WebElement link : links) {
				try {
					String name = link.getText().trim();
					String href = Optional.ofNullable(link.getAttribute("href")).orElse("").trim();

					if (name.isEmpty()) continue;

					String uniqueKey = !href.isEmpty() ? href : name;
					if (seen.contains(uniqueKey)) continue;
					seen.add(uniqueKey);

					WebElement card = findMatchCard(link);

					Map<String, String> map = new HashMap<>();
					map.put("name", name);
					map.put("url", href);

					try {
						String time = card.findElement(By.cssSelector("span[data-testid^='time']")).getText().trim();
						map.put("time", time);
					} catch (Exception ex) {
						map.put("time", "-");
					}

					try {
						WebElement mbsEl = card.findElement(By.cssSelector("[data-test-id='event_mbs'] span"));
						map.put("mbs", mbsEl.getText().trim());
					} catch (Exception ex) {
						map.put("mbs", "-1");
					}

					map.put("ms1", getOdd(card, "odd_Maç Sonucu_1"));
					map.put("ms0", getOdd(card, "odd_Maç Sonucu_X"));
					map.put("ms2", getOdd(card, "odd_Maç Sonucu_2"));
					map.put("alt", getOdd(card, "odd_2,5 Gol_Alt"));
					map.put("ust", getOdd(card, "odd_2,5 Gol_Üst"));
					map.put("var", getOdd(card, "odd_Karş. Gol_Var"));
					map.put("yok", getOdd(card, "odd_Karş. Gol_Yok"));

					collected.add(map);
					System.out.println("✅ " + name + " (" + map.get("time") + ") eklendi.");
				} catch (Exception ex) {
					System.out.println("⚠️ Kart parse edilemedi: " + ex.getMessage());
				}
			}

			int now = seen.size();
			if (now > prevSeen) {
				stable = 0;
				System.out.println("  ✓ Maç sayısı: " + now + " (+yeni " + (now - prevSeen) + ")");
			} else {
				stable++;
				System.out.println("  ⚠️ Stabilite sayacı: " + stable + "/15 (toplam: " + now + ")");
			}
			prevSeen = now;

			if (i % 5 == 0) {
				debugSelectorCounts();
			}

			if (stable >= 15) {
				System.out.println("✅ Scroll tamamlandı");
				break;
			}

			clickLoadMoreIfExists();

			List<WebElement> currentLinks = driver.findElements(matchLinkSelector);
			if (!currentLinks.isEmpty()) {
				try {
					WebElement last = currentLinks.get(currentLinks.size() - 1);
					js.executeScript("arguments[0].scrollIntoView({block:'center'});", last);
					last.sendKeys(Keys.PAGE_DOWN);
				} catch (Exception e) {
					js.executeScript("arguments[0].scrollTop = arguments[0].scrollTop + 1400;", scrollContainer);
				}
			} else {
				js.executeScript("arguments[0].scrollTop = arguments[0].scrollTop + 1400;", scrollContainer);
			}

			Thread.sleep(1500);
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
			if (s == null || s.equals("-") || s.isEmpty()) return 0.0;
			return Double.parseDouble(s.replace(",", "."));
		} catch (Exception e) {
			return 0.0;
		}
	}

	// =============================================================
	// GEÇMİŞ MAÇLAR
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

			try {
				List<WebElement> rows = driver.findElements(By.cssSelector("div[data-test-id='CompitionHistoryTableItem']"));
				System.out.println("🔹 Rekabet geçmişi satır sayısı: " + rows.size());

				for (WebElement r : rows) {
					try {
						String date = safeText(r,
								"[data-test-id='CompitionTableItemSeason'], [data-test-id='TableBodyDate']");
						String league = safeText(r,
								"[data-test-id='CompitionTableItemLeague'], [data-test-id='TableBodyTournament']");
						String homeTeam = extractTeamName(r.findElement(By.cssSelector("div[data-test-id='HomeTeam']")));
						String awayTeam = extractTeamName(r.findElement(By.cssSelector("div[data-test-id='AwayTeam']")));
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

			try {
				List<WebElement> tables = driver.findElements(By.cssSelector("div[data-test-id^='LastMatchesTable']"));
				for (WebElement table : tables) {
					int currentSide = 0;
					try {
						WebElement titleEl = table.findElement(By.cssSelector("h3, [data-test-id='LastMatchesTableTitle']"));
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
							String league = "-";
							String date = "-";
							try {
								WebElement leagueTd = r.findElement(By.cssSelector("td[data-test-id='TableBodyLeague']"));
								List<WebElement> spans = leagueTd.findElements(By.tagName("span"));
								if (spans.size() >= 1) league = spans.get(0).getText().trim();
								if (spans.size() >= 2) date = spans.get(1).getText().trim();
							} catch (Exception ignore) {
							}

							String homeTeam = extractTeamName(r.findElement(By.cssSelector("div[data-test-id='HomeTeam']")));
							String awayTeam = extractTeamName(r.findElement(By.cssSelector("div[data-test-id='AwayTeam']")));
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
			List<WebElement> direct = row.findElements(By.cssSelector("[data-test-id='Score'] span, td[data-test-id='Score']"));
			for (WebElement s : direct) {
				String t = s.getText().trim().replaceAll("\\(.*?\\)", "");
				if (t.matches("\\d+\\s*-\\s*\\d+")) return t;
			}

			List<WebElement> buttons = row.findElements(By.cssSelector("button[data-test-id='NsnButton'] span"));
			for (WebElement b : buttons) {
				String t = b.getText().trim().replaceAll("\\(.*?\\)", "");
				if (t.matches("\\d+\\s*-\\s*\\d+")) return t;
			}

			List<WebElement> spans = row.findElements(By.cssSelector("span"));
			for (WebElement s : spans) {
				String t = s.getText().trim().replaceAll("\\(.*?\\)", "");
				if (t.matches("\\d+\\s*-\\s*\\d+")) return t;
			}
		} catch (Exception e) {
			// ignore
		}
		return "-";
	}

	private int[] parseScore(String s) {
		try {
			String[] p = s.split("-");
			return new int[]{Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim())};
		} catch (Exception e) {
			return new int[]{-1, -1};
		}
	}

	private String[] extractTeamsFromHeader(String url) {
		String home = "-", away = "-", name = "";
		try {
			driver.get(url);
			PageWaitUtils.waitForPageLoad(driver, 12);
			wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("div[data-test-id='HeaderTeams']")));

			WebElement header = driver.findElement(By.cssSelector("div[data-test-id='HeaderTeams']"));
			List<WebElement> teams = header.findElements(
					By.cssSelector("a[data-test-id='TeamLink'] span[data-test-id='HeaderTeams']"));

			if (teams.size() >= 2) {
				home = teams.get(0).getText().trim();
				away = teams.get(1).getText().trim();
			}
		} catch (Exception e) {
			System.out.println("Takım adları çekilemedi: " + e.getMessage());
		}
		name = home + " - " + away;
		return new String[]{home, away, name};
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

	private WebElement findScrollableContainer() {
		List<By> candidates = Arrays.asList(
				By.cssSelector("div[class*='scroll']"),
				By.cssSelector("div[class*='content']"),
				By.cssSelector("main"),
				By.cssSelector("body")
		);

		for (By by : candidates) {
			try {
				List<WebElement> els = driver.findElements(by);
				for (WebElement el : els) {
					Object shObj = js.executeScript("return arguments[0].scrollHeight;", el);
					Object chObj = js.executeScript("return arguments[0].clientHeight;", el);

					long sh = shObj instanceof Number ? ((Number) shObj).longValue() : -1L;
					long ch = chObj instanceof Number ? ((Number) chObj).longValue() : -1L;

					if (sh > ch + 200) {
						System.out.println("✅ Scroll container bulundu: " + by);
						return el;
					}
				}
			} catch (Exception ignore) {
			}
		}

		System.out.println("⚠️ Özel scroll container bulunamadı, body kullanılacak");
		return driver.findElement(By.tagName("body"));
	}

	private void clickLoadMoreIfExists() {
		List<By> buttons = Arrays.asList(
				By.xpath("//button[contains(., 'Daha Fazla')]"),
				By.xpath("//button[contains(., 'Daha fazla')]"),
				By.xpath("//button[contains(., 'Tümünü Göster')]"),
				By.cssSelector("button[data-test-id*='load'], button[data-testid*='load']")
		);

		for (By by : buttons) {
			try {
				List<WebElement> els = driver.findElements(by);
				for (WebElement btn : els) {
					if (btn.isDisplayed() && btn.isEnabled()) {
						js.executeScript("arguments[0].click();", btn);
						System.out.println("➕ Daha fazla butonuna tıklandı");
						Thread.sleep(1200);
						return;
					}
				}
			} catch (Exception ignore) {
			}
		}
	}

	private boolean waitForNewContent(By eventSelector, WebElement scrollContainer, int oldVisibleCount, int timeoutMs) {
		long end = System.currentTimeMillis() + timeoutMs;
		long oldHeight = getScrollHeight(scrollContainer);

		while (System.currentTimeMillis() < end) {
			try {
				Thread.sleep(250);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return false;
			}

			int newVisibleCount = driver.findElements(eventSelector).size();
			long newHeight = getScrollHeight(scrollContainer);

			if (newVisibleCount > oldVisibleCount || newHeight > oldHeight) {
				return true;
			}
		}
		return false;
	}

	private long getScrollHeight(WebElement el) {
		try {
			Object val = js.executeScript("return arguments[0].scrollHeight;", el);
			if (val instanceof Number) {
				return ((Number) val).longValue();
			}
		} catch (Exception ignore) {
		}
		return -1;
	}

	private WebElement findMatchCard(WebElement matchLink) {
		try {
			return (WebElement) js.executeScript("""
				let el = arguments[0];
				while (el) {
					if (el.matches && (
							el.matches("div[data-test-id^='r_']") ||
							el.querySelector("[data-test-id='event_mbs']") ||
							el.querySelector("button[data-testid*='odd_']")
					)) {
						return el;
					}
					el = el.parentElement;
				}
				return arguments[0].parentElement;
			""", matchLink);
		} catch (Exception e) {
			return matchLink;
		}
	}

	private void debugSelectorCounts() {
		System.out.println("DEBUG row[data-sport-id=1]: " +
				driver.findElements(By.cssSelector("div[data-test-id^='r_'][data-sport-id='1']")).size());

		System.out.println("DEBUG any row[data-test-id^=r_]: " +
				driver.findElements(By.cssSelector("[data-test-id^='r_']")).size());

		System.out.println("DEBUG matchName links: " +
				driver.findElements(By.cssSelector("a[data-test-id='matchName']")).size());

		System.out.println("DEBUG all matchName elems: " +
				driver.findElements(By.cssSelector("[data-test-id='matchName']")).size());
	}
}