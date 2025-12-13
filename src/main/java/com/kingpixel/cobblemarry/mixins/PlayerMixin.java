package com.kingpixel.cobblemarry.mixins;

import com.kingpixel.cobblemarry.database.DataBaseFactory;
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

import java.util.concurrent.TimeUnit;

/**
 *
 * @author Carlos Varas Alonso - 13/12/2025 6:07
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerMixin {
  @Unique private long timeStamp = 0;

  @Inject(method = "interact", at = @At("HEAD"))
  private void interact(Entity entity, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
    if (!(entity instanceof ServerPlayerEntity interact)) return;
    ServerPlayerEntity self = (ServerPlayerEntity) (Object) this;

    var userinfo = DataBaseFactory.INSTANCE.getUserInfoCached(self.getUuid());
    if (userinfo == null) return;
    if (!userinfo.isMarriedTo(self)) return;
    if (timeStamp + TimeUnit.SECONDS.toMillis(2) > System.currentTimeMillis()) return;
    timeStamp = System.currentTimeMillis();
    // Particles
    SimpleParticleType heart = ParticleTypes.HEART;

    // Cantidad de partículas y radio alrededor del jugador
    int particleCount = 20;
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
        0, 0, 0,
        0
      );
    }
  }

}
