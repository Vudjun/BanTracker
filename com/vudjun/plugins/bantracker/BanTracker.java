package com.vudjun.plugins.bantracker;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerLoginEvent.Result;
import org.bukkit.plugin.java.JavaPlugin;

public class BanTracker extends JavaPlugin implements Listener {
	public ArrayList<PlayerFile> playerFiles;

	@Override
	public void onEnable() {
		getDataFolder().mkdirs();
		File playersFile = new File(getDataFolder(), "playerdata");
		if (playersFile.exists()) {
			try {
				load(playersFile);
			} catch (IOException e) {
				playersFile.renameTo(new File(getDataFolder(), "playerdata.old." + new Random().nextInt(1000000)));
				e.printStackTrace();
				playerFiles = new ArrayList<PlayerFile>(64);
			}
		} else {
			playerFiles = new ArrayList<PlayerFile>(64);
		}
		Bukkit.getPluginManager().registerEvents(this, this);
	}

	private void load(File file) throws IOException {
		DataInputStream in = new DataInputStream(new FileInputStream(file));
		int version = in.readInt();
		if (version != 1) {
			in.close();
			throw new IOException("Could not read version too high: " + version);
		}
		int playerCount = in.readInt();
		playerFiles = new ArrayList<PlayerFile>(playerCount + 64);
		for (int i = 0; i < playerCount; i++) {
			PlayerFile pFile = new PlayerFile(in.readUTF());
			int ipCount = in.readInt();
			pFile.ips = new ArrayList<String>(ipCount);
			for (int j = 0; j < ipCount; j++) {
				pFile.ips.add(in.readUTF());
			}
			int altCount = in.readInt();
			pFile.alts = new ArrayList<String>(altCount);
			for (int j = 0; j < altCount; j++) {
				pFile.alts.add(in.readUTF());
			}
			if (ipCount != 0 || altCount != 0) {
				playerFiles.add(pFile);
			}
		}
		in.close();
	}

	public List<PlayerFile> getPlayerFilesFromIP(String ip) {
		ArrayList<PlayerFile> fileList = new ArrayList<PlayerFile>(16);
		for (PlayerFile playerFile : playerFiles) {
			if (playerFile.ips.contains(ip)) {
				fileList.add(playerFile);
			}
		}
		return fileList;
	}

	public PlayerFile getPlayerFile(String player) {
		for (PlayerFile playerFile : playerFiles) {
			if (player.equals(playerFile.name)) {
				return playerFile;
			}
		}
		PlayerFile file = new PlayerFile(player);
		file.alts = new ArrayList<String>();
		file.ips = new ArrayList<String>();
		playerFiles.add(file);
		return file;
	}

	public List<String> getPlayers(String ip) {
		ArrayList<String> ips = new ArrayList<String>(16);
		for (PlayerFile playerFile : playerFiles) {
			if (playerFile.ips.contains(ip)) {
				ips.add(ip);
			}
		}
		return ips;
	}

	public List<String> getIPs(String player) {
		PlayerFile file = getPlayerFile(player);
		return file.ips;
	}

	public void getAltData(CommandSender sender, String player) {
		sender.sendMessage(ChatColor.YELLOW + "Alt data for '" + player + "'");
		String bannedFrom = null;
		String nameList = "";
		PlayerFile file = getPlayerFile(player);
		for (String xPlayer : file.alts) {
			nameList += ", " + xPlayer;
			OfflinePlayer oPlayer = Bukkit.getOfflinePlayer(xPlayer);
			if (oPlayer.isBanned()) {
				if (bannedFrom == null) {
					bannedFrom = oPlayer.getName();
				}
				else {
					bannedFrom += ", " + oPlayer.getName();
				}
			}
		}
		if (nameList.isEmpty()) {
			nameList = ", ";
		}
		nameList = ChatColor.LIGHT_PURPLE + "Alts (" + file.alts.size() + "): " + nameList.substring(2);
		sender.sendMessage(nameList);
		String ipList = "";
		for (String xIP : file.ips) {
			ipList += ", " + xIP;
		}
		if (ipList.isEmpty()) {
			ipList = ", ";
		}
		ipList = ChatColor.YELLOW + "IPs (" + file.ips.size() + "): " + ipList.substring(2);
		sender.sendMessage(ipList);
		if (bannedFrom != null) {
			sender.sendMessage(ChatColor.RED + "Bans: " + bannedFrom);
		}
	}

	public boolean syncFiles(PlayerFile a, PlayerFile b) {
		boolean changesMade = false;
		for (String alt : a.alts) {
			if (!b.alts.contains(alt)) {
				b.alts.add(alt);
				changesMade = true;
			}
		}
		for (String alt : b.alts) {
			if (!a.alts.contains(alt)) {
				a.alts.add(alt);
				changesMade = true;
			}
		}
		return changesMade;
	}

	@EventHandler(priority = EventPriority.HIGH)
	public void onPlayerLogin(PlayerLoginEvent evt) {
		if (evt.getResult() != Result.ALLOWED) {
			return;
		}
		String playerName = evt.getPlayer().getName();
		PlayerFile file = getPlayerFile(playerName);
		String ip = evt.getAddress().getHostAddress();
		if (!file.alts.contains(playerName)) {
			file.alts.add(playerName);
		}
		if (!file.ips.contains(ip)) {
			file.ips.add(ip);
		}
		boolean changesMade = true;
		while (changesMade) {
			changesMade = false;
			for (PlayerFile pFile : playerFiles) {
				boolean containsAlt = pFile.alts.contains(playerName);
				boolean containsIP = pFile.ips.contains(ip);
				if ((containsAlt || containsIP) && file != pFile) {
					changesMade = (changesMade || syncFiles(file, pFile));
				}
			}
		}
		String bannedFrom = null;
		for (String alt : file.alts) {
			OfflinePlayer oPlayer = Bukkit.getOfflinePlayer(alt);
			if (oPlayer.isBanned()) {
				if (bannedFrom == null) {
					bannedFrom = oPlayer.getName();
				}
				else {
					bannedFrom += ", " + oPlayer.getName();
				}
			}
		}
		try {
			save(new File(getDataFolder(), "playerdata"));
		} catch (IOException e) {
			e.printStackTrace();
		}
		if (bannedFrom != null) {
			evt.disallow(Result.KICK_OTHER, "Ban carried over from: " + bannedFrom);
		}
	}

	private void save(File file) throws IOException {
		file.createNewFile();
		DataOutputStream out = new DataOutputStream(new FileOutputStream(file));
		out.writeInt(1); // VERSION
		out.writeInt(playerFiles.size());
		for (PlayerFile pFile : playerFiles) {
			out.writeUTF(pFile.name);
			out.writeInt(pFile.ips.size());
			for (String ip : pFile.ips) {
				out.writeUTF(ip);
			}
			out.writeInt(pFile.alts.size());
			for (String alt : pFile.alts) {
				out.writeUTF(alt);
			}
		}
		out.close();
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (args.length != 1) {
			return false;
		}
		try {
			getAltData(sender, args[0]);
		} catch (Exception e) {
			e.printStackTrace();
			sender.sendMessage(ChatColor.RED + "Error: " + e.getMessage());
		}
		return true;
	}
}
