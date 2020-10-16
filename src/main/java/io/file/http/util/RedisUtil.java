package io.file.http.util;

import io.file.http.system.SystemConstant;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

/**
 * @author SuanCaiYv
 * @time 2020/9/14 上午11:25
 */
public class RedisUtil {

    private static final RedisClient redisClient = RedisClient.create("redis://"
            + SystemConstant.REDIS_PASSWORD
            + "@"
            + SystemConstant.REDIS_HOST
            + ":"
            + SystemConstant.REDIS_PORT
            + "/"
            + SystemConstant.REDIS_DATABASE);

    private static final StatefulRedisConnection<String, String> connection = redisClient.connect();

    private static final RedisCommands<String, String> syncCommands = connection.sync();

    public static boolean set(String key, String value) {
        syncCommands.set(key, value);
        return true;
    }

    public static boolean set(String key, String value, long millIime) {
        syncCommands.set(key, value);
        syncCommands.pexpire(key, millIime);
        return true;
    }

    public static String get(String key) {
        return syncCommands.get(key);
    }
}
