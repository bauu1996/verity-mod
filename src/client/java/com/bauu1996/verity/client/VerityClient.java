package com.bauu1996.verity.client;

import com.bauu1996.verity.client.ai.VerityBrain;
import com.bauu1996.verity.client.render.VerityModel;
import com.bauu1996.verity.client.render.VerityRenderer;
import com.bauu1996.verity.entity.ModEntityTypes;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.network.chat.Component;

public class VerityClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ModelLayerRegistry.registerModelLayer(VerityModel.LAYER, VerityModel::getTexturedModelData);
		EntityRenderers.register(ModEntityTypes.VERITY, VerityRenderer::new);

		ClientSendMessageEvents.CHAT.register(message -> {
			String text = message.trim();
			if (text.toLowerCase().startsWith("verity")) {
				String question = text.substring(6).replaceFirst("^[,:;!\\s]+", "");
				if (!question.isEmpty()) {
					VerityBrain.ask(question);
				}
			}
		});

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
			dispatcher.register(
				ClientCommandManager.literal("verity")
					.then(ClientCommandManager.literal("escuchar").executes(context -> {
						boolean on = VerityBrain.toggleListening();
						context.getSource().sendFeedback(Component.literal(
							on ? "Verity te está escuchando. Hablá normal." : "Verity dejó de escucharte."
						));
						return 1;
					}))
			)
		);
	}
}
