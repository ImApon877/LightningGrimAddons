package ac.grim.grimac.manager.datastore;

import ac.grim.grimac.api.storage.backend.BackendConfig;
import ac.grim.grimac.api.storage.backend.BackendV2;
import ac.grim.grimac.api.storage.check.CheckCatalogRow;
import ac.grim.grimac.internal.storage.backend.redis.RedisBackendConfig;
import ac.grim.grimac.internal.storage.checks.JdbcCheckCatalogPersistence;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/** Reads the persisted check catalog, split out of {@link DataStoreLifecycle}. */
final class CheckCatalogLoader {
    private CheckCatalogLoader() {
    }

    /** Rows already persisted in the backend's check catalog (SQL, Mongo or Redis); empty if none can be read. */
    static @NotNull List<CheckCatalogRow> load(
            @NotNull BackendV2 backend,
            @Nullable BackendConfig backendConfig,
            @NotNull String store,
            @NotNull Logger logger) {
        DataSource dataSource = backend.unwrap(DataSource.class).orElse(null);
        if (dataSource != null) {
            return rowsFrom(new JdbcCheckCatalogPersistence(dataSource::getConnection, store).loadAll());
        }

        MongoDatabase mongo = backend.unwrap(MongoDatabase.class).orElse(null);
        if (mongo != null) {
            List<CheckCatalogRow> out = new ArrayList<>();
            for (Document d : mongo.getCollection(store).find()) {
                CheckCatalogRow row = checkCatalogRowFromDocument(d);
                if (row != null) out.add(row);
            }
            return out;
        }

        if (backendConfig instanceof RedisBackendConfig redisConfig) {
            List<CheckCatalogRow> rows = loadRedisCheckCatalogRows(backend, redisConfig.keyPrefix(), store);
            if (rows != null) return rows;
        }

        logger.warning("[grim-datastore] no persisted check catalog loader available for v2 backend '"
                + backend.id() + "' - starting with an empty routed catalog view");
        return List.of();
    }

    private static @NotNull List<CheckCatalogRow> rowsFrom(@NotNull Iterable<CheckCatalogRow> rows) {
        List<CheckCatalogRow> out = new ArrayList<>();
        rows.forEach(out::add);
        return out;
    }

    private static @Nullable CheckCatalogRow checkCatalogRowFromDocument(@NotNull Document d) {
        Number id = d.get("check_id", Number.class);
        String stableKey = d.getString("stable_key");
        if (stableKey == null) {
            Object rawId = d.get("_id");
            if (rawId instanceof String s) stableKey = s;
        }
        if (id == null || stableKey == null) return null;
        Number introducedAt = d.get("introduced_at", Number.class);
        return new CheckCatalogRow(
                id.intValue(),
                stableKey,
                d.getString("display"),
                d.getString("description"),
                d.getString("introduced_version"),
                introducedAt == null ? 0L : introducedAt.longValue());
    }

    @SuppressWarnings("unchecked")
    private static @Nullable List<CheckCatalogRow> loadRedisCheckCatalogRows(
            @NotNull BackendV2 backend,
            @NotNull String keyPrefix,
            @NotNull String store) {
        try {
            Class<?> poolClass = Class.forName("redis.clients.jedis.JedisPool");
            Object pool = backend.unwrap((Class<Object>) poolClass).orElse(null);
            if (pool == null) return null;

            Class<?> scanParamsClass = Class.forName("redis.clients.jedis.params.ScanParams");
            Object params = scanParamsClass.getConstructor().newInstance();
            String rowPrefix = keyPrefix + store + ":";
            scanParamsClass.getMethod("match", String.class).invoke(params, rowPrefix + "*");
            scanParamsClass.getMethod("count", int.class).invoke(params, 1000);

            List<CheckCatalogRow> out = new ArrayList<>();
            Object jedis = poolClass.getMethod("getResource").invoke(pool);
            try {
                String cursor = "0";
                do {
                    Object result = jedis.getClass()
                            .getMethod("scan", String.class, scanParamsClass)
                            .invoke(jedis, cursor, params);
                    List<String> keys = (List<String>) result.getClass()
                            .getMethod("getResult")
                            .invoke(result);
                    for (String key : keys) {
                        if (key.startsWith(rowPrefix + "__idx:")) continue;
                        Map<String, String> hash = (Map<String, String>) jedis.getClass()
                                .getMethod("hgetAll", String.class)
                                .invoke(jedis, key);
                        CheckCatalogRow row = checkCatalogRowFromRedis(hash);
                        if (row != null) out.add(row);
                    }
                    cursor = (String) result.getClass().getMethod("getCursor").invoke(result);
                } while (!"0".equals(cursor));
            } finally {
                if (jedis instanceof AutoCloseable closeable) closeable.close();
            }
            return out;
        } catch (ClassNotFoundException e) {
            return null;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("failed to scan Redis check catalog", e);
        } catch (Exception e) {
            throw new IllegalStateException("failed to close Redis resource while scanning check catalog", e);
        }
    }

    private static @Nullable CheckCatalogRow checkCatalogRowFromRedis(@NotNull Map<String, String> h) {
        String id = h.get("check_id");
        String stableKey = h.get("stable_key");
        if (id == null || stableKey == null) return null;
        return new CheckCatalogRow(
                Integer.parseInt(id),
                stableKey,
                h.get("display"),
                h.get("description"),
                h.get("introduced_version"),
                parseLong(h.get("introduced_at")));
    }

    private static long parseLong(@Nullable String raw) {
        if (raw == null) return 0L;
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }
}
