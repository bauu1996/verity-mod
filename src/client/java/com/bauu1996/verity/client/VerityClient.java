package com.bauu1996.verity.client;

import com.bauu1996.verity.client.ai.VerityBrain;
import com.bauu1996.verity.client.render.VerityModel;
import com.bauu1996.verity.client.render.VerityRenderer;
import com.bauu1996.verity.entity.ModEntityTypes;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.renderer.entity.EntityRenderers;

public class VerityClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ModelLayerRegistry.registerModelLayer(VerityModel.LAYER, VerityModel::getTexturedModelData);
		EntityRenderers.register(ModEntityTypes.VERITY, VerityRenderer::new);

		ClientSendMessageEvents.CHAT.register(message -> {
			String text = message.trim();
			if (!text.toLowerCase().startsWith("verity")) {
				return;
			}
			String rest = text.substring(6).replaceFirst("^[,:;!\\s]+", "");
			if (rest.equalsIgnoreCase("escuchar")) {
				boolean on = VerityBrain.toggleListening();
				VerityBrain.notice(on ? "Te estoy escuchando. Hablá normal." : "Dejé de escucharte.");
			} else if (!rest.isEmpty()) {
				VerityBrain.ask(rest);
			}
		});
	}
}
