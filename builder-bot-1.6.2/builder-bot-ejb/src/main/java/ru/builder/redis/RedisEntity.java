package ru.builder.redis;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.ejb.*;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@Singleton
@Startup
@AccessTimeout(15000)
public class RedisEntity {

    private JedisPool jedisPool;
    private final Logger logger = Logger.getLogger(this.getClass().getSimpleName());

    @PostConstruct
    public void initialize() {
        try {
            logger.log(Level.SEVERE, "Initial JedisPool ...");
            jedisPool = new JedisPool(this.buildJedisPoolConfig(), "redis", 6379);
            logger.log(Level.SEVERE, "Initial JedisPool done.");
        } catch (Exception ex) {
            logger.log(Level.SEVERE, null, ex);
            if (this.jedisPool != null) {
                logger.log(Level.SEVERE, "Destroy jedisPool ...");
                jedisPool.close();
            }
        }
    }

    private JedisPoolConfig buildJedisPoolConfig() {
        final JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(50);
        poolConfig.setMaxIdle(25);
        poolConfig.setMinIdle(25);
        return poolConfig;
    }

    @Lock(LockType.READ)
    public List<String> getElements(String key) {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.lrange(key, 0, -1);
        } catch (Exception ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        return null;
    }

    @Lock(LockType.READ)
    public String getElement(String key) {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.get(key);
        } catch (Exception ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        return null;
    }

    public void setElement(String key, String value) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.set(key, value);
        } catch (Exception ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    public void pushElement(String key, String value) {
        try (Jedis jedis = jedisPool.getResource()) {
            if (!this.getElements(key).contains(value)) {
                jedis.rpush(key, value);
            }
        } catch (Exception ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    public void remElement(String key, String value) {
        try (Jedis jedis = jedisPool.getResource()) {
            if (this.getElements(key).contains(value)) {
                jedis.lrem(key, 0, value);
            }
        } catch (Exception ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    public void hashSet(String key, String filed, String value) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.hset(key, filed, value);
        } catch (Exception ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    @Lock(LockType.READ)
    public String hashGet(String key, String field) {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.hget(key, field);
        } catch (Exception ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        return null;
    }

    @Lock(LockType.READ)
    public Map<String, String> hashGetAll(String key) {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.hgetAll(key);
        } catch (Exception ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        return null;
    }

    public void deleteElement(String key) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.del(key);
        } catch (Exception ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    @PreDestroy
    public void cleanup() {
        try {
            if (this.jedisPool != null) {
                logger.log(Level.SEVERE, "Destroy jedisPool ...");
                jedisPool.close();
            }
        } catch (Exception ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }
}