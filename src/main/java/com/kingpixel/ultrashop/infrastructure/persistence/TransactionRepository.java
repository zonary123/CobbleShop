package com.kingpixel.ultrashop.infrastructure.persistence;

import com.kingpixel.ultrashop.domain.model.Transaction;

import java.util.List;
import java.util.UUID;

/**
 * Repository for transaction records (auditing/analytics).
 */
public interface TransactionRepository {
  void save(Transaction transaction);

  /**
   * Finds recent transactions for a player, ordered by timestamp descending.
   */
  List<Transaction> findByPlayer(UUID playerUuid, int limit);

  /**
   * Loads all transactions from the last N days.
   */
  List<Transaction> findAll(int maxDays);
}
