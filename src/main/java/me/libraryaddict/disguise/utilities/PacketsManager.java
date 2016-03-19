package me.libraryaddict.disguise.utilities;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.PacketType.Play.Server;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.events.PacketListener;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.WrappedAttribute;
import com.comphenix.protocol.wrappers.WrappedAttribute.Builder;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import com.comphenix.protocol.wrappers.WrappedDataWatcher.Registry;
import com.comphenix.protocol.wrappers.WrappedDataWatcher.WrappedDataWatcherObject;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import com.comphenix.protocol.wrappers.WrappedWatchableObject;
import me.libraryaddict.disguise.DisguiseAPI;
import me.libraryaddict.disguise.DisguiseConfig;
import me.libraryaddict.disguise.LibsDisguises;
import me.libraryaddict.disguise.disguisetypes.AnimalColor;
import me.libraryaddict.disguise.disguisetypes.Disguise;
import me.libraryaddict.disguise.disguisetypes.DisguiseType;
import me.libraryaddict.disguise.disguisetypes.FlagWatcher;
import me.libraryaddict.disguise.disguisetypes.MiscDisguise;
import me.libraryaddict.disguise.disguisetypes.MobDisguise;
import me.libraryaddict.disguise.disguisetypes.PlayerDisguise;
import me.libraryaddict.disguise.disguisetypes.watchers.LivingWatcher;
import me.libraryaddict.disguise.disguisetypes.watchers.PlayerWatcher;
import me.libraryaddict.disguise.disguisetypes.watchers.SheepWatcher;
import me.libraryaddict.disguise.disguisetypes.watchers.WolfWatcher;
import me.libraryaddict.disguise.utilities.DisguiseSound.SoundType;
import org.bukkit.Art;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Damageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.util.Vector;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class PacketsManager {
    /**
     * This is a fix for the stupidity that is
     * "I can't separate the sounds from the sounds the player heard, and the sounds of the entity tracker heard"
     */
    private static boolean cancelSound;
    private static PacketListener clientInteractEntityListener;
    private static PacketListener inventoryListener;
    private static boolean inventoryModifierEnabled;
    private static LibsDisguises libsDisguises;
    private static PacketListener mainListener;
    private static PacketListener soundsListener;
    private static boolean soundsListenerEnabled;
    private static PacketListener viewDisguisesListener;
    private static boolean viewDisguisesListenerEnabled;

    public static void addPacketListeners() {
        // Add a client listener to cancel them interacting with uninteractable disguised entitys.
        // You ain't supposed to be allowed to 'interact' with a item that cannot be clicked.
        // Because it kicks you for hacking.
        clientInteractEntityListener = new PacketAdapter(libsDisguises, ListenerPriority.NORMAL,
                PacketType.Play.Client.USE_ENTITY) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                if (event.isCancelled())
                    return;
                try {
                    if (event.getPlayer().getName().contains("UNKNOWN[")) //If the player is temporary
                        return;
                    Player observer = event.getPlayer();
                    StructureModifier<Entity> entityModifer = event.getPacket().getEntityModifier(observer.getWorld());
                    Entity entity = entityModifer.read(0);
                    if (entity instanceof ExperienceOrb || entity instanceof Item || entity instanceof Arrow
                            || entity == observer) {
                        event.setCancelled(true);
                    }
                    ItemStack item = observer.getItemInHand();
                    if (item != null && item.getType() == Material.INK_SACK) {
                        Disguise disguise = DisguiseAPI.getDisguise(observer, entity);
                        if (disguise != null
                                && (disguise.getType() == DisguiseType.SHEEP || disguise.getType() == DisguiseType.WOLF)) {
                            AnimalColor color = AnimalColor.getColor(item.getDurability());
                            if (disguise.getType() == DisguiseType.SHEEP) {
                                SheepWatcher watcher = (SheepWatcher) disguise.getWatcher();
                                watcher.setColor(DisguiseConfig.isSheepDyeable() ? color : watcher.getColor());
                            } else {
                                WolfWatcher watcher = (WolfWatcher) disguise.getWatcher();
                                watcher.setCollarColor(DisguiseConfig.isWolfDyeable() ? color : watcher.getCollarColor());
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        ProtocolLibrary.getProtocolManager().addPacketListener(clientInteractEntityListener);
        // Now I call this and the main listener is registered!
        setupMainPacketsListener();
    }

    /**
     * Construct the packets I need to spawn in the disguise
     */
    public static PacketContainer[][] constructSpawnPackets(final Player player, Disguise disguise, Entity disguisedEntity) {
        if (disguise.getEntity() == null)
            disguise.setEntity(disguisedEntity);
        ArrayList<PacketContainer> packets = new ArrayList<>();
        // This sends the armor packets so that the player isn't naked.
        // Please note it only sends the packets that wouldn't be sent normally
        if (DisguiseConfig.isEquipmentPacketsEnabled()) {
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                ItemStack itemstack = disguise.getWatcher().getItemStack(slot);
                if (itemstack != null && itemstack.getTypeId() != 0) {
                    ItemStack item = null;
                    if (disguisedEntity instanceof LivingEntity) {
                        item = ReflectionManager.getEquipment(slot, disguisedEntity);
                    }
                    if (item == null || item.getType() == Material.AIR) {
                        PacketContainer packet = new PacketContainer(Server.ENTITY_EQUIPMENT);
                        StructureModifier<Object> mods = packet.getModifier();
                        mods.write(0, disguisedEntity.getEntityId());
                        mods.write(1, ReflectionManager.createEnumItemSlot(slot));
                        mods.write(2, ReflectionManager.getNmsItem(itemstack));
                        packets.add(packet);
                    }
                }
            }
        }
        if (DisguiseConfig.isMiscDisguisesForLivingEnabled()) {
            if (disguise.getWatcher() instanceof LivingWatcher) {
                PacketContainer packet = new PacketContainer(Server.UPDATE_ATTRIBUTES);
                List<WrappedAttribute> attributes = new ArrayList<>();
                Builder builder = WrappedAttribute.newBuilder().attributeKey("generic.maxHealth");
                if (((LivingWatcher) disguise.getWatcher()).isMaxHealthSet()) {
                    builder.baseValue(((LivingWatcher) disguise.getWatcher()).getMaxHealth());
                } else if (DisguiseConfig.isMaxHealthDeterminedByDisguisedEntity() && disguisedEntity instanceof Damageable) {
                    builder.baseValue(((Damageable) disguisedEntity).getMaxHealth());
                } else {
                    builder.baseValue(DisguiseValues.getDisguiseValues(disguise.getType()).getMaxHealth());
                }
                builder.packet(packet);
                attributes.add(builder.build());
                packet.getIntegers().write(0, disguisedEntity.getEntityId());
                packet.getAttributeCollectionModifier().write(0, attributes);
                packets.add(packet);
            }
        }
        PacketContainer[] spawnPackets = new PacketContainer[2 + packets.size()];
        PacketContainer[] delayedPackets = new PacketContainer[0];
        for (int i = 0; i < packets.size(); i++) {
            spawnPackets[i + 2] = packets.get(i);
        }
        Location loc = disguisedEntity.getLocation().clone().add(0, getYModifier(disguisedEntity, disguise), 0);
        byte yaw = (byte) (int) (loc.getYaw() * 256.0F / 360.0F);
        byte pitch = (byte) (int) (loc.getPitch() * 256.0F / 360.0F);
        if (DisguiseConfig.isMovementPacketsEnabled()) {
            yaw = getYaw(disguise.getType(), disguisedEntity.getType(), yaw);
            pitch = getPitch(disguise.getType(), DisguiseType.getType(disguisedEntity.getType()), pitch);
        }

        if (disguise.getType() == DisguiseType.EXPERIENCE_ORB) {
            spawnPackets[0] = new PacketContainer(Server.SPAWN_ENTITY_EXPERIENCE_ORB);
            StructureModifier<Object> mods = spawnPackets[0].getModifier();
            mods.write(0, disguisedEntity.getEntityId());
            mods.write(1, Math.floor(loc.getX() * 32));
            mods.write(2, Math.floor(loc.getY() * 32) + 2);
            mods.write(3, Math.floor(loc.getZ() * 32));
            mods.write(4, 1);
        } else if (disguise.getType() == DisguiseType.PAINTING) {
            spawnPackets[0] = new PacketContainer(Server.SPAWN_ENTITY_PAINTING);
            StructureModifier<Object> mods = spawnPackets[0].getModifier();
            mods.write(0, disguisedEntity.getEntityId());
            mods.write(1, disguisedEntity.getUniqueId());
            mods.write(2, ReflectionManager.getBlockPosition(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
            mods.write(3, ReflectionManager.getEnumDirection(((int) loc.getYaw()) % 4));
            int id = ((MiscDisguise) disguise).getData();
            mods.write(4, ReflectionManager.getEnumArt(Art.values()[id]));

            // Make the teleport packet to make it visible..
            spawnPackets[1] = new PacketContainer(Server.ENTITY_TELEPORT);
            mods = spawnPackets[1].getModifier();
            mods.write(0, disguisedEntity.getEntityId());
            mods.write(1, Math.floor(loc.getX() * 32D));
            mods.write(2, Math.floor(loc.getY() * 32D));
            mods.write(3, Math.floor(loc.getZ() * 32D));
            mods.write(4, yaw);
            mods.write(5, pitch);
        } else if (disguise.getType().isPlayer()) {
            PlayerDisguise playerDisguise = (PlayerDisguise) disguise;
            String name = playerDisguise.getSkin() != null ? playerDisguise.getSkin() : playerDisguise.getName();
            int entityId = disguisedEntity.getEntityId();
            boolean removeName = false;
            if (!DisguiseUtilities.hasGameProfile(name)) {
                removeName = !DisguiseUtilities.getAddedByPlugins().contains(name);
            }
            WrappedGameProfile gameProfile = playerDisguise.getGameProfile();
            if (removeName) {
                DisguiseUtilities.getAddedByPlugins().remove(name);
            }
            Object entityPlayer = ReflectionManager.createEntityPlayer(player.getWorld(), gameProfile);
            spawnPackets[0] = ProtocolLibrary.getProtocolManager()
                    .createPacketConstructor(Server.NAMED_ENTITY_SPAWN, entityPlayer)
                    .createPacket(entityPlayer);

            //Write spawn packet in order
            spawnPackets[0].getIntegers().write(0, entityId);  //Id
            spawnPackets[0].getDoubles().write(0, loc.getX());
            spawnPackets[0].getDoubles().write(1, loc.getY());
            spawnPackets[0].getDoubles().write(2, loc.getZ());

            spawnPackets[0].getDataWatcherModifier().write(0, createDataWatcher(WrappedDataWatcher.getEntityWatcher(disguisedEntity), disguise.getWatcher())); //watcher, duh



            if (DisguiseConfig.isBedPacketsEnabled() && ((PlayerWatcher) disguise.getWatcher()).isSleeping()) {
                PacketContainer[] newPackets = new PacketContainer[spawnPackets.length + 1];
                System.arraycopy(spawnPackets, 1, newPackets, 2, spawnPackets.length - 1);
                newPackets[0] = spawnPackets[0];
                spawnPackets = newPackets;
                PacketContainer[] bedPackets = DisguiseUtilities.getBedPackets(player,
                        loc.clone().subtract(0, PacketsManager.getYModifier(disguisedEntity, disguise), 0), player.getLocation(),
                        ((PlayerDisguise) disguise));
                System.arraycopy(bedPackets, 0, spawnPackets, 1, 2);
            }

            ArrayList<PacketContainer> newPackets = new ArrayList<>();
            newPackets.add(null);
            for (PacketContainer spawnPacket : spawnPackets) {
                if (spawnPacket != null) { // Get rid of empty packet '1' if it exists.
                    newPackets.add(spawnPacket);
                }
            }

            //Send player info along with the disguise
            spawnPackets = newPackets.toArray(new PacketContainer[newPackets.size()]);
            spawnPackets[0] = new PacketContainer(Server.PLAYER_INFO);

            //Add player to the list, necessary to spawn them
            spawnPackets[0].getModifier().write(0, ReflectionManager.getEnumPlayerInfoAction(0));
            List playerList = new ArrayList();
            playerList.add(ReflectionManager.getPlayerInfoData(spawnPackets[0].getHandle(), playerDisguise.getGameProfile()));
            spawnPackets[0].getModifier().write(1, playerList);

            //Remove player from the list
            PacketContainer delayedPacket = spawnPackets[0].shallowClone();
            delayedPacket.getModifier().write(0, ReflectionManager.getEnumPlayerInfoAction(4));
            delayedPackets = new PacketContainer[]{delayedPacket};
        } else if (disguise.getType().isMob() || disguise.getType() == DisguiseType.ARMOR_STAND) {
            Vector vec = disguisedEntity.getVelocity();
            spawnPackets[0] = new PacketContainer(PacketType.Play.Server.SPAWN_ENTITY_LIVING);
            StructureModifier<Object> mods = spawnPackets[0].getModifier();
            mods.write(0, disguisedEntity.getEntityId());
            mods.write(1, UUID.randomUUID());
            mods.write(2, disguise.getType().getTypeId());
            //region Vector calculations
            double d1 = 3.9D;
            double d2 = vec.getX();
            double d3 = vec.getY();
            double d4 = vec.getZ();
            if (d2 < -d1)
                d2 = -d1;
            if (d3 < -d1)
                d3 = -d1;
            if (d4 < -d1)
                d4 = -d1;
            if (d2 > d1)
                d2 = d1;
            if (d3 > d1)
                d3 = d1;
            if (d4 > d1)
                d4 = d1;
            //endregion
            mods.write(3, loc.getX());
            mods.write(4, loc.getY());
            mods.write(5, loc.getZ());
            mods.write(6, (int) (d2 * 8000.0D));
            mods.write(7, (int) (d3 * 8000.0D));
            mods.write(8, (int) (d4 * 8000.0D));
            mods.write(9, yaw);
            mods.write(10, pitch);
            spawnPackets[0].getDataWatcherModifier().write(0,
                    createDataWatcher(WrappedDataWatcher.getEntityWatcher(disguisedEntity), disguise.getWatcher()));
        } else if (disguise.getType().isMisc()) {
            int objectId = disguise.getType().getObjectId();
            int data = ((MiscDisguise) disguise).getData();
            if (disguise.getType() == DisguiseType.FALLING_BLOCK) {
                data = ReflectionManager.getCombinedId(((MiscDisguise) disguise).getId(), data);
            } else if (disguise.getType() == DisguiseType.FISHING_HOOK && data == 0) {
                // If the MiscDisguise data isn't set. Then no entity id was provided, so default to the owners entity id
                data = disguisedEntity.getEntityId();
            } else if (disguise.getType() == DisguiseType.ITEM_FRAME) {
                data = ((((int) loc.getYaw() % 360) + 720 + 45) / 90) % 4;
            }
            Object nmsEntity = ReflectionManager.getNmsEntity(disguisedEntity);
            spawnPackets[0] = ProtocolLibrary.getProtocolManager()
                    .createPacketConstructor(PacketType.Play.Server.SPAWN_ENTITY, nmsEntity, objectId, data)
                    .createPacket(nmsEntity, objectId, data);
            spawnPackets[0].getModifier().write(8, pitch);
            spawnPackets[0].getModifier().write(9, yaw);
            if (disguise.getType() == DisguiseType.ITEM_FRAME) {
                if (data % 2 == 0) {
                    spawnPackets[0].getModifier().write(4, (int) Math.floor((loc.getZ() + (data == 0 ? -1 : 1)) * 32D));
                } else {
                    spawnPackets[0].getModifier().write(2, (int) Math.floor((loc.getX() + (data == 3 ? -1 : 1)) * 32D));
                }
            }
        }
        if (spawnPackets[1] == null || disguise.isPlayerDisguise()) {
            int entry = spawnPackets[1] == null ? 1 : 0;
            if (entry == 0) {
                entry = spawnPackets.length;
                spawnPackets = Arrays.copyOf(spawnPackets, spawnPackets.length + 1);
            }
            // Make a packet to turn his head!
            spawnPackets[entry] = new PacketContainer(Server.ENTITY_HEAD_ROTATION);
            StructureModifier<Object> mods = spawnPackets[entry].getModifier();
            mods.write(0, disguisedEntity.getEntityId());
            mods.write(1, yaw);
        }
        return new PacketContainer[][]{spawnPackets, delayedPackets};
    }

    /**
     * Create a new datawatcher but with the 'correct' values
     */
    private static WrappedDataWatcher createDataWatcher(WrappedDataWatcher watcher, FlagWatcher flagWatcher) {
        WrappedDataWatcher newWatcher = new WrappedDataWatcher();
        try {
            List<WrappedWatchableObject> list = DisguiseConfig.isMetadataPacketsEnabled() ? flagWatcher.convert(watcher.getWatchableObjects()) : flagWatcher.getWatchableObjects();
            for (WrappedWatchableObject watchableObject : list) {
                if (watchableObject == null) continue;
                if (watchableObject.getValue() == null) continue;
                if (Registry.get(watchableObject.getValue().getClass()) == null) continue;
                WrappedDataWatcherObject obj = new WrappedDataWatcherObject(watchableObject.getIndex(), Registry.get(watchableObject.getValue().getClass()));
                newWatcher.setObject(obj, watchableObject.getValue());
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return newWatcher;
    }

    public static byte getPitch(DisguiseType disguiseType, DisguiseType entityType, byte value) {
        switch (disguiseType) {
            case MINECART:
            case MINECART_CHEST:
            case MINECART_COMMAND:
            case MINECART_FURNACE:
            case MINECART_HOPPER:
            case MINECART_MOB_SPAWNER:
            case MINECART_TNT:
                value = (byte) -value;
                break;
            default:
                break;
        }
        switch (entityType) {
            case MINECART:
            case MINECART_CHEST:
            case MINECART_FURNACE:
            case MINECART_HOPPER:
            case MINECART_MOB_SPAWNER:
            case MINECART_TNT:
                value = (byte) -value;
                break;
            default:
                break;
        }
        return value;
    }

    /**
     * Add the yaw for the disguises
     */
    public static byte getYaw(DisguiseType disguiseType, EntityType entityType, byte value) {
        switch (disguiseType) {
            case MINECART:
            case MINECART_CHEST:
            case MINECART_COMMAND:
            case MINECART_FURNACE:
            case MINECART_HOPPER:
            case MINECART_MOB_SPAWNER:
            case MINECART_TNT:
                value += 64;
                break;
            case ENDER_DRAGON:
                value -= 128;
                break;
            case WITHER_SKULL:
                value -= 128;
                break;
            case ARROW:
            case TIPPED_ARROW:
            case SPECTRAL_ARROW:
                value = (byte) -value;
                break;
            case PAINTING:
            case ITEM_FRAME:
                value = (byte) -(value + 128);
                break;
            default:
                if (disguiseType.isMisc() && disguiseType != DisguiseType.ARMOR_STAND) {
                    value -= 64;
                }
                break;
        }
        switch (entityType) {
            case MINECART:
            case MINECART_CHEST:
            case MINECART_FURNACE:
            case MINECART_HOPPER:
            case MINECART_MOB_SPAWNER:
            case MINECART_TNT:
                value -= 64;
                break;
            case ENDER_DRAGON:
            case WITHER_SKULL:
                value += 128;
                break;
            case ARROW:
                value = (byte) -value;
                break;
            case PAINTING:
            case ITEM_FRAME:
                value = (byte) -(value - 128);
                break;
            default:
                if (!entityType.isAlive()) {
                    value += 64;
                }
                break;
        }
        return value;
    }

    /**
     * Get the Y level to add to the disguise for realism.
     */
    public static double getYModifier(Entity entity, Disguise disguise) {
        double yMod = 0;
        if ((disguise.getType() != DisguiseType.PLAYER || !((PlayerWatcher) disguise.getWatcher()).isSleeping())
                && entity.getType() == EntityType.DROPPED_ITEM) {
            yMod -= 0.13;
        }
        switch (disguise.getType()) {
            case BAT:
                if (entity instanceof LivingEntity)
                    return yMod + ((LivingEntity) entity).getEyeHeight();
            case MINECART:
            case MINECART_COMMAND:
            case MINECART_CHEST:
            case MINECART_FURNACE:
            case MINECART_HOPPER:
            case MINECART_MOB_SPAWNER:
            case MINECART_TNT:
                switch (entity.getType()) {
                    case MINECART:
                    case MINECART_CHEST:
                    case MINECART_FURNACE:
                    case MINECART_HOPPER:
                    case MINECART_MOB_SPAWNER:
                    case MINECART_TNT:
                        return yMod;
                    default:
                        return yMod + 0.4;
                }
            case TIPPED_ARROW:
            case SPECTRAL_ARROW:
            case BOAT:
            case EGG:
            case ENDER_PEARL:
            case ENDER_SIGNAL:
            case FIREWORK:
            case PAINTING:
            case SMALL_FIREBALL:
            case SNOWBALL:
            case SPLASH_POTION:
            case THROWN_EXP_BOTTLE:
            case WITHER_SKULL:
                return yMod + 0.7;
            case PLAYER:
                if (DisguiseConfig.isBedPacketsEnabled() && ((PlayerWatcher) disguise.getWatcher()).isSleeping()) {
                    return yMod + 0.35;
                }
                break;
            case DROPPED_ITEM:
                return yMod + 0.13;
            default:
                break;
        }
        return yMod;
    }

    /**
     * Creates the packet listeners
     */
    public static void init(LibsDisguises plugin) {
        libsDisguises = plugin;
        soundsListener = new PacketAdapter(libsDisguises, ListenerPriority.NORMAL, Server.NAMED_SOUND_EFFECT, Server.ENTITY_STATUS) {
            @Override
            public void onPacketSending(PacketEvent event) {
                if (event.isCancelled()) {
                    return;
                }
                if (event.isAsync()) {
                    return;
                }
                if (event.getPlayer().getName().contains("UNKNOWN[")) //If the player is temporary
                    return;
                event.setPacket(event.getPacket().deepClone());
                StructureModifier<Object> mods = event.getPacket().getModifier();
                Player observer = event.getPlayer();
                if (event.getPacketType() == Server.NAMED_SOUND_EFFECT) {
                    Object soundEffect = mods.read(0);
                    SoundType soundType = null;
                    Location soundLoc = new Location(observer.getWorld(), ((Integer) mods.read(2)) / 8D,
                            ((Integer) mods.read(3)) / 8D, ((Integer) mods.read(4)) / 8D);
                    Entity disguisedEntity = null;
                    DisguiseSound entitySound = null;
                    Disguise disguise = null;
                    Entity[] entities = soundLoc.getChunk().getEntities();
                    for (Entity entity : entities) {
                        Disguise entityDisguise = DisguiseAPI.getDisguise(observer, entity);
                        if (entityDisguise != null) {
                            Location loc = entity.getLocation();
                            loc = new Location(observer.getWorld(), ((int) (loc.getX() * 8)) / 8D, ((int) (loc.getY() * 8)) / 8D,
                                    ((int) (loc.getZ() * 8)) / 8D);
                            if (loc.equals(soundLoc)) {
                                entitySound = DisguiseSound.getType(entity.getType().name());
                                if (entitySound != null) {
                                    Object obj = null;
                                    if (entity instanceof LivingEntity) {
                                        try {
                                            // Use reflection so that this works for either int or double methods
                                            obj = LivingEntity.class.getMethod("getHealth").invoke(entity);
                                            if (obj instanceof Double ? (Double) obj == 0 : (Integer) obj == 0) {
                                                soundType = SoundType.DEATH;
                                            } else {
                                                obj = null;
                                            }
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                    }
                                    if (obj == null) {
                                        boolean hasInvun = false;
                                        Object nmsEntity = ReflectionManager.getNmsEntity(entity);
                                        try {
                                            if (entity instanceof LivingEntity) {
                                                hasInvun = ReflectionManager.getNmsField("Entity", "noDamageTicks").getInt(
                                                        nmsEntity) == ReflectionManager.getNmsField("EntityLiving",
                                                        "maxNoDamageTicks").getInt(nmsEntity);
                                            } else {
                                                Class clazz = ReflectionManager.getNmsClass("DamageSource");
                                                hasInvun = (Boolean) ReflectionManager.getNmsMethod("Entity", "isInvulnerable", clazz)
                                                        .invoke(nmsEntity, ReflectionManager.getNmsField(clazz, "GENERIC"));
                                            }
                                        } catch (Exception ex) {
                                            ex.printStackTrace();
                                        }
                                        soundType = entitySound.getType(ReflectionManager.convertSoundEffectToString(soundEffect), !hasInvun);
                                    }
                                    if (soundType != null) {
                                        disguise = entityDisguise;
                                        disguisedEntity = entity;
                                        break;
                                    }
                                }
                            }
                        }
                    }
                    if (disguise != null) {
                        if (disguise.isSelfDisguiseSoundsReplaced() || disguisedEntity != event.getPlayer()) {
                            if (disguise.isSoundsReplaced()) {
                                String sound = null;
                                DisguiseSound dSound = DisguiseSound.getType(disguise.getType().name());
                                if (dSound != null)
                                    sound = dSound.getSound(soundType);

                                if (sound == null) {
                                    event.setCancelled(true);
                                } else {
                                    if (sound.equals("step.grass")) {
                                        try {
                                            int typeId = soundLoc.getWorld().getBlockTypeIdAt(soundLoc.getBlockX(),
                                                    soundLoc.getBlockY() - 1, soundLoc.getBlockZ());
                                            Object block = ReflectionManager.getNmsMethod("RegistryMaterials", "a", int.class)
                                                    .invoke(ReflectionManager.getNmsField("Block", "REGISTRY").get(null),
                                                            typeId);
                                            if (block != null) {
                                                Object step = ReflectionManager.getNmsField("Block", "stepSound").get(block);
                                                mods.write(0, ReflectionManager.getNmsMethod(step.getClass(), "getStepSound").invoke(step));
                                            }
                                        } catch (Exception ex) {
                                            ex.printStackTrace();
                                        }
                                        // There is no else statement. Because seriously. This should never be null. Unless
                                        // someone is
                                        // sending fake sounds. In which case. Why cancel it.
                                    } else {
                                        mods.write(0, ReflectionManager.getCraftSoundEffect(sound));
                                        // Time to change the pitch and volume
                                        if (soundType == SoundType.HURT || soundType == SoundType.DEATH
                                                || soundType == SoundType.IDLE) {
                                            // If the volume is the default
                                            if (mods.read(5).equals(entitySound.getDamageAndIdleSoundVolume())) {
                                                mods.write(5, dSound.getDamageAndIdleSoundVolume());
                                            }
                                            // Here I assume its the default pitch as I can't calculate if its real.
                                            if (disguise instanceof MobDisguise && disguisedEntity instanceof LivingEntity
                                                    && ((MobDisguise) disguise).doesDisguiseAge()) {
                                                boolean baby = false;
                                                if (disguisedEntity instanceof Zombie) {
                                                    baby = ((Zombie) disguisedEntity).isBaby();
                                                } else if (disguisedEntity instanceof Ageable) {
                                                    baby = !((Ageable) disguisedEntity).isAdult();
                                                }
                                                if (((MobDisguise) disguise).isAdult() == baby) {

                                                    float pitch = (Integer) mods.read(5);
                                                    if (baby) {
                                                        // If the pitch is not the expected
                                                        if (pitch > 97 || pitch < 111)
                                                            return;
                                                        pitch = (DisguiseUtilities.random.nextFloat() - DisguiseUtilities.random.nextFloat()) * 0.2F + 1.5F;
                                                        // Min = 1.5
                                                        // Cap = 97.5
                                                        // Max = 1.7
                                                        // Cap = 110.5
                                                    } else {
                                                        // If the pitch is not the expected
                                                        if (pitch >= 63 || pitch <= 76)
                                                            return;
                                                        pitch = (DisguiseUtilities.random.nextFloat() - DisguiseUtilities.random.nextFloat()) * 0.2F + 1.0F;
                                                        // Min = 1
                                                        // Cap = 63
                                                        // Max = 1.2
                                                        // Cap = 75.6
                                                    }
                                                    pitch *= 63;
                                                    if (pitch < 0)
                                                        pitch = 0;
                                                    if (pitch > 255)
                                                        pitch = 255;
                                                    mods.write(6, (int) pitch);
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else if (event.getPacketType() == Server.ENTITY_STATUS) {
                    if ((byte) mods.read(1) == 2) {
                        // It made a damage animation
                        Entity entity = event.getPacket().getEntityModifier(observer.getWorld()).read(0);
                        Disguise disguise = DisguiseAPI.getDisguise(observer, entity);
                        if (disguise != null && !disguise.getType().isPlayer()
                                && (disguise.isSelfDisguiseSoundsReplaced() || entity != event.getPlayer())) {
                            DisguiseSound disSound = DisguiseSound.getType(entity.getType().name());
                            if (disSound == null)
                                return;
                            SoundType soundType = null;
                            Object obj = null;
                            if (entity instanceof LivingEntity) {
                                try {
                                    obj = LivingEntity.class.getMethod("getHealth").invoke(entity);
                                    if (obj instanceof Double ? (Double) obj == 0 : (Integer) obj == 0) {
                                        soundType = SoundType.DEATH;
                                    } else {
                                        obj = null;
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                            if (obj == null) {
                                soundType = SoundType.HURT;
                            }
                            if (disSound.getSound(soundType) == null
                                    || (disguise.isSelfDisguiseSoundsReplaced() && entity == event.getPlayer())) {
                                if (disguise.isSelfDisguiseSoundsReplaced() && entity == event.getPlayer()) {
                                    cancelSound = !cancelSound;
                                    if (cancelSound)
                                        return;
                                }
                                disSound = DisguiseSound.getType(disguise.getType().name());
                                if (disSound != null) {
                                    String sound = disSound.getSound(soundType);
                                    if (sound != null) {
                                        Location loc = entity.getLocation();
                                        PacketContainer packet = new PacketContainer(Server.NAMED_SOUND_EFFECT);
                                        mods = packet.getModifier();
                                        Object craftSoundEffect = ReflectionManager.getCraftSoundEffect(sound);
                                        mods.write(0, craftSoundEffect);
                                        mods.write(1, ReflectionManager.getSoundCategory("master")); //Meh
                                        mods.write(2, (int) (loc.getX() * 8D));
                                        mods.write(3, (int) (loc.getY() * 8D));
                                        mods.write(4, (int) (loc.getZ() * 8D));
                                        mods.write(5, disSound.getDamageAndIdleSoundVolume());
                                        float pitch;
                                        if (disguise instanceof MobDisguise && !((MobDisguise) disguise).isAdult()) {
                                            pitch = (DisguiseUtilities.random.nextFloat() - DisguiseUtilities.random.nextFloat()) * 0.2F + 1.5F;
                                        } else
                                            pitch = (DisguiseUtilities.random.nextFloat() - DisguiseUtilities.random.nextFloat()) * 0.2F + 1.0F;
                                        if (disguise.getType() == DisguiseType.BAT)
                                            pitch *= 95F;
                                        pitch *= 63;
                                        if (pitch < 0)
                                            pitch = 0;
                                        if (pitch > 255)
                                            pitch = 255;
                                        mods.write(6, (int) pitch);
                                        try {
                                            ProtocolLibrary.getProtocolManager().sendServerPacket(observer, packet);
                                        } catch (InvocationTargetException e) {
                                            e.printStackTrace();
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        };
        //Self disguise (/vsd) listener
        viewDisguisesListener = new PacketAdapter(libsDisguises, ListenerPriority.HIGH,
                Server.NAMED_ENTITY_SPAWN, Server.ATTACH_ENTITY,
                Server.REL_ENTITY_MOVE, Server.ENTITY_MOVE_LOOK,
                Server.ENTITY_LOOK, Server.ENTITY_TELEPORT,
                Server.ENTITY_HEAD_ROTATION, Server.ENTITY_METADATA,
                Server.ENTITY_EQUIPMENT, Server.ANIMATION, Server.BED,
                Server.ENTITY_EFFECT, Server.ENTITY_VELOCITY,
                Server.UPDATE_ATTRIBUTES, Server.ENTITY_STATUS) {
            @Override
            public void onPacketSending(PacketEvent event) {
                if (event.isCancelled())
                    return;
                if (event.getPlayer().getName().contains("UNKNOWN[")) //If the player is temporary
                    return;
                final Player observer = event.getPlayer();
                if (event.getPacket().getIntegers().read(0) == observer.getEntityId()) {
                    if (DisguiseAPI.isSelfDisguised(observer)) {
                        // Here I grab the packets to convert them to, So I can display them as if the disguise sent them.
                        PacketContainer[][] transformed = transformPacket(event.getPacket(), observer, observer);
                        PacketContainer[] packets = transformed == null ? null : transformed[0];
                        final PacketContainer[] delayedPackets = transformed == null ? null : transformed[1];
                        if (packets == null) {
                            packets = new PacketContainer[]{event.getPacket()};
                        }
                        for (PacketContainer packet : packets) {
                            if (packet.getType() != Server.PLAYER_INFO) {
                                if (packet.equals(event.getPacket())) {
                                    packet = packet.shallowClone();
                                }
                                packet.getIntegers().write(0, DisguiseAPI.getSelfDisguiseId());
                            }
                            try {
                                ProtocolLibrary.getProtocolManager().sendServerPacket(observer, packet, false);
                            } catch (InvocationTargetException e) {
                                e.printStackTrace();
                            }
                        }
                        if (delayedPackets != null && delayedPackets.length > 0) {
                            Bukkit.getScheduler().scheduleSyncDelayedTask(libsDisguises, new Runnable() {
                                public void run() {
                                    try {
                                        for (PacketContainer packet : delayedPackets) {
                                            ProtocolLibrary.getProtocolManager().sendServerPacket(observer, packet, false);
                                        }
                                    } catch (InvocationTargetException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }, 2);
                        }
                        if (event.getPacketType() == Server.ENTITY_METADATA) {
                            event.setPacket(event.getPacket().deepClone());
                            for (WrappedWatchableObject watch : event.getPacket().getWatchableCollectionModifier().read(0)) {
                                if (watch.getIndex() == 0) {
                                    byte b = (byte) watch.getValue();
                                    byte a = (byte) (b | 1 << 5);
                                    if ((b & 1 << 3) != 0)
                                        a = (byte) (a | 1 << 3);
                                    watch.setValue(a);
                                }
                            }
                        } else if (event.getPacketType() == Server.NAMED_ENTITY_SPAWN) {
                            event.setCancelled(true);
                            PacketContainer packet = new PacketContainer(Server.ENTITY_METADATA);
                            StructureModifier<Object> mods = packet.getModifier();
                            mods.write(0, observer.getEntityId());
                            List<WrappedWatchableObject> watchableList = new ArrayList<>();
                            Byte b = 1 << 5;
                            if (observer.isSprinting())
                                b = (byte) (b | 1 << 3);
                            WrappedWatchableObject watch = new WrappedWatchableObject(ReflectionManager.createDataWatcherItem(0, b));
                            watchableList.add(watch);
                            packet.getWatchableCollectionModifier().write(0, watchableList);
                            try {
                                ProtocolLibrary.getProtocolManager().sendServerPacket(observer, packet);
                            } catch (InvocationTargetException e) {
                                e.printStackTrace();
                            }
                        } else if (event.getPacketType() == Server.ANIMATION) {
                            if (event.getPacket().getIntegers().read(1) != 2) {
                                event.setCancelled(true);
                            }
                        } else if (event.getPacketType() == Server.ATTACH_ENTITY
                                || event.getPacketType() == Server.REL_ENTITY_MOVE
                                || event.getPacketType() == Server.ENTITY_MOVE_LOOK
                                || event.getPacketType() == Server.ENTITY_LOOK
                                || event.getPacketType() == Server.ENTITY_TELEPORT
                                || event.getPacketType() == Server.ENTITY_HEAD_ROTATION
                                || event.getPacketType() == Server.ENTITY_EFFECT
                                || event.getPacketType() == Server.ENTITY_EQUIPMENT) {
                            event.setCancelled(true);
                        } else if (event.getPacketType() == Server.ENTITY_STATUS) {
                            Disguise disguise = DisguiseAPI.getDisguise(event.getPlayer(), event.getPlayer());
                            if (disguise.isSelfDisguiseSoundsReplaced() && !disguise.getType().isPlayer()
                                    && event.getPacket().getBytes().read(0) == 2) {
                                event.setCancelled(true);
                            }
                        }
                    }
                }
            }
        };
        inventoryListener = new PacketAdapter(libsDisguises, ListenerPriority.HIGHEST, Server.SET_SLOT,
                Server.WINDOW_ITEMS, PacketType.Play.Client.HELD_ITEM_SLOT,
                PacketType.Play.Client.SET_CREATIVE_SLOT, PacketType.Play.Client.WINDOW_CLICK) {
            @Override
            public void onPacketReceiving(final PacketEvent event) {
                if (event.isCancelled())
                    return;
                if (event.getPlayer().getName().contains("UNKNOWN[")) //If the player is temporary
                    return;
                if (!(event.getPlayer() instanceof com.comphenix.net.sf.cglib.proxy.Factory)
                        && event.getPlayer().getVehicle() == null) {
                    Disguise disguise = DisguiseAPI.getDisguise(event.getPlayer(), event.getPlayer());
                    // If player is disguised, views self disguises and has a inventory modifier
                    if (disguise != null && disguise.isSelfDisguiseVisible() && (disguise.isHidingArmorFromSelf() || disguise.isHidingHeldItemFromSelf())) {
                        // If they are in creative and clicked on a slot
                        if (event.getPacketType() == PacketType.Play.Client.SET_CREATIVE_SLOT) {
                            int slot = event.getPacket().getIntegers().read(0);
                            if (slot >= 5 && slot <= 8) {
                                if (disguise.isHidingArmorFromSelf()) {
                                    int armorSlot = Math.abs((slot - 5) - 3);
                                    org.bukkit.inventory.ItemStack item = event.getPlayer().getInventory().getArmorContents()[armorSlot];
                                    if (item != null && item.getType() != Material.AIR) {
                                        PacketContainer packet = new PacketContainer(Server.SET_SLOT);
                                        StructureModifier<Object> mods = packet.getModifier();
                                        mods.write(0, 0);
                                        mods.write(1, slot);
                                        mods.write(2, ReflectionManager.getNmsItem(new org.bukkit.inventory.ItemStack(0)));
                                        try {
                                            ProtocolLibrary.getProtocolManager().sendServerPacket(event.getPlayer(), packet,
                                                    false);
                                        } catch (InvocationTargetException e) {
                                            e.printStackTrace();
                                        }
                                    }
                                }
                            } else if (slot >= 36 && slot <= 44) {
                                if (disguise.isHidingHeldItemFromSelf()) {
                                    int currentSlot = event.getPlayer().getInventory().getHeldItemSlot();
                                    if (slot + 36 == currentSlot) {
                                        org.bukkit.inventory.ItemStack item = event.getPlayer().getItemInHand();
                                        if (item != null && item.getType() != Material.AIR) {
                                            PacketContainer packet = new PacketContainer(Server.SET_SLOT);
                                            StructureModifier<Object> mods = packet.getModifier();
                                            mods.write(0, 0);
                                            mods.write(1, slot);
                                            mods.write(2, ReflectionManager.getNmsItem(new org.bukkit.inventory.ItemStack(0)));
                                            try {
                                                ProtocolLibrary.getProtocolManager().sendServerPacket(event.getPlayer(), packet,
                                                        false);
                                            } catch (InvocationTargetException e) {
                                                e.printStackTrace();
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        // If the player switched item, aka he moved from slot 1 to slot 2
                        else if (event.getPacketType() == PacketType.Play.Client.HELD_ITEM_SLOT) {
                            if (disguise.isHidingHeldItemFromSelf()) {
                                // From logging, it seems that both bukkit and nms uses the same thing for the slot switching.
                                // 0 1 2 3 - 8
                                // If the packet is coming, then I need to replace the item they are switching to
                                // As for the old item, I need to restore it.
                                org.bukkit.inventory.ItemStack currentlyHeld = event.getPlayer().getItemInHand();
                                // If his old weapon isn't air
                                if (currentlyHeld != null && currentlyHeld.getType() != Material.AIR) {
                                    PacketContainer packet = new PacketContainer(Server.SET_SLOT);
                                    StructureModifier<Object> mods = packet.getModifier();
                                    mods.write(0, 0);
                                    mods.write(1, event.getPlayer().getInventory().getHeldItemSlot() + 36);
                                    mods.write(2, ReflectionManager.getNmsItem(currentlyHeld));
                                    try {
                                        ProtocolLibrary.getProtocolManager().sendServerPacket(event.getPlayer(), packet, false);
                                    } catch (InvocationTargetException e) {
                                        e.printStackTrace();
                                    }
                                }
                                org.bukkit.inventory.ItemStack newHeld = event.getPlayer().getInventory()
                                        .getItem(event.getPacket().getIntegers().read(0));
                                // If his new weapon isn't air either!
                                if (newHeld != null && newHeld.getType() != Material.AIR) {
                                    PacketContainer packet = new PacketContainer(Server.SET_SLOT);
                                    StructureModifier<Object> mods = packet.getModifier();
                                    mods.write(0, 0);
                                    mods.write(1, event.getPacket().getIntegers().read(0) + 36);
                                    mods.write(2, ReflectionManager.getNmsItem(new org.bukkit.inventory.ItemStack(0)));
                                    try {
                                        ProtocolLibrary.getProtocolManager().sendServerPacket(event.getPlayer(), packet, false);
                                    } catch (InvocationTargetException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                        } else if (event.getPacketType() == PacketType.Play.Client.WINDOW_CLICK) {
                            int slot = event.getPacket().getIntegers().read(1);
                            org.bukkit.inventory.ItemStack clickedItem;
                            if (event.getPacket().getShorts().read(0) == 1) {
                                // Its a shift click
                                clickedItem = event.getPacket().getItemModifier().read(0);
                                if (clickedItem != null && clickedItem.getType() != Material.AIR) {
                                    // Rather than predict the clients actions
                                    // Lets just update the entire inventory..
                                    Bukkit.getScheduler().runTask(libsDisguises, new Runnable() {
                                        public void run() {
                                            event.getPlayer().updateInventory();
                                        }
                                    });
                                }
                                return;
                            } else {
                                // If its not a player inventory click
                                // Shift clicking is exempted for the item in hand..
                                if (event.getPacket().getIntegers().read(0) != 0) {
                                    return;
                                }
                                clickedItem = event.getPlayer().getItemOnCursor();
                            }
                            if (clickedItem != null && clickedItem.getType() != Material.AIR) {
                                // If the slot is a armor slot
                                if (slot >= 5 && slot <= 8) {
                                    if (disguise.isHidingArmorFromSelf()) {
                                        PacketContainer packet = new PacketContainer(Server.SET_SLOT);
                                        StructureModifier<Object> mods = packet.getModifier();
                                        mods.write(0, 0);
                                        mods.write(1, slot);
                                        mods.write(2, ReflectionManager.getNmsItem(new org.bukkit.inventory.ItemStack(0)));
                                        try {
                                            ProtocolLibrary.getProtocolManager().sendServerPacket(event.getPlayer(), packet,
                                                    false);
                                        } catch (InvocationTargetException e) {
                                            e.printStackTrace();
                                        }
                                    }
                                    // Else if its a hotbar slot
                                } else if (slot >= 36 && slot <= 44) {
                                    if (disguise.isHidingHeldItemFromSelf()) {
                                        int currentSlot = event.getPlayer().getInventory().getHeldItemSlot();
                                        // Check if the player is on the same slot as the slot that its setting
                                        if (slot == currentSlot + 36) {
                                            PacketContainer packet = new PacketContainer(Server.SET_SLOT);
                                            StructureModifier<Object> mods = packet.getModifier();
                                            mods.write(0, 0);
                                            mods.write(1, slot);
                                            mods.write(2, ReflectionManager.getNmsItem(new org.bukkit.inventory.ItemStack(0)));
                                            try {
                                                ProtocolLibrary.getProtocolManager().sendServerPacket(event.getPlayer(), packet,
                                                        false);
                                            } catch (InvocationTargetException e) {
                                                e.printStackTrace();
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            @Override
            public void onPacketSending(PacketEvent event) {
                // If the inventory is the players inventory
                if (!(event.getPlayer() instanceof com.comphenix.net.sf.cglib.proxy.Factory)
                        && event.getPlayer().getVehicle() == null && event.getPacket().getIntegers().read(0) == 0) {
                    Disguise disguise = DisguiseAPI.getDisguise(event.getPlayer(), event.getPlayer());
                    // If the player is disguised, views self disguises and is hiding a item.
                    if (disguise != null && disguise.isSelfDisguiseVisible()
                            && (disguise.isHidingArmorFromSelf() || disguise.isHidingHeldItemFromSelf())) {
                        // If the server is setting the slot
                        // Need to set it to air if its in a place it shouldn't be.
                        // Things such as picking up a item, spawned in item. Plugin sets the item. etc. Will fire this
                        /**
                         * Done
                         */
                        if (event.getPacketType() == Server.SET_SLOT) {
                            // The raw slot
                            // nms code has the start of the hotbar being 36.
                            int slot = event.getPacket().getIntegers().read(1);
                            // If the slot is a armor slot
                            if (slot >= 5 && slot <= 8) {
                                if (disguise.isHidingArmorFromSelf()) {
                                    // Get the bukkit armor slot!
                                    int armorSlot = Math.abs((slot - 5) - 3);
                                    org.bukkit.inventory.ItemStack item = event.getPlayer().getInventory().getArmorContents()[armorSlot];
                                    if (item != null && item.getType() != Material.AIR) {
                                        event.setPacket(event.getPacket().shallowClone());
                                        event.getPacket().getModifier()
                                                .write(2, ReflectionManager.getNmsItem(new org.bukkit.inventory.ItemStack(0)));
                                    }
                                }
                                // Else if its a hotbar slot
                            } else if (slot >= 36 && slot <= 44) {
                                if (disguise.isHidingHeldItemFromSelf()) {
                                    int currentSlot = event.getPlayer().getInventory().getHeldItemSlot();
                                    // Check if the player is on the same slot as the slot that its setting
                                    if (slot == currentSlot + 36) {
                                        org.bukkit.inventory.ItemStack item = event.getPlayer().getItemInHand();
                                        if (item != null && item.getType() != Material.AIR) {
                                            event.setPacket(event.getPacket().shallowClone());
                                            event.getPacket()
                                                    .getModifier()
                                                    .write(2, ReflectionManager.getNmsItem(new org.bukkit.inventory.ItemStack(0)));
                                        }
                                    }
                                }
                            }
                        } else if (event.getPacketType() == Server.WINDOW_ITEMS) {
                            event.setPacket(event.getPacket().deepClone());
                            StructureModifier<ItemStack[]> mods = event.getPacket().getItemArrayModifier();
                            ItemStack[] items = mods.read(0);
                            for (int slot = 0; slot < items.length; slot++) {
                                if (slot >= 5 && slot <= 8) {
                                    if (disguise.isHidingArmorFromSelf()) {
                                        // Get the bukkit armor slot!
                                        int armorSlot = Math.abs((slot - 5) - 3);
                                        org.bukkit.inventory.ItemStack item = event.getPlayer().getInventory().getArmorContents()[armorSlot];
                                        if (item != null && item.getType() != Material.AIR) {
                                            items[slot] = new org.bukkit.inventory.ItemStack(0);
                                        }
                                    }
                                    // Else if its a hotbar slot
                                } else if (slot >= 36 && slot <= 44) {
                                    if (disguise.isHidingHeldItemFromSelf()) {
                                        int currentSlot = event.getPlayer().getInventory().getHeldItemSlot();
                                        // Check if the player is on the same slot as the slot that its setting
                                        if (slot == currentSlot + 36) {
                                            org.bukkit.inventory.ItemStack item = event.getPlayer().getItemInHand();
                                            if (item != null && item.getType() != Material.AIR) {
                                                items[slot] = new org.bukkit.inventory.ItemStack(0);
                                            }
                                        }
                                    }
                                }
                            }
                            mods.write(0, items);
                        }
                    }
                }
            }
        };
    }

    public static boolean isHearDisguisesEnabled() {
        return soundsListenerEnabled;
    }

    public static boolean isInventoryListenerEnabled() {
        return inventoryModifierEnabled;
    }

    public static boolean isViewDisguisesListenerEnabled() {
        return viewDisguisesListenerEnabled;
    }

    public static void setHearDisguisesListener(boolean enabled) {
        //TODO: FIX SOUNDS
//        if (soundsListenerEnabled != enabled) {
//            soundsListenerEnabled = enabled;
//            if (soundsListenerEnabled) {
//                ProtocolLibrary.getProtocolManager().addPacketListener(soundsListener);
//            } else {
//                ProtocolLibrary.getProtocolManager().removePacketListener(soundsListener);
//            }
//        }
    }

    public static void setInventoryListenerEnabled(boolean enabled) {
        if (inventoryModifierEnabled != enabled) {
            inventoryModifierEnabled = enabled;
            if (inventoryModifierEnabled) {
                ProtocolLibrary.getProtocolManager().addPacketListener(inventoryListener);
            } else {
                ProtocolLibrary.getProtocolManager().removePacketListener(inventoryListener);
            }
            for (Player player : Bukkit.getOnlinePlayers()) {
                Disguise disguise = DisguiseAPI.getDisguise(player, player);
                if (disguise != null) {
                    if (viewDisguisesListenerEnabled && disguise.isSelfDisguiseVisible()
                            && (disguise.isHidingArmorFromSelf() || disguise.isHidingHeldItemFromSelf())) {
                        player.updateInventory();
                    }
                }
            }
        }
    }

    public static void setupMainPacketsListener() {
        if (clientInteractEntityListener != null) {
            if (mainListener != null) {
                ProtocolLibrary.getProtocolManager().removePacketListener(mainListener);
            }
            List<PacketType> packetsToListen = new ArrayList<>();
            // Add spawn packets
            {
                packetsToListen.add(Server.NAMED_ENTITY_SPAWN);
                packetsToListen.add(Server.SPAWN_ENTITY_EXPERIENCE_ORB);
                packetsToListen.add(Server.SPAWN_ENTITY);
                packetsToListen.add(Server.SPAWN_ENTITY_LIVING);
                packetsToListen.add(Server.SPAWN_ENTITY_PAINTING);
            }
            // Add packets that always need to be enabled to ensure safety
            {
                packetsToListen.add(Server.ENTITY_METADATA);
            }
            if (DisguiseConfig.isCollectPacketsEnabled()) {
                packetsToListen.add(Server.COLLECT);
            }
            if (DisguiseConfig.isMiscDisguisesForLivingEnabled()) {
                packetsToListen.add(Server.UPDATE_ATTRIBUTES);
            }
            // The bed packet.
            if (DisguiseConfig.isBedPacketsEnabled()) {
                packetsToListen.add(Server.BED);
            }
            // Add movement packets
            if (DisguiseConfig.isMovementPacketsEnabled()) {
                packetsToListen.add(Server.ENTITY_LOOK);
                packetsToListen.add(Server.ENTITY_MOVE_LOOK);
                packetsToListen.add(Server.ENTITY_HEAD_ROTATION);
                packetsToListen.add(Server.ENTITY_TELEPORT);
                packetsToListen.add(Server.REL_ENTITY_MOVE);
            }
            // Add equipment packet
            if (DisguiseConfig.isEquipmentPacketsEnabled()) {
                packetsToListen.add(Server.ENTITY_EQUIPMENT);
            }
            // Add the packet that ensures if they are sleeping or not
            if (DisguiseConfig.isAnimationPacketsEnabled()) {
                packetsToListen.add(Server.ANIMATION);
            }
            // Add the packet that makes sure that entities with armor do not send unpickupable armor on death
            if (DisguiseConfig.isEntityStatusPacketsEnabled()) {
                packetsToListen.add(Server.ENTITY_STATUS);
            }

            mainListener = new PacketAdapter(libsDisguises, ListenerPriority.HIGH, packetsToListen) {
                @Override
                public void onPacketSending(PacketEvent event) {
                    if (event.isCancelled())
                        return;
                    if (event.getPlayer().getName().contains("UNKNOWN[")) //If the player is temporary
                        return;
                    final Player observer = event.getPlayer();
                    // First get the entity, the one sending this packet
                    StructureModifier<Entity> entityModifer = event.getPacket().getEntityModifier(observer.getWorld());
                    org.bukkit.entity.Entity entity = entityModifer.read((Server.COLLECT == event.getPacketType() ? 1 : 0));
                    // If the entity is the same as the sender. Don't disguise!
                    // Prevents problems and there is no advantage to be gained.
                    if (entity == observer)
                        return;
                    PacketContainer[][] packets = transformPacket(event.getPacket(), event.getPlayer(), entity);
                    if (packets != null) {
                        event.setCancelled(true);
                        try {
                            for (PacketContainer packet : packets[0]) {
                                ProtocolLibrary.getProtocolManager().sendServerPacket(observer, packet, false);
                            }
                            final PacketContainer[] delayed = packets[1];
                            if (delayed.length > 0) {
                                Bukkit.getScheduler().scheduleSyncDelayedTask(libsDisguises, new Runnable() {
                                    public void run() {
                                        try {
                                            for (PacketContainer packet : delayed) {
                                                ProtocolLibrary.getProtocolManager().sendServerPacket(observer, packet, false);
                                            }
                                        } catch (InvocationTargetException e) {
                                            e.printStackTrace();
                                        }
                                    }
                                }, 2);
                            }
                        } catch (InvocationTargetException ex) {
                            ex.printStackTrace();
                        }
                    }
                }
            };
            ProtocolLibrary.getProtocolManager().addPacketListener(mainListener);
        }
    }

    public static void setViewDisguisesListener(boolean enabled) {
        if (viewDisguisesListenerEnabled != enabled) {
            viewDisguisesListenerEnabled = enabled;
            if (viewDisguisesListenerEnabled) {
                ProtocolLibrary.getProtocolManager().addPacketListener(viewDisguisesListener);
            } else {
                ProtocolLibrary.getProtocolManager().removePacketListener(viewDisguisesListener);
            }
            for (Player player : Bukkit.getOnlinePlayers()) {
                Disguise disguise = DisguiseAPI.getDisguise(player, player);
                if (disguise != null) {
                    if (disguise.isSelfDisguiseVisible()) {
                        if (enabled) {
                            DisguiseUtilities.setupFakeDisguise(disguise);
                        } else {
                            DisguiseUtilities.removeSelfDisguise(player);
                        }
                        if (inventoryModifierEnabled && (disguise.isHidingArmorFromSelf() || disguise.isHidingHeldItemFromSelf())) {
                            player.updateInventory();
                        }
                    }
                }
            }
        }
    }

    /**
     * Transform the packet magically into the one I have always dreamed off. My true luv!!! This will return null if its not
     * transformed
     */
    public static PacketContainer[][] transformPacket(PacketContainer sentPacket, Player observer, Entity entity) {
        PacketContainer[] packets = null;
        PacketContainer[] delayedPackets = new PacketContainer[0];
        try {
            Disguise disguise = DisguiseAPI.getDisguise(observer, entity);
            // If disguised.
            if (disguise != null) {
                packets = new PacketContainer[]{sentPacket};

                // This packet sends attributes
                if (sentPacket.getType() == Server.UPDATE_ATTRIBUTES) {
                    if (disguise.isMiscDisguise()) {
                        packets = new PacketContainer[0];
                    } else {
                        List<WrappedAttribute> attributes = new ArrayList<>();
                        for (WrappedAttribute attribute : sentPacket.getAttributeCollectionModifier().read(0)) {
                            if (attribute.getAttributeKey().equals("generic.maxHealth")) {
                                packets[0] = new PacketContainer(Server.UPDATE_ATTRIBUTES);
                                Builder builder;
                                if (((LivingWatcher) disguise.getWatcher()).isMaxHealthSet()) {
                                    builder = WrappedAttribute.newBuilder();
                                    builder.attributeKey("generic.maxHealth");
                                    builder.baseValue(((LivingWatcher) disguise.getWatcher()).getMaxHealth());
                                } else if (DisguiseConfig.isMaxHealthDeterminedByDisguisedEntity()) {
                                    builder = WrappedAttribute.newBuilder(attribute);
                                } else {
                                    builder = WrappedAttribute.newBuilder();
                                    builder.attributeKey("generic.maxHealth");
                                    builder.baseValue(DisguiseValues.getDisguiseValues(disguise.getType()).getMaxHealth());
                                }
                                builder.packet(packets[0]);
                                attributes.add(builder.build());
                                break;
                            }
                        }
                        if (!attributes.isEmpty()) {
                            packets[0].getIntegers().write(0, entity.getEntityId());
                            packets[0].getAttributeCollectionModifier().write(0, attributes);
                        } else {
                            packets = new PacketContainer[0];
                        }
                    }
                }

                // Else if the packet is sending entity metadata
                else if (sentPacket.getType() == Server.ENTITY_METADATA) {
                    if (DisguiseConfig.isMetadataPacketsEnabled() && (disguise.getType() != DisguiseType.WOLF &&
                            disguise.getType() != DisguiseType.OCELOT &&
                            disguise.getType() != DisguiseType.ENDERMAN &&
                            disguise.getType() != DisguiseType.SHULKER)) {
                        List<WrappedWatchableObject> watchableObjects = disguise.getWatcher().convert(
                                packets[0].getWatchableCollectionModifier().read(0));
                        packets[0] = new PacketContainer(sentPacket.getType());
                        StructureModifier<Object> newMods = packets[0].getModifier();
                        newMods.write(0, entity.getEntityId());
                        packets[0].getWatchableCollectionModifier().write(0, watchableObjects);
                    } else {
                        packets = new PacketContainer[0];
                    }
                }

                // Else if the packet is spawning..
                else if (sentPacket.getType() == Server.NAMED_ENTITY_SPAWN
                        || sentPacket.getType() == Server.SPAWN_ENTITY_LIVING
                        || sentPacket.getType() == Server.SPAWN_ENTITY_EXPERIENCE_ORB
                        || sentPacket.getType() == Server.SPAWN_ENTITY
                        || sentPacket.getType() == Server.SPAWN_ENTITY_PAINTING) {
                    PacketContainer[][] spawnPackets = constructSpawnPackets(observer, disguise, entity);
                    packets = spawnPackets[0];
                    delayedPackets = spawnPackets[1];
                }

                // Else if the disguise is attempting to send players a forbidden packet
                else if (sentPacket.getType() == Server.ANIMATION) {
                    if (disguise.getType().isMisc()
                            || (packets[0].getIntegers().read(1) == 2 && (!disguise.getType()
                            .isPlayer() || (DisguiseConfig.isBedPacketsEnabled() && ((PlayerWatcher) disguise
                            .getWatcher()).isSleeping())))) {
                        packets = new PacketContainer[0];
                    }
                }

                //Else if the disguise is collecting stuff
                else if (sentPacket.getType() == Server.COLLECT) {
                    if (disguise.getType().isMisc()) {
                        packets = new PacketContainer[0];
                    } else if (DisguiseConfig.isBedPacketsEnabled() && disguise.getType().isPlayer()
                            && ((PlayerWatcher) disguise.getWatcher()).isSleeping()) {
                        PacketContainer newPacket = new PacketContainer(Server.ANIMATION);
                        StructureModifier<Integer> mods = newPacket.getIntegers();
                        mods.write(0, disguise.getEntity().getEntityId());
                        mods.write(1, 3);
                        packets = new PacketContainer[]{newPacket, sentPacket};
                    }
                }

                // Else if the disguise is moving.
                else if (sentPacket.getType() == Server.ENTITY_MOVE_LOOK
                        || sentPacket.getType() == Server.ENTITY_LOOK
                        || sentPacket.getType() == Server.ENTITY_TELEPORT
                        || sentPacket.getType() == Server.REL_ENTITY_MOVE) {
                    if (disguise.getType() == DisguiseType.RABBIT && (sentPacket.getType() == Server.REL_ENTITY_MOVE || sentPacket.getType() == Server.ENTITY_MOVE_LOOK)) {
                        //Rabbit robbing...
                        if (entity.getMetadata("LibsRabbitHop").isEmpty()
                                || System.currentTimeMillis() - entity.getMetadata("LibsRabbitHop").get(0).asLong() < 100
                                || System.currentTimeMillis() - entity.getMetadata("LibsRabbitHop").get(0).asLong() > 500) {
                            if (entity.getMetadata("LibsRabbitHop").isEmpty()
                                    || System.currentTimeMillis() - entity.getMetadata("LibsRabbitHop").get(0).asLong() > 500) {
                                entity.removeMetadata("LibsRabbitHop", libsDisguises);
                                entity.setMetadata("LibsRabbitHop", new FixedMetadataValue(libsDisguises, System.currentTimeMillis()));
                            }
                            packets = Arrays.copyOf(packets, packets.length + 1);
                            packets[1] = new PacketContainer(Server.ENTITY_STATUS);
                            packets[1].getIntegers().write(0, entity.getEntityId());
                            packets[1].getBytes().write(0, (byte) 1);
                        }
                    }

                    //Stop wither skulls from looking
                    if (sentPacket.getType() == Server.ENTITY_LOOK && disguise.getType() == DisguiseType.WITHER_SKULL) {
                        packets = new PacketContainer[0];
                    } else if (sentPacket.getType() != Server.REL_ENTITY_MOVE) {
                        packets[0] = sentPacket.shallowClone();
                        StructureModifier<Byte> bytes = packets[0].getBytes();
                        byte yawValue = bytes.read(0);
                        bytes.write(0, getYaw(disguise.getType(), entity.getType(), yawValue));
                        byte pitchValue = bytes.read(1);
                        bytes.write(1, getPitch(disguise.getType(), DisguiseType.getType(entity.getType()), pitchValue));
                        if (sentPacket.getType() == Server.ENTITY_TELEPORT && disguise.getType() == DisguiseType.ITEM_FRAME) {
                            StructureModifier<Double> doubles = packets[0].getDoubles();
                            Location loc = entity.getLocation();
                            double data = (((loc.getYaw() % 360) + 720 + 45) / 90) % 4;
                            if (data % 2 == 0) {
                                if (data % 2 == 0) {
                                    doubles.write(3, loc.getZ());
                                } else {
                                    doubles.write(1, loc.getZ());
                                }
                            }
                            double y = getYModifier(entity, disguise);
                            if (y != 0) {
                                y *= 32;
                                doubles.write(2, doubles.read(2) + y);
                            }
                        }
                    }
                }

                //Else if the disguise is updating equipment
                else if (sentPacket.getType() == Server.ENTITY_EQUIPMENT) {
                    EquipmentSlot slot = ReflectionManager.createEquipmentSlot(packets[0].getModifier().read(1));
                    org.bukkit.inventory.ItemStack itemStack = disguise.getWatcher().getItemStack(slot);
                    if (itemStack != null) {
                        packets[0] = packets[0].shallowClone();
                        packets[0].getModifier().write(2, (itemStack.getTypeId() == 0 ? null : ReflectionManager.getNmsItem(itemStack)));
                    }
                    if (disguise.getWatcher().isRightClicking() && slot == EquipmentSlot.HAND) {
                        ItemStack heldItem = packets[0].getItemModifier().read(0);
                        if (heldItem != null && heldItem.getType() != Material.AIR) {
                            // Convert the datawatcher
                            List<WrappedWatchableObject> list = new ArrayList<>();
                            if (DisguiseConfig.isMetadataPacketsEnabled() && (disguise.getType() != DisguiseType.WOLF &&
                                    disguise.getType() != DisguiseType.OCELOT &&
                                    disguise.getType() != DisguiseType.ENDERMAN &&
                                    disguise.getType() != DisguiseType.SHULKER)) {
                                WrappedWatchableObject watch = new WrappedWatchableObject(ReflectionManager.createDataWatcherItem(0,
                                        WrappedDataWatcher.getEntityWatcher(entity).getByte(0)));
                                list.add(watch);
                                list = disguise.getWatcher().convert(list);
                            } else {
                                for (WrappedWatchableObject obj : disguise.getWatcher().getWatchableObjects()) {
                                    if (obj.getIndex() == 0) {
                                        list.add(obj);
                                        break;
                                    }
                                }
                            }
                            // Construct the packets to return
                            PacketContainer packetBlock = new PacketContainer(Server.ENTITY_METADATA);
                            packetBlock.getModifier().write(0, entity.getEntityId());
                            packetBlock.getWatchableCollectionModifier().write(0, list);
                            PacketContainer packetUnblock = packetBlock.deepClone();
                            // Make a packet to send the 'unblock'
                            for (WrappedWatchableObject watcher : packetUnblock.getWatchableCollectionModifier().read(0)) {
                                watcher.setValue((byte) ((byte) watcher.getValue() & ~(1 << 4)));
                            }
                            // Send the unblock before the itemstack change so that the 2nd metadata packet works. Why? Scheduler
                            // delay.
                            packets = new PacketContainer[]{packetUnblock, packets[0], packetBlock};
                            // Silly mojang made the right clicking datawatcher value only valid for one use. So I have to reset
                            // it.
                        }
                    }
                }

                //If the entity is going into a bed, stop everything but players from doing this
                else if (sentPacket.getType() == Server.BED) {
                    if (!disguise.getType().isPlayer()) {
                        packets = new PacketContainer[0];
                    }
                }

                //If the entity is updating their Facebook status, stop them from showing death
                else if (sentPacket.getType() == Server.ENTITY_STATUS) {
                    if (packets[0].getBytes().read(0) == (byte) 3) {
                        packets = new PacketContainer[0];
                    }
                }

                //If the entity is rotating his head
                else if (sentPacket.getType() == Server.ENTITY_HEAD_ROTATION) {
                    if (disguise.getType().isPlayer() && entity.getType() != EntityType.PLAYER) {
                        Location loc = entity.getLocation();
                        byte pitch = getPitch(disguise.getType(), DisguiseType.getType(entity.getType()),
                                (byte) (int) (loc.getPitch() * 256.0F / 360.0F));
                        byte yaw = getYaw(disguise.getType(), entity.getType(), sentPacket.getBytes().read(0));
                        PacketContainer rotation = new PacketContainer(Server.ENTITY_HEAD_ROTATION);
                        StructureModifier<Object> mods = rotation.getModifier();
                        mods.write(0, entity.getEntityId());
                        mods.write(1, yaw);
                        PacketContainer look = new PacketContainer(Server.ENTITY_LOOK);
                        look.getIntegers().write(0, entity.getEntityId());
                        look.getBytes().write(0, yaw);
                        look.getBytes().write(1, pitch);
                        packets = new PacketContainer[]{look, rotation};
                    }
                }

                //Whatever
                else {
                    packets = null;
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return packets == null ? null : new PacketContainer[][]{packets, delayedPackets};
    }
}
