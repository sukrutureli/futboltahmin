package com.example.scraper;

import com.example.PageWaitUtils;
import com.example.model.MatchInfo;
import com.example.model.MatchResult;
import com.example.model.Odds;
import com.example.model.TeamMatchHistory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

public class MatchScraper {

    private WebDriver driver;
    private JavascriptExecutor js;
    private WebDriverWait wait;

    private DevTools devTools;
    private final AtomicBoolean networkListenersAttached = new AtomicBoolean(false);

    private final Map<String, String> capturedResponses = new ConcurrentHashMap<>();
    private final List<String> capturedMatchJsonBodies = Collections.synchronizedList(new ArrayList<>());
    private final Set<String> capturedMatchJsonHashes = Collections.synchronizedSet(new HashSet<>());
    private final AtomicInteger responseCounter = new AtomicInteger(0);

    private final ObjectMapper objectMapper = new ObjectMapper();

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
    private void ensureNetworkCaptureStarted() {
        if (devTools == null) {
            System.out.println("⚠️ DevTools yok, network capture başlatılamadı");
            return;
        }

        if (networkListenersAttached.getAndSet(true)) {
            return;
        }

        startRequestLogging();
        startNetworkCapture();
    }

    private void startRequestLogging() {
        devTools.addListener(Network.requestWillBeSent(), req -> {
            try {
                String url = req.getRequest().getUrl();
                String u = url.toLowerCase(Locale.ROOT);

                if (isInterestingUrl(u)) {
                    System.out.println("➡️ Request: " + req.getRequest().getMethod() + " " + url);
                }
            } catch (Exception e) {
                System.out.println("⚠️ request log hata: " + e.getMessage());
            }
        });
    }

    private void startNetworkCapture() {
        Predicate<String> interestingUrl = url -> isInterestingUrl(url.toLowerCase(Locale.ROOT));

        devTools.addListener(Network.responseReceived(), response -> {
            try {
                String url = response.getResponse().getUrl();
                String lowerUrl = url.toLowerCase(Locale.ROOT);
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
                                || lowerUrl.contains("api")
                                || lowerUrl.contains("graphql")
                                || lowerUrl.contains("bulten")
                                || lowerUrl.contains("ls.nesine.com");

                if (!maybeUseful) {
                    return;
                }

                String body = getResponseBodyWithRetry(response.getRequestId(), url);
                if (body == null || body.isBlank()) {
                    return;
                }

                capturedResponses.put(url, body);

                int no = responseCounter.incrementAndGet();
                String shortBody = body.length() > 400 ? body.substring(0, 400) : body;

                System.out.println("📦 BODY #" + no + ": " + url);
                System.out.println(shortBody.replace("\n", " ").replace("\r", " "));

                if (shouldPersistResponse(url, body)) {
                    saveCapturedResponse(no, url, body);
                }

                if (looksLikeMatchJson(url, body)) {
                    String hash = Integer.toHexString(body.hashCode());
                    if (capturedMatchJsonHashes.add(hash)) {
                        capturedMatchJsonBodies.add(body);
                        System.out.println("🔥 Match JSON adayı yakalandı: " + url);
                    }
                }

            } catch (Exception e) {
                System.out.println("⚠️ response listener hata: " + e.getMessage());
            }
        });
    }

    private boolean isInterestingUrl(String u) {
        return u.contains("iddaa")
                || u.contains("event")
                || u.contains("match")
                || u.contains("odd")
                || u.contains("program")
                || u.contains("bet")
                || u.contains("sports")
                || u.contains("coupon")
                || u.contains("graphql")
                || u.contains("bulten.nesine.com")
                || u.contains("ls.nesine.com")
                || u.contains("livescore");
    }

    private boolean shouldPersistResponse(String url, String body) {
        String u = url.toLowerCase(Locale.ROOT);
        return looksLikeMatchJson(url, body)
                || u.contains("getsporteventcounts")
                || u.contains("getprebultendelta")
                || u.contains("getlivebultenv3")
                || u.contains("getchangedodds")
                || u.contains("getlivebetresultswithversion");
    }

