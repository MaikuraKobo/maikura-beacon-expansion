package jp.exnakamura.beaconexpansion;

import com.mojang.brigadier.CommandDispatcher;
import jp.exnakamura.beaconexpansion.mixin.BeaconBlockEntityAccessor;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.block.Blocks;
import net.minecraft.block.Block;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BeaconBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.PhantomEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.Items;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.Registry;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.util.Identifier;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.village.TradeOffer;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.util.WorldSavePath;

import java.util.*;
import java.util.Properties;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Field;

import static net.minecraft.server.command.CommandManager.literal;

public class BeaconExpansionMod implements ModInitializer {
    public static final String MOD_ID = "beacon_expansion";

    public static final Identifier BEACON_CORE_ID = Identifier.of(MOD_ID, "beacon_core");
    public static final Identifier ENHANCED_BEACON_CORE_ID = Identifier.of(MOD_ID, "enhanced_beacon_core");
    public static final Identifier GOLD_DECORATED_GLASS_ID = Identifier.of(MOD_ID, "gold_decorated_glass");
    public static final Identifier PURPLE_GOLD_DECORATED_GLASS_ID = Identifier.of(MOD_ID, "purple_gold_decorated_glass");
    public static final RegistryKey<Block> BEACON_CORE_BLOCK_KEY = RegistryKey.of(RegistryKeys.BLOCK, BEACON_CORE_ID);
    public static final RegistryKey<Item> BEACON_CORE_ITEM_KEY = RegistryKey.of(RegistryKeys.ITEM, BEACON_CORE_ID);
    public static final RegistryKey<Block> ENHANCED_BEACON_CORE_BLOCK_KEY = RegistryKey.of(RegistryKeys.BLOCK, ENHANCED_BEACON_CORE_ID);
    public static final RegistryKey<Item> ENHANCED_BEACON_CORE_ITEM_KEY = RegistryKey.of(RegistryKeys.ITEM, ENHANCED_BEACON_CORE_ID);
    public static final RegistryKey<Block> GOLD_DECORATED_GLASS_BLOCK_KEY = RegistryKey.of(RegistryKeys.BLOCK, GOLD_DECORATED_GLASS_ID);
    public static final RegistryKey<Item> GOLD_DECORATED_GLASS_ITEM_KEY = RegistryKey.of(RegistryKeys.ITEM, GOLD_DECORATED_GLASS_ID);
    public static final RegistryKey<Block> PURPLE_GOLD_DECORATED_GLASS_BLOCK_KEY = RegistryKey.of(RegistryKeys.BLOCK, PURPLE_GOLD_DECORATED_GLASS_ID);
    public static final RegistryKey<Item> PURPLE_GOLD_DECORATED_GLASS_ITEM_KEY = RegistryKey.of(RegistryKeys.ITEM, PURPLE_GOLD_DECORATED_GLASS_ID);
    public static final Block BEACON_CORE = new Block(AbstractBlock.Settings.create()
            .registryKey(BEACON_CORE_BLOCK_KEY)
            .strength(6.0F, 1200.0F)
            .nonOpaque()
            .luminance(state -> 15));
    public static final Block ENHANCED_BEACON_CORE = new Block(AbstractBlock.Settings.create()
            .registryKey(ENHANCED_BEACON_CORE_BLOCK_KEY)
            .strength(8.0F, 1200.0F)
            .nonOpaque()
            .luminance(state -> 15));
    public static final Block GOLD_DECORATED_GLASS = new Block(AbstractBlock.Settings.copy(Blocks.GLASS)
            .registryKey(GOLD_DECORATED_GLASS_BLOCK_KEY)
            .nonOpaque());
    public static final Block PURPLE_GOLD_DECORATED_GLASS = new Block(AbstractBlock.Settings.copy(Blocks.GLASS)
            .registryKey(PURPLE_GOLD_DECORATED_GLASS_BLOCK_KEY)
            .nonOpaque());

    private static final int RANGE_CORE = 128;
    private static final int RANGE_ENHANCED_CORE = 256;
    private static final int RANGE_TOP = 256;
    private static final int VANILLA_RANGE_BASE = 10;
    private static final int SCAN_INTERVAL = 20;
    private static final int LOCAL_BEACON_SCAN_RADIUS = 16;
    // v2-dev.28: 通常ビーコンの選択効果取得を補強。
    // 既知ビーコン探索半径は最大拡張範囲より少し広い288へ統一する。
    private static final int VANILLA_BEACON_TRACK_RADIUS = 288;
    private static final int WIDE_BEACON_SCAN_RADIUS = 288;
    private static final int WIDE_SCAN_INTERVAL = 40;
    private static final int WARD_SPAWN_CHECK_RANGE = 256;
    private static final int INFRA_SCAN_RADIUS = 64;
    private static final int FERTILITY_RADIUS = 12;
    private static final int WORKSHOP_RADIUS = 3;
    private static final int ALCHEMY_RADIUS = 3;

    // v2.2.0: エフェクト数が多い時の視認性を優先するため、
    // このMODが付与する追加系エフェクトはインベントリ右上のアイコンを非表示にできるよう分離。
    // 標準ビーコン6効果は確認しやすさを残すため表示ON、特殊効果は表示OFF。
    private static final boolean SHOW_CORE_EFFECT_ICONS = true;
    private static final boolean SHOW_EXTRA_EFFECT_ICONS = false;
    private static final boolean SHOW_NIGHT_VISION_ICON = false;

    private static final Map<UUID, PlayerBeaconState> PLAYER_STATES = new HashMap<>();
    private static final Map<UUID, Long> VISUALIZE_UNTIL = new HashMap<>();
    private static final Set<UUID> VISUALIZE_ALWAYS = new HashSet<>();
    private static final Set<UUID> VISUALIZE_DISABLED = new HashSet<>();
    private static final Map<String, Map<RegistryEntry<StatusEffect>, Integer>> VANILLA_BEACON_EFFECT_MEMORY = new HashMap<>();
    private static final Set<UUID> MOD_MANAGED_FLIGHT = new HashSet<>();
    private static final Map<UUID, HungerSnapshot> HUNGER_SNAPSHOTS = new HashMap<>();
    private static final Map<RegistryKey<World>, Set<BlockPos>> KNOWN_BEACONS = new HashMap<>();
    private static final Map<UUID, Long> LAST_WIDE_SCAN = new HashMap<>();
    // v48: ログイン直後はチャンク/ビーコンキャッシュがまだ安定しないため、数秒間だけ広域再探索を繰り返す。
    private static final Map<UUID, Long> LOGIN_FORCE_SCAN_UNTIL = new HashMap<>();
    private static final Map<UUID, Integer> LAST_REACH_BONUS = new HashMap<>();
    private static final Set<UUID> WAS_INSIDE_BEACON = new HashSet<>();
    private static final Map<UUID, Long> LAST_PLAYER_EFFECT_REFRESH = new HashMap<>();
    private static final Map<UUID, Set<RegistryEntry<StatusEffect>>> MOD_APPLIED_EFFECTS = new HashMap<>();
    // v2-dev.20: バニラビーコンが長時間残した標準効果を、範囲外で確実に掃除するための短期フラグ。
    private static final Map<UUID, Long> BEACON_EFFECT_CLEANUP_UNTIL = new HashMap<>();
    // v2-dev.14: ビーコンコア系は専用GUIで、各デフォルト効果のレベルを0〜2で切り替える。
    // level 0 = OFF, 1 = Lv I, 2 = Lv II
    private static final Map<String, CoreSettings> CORE_SETTINGS = new HashMap<>();
    // v2.1.0-dev6.8: ビーコンコアGUI設定をワールドに永続保存する。
    // これが無いと再起動後にメモリ上の初期値（全ON + Lv2）へ戻ってしまう。
    private static boolean CORE_SETTINGS_LOADED = false;
    private static final String CORE_SETTINGS_FILE = "maikura_beacon_expansion_core_settings.properties";
    // v51: 範囲外へ出た直後はvanillaビーコンらしく短時間だけ効果を残す。
    private static final Map<UUID, Long> LEAVE_EFFECT_GRACE_UNTIL = new HashMap<>();
    private static final Map<UUID, Long> LEAVE_FLIGHT_GRACE_UNTIL = new HashMap<>();
    private static final int EFFECT_LEAVE_GRACE_TICKS = 40;
    private static final int FLIGHT_LEAVE_GRACE_TICKS = 80;
    private static final int PLAYER_EFFECT_REFRESH_INTERVAL = 40;
    private static final int CORE_EXTRA_BASE_SLOT = 28;

    public static MinecraftServer CURRENT_SERVER;

    /**
     * v2-dev.33: 読み込み済み通常ビーコンをBlockEntity側からキャッシュする。
     * 毎tick広範囲の全ブロック探索をせず、既知ビーコンだけで128/256範囲再付与を安定させる。
     */
    public static void rememberBeaconAnchor(World world, BlockPos pos) {
        if (world == null || pos == null) return;
        KNOWN_BEACONS.computeIfAbsent(world.getRegistryKey(), k -> new HashSet<>()).add(pos.toImmutable());
    }

    private static final String ADV_LIGHTEN_UP = "adventure/lighten_up";
    private static final String ADV_SERIOUS_DEDICATION = "husbandry/obtain_netherite_hoe";
    private static final String ADV_WATER = "husbandry/axolotl_in_a_bucket";
    private static final String ADV_GREAT_VIEW = "adventure/fall_from_world_height";
    private static final String ADV_COVER_DEBRIS = "nether/netherite_armor";
    private static final String ADV_NEW_LOOK = "adventure/trim_with_any_armor_pattern";
    private static final String ADV_STYLISH_SMITHING = "adventure/trim_with_all_exclusive_armor_patterns";
    private static final String ADV_HOW_DID = "nether/all_effects";
    private static final String ADV_KILL_ALL_MOBS = "adventure/kill_all_mobs";
    private static final String ADV_KILL_DRAGON = "end/kill_dragon";
    private static final String ADV_HERO = "adventure/hero_of_the_village";
    private static final String ADV_ZOMBIE_DOCTOR = "story/cure_zombie_villager";
    private static final String ADV_STAR_TRADER = "adventure/trade_at_world_height";

    private static final String[] ADV_BUILD_REACH = new String[]{
            "nether/explore_nether",
            "adventure/adventuring_time",
            "nether/fast_travel",
            "nether/create_full_beacon",
            "end/find_end_city"
    };

