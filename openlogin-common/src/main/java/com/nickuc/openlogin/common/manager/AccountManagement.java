/*
 * This file is part of a NickUC project
 *
 * Copyright (c) NickUC <nickuc.com>
 * https://github.com/nickuc
 */

package com.nickuc.openlogin.common.manager;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.nickuc.openlogin.common.database.Database;
import com.nickuc.openlogin.common.model.Account;
import com.nickuc.openlogin.common.security.hashing.BCrypt;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import javax.annotation.Nullable;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
public class AccountManagement {

    private final Cache<String, Account> accountCache = CacheBuilder.newBuilder()
            .expireAfterWrite(30, TimeUnit.SECONDS)
            .build();

    private final Database database;

    /**
     * Checks if the password provided is valid
     *
     * @param password the password to compare
     * @return true if the passwords match
     */
    public boolean comparePassword(@NonNull Account account, @NonNull String password) {
        String hashedPassword = account.getHashedPassword();
        if (hashedPassword == null) {
            return false;
        }
        if (!hashedPassword.startsWith("$2")) {
            throw new IllegalArgumentException("Invalid hashed password for " + account.getRealname() + "! " + hashedPassword);
        }
        return BCrypt.checkpw(password, hashedPassword);
    }

    /**
     * Retrieve or load an account
     *
     * @param name the name of the player
     * @return the player's {@link Account}. Failing, will return empty Optional.
     */
    public Optional<Account> retrieveOrLoad(@NonNull String name) {
        Account account = accountCache.getIfPresent(name.toLowerCase());
        if (account == null) {
            Optional<Account> accountOpt = search(name);
            if (accountOpt.isPresent()) {
                account = accountOpt.get();
                accountCache.put(name.toLowerCase(), account);
            }
        }
        return Optional.ofNullable(account);
    }

    /**
     * Add an account to cache
     *
     * @param account the account to add
     */
    public void addToCache(@NonNull Account account) {
        accountCache.put(account.getRealname().toLowerCase(), account);
    }

    /**
     * Invalidate an account from cache
     *
     * @param key the key to invalidate
     */
    public void invalidateCache(@NonNull String key) {
        accountCache.invalidate(key);
    }

    /**
     * Searches for saved accounts
     *
     * @param name the name of the player
     * @return optional of {@link Account}
     */
    public Optional<Account> search(@NonNull String name) {
        try (Database.Query query = database.query("SELECT * FROM `openlogin` WHERE `name` = ?", name.toLowerCase())) {
            ResultSet resultSet = query.resultSet;
            if (resultSet.next()) {
                String realname = resultSet.getString("realname");
                String hashedPassword = resultSet.getString("password");
                String address = resultSet.getString("address");
                long lastlogin = Long.parseLong(resultSet.getString("lastlogin"));
                long regdate = Long.parseLong(resultSet.getString("regdate"));
                return Optional.of(new Account(realname, hashedPassword, address, lastlogin, regdate));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    /**
     * Update the player's database column
     *
     * @param name           the name of the player (realname)
     * @param hashedPassword the hashed password
     * @param address        the player address
     * @return true on success
     */
    public boolean update(@NonNull String name, @NonNull String hashedPassword, @Nullable String address) {
        return update(name, hashedPassword, address, true);
    }

    /**
     * Update the player's data
     *
     * @param name           the name of the player (realname)
     * @param hashedPassword the hashed password
     * @param address        the player address
     * @param replace        forces update if player data exists
     * @return true on success
     */
    public boolean update(@NonNull String name, @NonNull String hashedPassword, @Nullable String address, boolean replace) {
        boolean exists = search(name).isPresent();
        if (exists) {
            if (!replace) {
                return false;
            }
        }

        if (hashedPassword.trim().isEmpty()) {
            return false;
        }

        long current = System.currentTimeMillis();

        try {
            if (exists) {
                database.update(
                        "UPDATE `openlogin` SET `password` = ?, `address` ?, `lastlogin` = ? WHERE `name` = ?",
                        hashedPassword,
                        address == null ? "127.0.0.1" : address,
                        current,
                        name.toLowerCase()
                );
            } else {
                database.update(
                        "INSERT INTO `openlogin` (`name`, `realname`, `password`, `address`, `lastlogin`, `regdate`) VALUES (?, ?, ?, ?, ?, ?)",
                        name.toLowerCase(),
                        name,
                        hashedPassword,
                        address == null ? "127.0.0.1" : address,
                        current,
                        current
                );
            }
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Delete all of the player's data
     *
     * @param name the name of the player
     * @return true on success
     */
    public boolean delete(@NonNull String name) {
        boolean exists = search(name).isPresent();
        if (!exists) {
            return false;
        }

        try {
            database.update("DELETE FROM `openlogin` WHERE `name` = ?", name.toLowerCase());
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
}
