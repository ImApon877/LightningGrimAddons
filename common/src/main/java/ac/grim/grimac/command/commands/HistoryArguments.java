package ac.grim.grimac.command.commands;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.api.storage.category.Categories;
import ac.grim.grimac.api.storage.history.SessionDetail;
import ac.grim.grimac.api.storage.identity.NameResolver;
import ac.grim.grimac.api.storage.model.PlayerIdentity;
import ac.grim.grimac.api.storage.query.Page;
import ac.grim.grimac.api.storage.query.Queries;
import ac.grim.grimac.manager.datastore.DataStoreLifecycle;
import ac.grim.grimac.platform.api.manager.cloud.CloudPlatformCommandArguments;
import ac.grim.grimac.platform.api.player.PlatformPlayer;
import ac.grim.grimac.platform.api.sender.Sender;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.suggestion.Suggestion;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Tab completion and player-name resolution for {@link GrimHistory}. */
final class HistoryArguments {
    private static final int MAX_SUGGESTIONS = 30;
    private static final int MAX_PLAYER_SUGGESTIONS = 25;

    private HistoryArguments() {
    }

    /**
     * Online players + offline name-prefix matches from the datastore
     * (sorted by {@code last_seen} desc). Empty prefix skips the offline
     * lookup. Capped at {@link #MAX_PLAYER_SUGGESTIONS}.
     */
    static SuggestionProvider<Sender> targetSuggestions(CloudPlatformCommandArguments arguments) {
        SuggestionProvider<Sender> onlineProvider = arguments.onlinePlayerSuggestions();
        return SuggestionProvider.blocking((ctx, in) -> {
            String partial = in.remainingInput();
            String partialLower = partial == null ? "" : partial.toLowerCase(Locale.ROOT);

            List<Suggestion> onlineSuggestions;
            try {
                Iterable<? extends Suggestion> onlineIt = onlineProvider.suggestionsFuture(ctx, in)
                        .toCompletableFuture().get(500, TimeUnit.MILLISECONDS);
                onlineSuggestions = new ArrayList<>();
                onlineIt.forEach(onlineSuggestions::add);
            } catch (Exception e) {
                onlineSuggestions = List.of();
            }

            if (partialLower.isEmpty()) return onlineSuggestions;

            Set<String> seen = new HashSet<>();
            List<Suggestion> out = new ArrayList<>();
            for (Suggestion s : onlineSuggestions) {
                if (seen.add(s.suggestion().toLowerCase(Locale.ROOT))) {
                    out.add(s);
                    if (out.size() >= MAX_PLAYER_SUGGESTIONS) return out;
                }
            }

            DataStoreLifecycle dsl = GrimAPI.INSTANCE.getDataStoreLifecycle();
            if (dsl == null || !dsl.isLoaded() || dsl.dataStore() == null) return out;
            try {
                Page<PlayerIdentity> page = dsl.dataStore().query(
                                Categories.PLAYER_IDENTITY,
                                Queries.listPlayersByNamePrefix(partialLower, MAX_PLAYER_SUGGESTIONS))
                        .toCompletableFuture().get(1, TimeUnit.SECONDS);
                for (PlayerIdentity id : page.items()) {
                    if (id.currentName() == null) continue;
                    if (seen.add(id.currentName().toLowerCase(Locale.ROOT))) {
                        out.add(Suggestion.suggestion(id.currentName()));
                        if (out.size() >= MAX_PLAYER_SUGGESTIONS) return out;
                    }
                }
            } catch (Exception e) {
                // Datastore unavailable or timed out — online-only fallback already populated.
            }
            return out;
        });
    }

    /** Suggest 1..maxPages for the list-pagination page number. */
    static SuggestionProvider<Sender> listPageSuggestions() {
        return SuggestionProvider.blocking((ctx, in) -> {
            UUID uuid = resolveTargetUuid(ctx);
            if (uuid == null) return List.of();
            DataStoreLifecycle dsl = GrimAPI.INSTANCE.getDataStoreLifecycle();
            if (dsl == null || !dsl.isLoaded() || dsl.historyService() == null) return List.of();
            try {
                long total = dsl.historyService().countSessions(uuid)
                        .toCompletableFuture().get(1, TimeUnit.SECONDS);
                int entriesPerPage = dsl.config().history().entriesPerPage();
                int maxPages = Math.max(1, (int) ((total + entriesPerPage - 1) / Math.max(1, entriesPerPage)));
                return rangeSuggestions(1, Math.min(maxPages, MAX_SUGGESTIONS));
            } catch (Exception e) {
                return List.of();
            }
        });
    }

