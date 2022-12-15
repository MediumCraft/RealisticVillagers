package me.matsubara.realisticvillagers.entity.v1_18_r2.villager;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.data.ChangeItemType;
import me.matsubara.realisticvillagers.data.ExpectingType;
import me.matsubara.realisticvillagers.data.InteractType;
import me.matsubara.realisticvillagers.data.LastKnownPosition;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.entity.v1_18_r2.DummyFishingHook;
import me.matsubara.realisticvillagers.entity.v1_18_r2.PetParrot;
import me.matsubara.realisticvillagers.entity.v1_18_r2.villager.ai.VillagerNPCGoalPackages;
import me.matsubara.realisticvillagers.entity.v1_18_r2.villager.ai.behaviour.core.LootChest;
import me.matsubara.realisticvillagers.entity.v1_18_r2.villager.ai.behaviour.core.VillagerPanicTrigger;
import me.matsubara.realisticvillagers.entity.v1_18_r2.villager.ai.sensing.NearestItemSensor;
import me.matsubara.realisticvillagers.entity.v1_18_r2.villager.ai.sensing.VillagerHostilesSensor;
import me.matsubara.realisticvillagers.event.VillagerExhaustionEvent;
import me.matsubara.realisticvillagers.event.VillagerFishEvent;
import me.matsubara.realisticvillagers.event.VillagerRemoveEvent;
import me.matsubara.realisticvillagers.files.Config;
import me.matsubara.realisticvillagers.files.Messages;
import me.matsubara.realisticvillagers.listener.InventoryListeners;
import me.matsubara.realisticvillagers.nms.v1_18_r2.CustomGossipContainer;
import me.matsubara.realisticvillagers.nms.v1_18_r2.NMSConverter;
import me.matsubara.realisticvillagers.nms.v1_18_r2.VillagerFoodData;
import me.matsubara.realisticvillagers.util.ItemStackUtils;
import me.matsubara.realisticvillagers.util.Reflection;
import me.matsubara.realisticvillagers.util.npc.NPC;
import me.matsubara.realisticvillagers.util.npc.modifier.MetadataModifier;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.EntityDamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.VillagerGoalPackages;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.ai.village.ReputationEventType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.CrossbowAttackMob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.schedule.Schedule;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import org.apache.commons.lang3.ArrayUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.v1_18_R2.CraftWorld;
import org.bukkit.craftbukkit.v1_18_R2.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_18_R2.entity.CraftVillager;
import org.bukkit.craftbukkit.v1_18_R2.event.CraftEventFactory;
import org.bukkit.craftbukkit.v1_18_R2.inventory.CraftItemStack;
import org.bukkit.entity.FishHook;
import org.bukkit.event.entity.*;
import org.bukkit.event.weather.LightningStrikeEvent;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import javax.annotation.Nullable;
import java.lang.invoke.MethodHandle;
import java.time.LocalDate;
import java.time.temporal.ChronoField;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

@SuppressWarnings("Guava")
@Getter
@Setter
public class VillagerNPC extends Villager implements IVillagerNPC, CrossbowAttackMob {

    private final RealisticVillagers plugin = JavaPlugin.getPlugin(RealisticVillagers.class);

    private String villagerName;
    private String sex;
    private IVillagerNPC partner;
    private boolean isPartnerVillager;
    private long lastProcreation;
    private int skinTextureId = -1;
    private UUID interactingWith;
    private InteractType interactType;
    private ExpectingType expectingType;
    private UUID expectingFrom;
    private int expectingTicks;
    private boolean giftDropped;
    private UUID procreatingWith;
    private IVillagerNPC father;
    private boolean isFatherVillager;
    private IVillagerNPC mother;
    private boolean isMotherVillager = true;
    private List<IVillagerNPC> childrens = new ArrayList<>();
    private UUID bedHomeWorld;
    private BlockPos bedHome;
    private Set<EntityType<?>> targetEntities = getDefaultTargets();
    private long lastGossipTime;
    private long lastGossipDecayTime;
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private DamageSource lastDamageSource;
    private DummyFishingHook fishing;
    private boolean isEating;
    private boolean showingTrades;
    private boolean isTaming;
    private boolean isHealingGolem;
    private boolean isUsingBoneMeal;
    private boolean isLooting;
    private boolean wasInfected;
    private boolean shakingHead;
    private boolean equipped;
    private boolean isAttackingWithTrident;
    private ThrownTrident thrownTrident;
    private ServerPlayer shakingHeadAt;
    private long timeEntitySatOnShoulder;
    private @Getter(AccessLevel.NONE) CompoundTag shoulderEntityLeft = new CompoundTag();
    private @Getter(AccessLevel.NONE) CompoundTag shoulderEntityRight = new CompoundTag();
    private double previousSpeedModifier;

    private final SimpleContainer inventory = new SimpleContainer(Math.min(36, Config.VILLAGER_INVENTORY_SIZE.asInt()), getBukkitEntity());
    private final ItemCooldowns cooldowns = new ItemCooldowns();
    private final VillagerFoodData foodData = new VillagerFoodData(this);
    private final @Setter(AccessLevel.NONE) CustomGossipContainer gossips = new CustomGossipContainer();

    public static final MemoryModuleType<Boolean> HAS_HEALED_GOLEM_RECENTLY = NMSConverter.registerMemoryType("has_healed_golem_recently", Codec.BOOL);
    public static final MemoryModuleType<Boolean> HAS_FISHED_RECENTLY = NMSConverter.registerMemoryType("has_fished_recently", Codec.BOOL);
    public static final MemoryModuleType<Boolean> HAS_TAMED_RECENTLY = NMSConverter.registerMemoryType("has_tamed_recently", Codec.BOOL);
    public static final MemoryModuleType<Boolean> HAS_LOOTED_RECENTLY = NMSConverter.registerMemoryType("has_looted_recently", Codec.BOOL);
    public static final MemoryModuleType<Boolean> CELEBRATE_VICTORY = NMSConverter.registerMemoryType("celebrate_victory", Codec.BOOL);
    public static final MemoryModuleType<GlobalPos> STAY_PLACE = NMSConverter.registerMemoryType("stay_place", GlobalPos.CODEC);
    public static final MemoryModuleType<ItemEntity> NEAREST_WANTED_ITEM = NMSConverter.registerMemoryType("nearest_wanted_item");

    public static final Activity STAY = NMSConverter.registerActivity("stay");

    private static final ImmutableList<MemoryModuleType<?>> MEMORIES = ImmutableList.of(
            MemoryModuleType.HOME,
            MemoryModuleType.JOB_SITE,
            MemoryModuleType.POTENTIAL_JOB_SITE,
            MemoryModuleType.MEETING_POINT,
            MemoryModuleType.NEAREST_LIVING_ENTITIES,
            MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES,
            MemoryModuleType.VISIBLE_VILLAGER_BABIES,
            MemoryModuleType.NEAREST_PLAYERS,
            MemoryModuleType.NEAREST_VISIBLE_PLAYER,
            MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYER,
            MemoryModuleType.WALK_TARGET,
            MemoryModuleType.LOOK_TARGET,
            MemoryModuleType.INTERACTION_TARGET,
            MemoryModuleType.BREED_TARGET,
            MemoryModuleType.PATH,
            MemoryModuleType.DOORS_TO_CLOSE,
            MemoryModuleType.NEAREST_BED,
            MemoryModuleType.HURT_BY,
            MemoryModuleType.HURT_BY_ENTITY,
            MemoryModuleType.NEAREST_HOSTILE,
            MemoryModuleType.SECONDARY_JOB_SITE,
            MemoryModuleType.HIDING_PLACE,
            MemoryModuleType.HEARD_BELL_TIME,
            MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE,
            MemoryModuleType.LAST_SLEPT,
            MemoryModuleType.LAST_WOKEN,
            MemoryModuleType.LAST_WORKED_AT_POI,
            MemoryModuleType.GOLEM_DETECTED_RECENTLY,
            HAS_HEALED_GOLEM_RECENTLY,
            HAS_FISHED_RECENTLY,
            HAS_TAMED_RECENTLY,
            HAS_LOOTED_RECENTLY,
            CELEBRATE_VICTORY,
            STAY_PLACE,
            NEAREST_WANTED_ITEM,
            MemoryModuleType.ATTACK_TARGET,
            MemoryModuleType.ATTACK_COOLING_DOWN);

    private static final SensorType<VillagerHostilesSensor> NEAREST_HOSTILE = NMSConverter.registerSensor("nearest_hostile", VillagerHostilesSensor::new);
    private static final SensorType<NearestItemSensor> NEAREST_ITEMS = NMSConverter.registerSensor("nearest_wanted_items", NearestItemSensor::new);

    private static final ImmutableList<SensorType<? extends Sensor<? super Villager>>> SENSORS = ImmutableList.of(
            SensorType.NEAREST_LIVING_ENTITIES,
            SensorType.NEAREST_BED,
            SensorType.HURT_BY,
            SensorType.VILLAGER_BABIES,
            SensorType.SECONDARY_POIS,
            SensorType.GOLEM_DETECTED,
            SensorType.NEAREST_PLAYERS,
            NEAREST_ITEMS != null ? NEAREST_ITEMS : SensorType.NEAREST_ITEMS,
            NEAREST_HOSTILE != null ? NEAREST_HOSTILE : SensorType.VILLAGER_HOSTILES);

