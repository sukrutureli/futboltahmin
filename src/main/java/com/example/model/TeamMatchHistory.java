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
	}

	public void addRekabetGecmisiMatch(MatchResult match) {
		rekabetGecmisi.add(match);
	}

	public void addSonMacMatch(MatchResult match, int homeOrAway) {
		if (homeOrAway == 1) sonMaclarHome.add(match);
		else if (homeOrAway == 2) sonMaclarAway.add(match);
	}

	public String getTeamName() { return teamName; }
	public String getOriginalMatchUrl() { return originalMatchUrl; }
	public List<MatchResult> getRekabetGecmisi() { return rekabetGecmisi; }
	public List<MatchResult> getSonMaclarAway() { return sonMaclarAway; }
	public List<MatchResult> getSonMaclarHome() { return sonMaclarHome; }
	public String getTeamEv() { return teamEv; }
	public String getTeamDep() { return teamDep; }

	public int getTotalMatches() { return rekabetGecmisi.size() + sonMaclarHome.size() + sonMaclarAway.size(); }

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

	public int getDrawCount() { return (int) getAllMatches().stream().filter(match -> match.getResult().equals("D")).count(); }
	public int getLossCount() { return getTotalMatches() - getWinCount() - getDrawCount(); }
	public double getWinRate() { return getTotalMatches() == 0 ? 0.0 : (double) getWinCount() / getTotalMatches() * 100; }

	public double getMs1() {
		double ms1Rekabet = 0, ms1SonH = 0, ms1SonA = 0;
		for (MatchResult m : rekabetGecmisi) {
			if (m.getHomeTeam().contains(teamEv) && "H".equals(m.getResult())) ms1Rekabet++;
			else if (m.getAwayTeam().contains(teamEv) && "A".equals(m.getResult())) ms1Rekabet++;
		}
		for (MatchResult m : sonMaclarHome) {
			if (m.getHomeTeam().contains(teamEv) && "H".equals(m.getResult())) ms1SonH++;
			else if (m.getAwayTeam().contains(teamEv) && "A".equals(m.getResult())) ms1SonH++;
		}
		for (MatchResult m : sonMaclarAway) {
			if (m.getHomeTeam().contains(teamDep) && "A".equals(m.getResult())) ms1SonA++;
			else if (m.getAwayTeam().contains(teamDep) && "H".equals(m.getResult())) ms1SonA++;
		}
		if (isInfoEnough()) return (ms1Rekabet / rekabetGecmisi.size()) * 0.1 + (ms1SonH / sonMaclarHome.size()) * 0.45 + (ms1SonA / sonMaclarAway.size()) * 0.45;
		if (isInfoEnoughWithoutRekabet()) return (ms1SonH / sonMaclarHome.size()) * 0.5 + (ms1SonA / sonMaclarAway.size()) * 0.5;
		return (ms1Rekabet + ms1SonH + ms1SonA) / getTotalMatches();
	}

	public double getMs2() {
		double ms2Rekabet = 0, ms2SonH = 0, ms2SonA = 0;
		for (MatchResult m : rekabetGecmisi) {
			if (m.getHomeTeam().contains(teamDep) && "H".equals(m.getResult())) ms2Rekabet++;
			else if (m.getAwayTeam().contains(teamDep) && "A".equals(m.getResult())) ms2Rekabet++;
		}
		for (MatchResult m : sonMaclarHome) {
			if (m.getHomeTeam().contains(teamEv) && "A".equals(m.getResult())) ms2SonH++;
			else if (m.getAwayTeam().contains(teamEv) && "H".equals(m.getResult())) ms2SonH++;
		}
		for (MatchResult m : sonMaclarAway) {
			if (m.getHomeTeam().contains(teamDep) && "H".equals(m.getResult())) ms2SonA++;
			else if (m.getAwayTeam().contains(teamDep) && "A".equals(m.getResult())) ms2SonA++;
		}
		if (isInfoEnough()) return (ms2Rekabet / rekabetGecmisi.size()) * 0.1 + (ms2SonH / sonMaclarHome.size()) * 0.45 + (ms2SonA / sonMaclarAway.size()) * 0.45;
		if (isInfoEnoughWithoutRekabet()) return (ms2SonH / sonMaclarHome.size()) * 0.5 + (ms2SonA / sonMaclarAway.size()) * 0.5;
		return (ms2Rekabet + ms2SonH + ms2SonA) / getTotalMatches();
	}

	public double getMs0() { return 1 - getMs1() - getMs2(); }

	public double getVar() {
		double a=0,b=0,c=0;
		for (MatchResult m:rekabetGecmisi) if(m.getHomeScore()>0&&m.getAwayScore()>0)a++;
		for (MatchResult m:sonMaclarHome) if(m.getHomeScore()>0&&m.getAwayScore()>0)b++;
		for (MatchResult m:sonMaclarAway) if(m.getHomeScore()>0&&m.getAwayScore()>0)c++;
		if(isInfoEnough()) return a/rekabetGecmisi.size()*0.1+b/sonMaclarHome.size()*0.45+c/sonMaclarAway.size()*0.45;
		if(isInfoEnoughWithoutRekabet()) return b/sonMaclarHome.size()*0.5+c/sonMaclarAway.size()*0.5;
		return (a+b+c)/getTotalMatches();
	}
	public double getYok(){return 1-getVar();}
	public double getUst(){
		double a=0,b=0,c=0;
		for(MatchResult m:rekabetGecmisi)if(m.getHomeScore()+m.getAwayScore()>2)a++;
		for(MatchResult m:sonMaclarHome)if(m.getHomeScore()+m.getAwayScore()>2)b++;
		for(MatchResult m:sonMaclarAway)if(m.getHomeScore()+m.getAwayScore()>2)c++;
		if(isInfoEnough())return a/rekabetGecmisi.size()*0.1+b/sonMaclarHome.size()*0.45+c/sonMaclarAway.size()*0.45;
		if(isInfoEnoughWithoutRekabet())return b/sonMaclarHome.size()*0.5+c/sonMaclarAway.size()*0.5;
		return(a+b+c)/getTotalMatches();
	}
	public double getAlt(){return 1-getUst();}
	public boolean isInfoEnough(){return sonMaclarHome.size()>=2&&sonMaclarAway.size()>=2&&rekabetGecmisi.size()>=2;}
	public boolean isInfoEnoughWithoutRekabet(){return sonMaclarHome.size()>1&&sonMaclarAway.size()>1&&rekabetGecmisi.size()<2;}

	public String getStyle(String type, Double oddValue) {
		String color="background-color:#e8fbe8; border:1px solid #6ecf6e;";
		if(!isInfoEnough()&&!isInfoEnoughWithoutRekabet())return "";
		if(oddValue>1.0&&type.equals(getMax()))return color;
		return "";
	}

	public Match createMatch(MatchInfo pMatch) { return new Match(teamEv, teamDep); }
	public String getMax() {
		double max=Math.max(Math.max(getMs1(),getMs0()),Math.max(getMs2(),Math.max(getAlt(),Math.max(getUst(),Math.max(getVar(),getYok())))));
		if(max==getMs1())return "MS1"; if(max==getMs0())return "MS0"; if(max==getMs2())return "MS2"; if(max==getAlt())return "Alt"; if(max==getUst())return "Üst"; if(max==getVar())return "Var"; return "Yok";
	}
}