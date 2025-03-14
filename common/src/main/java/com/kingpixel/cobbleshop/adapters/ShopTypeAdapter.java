package com.kingpixel.cobbleshop.adapters;

import com.google.gson.*;
import com.kingpixel.cobbleshop.models.TypeShop;

import java.lang.reflect.Type;

/**
 * @author Carlos Varas Alonso - 21/02/2025 18:48
 */
public class ShopTypeAdapter implements JsonDeserializer<ShopType> {
  public static ShopTypeAdapter INSTANCE = new ShopTypeAdapter();

  @Override
  public ShopType deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
    JsonObject jsonObject = json.getAsJsonObject();
    JsonElement typeShopElement = jsonObject.get("typeShop");

    if (typeShopElement == null || typeShopElement.getAsString().isEmpty()) {
      throw new JsonParseException("Missing or empty 'typeShop' field");
    }

    String typeShop = typeShopElement.getAsString();

    return switch (TypeShop.valueOf(typeShop)) {
      case PERMANENT -> context.deserialize(jsonObject, ShopTypePermanent.class);
      case DYNAMIC -> context.deserialize(jsonObject, ShopTypeDynamic.class);
      case WEEKLY -> context.deserialize(jsonObject, ShopTypeWeekly.class);
      case DYNAMIC_WEEKLY -> context.deserialize(jsonObject, ShopTypeDynamicWeekly.class);
      case CALENDAR -> context.deserialize(jsonObject, ShopTypeCalendar.class);
      case DYNAMIC_CALENDAR -> context.deserialize(jsonObject, ShopTypeDynamicCalendar.class);
      default -> throw new JsonParseException("Unknown type: " + typeShop);
    };
  }
}
