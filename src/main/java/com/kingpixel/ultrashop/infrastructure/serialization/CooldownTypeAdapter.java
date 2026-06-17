package com.kingpixel.ultrashop.infrastructure.serialization;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;

/**
 * Type adapter for Gson that converts old integer product cooldowns (minutes)
 * to strings with "m" suffix, and reads normal string cooldowns natively.
 */
public class CooldownTypeAdapter extends TypeAdapter<String> {
  @Override
  public void write(JsonWriter out, String value) throws IOException {
    if (value == null) {
      out.nullValue();
    } else {
      out.value(value);
    }
  }

  @Override
  public String read(JsonReader in) throws IOException {
    if (in.peek() == JsonToken.NULL) {
      in.nextNull();
      return null;
    }
    if (in.peek() == JsonToken.NUMBER) {
      // Old configs represented cooldown as integer minutes.
      // E.g. 60 -> "60m"
      int minutes = in.nextInt();
      return minutes + "m";
    }
    return in.nextString();
  }
}