    public static final float SWIMMING_SPEED_MODIFIER = SPEED_MODIFIER * 2.0f;
    public static final float SPRINT_SPEED_MODIFIER = SPEED_MODIFIER * 1.5f;
    private static final int GOSSIP_DECAY_INTERVAL = 24000;
    private static final int[] ROTATION = {-1, -3 - 5, -7, -7, -6, -4, -2, 1, 3, 5, 7, 7, 6, 4, 2, 2, 0};
    private static final EntityDataAccessor<Boolean> DATA_IS_CHARGING = SynchedEntityData.defineId(VillagerNPC.class, EntityDataSerializers.BOOLEAN);
    private static final ImmutableSet<Class<? extends Item>> DO_NOT_SAVE = ImmutableSet.of(
            SwordItem.class,
            AxeItem.class,
            TridentItem.class,
            ProjectileWeaponItem.class,
            ShieldItem.class,
            ArmorItem.class);

    private static final MethodHandle BUKKIT_ENTITY = Reflection.getFieldSetter(Entity.class, "bukkitEntity");

    public VillagerNPC(EntityType<? extends Villager> type, Level level) {
        this(type, level, VillagerType.PLAINS);
    }

    public VillagerNPC(EntityType<? extends Villager> type, Level level, VillagerType villagerType) {
        super(type, level, villagerType);

        refreshBrain((ServerLevel) level);

        NMSConverter.registerAttribute(this, Attributes.ATTACK_DAMAGE, Config.ATTACK_DAMAGE.asDouble());
        NMSConverter.registerAttribute(this, Attributes.MAX_HEALTH, Config.VILLAGER_MAX_HEALTH.asDouble());

        setPersistenceRequired();
    }

    @Override
    protected Brain<?> makeBrain(Dynamic<?> dynamic) {
        Brain<Villager> brain = brainProvider().makeBrain(dynamic);
        registerBrainGoals(brain);
        return brain;
    }

    @Override
    protected Brain.Provider<Villager> brainProvider() {
        return Brain.provider(MEMORIES, SENSORS);
    }

    @Override
    public void refreshBrain(ServerLevel level) {
        Brain<Villager> brain = getBrain();
        brain.stopAll(level, this);
        this.brain = brain.copyWithoutBehaviors();
        this.brain.removeAllBehaviors();
        registerBrainGoals(getBrain());
    }

    private void registerBrainGoals(Brain<Villager> brain) {
        // We can use VillagerGoalPackages for PANIC, PLAY, HIDE & PRE_RAID activities since we don't modify any behavior.
        VillagerProfession profession = getProfession();
        if (isBaby()) {
            brain.setSchedule(Schedule.VILLAGER_BABY);
            brain.addActivity(Activity.PLAY, VillagerGoalPackages.getPlayPackage(SPEED_MODIFIER));
        } else {
            brain.setSchedule(Schedule.VILLAGER_DEFAULT);
            brain.addActivityWithConditions(
                    Activity.WORK,
                    VillagerNPCGoalPackages.getWorkPackage(profession),
                    ImmutableSet.of(Pair.of(MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT)));
        }

        brain.addActivity(Activity.CORE, VillagerNPCGoalPackages.getCorePackage(profession));
        brain.addActivityWithConditions(
                Activity.MEET,
                VillagerNPCGoalPackages.getMeetPackage(),
                ImmutableSet.of(Pair.of(MemoryModuleType.MEETING_POINT, MemoryStatus.VALUE_PRESENT)));
        brain.addActivity(Activity.REST, VillagerNPCGoalPackages.getRestPackage());
        brain.addActivity(Activity.IDLE, VillagerNPCGoalPackages.getIdlePackage());
        brain.addActivity(Activity.PANIC, VillagerGoalPackages.getPanicPackage(profession, SPEED_MODIFIER));
        brain.addActivity(Activity.PRE_RAID, VillagerGoalPackages.getPreRaidPackage(profession, SPEED_MODIFIER));
        brain.addActivity(Activity.RAID, VillagerNPCGoalPackages.getRaidPackage());
        brain.addActivity(Activity.HIDE, VillagerGoalPackages.getHidePackage(profession, SPRINT_SPEED_MODIFIER));
        brain.addActivity(Activity.FIGHT, VillagerNPCGoalPackages.getFightPackage());
        brain.addActivity(STAY, VillagerNPCGoalPackages.getStayPackage());
        brain.setCoreActivities(ImmutableSet.of(Activity.CORE));
        brain.setDefaultActivity(Activity.IDLE);
        brain.setActiveActivityIfPossible(Activity.IDLE);
        brain.updateActivityFromSchedule(level.getDayTime(), level.getGameTime());
    }

    @Override
    public CustomGossipContainer getGossips() {
        return gossips;
    }

    @Override
    public int getPlayerReputation(Player player) {
        return gossips.getReputation(player.getUUID(), (type) -> true);
    }

    @Override
    public void setGossips(Tag tag) {
        gossips.update(new Dynamic<>(NbtOps.INSTANCE, tag));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);

        tag.putLong("LastGossipDecay", lastGossipDecayTime);

        // Save the previous (vanilla) custom name.
        Component customName = getCustomName(true);
        if (customName != null) {
            tag.putString("CustomName", Component.Serializer.toJson(customName));
        } else {
            tag.remove("CustomName");
        }

        CompoundTag bukkit = NMSConverter.getOrCreateBukkitTag(tag);
        savePluginData(bukkit);

        tag.put("BukkitValues", bukkit);