    @Override
    public void onInitialize() {
        Registry.register(Registries.BLOCK, BEACON_CORE_ID, BEACON_CORE);
        Registry.register(Registries.ITEM, BEACON_CORE_ID, new BlockItem(BEACON_CORE, new Item.Settings().registryKey(BEACON_CORE_ITEM_KEY)));
        Registry.register(Registries.BLOCK, ENHANCED_BEACON_CORE_ID, ENHANCED_BEACON_CORE);
        Registry.register(Registries.ITEM, ENHANCED_BEACON_CORE_ID, new BlockItem(ENHANCED_BEACON_CORE, new Item.Settings().registryKey(ENHANCED_BEACON_CORE_ITEM_KEY)));
        Registry.register(Registries.BLOCK, GOLD_DECORATED_GLASS_ID, GOLD_DECORATED_GLASS);
        Registry.register(Registries.ITEM, GOLD_DECORATED_GLASS_ID, new BlockItem(GOLD_DECORATED_GLASS, new Item.Settings().registryKey(GOLD_DECORATED_GLASS_ITEM_KEY)));
        Registry.register(Registries.BLOCK, PURPLE_GOLD_DECORATED_GLASS_ID, PURPLE_GOLD_DECORATED_GLASS);
        Registry.register(Registries.ITEM, PURPLE_GOLD_DECORATED_GLASS_ID, new BlockItem(PURPLE_GOLD_DECORATED_GLASS, new Item.Settings().registryKey(PURPLE_GOLD_DECORATED_GLASS_ITEM_KEY)));

        // v2-dev.12: クリエイティブ一覧にも確実に表示する。
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> {
            entries.add(BEACON_CORE);
            entries.add(ENHANCED_BEACON_CORE);
            entries.add(GOLD_DECORATED_GLASS);
            entries.add(PURPLE_GOLD_DECORATED_GLASS);
        });

        ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> saveCoreSettings(server));
        UseBlockCallback.EVENT.register(BeaconExpansionMod::onUseBlock);
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> registerCommands(dispatcher));
    }


    private static ActionResult onUseBlock(PlayerEntity player, World world, Hand hand, net.minecraft.util.hit.BlockHitResult hitResult) {
        BlockPos pos = hitResult.getBlockPos();
        BlockState state = world.getBlockState(pos);
        if (!state.isOf(BEACON_CORE) && !state.isOf(ENHANCED_BEACON_CORE)) return ActionResult.PASS;
        if (world.isClient()) return ActionResult.SUCCESS;
        if (!(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.SUCCESS;

        serverPlayer.openHandledScreen(new CoreControlFactory(world, pos.toImmutable(), state.isOf(ENHANCED_BEACON_CORE)));
        return ActionResult.SUCCESS;
    }

    private static String coreKey(World world, BlockPos pos) {
        return world.getRegistryKey().getValue().toString() + ":" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static boolean isCoreBlock(ServerWorld world, BlockPos pos) {
        BlockState s = world.getBlockState(pos);
        return s.isOf(BEACON_CORE) || s.isOf(ENHANCED_BEACON_CORE);
    }

    private static CoreSettings getCoreSettings(World world, BlockPos pos) {
        return CORE_SETTINGS.computeIfAbsent(coreKey(world, pos), k -> CoreSettings.defaults());
    }

    private static int ampFromLevel(int level) {
        return level <= 0 ? -1 : Math.min(1, level - 1);
    }

    private static void addCoreEffect(ServerPlayerEntity player, RegistryEntry<StatusEffect> effect, int level) {
        int amp = ampFromLevel(level);
        if (amp >= 0) addShortCoreEffect(player, effect, amp);
    }

    private static void addShortCoreEffect(ServerPlayerEntity player, RegistryEntry<StatusEffect> effect, int amplifier) {
        StatusEffectInstance current = player.getStatusEffect(effect);
        if (current != null && current.getAmplifier() >= amplifier && current.getDuration() > 300) return;
        // v2.2.0-dev2: GUIでON/OFFするコア効果は、インベントリ右上のエフェクトアイコンで確認しやすいよう非ambientで付与する。
        player.addStatusEffect(new StatusEffectInstance(effect, 600, amplifier, false, false, SHOW_CORE_EFFECT_ICONS));
        MOD_APPLIED_EFFECTS.computeIfAbsent(player.getUuid(), k -> new HashSet<>()).add(effect);
    }

    private static RegistryEntry<StatusEffect> coreEffectByIndex(int index) {
        return switch (index) {
            case 0 -> StatusEffects.SPEED;
            case 1 -> StatusEffects.HASTE;
            case 2 -> StatusEffects.STRENGTH;
            case 3 -> StatusEffects.RESISTANCE;
            case 4 -> StatusEffects.JUMP_BOOST;
            case 5 -> StatusEffects.REGENERATION;
            default -> null;
        };
    }

    private static void clearCoreEffectNear(World world, BlockPos pos, int effectIndex) {
        if (!(world instanceof ServerWorld serverWorld)) return;
        RegistryEntry<StatusEffect> effect = coreEffectByIndex(effectIndex);
        if (effect == null) return;
        int range = world.getBlockState(pos).isOf(ENHANCED_BEACON_CORE) ? RANGE_ENHANCED_CORE : RANGE_CORE;
        for (ServerPlayerEntity p : serverWorld.getPlayers()) {
            if (insideSquareRange(p, pos, range)) {
                p.removeStatusEffect(effect);
                Set<RegistryEntry<StatusEffect>> set = MOD_APPLIED_EFFECTS.get(p.getUuid());
                if (set != null) set.remove(effect);
            }
        }
    }

    private static void clearExtraEffectNear(World world, BlockPos pos, int extraIndex) {
        if (!(world instanceof ServerWorld serverWorld)) return;
        int range = world.getBlockState(pos).isOf(ENHANCED_BEACON_CORE) ? RANGE_ENHANCED_CORE : RANGE_CORE;
        for (ServerPlayerEntity p : serverWorld.getPlayers()) {
            if (!insideSquareRange(p, pos, range)) continue;
            switch (extraIndex) {
                case 2 -> HUNGER_SNAPSHOTS.remove(p.getUuid());
                case 3 -> removeManagedEffect(p, StatusEffects.NIGHT_VISION);
                case 4 -> {
                    removeManagedEffect(p, StatusEffects.WATER_BREATHING);
                    removeManagedEffect(p, StatusEffects.DOLPHINS_GRACE);
                    removeManagedEffect(p, StatusEffects.CONDUIT_POWER);
                }
                case 6 -> removeManagedEffect(p, StatusEffects.FIRE_RESISTANCE);
                case 7 -> removeManagedEffect(p, StatusEffects.HERO_OF_THE_VILLAGE);
                case 8 -> removeManagedEffect(p, StatusEffects.REGENERATION);
                case 11 -> handleFlight(p, false);
                case 12 -> removeDynamicAttributes(p);
                default -> {}
            }
        }
    }

    private static void clearAllCoreEffectsNear(World world, BlockPos pos) {
        for (int i = 0; i < 6; i++) {
            clearCoreEffectNear(world, pos, i);
        }
        for (int i = 0; i < 13; i++) {
            clearExtraEffectNear(world, pos, i);
        }
    }

    private static void removeManagedEffect(ServerPlayerEntity player, RegistryEntry<StatusEffect> effect) {
        player.removeStatusEffect(effect);
        Set<RegistryEntry<StatusEffect>> set = MOD_APPLIED_EFFECTS.get(player.getUuid());
        if (set != null) set.remove(effect);
    }


    private static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("beaconrange")
                .then(literal("show").executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayer();
                    ServerWorld world = ctx.getSource().getWorld();
                    PlayerBeaconState state = findBestBeacon(player, world, true);
                    if (!state.active) {
                        player.sendMessage(Text.translatable("commands.beacon_expansion.none"), false);
                        return 0;
                    }
                    PLAYER_STATES.put(player.getUuid(), state);
                    VISUALIZE_UNTIL.put(player.getUuid(), world.getTime() + 200);
                    player.sendMessage(Text.translatable("commands.beacon_expansion.show"), false);
                    return 1;
                }))
                .then(literal("on").executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayer();
                    VISUALIZE_DISABLED.remove(player.getUuid());
                    VISUALIZE_ALWAYS.add(player.getUuid());
                    player.sendMessage(Text.literal("ビーコン範囲の常時表示をONにしました"), false);
                    return 1;
                }))
                .then(literal("off").executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayer();
                    VISUALIZE_ALWAYS.remove(player.getUuid());
                    VISUALIZE_DISABLED.add(player.getUuid());
                    VISUALIZE_UNTIL.remove(player.getUuid());
                    player.sendMessage(Text.literal("ビーコン範囲の常時表示をOFFにしました"), false);
                    return 1;
                }))
                .then(literal("hide").executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayer();
                    VISUALIZE_UNTIL.remove(player.getUuid());
                    VISUALIZE_ALWAYS.remove(player.getUuid());
                    VISUALIZE_DISABLED.add(player.getUuid());
                    return 1;
                })));

        // dev6.3: 追加ガラス2種をチート確認しやすくする開発用取得コマンド。
        // /give で使う正式IDは beacon_expansion:gold_decorated_glass / beacon_expansion:purple_gold_decorated_glass
        dispatcher.register(literal("maikurabeacon")
                .then(literal("give_gold_glass").executes(ctx -> giveDebugStack(ctx.getSource().getPlayer(), new ItemStack(GOLD_DECORATED_GLASS, 64))))
                .then(literal("give_purple_gold_glass").executes(ctx -> giveDebugStack(ctx.getSource().getPlayer(), new ItemStack(PURPLE_GOLD_DECORATED_GLASS, 64)))));
    }

    private static int giveDebugStack(ServerPlayerEntity player, ItemStack stack) {
        if (!player.getInventory().insertStack(stack)) {
            player.dropItem(stack, false);
        }
        player.sendMessage(Text.literal(stack.getName().getString() + " を付与しました"), false);
        return 1;
    }

    private void onServerTick(MinecraftServer server) {
        CURRENT_SERVER = server;
        ensureCoreSettingsLoaded(server);
        long tick = server.getOverworld().getTime();

        for (ServerWorld world : server.getWorlds()) {
            for (ServerPlayerEntity player : world.getPlayers()) {
                UUID uuid = player.getUuid();
                PlayerBeaconState state = PLAYER_STATES.get(uuid);
                boolean freshLoginOrDimensionChange = (state == null || !state.worldKey.equals(world.getRegistryKey()));
                // v47: ログイン直後・ディメンション移動直後に「既に範囲内扱い」のまま残ると、
                // 範囲へ入り直すまで追加効果が付かないことがあったため、初回は必ず未入域扱いに戻す。
                if (freshLoginOrDimensionChange) {
                    // v51: ディメンション移動時は前ディメンションのビーコン効果を持ち込まない。
                    clearModAppliedEffects(player);
                    handleFlight(player, false);
                    HUNGER_SNAPSHOTS.remove(uuid);
                    WAS_INSIDE_BEACON.remove(uuid);
                    LAST_PLAYER_EFFECT_REFRESH.remove(uuid);
                    MOD_APPLIED_EFFECTS.remove(uuid);
                    BEACON_EFFECT_CLEANUP_UNTIL.remove(uuid);
                    LEAVE_EFFECT_GRACE_UNTIL.remove(uuid);
                    LEAVE_FLIGHT_GRACE_UNTIL.remove(uuid);
                    // v48/v51: ログイン直後/ディメンション移動直後は数秒間だけ広域探索して初回付与を安定化。
                    LOGIN_FORCE_SCAN_UNTIL.put(uuid, tick + 120);
                    LAST_WIDE_SCAN.put(uuid, -999999L);
                }
                boolean needWideScan = false;
                long lastWide = LAST_WIDE_SCAN.getOrDefault(uuid, -999999L);
                long forceScanUntil = LOGIN_FORCE_SCAN_UNTIL.getOrDefault(uuid, -1L);
                boolean loginForceScan = tick <= forceScanUntil;
                if (loginForceScan && tick - lastWide >= 20) {
                    needWideScan = true; // v48: ログイン直後は1秒ごとに広域探索して初回付与を安定化
                } else if (freshLoginOrDimensionChange) {
                    needWideScan = true; // ログイン直後・ディメンション移動直後は広めに探索
                } else if (!state.active && tick - lastWide >= WIDE_SCAN_INTERVAL) {
                    needWideScan = true; // 遠めのビーコンを見落とした場合の低頻度再探索
                }
                if (needWideScan) LAST_WIDE_SCAN.put(uuid, tick);
                if (!loginForceScan && forceScanUntil >= 0) LOGIN_FORCE_SCAN_UNTIL.remove(uuid);

                if (state == null || loginForceScan || tick - state.lastScanTick >= SCAN_INTERVAL || !state.worldKey.equals(world.getRegistryKey())) {
                    state = findBestBeacon(player, world, needWideScan || loginForceScan);
                    state.lastScanTick = tick;
                    PLAYER_STATES.put(uuid, state);
                }
                if (state.active && !isStillInsideTrackedBeacon(player, world, state)) {
                    state = PlayerBeaconState.inactive();
                    state.lastScanTick = tick;
                    PLAYER_STATES.put(uuid, state);
                }

                boolean inside = state.active;
                boolean wasInsideBeacon = WAS_INSIDE_BEACON.contains(uuid);
                if (inside) {
                    LEAVE_EFFECT_GRACE_UNTIL.remove(uuid);
                    LEAVE_FLIGHT_GRACE_UNTIL.remove(uuid);
                } else if (wasInsideBeacon && !LEAVE_EFFECT_GRACE_UNTIL.containsKey(uuid)) {
                    // v51: 範囲外へ出ても即解除せず、通常効果は10秒・飛行は4秒程度残す。
                    LEAVE_EFFECT_GRACE_UNTIL.put(uuid, tick + EFFECT_LEAVE_GRACE_TICKS);
                    LEAVE_FLIGHT_GRACE_UNTIL.put(uuid, tick + FLIGHT_LEAVE_GRACE_TICKS);
                }
                boolean effectGrace = !inside && LEAVE_EFFECT_GRACE_UNTIL.getOrDefault(uuid, -1L) >= tick;
                boolean flightGrace = !inside && LEAVE_FLIGHT_GRACE_UNTIL.getOrDefault(uuid, -1L) >= tick;
                boolean effectsActive = inside || effectGrace;
                if (inside) {
                    // v2-dev.21: 通常ビーコン効果は強制削除せず、15秒付与の自然消滅に任せる。
                    // BEACON_EFFECT_CLEANUP_UNTIL は互換用に残すがここでは更新しない。
                }

                boolean coreBeaconForToggles = state.active && isCoreBlock(world, state.beaconPos);
                CoreSettings activeCoreSettings = coreBeaconForToggles ? getCoreSettings(world, state.beaconPos) : null;
                boolean hasSeriousDedication = hasAdvancement(player, ADV_SERIOUS_DEDICATION);
                boolean hungerEnabled = activeCoreSettings == null || activeCoreSettings.hungerMaintenance;
                updateHungerMaintenance(player, effectsActive && hasSeriousDedication && hungerEnabled);
                // v23/v51: 落下ダメージ無効はビーコン範囲内標準機能。範囲外では短時間だけ猶予。
                boolean fallGuardEnabled = activeCoreSettings == null || activeCoreSettings.fallGuard;
                if ((inside || flightGrace) && fallGuardEnabled) player.fallDistance = 0.0F;

                long lastEffectRefresh = LAST_PLAYER_EFFECT_REFRESH.getOrDefault(uuid, -999999L);
                if (inside) {
                    boolean coreBeacon = isCoreBlock(world, state.beaconPos);
                    // v2-dev.15: コアGUIのLv変更がすぐ反映されるよう、コア系は短周期で再付与する。
                    // v2-dev.19: 通常ビーコンも短時間効果を短周期で維持し、範囲外では素早く消えるよう統一。
                    if (!wasInsideBeacon || needsRespawnReapply(player) || tick - lastEffectRefresh >= (coreBeacon ? 40 : PLAYER_EFFECT_REFRESH_INTERVAL)) {
                        applyPlayerEffects(player, world, state, tick);
                        LAST_PLAYER_EFFECT_REFRESH.put(uuid, tick);
                    }
                    WAS_INSIDE_BEACON.add(uuid);
                } else if (wasInsideBeacon && !effectGrace) {
                    // v2-dev.21: 範囲外では効果を強制削除しない。
                    // 通常ビーコン/追加機能とも短時間付与にして、範囲外では自然消滅させる。
                    MOD_APPLIED_EFFECTS.remove(uuid);
                    WAS_INSIDE_BEACON.remove(uuid);
                    LAST_PLAYER_EFFECT_REFRESH.remove(uuid);
                    BEACON_EFFECT_CLEANUP_UNTIL.remove(uuid);
                    LEAVE_EFFECT_GRACE_UNTIL.remove(uuid);
                    LEAVE_FLIGHT_GRACE_UNTIL.remove(uuid);
                }

                // v2-dev.35: 通常ビーコン/コア効果は短時間付与＋範囲内更新に統一。
                // 範囲外での強制削除は判定ズレや余計な負荷の原因になるため廃止し、自然消滅に任せる。

                boolean flightEnabled = activeCoreSettings == null || activeCoreSettings.flight;
                handleFlight(player, (inside || flightGrace) && hasAdvancement(player, ADV_KILL_DRAGON) && flightEnabled);
                // v14切り分け: 建築距離は復活。ただし1秒更新・ビーコンキャッシュ方式は維持。
                if (tick % 20 == 0) {
                    applyDynamicAttributesIfChanged(player, state, world);
                }

                Long until = VISUALIZE_UNTIL.get(uuid);
                boolean alwaysVisualize = !VISUALIZE_DISABLED.contains(uuid) || VISUALIZE_ALWAYS.contains(uuid);
                if ((alwaysVisualize || until != null) && state.active) {
                    // v15: 境界線を見やすくするため、常時表示でも2秒ごとに線状粒子を再描画。
                    if (tick % 20 == 0) {
                        drawRange(player, world, state);
                    }
                    if (!alwaysVisualize && until != null && tick > until) {
                        VISUALIZE_UNTIL.remove(uuid);
                    }
                }
            }
            // v24: ログイン直後だけファントムがスポーンキャンセル前に出ることがあるため、
            // ファントムだけ低頻度で保険浄化する。通常Mobはスポーンキャンセル方式を維持。
            if (tick % 20 == 0) {
                suppressPhantomsNearWards(world);
                processInfrastructureZones(world);
            }
        }
    }

    private static boolean needsRespawnReapply(ServerPlayerEntity player) {
        // v51: 範囲内死亡→範囲内リスポーンでは「範囲外→範囲内」の侵入判定が起きない。
        // 死亡でステータス効果だけ消えている場合に、必要な効果を再付与する。
        if (hasAdvancement(player, ADV_LIGHTEN_UP) && player.getStatusEffect(StatusEffects.NIGHT_VISION) == null) return true;
        if (hasAdvancement(player, ADV_WATER) && player.getStatusEffect(StatusEffects.WATER_BREATHING) == null) return true;
        if (hasAdvancement(player, ADV_COVER_DEBRIS) && player.getStatusEffect(StatusEffects.FIRE_RESISTANCE) == null) return true;
        if ((hasAdvancement(player, ADV_HERO) || hasAdvancement(player, ADV_ZOMBIE_DOCTOR)) && player.getStatusEffect(StatusEffects.HERO_OF_THE_VILLAGE) == null) return true;
        if (hasAdvancement(player, ADV_KILL_ALL_MOBS) && player.getStatusEffect(StatusEffects.REGENERATION) == null) return true;
        return false;
    }

    private static void applyPlayerEffects(ServerPlayerEntity player, ServerWorld world, PlayerBeaconState state, long tick) {
        if (!state.active) return;

        applyExpandedVanillaBeaconEffects(player, world, state);
        // v2-dev.18: ビーコンコア範囲と通常ビーコン範囲が重なっても、通常ビーコン側の選択効果は別途維持する。
        applyNearbyVanillaBeaconEffects(player, world);

        // v2-dev.14: ビーコンコア系は専用GUI設定に従って、デフォルトビーコン効果を0〜2で付与する。
        if (isCoreBlock(world, state.beaconPos)) {
            CoreSettings settings = getCoreSettings(world, state.beaconPos);
            addCoreEffect(player, StatusEffects.SPEED, settings.speed);
            addCoreEffect(player, StatusEffects.HASTE, settings.haste);
            addCoreEffect(player, StatusEffects.STRENGTH, settings.strength);
            addCoreEffect(player, StatusEffects.RESISTANCE, settings.resistance);
            addCoreEffect(player, StatusEffects.JUMP_BOOST, settings.jump);
            addCoreEffect(player, StatusEffects.REGENERATION, settings.regeneration);
        }

        CoreSettings coreSettings = isCoreBlock(world, state.beaconPos) ? getCoreSettings(world, state.beaconPos) : null;
        boolean allowNightVision = coreSettings == null || coreSettings.nightVision;
        boolean allowWaterSupport = coreSettings == null || coreSettings.waterSupport;
        boolean allowFireResistance = coreSettings == null || coreSettings.fireResistance;
        boolean allowVillagerBlessing = coreSettings == null || coreSettings.villagerBlessing;
        boolean allowRegenBonus = coreSettings == null || coreSettings.regenBonus;
        boolean allowDurabilityCare = coreSettings == null || coreSettings.durabilityCare;
        boolean allowNegativeShorten = coreSettings == null || coreSettings.negativeShorten;

        // v2.3.1-dev1: 暗視も進捗ロック対象へ戻す。
        // コアGUIでONになっていても「明るくなーれ」未達成なら付与しない。
        if (allowNightVision && hasAdvancement(player, ADV_LIGHTEN_UP)) addNightVisionEffect(player);
        if (allowWaterSupport && hasAdvancement(player, ADV_WATER)) {
            addEffect(player, StatusEffects.WATER_BREATHING, 260, 0);
            addEffect(player, StatusEffects.DOLPHINS_GRACE, 260, 0);
            addEffect(player, StatusEffects.CONDUIT_POWER, 260, 0);
        }
        // 落下無効はステータス効果ではなく、範囲内かつ進捗達成時だけ fallDistance をリセットする。
        if (allowFireResistance && hasAdvancement(player, ADV_COVER_DEBRIS)) addEffect(player, StatusEffects.FIRE_RESISTANCE, 260, 0);
        if (allowVillagerBlessing) {
            int villagerLevel = getVillagerBlessingLevel(player);
            if (villagerLevel >= 2) addEffect(player, StatusEffects.HERO_OF_THE_VILLAGE, 260, 3); // 体感約40%
            else if (villagerLevel >= 1) addEffect(player, StatusEffects.HERO_OF_THE_VILLAGE, 260, 0); // Vanilla仕様上の最小段階
            // v2.3.1-dev12: Lv3の補充高速化をdev10とdev11の中間へ調整する。
            if (villagerLevel >= 3 && tick % 240 == 0) fastRestockNearbyVillagers(player, world);
        }
        // v18: 再生能力は全モンスター討伐達成時だけ。vanillaビーコン側の再生拡張とは分離。
        if (allowRegenBonus && hasAdvancement(player, ADV_KILL_ALL_MOBS)) addEffect(player, StatusEffects.REGENERATION, 120, 0);

        if (allowDurabilityCare && (hasAdvancement(player, ADV_NEW_LOOK) || hasAdvancement(player, ADV_STYLISH_SMITHING)) && tick % 100 == 0) {
            repairEquipment(player, hasAdvancement(player, ADV_STYLISH_SMITHING) ? 3 : 1);
        }
        if (allowNegativeShorten && hasAdvancement(player, ADV_HOW_DID) && tick % 40 == 0) {
            shortenNegativeEffects(player);
        }
    }


    private static void applyExpandedVanillaBeaconEffects(ServerPlayerEntity player, ServerWorld world, PlayerBeaconState state) {
        if (state == null || !state.active) return;
        if (!world.getBlockState(state.beaconPos).isOf(Blocks.BEACON)) return;
        applyVanillaBeaconEffectsAt(player, world, state.beaconPos);
    }

    private static void applyNearbyVanillaBeaconEffects(ServerPlayerEntity player, ServerWorld world) {
        BlockPos p = player.getBlockPos();
        Set<BlockPos> checked = new HashSet<>();
        Set<BlockPos> known = KNOWN_BEACONS.computeIfAbsent(world.getRegistryKey(), k -> new HashSet<>());
        for (BlockPos pos : known) {
            if (!checked.add(pos)) continue;
            if (!world.getBlockState(pos).isOf(Blocks.BEACON)) continue;
            BeaconInfo info = analyzeBeacon(world, pos);
            if (info.active && insideSquareRange(player, pos, info.range)) {
                applyVanillaBeaconEffectsAt(player, world, pos);
            }
        }
        int scan = LOCAL_BEACON_SCAN_RADIUS;
        for (BlockPos pos : BlockPos.iterate(p.add(-scan, -scan, -scan), p.add(scan, scan, scan))) {
            BlockPos immutable = pos.toImmutable();
            if (!checked.add(immutable)) continue;
            if (!world.getBlockState(immutable).isOf(Blocks.BEACON)) continue;
            known.add(immutable);
            BeaconInfo info = analyzeBeacon(world, immutable);
            if (info.active && insideSquareRange(player, immutable, info.range)) {
                applyVanillaBeaconEffectsAt(player, world, immutable);
            }
        }
    }

    private static void applyVanillaBeaconEffectsAt(ServerPlayerEntity player, ServerWorld world, BlockPos beaconPos) {
        BlockEntity be = world.getBlockEntity(beaconPos);
        if (!(be instanceof BeaconBlockEntity)) return;

        // v2-dev.28: 通常ビーコンは「GUIで選択されている効果だけ」を補助付与する。
        // dev.25系の記憶再付与は、過去に付いた効果まで再付与してしまう原因になったため廃止。
        List<RegistryEntry<StatusEffect>> effects = readBeaconSelectedEffects(be);
        if (effects.isEmpty()) return;
        if (effects.size() > 2) return;
        for (RegistryEntry<StatusEffect> effect : effects) {
            if (effect == null || !isVanillaBeaconEffect(effect)) continue;
            int amp = shouldBoostBeaconEffect(effects, effect) ? 1 : 0;
            addBeaconEffect(player, effect, 300, amp);
        }
    }

    private static String beaconMemoryKey(ServerWorld world, BlockPos pos) {
        return world.getRegistryKey().getValue().toString() + ":" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static void rememberCurrentVanillaBeaconEffects(ServerPlayerEntity player, String key) {
        Map<RegistryEntry<StatusEffect>, Integer> map = VANILLA_BEACON_EFFECT_MEMORY.computeIfAbsent(key, k -> new HashMap<>());
        rememberEffect(player, map, StatusEffects.SPEED);
        rememberEffect(player, map, StatusEffects.HASTE);
        rememberEffect(player, map, StatusEffects.RESISTANCE);
        rememberEffect(player, map, StatusEffects.JUMP_BOOST);
        rememberEffect(player, map, StatusEffects.STRENGTH);
        // v18: 再生は全モンスター討伐の進捗効果に限定するため、vanilla拡張記憶から除外。
    }

    private static void rememberEffect(ServerPlayerEntity player, Map<RegistryEntry<StatusEffect>, Integer> map, RegistryEntry<StatusEffect> effect) {
        StatusEffectInstance instance = player.getStatusEffect(effect);
        if (instance != null) map.put(effect, Math.max(map.getOrDefault(effect, 0), instance.getAmplifier()));
    }

    @SuppressWarnings("unchecked")
    private static List<RegistryEntry<StatusEffect>> readBeaconSelectedEffects(BlockEntity beaconEntity) {
        List<RegistryEntry<StatusEffect>> result = new ArrayList<>();
        try {
            if (beaconEntity instanceof BeaconBlockEntityAccessor accessor) {
                RegistryEntry<StatusEffect> primary = accessor.maikura$getPrimaryEffect();
                RegistryEntry<StatusEffect> secondary = accessor.maikura$getSecondaryEffect();
                if (primary != null && isVanillaBeaconEffect(primary) && !result.contains(primary)) result.add(primary);
                if (secondary != null && isVanillaBeaconEffect(secondary) && !result.contains(secondary)) result.add(secondary);
                if (!result.isEmpty()) return result;
            }

            Class<?> cls = beaconEntity.getClass();
            while (cls != null) {
                for (Field field : cls.getDeclaredFields()) {
                    String fieldName = field.getName().toLowerCase(Locale.ROOT);
                    // Yarn名/一部環境名のprimary/secondaryだけを優先して読む。
                    if (!fieldName.contains("primary") && !fieldName.contains("secondary")) continue;
                    if (!field.getType().getName().equals("net.minecraft.registry.entry.RegistryEntry")) continue;
                    field.setAccessible(true);
                    Object value = field.get(beaconEntity);
                    if (!(value instanceof RegistryEntry<?> entry)) continue;
                    Object raw = entry.value();
                    if (raw instanceof StatusEffect) {
                        RegistryEntry<StatusEffect> effect = (RegistryEntry<StatusEffect>) entry;
                        if (isVanillaBeaconEffect(effect) && !result.contains(effect)) result.add(effect);
                    }
                }
                cls = cls.getSuperclass();
            }

            // v2-dev.28: フィールド名不明時の総当たり取得は、未選択効果まで拾う原因になるため廃止。
            // primary/secondary名で読めない環境では補助付与せず、バニラ本体の付与だけに任せる。
        } catch (Throwable ignored) {
            // Yarn名や内部構造が変わっても追加効果側は止めない。
        }
        return result;
    }


    private static boolean isVanillaBeaconEffect(RegistryEntry<StatusEffect> effect) {
        return Objects.equals(effect, StatusEffects.SPEED)
                || Objects.equals(effect, StatusEffects.HASTE)
                || Objects.equals(effect, StatusEffects.RESISTANCE)
                || Objects.equals(effect, StatusEffects.JUMP_BOOST)
                || Objects.equals(effect, StatusEffects.STRENGTH);
    }

    private static boolean shouldBoostBeaconEffect(List<RegistryEntry<StatusEffect>> effects, RegistryEntry<StatusEffect> effect) {
        // vanillaの二次効果が同じ効果ならLv2相当。反射でprimary/secondary名を固定しない安全寄り判定。
        int count = 0;
        for (RegistryEntry<StatusEffect> e : effects) if (Objects.equals(e, effect)) count++;
        return count >= 2;
    }

    private static void addBeaconEffect(ServerPlayerEntity player, net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.effect.StatusEffect> effect, int duration, int amplifier) {
        StatusEffectInstance current = player.getStatusEffect(effect);
        // v2-dev.21: 通常ビーコンは強制削除せず、15秒付与＋短周期更新で自然消滅させる。
        // 範囲内では残り5秒以下で更新する。
        int safeDuration = Math.max(duration, 300);
        int refreshThreshold = 100;
        if (current != null && current.getAmplifier() >= amplifier && current.getDuration() > refreshThreshold) {
            MOD_APPLIED_EFFECTS.computeIfAbsent(player.getUuid(), k -> new HashSet<>()).add(effect);
            return;
        }
        player.addStatusEffect(new StatusEffectInstance(effect, safeDuration, amplifier, true, false, true));
        MOD_APPLIED_EFFECTS.computeIfAbsent(player.getUuid(), k -> new HashSet<>()).add(effect);
    }


    private static void addNightVisionEffect(ServerPlayerEntity player) {
        StatusEffectInstance current = player.getStatusEffect(StatusEffects.NIGHT_VISION);
        // v2.2.0-dev4: 暗視は30秒付与し、残り15秒以上なら再付与しない。
        // 更新頻度を下げて、エフェクトアイコンの点滅・並び替えをさらに抑える。
        int safeDuration = 600;      // 30秒
        int refreshThreshold = 300;  // 15秒
        if (current != null && current.getAmplifier() >= 0 && current.getDuration() > refreshThreshold) {
            MOD_APPLIED_EFFECTS.computeIfAbsent(player.getUuid(), k -> new HashSet<>()).add(StatusEffects.NIGHT_VISION);
            return;
        }
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, safeDuration, 0, false, false, SHOW_NIGHT_VISION_ICON));
        MOD_APPLIED_EFFECTS.computeIfAbsent(player.getUuid(), k -> new HashSet<>()).add(StatusEffects.NIGHT_VISION);
    }

    private static void addEffect(ServerPlayerEntity player, net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.effect.StatusEffect> effect, int duration, int amplifier) {
        StatusEffectInstance current = player.getStatusEffect(effect);
        // v2-dev.19: 追加機能側も指定durationを尊重する。
        int safeDuration = Math.max(duration, 600);
        int refreshThreshold = Math.max(300, safeDuration / 2);
        if (current != null && current.getAmplifier() >= amplifier && current.getDuration() > refreshThreshold) {
            MOD_APPLIED_EFFECTS.computeIfAbsent(player.getUuid(), k -> new HashSet<>()).add(effect);
            return;
        }
        // v2.2.0-dev3: 追加機能側は長めの効果時間＋早め更新にして、暗視などの残り時間点滅とアイコン並び替えを抑える。
        player.addStatusEffect(new StatusEffectInstance(effect, safeDuration, amplifier, false, false, SHOW_EXTRA_EFFECT_ICONS));
        MOD_APPLIED_EFFECTS.computeIfAbsent(player.getUuid(), k -> new HashSet<>()).add(effect);
    }


    private static void cleanupInactiveBeaconEffects(ServerPlayerEntity player, ServerWorld world) {
        Set<RegistryEntry<StatusEffect>> allowed = collectAllowedBeaconEffects(player, world);

        // v2-dev.34: 範囲内の通常ビーコンで選択効果が読めない瞬間は、
        // dev.28のように有効効果まで消してしまうため掃除を見送る。
        // 範囲外ではallowedが空になるので通常通り掃除する。
        PlayerBeaconState state = PLAYER_STATES.get(player.getUuid());
        if (allowed.isEmpty() && state != null && state.active && world.getRegistryKey().equals(state.worldKey)
                && world.getBlockState(state.beaconPos).isOf(Blocks.BEACON)) {
            return;
        }

        cleanupOneBeaconEffect(player, StatusEffects.SPEED, allowed);
        cleanupOneBeaconEffect(player, StatusEffects.HASTE, allowed);
        cleanupOneBeaconEffect(player, StatusEffects.RESISTANCE, allowed);
        cleanupOneBeaconEffect(player, StatusEffects.JUMP_BOOST, allowed);
        cleanupOneBeaconEffect(player, StatusEffects.STRENGTH, allowed);
        // v2-dev.31: 再生能力は他MOD/進捗ボーナス由来の可能性が高いため掃除対象から外す。
    }

    private static void cleanupOneBeaconEffect(ServerPlayerEntity player, RegistryEntry<StatusEffect> effect, Set<RegistryEntry<StatusEffect>> allowed) {
        if (allowed.contains(effect)) return;
        if (player.getStatusEffect(effect) != null) {
            player.removeStatusEffect(effect);
        }
        Set<RegistryEntry<StatusEffect>> managed = MOD_APPLIED_EFFECTS.get(player.getUuid());
        if (managed != null) managed.remove(effect);
    }

    private static Set<RegistryEntry<StatusEffect>> collectAllowedBeaconEffects(ServerPlayerEntity player, ServerWorld world) {
        Set<RegistryEntry<StatusEffect>> allowed = new HashSet<>();
        Set<BlockPos> checked = new HashSet<>();
        Set<BlockPos> known = KNOWN_BEACONS.computeIfAbsent(world.getRegistryKey(), k -> new HashSet<>());

        for (BlockPos pos : known) {
            collectAllowedBeaconEffectsAt(player, world, pos, allowed, checked);
        }

        BlockPos p = player.getBlockPos();
        int scan = LOCAL_BEACON_SCAN_RADIUS;
        for (BlockPos pos : BlockPos.iterate(p.add(-scan, -scan, -scan), p.add(scan, scan, scan))) {
            collectAllowedBeaconEffectsAt(player, world, pos.toImmutable(), allowed, checked);
        }
        return allowed;
    }

    private static void collectAllowedBeaconEffectsAt(ServerPlayerEntity player, ServerWorld world, BlockPos pos, Set<RegistryEntry<StatusEffect>> allowed, Set<BlockPos> checked) {
        if (!checked.add(pos)) return;
        if (!isBeaconAnchorBlock(world.getBlockState(pos))) return;
        BeaconInfo info = analyzeBeacon(world, pos);
        if (!info.active || !insideSquareRange(player, pos, info.range)) return;

        if (world.getBlockState(pos).isOf(Blocks.BEACON)) {
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof BeaconBlockEntity) {
                allowed.addAll(readBeaconSelectedEffects(be));
            }
            // v1追加機能の自然回復強化は通常ビーコン範囲内でも有効。これを掃除対象から除外する。
            if (hasAdvancement(player, ADV_KILL_ALL_MOBS)) {
                allowed.add(StatusEffects.REGENERATION);
            }
            return;
        }

        if (isCoreBlock(world, pos)) {
            CoreSettings settings = getCoreSettings(world, pos);
            if (settings.speed > 0) allowed.add(StatusEffects.SPEED);
            if (settings.haste > 0) allowed.add(StatusEffects.HASTE);
            if (settings.strength > 0) allowed.add(StatusEffects.STRENGTH);
            if (settings.resistance > 0) allowed.add(StatusEffects.RESISTANCE);
            if (settings.jump > 0) allowed.add(StatusEffects.JUMP_BOOST);
            if (settings.regeneration > 0 || (settings.regenBonus && hasAdvancement(player, ADV_KILL_ALL_MOBS))) {
                allowed.add(StatusEffects.REGENERATION);
            }
        }
    }


    private static void clearLingeringVanillaBeaconEffects(ServerPlayerEntity player) {
        removeManagedEffect(player, StatusEffects.SPEED);
        removeManagedEffect(player, StatusEffects.HASTE);
        removeManagedEffect(player, StatusEffects.RESISTANCE);
        removeManagedEffect(player, StatusEffects.JUMP_BOOST);
        removeManagedEffect(player, StatusEffects.STRENGTH);
        // v2-dev.31: 再生能力は掃除しない。
    }

    private static void clearModAppliedEffects(ServerPlayerEntity player) {
        Set<RegistryEntry<StatusEffect>> effects = MOD_APPLIED_EFFECTS.remove(player.getUuid());
        if (effects == null) return;
        for (RegistryEntry<StatusEffect> effect : effects) {
            player.removeStatusEffect(effect);
        }
    }

    private static void updateHungerMaintenance(ServerPlayerEntity player, boolean active) {
        UUID uuid = player.getUuid();
        if (!active) {
            HUNGER_SNAPSHOTS.remove(uuid);
            return;
        }
        HungerSnapshot snap = HUNGER_SNAPSHOTS.computeIfAbsent(uuid, k -> new HungerSnapshot(player));
        player.getHungerManager().setFoodLevel(Math.max(player.getHungerManager().getFoodLevel(), snap.foodLevel));
        player.getHungerManager().setSaturationLevel(Math.max(player.getHungerManager().getSaturationLevel(), snap.saturation));
        snap.foodLevel = player.getHungerManager().getFoodLevel();
        snap.saturation = player.getHungerManager().getSaturationLevel();
    }

    private static void handleFlight(ServerPlayerEntity player, boolean active) {
        UUID uuid = player.getUuid();
        var abilities = player.getAbilities();
        if (active) {
            if (!abilities.allowFlying) {
                abilities.allowFlying = true;
                player.sendAbilitiesUpdate();
            }
            MOD_MANAGED_FLIGHT.add(uuid);
        } else if (MOD_MANAGED_FLIGHT.remove(uuid)) {
            if (!player.isCreative() && !player.isSpectator()) {
                abilities.allowFlying = false;
                abilities.flying = false;
                player.sendAbilitiesUpdate();
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 80, 0, true, false, true));
            }
        }
    }


    private static void removeDynamicAttributes(ServerPlayerEntity player) {
        removeAttr(player, EntityAttributes.BLOCK_INTERACTION_RANGE, "maikura_beacon_build_reach");
        removeAttr(player, EntityAttributes.ENTITY_INTERACTION_RANGE, "maikura_beacon_entity_reach");
    }

    private static void removeAttr(ServerPlayerEntity player, net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> attr, String idPath) {
        EntityAttributeInstance inst = player.getAttributeInstance(attr);
        if (inst == null) return;
        Identifier id = Identifier.of(MOD_ID, idPath);
        if (inst.getModifier(id) != null) inst.removeModifier(id);
    }

    private static void applyDynamicAttributes(ServerPlayerEntity player, PlayerBeaconState state, ServerWorld world) {
        int bonus = getEffectiveBuildReachBonus(player, state, world);
        applyAttr(player, EntityAttributes.BLOCK_INTERACTION_RANGE, "maikura_beacon_build_reach", bonus);
        applyAttr(player, EntityAttributes.ENTITY_INTERACTION_RANGE, "maikura_beacon_entity_reach", bonus);
    }

    private static void applyDynamicAttributesIfChanged(ServerPlayerEntity player, PlayerBeaconState state, ServerWorld world) {
        int bonus = getEffectiveBuildReachBonus(player, state, world);
        UUID uuid = player.getUuid();
        Integer oldBonus = LAST_REACH_BONUS.get(uuid);
        if (oldBonus != null && oldBonus == bonus) return;
        LAST_REACH_BONUS.put(uuid, bonus);
        applyDynamicAttributes(player, state, world);
    }

    private static void applyAttr(ServerPlayerEntity player, net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> attr, String idPath, double value) {
        EntityAttributeInstance inst = player.getAttributeInstance(attr);
        if (inst == null) return;
        Identifier id = Identifier.of(MOD_ID, idPath);
        if (inst.getModifier(id) != null) inst.removeModifier(id);
        if (value > 0) inst.addTemporaryModifier(new EntityAttributeModifier(id, value, EntityAttributeModifier.Operation.ADD_VALUE));
    }

    private static int getEffectiveBuildReachBonus(ServerPlayerEntity player, PlayerBeaconState state, ServerWorld world) {
        if (state == null || !state.active) return 0;
        int unlocked = getBuildReachUnlockedCount(player);
        if (!isCoreBlock(world, state.beaconPos)) return unlocked;
        CoreSettings settings = getCoreSettings(world, state.beaconPos);
        return Math.max(0, Math.min(unlocked, settings.buildReachLevel));
    }

    private static int getBuildReachUnlockedCount(ServerPlayerEntity player) {
        int count = 0;
        for (String adv : ADV_BUILD_REACH) if (hasAdvancement(player, adv)) count++;
        return Math.min(5, count);
    }

    // v2.3.1-dev12-r1: 村人支援Lv3の補充高速化間隔を中間調整。
    // 1/3 = 割引小、2/3 = 割引大、3/3 = 割引大 + 周辺村人の取引補充高速化。
    private static int getVillagerBlessingLevel(ServerPlayerEntity player) {
        int count = 0;
        if (hasAdvancement(player, ADV_HERO)) count++;
        if (hasAdvancement(player, ADV_ZOMBIE_DOCTOR)) count++;
        if (hasAdvancement(player, ADV_STAR_TRADER)) count++;
        return Math.min(3, count);
    }


    private static void fastRestockNearbyVillagers(ServerPlayerEntity player, ServerWorld world) {
        Box box = new Box(player.getBlockPos()).expand(48.0);
        List<VillagerEntity> villagers = world.getEntitiesByClass(VillagerEntity.class, box, villager -> villager.isAlive() && !villager.isBaby());
        for (VillagerEntity villager : villagers) {
            boolean changed = false;
            for (TradeOffer offer : villager.getOffers()) {
                if (offer.getUses() > 0) {
                    offer.resetUses();
                    changed = true;
                }
            }
            // resetUses()だけで補充済み状態へ戻す。経験値やレベルは触らない。
        }
    }

    private static void repairEquipment(ServerPlayerEntity player, int amount) {
        repairStack(player.getMainHandStack(), amount);
        repairStack(player.getOffHandStack(), amount);
    }

    private static void repairStack(ItemStack stack, int amount) {
        if (!stack.isEmpty() && stack.isDamaged()) stack.setDamage(Math.max(0, stack.getDamage() - amount));
    }

    private static void shortenNegativeEffects(ServerPlayerEntity player) {
        List<StatusEffectInstance> list = new ArrayList<>(player.getStatusEffects());
        for (StatusEffectInstance effect : list) {
            if (isNegative(effect) && effect.getDuration() > 40) {
                player.removeStatusEffect(effect.getEffectType());
                player.addStatusEffect(new StatusEffectInstance(effect.getEffectType(), Math.max(20, effect.getDuration() * 3 / 4), effect.getAmplifier(), effect.isAmbient(), effect.shouldShowParticles(), effect.shouldShowIcon()));
            }
        }
    }

    private static boolean isNegative(StatusEffectInstance effect) {
        return !effect.getEffectType().value().isBeneficial();
    }

    private static PlayerBeaconState findBestBeacon(ServerPlayerEntity player, ServerWorld world, boolean wideScan) {
        BlockPos p = player.getBlockPos();
        PlayerBeaconState best = PlayerBeaconState.inactive();
        Set<BlockPos> known = KNOWN_BEACONS.computeIfAbsent(world.getRegistryKey(), k -> new HashSet<>());
        if (wideScan) {
            registerKnownBeaconsFromLoadedChunks(world, p, WIDE_BEACON_SCAN_RADIUS);
        } else {
            registerKnownBeaconsFromLoadedChunks(world, p, VANILLA_BEACON_TRACK_RADIUS);
        }

        // 既知ビーコンを優先確認。256範囲でも毎回巨大な立方体探索をしない。
        Iterator<BlockPos> it = known.iterator();
        while (it.hasNext()) {
            BlockPos pos = it.next();
            if (!isBeaconAnchorBlock(world.getBlockState(pos))) {
                it.remove();
                continue;
            }
            BeaconInfo info = analyzeBeacon(world, pos);
            if (!info.active) continue;
            if (insideSquareRange(player, pos, info.range) && shouldPreferBeaconCandidate(world, pos, info, best)) {
                best = new PlayerBeaconState(true, pos.toImmutable(), info.range, info.ward, world.getRegistryKey());
            }
        }

        // v14: 新規ビーコン探索は近距離だけ。ビーコン付近で一度検出するとキャッシュされる。
        // 広域の全ブロック探索はしない。
        int scan = LOCAL_BEACON_SCAN_RADIUS;
        for (BlockPos pos : BlockPos.iterate(p.add(-scan, -scan, -scan), p.add(scan, scan, scan))) {
            if (!isBeaconAnchorBlock(world.getBlockState(pos))) continue;
            BlockPos immutable = pos.toImmutable();
            known.add(immutable);
            BeaconInfo info = analyzeBeacon(world, immutable);
            if (!info.active) continue;
            if (insideSquareRange(player, immutable, info.range) && shouldPreferBeaconCandidate(world, immutable, info, best)) {
                best = new PlayerBeaconState(true, immutable, info.range, info.ward, world.getRegistryKey());
            }
        }
        return best;
    }


    private static boolean shouldPreferBeaconCandidate(ServerWorld world, BlockPos candidatePos, BeaconInfo candidateInfo, PlayerBeaconState currentBest) {
        if (candidateInfo == null || !candidateInfo.active) return false;
        if (currentBest == null || !currentBest.active) return true;
        if (candidateInfo.range > currentBest.range) return true;
        if (candidateInfo.range < currentBest.range) return false;

        // RC1: 同じ範囲の場合は通常ビーコンよりビーコンコア系を優先する。
        // 通常ビーコンはMinecraft標準、追加効果はビーコンコア系を主役にする。
        int candidatePriority = beaconPriority(world.getBlockState(candidatePos));
        int currentPriority = beaconPriority(world.getBlockState(currentBest.beaconPos));
        if (candidatePriority != currentPriority) return candidatePriority > currentPriority;

        // 優先順位も同じなら、既存bestを維持して無駄な切り替わりを防ぐ。
        return false;
    }

    private static int beaconPriority(BlockState state) {
        if (state.isOf(ENHANCED_BEACON_CORE)) return 3;
        if (state.isOf(BEACON_CORE)) return 2;
        if (state.isOf(Blocks.BEACON)) return 1;
        return 0;
    }

    private static void registerKnownBeaconsFromLoadedChunks(ServerWorld world, BlockPos center, int radius) {
        Set<BlockPos> known = KNOWN_BEACONS.computeIfAbsent(world.getRegistryKey(), k -> new HashSet<>());
        int minChunkX = (center.getX() - radius) >> 4;
        int maxChunkX = (center.getX() + radius) >> 4;
        int minChunkZ = (center.getZ() - radius) >> 4;
        int maxChunkZ = (center.getZ() + radius) >> 4;
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                try {
                    if (!world.getChunkManager().isChunkLoaded(cx, cz)) continue;
                    WorldChunk chunk = world.getChunk(cx, cz);
                    for (BlockPos pos : chunk.getBlockEntities().keySet()) {
                        if (Math.abs(pos.getX() - center.getX()) > radius || Math.abs(pos.getZ() - center.getZ()) > radius) continue;
                        if (isBeaconAnchorBlock(world.getBlockState(pos))) known.add(pos.toImmutable());
                    }
                } catch (Throwable ignored) {
                    // 未ロードチャンクや環境差があってもゲーム処理を止めない。
                }
            }
        }
    }

    private static boolean isStillInsideTrackedBeacon(ServerPlayerEntity player, ServerWorld world, PlayerBeaconState state) {
        if (!state.active) return false;
        if (!world.getRegistryKey().equals(state.worldKey)) return false;
        if (!isBeaconAnchorBlock(world.getBlockState(state.beaconPos))) return false;
        BeaconInfo info = analyzeBeacon(world, state.beaconPos);
        return info.active && insideSquareRange(player, state.beaconPos, info.range);
    }

    private static boolean insideSquareRange(ServerPlayerEntity player, BlockPos beacon, int range) {
        BlockPos p = player.getBlockPos();
        return Math.abs(p.getX() - beacon.getX()) <= range
                && Math.abs(p.getZ() - beacon.getZ()) <= range
                && Math.abs(p.getY() - beacon.getY()) <= range;
    }

    private static boolean isBeaconAnchorBlock(BlockState state) {
        return state.isOf(Blocks.BEACON) || state.isOf(BEACON_CORE) || state.isOf(ENHANCED_BEACON_CORE);
    }

    private static BeaconInfo analyzeBeacon(ServerWorld world, BlockPos beaconPos) {
        // RC1: 通常ビーコンはMinecraft標準仕様のまま。
        // - ビーコンコア単体: 範囲128
        // - ビーコンコア・極単体: 範囲256
        // 旧v1のネザライト土台による通常ビーコン範囲拡張は廃止。
        if (world.getBlockState(beaconPos).isOf(ENHANCED_BEACON_CORE)) {
            CoreSettings settings = getCoreSettings(world, beaconPos);
            return new BeaconInfo(true, RANGE_ENHANCED_CORE, true);
        }
        if (world.getBlockState(beaconPos).isOf(BEACON_CORE)) {
            CoreSettings settings = getCoreSettings(world, beaconPos);
            return new BeaconInfo(true, RANGE_CORE, true);
        }

        int tier = getVanillaTier(world, beaconPos);
        if (tier <= 0) return new BeaconInfo(false, 0, false);

        int range = VANILLA_RANGE_BASE + tier * 10;
        boolean ward = hasSoulLanternWard(world, beaconPos);
        return new BeaconInfo(true, range, ward);
    }

    private static int getVanillaTier(World world, BlockPos beaconPos) {
        int tier = 0;
        for (int y = 1; y <= 4; y++) {
            int radius = y;
            boolean ok = true;
            for (int x = -radius; x <= radius && ok; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (!isBeaconBaseBlock(world.getBlockState(beaconPos.add(x, -y, z)).getBlock())) {
                        ok = false;
                        break;
                    }
                }
            }
            if (ok) tier = y;
            else break;
        }
        return tier;
    }

    private static boolean isBeaconBaseBlock(net.minecraft.block.Block block) {
        return block == Blocks.IRON_BLOCK || block == Blocks.GOLD_BLOCK || block == Blocks.EMERALD_BLOCK || block == Blocks.DIAMOND_BLOCK || block == Blocks.NETHERITE_BLOCK;
    }

    private static boolean hasSoulLanternWard(World world, BlockPos beaconPos) {
        return world.getBlockState(beaconPos.north()).isOf(Blocks.SOUL_LANTERN)
                && world.getBlockState(beaconPos.south()).isOf(Blocks.SOUL_LANTERN)
                && world.getBlockState(beaconPos.east()).isOf(Blocks.SOUL_LANTERN)
                && world.getBlockState(beaconPos.west()).isOf(Blocks.SOUL_LANTERN);
    }



    private static void processInfrastructureZones(ServerWorld world) {
        // v27: ブロック全探索ではなく、周辺チャンク内のBlockEntityだけを見る。
        // 蜂の巣・かまど・醸造台はいずれもBlockEntityなので、複数設備を拾いやすくしつつ軽量化。
        Set<BlockPos> processedHives = new HashSet<>();
        Set<BlockPos> processedWorkstations = new HashSet<>();

        for (ServerPlayerEntity player : world.getPlayers()) {
            PlayerBeaconState state = PLAYER_STATES.get(player.getUuid());
            if (state == null || !state.active || !state.worldKey.equals(world.getRegistryKey())) continue;

            BlockPos center = player.getBlockPos();
            int radius = INFRA_SCAN_RADIUS;
            int minChunkX = (center.getX() - radius) >> 4;
            int maxChunkX = (center.getX() + radius) >> 4;
            int minChunkZ = (center.getZ() - radius) >> 4;
            int maxChunkZ = (center.getZ() + radius) >> 4;

            for (int cx = minChunkX; cx <= maxChunkX; cx++) {
                for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                    WorldChunk chunk;
                    try {
                        chunk = world.getChunk(cx, cz);
                    } catch (Throwable ignored) {
                        continue;
                    }
                    for (BlockPos rawPos : chunk.getBlockEntities().keySet()) {
                        BlockPos pos = rawPos.toImmutable();
                        if (Math.abs(pos.getX() - center.getX()) > radius || Math.abs(pos.getZ() - center.getZ()) > radius || Math.abs(pos.getY() - center.getY()) > radius) continue;
                        if (!insideSquareRange(pos, state.beaconPos, state.range)) continue;

                        BlockState blockState = world.getBlockState(pos);
                        if (isBeeHome(blockState) && processedHives.add(pos)) {
                            processFertilityZone(world, pos, state);
                        } else if (isFurnaceLike(blockState) && processedWorkstations.add(pos)) {
                            // v49: 工房領域テスト。燃料効率は触らず、燃焼中/精錬中のcook進行だけ少し足す。
                            processWorkshopZone(world, pos);
                        } else if (blockState.isOf(Blocks.BREWING_STAND) && processedWorkstations.add(pos)) {
                            processAlchemyZone(world, pos);
                        }
                    }
                }
            }
        }
    }

    private static boolean isBeeHome(BlockState state) {
        return state.isOf(Blocks.BEE_NEST) || state.isOf(Blocks.BEEHIVE);
    }

    private static boolean isFurnaceLike(BlockState state) {
        // v50: 工房領域は通常かまどのみ対象。溶鉱炉・燻製器はvanilla速度のまま触らない。
        return state.isOf(Blocks.FURNACE);
    }

    private static void processFertilityZone(ServerWorld world, BlockPos hivePos, PlayerBeaconState state) {
        int r = FERTILITY_RADIUS;
        int grown = 0;
        // v27: v25で効いていた順走査方式へ戻しつつ、対象作物だけに限定。
        // 複数巣箱はprocessInfrastructureZones側でそれぞれ処理する。
        for (BlockPos pMutable : BlockPos.iterate(hivePos.add(-r, -r, -r), hivePos.add(r, r, r))) {
            if (grown >= 64) break;
            BlockPos pos = pMutable.toImmutable();
            if (!insideSquareRange(pos, state.beaconPos, state.range)) continue;
            BlockState crop = world.getBlockState(pos);
            if (!isFertilityTarget(crop) || !crop.hasRandomTicks()) continue;
            try {
                crop.randomTick(world, pos, world.random);
                if (world.random.nextInt(2) == 0) crop.randomTick(world, pos, world.random);
                grown++;
            } catch (Throwable ignored) {
                // 一部ブロックでrandomTick差異があっても処理を止めない。
            }
        }
    }

    private static boolean isFertilityTarget(BlockState state) {
        return state.isOf(Blocks.WHEAT)
                || state.isOf(Blocks.CARROTS)
                || state.isOf(Blocks.POTATOES)
                || state.isOf(Blocks.BEETROOTS)
                || state.isOf(Blocks.MELON_STEM)
                || state.isOf(Blocks.PUMPKIN_STEM)
                || state.isOf(Blocks.SUGAR_CANE)
                || state.isOf(Blocks.CACTUS)
                || state.isOf(Blocks.SWEET_BERRY_BUSH)
                || state.isOf(Blocks.NETHER_WART)
                || state.isOf(Blocks.BAMBOO)
                || state.isOf(Blocks.COCOA)
                || state.isOf(Blocks.TORCHFLOWER_CROP)
                || state.isOf(Blocks.PITCHER_CROP);
    }

    private static void processWorkshopZone(ServerWorld world, BlockPos furnacePos) {
        if (!hasHeatSourceNearby(world, furnacePos, WORKSHOP_RADIUS)) return;
        BlockEntity be = world.getBlockEntity(furnacePos);
        if (be == null) return;
        // v31: かまど系はまず「精錬速度UPだけ」を確実化する。
        // 燃料効率には触らない。燃料系カウンタを誤って動かすと逆に遅くなるため。
        if (!hasFurnaceInput(be)) return;
        boostFurnaceProgressOnly(be);
        markDirtySafe(be);
    }


    private static boolean hasFurnaceInput(BlockEntity be) {
        try {
            if (be instanceof net.minecraft.inventory.Inventory inv) {
                return inv.size() > 0 && !inv.getStack(0).isEmpty();
            }
        } catch (Throwable ignored) {}
        return true;
    }

    private static void boostFurnaceProgressOnly(BlockEntity be) {
        try {
            // かまどの精錬進捗は「0〜200付近で増える小さなint」であることが多い。
            // 燃焼時間は燃料投入直後に大きめの値になりやすいので、まず除外寄りにする。
            ArrayList<Field> candidates = new ArrayList<>();
            for (Field f : allIntFields(be)) {
                f.setAccessible(true);
                int v = f.getInt(be);
                String n = f.getName().toLowerCase(Locale.ROOT);
                if (v <= 0 || v >= 200) continue;
                if (n.contains("burn") || n.contains("fuel") || n.contains("lit")) continue;
                if (n.contains("cook") || n.contains("progress") || n.contains("time") || n.contains("field") || n.length() <= 3) {
                    candidates.add(f);
                }
            }
            if (candidates.isEmpty()) return;

            // 最も進捗らしい「現在値が大きい」候補を少しだけ進める。
            // 燃料カウンタやレシピ時間は触らず、逆に遅くなる事故を避けるため控えめに加算。
            Field best = null;
            int bestValue = -1;
            for (Field f : candidates) {
                int v = f.getInt(be);
                if (v > bestValue) {
                    best = f;
                    bestValue = v;
                }
            }
            if (best != null) {
                int boosted = Math.min(199, bestValue + 40);
                best.setInt(be, boosted);
            }
        } catch (Throwable ignored) {}
    }

    private static boolean hasHeatSourceNearby(ServerWorld world, BlockPos center, int radius) {
        for (BlockPos p : BlockPos.iterate(center.add(-radius, -radius, -radius), center.add(radius, radius, radius))) {
            BlockState s = world.getBlockState(p);
            if (s.isOf(Blocks.LAVA) || s.isOf(Blocks.MAGMA_BLOCK)) return true;
        }
        return false;
    }

    private static void processAlchemyZone(ServerWorld world, BlockPos brewingPos) {
        if (!hasAlchemyCatalystNearby(world, brewingPos, ALCHEMY_RADIUS)) return;
        BlockEntity be = world.getBlockEntity(brewingPos);
        if (be == null) return;
        // v31: 醸造台は「実際に醸造中らしい時」だけ加速する。
        // 材料が無い待機状態では一切カウンタを触らない。
        if (!hasBrewingContents(be)) return;
        boostBrewingProgressOnly(be);
    }

    private static boolean hasBrewingContents(BlockEntity be) {
        try {
            if (be instanceof net.minecraft.inventory.Inventory inv) {
                boolean hasBottle = false;
                for (int i = 0; i < Math.min(3, inv.size()); i++) {
                    if (!inv.getStack(i).isEmpty()) {
                        hasBottle = true;
                        break;
                    }
                }
                boolean hasIngredient = inv.size() > 3 && !inv.getStack(3).isEmpty();
                return hasBottle && hasIngredient && getBrewingTimeCandidate(be) > 0;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static int getBrewingTimeCandidate(BlockEntity be) {
        int best = 0;
        for (Field f : allIntFields(be)) {
            try {
                f.setAccessible(true);
                int v = f.getInt(be);
                String n = f.getName().toLowerCase(Locale.ROOT);
                if (v > 0 && v <= 400 && !n.contains("fuel")) {
                    if (n.contains("brew") || n.contains("time") || n.contains("field") || n.length() <= 3) {
                        best = Math.max(best, v);
                    }
                }
            } catch (Throwable ignored) {}
        }
        return best;
    }

    private static void boostBrewingProgressOnly(BlockEntity be) {
        try {
            Field best = null;
            int bestValue = 0;
            for (Field f : allIntFields(be)) {
                f.setAccessible(true);
                int v = f.getInt(be);
                String n = f.getName().toLowerCase(Locale.ROOT);
                // 醸造時間は400から0へ減る。燃料や0付近の小カウンタは触らない。
                if (v > 20 && v <= 400 && !n.contains("fuel")) {
                    if (n.contains("brew") || n.contains("time") || n.contains("field") || n.length() <= 3) {
                        if (v > bestValue) {
                            best = f;
                            bestValue = v;
                        }
                    }
                }
            }
            if (best != null) {
                best.setInt(be, Math.max(1, bestValue - 80));
                markDirtySafe(be);
            }
        } catch (Throwable ignored) {}
    }

    private static boolean hasAlchemyCatalystNearby(ServerWorld world, BlockPos center, int radius) {
        boolean soulSand = false;
        boolean netherWart = false;
        for (BlockPos p : BlockPos.iterate(center.add(-radius, -radius, -radius), center.add(radius, radius, radius))) {
            BlockState s = world.getBlockState(p);
            if (s.isOf(Blocks.SOUL_SAND)) soulSand = true;
            if (s.isOf(Blocks.NETHER_WART)) netherWart = true;
            if (soulSand && netherWart) return true;
        }
        return false;
    }


    private static void boostFurnaceLikeInts(BlockEntity be) {
        try {
            for (Field f : allIntFields(be)) {
                f.setAccessible(true);
                int v = f.getInt(be);
                // Furnace-like block entities normally have cook progress around 0-200.
                // Boost only small positive counters to avoid damaging unrelated state.
                if (v > 0 && v < 200) {
                    f.setInt(be, Math.min(199, v + 6));
                    return;
                }
            }
        } catch (Throwable ignored) {}
    }

    private static List<Field> allIntFields(BlockEntity be) {
        ArrayList<Field> result = new ArrayList<>();
        Class<?> cls = be.getClass();
        while (cls != null) {
            for (Field f : cls.getDeclaredFields()) {
                if (f.getType() == int.class) result.add(f);
            }
            cls = cls.getSuperclass();
        }
        return result;
    }

    private static void markDirtySafe(BlockEntity be) {
        try { be.markDirty(); } catch (Throwable ignored) {}
    }

    private static boolean boostIntFieldByNameResult(BlockEntity be, String[] names, int amount, int min, int max) {
        Field f = findIntField(be, names);
        if (f == null) return false;
        try {
            f.setAccessible(true);
            int v = f.getInt(be);
            if (v <= min || v >= max) return false;
            f.setInt(be, Math.min(max, v + amount));
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void boostIntFieldByName(BlockEntity be, String[] names, int amount, int min, int max) {
        Field f = findIntField(be, names);
        if (f == null) return;
        try {
            f.setAccessible(true);
            int v = f.getInt(be);
            if (v <= min) return;
            f.setInt(be, Math.min(max, v + amount));
        } catch (Throwable ignored) {}
    }

    private static void reduceIntFieldByName(BlockEntity be, String[] names, int amount, int min) {
        Field f = findIntField(be, names);
        if (f == null) return;
        try {
            f.setAccessible(true);
            int v = f.getInt(be);
            if (v <= min) return;
            f.setInt(be, Math.max(min, v - amount));
        } catch (Throwable ignored) {}
    }

    private static Field findIntField(BlockEntity be, String[] names) {
        Class<?> cls = be.getClass();
        while (cls != null) {
            for (Field f : cls.getDeclaredFields()) {
                if (f.getType() != int.class) continue;
                String n = f.getName();
                for (String wanted : names) {
                    if (n.equals(wanted) || n.toLowerCase(Locale.ROOT).contains(wanted.toLowerCase(Locale.ROOT))) return f;
                }
            }
            cls = cls.getSuperclass();
        }
        return null;
    }

    private static void suppressPhantomsNearWards(ServerWorld world) {
        Set<BlockPos> checked = new HashSet<>();
        for (PlayerBeaconState state : PLAYER_STATES.values()) {
            if (state == null || !state.active || !state.ward || !state.worldKey.equals(world.getRegistryKey())) continue;
            if (!checked.add(state.beaconPos)) continue;
            if (!isBeaconAnchorBlock(world.getBlockState(state.beaconPos))) continue;
            BeaconInfo info = analyzeBeacon(world, state.beaconPos);
            if (!info.active || !info.ward) continue;
            if (isCoreBlock(world, state.beaconPos) && !getCoreSettings(world, state.beaconPos).phantomSuppression) continue;
            Box box = new Box(
                    state.beaconPos.getX() - info.range, state.beaconPos.getY() - info.range, state.beaconPos.getZ() - info.range,
                    state.beaconPos.getX() + info.range + 1, state.beaconPos.getY() + info.range + 1, state.beaconPos.getZ() + info.range + 1
            );
            List<PhantomEntity> phantoms = world.getEntitiesByClass(PhantomEntity.class, box, phantom -> phantom != null && phantom.isAlive() && insideSquareRange(phantom.getBlockPos(), state.beaconPos, info.range));
            for (PhantomEntity phantom : phantoms) {
                phantom.discard();
            }
        }
    }

    private static void suppressMobsLegacy(ServerWorld world) {
        Set<BlockPos> checked = new HashSet<>();
        for (PlayerBeaconState state : PLAYER_STATES.values()) {
            if (state == null || !state.active || !state.ward || !state.worldKey.equals(world.getRegistryKey())) continue;
            if (!checked.add(state.beaconPos)) continue;
            if (!isBeaconAnchorBlock(world.getBlockState(state.beaconPos))) continue;
            BeaconInfo info = analyzeBeacon(world, state.beaconPos);
            if (!info.active || !info.ward) continue;
            Box box = new Box(
                    state.beaconPos.getX() - info.range, state.beaconPos.getY() - info.range, state.beaconPos.getZ() - info.range,
                    state.beaconPos.getX() + info.range + 1, state.beaconPos.getY() + info.range + 1, state.beaconPos.getZ() + info.range + 1
            );
            List<HostileEntity> mobs = world.getEntitiesByClass(HostileEntity.class, box, mob -> shouldSuppressHostileMob(mob, state.beaconPos, info.range));
            int removed = 0;
            for (HostileEntity mob : mobs) {
                mob.discard();
                removed++;
                if (removed >= 80) break;
            }
        }
    }

    private static boolean shouldSuppressHostileMob(HostileEntity entity, BlockPos beaconPos, int range) {
        if (entity == null || !entity.isAlive()) return false;
        if (entity.hasCustomName()) return false;
        EntityType<?> type = entity.getType();
        if (type == EntityType.WITHER || type == EntityType.ENDER_DRAGON || type == EntityType.SLIME) return false;
        return insideSquareRange(entity.getBlockPos(), beaconPos, range);
    }

    public static boolean shouldCancelNaturalHostileSpawn(EntityType<?> type, WorldAccess world, SpawnReason reason, BlockPos pos) {
        // v18: モンスター抑制を再実装。
        // Mobを毎tick探して消す方式ではなく、自然スポーン判定の瞬間だけキャンセルする。
        // スポナー・コマンド・襲撃などは止めない。
        if (reason != SpawnReason.NATURAL) return false;
        if (type == null || type.getSpawnGroup() != net.minecraft.entity.SpawnGroup.MONSTER) return false;
        if (!(world instanceof ServerWorld serverWorld)) return false;

        Set<BlockPos> known = KNOWN_BEACONS.get(serverWorld.getRegistryKey());
        if (known == null || known.isEmpty()) return false;

        Iterator<BlockPos> it = known.iterator();
        while (it.hasNext()) {
            BlockPos beaconPos = it.next();
            if (!isBeaconAnchorBlock(serverWorld.getBlockState(beaconPos))) {
                it.remove();
                continue;
            }
            BeaconInfo info = analyzeBeacon(serverWorld, beaconPos);
            if (!info.active || !info.ward) continue;
            // v2.1-dev3: ビーコンコア系はモンスター自然湧き抑制を標準搭載。GUI設定に関係なく有効。
            if (insideSquareRange(pos, beaconPos, info.range)) {
                return true;
            }
        }
        return false;
    }

    private static boolean insideSquareRange(BlockPos pos, BlockPos beacon, int range) {
        return Math.abs(pos.getX() - beacon.getX()) <= range
                && Math.abs(pos.getZ() - beacon.getZ()) <= range
                && Math.abs(pos.getY() - beacon.getY()) <= range;
    }

    private static List<ServerWorld> iterableToList(Iterable<ServerWorld> it) {
        List<ServerWorld> list = new ArrayList<>();
        for (ServerWorld w : it) list.add(w);
        return list;
    }

    private static void drawRange(ServerPlayerEntity player, ServerWorld world, PlayerBeaconState state) {
        if (!state.active) return;
        int range = state.range;
        BlockPos c = state.beaconPos;

        // v2-dev.36: 範囲表示は外周1列へ戻す。高さ表示はdev.35相当を維持。
        int step = range >= 256 ? 8 : (range >= 128 ? 5 : 4);
        double baseY = player.getY() + 0.18D;
        double[] heights = new double[]{0.0D, 3.0D, 6.0D};

        int r = range;
        for (double h : heights) {
            double y = baseY + h;
            for (int d = -r; d <= r; d += step) {
                spawnLineParticle(player, world, c.getX() + d + 0.5, y, c.getZ() - r + 0.5);
                spawnLineParticle(player, world, c.getX() + d + 0.5, y, c.getZ() + r + 0.5);
                spawnLineParticle(player, world, c.getX() - r + 0.5, y, c.getZ() + d + 0.5);
                spawnLineParticle(player, world, c.getX() + r + 0.5, y, c.getZ() + d + 0.5);
            }
        }

        // 角だけは少し濃くして、境界角を見つけやすくする。
        int cornerRange = range;
        double[][] corners = new double[][]{
                {c.getX() - cornerRange + 0.5, c.getZ() - cornerRange + 0.5},
                {c.getX() - cornerRange + 0.5, c.getZ() + cornerRange + 0.5},
                {c.getX() + cornerRange + 0.5, c.getZ() - cornerRange + 0.5},
                {c.getX() + cornerRange + 0.5, c.getZ() + cornerRange + 0.5}
        };
        for (double[] corner : corners) {
            for (int i = 0; i < 14; i++) {
                spawnLineParticle(player, world, corner[0], baseY + i * 0.55D, corner[1]);
            }
        }
    }

    private static void spawnLineParticle(ServerPlayerEntity player, ServerWorld world, double x, double y, double z) {
        // 火の玉系ではなく、細い光の点を高密度に並べてライン状に見せる。
        world.spawnParticles(player, ParticleTypes.END_ROD, true, true, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
    }

    private static boolean hasAdvancement(ServerPlayerEntity player, String idPath) {
        MinecraftServer server = CURRENT_SERVER;
        if (server == null) return false;
        Identifier id = Identifier.of("minecraft", idPath);
        AdvancementEntry entry = server.getAdvancementLoader().get(id);
        return entry != null && player.getAdvancementTracker().getProgress(entry).isDone();
    }

    private static boolean isExtraUnlocked(PlayerEntity player, int extraIndex) {
        if (extraIndex == 12) return player instanceof ServerPlayerEntity sp && getBuildReachUnlockedCount(sp) > 0;
        if (extraIndex == 7) return player instanceof ServerPlayerEntity sp && getVillagerBlessingLevel(sp) > 0;
        String adv = extraAdvancementId(extraIndex);
        if (adv == null) return true;
        return player instanceof ServerPlayerEntity sp && hasAdvancement(sp, adv);
    }

    private static String extraAdvancementId(int extraIndex) {
        return switch (extraIndex) {
            case 2 -> ADV_SERIOUS_DEDICATION;
            case 3 -> ADV_LIGHTEN_UP;
            case 4 -> ADV_WATER;
            case 6 -> ADV_COVER_DEBRIS;
            case 7 -> ADV_HERO;
            case 8 -> ADV_KILL_ALL_MOBS;
            case 9 -> ADV_NEW_LOOK;
            case 10 -> ADV_HOW_DID;
            case 11 -> ADV_KILL_DRAGON;
            default -> null;
        };
    }

    private static String extraAdvancementName(int extraIndex) {
        return switch (extraIndex) {
            case 2 -> "真面目な献身";
            case 3 -> "明るくなーれ";
            case 4 -> "いちばんカワイイ捕食者";
            case 6 -> "残骸で私を覆って";
            case 7 -> "対象進捗の達成数（順不同）";
            case 8 -> "モンスター狩りの達人";
            case 9 -> "レベル1: おニューの衣装 / レベル3: オシャレな鍛冶職人";
            case 10 -> "どうやってここまで？";
            case 11 -> "エンドの解放";
            case 12 -> "対象進捗の達成数（順不同）";
            default -> "なし";
        };
    }


    private static void ensureCoreSettingsLoaded(MinecraftServer server) {
        if (CORE_SETTINGS_LOADED) return;
        CORE_SETTINGS_LOADED = true;
        loadCoreSettings(server);
    }

    private static Path coreSettingsPath(MinecraftServer server) {
        return server.getSavePath(WorldSavePath.ROOT).resolve(CORE_SETTINGS_FILE);
    }

    private static void loadCoreSettings(MinecraftServer server) {
        Path path = coreSettingsPath(server);
        if (!Files.exists(path)) return;
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            props.load(in);
            CORE_SETTINGS.clear();
            Set<String> keys = new HashSet<>();
            for (String name : props.stringPropertyNames()) {
                int dot = name.lastIndexOf('.');
                if (dot > 0) keys.add(name.substring(0, dot));
            }
            for (String key : keys) {
                CoreSettings settings = CoreSettings.fromProperties(props, key);
                CORE_SETTINGS.put(key, settings);
            }
            System.out.println("[Maikura Beacon Expansion] Loaded core settings: " + CORE_SETTINGS.size());
        } catch (Exception e) {
            System.err.println("[Maikura Beacon Expansion] Failed to load core settings: " + e.getMessage());
        }
    }

    private static void saveCoreSettings(MinecraftServer server) {
        if (server == null) return;
        Path path = coreSettingsPath(server);
        Properties props = new Properties();
        for (Map.Entry<String, CoreSettings> entry : CORE_SETTINGS.entrySet()) {
            entry.getValue().toProperties(props, entry.getKey());
        }
        try {
            Files.createDirectories(path.getParent());
            try (OutputStream out = Files.newOutputStream(path)) {
                props.store(out, "Maikura Beacon Expansion core settings");
            }
        } catch (Exception e) {
            System.err.println("[Maikura Beacon Expansion] Failed to save core settings: " + e.getMessage());
        }
    }

    private static void markCoreSettingsChanged(World world) {
        if (world != null && world.getServer() != null) {
            saveCoreSettings(world.getServer());
        } else if (CURRENT_SERVER != null) {
            saveCoreSettings(CURRENT_SERVER);
        }
    }


    private static class CoreSettings {
        int speed = 2;
        int haste = 2;
        int strength = 2;
        int resistance = 2;
        int jump = 2;
        int regeneration = 2;
        boolean monsterSuppression = true;
        boolean phantomSuppression = true;
        boolean hungerMaintenance = true;
        boolean nightVision = true;
        boolean waterSupport = true;
        boolean fallGuard = true;
        boolean fireResistance = true;
        boolean villagerBlessing = true;
        boolean regenBonus = true;
        boolean durabilityCare = true;
        boolean negativeShorten = true;
        boolean flight = true;
        int buildReachLevel = 5;
        static CoreSettings defaults() { return new CoreSettings(); }

        static CoreSettings fromProperties(Properties props, String prefix) {
            CoreSettings s = new CoreSettings();
            s.speed = readInt(props, prefix + ".speed", s.speed);
            s.haste = readInt(props, prefix + ".haste", s.haste);
            s.strength = readInt(props, prefix + ".strength", s.strength);
            s.resistance = readInt(props, prefix + ".resistance", s.resistance);
            s.jump = readInt(props, prefix + ".jump", s.jump);
            s.regeneration = readInt(props, prefix + ".regeneration", s.regeneration);
            s.monsterSuppression = readBool(props, prefix + ".monsterSuppression", s.monsterSuppression);
            s.phantomSuppression = readBool(props, prefix + ".phantomSuppression", s.phantomSuppression);
            s.hungerMaintenance = readBool(props, prefix + ".hungerMaintenance", s.hungerMaintenance);
            s.nightVision = readBool(props, prefix + ".nightVision", s.nightVision);
            s.waterSupport = readBool(props, prefix + ".waterSupport", s.waterSupport);
            s.fallGuard = readBool(props, prefix + ".fallGuard", s.fallGuard);
            s.fireResistance = readBool(props, prefix + ".fireResistance", s.fireResistance);
            s.villagerBlessing = readBool(props, prefix + ".villagerBlessing", s.villagerBlessing);
            s.regenBonus = readBool(props, prefix + ".regenBonus", s.regenBonus);
            s.durabilityCare = readBool(props, prefix + ".durabilityCare", s.durabilityCare);
            s.negativeShorten = readBool(props, prefix + ".negativeShorten", s.negativeShorten);
            s.flight = readBool(props, prefix + ".flight", s.flight);
            s.buildReachLevel = readInt(props, prefix + ".buildReachLevel", s.buildReachLevel);
            s.clampLevels();
            return s;
        }

        void toProperties(Properties props, String prefix) {
            clampLevels();
            props.setProperty(prefix + ".speed", Integer.toString(speed));
            props.setProperty(prefix + ".haste", Integer.toString(haste));
            props.setProperty(prefix + ".strength", Integer.toString(strength));
            props.setProperty(prefix + ".resistance", Integer.toString(resistance));
            props.setProperty(prefix + ".jump", Integer.toString(jump));
            props.setProperty(prefix + ".regeneration", Integer.toString(regeneration));
            props.setProperty(prefix + ".monsterSuppression", Boolean.toString(monsterSuppression));
            props.setProperty(prefix + ".phantomSuppression", Boolean.toString(phantomSuppression));
            props.setProperty(prefix + ".hungerMaintenance", Boolean.toString(hungerMaintenance));
            props.setProperty(prefix + ".nightVision", Boolean.toString(nightVision));
            props.setProperty(prefix + ".waterSupport", Boolean.toString(waterSupport));
            props.setProperty(prefix + ".fallGuard", Boolean.toString(fallGuard));
            props.setProperty(prefix + ".fireResistance", Boolean.toString(fireResistance));
            props.setProperty(prefix + ".villagerBlessing", Boolean.toString(villagerBlessing));
            props.setProperty(prefix + ".regenBonus", Boolean.toString(regenBonus));
            props.setProperty(prefix + ".durabilityCare", Boolean.toString(durabilityCare));
            props.setProperty(prefix + ".negativeShorten", Boolean.toString(negativeShorten));
            props.setProperty(prefix + ".flight", Boolean.toString(flight));
            props.setProperty(prefix + ".buildReachLevel", Integer.toString(buildReachLevel));
        }

        private void clampLevels() {
            speed = clampLevel(speed);
            haste = clampLevel(haste);
            strength = clampLevel(strength);
            resistance = clampLevel(resistance);
            jump = clampLevel(jump);
            regeneration = clampLevel(regeneration);
            buildReachLevel = Math.max(0, Math.min(5, buildReachLevel));
        }

        private static int clampLevel(int value) {
            return Math.max(0, Math.min(2, value));
        }

        private static int readInt(Properties props, String key, int fallback) {
            try { return Integer.parseInt(props.getProperty(key, Integer.toString(fallback))); }
            catch (Exception ignored) { return fallback; }
        }

        private static boolean readBool(Properties props, String key, boolean fallback) {
            String value = props.getProperty(key);
            return value == null ? fallback : Boolean.parseBoolean(value);
        }

        int get(int index) {
            return switch (index) {
                case 0 -> speed;
                case 1 -> haste;
                case 2 -> strength;
                case 3 -> resistance;
                case 4 -> jump;
                case 5 -> regeneration;
                default -> 0;
            };
        }
        void cycle(int index) { cycle(index, 1); }
        void cycle(int index, int delta) {
            int next = (get(index) + delta) % 3;
            if (next < 0) next += 3;
            switch (index) {
                case 0 -> speed = next;
                case 1 -> haste = next;
                case 2 -> strength = next;
                case 3 -> resistance = next;
                case 4 -> jump = next;
                case 5 -> regeneration = next;
            }
        }
        void setAllPrimaryLevels(int level) {
            level = clampLevel(level);
            speed = level;
            haste = level;
            strength = level;
            resistance = level;
            jump = level;
            regeneration = level;
        }
        void setAllExtras(boolean enabled) {
            monsterSuppression = enabled;
            phantomSuppression = enabled;
            hungerMaintenance = enabled;
            nightVision = enabled;
            waterSupport = enabled;
            fallGuard = enabled;
            fireResistance = enabled;
            villagerBlessing = enabled;
            regenBonus = enabled;
            durabilityCare = enabled;
            negativeShorten = enabled;
            flight = enabled;
            buildReachLevel = enabled ? 5 : 0;
        }
        void resetDefaults() {
            setAllPrimaryLevels(2);
            setAllExtras(true);
        }
        boolean getExtra(int index) {
            return switch (index) {
                case 0 -> monsterSuppression;
                case 1 -> phantomSuppression;
                case 2 -> hungerMaintenance;
                case 3 -> nightVision;
                case 4 -> waterSupport;
                case 5 -> fallGuard;
                case 6 -> fireResistance;
                case 7 -> villagerBlessing;
                case 8 -> regenBonus;
                case 9 -> durabilityCare;
                case 10 -> negativeShorten;
                case 11 -> flight;
                case 12 -> buildReachLevel > 0;
                default -> false;
            };
        }
        void setExtra(int index, boolean enabled) {
            switch (index) {
                case 0 -> monsterSuppression = enabled;
                case 1 -> phantomSuppression = enabled;
                case 2 -> hungerMaintenance = enabled;
                case 3 -> nightVision = enabled;
                case 4 -> waterSupport = enabled;
                case 5 -> fallGuard = enabled;
                case 6 -> fireResistance = enabled;
                case 7 -> villagerBlessing = enabled;
                case 8 -> regenBonus = enabled;
                case 9 -> durabilityCare = enabled;
                case 10 -> negativeShorten = enabled;
                case 11 -> flight = enabled;
                case 12 -> buildReachLevel = enabled ? 5 : 0;
            }
        }
        void toggleExtra(int index) {
            switch (index) {
                case 0 -> monsterSuppression = !monsterSuppression;
                case 1 -> phantomSuppression = !phantomSuppression;
                case 2 -> hungerMaintenance = !hungerMaintenance;
                case 3 -> nightVision = !nightVision;
                case 4 -> waterSupport = !waterSupport;
                case 5 -> fallGuard = !fallGuard;
                case 6 -> fireResistance = !fireResistance;
                case 7 -> villagerBlessing = !villagerBlessing;
                case 8 -> regenBonus = !regenBonus;
                case 9 -> durabilityCare = !durabilityCare;
                case 10 -> negativeShorten = !negativeShorten;
                case 11 -> flight = !flight;
                case 12 -> buildReachLevel = buildReachLevel > 0 ? 0 : 5;
            }
        }
    }

    private static class CoreControlFactory implements net.minecraft.screen.NamedScreenHandlerFactory {
        private final World world;
        private final BlockPos pos;
        private final boolean enhanced;
        CoreControlFactory(World world, BlockPos pos, boolean enhanced) {
            this.world = world;
            this.pos = pos;
            this.enhanced = enhanced;
        }
        @Override
        public Text getDisplayName() {
            return Text.literal(enhanced ? "ビーコンコア・極 管理" : "ビーコンコア 管理");
        }
        @Override
        public ScreenHandler createMenu(int syncId, net.minecraft.entity.player.PlayerInventory playerInventory, PlayerEntity player) {
            return new CoreControlScreenHandler(syncId, playerInventory, world, pos, player);
        }
    }

    private static class CoreControlScreenHandler extends GenericContainerScreenHandler {
        private final SimpleInventory guiInventory;
        private final World world;
        private final BlockPos pos;
        private final PlayerEntity viewer;
        private int page = 0;
        private static final int PAGE_STANDARD = 0;
        private static final int PAGE_EXTRA = 1;
        private static final int PAGE_UTILITY = 2;
        CoreControlScreenHandler(int syncId, net.minecraft.entity.player.PlayerInventory playerInventory, World world, BlockPos pos, PlayerEntity viewer) {
            this(syncId, playerInventory, new SimpleInventory(54), world, pos, viewer);
        }
        private CoreControlScreenHandler(int syncId, net.minecraft.entity.player.PlayerInventory playerInventory, SimpleInventory inventory, World world, BlockPos pos, PlayerEntity viewer) {
            super(net.minecraft.screen.ScreenHandlerType.GENERIC_9X6, syncId, playerInventory, inventory, 6);
            this.guiInventory = inventory;
            this.world = world;
            this.pos = pos;
            this.viewer = viewer;
            if (!world.isClient()) {
                CoreSettings settings = getCoreSettings(world, pos);
                applyExtraLocksForPlayer(settings, viewer);
                markCoreSettingsChanged(world);
            }
            refreshItems();
        }
        @Override
        public boolean canUse(PlayerEntity player) {
            return player.squaredDistanceTo(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= 64.0D
                    && (world.getBlockState(pos).isOf(BEACON_CORE) || world.getBlockState(pos).isOf(ENHANCED_BEACON_CORE));
        }
        @Override
        public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
            if (slotIndex >= 0 && slotIndex < 54) {
                if (handlePageButton(slotIndex, player)) {
                    return;
                }
                int effectIndex = slotToEffect(slotIndex);
                if (effectIndex >= 0 && !world.isClient()) {
                    CoreSettings settings = getCoreSettings(world, pos);
                    clearCoreEffectNear(world, pos, effectIndex);
                    settings.cycle(effectIndex, button == 1 ? -1 : 1);
                    markCoreSettingsChanged(world);
                    refreshItems();
                    player.sendMessage(Text.literal(effectName(effectIndex) + " : レベル" + settings.get(effectIndex)), true);
                    if (player instanceof ServerPlayerEntity sp) {
                        LAST_PLAYER_EFFECT_REFRESH.put(sp.getUuid(), -999999L);
                    }
                    return;
                }
                int extraIndex = slotToExtra(slotIndex);
                if (extraIndex >= 0 && !world.isClient()) {
                    CoreSettings settings = getCoreSettings(world, pos);
                    boolean currentlyEnabled = settings.getExtra(extraIndex);
                    boolean unlocked = isExtraUnlocked(player, extraIndex);
                    if (extraIndex == 12 && player instanceof ServerPlayerEntity sp) {
                        int max = getBuildReachUnlockedCount(sp);
                        if (max <= 0) {
                            refreshItems();
                            player.sendMessage(Text.literal(extraName(extraIndex) + " : LOCKED / 解放条件: " + extraAdvancementName(extraIndex)), true);
                            return;
                        }
                        int next = settings.buildReachLevel + (button == 1 ? -1 : 1);
                        if (next < 0) next = max;
                        if (next > max) next = 0;
                        settings.buildReachLevel = next;
                    } else {
                        if (!currentlyEnabled && !unlocked) {
                            refreshItems();
                            player.sendMessage(Text.literal(extraName(extraIndex) + " : LOCKED / 解放条件: " + extraAdvancementName(extraIndex)), true);
                            return;
                        }
                        settings.toggleExtra(extraIndex);
                    }
                    markCoreSettingsChanged(world);
                    clearExtraEffectNear(world, pos, extraIndex);
                    refreshItems();
                    player.sendMessage(Text.literal(extraToggleMessage(player, extraIndex, settings)), true);
                    if (player instanceof ServerPlayerEntity sp) {
                        LAST_PLAYER_EFFECT_REFRESH.put(sp.getUuid(), -999999L);
                    }
                    return;
                }
                if (handleUtilityButton(slotIndex, player)) {
                    return;
                }
                return;
            }
            super.onSlotClick(slotIndex, button, actionType, player);
        }
        @Override
        public ItemStack quickMove(PlayerEntity player, int slot) {
            return ItemStack.EMPTY;
        }
        private boolean handlePageButton(int slotIndex, PlayerEntity player) {
            if (slotIndex == 45) {
                page = (page + 2) % 3;
                refreshItems();
                if (!world.isClient()) player.sendMessage(Text.literal(pageTitle() + "を表示"), true);
                return true;
            }
            if (slotIndex == 53) {
                page = (page + 1) % 3;
                refreshItems();
                if (!world.isClient()) player.sendMessage(Text.literal(pageTitle() + "を表示"), true);
                return true;
            }
            return false;
        }
        private int slotToEffect(int slot) {
            if (page != PAGE_STANDARD) return -1;
            return switch (slot) {
                case 19 -> 0;
                case 20 -> 1;
                case 21 -> 2;
                case 23 -> 3;
                case 24 -> 4;
                case 25 -> 5;
                default -> -1;
            };
        }
        private void refreshItems() {
            CoreSettings settings = getCoreSettings(world, pos);
            if (!world.isClient()) {
                applyExtraLocksForPlayer(settings, viewer);
            }
            boolean enhancedCore = world.getBlockState(pos).isOf(ENHANCED_BEACON_CORE);
            guiInventory.clear();
            fillDedicatedPanel(enhancedCore);
            setInfoItem(4, enhancedCore);
            setPageButton(45, Items.ARROW, "前のページ", previousPageTitle());
            setPageButton(49, Items.PAPER, "現在のページ", pageTitle());
            setPageButton(53, Items.ARROW, "次のページ", nextPageTitle());
            if (page == PAGE_STANDARD) {
                setPageNote(13, Items.BEACON, "標準ビーコン効果", "左クリックでLv+ / 右クリックでLv-");
                setButton(19, Items.SUGAR, 0, settings.speed);
                setButton(20, Items.GOLDEN_PICKAXE, 1, settings.haste);
                setButton(21, Items.IRON_SWORD, 2, settings.strength);
                setButton(23, Items.SHIELD, 3, settings.resistance);
                setButton(24, Items.RABBIT_FOOT, 4, settings.jump);
                setButton(25, Items.GHAST_TEAR, 5, settings.regeneration);
                setPageNote(31, Items.LIGHT_BLUE_STAINED_GLASS_PANE, "Lv表示", "OFF / Lv1 / Lv2を切替");
            } else if (page == PAGE_EXTRA) {
                setPageNote(13, Items.NETHER_STAR, "追加機能", "解放済みのみON可 / 未解放はLOCKED");
                setExtraButton(10, Items.SOUL_LANTERN, 0, settings.monsterSuppression);
                setExtraButton(11, Items.PHANTOM_MEMBRANE, 1, settings.phantomSuppression);
                setExtraButton(12, Items.GOLDEN_CARROT, 2, settings.hungerMaintenance);
                setExtraButton(14, Items.SPYGLASS, 3, settings.nightVision);
                setExtraButton(15, Items.HEART_OF_THE_SEA, 4, settings.waterSupport);
                setExtraButton(16, Items.FEATHER, 5, settings.fallGuard);
                setExtraButton(28, Items.MAGMA_CREAM, 6, settings.fireResistance);
                setExtraButton(29, Items.EMERALD, 7, settings.villagerBlessing);
                setExtraButton(30, Items.TOTEM_OF_UNDYING, 8, settings.regenBonus);
                setExtraButton(31, Items.SCAFFOLDING, 12, settings.buildReachLevel > 0);
                setExtraButton(32, Items.ANVIL, 9, settings.durabilityCare);
                setExtraButton(33, Items.MILK_BUCKET, 10, settings.negativeShorten);
                setExtraButton(34, Items.ELYTRA, 11, settings.flight);
                setPageNote(22, Items.CYAN_STAINED_GLASS_PANE, "ON/OFF状態", "表示: ON/OFF + 解放条件 + 解放状態");
            } else {
                setPageNote(13, Items.COMPARATOR, "一括操作", "コア全体の設定をまとめて変更");
                setUtilityButton(20, Items.LIME_DYE, "全効果ON", "標準効果Lv2 / 追加機能ON");
                setUtilityButton(22, Items.NETHER_STAR, "初期設定へ戻す", "標準効果Lv2 / 追加機能ON");
                setUtilityButton(24, Items.GRAY_DYE, "全効果OFF", "標準効果Lv0 / 追加機能OFF");
                setPageNote(31, enhancedCore ? Items.NETHERITE_BLOCK : Items.BEACON, "有効範囲", enhancedCore ? "256ブロック" : "128ブロック");
            }
        }
        private boolean handleUtilityButton(int slotIndex, PlayerEntity player) {
            if (world.isClient()) return false;
            if (page != PAGE_UTILITY) return false;
            if (slotIndex != 20 && slotIndex != 22 && slotIndex != 24) return false;
            CoreSettings settings = getCoreSettings(world, pos);
            if (slotIndex == 24) {
                settings.setAllPrimaryLevels(0);
                settings.setAllExtras(false);
                clearAllCoreEffectsNear(world, pos);
                player.sendMessage(Text.literal("ビーコンコア設定 : 全効果OFF"), true);
            } else {
                settings.resetDefaults();
                applyExtraLocksForPlayer(settings, player);
                player.sendMessage(Text.literal(slotIndex == 20 ? "ビーコンコア設定 : 全効果ON（未解放はLOCKED）" : "ビーコンコア設定 : 初期設定へ戻しました（未解放はLOCKED）"), true);
            }
            markCoreSettingsChanged(world);
            refreshItems();
            if (player instanceof ServerPlayerEntity sp) {
                LAST_PLAYER_EFFECT_REFRESH.put(sp.getUuid(), -999999L);
            }
            return true;
        }
        private void applyExtraLocksForPlayer(CoreSettings settings, PlayerEntity player) {
            for (int i = 0; i <= 12; i++) {
                if (i == 12 && player instanceof ServerPlayerEntity sp) {
                    settings.buildReachLevel = Math.min(settings.buildReachLevel, getBuildReachUnlockedCount(sp));
                } else if (!isExtraUnlocked(player, i)) {
                    settings.setExtra(i, false);
                }
            }
        }
        private void fillDedicatedPanel(boolean enhancedCore) {
            Item frame = enhancedCore ? Items.PURPLE_STAINED_GLASS_PANE : Items.BLUE_STAINED_GLASS_PANE;
            Item inner = enhancedCore ? Items.MAGENTA_STAINED_GLASS_PANE : Items.CYAN_STAINED_GLASS_PANE;
            Item line = Items.YELLOW_STAINED_GLASS_PANE;
            for (int i = 0; i < 54; i++) {
                boolean outer = i < 9 || i >= 45 || i % 9 == 0 || i % 9 == 8;
                if (outer) {
                    setDecorativePane(i, frame, " ");
                }
            }
            for (int slot : new int[]{3,5,39,41,47,51}) {
                setDecorativePane(slot, line, " ");
            }
            for (int slot : new int[]{18,26,27,35,36,44}) {
                setDecorativePane(slot, inner, " ");
            }
        }
        private void setDecorativePane(int slot, Item item, String name) {
            ItemStack stack = new ItemStack(item);
            try {
                stack.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal(name));
            } catch (Throwable ignored) {}
            guiInventory.setStack(slot, stack);
        }
        private void setNameAndLore(ItemStack stack, String name, String... loreLines) {
            try {
                stack.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal(name));
                List<Text> lore = new ArrayList<>();
                for (String line : loreLines) {
                    if (line == null || line.isBlank()) continue;
                    lore.add(Text.literal(line));
                }
                if (!lore.isEmpty()) {
                    stack.set(net.minecraft.component.DataComponentTypes.LORE, new LoreComponent(lore));
                }
            } catch (Throwable ignored) {}
        }
        private void setInfoItem(int slot, boolean enhancedCore) {
            ItemStack stack = new ItemStack(enhancedCore ? Items.NETHERITE_BLOCK : Items.BEACON);
            String range = enhancedCore ? "有効範囲: 256ブロック" : "有効範囲: 128ブロック";
            setNameAndLore(stack, enhancedCore ? "ビーコンコア・極 設定パネル" : "ビーコンコア 設定パネル", range);
            guiInventory.setStack(slot, stack);
        }
        private void setUtilityButton(int slot, Item item, String name, String note) {
            ItemStack stack = new ItemStack(item);
            setNameAndLore(stack, name, note);
            guiInventory.setStack(slot, stack);
        }
        private void setPageButton(int slot, Item item, String name, String note) {
            ItemStack stack = new ItemStack(item);
            setNameAndLore(stack, name, note);
            guiInventory.setStack(slot, stack);
        }
        private void setPageNote(int slot, Item item, String name, String note) {
            ItemStack stack = new ItemStack(item);
            setNameAndLore(stack, name, note);
            guiInventory.setStack(slot, stack);
        }
        private void setButton(int slot, Item item, int index, int level) {
            ItemStack stack = new ItemStack(item);
            String state = level <= 0 ? "OFF" : "レベル" + level;
            setNameAndLore(stack, effectName(index) + " " + state,
                    "状態: " + state,
                    "左クリック: 次へ",
                    "右クリック: 前へ",
                    "標準効果は進捗不要です。");
            guiInventory.setStack(slot, stack);
        }
        private void setExtraButton(int slot, Item item, int index, boolean enabled) {
            ItemStack stack = new ItemStack(item);
            boolean unlocked = isExtraUnlocked(viewer, index);
            String condition = extraAdvancementName(index);
            String lockState = unlocked ? "解放済み" : "未解放";
            String action = unlocked || enabled ? (index == 12 ? "左クリック: レベル+ / 右クリック: レベル-" : "クリックで切替") : "未解放のためON不可";
            if (index == 12) {
                int unlockedCount = viewer instanceof ServerPlayerEntity sp ? getBuildReachUnlockedCount(sp) : 0;
                CoreSettings settings = getCoreSettings(world, pos);
                int current = Math.min(settings.buildReachLevel, unlockedCount);
                List<String> lines = new ArrayList<>();
                lines.add("状態: " + (current > 0 ? "ON" : "OFF"));
                lines.add("現在レベル: " + current + "/5");
                lines.add("効果: +" + current + "ブロック");
                lines.add("解放条件: 対象進捗の達成数（順不同）");
                lines.add("達成状況: " + unlockedCount + "/5");
                lines.addAll(buildReachProgressLines(viewer));
                lines.add(action);
                setNameAndLore(stack, extraName(index) + " " + (current > 0 ? "レベル" + current + "/5" : "OFF (LOCKED)"), lines.toArray(new String[0]));
            } else if (index == 7) {
                int level = viewer instanceof ServerPlayerEntity sp ? getVillagerBlessingLevel(sp) : 0;
                List<String> lines = new ArrayList<>();
                lines.add("状態: " + lockState);
                lines.add("現在レベル: " + level + "/3");
                lines.add("効果: " + villagerBlessingEffectText(level));
                lines.add("解放条件: 対象進捗の達成数（順不同）");
                lines.add("達成状況: " + level + "/3");
                lines.addAll(villagerBlessingProgressLines(viewer));
                lines.add(action);
                setNameAndLore(stack, extraName(index) + " " + (enabled ? "ON" : "OFF") + (unlocked ? "" : " (LOCKED)"), lines.toArray(new String[0]));
            } else if (index == 9) {
                int level = viewer instanceof ServerPlayerEntity sp ? getDurabilityCareLevel(sp) : 0;
                List<String> lines = new ArrayList<>();
                lines.add("状態: " + lockState);
                lines.add("現在レベル: " + level + "/3");
                lines.add("効果: 武器・防具の耐久保護/修復");
                lines.add("解放条件:");
                lines.addAll(durabilityCareProgressLines(viewer));
                lines.add(action);
                setNameAndLore(stack, extraName(index) + " " + (enabled ? "ON" : "OFF") + (unlocked ? "" : " (LOCKED)"), lines.toArray(new String[0]));
            } else {
                setNameAndLore(stack, extraName(index) + " " + (enabled ? "ON" : "OFF") + (unlocked ? "" : " (LOCKED)"),
                        "状態: " + lockState,
                        "現在レベル: " + (unlocked ? "1/1" : "0/1"),
                        "効果: " + extraName(index),
                        "解放条件: " + condition,
                        action);
            }
            guiInventory.setStack(slot, stack);
        }
        private static int getVillagerBlessingLevel(ServerPlayerEntity player) {
            int count = 0;
            if (hasAdvancement(player, ADV_HERO)) count++;
            if (hasAdvancement(player, ADV_ZOMBIE_DOCTOR)) count++;
            if (hasAdvancement(player, ADV_STAR_TRADER)) count++;
            return count;
        }
        private static String villagerBlessingEffectText(int level) {
            if (level >= 3) return "取引価格割引（大） + 取引補充高速化";
            if (level >= 2) return "取引価格割引（大）";
            if (level >= 1) return "取引価格割引（小）";
            return "未解放";
        }
        private static int getDurabilityCareLevel(ServerPlayerEntity player) {
            if (hasAdvancement(player, ADV_STYLISH_SMITHING)) return 3;
            if (hasAdvancement(player, ADV_NEW_LOOK)) return 1;
            return 0;
        }
        private static String extraToggleMessage(PlayerEntity player, int extraIndex, CoreSettings settings) {
            if (extraIndex == 12 && player instanceof ServerPlayerEntity sp) {
                int unlocked = getBuildReachUnlockedCount(sp);
                int current = Math.min(settings.buildReachLevel, unlocked);
                return extraName(extraIndex) + " : レベル" + current + "/" + unlocked + " / " + (current > 0 ? "ON" : "OFF");
            }
            boolean unlocked = isExtraUnlocked(player, extraIndex);
            return extraName(extraIndex) + " : " + (settings.getExtra(extraIndex) ? "ON" : "OFF") + " / " + (unlocked ? "解放済み" : "進捗不要");
        }
        private static List<String> buildReachProgressLines(PlayerEntity player) {
            List<String> lines = new ArrayList<>();
            if (!(player instanceof ServerPlayerEntity sp)) {
                lines.add("達成状況: 未確認");
                return lines;
            }
            for (int i = 0; i < ADV_BUILD_REACH.length; i++) {
                lines.add((hasAdvancement(sp, ADV_BUILD_REACH[i]) ? "✓ " : "✗ ") + buildReachAdvancementName(i));
            }
            return lines;
        }
        private static List<String> villagerBlessingProgressLines(PlayerEntity player) {
            List<String> lines = new ArrayList<>();
            if (!(player instanceof ServerPlayerEntity sp)) {
                lines.add("解放条件: 未確認");
                return lines;
            }
            lines.add((hasAdvancement(sp, ADV_HERO) ? "✓ " : "✗ ") + "村の英雄");
            lines.add((hasAdvancement(sp, ADV_ZOMBIE_DOCTOR) ? "✓ " : "✗ ") + "ゾンビドクター");
            lines.add((hasAdvancement(sp, ADV_STAR_TRADER) ? "✓ " : "✗ ") + "星の商人");
            return lines;
        }
        private static List<String> durabilityCareProgressLines(PlayerEntity player) {
            List<String> lines = new ArrayList<>();
            if (!(player instanceof ServerPlayerEntity sp)) {
                lines.add("解放条件: 未確認");
                return lines;
            }
            lines.add("レベル1: おニューの衣装 " + (hasAdvancement(sp, ADV_NEW_LOOK) ? "(達成済み)" : "(未達成)"));
            lines.add("レベル3: オシャレな鍛冶職人 " + (hasAdvancement(sp, ADV_STYLISH_SMITHING) ? "(達成済み)" : "(未達成)"));
            return lines;
        }
        private static String buildReachAdvancementName(int index) {
            return switch (index) {
                case 0 -> "ホットな観光地";
                case 1 -> "冒険の時間";
                case 2 -> "亜空間バブル";
                case 3 -> "ビーコネーター";
                case 4 -> "遠方への逃走";
                default -> "進捗";
            };
        }
        private int slotToExtra(int slot) {
            if (page != PAGE_EXTRA) return -1;
            return switch (slot) {
                case 10 -> 0;
                case 11 -> 1;
                case 12 -> 2;
                case 14 -> 3;
                case 15 -> 4;
                case 16 -> 5;
                case 28 -> 6;
                case 29 -> 7;
                case 30 -> 8;
                case 32 -> 9;
                case 33 -> 10;
                case 34 -> 11;
                case 31 -> 12;
                default -> -1;
            };
        }
        private String pageTitle() {
            return switch (page) {
                case PAGE_STANDARD -> "標準効果ページ";
                case PAGE_EXTRA -> "追加機能ページ";
                case PAGE_UTILITY -> "一括操作ページ";
                default -> "設定ページ";
            };
        }
        private String nextPageTitle() {
            int next = (page + 1) % 3;
            return switch (next) {
                case PAGE_STANDARD -> "標準効果";
                case PAGE_EXTRA -> "追加機能";
                case PAGE_UTILITY -> "一括操作";
                default -> "次";
            };
        }
        private String previousPageTitle() {
            int prev = (page + 2) % 3;
            return switch (prev) {
                case PAGE_STANDARD -> "標準効果";
                case PAGE_EXTRA -> "追加機能";
                case PAGE_UTILITY -> "一括操作";
                default -> "前";
            };
        }
        private static String extraName(int index) {
            return switch (index) {
                case 0 -> "モンスター抑制";
                case 1 -> "ファントム抑制";
                case 2 -> "満腹維持";
                case 3 -> "暗視";
                case 4 -> "水中支援";
                case 5 -> "落下無効";
                case 6 -> "火炎耐性";
                case 7 -> "村人支援";
                case 8 -> "自然回復強化";
                case 9 -> "耐久保護/修復";
                case 10 -> "負効果短縮";
                case 11 -> "飛行";
                case 12 -> "ブロック操作距離";
                default -> "追加機能";
            };
        }
        private static String effectName(int index) {
            return switch (index) {
                case 0 -> "移動速度上昇";
                case 1 -> "採掘速度上昇";
                case 2 -> "攻撃力上昇";
                case 3 -> "耐性上昇";
                case 4 -> "跳躍力上昇";
                case 5 -> "再生能力";
                default -> "効果";
            };
        }
    }

    private static class HungerSnapshot {
        int foodLevel;
        float saturation;
        HungerSnapshot(ServerPlayerEntity player) {
            this.foodLevel = player.getHungerManager().getFoodLevel();
            this.saturation = player.getHungerManager().getSaturationLevel();
        }
    }

    private static class BeaconInfo {
        final boolean active; final int range; final boolean ward;
        BeaconInfo(boolean active, int range, boolean ward) { this.active = active; this.range = range; this.ward = ward; }
    }

    private static class PlayerBeaconState {
        final boolean active; final BlockPos beaconPos; final int range; final boolean ward; final RegistryKey<World> worldKey; long lastScanTick;
        PlayerBeaconState(boolean active, BlockPos beaconPos, int range, boolean ward, RegistryKey<World> worldKey) { this.active = active; this.beaconPos = beaconPos; this.range = range; this.ward = ward; this.worldKey = worldKey; }
        static PlayerBeaconState inactive() { return new PlayerBeaconState(false, BlockPos.ORIGIN, 0, false, World.OVERWORLD); }
    }
}
