package me.adam.tpa;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TpaRequestManager {
    private final TpaPlugin plugin;
    private final Map<UUID, TpaRequest> incomingByTarget = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> outgoingBySender = new ConcurrentHashMap<>();

    public TpaRequestManager(TpaPlugin plugin) {
        this.plugin = plugin;
    }

    public synchronized boolean hasOutgoing(UUID sender) {
        cleanupExpired(sender, null);
        return outgoingBySender.containsKey(sender);
    }

    public synchronized TpaRequest getIncoming(UUID target) {
        cleanupExpired(null, target);
        return incomingByTarget.get(target);
    }

    public synchronized void putRequest(UUID sender, UUID target, RequestType type) {
        removeOutgoing(sender);
        removeIncomingForTarget(target);

        TpaRequest request = new TpaRequest(sender, target, type, System.currentTimeMillis());
        incomingByTarget.put(target, request);
        outgoingBySender.put(sender, target);
    }

    public enum RequestType {
        /** Sender teleports to target on accept. */
        TO_TARGET,
        /** Target teleports to sender on accept. */
        TO_SENDER
    }

    public synchronized TpaRequest removeIncoming(UUID target) {
        TpaRequest request = incomingByTarget.remove(target);
        if (request != null) {
            outgoingBySender.remove(request.sender());
        }
        return request;
    }

    public synchronized TpaRequest removeOutgoing(UUID sender) {
        UUID target = outgoingBySender.remove(sender);
        if (target == null) {
            return null;
        }
        TpaRequest request = incomingByTarget.remove(target);
        if (request != null && request.sender().equals(sender)) {
            return request;
        }
        return null;
    }

    public synchronized void clearPlayer(UUID uuid) {
        removeOutgoing(uuid);
        removeIncoming(uuid);
    }

    private void removeIncomingForTarget(UUID target) {
        TpaRequest existing = incomingByTarget.remove(target);
        if (existing != null) {
            outgoingBySender.remove(existing.sender());
        }
    }

    private void cleanupExpired(UUID sender, UUID target) {
        int timeout = plugin.getConfig().getInt("request-timeout-seconds", 60);
        if (timeout <= 0) {
            return;
        }
        long maxAgeMs = timeout * 1000L;
        long now = System.currentTimeMillis();

        if (target != null) {
            TpaRequest request = incomingByTarget.get(target);
            if (request != null && now - request.createdAtMs() > maxAgeMs) {
                removeIncoming(target);
            }
        }
        if (sender != null) {
            UUID targetUuid = outgoingBySender.get(sender);
            if (targetUuid != null) {
                TpaRequest request = incomingByTarget.get(targetUuid);
                if (request != null && request.sender().equals(sender) && now - request.createdAtMs() > maxAgeMs) {
                    removeOutgoing(sender);
                }
            }
        }
    }

    public static final class TpaRequest {
        private final UUID sender;
        private final UUID target;
        private final RequestType type;
        private final long createdAtMs;

        public TpaRequest(UUID sender, UUID target, RequestType type, long createdAtMs) {
            this.sender = sender;
            this.target = target;
            this.type = type;
            this.createdAtMs = createdAtMs;
        }

        public UUID sender() {
            return sender;
        }

        public UUID target() {
            return target;
        }

        public RequestType type() {
            return type;
        }

        public long createdAtMs() {
            return createdAtMs;
        }
    }
}