        NMSConverter.updateBukkitValues(tag, plugin.getNpcValuesKey().getNamespace(), this);
    }

    public void savePluginData(CompoundTag tag) {
        CompoundTag villagerTag = new CompoundTag();
        villagerTag.put(OfflineVillagerNPC.INVENTORY, inventory.createTag());
        villagerTag.putString(OfflineVillagerNPC.NAME, villagerName);
        villagerTag.putString(OfflineVillagerNPC.SEX, sex);
        if (partner != null) villagerTag.put(OfflineVillagerNPC.PARTNER, fromOffline(partner));
        villagerTag.putBoolean(OfflineVillagerNPC.IS_PARTNER_VILLAGER, isPartnerVillager);
        villagerTag.putLong(OfflineVillagerNPC.LAST_PROCREATION, lastProcreation);
        villagerTag.putInt(OfflineVillagerNPC.SKIN_TEXTURE_ID, skinTextureId);
        if (father != null) villagerTag.put(OfflineVillagerNPC.FATHER, fromOffline(father));
        if (mother != null) villagerTag.put(OfflineVillagerNPC.MOTHER, fromOffline(mother));
        villagerTag.putBoolean(OfflineVillagerNPC.IS_FATHER_VILLAGER, isFatherVillager);
        villagerTag.putBoolean(OfflineVillagerNPC.WAS_INFECTED, wasInfected);
        villagerTag.putBoolean(OfflineVillagerNPC.EQUIPPED, equipped);
        if (!shoulderEntityLeft.isEmpty()) {
            villagerTag.put(OfflineVillagerNPC.SHOULDER_ENTITY_LEFT, shoulderEntityLeft);
        }
        if (!shoulderEntityRight.isEmpty()) {
            villagerTag.put(OfflineVillagerNPC.SHOULDER_ENTITY_RIGHT, shoulderEntityRight);
        }
        villagerTag.put(OfflineVillagerNPC.GOSSIPS, gossips.store(NbtOps.INSTANCE).getValue());
        saveCollection(childrens, this::fromOffline, OfflineVillagerNPC.CHILDRENS, villagerTag);
        saveCollection(targetEntities, type -> StringTag.valueOf(type.toShortString()), OfflineVillagerNPC.TARGET_ENTITIES, villagerTag);
        if (bedHome != null && bedHomeWorld != null) {
            CompoundTag bedHomeTag = new CompoundTag();
            bedHomeTag.putUUID(OfflineVillagerNPC.BED_HOME_WORLD, bedHomeWorld);
            bedHomeTag.put(OfflineVillagerNPC.BED_HOME_POS, newDoubleList(bedHome.getX(), bedHome.getY(), bedHome.getZ()));
            villagerTag.put(OfflineVillagerNPC.BED_HOME, bedHomeTag);
        }
        foodData.addAdditionalSaveData(villagerTag);
        tag.put(plugin.getNpcValuesKey().toString(), villagerTag);
    }

    private CompoundTag fromOffline(IVillagerNPC villager) {
        return villager instanceof OfflineVillagerNPC offline ? offline.getTag() : null;
    }

    private <T> void saveCollection(Collection<T> collection, Function<T, Tag> mapper, String name, CompoundTag tag) {
        ListTag list = new ListTag();
        for (T object : collection) {
            list.add(mapper.apply(object));
        }
        tag.put(name, list);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);

        // Load gossips from vanilla. If our custom gossips isn't in our custom tag, we use vanilla ones.
        gossips.update(new Dynamic<>(NbtOps.INSTANCE, tag.getList("Gossips", 10)));
        lastGossipDecayTime = tag.getLong("LastGossipDecay");

        CompoundTag bukkit = NMSConverter.getOrCreateBukkitTag(tag);

        Tag base = bukkit.get(plugin.getNpcValuesKey().toString());
        loadPluginData(base != null ? (CompoundTag) base : new CompoundTag());

        // Previous versions of this plugin used setCustomName() before.
        Component customName = getCustomName();
        if (customName != null && villagerName.equals(customName.getString())) {
            setCustomName(null);
        }
    }

    public void loadPluginData(CompoundTag villagerTag) {
        inventory.fromTag(villagerTag.getList(OfflineVillagerNPC.INVENTORY, 10));
        villagerName = villagerTag.getString(OfflineVillagerNPC.NAME);
        sex = villagerTag.getString(OfflineVillagerNPC.SEX);
        if (sex.isEmpty()) sex = random.nextBoolean() ? "male" : "female";
        if (villagerName.isEmpty()) setVillagerName(plugin.getTracker().getRandomNameBySex(sex));
        isPartnerVillager = villagerTag.getBoolean(OfflineVillagerNPC.IS_PARTNER_VILLAGER);
        partner = getFamily(villagerTag, OfflineVillagerNPC.PARTNER, isPartnerVillager);
        lastProcreation = villagerTag.getLong(OfflineVillagerNPC.LAST_PROCREATION);
        skinTextureId = villagerTag.contains(OfflineVillagerNPC.SKIN_TEXTURE_ID) ?
                villagerTag.getInt(OfflineVillagerNPC.SKIN_TEXTURE_ID) :
                -1;
        isFatherVillager = villagerTag.getBoolean(OfflineVillagerNPC.IS_FATHER_VILLAGER);
        father = getFamily(villagerTag, OfflineVillagerNPC.FATHER, isFatherVillager);
        mother = getFamily(villagerTag, OfflineVillagerNPC.MOTHER, true);
        wasInfected = villagerTag.getBoolean(OfflineVillagerNPC.WAS_INFECTED);
        equipped = villagerTag.getBoolean(OfflineVillagerNPC.EQUIPPED);
        if (villagerTag.contains(OfflineVillagerNPC.SHOULDER_ENTITY_LEFT, 10)) {
            setShoulderEntityLeft(villagerTag.getCompound(OfflineVillagerNPC.SHOULDER_ENTITY_LEFT));
        }
        if (villagerTag.contains(OfflineVillagerNPC.SHOULDER_ENTITY_RIGHT, 10)) {
            setShoulderEntityRight(villagerTag.getCompound(OfflineVillagerNPC.SHOULDER_ENTITY_RIGHT));
        }
        ListTag gossips = villagerTag.getList(OfflineVillagerNPC.GOSSIPS, 10);
        if (!gossips.isEmpty()) {
            this.gossips.clear();
            this.gossips.update(new Dynamic<>(NbtOps.INSTANCE, gossips));
        }
        fillCollection(
                childrens,
                input -> {
                    if (input instanceof CompoundTag compound) {
                        return OfflineVillagerNPC.from(compound);
                    } else {
                        UUID uuid = NbtUtils.loadUUID(input);
                        return plugin.getTracker().getOffline(uuid);
                    }
                },
                OfflineVillagerNPC.CHILDRENS,
                villagerTag);
        fillCollection(
                targetEntities,
                input -> EntityType.byString(input.getAsString()).orElse(null),
                OfflineVillagerNPC.TARGET_ENTITIES,
                villagerTag);
        loadBedHomePosition(villagerTag);
        foodData.readAdditionalSaveData(villagerTag);
    }

    @Override
    public float getScale() {
        // For babies is 0.5, but that would suffocate villagers.
        return 1.0f;
    }

    private IVillagerNPC getFamily(CompoundTag tag, String who, boolean isVillager) {
        if (!tag.contains(who)) return null;

        if (tag.hasUUID(who)) {
            UUID uuid = tag.getUUID(who);
            return isVillager ? plugin.getTracker().getOffline(uuid) : dummyPlayerOffline(uuid);
        } else {
            return OfflineVillagerNPC.from(tag.getCompound(who));
        }
    }

    private void loadBedHomePosition(CompoundTag tag) {
        if (!tag.contains(OfflineVillagerNPC.BED_HOME)) return;

        CompoundTag bedHomeTag = (CompoundTag) tag.get(OfflineVillagerNPC.BED_HOME);
        if (bedHomeTag == null) return;

        World world = Bukkit.getServer().getWorld(bedHomeTag.getUUID(OfflineVillagerNPC.BED_HOME_WORLD));
        if (world == null) return;

        ListTag coords = bedHomeTag.getList(OfflineVillagerNPC.BED_HOME_POS, 6);
        double x = coords.getDouble(0);
        double y = coords.getDouble(1);
        double z = coords.getDouble(2);

        ServerLevel level = ((CraftWorld) world).getHandle();
        if (!level.getChunkSource().isChunkLoaded((int) x, (int) z)
                || level.getChunkIfLoaded((int) x, (int) z) == null) return;

        GlobalPos pos = GlobalPos.of(level.dimension(), new BlockPos(x, y, z));

        BlockState state = level.getBlockState(pos.pos());
        if (!state.is(BlockTags.BEDS) || state.getValue(BedBlock.OCCUPIED)) return;

        Predicate<PoiType> predicate = poi -> poi.equals(PoiType.HOME);
        if (!level.getPoiManager().exists(pos.pos(), predicate)) return;

        Optional<BlockPos> temp = level.getPoiManager().find(predicate, found -> found.equals(pos.pos()), pos.pos(), 1, PoiManager.Occupancy.ANY);
        if (temp.isEmpty() || !temp.get().equals(pos.pos())) return;

        getBrain().setMemory(MemoryModuleType.HOME, pos);
        bedHome = pos.pos();
        bedHomeWorld = world.getUID();
    }

    @SuppressWarnings({"Guava"})
    public static <T> void fillCollection(Collection<T> collection, Function<Tag, T> mapper, String name, CompoundTag tag) {
        if (!tag.contains(name)) return;
        collection.clear();

        ListTag list = (ListTag) tag.get(name);
        if (list == null) return;

        for (Tag content : list) {
            T value = mapper.apply(content);
            if (value != null) collection.add(value);
        }
    }

    @Override
    public void releasePoi(MemoryModuleType<GlobalPos> memory) {
        level.broadcastEntityEvent(this, (byte) 13);
        if (memory.equals(MemoryModuleType.HOME)) {
            Optional<GlobalPos> pos = getBrain().getMemory(memory);
            if (pos.isPresent() && pos.get().pos().equals(bedHome)) {
                bedHome = null;
                bedHomeWorld = null;
            }
        }
        super.releasePoi(memory);
    }

    @Override
    public CraftVillager getBukkitEntity() {
        return (CraftVillager) super.getBukkitEntity();
    }

    public boolean hasPartner() {
        return partner != null;
    }

    public boolean isConversating() {
        return isInteracting() && interactType.isGUI();
    }

    public boolean isFollowing() {
        return isInteracting() && interactType.isFollowing();
    }

    public void stopInteracting() {
        setInteractingWithAndType(null, null);
    }

    public void setInteractingWithAndType(UUID interactingWith, InteractType interactType) {
        this.interactingWith = interactingWith;
        this.interactType = interactType;
    }

    public int getMeleeAttackCooldown() {
        return Config.MELEE_ATTACK_COOLDOWN.asInt();
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    @Override
    public boolean canAttack() {
        return isHoldingWeapon();
    }

    public boolean is(VillagerProfession... professions) {
        for (VillagerProfession profession : professions) {
            if (getProfession() == profession) return true;
        }
        return false;
    }

    public boolean isHoldingWeapon() {
        return isHoldingMeleeWeapon() || isHoldingRangeWeapon();
    }

    public boolean isHoldingMeleeWeapon() {
        return isHolding(item -> item.getItem() instanceof SwordItem || item.getItem() instanceof AxeItem || item.is(Items.TRIDENT));
    }

    public boolean isHoldingRangeWeapon() {
        return isHolding(item -> item.getItem() instanceof ProjectileWeaponItem weapon && canFireProjectileWeapon(weapon));
    }

    @Override
    public void setChargingCrossbow(boolean flag) {
        entityData.set(DATA_IS_CHARGING, flag);
    }

    @Override
    public ItemStack getProjectile(ItemStack itemstack) {
        return Items.ARROW.getDefaultInstance();
    }

    @Override
    public void onCrossbowAttackPerformed() {
        noActionTime = 0;
    }

    @Override
    public void shootCrossbowProjectile(LivingEntity target, ItemStack bow, Projectile projectile, float force) {
        shootCrossbowProjectile(this, target, projectile, force, Config.RANGE_WEAPON_POWER.asFloat());
    }

    @Override
    public void performRangedAttack(LivingEntity target, float force) {
        if (!isHoldingRangeWeapon()) {
            onCrossbowAttackPerformed();
            return;
        }

        boolean isBow = isHolding(Items.BOW);
        InteractionHand hand = ProjectileUtil.getWeaponHoldingHand(this, getMainHandItem().getItem());

        ItemStack weapon = getItemInHand(hand);
        ItemStack arrow = getProjectile((ProjectileWeaponItem) weapon.getItem());

        boolean isRocket = arrow.is(Items.FIREWORK_ROCKET);

        Projectile projectile;
        if (isBow) {
            projectile = ProjectileUtil.getMobArrow(this, arrow, force);

            float powerForTime = BowItem.getPowerForTime(BowItem.MAX_DRAW_DURATION);
            if (powerForTime == 1.0f) {
                ((AbstractArrow) projectile).setCritArrow(true);
            }
        } else {
            if (isRocket) {
                projectile = new FireworkRocketEntity(level, arrow, this, getX(), getEyeY() - 0.15000000596046448d, getZ(), true);
            } else {
                projectile = ((ArrowItem) arrow.getItem()).createArrow(level, arrow, this);
                if (projectile instanceof AbstractArrow abstractArrow) {
                    abstractArrow.setSoundEvent(SoundEvents.CROSSBOW_HIT);
                    abstractArrow.setShotFromCrossbow(true);
                    abstractArrow.setCritArrow(true);
                }
            }

            int piercing = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.PIERCING, weapon);
            if (piercing > 0 && projectile instanceof AbstractArrow abstractArrow) {
                abstractArrow.setPierceLevel((byte) piercing);
            }
        }

        if (!isRocket) {
            ((AbstractArrow) projectile).pickup = AbstractArrow.Pickup.ALLOWED;
        }

        if (isBow) {
            shootBow(target, projectile);
        } else {
            shootCrossbowProjectile(target, weapon, projectile, isRocket ? 1.6f : force);
        }

        weapon.hurtAndBreak(arrow.is(Items.FIREWORK_ROCKET) ? 3 : 1, this, (npc) -> npc.broadcastBreakEvent(hand));

        EntityShootBowEvent event = CraftEventFactory.callEntityShootBowEvent(
                this,
                weapon,
                arrow,
                projectile,
                getUsedItemHand(),
                force,
                true);

        if (event.isCancelled()) {
            event.getProjectile().remove();
            return;
        } else if (event.getProjectile() == projectile.getBukkitEntity() && !level.addFreshEntity(projectile)) {
            plugin.getLogger().info("Can't add projectile to world!");
            return;
        }

        onCrossbowAttackPerformed();
    }

    private void shootBow(LivingEntity target, Projectile projectile) {
        double deltaX = target.getX() - getX();
        double deltaY = target.getY(0.3333333333333333d) - projectile.getY();
        double deltaZ = target.getZ() - getZ();

        double horizontalMagnitude = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);

        projectile.shoot(
                deltaX,
                deltaY + horizontalMagnitude * 0.20000000298023224d,
                deltaZ,
                Config.RANGE_WEAPON_POWER.asFloat(),
                (float) (14 - level.getDifficulty().getId() * 4));

        playSound(SoundEvents.ARROW_SHOOT, 1.0f, 1.0f / (random.nextFloat() * 0.4f + 0.8f));
    }

    @Override
    public boolean canFireProjectileWeapon(ProjectileWeaponItem item) {
        return !getProjectile(item).isEmpty();
    }

    public ItemStack getProjectile(ProjectileWeaponItem item) {
        ItemStack held = ProjectileWeaponItem.getHeldProjectile(this, item.getSupportedHeldProjectiles());
        if (!held.isEmpty()) return held;

        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (item.getAllSupportedProjectiles().test(stack)) return stack;
        }

        return ItemStack.EMPTY;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_IS_CHARGING, false);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void die(DamageSource source) {
        super.die(source);

        tellFamilyThatIWasMurdered(source);

        // Stop all behaviors.
        for (Behavior<? super Villager> behavior : getBrain().getRunningBehaviors()) {
            if (behavior instanceof LootChest loot) {
                loot.doStop((ServerLevel) level, this, level.getGameTime());
            }
        }

        if (!level.isClientSide && hasPartner() && !isPartnerVillager) {
            Player player = level.getPlayerByUUID(getPartner().getUniqueId());
            if (player != null) player.sendMessage(getCombatTracker().getDeathMessage(), Util.NIL_UUID);
        }

        if (!isRemoved()) removeEntitiesOnShoulder();
    }

    private void tellFamilyThatIWasMurdered(DamageSource source) {
        if (isInsideRaid()) return;

        Entity entity = source.getEntity();
        if (!EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(entity)) return;

        Optional<NearestVisibleLivingEntities> optional = getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES);
        if (optional.isEmpty()) return;

        optional.get()
                .findAll(living -> living instanceof VillagerNPC npc && npc.isFamily(getUUID(), true))
                .forEach(living -> VillagerPanicTrigger.handleFightReaction(((Villager) living).getBrain(), (LivingEntity) entity));
    }

    @SuppressWarnings("unused")
    public boolean isCharging() {
        return entityData.get(DATA_IS_CHARGING);
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType, @Nullable SpawnGroupData groupData, @Nullable CompoundTag tag) {
        if (!wasInfected && !equipped && !isBaby() && spawnType != MobSpawnType.BREEDING) {
            plugin.equipVillager(getBukkitEntity(), true);
        }

        if (wasInfected) wasInfected = false;

        if (getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
            LocalDate now = LocalDate.now();
            int day = now.get(ChronoField.DAY_OF_MONTH);
            int month = now.get(ChronoField.MONTH_OF_YEAR);
            if (month == 10 && day == 31 && random.nextFloat() < Config.CHANCE_OF_WEARING_HALLOWEEN_MASK.asFloat()) {
                setSlotWithDropChance(
                        EquipmentSlot.HEAD,
                        new ItemStack(random.nextFloat() < 0.1f ? Blocks.JACK_O_LANTERN : Blocks.CARVED_PUMPKIN),
                        0.0f);
            }
        }

        return super.finalizeSpawn(level, difficulty, spawnType, groupData, tag);
    }

    public void setSlotWithDropChance(EquipmentSlot slot, ItemStack item, float chance) {
        setItemSlot(slot, item);
        setDropChance(slot, chance);
    }

    @Override
    public void setDropChance(EquipmentSlot slot, float chance) {
        (slot.getType() == EquipmentSlot.Type.HAND ? handDropChances : armorDropChances)[slot.getIndex()] = chance;
    }

    @Override
    public void onReputationEventFrom(ReputationEventType type, Entity entity) {
        boolean raidCheck = Config.VILLAGER_ATTACK_PLAYER_DURING_RAID.asBool() || !isInsideRaid();
        boolean thornsCheck = !(getLastDamageSource() instanceof EntityDamageSource source) || !source.isThorns();

        if (type != ReputationEventType.VILLAGER_HURT
                || (EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(entity)
                && raidCheck
                && thornsCheck)) {
            if (type == ReputationEventType.ZOMBIE_VILLAGER_CURED) {
                gossips.add(entity.getUUID(), GossipType.MAJOR_POSITIVE, 20);
                gossips.add(entity.getUUID(), GossipType.MINOR_POSITIVE, 25);
            } else if (type == ReputationEventType.TRADE) {
                gossips.add(entity.getUUID(), GossipType.TRADING, 2);
            } else if (type == ReputationEventType.VILLAGER_HURT) {
                gossips.add(entity.getUUID(), GossipType.MINOR_NEGATIVE, 25);
            } else if (type == ReputationEventType.VILLAGER_KILLED) {
                gossips.add(entity.getUUID(), GossipType.MAJOR_NEGATIVE, 25);
            }
        }

        if (!isPartner(entity.getUUID()) || !(entity instanceof ServerPlayer player)) return;

        if (getPlayerReputation(player) < Config.DIVORCE_IF_REPUTATION_IS_LESS_THAN.asInt()) {
            plugin.getMessages().send(player.getBukkitEntity(), this, Messages.Message.MARRY_END);

            divorceAndDropRing(player);
        }
    }

    public void divorceAndDropRing(Player player) {
        org.bukkit.inventory.ItemStack ring = plugin.getRing().getResult();
        getBukkitEntity().getInventory().removeItem(ring);
        drop(CraftItemStack.asNMSCopy(ring));

        setPartner(null);
        player.getBukkitEntity().getPersistentDataContainer().remove(plugin.getMarriedWith());
    }

    @Override
    public boolean isPartner(UUID uuid) {
        return partner != null && partner.getUniqueId().equals(uuid);
    }

    @Override
    public String getActivityName(String none) {
        return getBrain().getActiveNonCoreActivity().map(Activity::getName).orElse(none);
    }

    @Override
    public void addTarget(org.bukkit.entity.EntityType type) {
        ifTargetPresent(type, entityType -> targetEntities.add(entityType));
    }

    @Override
    public void removeTarget(org.bukkit.entity.EntityType type) {
        ifTargetPresent(type, entityType -> targetEntities.remove(entityType));
    }

    private void ifTargetPresent(org.bukkit.entity.EntityType type, Consumer<EntityType<?>> consumer) {
        EntityType.byString(type.name().toLowerCase()).ifPresent(consumer);
    }

    private Set<EntityType<?>> getDefaultTargets() {
        Set<EntityType<?>> types = new HashSet<>();

        for (String entity : plugin.getDefaultTargets()) {
            Optional<EntityType<?>> type = EntityType.byString(entity.toLowerCase());
            if (type.isEmpty()) continue;
            if (type.get().getCategory() != MobCategory.MONSTER) continue;
            types.add(type.get());
        }
        return types;
    }

    @Override
    public boolean isTarget(org.bukkit.entity.EntityType type) {
        for (EntityType<?> entityType : targetEntities) {
            if (entityType.toShortString().equalsIgnoreCase(type.name())) return true;
        }
        return false;
    }

    @Override
    public ItemStack eat(Level level, ItemStack item) {
        foodData.eat(item.getItem(), item);
        level.playSound(null, positionAsBlock(), SoundEvents.PLAYER_BURP, getSoundSource(), 0.5f, random.nextFloat() * 0.1f + 0.9f);
        return super.eat(level, item);
    }

    public boolean isHurt() {
        return getHealth() > 0.0f && getHealth() < getMaxHealth();
    }

    @Override
    public void causeFoodExhaustion(float exhaustion, VillagerExhaustionEvent.ExhaustionReason reason) {
        if (!level.isClientSide) {
            VillagerExhaustionEvent event = new VillagerExhaustionEvent(this, reason, exhaustion);
            if (!event.isCancelled()) foodData.addExhaustion(event.getExhaustion());
        }
    }

    @Override
    public SoundEvent getNotifyTradeSound() {
        return useVillagerSounds() ? super.getNotifyTradeSound() : null;
    }

    @Override
    public void notifyTradeUpdated(ItemStack item) {
        if (level.isClientSide || ambientSoundTime <= -getAmbientSoundInterval() + 20) return;

        ambientSoundTime = -getAmbientSoundInterval();

        SoundEvent tradeSound = getTradeUpdatedSound(!item.isEmpty());
        if (tradeSound != null) playSound(tradeSound, getSoundVolume(), getVoicePitch());
    }

    @Override
    public boolean startRiding(Entity entity) {
        if (!Config.DISABLE_VILLAGER_RIDING_NEARBY_BOAT.asBool()) return super.startRiding(entity);

        StackTraceElement[] stacktraces = Thread.currentThread().getStackTrace();
        if (stacktraces.length >= 3) {
            if (Boat.class.getName().equalsIgnoreCase(stacktraces[2].getClassName())) {
                return false;
            }
        }

        return super.startRiding(entity);
    }

    @Override
    protected SoundEvent getTradeUpdatedSound(boolean success) {
        return useVillagerSounds() ? super.getTradeUpdatedSound(success) : null;
    }

    @Override
    public void playCelebrateSound() {
        if (useVillagerSounds()) super.playCelebrateSound();
    }

    @Override
    public void setUnhappy() {
        setUnhappyCounter(40);
        if (!level.isClientSide() && useVillagerSounds()) {
            playSound(SoundEvents.VILLAGER_NO, getSoundVolume(), getVoicePitch());
        }
    }

    @Override
    public void setUnhappyCounter(int ticks) {
        if (Config.DISABLE_SKINS.asBool()) super.setUnhappyCounter(ticks);
    }

    @Override
    public void shakeHead(org.bukkit.entity.Player at) {
        if (shakingHead) return;

        shakingHeadAt = ((CraftPlayer) at).getHandle();
        getLookControl().setLookAt(shakingHeadAt);

        new BukkitRunnable() {
            int current;
            int turns;

            @Override
            public void run() {
                if (!getBukkitEntity().isValid()) {
                    cancel();
                    return;
                }

                if (current == ROTATION.length) {
                    current = 0;
                    turns++;
                }

                if (turns == 2) {
                    shakingHead = false;

                    if (shakingHeadAt.getBukkitEntity().isOnline()) {
                        getLookControl().setLookAt(shakingHeadAt);
                    }

                    shakingHeadAt = null;
                    cancel();
                    return;
                }

                yHeadRot += ROTATION[current] * 3;
                current++;
            }
        }.runTaskTimer(plugin, 4L, 1L);

        shakingHead = true;
    }

    @Override
    public void convertToVanilla() {
        unRide();

        Villager entity = new Villager(EntityType.VILLAGER, level, getVillagerData().getType());
        entity.restoreFrom(this);
        entity.moveTo(getX(), getY(), getZ(), getYRot(), getXRot());
        entity.setDeltaMovement(getDeltaMovement());
        entity.setId(getId());

        Component customName = getCustomName(true);
        if (customName != null) {
            entity.setCustomName(customName);
        }

        getBukkitEntity().setHandle(entity);

        try {
            BUKKIT_ENTITY.invoke(entity, getBukkitEntity());
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }

        dropLeash(true, false);
        discard();

        ((ServerLevel) level).addDuringTeleport(entity);
    }

    @Override
    public IVillagerNPC getOffline() {
        CompoundTag tag = new CompoundTag();
        savePluginData(tag);

        return OfflineVillagerNPC.from(uuid,
                (CompoundTag) tag.get(plugin.getNpcValuesKey().toString()),
                level.getWorld().getName(),
                getX(),
                getY(),
                getZ());
    }

    @Override
    public LastKnownPosition getLastKnownPosition() {
        // Only needed for offlines.
        return null;
    }

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return useVillagerSounds() ? super.getAmbientSound() : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        if (useVillagerSounds()) return super.getHurtSound(source);
        return switch (source.getMsgId()) {
            case "onFire" -> SoundEvents.PLAYER_HURT_ON_FIRE;
            case "drown" -> SoundEvents.PLAYER_HURT_DROWN;
            case "sweetBerryBush" -> SoundEvents.PLAYER_HURT_SWEET_BERRY_BUSH;
            case "freeze" -> SoundEvents.PLAYER_HURT_FREEZE;
            default -> SoundEvents.PLAYER_HURT;
        };
    }

    @Override
    protected SoundEvent getDeathSound() {
        return useVillagerSounds() ? super.getDeathSound() : SoundEvents.PLAYER_DEATH;
    }

    @Override
    public SoundEvent getSwimSound() {
        return useVillagerSounds() ? super.getSwimSound() : SoundEvents.PLAYER_SWIM;
    }

    @Override
    public SoundEvent getSwimSplashSound() {
        return useVillagerSounds() ? super.getSwimSplashSound() : SoundEvents.PLAYER_SPLASH;
    }

    @Override
    public SoundEvent getSwimHighSpeedSplashSound() {
        return useVillagerSounds() ? super.getSwimHighSpeedSplashSound() : SoundEvents.PLAYER_SPLASH_HIGH_SPEED;
    }

    @Override
    public SoundSource getSoundSource() {
        return useVillagerSounds() ? super.getSoundSource() : SoundSource.PLAYERS;
    }

    @Override
    public Fallsounds getFallSounds() {
        return useVillagerSounds() ? super.getFallSounds() : new Fallsounds(SoundEvents.PLAYER_SMALL_FALL, SoundEvents.PLAYER_BIG_FALL);
    }

    private boolean useVillagerSounds() {
        return Config.USE_VILLAGER_SOUNDS.asBool();
    }

    @Override
    public SimpleContainer getInventory() {
        return inventory;
    }

    public void setPartner(UUID partner, boolean isPartnerVillager) {
        if (isPartnerVillager) {
            IVillagerNPC partnerNPC = plugin.getTracker().getOffline(partner);
            if (partnerNPC != null) this.partner = partnerNPC;
        } else {
            this.partner = dummyPlayerOffline(partner);
        }
        this.isPartnerVillager = isPartnerVillager;
    }

    public static OfflineVillagerNPC dummyPlayerOffline(UUID uuid) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        CompoundTag tag = new CompoundTag();
        String name = player.getName();
        tag.putString("Name", name != null ? name : "???");
        return new OfflineVillagerNPC(uuid, tag, LastKnownPosition.ZERO);
    }

    @Override
    public int getFoodLevel() {
        return foodData.getFoodLevel();
    }

    @Override
    public boolean isFishing() {
        return fishing != null;
    }

    @Override
    public void toggleFishing() {
        if (fishing != null) {
            int damage = fishing.retrieve(getMainHandItem());
            getMainHandItem().hurtAndBreak(damage, this, (npc) -> npc.broadcastBreakEvent(InteractionHand.MAIN_HAND));

            level.playSound(null,
                    positionAsBlock(),
                    SoundEvents.FISHING_BOBBER_RETRIEVE,
                    SoundSource.NEUTRAL,
                    0.5f, 0.4f / (random.nextFloat() * 0.4f + 0.8f));
            gameEvent(GameEvent.FISHING_ROD_REEL_IN);
            return;
        }
        int luck = EnchantmentHelper.getFishingLuckBonus(getMainHandItem());
        int lureSpeed = EnchantmentHelper.getFishingSpeedBonus(getMainHandItem());

        DummyFishingHook hook = new DummyFishingHook(this, level, luck, lureSpeed);

        VillagerFishEvent fishEvent = new VillagerFishEvent(this, null, (FishHook) hook.getBukkitEntity(), VillagerFishEvent.State.FISHING);
        plugin.getServer().getPluginManager().callEvent(fishEvent);
        if (fishEvent.isCancelled()) {
            fishing = null;
            return;
        }

        level.playSound(
                null,
                positionAsBlock(),
                SoundEvents.FISHING_BOBBER_THROW,
                SoundSource.NEUTRAL,
                0.5f, 0.4f / (random.nextFloat() * 0.4f + 0.8f));
        level.addFreshEntity(hook);

        gameEvent(GameEvent.FISHING_ROD_CAST);
    }

    @Override
    public void sendSpawnPacket() {
        sendPacket(new ClientboundAddEntityPacket(this));
        sendPacket(new ClientboundSetEntityDataPacket(getId(), getEntityData(), true));
    }

    @Override
    public void sendDestroyPacket() {
        sendPacket(new ClientboundRemoveEntitiesPacket(getId()));
    }

    private void sendPacket(Packet<?> packet) {
        for (ServerPlayer player : ((ServerLevel) level).players()) {
            player.connection.connection.send(packet);
        }
    }

    public void startTrading(Player player) {
        updateSpecialPrices(player);
        setTradingPlayer(player);
        openTradingScreen(player, getDisplayName(), getVillagerData().getLevel());
    }

    @SuppressWarnings("WhileLoopReplaceableByForEach")
    private void updateSpecialPrices(Player player) {
        if (Config.DISABLE_SPECIAL_PRICES.asBool() || (Config.DISABLE_SPECIAL_PRICES_IF_ALLOWED_TO_MODIFY_INVENTORY.asBool()
                && InventoryListeners.canModifyInventory(this, (org.bukkit.entity.Player) player.getBukkitEntity()))) {
            return;
        }

        int reputation = getPlayerReputation(player);
        if (reputation != 0) {
            Iterator<MerchantOffer> offers = getOffers().iterator();
            while (offers.hasNext()) {
                MerchantOffer offer = offers.next();
                offer.addToSpecialPriceDiff(-Mth.floor((float) reputation * offer.getPriceMultiplier()));
            }
        }

        MobEffectInstance heroEffect = player.getEffect(MobEffects.HERO_OF_THE_VILLAGE);
        if (heroEffect == null) return;

        int amplifier = heroEffect.getAmplifier();
        Iterator<MerchantOffer> offers = getOffers().iterator();

        while (offers.hasNext()) {
            MerchantOffer offer = offers.next();
            int discount = (int) Math.floor((0.3d + 0.0625d * (double) amplifier) * (double) offer.getBaseCostA().getCount());
            offer.addToSpecialPriceDiff(-Math.max(discount, 1));
        }
    }

    @Override
    public boolean isFemale() {
        return sex.equalsIgnoreCase("female");
    }

    @Override
    public boolean isMale() {
        return sex.equalsIgnoreCase("male");
    }

    @Override
    public boolean is(org.bukkit.entity.Villager.Profession... professions) {
        for (org.bukkit.entity.Villager.Profession profession : professions) {
            if (is(CraftVillager.bukkitToNmsProfession(profession))) return true;
        }
        return false;
    }

    @Override
    public SlotAccess getSlot(int slot) {
        int finalSlot = slot - 300;
        return finalSlot >= 0 && finalSlot < inventory.getContainerSize() ?
                SlotAccess.forContainer(inventory, finalSlot) :
                super.getSlot(slot);
    }

    public BlockPos positionAsBlock() {
        return new BlockPos(position());
    }

    @Override
    public void increaseMerchantCareer() {
        super.increaseMerchantCareer();

        if (!Config.USE_VILLAGER_SOUNDS.asBool()) {
            level.playSound(null, positionAsBlock(), SoundEvents.PLAYER_LEVELUP, getSoundSource(), 1.0f, 1.0f);
        }
    }

    @Override
    public void pickUpItem(ItemEntity entity) {
        ItemStack stack = entity.getItem();
        if (!wantsToPickUp(stack)) return;

        SimpleContainer container = getInventory();
        if (!container.canAddItem(stack)) return;

        ItemStack fakeRemaining = new SimpleContainer(container).addItem(stack);

        UUID thrower = getThrower(stack);
        boolean wasFromGift = isExpectingGiftFrom(thrower);

        EntityPickupItemEvent event = CraftEventFactory.callEntityPickupItemEvent(this, entity, fakeRemaining.getCount(), false);

        stack = CraftItemStack.asNMSCopy(event.getItem().getItemStack());

        onItemPickup(entity);
        take(entity, stack.getCount() - fakeRemaining.getCount());

        Item item = stack.getItem();
        for (Class<? extends Item> clazz : DO_NOT_SAVE) {
            if (clazz.isAssignableFrom(item.getClass())) {
                handleRemaining(stack, fakeRemaining, entity);
                if (!wasFromGift) {
                    ItemStackUtils.setBetterWeaponInMaindHand(getBukkitEntity(), event.getItem().getItemStack(), true, true);
                    ItemStackUtils.setArmorItem(getBukkitEntity(), event.getItem().getItemStack());
                }
                return;
            }
        }

        ItemStack remaining = container.addItem(stack);
        handleRemaining(stack, remaining, entity);
    }

    public boolean isDoingNothing(boolean checkAllChangeItemType) {
        return isDoingNothing(checkAllChangeItemType ? ChangeItemType.NONE : null);
    }

    public boolean isDoingNothing(@Nullable ChangeItemType type) {
        return !isFighting()
                && !isExpecting()
                && !isInteracting()
                && !isTrading()
                && !isProcreating()
                && !isFishing()
                && (type == null || !isChangingItem(type));
    }

    public boolean isDoingNothing(ChangeItemType... types) {
        for (ChangeItemType type : types) {
            ChangeItemType changing = getChangingItem(type);
            if (changing != null && !ArrayUtils.contains(types, changing)) {
                return false;
            }
        }

        return isDoingNothing((ChangeItemType) null);
    }

    public boolean isChangingItem(ChangeItemType ignore) {
        return getChangingItem(ignore) != null;
    }

    private ChangeItemType getChangingItem(ChangeItemType ignore) {
        if (ignore.isEating(isEating)) return ChangeItemType.EATING;
        if (ignore.isShowingTrades(showingTrades)) return ChangeItemType.SHOWING_TRADES;
        if (ignore.isTaming(isTaming)) return ChangeItemType.TAMING;
        if (ignore.isHealingGolem(isHealingGolem)) return ChangeItemType.HEALING_GOLEM;
        if (ignore.isUsingBoneMeal(isUsingBoneMeal)) return ChangeItemType.USING_BONE_MEAL;
        if (ignore.isLooting(isLooting)) return ChangeItemType.LOOTING;
        return null;
    }

    private void handleRemaining(ItemStack original, ItemStack remaining, ItemEntity itemEntity) {
        if (remaining.isEmpty()) itemEntity.discard();
        else original.setCount(remaining.getCount());
    }

    @Override
    public VillagerNPC getBreedOffspring(ServerLevel level, AgeableMob breedWith) {
        double chance = random.nextDouble();

        VillagerType type;
        if (chance < 0.5d) {
            type = VillagerType.byBiome(level.getBiome(blockPosition()));
        } else if (chance < 0.75d) {
            type = getVillagerData().getType();
        } else {
            type = ((Villager) breedWith).getVillagerData().getType();
        }

        VillagerNPC baby = new VillagerNPC(EntityType.VILLAGER, level, type);
        baby.finalizeSpawn(level, level.getCurrentDifficultyAt(baby.blockPosition()), MobSpawnType.BREEDING, null, null);
        return baby;
    }

    @Override
    public boolean wantsToPickUp(ItemStack stack) {
        if (!getInventory().canAddItem(stack)) return false;

        UUID thrower = getThrower(stack);
        if (isExpectingGiftFrom(thrower)) return true;
        if (fished(stack)) return true;

        return thrower == null && plugin.getWantedItem(this, CraftItemStack.asBukkitCopy(stack), true) != null;
    }

    public boolean fished(ItemStack item) {
        ItemMeta meta = CraftItemStack.asBukkitCopy(item).getItemMeta();
        if (meta != null) {
            String uuidString = meta.getPersistentDataContainer().get(plugin.getFishedKey(), PersistentDataType.STRING);
            if (uuidString != null) return UUID.fromString(uuidString).equals(getUUID());
        }
        return false;
    }

    private UUID getThrower(ItemStack stack) {
        ItemMeta meta = CraftItemStack.asBukkitCopy(stack).getItemMeta();
        if (meta != null) {
            String uuidString = meta.getPersistentDataContainer().get(plugin.getGiftKey(), PersistentDataType.STRING);
            if (uuidString != null) return UUID.fromString(uuidString);
        }
        return null;
    }

    public VillagerProfession getProfession() {
        return getVillagerData().getProfession();
    }

    @Override
    public void startSleeping(BlockPos pos) {
        Optional<Long> lastSlept = brain.getMemory(MemoryModuleType.LAST_SLEPT);

        super.startSleeping(pos);
        collides = false;

        if (plugin.getTracker().fixSleep()) brain.setMemory(MemoryModuleType.LAST_SLEPT, lastSlept);
    }

    @Override
    public void stopSleeping() {
        Optional<Long> lastWoken = getBrain().getMemory(MemoryModuleType.LAST_WOKEN);

        super.stopSleeping();
        collides = true;

        if (plugin.getTracker().fixSleep()) brain.setMemory(MemoryModuleType.LAST_WOKEN, lastWoken);
    }

    @Override
    public <T extends Mob> T convertTo(EntityType<T> to, boolean equipment, EntityTransformEvent.TransformReason transformReason, CreatureSpawnEvent.SpawnReason spawnReason) {
        return Config.ZOMBIE_INFECTION.asBool() ? super.convertTo(to, equipment, transformReason, spawnReason) : null;
    }

    @Override
    public void thunderHit(ServerLevel level, LightningBolt lightning) {
        if (Config.WITCH_CONVERTION.asBool() && fromTrident(lightning)) {
            super.thunderHit(level, lightning);
        }
    }

    @SuppressWarnings("ConstantConditions")
    private boolean fromTrident(LightningBolt lightning) {
        // If the cause is a player, then it's from a player trident.
        if (lightning.getCause() != null) return true;

        for (MetadataValue meta : lightning.getBukkitEntity().getMetadata("Cause")) {
            if (!plugin.equals(meta.getOwningPlugin())) continue;
            if (!(meta.value() instanceof LightningStrikeEvent.Cause cause)) continue;

            // 100% chance that this lightning is from a trident thrown by a villager.
            if (cause == LightningStrikeEvent.Cause.TRIDENT) {
                return Config.WITCH_CONVERTION_FROM_VILLAGER_TRIDENT.asBool();
            }
        }

        // Probably it's a natural lightning.
        return true;
    }

    @Override
    public double getMeleeAttackRangeSqr(@Nullable LivingEntity living) {
        return Config.MELEE_ATTACK_RANGE.asDouble();
    }

    public void startAutoSpinAttack(int autoSpinAttackTicks) {
        this.autoSpinAttackTicks = autoSpinAttackTicks;
        if (!level.isClientSide) {
            removeEntitiesOnShoulder();
            setLivingEntityFlag(4, true);
            setPose(Pose.SPIN_ATTACK);
        }
    }

    @Override
    public float getStandingEyeHeight(Pose pose, EntityDimensions dimensions) {
        return switch (pose) {
            case FALL_FLYING, SWIMMING, SPIN_ATTACK -> 0.4f;
            case CROUCHING -> 1.27f;
            default -> 1.62f;
        };
    }

    @Override
    public void checkAutoSpinAttack(AABB first, AABB second) {
        // super.checkAutoSpinAttack(first, second);

        List<Entity> list = level.getEntities(
                this,
                first.minmax(second),
                entity -> entity instanceof LivingEntity living && !(living instanceof VillagerNPC));

        if (!list.isEmpty()) {
            for (Entity entity : list) {
                doAutoAttackOnTouch((LivingEntity) entity);
                autoSpinAttackTicks = 0;
                setDeltaMovement(getDeltaMovement().scale(-0.2d));
                break;
            }
        } else if (horizontalCollision) {
            autoSpinAttackTicks = 0;
        }

        if (!level.isClientSide && autoSpinAttackTicks <= 0) {
            setLivingEntityFlag(4, false);
        }

        if (!isAutoSpinAttack()) {
            setPose(Pose.STANDING);
        }
    }

    @Override
    protected void doAutoAttackOnTouch(LivingEntity living) {
        doHurtTarget(living);
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);

        VillagerRemoveEvent removeEvent = new VillagerRemoveEvent(this, toWrapper(reason));
        plugin.getServer().getPluginManager().callEvent(removeEvent);
    }

    private VillagerRemoveEvent.RemovalReason toWrapper(RemovalReason reason) {
        return switch (reason) {
            case KILLED -> VillagerRemoveEvent.RemovalReason.KILLED;
            case DISCARDED -> VillagerRemoveEvent.RemovalReason.DISCARDED;
            case UNLOADED_TO_CHUNK -> VillagerRemoveEvent.RemovalReason.UNLOADED_TO_CHUNK;
            case UNLOADED_WITH_PLAYER -> VillagerRemoveEvent.RemovalReason.UNLOADED_WITH_PLAYER;
            case CHANGED_DIMENSION -> VillagerRemoveEvent.RemovalReason.CHANGED_DIMENSION;
        };
    }

    @Override
    public void spawnGolemIfNeeded(ServerLevel level, long time, int villagersRequried) {
        if (!Config.VILLAGER_SPAWN_IRON_GOLEM.asBool()) return;
        super.spawnGolemIfNeeded(level, time, villagersRequried);
    }

    @Override
    protected void hurtCurrentlyUsedShield(float blockingModifier) {
        if (!useItem.is(Items.SHIELD)) return;

        if (blockingModifier < 3.0f) return;

        int damage = 1 + Mth.floor(blockingModifier);

        InteractionHand hand = getUsedItemHand();
        useItem.hurtAndBreak(damage, this, (entityhuman) -> entityhuman.broadcastBreakEvent(hand));

        if (!useItem.isEmpty()) return;

        if (hand == InteractionHand.MAIN_HAND) {
            setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        } else {
            setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        }

        useItem = ItemStack.EMPTY;
        playSound(SoundEvents.SHIELD_BREAK, 0.8f, 0.8f + random.nextFloat() * 0.4f);
    }

    @Override
    protected void hurtArmor(DamageSource source, float damage) {
        hurt(source, damage, EquipmentSlot.values());
    }

    @Override
    protected void hurtHelmet(DamageSource source, float damage) {
        hurt(source, damage, EquipmentSlot.HEAD);
    }

    private void hurt(DamageSource source, float damage, EquipmentSlot... slots) {
        if (damage <= 0.0f) return;

        damage /= 4.0f;
        if (damage < 1.0f) damage = 1.0f;

        for (EquipmentSlot slot : slots) {
            if (slot.getType() == EquipmentSlot.Type.HAND) continue;
            ItemStack item = getItemBySlot(slot);

            if ((!source.isFire() || !item.getItem().isFireResistant()) && item.getItem() instanceof ArmorItem) {
                item.hurtAndBreak(
                        (int) damage,
                        this,
                        npc -> npc.broadcastBreakEvent(EquipmentSlot.byTypeAndIndex(EquipmentSlot.Type.ARMOR, slot.getIndex())));
            }
        }
    }

    @Override
    protected void blockUsingShield(LivingEntity living) {
        super.blockUsingShield(living);
        if (living.getMainHandItem().getItem() instanceof AxeItem) disableShield(true);
    }

    @Override
    public @Nullable Component getCustomName() {
        return getCustomName(false);
    }

    private @Nullable Component getCustomName(boolean vanilla) {
        return vanilla ? super.getCustomName() : new TextComponent(villagerName);
    }

    public boolean canBreedWith(VillagerNPC other) {
        return other.getSex() != null
                && !other.getSex().equalsIgnoreCase(sex)
                && canBreed()
                && other.canBreed()
                && (!hasPartner() ? !other.hasPartner() : isPartner(other.getUUID()))
                && !isFamily(other.getUUID(), false);
    }

    @Override
    public boolean isFamily(UUID otherUUID) {
        return isFamily(otherUUID, false);
    }

    @Override
    public void gossip(ServerLevel level, Villager with, long time) {
        if (!(with instanceof VillagerNPC npc)) return;
        if ((time >= lastGossipTime && time < lastGossipTime + 1200L)) return;
        if ((time >= npc.getLastGossipTime() && time < npc.getLastGossipTime() + 1200L)) return;

        getGossips().transferFrom(npc.getGossips(), random, Config.MAX_GOSSIP_TOPICS.asInt());
        lastGossipTime = time;
        npc.setLastGossipTime(time);
        spawnGolemIfNeeded(level, time, 5);
    }

    @Override
    public boolean isFamily(UUID otherUUID, boolean checkPartner) {
        return (checkPartner && isPartner(otherUUID))
                || isChildren(otherUUID)
                || (father != null && father.getUniqueId().equals(otherUUID))
                || (mother != null && mother.getUniqueId().equals(otherUUID));
    }

    private boolean isChildren(UUID uuid) {
        for (IVillagerNPC npc : childrens) {
            if (npc.getUniqueId().equals(uuid)) return true;
        }
        return false;
    }

    public void disableShield(boolean flag) {
        float chanceOfBlocking = 0.25f + (float) EnchantmentHelper.getBlockEfficiency(this) * 0.05f;
        if (flag) chanceOfBlocking += 0.75f;

        if (random.nextFloat() < chanceOfBlocking) {
            getCooldowns().addCooldown(Items.SHIELD, 100);
            stopUsingItem();
            level.broadcastEntityEvent(this, (byte) 30);
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();

        if (isAlive() && thrownTrident != null && thrownTrident.getBukkitEntity().isValid()) {
            AABB box;
            Entity vehicle = getVehicle();
            if (vehicle != null && !vehicle.isRemoved()) {
                box = getBoundingBox().minmax(vehicle.getBoundingBox()).inflate(1.0d, 0.0d, 1.0d);
            } else {
                box = getBoundingBox().inflate(1.0d, 0.5d, 1.0d);
            }

            for (Entity entity : level.getEntities(this, box)) {
                if (entity.isRemoved() || !(entity instanceof ThrownTrident trident)) continue;
                if (trident.shakeTime > 0 || (!trident.inGround && !trident.isNoPhysics())) continue;

                ItemStack tridentItem = trident.tridentItem;
                if (!tridentItem.isEmpty()
                        && is(trident.getOwner())
                        && getInventory().canAddItem(tridentItem)) {
                    getInventory().addItem(tridentItem);
                    take(trident, 1);
                    trident.discard();
                }
            }
        }

        playShoulderEntityAmbientSound(shoulderEntityLeft);
        playShoulderEntityAmbientSound(shoulderEntityRight);

        if (!level.isClientSide && (fallDistance > 0.5f || isInWater()) || isSleeping() || isInPowderSnow) {
            removeEntitiesOnShoulder();
        }

        if (level.getDifficulty() != Difficulty.PEACEFUL || !level.getGameRules().getBoolean(GameRules.RULE_NATURAL_REGENERATION)) {
            return;
        }

        if (getHealth() < getMaxHealth() && tickCount % 20 == 0) {
            heal(1.0f, EntityRegainHealthEvent.RegainReason.REGEN);
        }

        if (foodData.needsFood() && tickCount % 10 == 0) {
            foodData.setFoodLevel(foodData.getFoodLevel() + 1);
        }
    }

    @Override
    public void tick() {
        super.tick();
        cooldowns.tick();
        foodData.tick();

        maybeDecayGossip();

        double speedModifier = moveControl.getSpeedModifier();
        if (isSwimming()
                || isVisuallySwimming()
                || isInWater()
                || isUnderWater()
                || getFluidHeight(FluidTags.WATER) > getFluidJumpThreshold()
                || isInLava()) {
            setSpeed((float) (SWIMMING_SPEED_MODIFIER * getAttributeValue(Attributes.MOVEMENT_SPEED)));
        } else if (isEating()) {
            setSpeed((float) ((SPEED_MODIFIER / 2) * getAttributeValue(Attributes.MOVEMENT_SPEED)));
        } else if (previousSpeedModifier != speedModifier) {
            setSpeed((float) (speedModifier * getAttributeValue(Attributes.MOVEMENT_SPEED)));
        }

        if (!collides && !isSleeping()) collides = true;

        if (expectingTicks > 0) {
            if (isExpectingBed() || !giftDropped) expectingTicks--;
        } else if (expectingFrom != null) {
            plugin.getExpectingManager().remove(expectingFrom);

            Player player = level.getPlayerByUUID(expectingFrom);
            if (player instanceof ServerPlayer serverPlayer) {
                if (expectingType.isGift()) {
                    plugin.getMessages().send(serverPlayer.getBukkitEntity(), this, Messages.Message.GIFT_EXPECTING_FAIL);
                } else {
                    plugin.getMessages().send(serverPlayer.getBukkitEntity(), this, Messages.Message.SET_HOME_FAIL);
                }
            }

            stopExpecting();
        }
    }

    private void maybeDecayGossip() {
        long time = level.getGameTime();
        if (lastGossipDecayTime == 0L) {
            lastGossipDecayTime = time;
        } else if (time >= lastGossipDecayTime + GOSSIP_DECAY_INTERVAL) {
            gossips.decay();
            lastGossipDecayTime = time;
        }
    }

    @Override
    protected boolean damageEntity0(DamageSource source, float damage) {
        lastDamageSource = source;
        boolean flag = super.damageEntity0(source, damage);
        lastDamageSource = null;
        return flag;
    }

    @Override
    public boolean isDamageSourceBlocked() {
        return lastDamageSource != null && isDamageSourceBlocked(lastDamageSource);
    }

    public boolean setEntityOnShoulder(CompoundTag tag) {
        if (isPassenger() || !onGround || isInWater() || isInPowderSnow) return false;

        boolean leftEmpty = shoulderEntityLeft.isEmpty();
        if (!leftEmpty && !shoulderEntityRight.isEmpty()) return false;

        if (leftEmpty) {
            setShoulderEntityLeft(tag);
        } else {
            setShoulderEntityRight(tag);
        }

        updateNPC();

        timeEntitySatOnShoulder = level.getGameTime();
        return true;
    }

    private void playShoulderEntityAmbientSound(@Nullable CompoundTag tag) {
        if (tag == null
                || tag.isEmpty()
                || tag.getBoolean("Silent")
                || level.random.nextInt(200) != 0) return;

        String id = tag.getString("id");
        EntityType.byString(id)
                .filter((type) -> type == EntityType.PARROT)
                .ifPresent((type) -> {
                    if (Parrot.imitateNearbyMobs(level, this)) return;
                    level.playSound(null,
                            getX(),
                            getY(),
                            getZ(),
                            Parrot.getAmbient(level, level.random),
                            getSoundSource(),
                            1.0f,
                            Parrot.getPitch(level.random));
                });
    }

    private void removeEntitiesOnShoulder() {
        if (timeEntitySatOnShoulder + 20L >= level.getGameTime()) return;
        if (shoulderEntityLeft.isEmpty() && shoulderEntityRight.isEmpty()) return;

        if (spawnEntityFromShoulder(shoulderEntityLeft)) {
            setShoulderEntityLeft(new CompoundTag());
        }

        if (spawnEntityFromShoulder(shoulderEntityRight)) {
            setShoulderEntityRight(new CompoundTag());
        }

        updateNPC();
    }

    private void updateNPC() {
        Optional<NPC> npc = plugin.getTracker().getNPC(getId());
        if (npc.isEmpty()) return;

        MetadataModifier metadata = npc.get().metadata();
        metadata.queue(MetadataModifier.EntityMetadata.SHOULDER_ENTITY_LEFT, getShoulderEntityLeft()).send();
        metadata.queue(MetadataModifier.EntityMetadata.SHOULDER_ENTITY_RIGHT, getShoulderEntityRight()).send();
    }

    private boolean spawnEntityFromShoulder(CompoundTag tag) {
        return !level.isClientSide && !tag.isEmpty() ? EntityType.create(tag, level).map((entity) -> {
            if (entity instanceof PetParrot parrot) {
                parrot.setOwnerUUID(uuid);
            }

            entity.setPos(getX(), getY() + 0.699999988079071d, getZ());
            return ((ServerLevel) level).addWithUUID(entity, CreatureSpawnEvent.SpawnReason.SHOULDER_ENTITY);
        }).orElse(true) : true;
    }

    @Override
    public Object getShoulderEntityLeft() {
        return shoulderEntityLeft;
    }

    @Override
    public Object getShoulderEntityRight() {
        return shoulderEntityRight;
    }

    @Override
    public boolean hurt(DamageSource source, float damage) {
        boolean damaged = super.hurt(source, damage);
        if (damaged) removeEntitiesOnShoulder();
        return damaged;
    }

    @Override
    public @Nullable LivingEntity getTarget() {
        return brain.getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
    }

    public void startExpectingFrom(ExpectingType expectingType, UUID expectingGiftFrom, int expectingGiftTicks) {
        setExpectingType(expectingType);
        setExpectingFrom(expectingGiftFrom);
        setExpectingTicks(expectingGiftTicks);
    }

    @Override
    public void divorceAndDropRing(org.bukkit.entity.Player player) {
        divorceAndDropRing(((CraftPlayer) player).getHandle());
    }

    @Override
    public void drop(org.bukkit.inventory.ItemStack item) {
        drop(CraftItemStack.asNMSCopy(item));
    }

    @Override
    public void startTrading(org.bukkit.entity.Player player) {
        startTrading(((CraftPlayer) player).getHandle());
    }

    public boolean isExpectingGift() {
        return isExpecting() && expectingType.isGift();
    }

    public boolean isExpectingBed() {
        return isExpecting() && expectingType.isBed();
    }

    public boolean isExpecting() {
        return expectingTicks > 0 && expectingFrom != null;
    }

    public void stopExpecting() {
        startExpectingFrom(null, null, 0);
        giftDropped = false;
    }

    public boolean isStayingInPlace() {
        return checkCurrentActivity(STAY) && isInteracting() && interactType.isStay();
    }

    public boolean checkCurrentActivity(Activity checkActivity) {
        Optional<Activity> active = getBrain().getActiveNonCoreActivity();
        return active.isPresent() && active.get().equals(checkActivity);
    }

    public boolean checkCurrentActivity(Activity... checkActivities) {
        for (Activity checkActivity : checkActivities) {
            if (checkCurrentActivity(checkActivity)) return true;
        }
        return false;
    }

    public void drop(ItemStack item) {
        if (item.isEmpty()) return;
        if (level.isClientSide) swing(InteractionHand.MAIN_HAND);

        ItemEntity itemEntity = new ItemEntity(level, getX(), getEyeY() - 0.30000001192092896d, getZ(), item);
        itemEntity.setPickUpDelay(40);
        itemEntity.setThrower(getUUID());

        float xRotSin = Mth.sin(getXRot() * 0.017453292f);
        float xRotCos = Mth.cos(getXRot() * 0.017453292f);

        float yRotSin = Mth.sin(getYRot() * 0.017453292f);
        float yRotCos = Mth.cos(getYRot() * 0.017453292f);

        float f5 = random.nextFloat() * 6.2831855f;
        float f6 = random.nextFloat() * 0.02f;

        itemEntity.setDeltaMovement(
                (double) (-yRotSin * xRotCos * 0.3f) + Math.cos(f5) * (double) f6,
                -xRotSin * 0.3f + 0.1f + (random.nextFloat() - random.nextFloat()) * 0.1f,
                (double) (yRotCos * xRotCos * 0.3f) + Math.sin(f5) * (double) f6);

        level.addFreshEntity(itemEntity);
    }

    @Override
    public UUID getUniqueId() {
        return uuid;
    }

    @Override
    public int getReputation(UUID uuid) {
        return getGossips().getReputation(uuid, (type) -> true);
    }

    @Override
    public org.bukkit.entity.Villager bukkit() {
        return getBukkitEntity();
    }

    @Override
    public void addMinorPositive(UUID uuid, int amount) {
        getGossips().add(uuid, GossipType.MINOR_POSITIVE, amount);
    }

    @Override
    public void addMinorNegative(UUID uuid, int amount) {
        getGossips().add(uuid, GossipType.MINOR_NEGATIVE, amount);
    }

    @Override
    public void jumpIfPossible() {
        if (isOnGround()) getJumpControl().jump();
    }

    @Override
    public boolean handleBedHome(Block block) {
        BlockPos bedPosition = new BlockPos(block.getX(), block.getY(), block.getZ());

        Predicate<PoiType> predicate = PoiType.HOME.getPredicate();

        // Add poi if doesn't exists.
        PoiManager poiManager = ((ServerLevel) level).getPoiManager();
        if (!poiManager.exists(bedPosition, predicate)) {
            poiManager.add(bedPosition, PoiType.HOME);
        }

        // Bed already setted in same position.
        Optional<GlobalPos> previousHome = getBrain().getMemory(MemoryModuleType.HOME);
        if (previousHome.isPresent()) {
            Optional<BlockPos> temp = poiManager.find(predicate, pos -> pos.equals(bedPosition), bedPosition, 1, PoiManager.Occupancy.ANY);
            if (temp.isPresent() && temp.get().equals(previousHome.get().pos())) {
                releasePoi(MemoryModuleType.HOME);
            }
        }

        Optional<BlockPos> takePos = poiManager.take(predicate, pos -> pos.equals(bedPosition), bedPosition, 1);
        if (takePos.isPresent()) {
            // Release previous POI.
            releasePoi(MemoryModuleType.HOME);
        } else {
            plugin.getLogger().warning("Either the POI is already occupied or an error occurred when trying to acquire it.");
            return false;
        }

        GlobalPos bedHome = GlobalPos.of(level.dimension(), bedPosition);
        getBrain().setMemory(MemoryModuleType.HOME, bedHome);

        setBedHomeWorld(block.getWorld().getUID());
        setBedHome(bedHome.pos());

        level.broadcastEntityEvent(this, (byte) 14);
        DebugPackets.sendPoiTicketCountPacket((ServerLevel) level, bedPosition);
        return true;
    }

    @Override
    public void stayInPlace() {
        getBrain().setMemory(STAY_PLACE, GlobalPos.of(level.dimension(), blockPosition()));
        getBrain().setDefaultActivity(STAY);
        getBrain().setActiveActivityIfPossible(STAY);
    }

    public void stopStayingInPlace() {
        getBrain().eraseMemory(STAY_PLACE);
        getBrain().setDefaultActivity(Activity.IDLE);
        getBrain().updateActivityFromSchedule(level.getDayTime(), level.getGameTime());
    }

    @Override
    public void reactToSeekHorn(org.bukkit.entity.Player player) {

    }

    @Override
    public boolean isInsideRaid() {
        return ((ServerLevel) level).getRaidAt(blockPosition()) != null;
    }

    @Override
    public boolean isFighting() {
        return getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET);
    }

    @Override
    public boolean isProcreating() {
        return procreatingWith != null;
    }

    @Override
    public boolean isExpectingGiftFrom(UUID uuid) {
        return isExpectingGift() && expectingFrom.equals(uuid);
    }

    @Override
    public boolean isExpectingBedFrom(UUID uuid) {
        return isExpectingBed() && expectingFrom.equals(uuid);
    }

    @Override
    public boolean isInteracting() {
        return interactingWith != null && interactType != null;
    }

    @Override
    public void spawnEntityEventParticle(Particle particle) {
        getBukkitEntity().getWorld().spawnParticle(
                particle,
                getRandomX(1.05d),
                getRandomY() + 1.15d,
                getRandomZ(1.05d),
                1,
                random.nextGaussian() * 0.02d,
                random.nextGaussian() * 0.02d,
                random.nextGaussian() * 0.02d);
    }
}