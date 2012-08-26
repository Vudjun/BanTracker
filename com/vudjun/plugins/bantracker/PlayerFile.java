package com.vudjun.plugins.bantracker;

import java.util.ArrayList;

public class PlayerFile {
	public final String name;
	public ArrayList<String> ips;
	public ArrayList<String> alts;
	
	public PlayerFile(String name) {
		this.name = name;
	}
}
