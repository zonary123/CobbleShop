package com.kingpixel.ultramarry.mixins;

import com.kingpixel.cobbleutils.CobbleUtils;
import com.kingpixel.ultramarry.UltraMarry;
import com.kingpixel.ultramarry.database.DataBaseFactory;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 *
 * @author Carlos Varas Alonso - 13/12/2025 6:07
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerMixin {
  @Unique private long timeStamp = 0;

  @Inject(method = "interact", at = @At("HEAD"))
  private void interact(Entity entity, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
    if (!(entity instanceof ServerPlayerEntity interact)) {
      CobbleUtils.LOGGER.info("Not a server player entity");
      return;
    }
    ServerPlayerEntity self = (ServerPlayerEntity) (Object) this;

    var userinfo = DataBaseFactory.INSTANCE.getUserInfo(self.getUuid());
    if (userinfo == null) {
      CobbleUtils.LOGGER.info("No userinfo found");
      return;
    }
    if (!userinfo.isMarriedTo(interact)) {
      CobbleUtils.LOGGER.info("Not married to this player");
      return;
    }
    if (timeStamp + UltraMarry.config.getCooldown().toMillis() > System.currentTimeMillis()) {
      CobbleUtils.LOGGER.info("Cooldown active");
      return;
    }
    timeStamp = System.currentTimeMillis();
    // Particles
    SimpleParticleType heart = ParticleTypes.HEART;

    // Cantidad de partículas y radio alrededor del jugador
    int particleCount = UltraMarry.config.getAmountParticles();
    double radius = 1.5;

    for (int i = 0; i < particleCount; i++) {
      double offsetX = (self.getRandom().nextDouble() - 0.5) * 2 * radius;
      double offsetY = self.getRandom().nextDouble() * 2; // Altura entre 0 y 2
      double offsetZ = (self.getRandom().nextDouble() - 0.5) * 2 * radius;

      // Enviar partículas al mundo para todos los jugadores cercanos
      self.getServerWorld().spawnParticles(
        heart,
        interact.getX() + offsetX,
        interact.getY() + offsetY,
        interact.getZ() + offsetZ,
        1,
        1, 1, 1,
        0
      );
    }
  }

}