    /** Suggest 1..totalSessions (capped) plus "latest". */
    static SuggestionProvider<Sender> sessionSuggestions() {
        return SuggestionProvider.blocking((ctx, in) -> {
            UUID uuid = resolveTargetUuid(ctx);
            if (uuid == null) return List.of(Suggestion.suggestion(GrimHistory.LATEST_ALIAS));
            DataStoreLifecycle dsl = GrimAPI.INSTANCE.getDataStoreLifecycle();
            if (dsl == null || !dsl.isLoaded() || dsl.historyService() == null) {
                return List.of(Suggestion.suggestion(GrimHistory.LATEST_ALIAS));
            }
            try {
                long total = dsl.historyService().countSessions(uuid)
                        .toCompletableFuture().get(1, TimeUnit.SECONDS);
                int max = (int) Math.min(total, MAX_SUGGESTIONS);
                List<Suggestion> out = new ArrayList<>(max + 1);
                out.add(Suggestion.suggestion(GrimHistory.LATEST_ALIAS));
                for (int i = 1; i <= max; i++) out.add(Suggestion.suggestion(Integer.toString(i)));
                return out;
            } catch (Exception e) {
                return List.of(Suggestion.suggestion(GrimHistory.LATEST_ALIAS));
            }
        });
    }

    /**
     * Suggest 1..violationPages for the violation-page argument inside a session.
     * Relies on the already-parsed {@code session} argument in the context to
     * find the matching sessionId and count its violations.
     */
    static SuggestionProvider<Sender> violationPageSuggestions() {
        return SuggestionProvider.blocking((ctx, in) -> {
            UUID uuid = resolveTargetUuid(ctx);
            if (uuid == null) return List.of();
            DataStoreLifecycle dsl = GrimAPI.INSTANCE.getDataStoreLifecycle();
            if (dsl == null || !dsl.isLoaded() || dsl.historyService() == null) return List.of();
            String sessionRaw = ctx.<String>getOrDefault("session", null);
            if (sessionRaw == null) return List.of();
            try {
                Integer ordinal = GrimHistory.resolveSessionOrdinal(sessionRaw, uuid, dsl.historyService());
                if (ordinal == null) return List.of();
                SessionDetail detail;
                if (!(dsl.historyService() instanceof ac.grim.grimac.internal.storage.history.HistoryServiceImpl impl)) {
                    return List.of();
                }
                detail = impl.getSessionDetailByOrdinal(uuid, ordinal)
                        .toCompletableFuture().get(1, TimeUnit.SECONDS);
                if (detail == null) return List.of();
                int entriesPerPage = dsl.config().history().entriesPerPage();
                // Page unit depends on --detailed; without that info here, suggest
                // the larger of the two so we never under-offer. Detailed mode
                // paginates violations (larger count); grouped paginates buckets.
                int rows = Math.max(detail.violations().size(), detail.buckets().size());
                int maxPages = Math.max(1, (rows + entriesPerPage - 1) / Math.max(1, entriesPerPage));
                return rangeSuggestions(1, Math.min(maxPages, MAX_SUGGESTIONS));
            } catch (Exception e) {
                return List.of();
            }
        });
    }

    static @Nullable UUID resolveTargetUuid(CommandContext<Sender> ctx) {
        String target = ctx.<String>getOrDefault("target", null);
        if (target == null) return null;
        return resolveUuid(target, GrimAPI.INSTANCE.getDataStoreLifecycle(), null);
    }

    /**
     * Resolve history targets without platform offline-player lookups. Those
     * can synchronously hit profile storage and miss names known only through a
     * shared history backend.
     */
    static @Nullable UUID resolveUuid(String name, @Nullable DataStoreLifecycle lifecycle) {
        return resolveUuid(name, lifecycle, onlineUuid(name));
    }

    static @Nullable UUID resolveUuid(String name, @Nullable DataStoreLifecycle lifecycle, @Nullable UUID onlineUuid) {
        if (onlineUuid != null) return onlineUuid;
        if (lifecycle != null && lifecycle.isLoaded()) {
            NameResolver resolver = lifecycle.nameResolver();
            if (resolver != null) {
                try {
                    Optional<UUID> hit = resolver.resolveByName(name)
                            .toCompletableFuture().get(2, TimeUnit.SECONDS);
                    if (hit.isPresent()) return hit.get();
                } catch (Exception ignored) {
                }
            }
        }

        try {
            return UUID.fromString(name);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    static @Nullable UUID onlineUuid(String name) {
        PlatformPlayer online = GrimAPI.INSTANCE.getPlatformPlayerFactory().getFromName(name);
        return online == null ? null : online.getUniqueId();
    }

    static List<Suggestion> rangeSuggestions(int fromInclusive, int toInclusive) {
        if (toInclusive < fromInclusive) return List.of();
        List<Suggestion> out = new ArrayList<>(toInclusive - fromInclusive + 1);
        for (int i = fromInclusive; i <= toInclusive; i++) out.add(Suggestion.suggestion(Integer.toString(i)));
        return out;
    }

}
