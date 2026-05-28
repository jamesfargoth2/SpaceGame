package com.galacticodyssey.server.persistence;

import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.Jedis;

public class RedisManager {

    public static class Config {
        public String host = "localhost";
        public int port = 6379;
        public int maxPoolSize = 10;
    }

    private final Config config;
    private JedisPool pool;

    public RedisManager(Config config) {
        this.config = config;
    }

    public void init() {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(config.maxPoolSize);
        pool = new JedisPool(poolConfig, config.host, config.port);
    }

    public Jedis getResource() {
        if (pool == null) {
            throw new IllegalStateException("RedisManager not initialized — call init() first");
        }
        return pool.getResource();
    }

    public boolean isRunning() {
        return pool != null && !pool.isClosed();
    }

    public void shutdown() {
        if (pool != null && !pool.isClosed()) {
            pool.close();
        }
    }

    public static String sessionKey(String token) {
        return "session:" + token;
    }

    public static String zoneLoadKey(String zoneId) {
        return "zone:" + zoneId + ":load";
    }

    public static String zoneBorderChannel(String zoneId) {
        return "zone.border." + zoneId;
    }

    public static String handoffPrepareChannel(String zoneId) {
        return "zone.handoff.prepare." + zoneId;
    }

    public static String handoffAckChannel(String zoneId) {
        return "zone.handoff.ack." + zoneId;
    }
}
