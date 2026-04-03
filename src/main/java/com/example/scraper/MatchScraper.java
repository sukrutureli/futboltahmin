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

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
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
    }

    // =============================================================
    // GÜNLÜK MAÇLARI ÇEK
    // =============================================================
    public List<MatchInfo> fetchMatches() {
        List<MatchInfo> list = new ArrayList<>();
        try {
            String date = LocalDate.now(ZoneId.of("Europe/Istanbul"))
                    .format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));

            // le=1 KORUNDU
            String url = "https://www.nesine.com/iddaa?et=1&le=1&dt=" + date;

            System.out.println("🔗 URL açılıyor: " + url);
            driver.manage().deleteAllCookies();
            driver.get(url);
            PageWaitUtils.safeWaitForLoad(driver, 25);

            wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.cssSelector("[data-test-id^='r_'], a[data-test-id='matchName']")));

            expandAllSectionsIfPossible();

            List<Map<String, String>> rawData = scrollAndCollectMatchData();

            System.out.println("✅ Toplam benzersiz maç: " + rawData.size());

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
    // MAÇ SATIRLARINI DOM'DAN TOPLA
    // =============================================================
    private List<Map<String, String>> scrollAndCollectMatchData() throws InterruptedException {
        By matchLinkSelector = By.cssSelector("a[data-test-id='matchName']");
        Set<String> seen = new HashSet<>();
        List<Map<String, String>> collected = new ArrayList<>();

        int stable = 0;
        int maxScroll = 300;
        int prevSeen = 0;

        long startTime = System.currentTimeMillis();
        long maxWaitTime = 420000;

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

                    // SADECE gerçek detail URL'si olanlar
                    if (!isRealDetailUrl(href)) {
                        continue;
                    }

                    WebElement card = findMatchCard(link);

                    String time = "-";
                    try {
                        time = card.findElement(By.cssSelector("span[data-testid^='time']")).getText().trim();
                    } catch (Exception ignore) {
                    }

                    String uniqueKey = href + "|" + time;
                    if (seen.contains(uniqueKey)) continue;
                    seen.add(uniqueKey);

                    Map<String, String> map = new HashMap<>();
                    map.put("name", name);
                    map.put("url", href);
                    map.put("time", time);

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
                    System.out.println("✅ " + name + " (" + time + ") eklendi. | URL=" + href);
                } catch (Exception ex) {
                    System.out.println("⚠️ Kart parse edilemedi: " + ex.getMessage());
                }
            }

            int now = seen.size();
            if (now > prevSeen) {
                stable = 0;
                System.out.println("  ✓ URL'li maç sayısı: " + now + " (+yeni " + (now - prevSeen) + ")");
            } else {
                stable++;
                System.out.println("  ⚠️ Stabilite sayacı: " + stable + "/20 (toplam URL'li: " + now + ")");
            }
            prevSeen = now;

            if (i % 5 == 0) {
                debugSelectorCounts();
            }

            if (stable >= 20) {
                System.out.println("✅ Scroll tamamlandı");
                break;
            }

            clickLoadMoreIfExists();

            List<WebElement> currentLinks = driver.findElements(matchLinkSelector);
            if (!currentLinks.isEmpty()) {
                try {
                    WebElement last = currentLinks.get(currentLinks.size() - 1);
                    js.executeScript("arguments[0].scrollIntoView({block:'center'});", last);
                    Thread.sleep(400);
                    last.sendKeys(Keys.PAGE_DOWN);
                    Thread.sleep(400);
                } catch (Exception e) {
                    js.executeScript("arguments[0].scrollTop = arguments[0].scrollTop + 2400;", scrollContainer);
                }
            } else {
                js.executeScript("arguments[0].scrollTop = arguments[0].scrollTop + 2400;", scrollContainer);
            }

            Thread.sleep(2500);
        }

        System.out.println("🧩 TOPLAM URL'Lİ MAÇ: " + seen.size());
        return collected;
    }

    private boolean isRealDetailUrl(String href) {
        if (href == null || href.isBlank()) return false;
        if (!href.startsWith("http")) return false;

        String h = href.toLowerCase(Locale.ROOT);

        return h.contains("istatistik.nesine.com")
                || h.contains("/ozet")
                || h.contains("/detay")
                || h.contains("/mac/")
                || h.contains("/match/");
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
                for (int idx = 0; idx < tables.size(); idx++) {
                    WebElement table = tables.get(idx);
                    int currentSide = 0;
                    try {
                        WebElement titleEl = table.findElement(By.cssSelector("h3, [data-test-id='LastMatchesTableTitle']"));
                        String titleText = titleEl.getText().toLowerCase(Locale.ROOT);
                        if (titleText.contains("ev") || titleText.contains("home"))
                            currentSide = 1;
                        else if (titleText.contains("deplasman") || titleText.contains("away"))
                            currentSide = 2;
                    } catch (Exception e) {
                        currentSide = (idx == 0) ? 1 : 2;
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
        } catch (Exception ignore) {
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

    // =============================================================
    // DOM YARDIMCILARI
    // =============================================================
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

    private void expandAllSectionsIfPossible() {
        List<By> candidates = Arrays.asList(
                By.cssSelector("button[aria-expanded='false']"),
                By.cssSelector("[data-test-id*='accordion'] button"),
                By.cssSelector("[data-testid*='accordion'] button"),
                By.xpath("//button[contains(@aria-label,'aç')]"),
                By.xpath("//button[contains(., 'Daha Fazla')]"),
                By.xpath("//button[contains(., 'Tümünü Göster')]")
        );

        int clicked = 0;

        for (By by : candidates) {
            try {
                List<WebElement> elements = driver.findElements(by);
                for (WebElement el : elements) {
                    try {
                        if (el.isDisplayed() && el.isEnabled()) {
                            js.executeScript("arguments[0].click();", el);
                            clicked++;
                            Thread.sleep(500);
                        }
                    } catch (Exception ignore) {
                    }
                }
            } catch (Exception ignore) {
            }
        }

        System.out.println("📂 Açılabilen bölüm/buton sayısı: " + clicked);
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

        int realUrlCount = 0;
        List<WebElement> links = driver.findElements(By.cssSelector("a[data-test-id='matchName']"));
        for (WebElement link : links) {
            try {
                String href = Optional.ofNullable(link.getAttribute("href")).orElse("");
                if (isRealDetailUrl(href)) {
                    realUrlCount++;
                }
            } catch (Exception ignore) {
            }
        }

        System.out.println("DEBUG real detail url count: " + realUrlCount);
    }
}
