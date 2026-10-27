package ac.grim.grimac.events.packets;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.update.*;
import ac.grim.grimac.utils.data.BlockPlaceSnapshot;
import ac.grim.grimac.utils.data.*;
import ac.grim.grimac.utils.math.VectorUtils;
import ac.grim.grimac.utils.nmsutil.*;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.ConnectionState;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import com.github.retrooper.packetevents.protocol.world.BlockFace;
import com.github.retrooper.packetevents.protocol.world.Location;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.client.*;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerAcknowledgeBlockChanges;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot;


public class CheckManagerListener extends PacketListenerAbstract {

    public CheckManagerListener() {
        super(PacketListenerPriority.LOW);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        GrimPlayer player = GrimAPI.INSTANCE.getPlayerDataManager().getPlayer(event.getUser());
        if (player == null) return;

        if (event.getConnectionState() != ConnectionState.PLAY) {
            // Allow checks to listen to configuration packets
            if (event.getConnectionState() != ConnectionState.CONFIGURATION) return;
            player.checkManager.onPacketReceive(event);
            return;
        }

        // Determine if teleport BEFORE we call the pre-prediction vehicle
        if (event.getPacketType() == PacketType.Play.Client.VEHICLE_MOVE) {
            WrapperPlayClientVehicleMove move = new WrapperPlayClientVehicleMove(event);
            Vector3d position = move.getPosition();
            player.packetStateData.lastPacketWasTeleport = player.getSetbackTeleportUtil().checkVehicleTeleportQueue(position.getX(), position.getY(), position.getZ());
        }

        TeleportAcceptData teleportData = null;

        if (WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) {
            player.serverOpenedInventoryThisTick = false;

            WrapperPlayClientPlayerFlying flying = new WrapperPlayClientPlayerFlying(event);

            Location location = flying.getLocation();
            Vector3d position = VectorUtils.clampVector(location.getPosition());
            // Teleports must be POS LOOK
            teleportData = flying.hasPositionChanged() && flying.hasRotationChanged() ? player.getSetbackTeleportUtil().checkTeleportQueue(position.getX(), position.getY(), position.getZ(), location.getYaw(), location.getPitch()) : new TeleportAcceptData();
            player.packetStateData.lastPacketWasTeleport = teleportData.isTeleport();

            if (flying.hasRotationChanged() && !flying.hasPositionChanged() && !flying.isOnGround() && !flying.isHorizontalCollision()) {
                RotationData last = null;
                int transaction = player.getLastTransactionReceived();
                float yaw = flying.getLocation().getYaw();
                float pitch = flying.getLocation().getPitch();

                for (RotationData data : player.pendingRotations) {
                    if (transaction == data.getTransaction()
                            && (data.isRelativeYaw() || data.getYaw() == yaw)
                            // TODO: pitch bounds?
                            && (data.isRelativePitch() || data.getPitch() == pitch)) {
                        last = data;
                    }

                    if (!data.isAccepted()) {
                        break;
                    }
                }

                if (last != null) {
                    player.packetStateData.lastPacketWasTeleport = true;
                    last.accept(); // we could be wrong (especially in vehicles), don't remove this
                }
            }

            player.packetStateData.lastPacketWasOnePointSeventeenDuplicate = isMojangStupid(player, event, flying);
        }

        if (player.inVehicle() ? event.getPacketType() == PacketType.Play.Client.VEHICLE_MOVE : WrapperPlayClientPlayerFlying.isFlying(event.getPacketType()) && !player.packetStateData.lastPacketWasOnePointSeventeenDuplicate) {
            // Update knockback and explosions immediately, before anything can setback
            int kbEntityId = player.inVehicle() ? player.getRidingVehicleId() : player.entityID;

            VelocityData calculatedFirstBreadKb = player.checkManager.getKnockbackHandler().calculateFirstBreadKnockback(kbEntityId, player.lastTransactionReceived.get());
            VelocityData calculatedRequireKb = player.checkManager.getKnockbackHandler().calculateRequiredKB(kbEntityId, player.lastTransactionReceived.get(), false);
            player.firstBreadKB = calculatedFirstBreadKb == null ? player.firstBreadKB : calculatedFirstBreadKb;
            player.likelyKB = calculatedRequireKb == null ? player.likelyKB : calculatedRequireKb;

            VelocityData calculateFirstBreadExplosion = player.checkManager.getExplosionHandler().getFirstBreadAddedExplosion(player.lastTransactionReceived.get());
            VelocityData calculateRequiredExplosion = player.checkManager.getExplosionHandler().getPossibleExplosions(player.lastTransactionReceived.get(), false);
            player.firstBreadExplosion = calculateFirstBreadExplosion == null ? player.firstBreadExplosion : calculateFirstBreadExplosion;
            player.likelyExplosions = calculateRequiredExplosion == null ? player.likelyExplosions : calculateRequiredExplosion;
        }

        player.checkManager.onPrePredictionReceivePacket(event);

        // The player flagged crasher or timer checks, therefore we must protect predictions against these attacks
        if (event.isCancelled() && (WrapperPlayClientPlayerFlying.isFlying(event.getPacketType()) || event.getPacketType() == PacketType.Play.Client.VEHICLE_MOVE)) {
            player.packetStateData.cancelDuplicatePacket = false;
            return;
        }

        if (WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) {
            WrapperPlayClientPlayerFlying flying = new WrapperPlayClientPlayerFlying(event);
            Location pos = flying.getLocation();
            boolean ignoreRotation = player.packetStateData.lastPacketWasOnePointSeventeenDuplicate && player.isIgnoreDuplicatePacketRotation();
            handleFlying(player, pos.getX(), pos.getY(), pos.getZ(), ignoreRotation ? 0 : pos.getYaw(), ignoreRotation ? 0 : pos.getPitch(), flying.hasPositionChanged(), flying.hasRotationChanged() && !ignoreRotation, flying.isOnGround(), teleportData, event);
        }

        if (event.getPacketType() == PacketType.Play.Client.VEHICLE_MOVE && player.inVehicle()) {
            WrapperPlayClientVehicleMove move = new WrapperPlayClientVehicleMove(event);
            Vector3d position = move.getPosition();

            player.lastX = player.x;
            player.lastY = player.y;
            player.lastZ = player.z;

            Vector3d clamp = VectorUtils.clampVector(position);
            player.x = clamp.getX();
            player.y = clamp.getY();
            player.z = clamp.getZ();

            player.yaw = move.getYaw();
            player.pitch = move.getPitch();

            final VehiclePositionUpdate update = new VehiclePositionUpdate(clamp, position, move.getYaw(), move.getPitch(), move.isOnGround(), player.packetStateData.lastPacketWasTeleport);
            player.checkManager.onVehiclePositionUpdate(update);

            player.packetStateData.receivedSteerVehicle = false;
        }

        if (event.getPacketType() == PacketType.Play.Client.PLAYER_DIGGING) {
            BlockInteractionHandler.handleDigging(player, event);
        }

        if (event.getPacketType() == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) {
            WrapperPlayClientPlayerBlockPlacement packet = new WrapperPlayClientPlayerBlockPlacement(event);
            player.lastBlockPlaceUseItem = System.currentTimeMillis();

            ItemStack placedWith = player.inventory.getHeldItem();
            if (packet.getHand() == InteractionHand.OFF_HAND) {
                placedWith = player.inventory.getOffHand();
            }

            // This is the use item packet
            if (packet.getFace() == BlockFace.OTHER && PacketEvents.getAPI().getServerManager().getVersion().isOlderThan(ServerVersion.V_1_9)) {
                player.placeUseItemPackets.add(new BlockPlaceSnapshot(packet, player.isSneaking));
            } else {
                // Anti-air place
                BlockPlace blockPlace = new BlockPlace(player, packet.getHand(), packet.getBlockPosition(), packet.getFaceId(), packet.getFace(), placedWith, WorldRayTrace.getNearestBlockHitResult(player, null, true, false, false), packet.getSequence());
                blockPlace.cursor = packet.getCursorPosition();

                if (PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_11) && player.getClientVersion().isOlderThan(ClientVersion.V_1_11)) {
                    // ViaRewind is stupid and divides the byte by 15 to get the float
                    // We must undo this to get the correct block place... why?
                    if (packet.getCursorPosition().getX() * 15 % 1 == 0 && packet.getCursorPosition().getY() * 15 % 1 == 0 && packet.getCursorPosition().getZ() * 15 % 1 == 0) {
                        // This is impossible to occur without ViaRewind, fix their stupidity
                        int trueByteX = (int) (packet.getCursorPosition().getX() * 15);
                        int trueByteY = (int) (packet.getCursorPosition().getY() * 15);
                        int trueByteZ = (int) (packet.getCursorPosition().getZ() * 15);

                        blockPlace.cursor = new Vector3f(trueByteX / 16f, trueByteY / 16f, trueByteZ / 16f);
                    }
                }

                player.checkManager.onBlockPlace(blockPlace);

                if (event.isCancelled() || blockPlace.isCancelled() || player.getSetbackTeleportUtil().shouldBlockMovement()) { // The player tried placing blocks in air/water

                    if (!event.isCancelled()) {
                        event.setCancelled(true);
                        player.onPacketCancel();
                    }

                    Vector3i facePos = new Vector3i(packet.getBlockPosition().getX() + packet.getFace().getModX(), packet.getBlockPosition().getY() + packet.getFace().getModY(), packet.getBlockPosition().getZ() + packet.getFace().getModZ());

                    // Ends the client prediction introduced in 1.19+
                    if (player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_19) && PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_19)) {
                        player.user.sendPacket(new WrapperPlayServerAcknowledgeBlockChanges(packet.getSequence()));
                    } else { // The client isn't smart enough to revert changes
                        player.resyncPosition(packet.getBlockPosition());
                        player.resyncPosition(facePos);
                    }

                    // Stop inventory desync from cancelling place
                    if (player.platformPlayer != null) {
                        if (packet.getHand() == InteractionHand.MAIN_HAND) {
                            ItemStack mainHand = player.platformPlayer.getInventory().getItemInHand();
                            player.user.sendPacket(new WrapperPlayServerSetSlot(0, player.inventory.stateID, 36 + player.packetStateData.lastSlotSelected, mainHand));
                        } else {
                            ItemStack offHand = player.platformPlayer.getInventory().getItemInOffHand();
                            player.user.sendPacket(new WrapperPlayServerSetSlot(0, player.inventory.stateID, 45, offHand));
                        }
                    }

                } else { // Legit place
                    player.placeUseItemPackets.add(new BlockPlaceSnapshot(packet, player.isSneaking));
                }
            }
        }

        if (event.getPacketType() == PacketType.Play.Client.USE_ITEM) {
            WrapperPlayClientUseItem packet = new WrapperPlayClientUseItem(event);
            player.placeUseItemPackets.add(new BlockPlaceSnapshot(packet, player.isSneaking));
            player.lastBlockPlaceUseItem = System.currentTimeMillis();
        }

        // Call the packet checks last as they can modify the contents of the packet
        // Such as the NoFall check setting the player to not be on the ground
        player.checkManager.onPacketReceive(event);

        if (player.packetStateData.cancelDuplicatePacket) {
            event.setCancelled(true);
            player.packetStateData.cancelDuplicatePacket = false;
        }

        if (event.getPacketType() == PacketType.Play.Client.CLIENT_TICK_END && player.supportsEndTick()) {
            player.serverOpenedInventoryThisTick = false;
            if (!player.packetStateData.didSendMovementBeforeTickEnd) {
                // The player didn't send a movement packet, so we can predict this like we had idle tick on 1.8
                player.packetStateData.didLastLastMovementIncludePosition = player.packetStateData.didLastMovementIncludePosition;
                player.packetStateData.didLastMovementIncludePosition = false;

                // Track dash cooldown
                if (!player.inVehicle()) {
                    player.dashableEntities.tick();
                }
            }
            player.packetStateData.didSendMovementBeforeTickEnd = false;
        }

        // Finally, remove the packet state variables on this packet
        player.packetStateData.lastPacketWasOnePointSeventeenDuplicate = false;
        player.packetStateData.lastPacketWasTeleport = false;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getConnectionState() != ConnectionState.PLAY) return;
        GrimPlayer player = GrimAPI.INSTANCE.getPlayerDataManager().getPlayer(event.getUser());
        if (player == null) return;
        player.checkManager.onPacketSend(event);
    }

    private static boolean isMojangStupid(GrimPlayer player, PacketReceiveEvent event, WrapperPlayClientPlayerFlying flying) {
        // Teleports are not stupidity packets.
        if (player.packetStateData.lastPacketWasTeleport) return false;
        // Mojang has become less stupid!
        if (player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_21)) return false;

        final Location location = flying.getLocation();
        final double threshold = player.getMovementThreshold();

        // Don't check duplicate 1.17 packets (Why would you do this mojang?)
        // Don't check rotation since it changes between these packets, with the second being irrelevant.
        //
        // removed a large rant, but I'm keeping this out of context insult below
        // EVEN A BUNCH OF MONKEYS ON A TYPEWRITER COULDNT WRITE WORSE NETCODE THAN MOJANG
        if (flying.hasPositionChanged() && flying.hasRotationChanged() &&
                // Ground status will never change in this stupidity packet
                ((flying.isOnGround() == player.packetStateData.packetPlayerOnGround
                        // Mojang added this stupid mechanic in 1.17
                        && (player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_17) &&
                        // Due to 0.03, we can't check exact position, only within 0.03
                        player.filterMojangStupidityOnMojangStupidity.distanceSquared(location.getPosition()) < threshold * threshold))
                        // If the player was in a vehicle, has position and look, and wasn't a teleport, then it was this stupid packet
                        || player.inVehicle())) {

            // Mark that we want this packet to be cancelled from reaching the server
            // Additionally, only yaw/pitch matters: https://github.com/GrimAnticheat/Grim/issues/1275#issuecomment-1872444018
            // 1.9+ isn't impacted by this packet as much.
            if (PacketEvents.getAPI().getServerManager().getVersion().isOlderThanOrEquals(ServerVersion.V_1_9)) {
                if (player.isCancelDuplicatePacket()) {
                    player.packetStateData.cancelDuplicatePacket = true;
                }
            } else {
                // Override location to force it to use the last real position of the player. Prevents position-related bypasses like nofall.
                flying.setLocation(new Location(player.filterMojangStupidityOnMojangStupidity.getX(), player.filterMojangStupidityOnMojangStupidity.getY(), player.filterMojangStupidityOnMojangStupidity.getZ(), location.getYaw(), location.getPitch()));
                event.markForReEncode(true);
            }

            player.packetStateData.lastPacketWasOnePointSeventeenDuplicate = true;

            if (!player.isIgnoreDuplicatePacketRotation()) {
                if (player.yaw != location.getYaw() || player.pitch != location.getPitch()) {
                    player.lastYaw = player.yaw;
                    player.lastPitch = player.pitch;
                }

                // Take the pitch and yaw, just in case we were wrong about this being a stupidity packet
                player.yaw = location.getYaw();
                player.pitch = location.getPitch();
            }

            player.packetStateData.lastClaimedPosition = location.getPosition();
            return true;
        }
        return false;
    }

    private static void handleFlying(GrimPlayer player, double x, double y, double z, float yaw, float pitch, boolean hasPosition, boolean hasLook, boolean onGround, TeleportAcceptData teleportData, PacketReceiveEvent event) {
        long now = System.currentTimeMillis();

        if (!hasPosition) {
            // This may need to be secured later, although nothing that is very important relies on this
            // 1.8 ghost clients can't abuse this anyway
            player.uncertaintyHandler.lastPointThree.reset();
        }

        // We can't set the look if this is actually the stupidity packet
        // If the last packet wasn't stupid, then ignore this logic
        // If it was stupid, only change the look if it's different
        // Otherwise, reach and fireworks can false
        if (hasLook && (!player.packetStateData.lastPacketWasOnePointSeventeenDuplicate ||
                player.yaw != yaw || player.pitch != pitch)) {
            player.lastYaw = player.yaw;
            player.lastPitch = player.pitch;
        }

        BlockInteractionHandler.handleQueuedPlaces(player, hasLook, pitch, yaw, now);
        BlockInteractionHandler.handleQueuedBreaks(player, hasLook, pitch, yaw, now);

        // We can set the new pos after the places
        if (hasPosition) {
            player.packetStateData.lastClaimedPosition = new Vector3d(x, y, z);
        }

        // This stupid mechanic has been measured with 0.03403409022229198 y velocity... DAMN IT MOJANG, use 0.06 to be safe...
        if (!hasPosition && onGround != player.packetStateData.packetPlayerOnGround && !player.inVehicle()) {
            // Check for blocks within 0.03 of the player's position before allowing ground to be true - if 0.03
            // Cannot use collisions like normal because stepping messes it up :(
            //
            // This may need to be secured better, but limiting the new setback positions seems good enough for now...
            boolean canFeasiblyPointThree = Collisions.slowCouldPointThreeHitGround(player, player.x, player.y, player.z);
            if (!canFeasiblyPointThree && !player.compensatedWorld.isNearHardEntity(player.boundingBox.copy().expand(4))
                    || player.clientVelocity.getY() > 0.06 && !player.uncertaintyHandler.wasAffectedByStuckSpeed()) {
                // Ghost block/0.03 abuse
                player.getSetbackTeleportUtil().executeForceResync();
            } else {
                // Accept the new ground status
                player.lastOnGround = onGround;
                player.clientClaimsLastOnGround = onGround;
                player.uncertaintyHandler.onGroundUncertain = true;
            }
        }

        if (!player.packetStateData.lastPacketWasTeleport) {
            player.packetStateData.packetPlayerOnGround = onGround;
        }

        if (hasLook) {
            player.yaw = yaw;
            player.pitch = pitch;
            player.vehicleData.playerPitch = pitch;
            player.vehicleData.playerYaw = yaw;

            float deltaXRot = player.yaw - player.lastYaw;
            float deltaYRot = player.pitch - player.lastPitch;

            final RotationUpdate update = new RotationUpdate(new HeadRotation(player.lastYaw, player.lastPitch), new HeadRotation(player.yaw, player.pitch), deltaXRot, deltaYRot);
            player.checkManager.onRotationUpdate(update);
        }

        if (hasPosition) {
            Vector3d position = new Vector3d(x, y, z);
            Vector3d clampVector = VectorUtils.clampVector(position);
            final PositionUpdate update = new PositionUpdate(new Vector3d(player.x, player.y, player.z), position, onGround, teleportData.getSetback(), teleportData.getTeleportData(), teleportData.isTeleport());

            // Stupidity doesn't care about 0.03
            if (!player.packetStateData.lastPacketWasOnePointSeventeenDuplicate) {
                player.filterMojangStupidityOnMojangStupidity = clampVector;
            }

            if (!player.inVehicle() && !player.packetStateData.lastPacketWasOnePointSeventeenDuplicate) {
                player.lastX = player.x;
                player.lastY = player.y;
                player.lastZ = player.z;

                player.x = clampVector.getX();
                player.y = clampVector.getY();
                player.z = clampVector.getZ();

                player.checkManager.onPositionUpdate(update);
            } else if (update.isTeleport()) { // Mojang doesn't use their own exit vehicle field to leave vehicles, manually call the setback handler
                player.getSetbackTeleportUtil().onPredictionComplete(new PredictionComplete(0, update, true));
            }
        }

        player.packetStateData.didLastLastMovementIncludePosition = player.packetStateData.didLastMovementIncludePosition;
        player.packetStateData.didLastMovementIncludePosition = hasPosition;

        if (!player.packetStateData.lastPacketWasTeleport) {
            player.packetStateData.didSendMovementBeforeTickEnd = true;
        }

        player.packetStateData.horseInteractCausedForcedRotation = false;
    }
}
