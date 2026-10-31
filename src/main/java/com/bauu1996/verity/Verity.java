package com.bauu1996.verity;

import com.bauu1996.verity.entity.ModEntityTypes;
import com.bauu1996.verity.entity.VerityEntity;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Verity implements ModInitializer {
	public static final String MOD_ID = "verity";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final Map<UUID, Integer> PENDING = new ConcurrentHashMap<>();

	@Override
	public void onInitialize() {
		LOGGER.info("Verity cargado");

		ModEntityTypes.init();

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayer player = handler.getPlayer();
			player.sendSystemMessage(Component.literal(
				"¡Hola! Soy Verity, tu ayudante personal. Escribime \"verity hola\" o usá /verity escuchar para hablarme con el micrófono."
			));
			PENDING.put(player.getUUID(), 60);
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (Map.Entry<UUID, Integer> entry : PENDING.entrySet()) {
				int left = entry.getValue() - 1;
				if (left > 0) {
					entry.setValue(left);
					continue;
				}
				PENDING.remove(entry.getKey());
				ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
				if (player == null) {
					continue;
				}
				if (player.level() instanceof ServerLevel level) {
					boolean exists = !level.getEntitiesOfClass(VerityEntity.class, player.getBoundingBox().inflate(256.0)).isEmpty();
					if (!exists) {
						VerityEntity verity = new VerityEntity(ModEntityTypes.VERITY, level);
						verity.setPos(player.getX() + 1.5, player.getY(), player.getZ() + 1.5);
						level.addFreshEntity(verity);
					}
				}
			}
		});
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
