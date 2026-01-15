package net.hunter9649.ghostliferewind;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Formatting;
import net.minecraft.world.GameMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.UUID;

public class GhostLifeRewind implements ModInitializer {
	public static final String MOD_ID = "ghostliferewind";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private final HashMap<UUID, Integer> lives = new HashMap<>();

	@Override
	public void onInitialize() {

		// When a player joins, create a team with their exact name
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayerEntity player = handler.getPlayer();
			UUID id = player.getUuid();

			lives.putIfAbsent(id, 8); //set lives to 8

			updatePlayerColor(player, server);
		});

		//player death
		ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
			if(!alive) {
				UUID id = oldPlayer.getUuid();
				int currentLives = lives.getOrDefault(id, 8);

				currentLives -= 1;
				lives.put(id,currentLives);

				if(currentLives <= 0) {
					newPlayer.changeGameMode(GameMode.SPECTATOR);
				}
			}
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				updatePlayerColor(player, server);
			}
		});

	}

	private void updatePlayerColor(ServerPlayerEntity player, MinecraftServer server) {
		ServerScoreboard scoreboard = server.getScoreboard();
		String playerName = player.getName().getString();
		UUID id = player.getUuid();

		int lifeCount = lives.getOrDefault(id, 8);

		// Determine which team the player should be in
		String teamName;
		Formatting color;

		if (lifeCount >= 5) {
			teamName = playerName + "_DG";
			color = Formatting.DARK_GREEN;
		} else if (lifeCount == 4) {
			teamName = playerName + "_G";
			color = Formatting.GREEN;
		} else if (lifeCount == 3) {
			teamName = playerName + "_Y";
			color = Formatting.YELLOW;
		} else if (lifeCount == 2) {
			teamName = playerName + "_R";
			color = Formatting.RED;
		} else if (lifeCount == 1) {
			teamName = playerName + "_GH";
			color = Formatting.GRAY;
		} else {
			teamName = playerName + "_D";
			color = Formatting.DARK_GRAY;
		}

		// Remove from any old team
		Team oldTeam = scoreboard.getScoreHolderTeam(playerName);
		if (oldTeam != null) {
			scoreboard.removeScoreHolderFromTeam(playerName, oldTeam);
		}

		// Create or get the correct team
		Team team = scoreboard.getTeam(teamName);
		if (team == null) {
			team = scoreboard.addTeam(teamName);
		}

		// Set the color
		team.setColor(color);

		// Add player to team
		scoreboard.addScoreHolderToTeam(playerName, team);
	}
}