    private String getResponseBodyWithRetry(RequestId requestId, String url) {
        for (int i = 0; i < 4; i++) {
            try {
                if (i > 0) {
                    Thread.sleep(600L * i);
                }
                Network.GetResponseBodyResponse bodyResponse =
                        devTools.send(Network.getResponseBody(requestId));
                String body = bodyResponse.getBody();
                if (body != null && !body.isBlank()) {
                    return body;
                }
            } catch (Exception ex) {
                if (i == 3) {
                    System.out.println("⚠️ Body alınamadı: " + url + " | " + ex.getMessage());
                }
            }
        }
        return null;
    }

    private boolean looksLikeMatchJson(String url, String body) {
        String u = url.toLowerCase(Locale.ROOT);
        if (!(u.contains("bulten") || u.contains("bet") || u.contains("live") || u.contains("event") || u.contains("ls.nesine.com"))) {
            return false;
        }

        String b = body == null ? "" : body;
        return (b.contains("\"MID\"") && b.contains("\"matchDate\""))
                || (b.contains("\"d\"") && b.contains("\"MID\""))
                || (b.contains("\"N\"") && b.contains("\"DT\""))
                || (b.contains("\"HT\"") && b.contains("\"AT\""))
                || (b.contains("\"HomeTeamName\"") && b.contains("\"AwayTeamName\""));
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

    private void resetCapturedState() {
        capturedResponses.clear();
        capturedMatchJsonBodies.clear();
        capturedMatchJsonHashes.clear();
        responseCounter.set(0);
    }

    // =============================================================
    // GÜNLÜK MAÇLARI ÇEK
    // =============================================================
    public List<MatchInfo> fetchMatches() {
        List<MatchInfo> list = new ArrayList<>();
        try {
            resetCapturedState();
            ensureNetworkCaptureStarted();

            String date = LocalDate.now(ZoneId.of("Europe/Istanbul"))
                    .format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));

            String url = "https://www.nesine.com/iddaa?et=1&le=1&dt=" + date;

            System.out.println("🔗 URL açılıyor: " + url);
            driver.manage().deleteAllCookies();
            driver.get(url);
            PageWaitUtils.safeWaitForLoad(driver, 25);

            wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.cssSelector("[data-test-id^='r_'], a[data-test-id='matchName']")));

            waitForNetworkData(7000);

            List<Map<String, String>> jsonMatches = collectMatchesFromCapturedJson();
            List<Map<String, String>> finalMatches;

            if (jsonMatches.size() >= 20) {
                System.out.println("✅ JSON'dan yeterli maç geldi: " + jsonMatches.size());
                finalMatches = enrichJsonMatchesWithDom(jsonMatches);
            } else {
                System.out.println("⚠️ JSON yetersiz (" + jsonMatches.size() + "), DOM fallback devreye giriyor...");
                List<Map<String, String>> domMatches = scrollAndCollectMatchData();
                finalMatches = mergeMatchLists(jsonMatches, domMatches);
            }

            System.out.println("✅ Toplam benzersiz maç: " + finalMatches.size());
            //dumpInterestingCapturedResponses();

            int index = 0;
            for (Map<String, String> data : finalMatches) {
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

    private void waitForNetworkData(long timeoutMs) {
        long end = System.currentTimeMillis() + timeoutMs;
        int prev = -1;
        int stable = 0;

        while (System.currentTimeMillis() < end) {
            int current = capturedMatchJsonBodies.size();
            if (current == prev) {
                stable++;
            } else {
                stable = 0;
            }
            prev = current;

            if (current > 0 && stable >= 4) {
                break;
            }

            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        System.out.println("🧠 Yakalanan match JSON body sayısı: " + capturedMatchJsonBodies.size());
    }

    // =============================================================
    // JSON MATCH COLLECTOR
    // =============================================================
    private List<Map<String, String>> collectMatchesFromCapturedJson() {
        Map<String, Map<String, String>> merged = new LinkedHashMap<>();

        for (String body : capturedMatchJsonBodies) {
            try {
                JsonNode root = objectMapper.readTree(body);
                JsonNode dataNode = root.path("d");

                if (!dataNode.isArray()) {
                    continue;
                }

                for (JsonNode item : dataNode) {
                    try {
                        String leagueOrType = firstNonBlank(
                                text(item, "TT"),
                                text(item, "LN"),
                                text(item, "LeagueName"),
                                text(item, "CN")
                        );

                        String lowerLeague = leagueOrType.toLowerCase(Locale.ROOT);
                        if (lowerLeague.contains("esoccer")
                                || lowerLeague.contains("ebasketball")
                                || lowerLeague.contains("esports")) {
                            continue;
                        }

                        String sportType = text(item, "T");
                        String name = extractMatchName(item);

                        if (name.isBlank() || "-".equals(name) || !name.contains(" - ")) {
                            continue;
                        }

                        if (!isLikelyFootball(item, sportType, name, leagueOrType)) {
                            continue;
                        }

                        String time = firstNonBlank(
                                text(item, "DT"),
                                text(item, "MD"),
                                text(item, "matchDate"),
                                "-"
                        );

                        String mid = firstNonBlank(
                                text(item, "MID"),
                                text(item, "BID"),
                                text(item, "C"),
                                UUID.randomUUID().toString()
                        );

                        String url = buildSyntheticUrl(item, mid);

                        Map<String, String> map = new HashMap<>();
                        map.put("name", name);
                        map.put("time", normalizeTime(time));
                        map.put("url", url);
                        map.put("mbs", extractMbs(item));

                        map.put("ms1", extractOdd(item, "1", "MS1"));
                        map.put("ms0", extractOdd(item, "X", "MSX"));
                        map.put("ms2", extractOdd(item, "2", "MS2"));
                        map.put("alt", extractOdd(item, "ALT", "ALT"));
                        map.put("ust", extractOdd(item, "ÜST", "UST"));
                        map.put("var", extractOdd(item, "VAR", "VAR"));
                        map.put("yok", extractOdd(item, "YOK", "YOK"));

                        merged.putIfAbsent(mid, map);

                    } catch (Exception ex) {
                        System.out.println("⚠️ JSON item parse edilemedi: " + ex.getMessage());
                    }
                }
            } catch (Exception e) {
                System.out.println("⚠️ JSON body parse edilemedi: " + e.getMessage());
            }
        }

        System.out.println("🧠 JSON'dan çıkan maç sayısı: " + merged.size());
        return new ArrayList<>(merged.values());
    }

    private String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        if (v.isMissingNode() || v.isNull()) return "";
        return v.asText("").trim();
    }

    private String firstNonBlank(String... vals) {
        for (String v : vals) {
            if (v != null && !v.isBlank()) return v;
        }
        return "";
    }

    private boolean isLikelyFootball(JsonNode item, String sportType, String name, String leagueOrType) {
        if ("1".equals(sportType)) return true;

        String league = leagueOrType == null ? "" : leagueOrType.toLowerCase(Locale.ROOT);

        if (league.contains("football")
                || league.contains("futbol")
                || league.contains("u21")
                || league.contains("u19")
                || league.contains("kadın")
                || league.contains("women")
                || league.contains("hazırlık")
                || league.contains("friendly")
                || league.contains("cup")
                || league.contains("league")) {
            return true;
        }

        return name.contains(" - ");
    }

    private String extractMatchName(JsonNode item) {
        String n = text(item, "N");
        if (!n.isBlank()) return n;

        String home = firstNonBlank(
                text(item, "HT"),
                text(item, "HomeTeamName"),
                text(item, "HN")
        );
        String away = firstNonBlank(
                text(item, "AT"),
                text(item, "AwayTeamName"),
                text(item, "AN")
        );

        if (!home.isBlank() && !away.isBlank()) {
            return home + " - " + away;
        }

        JsonNode teams = item.path("TE");
        if (teams.isArray() && teams.size() >= 2) {
            String t1 = teams.get(0).asText("").trim();
            String t2 = teams.get(1).asText("").trim();
            if (!t1.isBlank() && !t2.isBlank()) {
                return t1 + " - " + t2;
            }
        }

        return "-";
    }

    private String normalizeTime(String raw) {
        if (raw == null || raw.isBlank()) return "-";

        try {
            if (raw.contains("T")) {
                OffsetDateTime odt = OffsetDateTime.parse(raw);
                return odt.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"));
            }
        } catch (Exception ignore) {
        }

        if (raw.matches("\\d{1,2}:\\d{2}")) {
            return raw;
        }

        return raw;
    }

    private String extractMbs(JsonNode item) {
        return firstNonBlank(
                text(item, "MBS"),
                text(item, "Mbs"),
                "-1"
        );
    }

    private String buildSyntheticUrl(JsonNode item, String mid) {
        String detail = firstNonBlank(
                text(item, "DetailUrl"),
                text(item, "U"),
                text(item, "Url")
        );

        if (!detail.isBlank()) {
            if (detail.startsWith("http")) {
                return detail;
            }
            if (detail.startsWith("/")) {
                return "https://www.nesine.com" + detail;
            }
        }

        return "nesine://match/" + mid;
    }

    private String extractOdd(JsonNode item, String containsKey, String fallbackLabel) {
        JsonNode odds = item.path("O");
        if (odds.isArray()) {
            for (JsonNode odd : odds) {
                String n = firstNonBlank(text(odd, "N"), text(odd, "Name"), text(odd, "OCN")).toUpperCase(Locale.ROOT);
                String v = firstNonBlank(text(odd, "V"), text(odd, "Value"), text(odd, "O")).replace(",", ".");

                if (!n.isBlank() && !v.isBlank()) {
                    if (n.contains(containsKey)) {
                        return v;
                    }
                }
            }
        }

        JsonNode markets = item.path("MDT");
        if (markets.isArray()) {
            for (JsonNode market : markets) {
                String n = market.toString().toUpperCase(Locale.ROOT);
                if (n.contains(containsKey)) {
                    String v = findFirstDecimal(market.toString());
                    if (!v.equals("-")) return v;
                }
            }
        }

        return "-";
    }

    private String findFirstDecimal(String input) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+[\\.,]\\d+)").matcher(input);
        if (m.find()) {
            return m.group(1).replace(",", ".");
        }
        return "-";
    }

    private List<Map<String, String>> mergeMatchLists(List<Map<String, String>> primary, List<Map<String, String>> secondary) {
        Map<String, Map<String, String>> merged = new LinkedHashMap<>();

        for (Map<String, String> m : primary) {
            merged.put(matchKey(m), new HashMap<>(m));
        }

        for (Map<String, String> m : secondary) {
            String key = matchKey(m);
            if (!merged.containsKey(key)) {
                merged.put(key, new HashMap<>(m));
            } else {
                Map<String, String> base = merged.get(key);
                fillIfMissing(base, m, "url");
                fillIfMissing(base, m, "time");
                fillIfMissing(base, m, "mbs");
                fillIfMissing(base, m, "ms1");
                fillIfMissing(base, m, "ms0");
                fillIfMissing(base, m, "ms2");
                fillIfMissing(base, m, "alt");
                fillIfMissing(base, m, "ust");
                fillIfMissing(base, m, "var");
                fillIfMissing(base, m, "yok");
            }
        }

        return new ArrayList<>(merged.values());
    }

    private List<Map<String, String>> enrichJsonMatchesWithDom(List<Map<String, String>> jsonMatches) {
        try {
            List<Map<String, String>> domMatches = scrollAndCollectMatchData();
            return mergeMatchLists(jsonMatches, domMatches);
        } catch (Exception e) {
            System.out.println("⚠️ DOM enrich başarısız: " + e.getMessage());
            return jsonMatches;
        }
    }

    private String matchKey(Map<String, String> m) {
        return (m.getOrDefault("name", "").trim().toLowerCase(Locale.ROOT) + "|" +
                m.getOrDefault("time", "").trim());
    }

    private void fillIfMissing(Map<String, String> base, Map<String, String> from, String key) {
        String b = base.getOrDefault(key, "");
        String f = from.getOrDefault(key, "");
        if (b == null || b.isBlank() || "-".equals(b) || "0".equals(b) || "-1".equals(b)) {
            if (f != null && !f.isBlank()) {
                base.put(key, f);
            }
        }
    }

    // =============================================================
    // MAÇ SATIRLARINI DOM'DAN TOPLA
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

                    String uniqueKey = (!href.isEmpty() ? href : name) + "|" + safeLinkTime(link);
                    if (seen.contains(uniqueKey)) continue;
                    seen.add(uniqueKey);

                    WebElement card = findMatchCard(link);

                    Map<String, String> map = new HashMap<>();
                    map.put("name", name);
                    map.put("url", href.isBlank() ? "-" : href);

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
                //debugSelectorCounts();
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

    private String safeLinkTime(WebElement link) {
        try {
            WebElement card = findMatchCard(link);
            return card.findElement(By.cssSelector("span[data-testid^='time']")).getText().trim();
        } catch (Exception e) {
            return "-";
        }
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