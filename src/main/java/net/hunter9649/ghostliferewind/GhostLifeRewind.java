package net.hunter9649.ghostliferewind;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.GameMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

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
	private static final float LIFE_PROMOTION_HEALTH_THRESHOLD = 60.0f;
	private static final long CLAIMKILL_EXPIRY_MILLIS = TimeUnit.MINUTES.toMillis(20);

	private final HashMap<UUID, GLPlayer> players = new HashMap<>();
	private final ArrayList<ClaimableKill> claimableKills = new ArrayList<>();

	public static boolean isBloodMoon = true;

	@Override
	public void onInitialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
				CommandManager.literal("lives")
						.requires(CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK))
						.then(CommandManager.literal("add")
								.then(CommandManager.argument("player", net.minecraft.command.argument.EntityArgumentType.player())
										.then(CommandManager.argument("amount", IntegerArgumentType.integer())
												.executes(context -> executeLivesCommand(context, LivesOperation.ADD)))))
						.then(CommandManager.literal("remove")
								.then(CommandManager.argument("player", net.minecraft.command.argument.EntityArgumentType.player())
										.then(CommandManager.argument("amount", IntegerArgumentType.integer())
												.executes(context -> executeLivesCommand(context, LivesOperation.REMOVE)))))
						.then(CommandManager.literal("set")
								.then(CommandManager.argument("player", net.minecraft.command.argument.EntityArgumentType.player())
										.then(CommandManager.argument("amount", IntegerArgumentType.integer())
												.executes(context -> executeLivesCommand(context, LivesOperation.SET)))))
		));
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
				CommandManager.literal("hearts")
						.requires(CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK))
						.then(CommandManager.literal("add")
								.then(CommandManager.argument("player", net.minecraft.command.argument.EntityArgumentType.player())
										.then(CommandManager.argument("amount", IntegerArgumentType.integer(0))
												.executes(context -> executeHeartsCommand(context, HeartsOperation.ADD)))))
						.then(CommandManager.literal("remove")
								.then(CommandManager.argument("player", net.minecraft.command.argument.EntityArgumentType.player())
										.then(CommandManager.argument("amount", IntegerArgumentType.integer(0))
												.executes(context -> executeHeartsCommand(context, HeartsOperation.REMOVE)))))
						.then(CommandManager.literal("set")
								.then(CommandManager.argument("player", net.minecraft.command.argument.EntityArgumentType.player())
										.then(CommandManager.argument("amount", IntegerArgumentType.integer(1))
												.executes(context -> executeHeartsCommand(context, HeartsOperation.SET)))))
		));
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
				CommandManager.literal("claimkill")
						.then(CommandManager.argument("player", StringArgumentType.word())
								.suggests((context, builder) -> {
									removeExpiredClaimableKills();
									for (ClaimableKill kill : claimableKills) {
										builder.suggest(kill.victimName);
									}
									return builder.buildFuture();
								})
								.executes(this::executeClaimKillCommand))
		));

		// When a player joins, create a team with their exact name
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayerEntity player = handler.getPlayer();
			UUID id = player.getUuid();

			players.putIfAbsent(id, new GLPlayer(id, 8)); //create player profile and set lives to 5

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
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
			if (!(entity instanceof ServerPlayerEntity victim)) {
				return;
			}
			GLPlayer victimProfile = players.get(victim.getUuid());
			if (victimProfile == null) {
				return;
			}
			claimableKills.add(new ClaimableKill(
					victim.getUuid(),
					victim.getName().getString(),
					victimProfile.getLives(),
					System.currentTimeMillis()
			));
		});


		ServerTickEvents.END_SERVER_TICK.register(server -> {
			removeExpiredClaimableKills();
			for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				promoteFromOneLifeIfThresholdReached(player);
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

	private void showTitle(ServerPlayerEntity player, String message) {
		player.networkHandler.sendPacket(new TitleFadeS2CPacket(5, 30, 10));
		player.networkHandler.sendPacket(new TitleS2CPacket(Text.literal(message).formatted(Formatting.GREEN)));
	}

	private int executeClaimKillCommand(CommandContext<ServerCommandSource> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayerEntity claimer = context.getSource().getPlayerOrThrow();
		String victimName = StringArgumentType.getString(context, "player");

		removeExpiredClaimableKills();
		ClaimableKill claimedKill = null;
		for (ClaimableKill kill : claimableKills) {
			if (kill.victimName.equalsIgnoreCase(victimName)) {
				claimedKill = kill;
				break;
			}
		}

		if (claimedKill == null) {
			context.getSource().sendError(Text.literal("That player is not in the claim list."));
			return 0;
		}

		claimableKills.remove(claimedKill);
		applyKillCredit(claimer, claimedKill.victimLivesAtDeath, context.getSource().getServer());
		String claimedVictimName = claimedKill.victimName;

		String message = claimer.getName().getString() + " has claimed a kill from " + claimedVictimName + ".";
		var livesNode = context.getSource().getServer().getCommandManager().getDispatcher().getRoot().getChild("lives");
		for (ServerPlayerEntity onlinePlayer : context.getSource().getServer().getPlayerManager().getPlayerList()) {
			if (livesNode != null && livesNode.canUse(onlinePlayer.getCommandSource())) {
				onlinePlayer.sendMessage(Text.literal(message), false);
			}
		}

		context.getSource().sendFeedback(() -> Text.literal("Claimed kill from " + claimedVictimName), false);
		return 1;
	}

	private void applyKillCredit(ServerPlayerEntity killer, int victimLivesAtDeath, MinecraftServer server) {
		GLPlayer killerProfile = players.computeIfAbsent(killer.getUuid(), uuid -> new GLPlayer(uuid, 8));
		int killerLives = killerProfile.getLives();

		if (killerLives == 3 && victimLivesAtDeath >= 5) {
			killerProfile.setLives(killerLives + 1);
			showTitle(killer, "+1 Life");
		} else if (killerLives == 2 && victimLivesAtDeath >= 4) {
			killerProfile.setLives(killerLives + 1);
			showTitle(killer, "+1 Life");
		} else if (killerLives == 1) {
			float newHealth = killer.getHealth() + 20.0f;
			killer.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(Math.max(killer.getMaxHealth(), newHealth));
			killer.setHealth(newHealth);

			if (!promoteFromOneLifeIfThresholdReached(killer)) {
				showTitle(killer, "+10 Hearts");
			}
		}

		updatePlayerHearts(killer);
		updatePlayerColor(killer, server);
	}

	private void removeExpiredClaimableKills() {
		long now = System.currentTimeMillis();
		Iterator<ClaimableKill> iterator = claimableKills.iterator();
		while (iterator.hasNext()) {
			ClaimableKill kill = iterator.next();
			if (now - kill.createdAtMillis > CLAIMKILL_EXPIRY_MILLIS) {
				iterator.remove();
			}
		}
	}

	private int executeLivesCommand(CommandContext<ServerCommandSource> context, LivesOperation operation) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayerEntity target = net.minecraft.command.argument.EntityArgumentType.getPlayer(context, "player");
		int amount = IntegerArgumentType.getInteger(context, "amount");

		GLPlayer profile = players.computeIfAbsent(target.getUuid(), uuid -> new GLPlayer(uuid, 8));
		int currentLives = profile.getLives();
		int updatedLives = switch (operation) {
			case ADD -> currentLives + amount;
			case REMOVE -> currentLives - amount;
			case SET -> amount;
		};

		profile.setLives(updatedLives);
		updatePlayerHearts(target);
		updatePlayerColor(target, context.getSource().getServer());

		if (updatedLives <= 0) {
			target.changeGameMode(GameMode.SPECTATOR);
		}

		context.getSource().sendFeedback(
				() -> Text.literal("Set " + target.getName().getString() + "'s lives to " + updatedLives),
				true
		);

		return updatedLives;
	}

	private int executeHeartsCommand(CommandContext<ServerCommandSource> context, HeartsOperation operation) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayerEntity target = net.minecraft.command.argument.EntityArgumentType.getPlayer(context, "player");
		int amount = IntegerArgumentType.getInteger(context, "amount");
		GLPlayer profile = players.computeIfAbsent(target.getUuid(), uuid -> new GLPlayer(uuid, 8));

		if (profile.getLives() != 1) {
			context.getSource().sendError(Text.literal(target.getName().getString() + " is not on 1 life."));
			return 0;
		}

		float currentMaxHealth = target.getMaxHealth();
		float updatedMaxHealth = switch (operation) {
			case ADD -> currentMaxHealth + amount;
			case REMOVE -> currentMaxHealth - amount;
			case SET -> amount;
		};

		updatedMaxHealth = Math.max(1.0f, updatedMaxHealth);
		int finalMaxHealth = (int) updatedMaxHealth;
		target.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(updatedMaxHealth);
		target.setHealth(updatedMaxHealth);
		boolean promoted = promoteFromOneLifeIfThresholdReached(target);

		context.getSource().sendFeedback(
				() -> Text.literal(promoted
						? target.getName().getString() + " reached 30 hearts and gained +1 Life."
						: "Set " + target.getName().getString() + "'s max health to " + finalMaxHealth),
				true
		);

		return promoted ? 2 : finalMaxHealth;
	}

	private boolean promoteFromOneLifeIfThresholdReached(ServerPlayerEntity player) {
		GLPlayer profile = players.get(player.getUuid());
		if (profile == null || profile.getLives() != 1 || player.getMaxHealth() < LIFE_PROMOTION_HEALTH_THRESHOLD) {
			return false;
		}

		profile.setLives(2);
		player.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(20.0f);
		player.setHealth(20.0f);
		showTitle(player, "+1 Life");
		return true;
	}

	private enum LivesOperation {
		ADD,
		REMOVE,
		SET
	}

	private enum HeartsOperation {
		ADD,
		REMOVE,
		SET
	}

	private static class ClaimableKill {
		private final UUID victimUuid;
		private final String victimName;
		private final int victimLivesAtDeath;
		private final long createdAtMillis;

		private ClaimableKill(UUID victimUuid, String victimName, int victimLivesAtDeath, long createdAtMillis) {
			this.victimUuid = victimUuid;
			this.victimName = victimName;
			this.victimLivesAtDeath = victimLivesAtDeath;
			this.createdAtMillis = createdAtMillis;
		}
	}
}
