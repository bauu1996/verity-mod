package com.bauu1996.verity.entity;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.EnumSet;

public class VerityEntity extends PathfinderMob {
	public VerityEntity(EntityType<? extends VerityEntity> entityType, Level level) {
		super(entityType, level);
		this.setCustomName(Component.literal("Verity"));
		this.setCustomNameVisible(true);
		this.setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes()
			.add(Attributes.MAX_HEALTH, 1000)
			.add(Attributes.MOVEMENT_SPEED, 0.35);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new FollowNearestPlayerGoal(this, 1.15, 3.0f, 6.0f));
		this.goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 10.0f));
		this.goalSelector.addGoal(3, new RandomLookAroundGoal(this));
	}

	static class FollowNearestPlayerGoal extends Goal {
		private final PathfinderMob mob;
		private final double speed;
		private final float stopDistance;
		private final float startDistance;
		private Player target;

		FollowNearestPlayerGoal(PathfinderMob mob, double speed, float stopDistance, float startDistance) {
			this.mob = mob;
			this.speed = speed;
			this.stopDistance = stopDistance;
			this.startDistance = startDistance;
			this.setFlags(EnumSet.of(Goal.Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			Player player = this.mob.level().getNearestPlayer(this.mob, 64.0);
			if (player == null || player.isSpectator()) {
				return false;
			}
			if (this.mob.distanceToSqr(player) < (double) (this.startDistance * this.startDistance)) {
				return false;
			}
			this.target = player;
			return true;
		}

		@Override
		public boolean canContinueToUse() {
			return this.target != null
				&& this.target.isAlive()
				&& this.mob.distanceToSqr(this.target) > (double) (this.stopDistance * this.stopDistance);
		}

		@Override
		public void tick() {
			if (this.target == null) {
				return;
			}
			if (this.mob.distanceToSqr(this.target) > 40.0 * 40.0) {
				this.mob.setPos(this.target.getX(), this.target.getY(), this.target.getZ());
				return;
			}
			if (this.mob.tickCount % 10 == 0) {
				this.mob.getNavigation().moveTo(this.target, this.speed);
			}
		}

		@Override
		public void stop() {
			this.target = null;
			this.mob.getNavigation().stop();
		}
	}
}
