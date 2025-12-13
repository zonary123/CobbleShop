package com.kingpixel.cobblemarry.command.base;

import com.kingpixel.cobblemarry.CobbleMarry;
import com.kingpixel.cobblemarry.database.DataBaseFactory;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 *
 * @author Carlos Varas Alonso - 13/12/2025 5:38
 */
public class GenderCommand {
  public static void register(LiteralArgumentBuilder<ServerCommandSource> base) {
    base.then(
      CommandManager.literal("gender")
        .then(
          CommandManager.argument("gender", StringArgumentType.string())
            .suggests((context, builder) -> {
              return CommandSource.suggestMatching(CobbleMarry.config.getGenders().keySet(), builder);
            })
            .executes(context -> {
              if (!context.getSource().isExecutedByPlayer()) return 0;
              ServerPlayerEntity player = context.getSource().getPlayer();
              if (player == null) return 0;
              var gender = StringArgumentType.getString(context, "gender");
              var userInfo = DataBaseFactory.INSTANCE.getUserInfoCached(player.getUuid());
              if (userInfo == null) return 0;
              userInfo.setGender(gender);
              CobbleMarry.runAsync(() -> DataBaseFactory.INSTANCE.updateUserInfo(userInfo));
              return 1;
            })
        )
    );
  }
}
