package com.bauu1996.verity.client.render;

import com.bauu1996.verity.entity.VerityEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

public class VerityRenderer extends MobRenderer<VerityEntity, VerityRenderState, VerityModel> {
	private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("minecraft", "textures/block/yellow_concrete.png");

	public VerityRenderer(EntityRendererProvider.Context context) {
		super(context, new VerityModel(context.bakeLayer(VerityModel.LAYER)), 0.4f);
	}

	@Override
	public VerityRenderState createRenderState() {
		return new VerityRenderState();
	}

	@Override
	public Identifier getTextureLocation(VerityRenderState state) {
		return TEXTURE;
	}
}
