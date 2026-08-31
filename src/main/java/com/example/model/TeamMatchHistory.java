package com.example.model;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TeamMatchHistory {
	private String teamName;
	private String teamEv;
	private String teamDep;
	private String originalMatchUrl;
	private List<MatchResult> rekabetGecmisi;
	private List<MatchResult> sonMaclarHome;
	private List<MatchResult> sonMaclarAway;
//	private LocalDateTime lastUpdated;

	public TeamMatchHistory() {

	}

	public TeamMatchHistory(String teamName, String teamEv, String teamDep, String originalMatchUrl) {
		this.teamName = teamName;
		this.teamEv = teamEv;
		this.teamDep = teamDep;
		this.originalMatchUrl = originalMatchUrl;
		this.rekabetGecmisi = new ArrayList<>();
		this.sonMaclarHome = new ArrayList<>();
		this.sonMaclarAway = new ArrayList<>();
//		this.lastUpdated = LocalDateTime.now();
	}

	// Add methods
	public void addRekabetGecmisiMatch(MatchResult match) {
		rekabetGecmisi.add(match);
	}

	public void addSonMacMatch(MatchResult match, int homeOrAway) {
		if (homeOrAway == 1) {
			sonMaclarHome.add(match);
		} else if (homeOrAway == 2) {
			sonMaclarAway.add(match);
		}
	}

	// Getters
	public String getTeamName() {
		return teamName;
	}

	public String getOriginalMatchUrl() {
		return originalMatchUrl;
	}

	public List<MatchResult> getRekabetGecmisi() {
		return rekabetGecmisi;
	}

	public List<MatchResult> getSonMaclarAway() {
		return sonMaclarAway;
	}

	public List<MatchResult> getSonMaclarHome() {
		return sonMaclarHome;
	}

//	public LocalDateTime getLastUpdated() {
//		return lastUpdated;
//	}

	public String getTeamEv() {
		return teamEv;
	}

	public String getTeamDep() {
		return teamDep;
	}

	// Utility methods
	public int getTotalMatches() {
		return rekabetGecmisi.size() + sonMaclarHome.size() + sonMaclarAway.size();
	}

	@JsonIgnore
	public List<MatchResult> getAllMatches() {
		List<MatchResult> allMatches = new ArrayList<>();
		allMatches.addAll(rekabetGecmisi);
		allMatches.addAll(sonMaclarHome);
		allMatches.addAll(sonMaclarAway);
		return allMatches;
	}

	public int getWinCount() {
		return (int) getAllMatches().stream().filter(match -> {
			String result = match.getResult();
			return (match.getHomeTeam().contains(teamName) && result.equals("H"))
					|| (match.getAwayTeam().contains(teamName) && result.equals("A"));
		}).count();
	}

	public int getDrawCount() {
		return (int) getAllMatches().stream().filter(match -> match.getResult().equals("D")).count();
	}

	public int getLossCount() {
		return getTotalMatches() - getWinCount() - getDrawCount();
	}

	public double getWinRate() {
		if (getTotalMatches() == 0)
			return 0.0;
		return (double) getWinCount() / getTotalMatches() * 100;
	}

	public double getMs1() {
		double ms1Rekabet = 0;
		double ms1SonH = 0;
		double ms1SonA = 0;

		for (int i = 0; i < rekabetGecmisi.size(); i++) {
			if (rekabetGecmisi.get(i).getHomeTeam().contains(teamEv) && rekabetGecmisi.get(i).getResult() == "H") {
				ms1Rekabet++;
			} else if (rekabetGecmisi.get(i).getAwayTeam().contains(teamEv)
					&& rekabetGecmisi.get(i).getResult() == "A") {
				ms1Rekabet++;
			}
		}

		for (int i = 0; i < sonMaclarHome.size(); i++) {
			if (sonMaclarHome.get(i).getHomeTeam().contains(teamEv) && sonMaclarHome.get(i).getResult() == "H") {
				ms1SonH++;
			} else if (sonMaclarHome.get(i).getAwayTeam().contains(teamEv) && sonMaclarHome.get(i).getResult() == "A") {
				ms1SonH++;
			}
		}

		for (int i = 0; i < sonMaclarAway.size(); i++) {
			if (sonMaclarAway.get(i).getHomeTeam().contains(teamDep) && sonMaclarAway.get(i).getResult() == "A") {
				ms1SonA++;
			} else if (sonMaclarAway.get(i).getAwayTeam().contains(teamDep)
					&& sonMaclarAway.get(i).getResult() == "H") {
				ms1SonA++;
			}
		}

		if (isInfoEnough()) {
			double result = (ms1Rekabet / rekabetGecmisi.size()) * 0.1;
			result += ((ms1SonH / sonMaclarHome.size()) * 0.45);
			result += ((ms1SonA / sonMaclarAway.size()) * 0.45);

			return result;
		} else if (isInfoEnoughWithoutRekabet()) {
			double result = ((ms1SonH / sonMaclarHome.size()) * 0.5);
			result += ((ms1SonA / sonMaclarAway.size()) * 0.5);

			return result;
		} else {
			return (ms1Rekabet + ms1SonH + ms1SonA) / getTotalMatches();
		}
	}

	public double getMs2() {
		double ms2Rekabet = 0;
		double ms2SonH = 0;
		double ms2SonA = 0;

		for (int i = 0; i < rekabetGecmisi.size(); i++) {
			if (rekabetGecmisi.get(i).getHomeTeam().contains(teamDep) && rekabetGecmisi.get(i).getResult() == "H") {
				ms2Rekabet++;
			} else if (rekabetGecmisi.get(i).getAwayTeam().contains(teamDep)
					&& rekabetGecmisi.get(i).getResult() == "A") {
				ms2Rekabet++;
			}
		}

		for (int i = 0; i < sonMaclarHome.size(); i++) {
			if (sonMaclarHome.get(i).getHomeTeam().contains(teamEv) && sonMaclarHome.get(i).getResult() == "A") {
				ms2SonH++;
			} else if (sonMaclarHome.get(i).getAwayTeam().contains(teamEv) && sonMaclarHome.get(i).getResult() == "H") {
				ms2SonH++;
			}
		}

		for (int i = 0; i < sonMaclarAway.size(); i++) {
			if (sonMaclarAway.get(i).getHomeTeam().contains(teamDep) && sonMaclarAway.get(i).getResult() == "H") {
				ms2SonA++;
			} else if (sonMaclarAway.get(i).getAwayTeam().contains(teamDep)
					&& sonMaclarAway.get(i).getResult() == "A") {
				ms2SonA++;
			}
		}

		if (isInfoEnough()) {
			double result = (ms2Rekabet / rekabetGecmisi.size()) * 0.1;
			result += ((ms2SonH / sonMaclarHome.size()) * 0.45);
			result += ((ms2SonA / sonMaclarAway.size()) * 0.45);

			return result;
		} else if (isInfoEnoughWithoutRekabet()) {
			double result = ((ms2SonH / sonMaclarHome.size()) * 0.5);
			result += ((ms2SonA / sonMaclarAway.size()) * 0.5);

			return result;
		} else {
			return (ms2Rekabet + ms2SonH + ms2SonA) / getTotalMatches();
		}
	}

	public double getMs0() {
		return 1 - getMs1() - getMs2();
	}

	public double getVar() {
		double varRekabet = 0;
		double varSonH = 0;
		double varSonA = 0;

		for (int i = 0; i < rekabetGecmisi.size(); i++) {
			if (rekabetGecmisi.get(i).getHomeScore() > 0 && rekabetGecmisi.get(i).getAwayScore() > 0) {
				varRekabet++;
			}
		}

		for (int i = 0; i < sonMaclarHome.size(); i++) {
			if (sonMaclarHome.get(i).getHomeScore() > 0 && sonMaclarHome.get(i).getAwayScore() > 0) {
				varSonH++;
			}
		}

		for (int i = 0; i < sonMaclarAway.size(); i++) {
			if (sonMaclarAway.get(i).getHomeScore() > 0 && sonMaclarAway.get(i).getAwayScore() > 0) {
				varSonA++;
			}
		}

		if (isInfoEnough()) {
			double result = (varRekabet / rekabetGecmisi.size()) * 0.1;
			result += ((varSonH / sonMaclarHome.size()) * 0.45);
			result += ((varSonA / sonMaclarAway.size()) * 0.45);

			return result;
		} else if (isInfoEnoughWithoutRekabet()) {
			double result = ((varSonH / sonMaclarHome.size()) * 0.5);
			result += ((varSonA / sonMaclarAway.size()) * 0.5);

			return result;
		} else {
			return (varRekabet + varSonH + varSonA) / getTotalMatches();
		}
	}

	public double getYok() {
		return 1 - getVar();
	}

	public double getUst() {
		double ustRekabet = 0;
		double ustSonH = 0;
		double ustSonA = 0;

		for (int i = 0; i < rekabetGecmisi.size(); i++) {
			if ((rekabetGecmisi.get(i).getHomeScore() + rekabetGecmisi.get(i).getAwayScore()) > 2) {
				ustRekabet++;
			}
		}

		for (int i = 0; i < sonMaclarHome.size(); i++) {
			if ((sonMaclarHome.get(i).getHomeScore() + sonMaclarHome.get(i).getAwayScore()) > 2) {
				ustSonH++;
			}
		}

		for (int i = 0; i < sonMaclarAway.size(); i++) {
			if ((sonMaclarAway.get(i).getHomeScore() + sonMaclarAway.get(i).getAwayScore()) > 2) {
				ustSonA++;
			}
		}

		if (isInfoEnough()) {
			double result = (ustRekabet / rekabetGecmisi.size()) * 0.1;
			result += ((ustSonH / sonMaclarHome.size()) * 0.45);
			result += ((ustSonA / sonMaclarAway.size()) * 0.45);

			return result;
		} else if (isInfoEnoughWithoutRekabet()) {
			double result = ((ustSonH / sonMaclarHome.size()) * 0.5);
			result += ((ustSonA / sonMaclarAway.size()) * 0.5);

			return result;
		} else {
			return (ustRekabet + ustSonH + ustSonA) / getTotalMatches();
		}
	}

	public double getAlt() {
		return 1 - getUst();
	}

	public boolean isInfoEnough() {
		if (sonMaclarHome.size() < 2 || sonMaclarAway.size() < 2 || rekabetGecmisi.size() < 2) {
			return false;
		}

		return true;
	}

	public boolean isInfoEnoughWithoutRekabet() {
		if (sonMaclarHome.size() > 1 && sonMaclarAway.size() > 1 && rekabetGecmisi.size() < 2) {
			return true;
		}

		return false;
	}

	public String getStyle(String type, Double oddValue) {
		String color = "background-color:#e8fbe8; border:1px solid #6ecf6e;";

		if (!isInfoEnough() && !isInfoEnoughWithoutRekabet()) {
			return "";
		}

		if (oddValue > 1.0) {
			if (type.equals(getMax())) {
				return color;
			}
		}
		return "";
	}

	public Match createMatch(MatchInfo pMatch) {
		Match currentMatch = new Match(teamEv, teamDep);
		currentMatch.setOdds(pMatch.getOdds());

		TeamStats homeStats = new TeamStats();
		homeStats.setLast5Points(getPoints(sonMaclarHome, teamEv));
		homeStats.setLast5Count(sonMaclarHome.size());
		homeStats.calculateAvgPointsPerMatch();
		homeStats.setAvgGF(getGFandGA(sonMaclarHome, teamEv)[0]);
		homeStats.setAvgGA(getGFandGA(sonMaclarHome, teamEv)[1]);
		homeStats.setRating100(getRating(homeStats.getAvgGF(), homeStats.getAvgGA(), homeStats.getAvgPointsPerMatch()));
		homeStats.setH2hCount(rekabetGecmisi.size());
		homeStats.setH2hWins(getWinCount(rekabetGecmisi, teamEv));
		homeStats.calculateH2hWinRate();

		TeamStats awayStats = new TeamStats();
		awayStats.setLast5Points(getPoints(sonMaclarAway, teamDep));
		awayStats.setLast5Count(sonMaclarAway.size());
		awayStats.calculateAvgPointsPerMatch();
		awayStats.setAvgGF(getGFandGA(sonMaclarAway, teamDep)[0]);
		awayStats.setAvgGA(getGFandGA(sonMaclarAway, teamDep)[1]);
		awayStats.setRating100(getRating(awayStats.getAvgGF(), awayStats.getAvgGA(), awayStats.getAvgPointsPerMatch()));
		awayStats.setH2hCount(rekabetGecmisi.size());
		awayStats.setH2hWins(getWinCount(rekabetGecmisi, teamDep));
		awayStats.calculateH2hWinRate();

		currentMatch.setHomeStats(homeStats);
		currentMatch.setAwayStats(awayStats);

		return currentMatch;
	}

	private int getWinCount(List<MatchResult> macResult, String teamName) {
		int winCount = 0;

		for (int i = 0; i < macResult.size(); i++) {
			if (macResult.get(i).getHomeTeam().contains(teamName) && macResult.get(i).getResult() == "H") {
				winCount++;
			} else if (macResult.get(i).getAwayTeam().contains(teamName) && macResult.get(i).getResult() == "A") {
				winCount++;
			}
		}

		return winCount;
	}

	public int getPoints(List<MatchResult> macResult, String teamName) {
		int points = 0;

		for (int i = 0; i < macResult.size(); i++) {
			if (macResult.get(i).getHomeTeam().contains(teamName) && macResult.get(i).getResult() == "H") {
				points += 3;
			} else if (macResult.get(i).getAwayTeam().contains(teamName) && macResult.get(i).getResult() == "A") {
				points += 3;
			} else if (macResult.get(i).getResult() == "D") {
				points += 1;
			}
		}

		return points;
	}

	public double[] getGFandGA(List<MatchResult> macResult, String teamName) {
		double[] goals = { 0.0, 0.0 };

		int size = macResult.size();

		for (int i = 0; i < size; i++) {
			if (macResult.get(i).getHomeTeam().contains(teamName)) {
				goals[0] += macResult.get(i).getHomeScore();
				goals[1] += macResult.get(i).getAwayScore();
			} else if (macResult.get(i).getAwayTeam().contains(teamName)) {
				goals[1] += macResult.get(i).getHomeScore();
				goals[0] += macResult.get(i).getAwayScore();
			}
		}

		if (size != 0) {
			goals[0] /= size;
			goals[1] /= size;
		}

		return goals;
	}

	public int getRating(double gf, double ga, double ppg) {
		double share = gf + ga > 0 ? (gf / (gf + ga)) : 0.5;
		double rating = (ppg / 3.0) * 60.0 + share * 40.0;

		return (int) Math.round(clamp(rating, 0, 100));
	}

	private static double clamp(double v, double lo, double hi) {
		return Math.max(lo, Math.min(hi, v));
	}

	public String getMax() {
		String maxStr = "";
		double max = 0.0;

		if (getMs1() > max) {
			maxStr = "MS1";
			max = getMs1();
		}
		if (getMs0() > max) {
			maxStr = "MSX";
			max = getMs0();
		}
		if (getMs2() > max) {
			maxStr = "MS2";
			max = getMs2();
		}
		if (getAlt() > max) {
			maxStr = "Alt";
			max = getAlt();
		}
		if (getUst() > max) {
			maxStr = "Üst";
			max = getUst();
		}
		if (getVar() > max) {
			maxStr = "Var";
			max = getVar();
		}
		if (getYok() > max) {
			maxStr = "Yok";
			max = getYok();
		}

		return maxStr;
	}

	@Override
	public String toString() {
		return String.format(
				"%s: %d maç (Rekabet: %d, Son Maçlar Home: %d, Son Maçlar Away: %d) - G:%d B:%d M:%d (%.1f%%)",
				teamName, getTotalMatches(), rekabetGecmisi.size(), sonMaclarHome.size(), sonMaclarAway.size(),
				getWinCount(), getDrawCount(), getLossCount(), getWinRate());
	}
}
