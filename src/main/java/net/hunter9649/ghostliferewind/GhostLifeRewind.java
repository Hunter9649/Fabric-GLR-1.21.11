package net.hunter9649.ghostliferewind;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.GameMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.UUID;

class GLPlayer {

	private UUID id;
	private int lives;
	private boolean boogey = false;

	public GLPlayer(UUID id, int lives) {
		this.id = id;
		this.lives = lives;
	}

	public int getLives() {
		return lives;
	}

	public void setLives(int lives) {
		this.lives = lives;
	}

	public boolean getBoogey() {
		return boogey;
	}

	public void setBoogey(boolean boogey) {
		this.boogey = boogey;
	}
}

public class GhostLifeRewind implements ModInitializer {
	public static final String MOD_ID = "ghostliferewind";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private final HashMap<UUID, GLPlayer> players = new HashMap<>();

	public static boolean isBloodMoon = true;

	@Override
	public void onInitialize() {

		// When a player joins, create a team with their exact name
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayerEntity player = handler.getPlayer();
			UUID id = player.getUuid();

			players.putIfAbsent(id, new GLPlayer(id, 5)); //create player profile and set lives to 5

			updatePlayerColor(player, server);
		});

		//player death
		ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
			if(!alive) {
				UUID id = oldPlayer.getUuid();
				GLPlayer playerProfile = players.get(id);
				int currentLives = playerProfile.getLives();

				currentLives -= 1;
				playerProfile.setLives(currentLives);

				if (currentLives == 1) {
					newPlayer.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(40.0f);
					newPlayer.setHealth(40.0f);
				}
				if(currentLives <= 0) {
					newPlayer.changeGameMode(GameMode.SPECTATOR);
				}
			}
		});

		//player kills
		ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((world, entity, livingEntity,damageSource) -> {
			if (entity instanceof ServerPlayerEntity victim && livingEntity instanceof ServerPlayerEntity killer) {
				// Now you know a player killed another player
				killer.sendMessage(Text.literal("+1 Life").formatted(Formatting.GREEN), true);
			}
		});


		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				updatePlayerHearts(player);
				updatePlayerColor(player, server);
			}
		});

	}

	private void updatePlayerHearts(ServerPlayerEntity player) {
		UUID id = player.getUuid();

		GLPlayer playerProfile = players.get(id);
		int lifeCount = playerProfile.getLives();
		float health = player.getHealth();

		if (lifeCount > 1) {
			player.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(20.0f);
		} else if (lifeCount == 1) {
			player.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(health);
		}
	}

	private void updatePlayerColor(ServerPlayerEntity player, MinecraftServer server) {
		ServerScoreboard scoreboard = server.getScoreboard();
		String playerName = player.getName().getString();
		UUID id = player.getUuid();

		GLPlayer playerProfile = players.get(id);
		int lifeCount = playerProfile.getLives();

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
