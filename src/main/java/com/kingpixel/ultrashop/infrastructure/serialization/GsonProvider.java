package com.kingpixel.ultrashop.infrastructure.serialization;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.kingpixel.ultrashop.domain.model.shop.Shop;
import com.kingpixel.ultrashop.domain.scheduler.Scheduler;
import com.kingpixel.ultrashop.infrastructure.serialization.scheduler.SchedulerJsonAdapter;
import com.kingpixel.ultrashop.infrastructure.serialization.shop.ShopTypeAdapterFactory;

/**
 * Centralized factory for the project's {@link Gson} instance.
 *
 * <p>Wires together the polymorphic {@link Shop} and {@link Scheduler} adapters
 * so any caller using {@link #gson()} gets transparent (de)serialization of the
 * sealed hierarchies — including legacy-format compatibility for existing
 * production configs.</p>
 *
 * <p>Use this in place of {@code new Gson()} or {@code UtilsFile.getGson()} when
 * reading/writing shop or scheduler JSON.</p>
 */
public final class GsonProvider {

  private static final Gson INSTANCE = new GsonBuilder()
    .registerTypeAdapter(Shop.class, new ShopTypeAdapterFactory())
    .registerTypeAdapter(Scheduler.class, new SchedulerJsonAdapter())
    .setPrettyPrinting()
    .create();

  private GsonProvider() {
    // utility
  }

  /** Shared, thread-safe Gson instance. */
  public static Gson gson() {
    return INSTANCE;
  }
}

