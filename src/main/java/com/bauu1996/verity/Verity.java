package com.bauu1996.verity;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Verity implements ModInitializer {
	public static final String MOD_ID = "verity";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Verity cargado");

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			handler.getPlayer().sendSystemMessage(
				Component.literal("¡Hola! Soy Verity, tu ayudante personal. :)")
			);
		});
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
