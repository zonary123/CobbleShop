package com.kingpixel.ultrashop.infrastructure.persistence.json;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.kingpixel.cobbleutils.CobbleUtils;
import com.kingpixel.cobbleutils.util.UtilsFile;
import com.kingpixel.ultrashop.UltraShop;
import com.kingpixel.ultrashop.domain.model.Transaction;
import com.kingpixel.ultrashop.infrastructure.persistence.TransactionRepository;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * JSON file-based transaction repository. Writes one file per day for easy archiving.
 * Each daily file contains a JSON array of transactions.
 */
public class JsonTransactionRepository implements TransactionRepository {

  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    .withZone(ZoneId.systemDefault());

  private static final Gson GSON = new Gson();
  private static final Type TRANSACTION_LIST_TYPE = new TypeToken<List<Transaction>>() {}.getType();

  private final Path basePath;

  public JsonTransactionRepository() {
    this.basePath = CobbleUtils.getPath().resolve(UltraShop.MOD_ID).resolve("data").resolve("transactions");
  }

  @Override
  public void save(Transaction transaction) {
    String date = DATE_FORMAT.format(Instant.ofEpochMilli(transaction.getTimestamp()));
    Path filePath = basePath.resolve(date + ".json");

    try {
      Files.createDirectories(basePath);
      List<Transaction> daily;
      if (Files.exists(filePath)) {
        String content = Files.readString(filePath);
        daily = GSON.fromJson(content, TRANSACTION_LIST_TYPE);
        if (daily == null) daily = new ArrayList<>();
      } else {
        daily = new ArrayList<>();
      }
      daily.add(transaction);
      UtilsFile.writeAsync(filePath, daily)
        .exceptionally(e -> {
          UltraShop.LOGGER.error(UltraShop.MOD_ID, "Error writing transaction: " + e.getMessage());
          return null;
        });
    } catch (IOException e) {
      UltraShop.LOGGER.error(UltraShop.MOD_ID, "Error saving transaction: " + e.getMessage());
    }
  }

  @Override
  public List<Transaction> findByPlayer(UUID playerUuid, int limit) {
    List<Transaction> result = new ArrayList<>();
    if (!Files.exists(basePath)) return result;

    try (Stream<Path> files = Files.list(basePath)) {
      // Sort by filename descending (newest first)
      List<Path> sorted = files
        .filter(p -> p.toString().endsWith(".json"))
        .sorted(Comparator.comparing(Path::getFileName).reversed())
        .collect(Collectors.toList());

      for (Path file : sorted) {
        if (result.size() >= limit) break;
        try {
          String content = Files.readString(file);
          List<Transaction> daily = GSON.fromJson(content, TRANSACTION_LIST_TYPE);
          if (daily != null) {
            daily.stream()
              .filter(t -> t.getPlayerUuid().equals(playerUuid))
              .forEach(result::add);
          }
        } catch (Exception e) {
          UltraShop.LOGGER.error(UltraShop.MOD_ID, "Error reading transaction file " + file + ": " + e.getMessage());
        }
      }
    } catch (IOException e) {
      UltraShop.LOGGER.error(UltraShop.MOD_ID, "Error listing transaction files: " + e.getMessage());
    }

    // Sort by timestamp desc, limit
    result.sort(Comparator.comparingLong(Transaction::getTimestamp).reversed());
    if (result.size() > limit) {
      return result.subList(0, limit);
    }
    return result;
  }

  @Override
  public List<Transaction> findAll(int maxDays) {
    List<Transaction> result = new ArrayList<>();
    if (!Files.exists(basePath)) return result;

    long cutoff = System.currentTimeMillis() - (long) maxDays * 86_400_000L;

    try (Stream<Path> files = Files.list(basePath)) {
      List<Path> sorted = files
        .filter(p -> p.toString().endsWith(".json"))
        .sorted(Comparator.comparing(Path::getFileName).reversed())
        .collect(Collectors.toList());

      for (Path file : sorted) {
        try {
          String content = Files.readString(file);
          List<Transaction> daily = GSON.fromJson(content, TRANSACTION_LIST_TYPE);
          if (daily != null) {
            for (Transaction t : daily) {
              if (t.getTimestamp() >= cutoff) {
                result.add(t);
              }
            }
            // If the oldest in this file is before cutoff, no need to read older files
            if (!daily.isEmpty() && daily.get(0).getTimestamp() < cutoff) break;
          }
        } catch (Exception e) {
          UltraShop.LOGGER.error(UltraShop.MOD_ID, "Error reading transaction file " + file + ": " + e.getMessage());
        }
      }
    } catch (IOException e) {
      UltraShop.LOGGER.error(UltraShop.MOD_ID, "Error listing transaction files: " + e.getMessage());
    }

    result.sort(Comparator.comparingLong(Transaction::getTimestamp).reversed());
    return result;
  }
}
