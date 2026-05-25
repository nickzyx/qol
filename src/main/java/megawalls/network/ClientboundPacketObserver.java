package megawalls.network;

import com.mojang.authlib.GameProfile;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import megawalls.service.MegaWallsService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.DataWatcher;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.S04PacketEntityEquipment;
import net.minecraft.network.play.server.S1CPacketEntityMetadata;
import net.minecraft.network.play.server.S1DPacketEntityEffect;
import net.minecraft.network.play.server.S1EPacketRemoveEntityEffect;
import net.minecraft.network.play.server.S38PacketPlayerListItem;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class ClientboundPacketObserver {

    public static final ClientboundPacketObserver INSTANCE = new ClientboundPacketObserver();

    private static final String HANDLER_NAME = "qol_tracker_observer";
    private static final int PLAYER_HEALTH_DATA_ID = 6;

    private final Queue<PacketObservation> pendingObservations = new ConcurrentLinkedQueue<PacketObservation>();
    private final ChannelDuplexHandler inboundObserver = new ChannelDuplexHandler() {
        @Override
        public void channelRead(ChannelHandlerContext context, Object message) throws Exception {
            capturePacket(message);
            super.channelRead(context, message);
        }
    };

    private Field networkChannelField;
    private NetworkManager attachedManager;
    private Channel attachedChannel;

    private ClientboundPacketObserver() {}

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        updateAttachment();
        drainObservations();
    }

    private void updateAttachment() {
        Minecraft minecraft = Minecraft.getMinecraft();
        NetHandlerPlayClient netHandler = minecraft == null ? null : minecraft.getNetHandler();
        NetworkManager networkManager = netHandler == null ? null : netHandler.getNetworkManager();

        if (networkManager == null) {
            detachObserver();
            return;
        }

        if (attachedManager != networkManager) {
            detachObserver();
            attachedManager = networkManager;
        }

        Channel channel = resolveChannel(networkManager);
        if (channel == null) {
            return;
        }

        attachedChannel = channel;
        installObserver(channel);
    }

    private void capturePacket(Object message) {
        if (message instanceof S38PacketPlayerListItem) {
            capturePlayerListPacket((S38PacketPlayerListItem) message);
        } else if (message instanceof S1CPacketEntityMetadata) {
            captureEntityMetadataPacket((S1CPacketEntityMetadata) message);
        } else if (message instanceof S04PacketEntityEquipment) {
            captureEquipmentPacket((S04PacketEntityEquipment) message);
        } else if (message instanceof S1DPacketEntityEffect) {
            captureEntityEffectPacket((S1DPacketEntityEffect) message);
        } else if (message instanceof S1EPacketRemoveEntityEffect) {
            captureEntityEffectRemovalPacket((S1EPacketRemoveEntityEffect) message);
        }
    }

    private void capturePlayerListPacket(S38PacketPlayerListItem packet) {
        if (packet.getEntries() == null || packet.getEntries().isEmpty()) {
            return;
        }

        for (S38PacketPlayerListItem.AddPlayerData entry : packet.getEntries()) {
            if (entry == null) {
                continue;
            }

            GameProfile profile = entry.getProfile();
            if (profile == null) {
                continue;
            }

            final UUID playerId = profile.getId();
            final String profileName = profile.getName();
            final IChatComponent displayName = entry.getDisplayName();
            final String renderedName = displayName == null ? null : displayName.getFormattedText();
            pendingObservations.add(new PacketObservation() {
                @Override
                public void apply() {
                    MegaWallsService.INSTANCE.observeTabProfile(playerId, profileName, renderedName);
                }
            });
        }
    }

    private void captureEntityMetadataPacket(S1CPacketEntityMetadata packet) {
        List<DataWatcher.WatchableObject> watchedObjects = packet.func_149376_c();
        if (watchedObjects == null || watchedObjects.isEmpty()) {
            return;
        }

        Float health = null;
        for (DataWatcher.WatchableObject watchedObject : watchedObjects) {
            if (watchedObject == null || watchedObject.getDataValueId() != PLAYER_HEALTH_DATA_ID) {
                continue;
            }

            Object watchedValue = watchedObject.getObject();
            if (watchedValue instanceof Number) {
                health = Float.valueOf(((Number) watchedValue).floatValue());
                break;
            }
        }

        if (health == null) {
            return;
        }

        final int entityId = packet.getEntityId();
        final float observedHealth = health.floatValue();
        pendingObservations.add(new PacketObservation() {
            @Override
            public void apply() {
                MegaWallsService.INSTANCE.observeEntityMetadata(entityId, observedHealth);
            }
        });
    }

    private void captureEquipmentPacket(S04PacketEntityEquipment packet) {
        final int entityId = packet.getEntityID();
        final int slot = packet.getEquipmentSlot();
        final net.minecraft.item.ItemStack itemStack = packet.getItemStack() == null ? null : packet.getItemStack().copy();

        pendingObservations.add(new PacketObservation() {
            @Override
            public void apply() {
                MegaWallsService.INSTANCE.observeEquipmentPacket(entityId, slot, itemStack);
            }
        });
    }

    private void captureEntityEffectPacket(S1DPacketEntityEffect packet) {
        final int entityId = packet.getEntityId();
        final int effectId = packet.getEffectId();
        final int duration = packet.getDuration();

        pendingObservations.add(new PacketObservation() {
            @Override
            public void apply() {
                MegaWallsService.INSTANCE.observeEntityEffect(entityId, effectId, duration);
            }
        });
    }

    private void captureEntityEffectRemovalPacket(S1EPacketRemoveEntityEffect packet) {
        final int entityId = packet.getEntityId();
        final int effectId = packet.getEffectId();

        pendingObservations.add(new PacketObservation() {
            @Override
            public void apply() {
                MegaWallsService.INSTANCE.observeEntityEffectRemoved(entityId, effectId);
            }
        });
    }

    private void drainObservations() {
        PacketObservation observation;
        while ((observation = pendingObservations.poll()) != null) {
            observation.apply();
        }
    }

    private void installObserver(final Channel channel) {
        if (channel == null) {
            return;
        }

        runOnChannel(channel, new Runnable() {
            @Override
            public void run() {
                ChannelPipeline pipeline = channel.pipeline();
                if (pipeline.get(HANDLER_NAME) != null) {
                    return;
                }

                if (pipeline.get("packet_handler") != null) {
                    pipeline.addBefore("packet_handler", HANDLER_NAME, inboundObserver);
                } else {
                    pipeline.addLast(HANDLER_NAME, inboundObserver);
                }
            }
        });
    }

    private void detachObserver() {
        final Channel channel = attachedChannel;
        attachedChannel = null;
        attachedManager = null;

        if (channel == null) {
            return;
        }

        runOnChannel(channel, new Runnable() {
            @Override
            public void run() {
                ChannelPipeline pipeline = channel.pipeline();
                if (pipeline.get(HANDLER_NAME) != null) {
                    pipeline.remove(HANDLER_NAME);
                }
            }
        });
    }

    private void runOnChannel(Channel channel, Runnable action) {
        if (channel == null || action == null) {
            return;
        }

        try {
            if (channel.eventLoop().inEventLoop()) {
                action.run();
            } else {
                channel.eventLoop().execute(action);
            }
        } catch (Throwable ignored) {
        }
    }

    private Channel resolveChannel(NetworkManager networkManager) {
        if (networkManager == null) {
            return null;
        }

        try {
            if (networkChannelField == null) {
                networkChannelField = findField(networkManager.getClass(), Channel.class);
            }
            return networkChannelField == null ? null : (Channel) networkChannelField.get(networkManager);
        } catch (IllegalAccessException ignored) {
            return null;
        }
    }

    private Field findField(Class<?> ownerType, Class<?> fieldType) {
        Class<?> currentType = ownerType;
        while (currentType != null) {
            Field[] fields = currentType.getDeclaredFields();
            for (Field field : fields) {
                if (!fieldType.isAssignableFrom(field.getType())) {
                    continue;
                }

                field.setAccessible(true);
                return field;
            }
            currentType = currentType.getSuperclass();
        }
        return null;
    }

    private interface PacketObservation {
        void apply();
    }
}
