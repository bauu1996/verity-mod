package com.bauu1996.verity.client.render;

import com.bauu1996.verity.Verity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

public class VerityModel extends EntityModel<VerityRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Verity.id("verity"), "main");

	public VerityModel(ModelPart root) {
		super(root);
	}

	public static LayerDefinition getTexturedModelData() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild(
			"body",
			CubeListBuilder.create().texOffs(0, 0).addBox(-6, -6, -6, 12, 12, 12),
			PartPose.offset(0, 18, 0)
		);
		return LayerDefinition.create(mesh, 64, 32);
	}
}
