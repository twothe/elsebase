package dev.elsebase;

import dev.elsebase.portal.PortalBlock;
import dev.elsebase.world.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;

/** Blocks, tools and codecs; ordinary structural items deliberately place unprotected states. */
public final class Content {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Elsebase.ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Elsebase.ID);
    private static final DeferredRegister<net.minecraft.world.level.block.entity.BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,Elsebase.ID);
    public static final DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>,net.minecraft.world.level.block.entity.BlockEntityType<dev.elsebase.portal.PortalSurface>> PORTAL_SURFACE =
            BLOCK_ENTITIES.register("portal_surface",() -> net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(dev.elsebase.portal.PortalSurface::new,Content.PORTAL.get()).build(null));
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Elsebase.ID);
    private static final DeferredRegister<com.mojang.serialization.MapCodec<? extends net.minecraft.world.level.chunk.ChunkGenerator>> GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, Elsebase.ID);
    public static final DeferredBlock<StructuralBlock> FLOOR = structural("floor");
    public static final DeferredBlock<StructuralBlock> BORDER = structural("border");
    public static final DeferredBlock<StructuralBlock> WALL = structural("wall");
    public static final DeferredBlock<StructuralBlock> CEILING = structural("ceiling");
    public static final DeferredBlock<StructuralBlock> LIGHT = structural("light");
    public static final DeferredBlock<PortalBlock> PORTAL = BLOCKS.register("portal", () -> new PortalBlock(
            BlockBehaviour.Properties.of().strength(-1, 3600000).noCollission().noOcclusion().noLootTable().pushReaction(PushReaction.BLOCK)));
    public static final DeferredBlock<Block> ANCHOR = BLOCKS.register("anchor", () -> new AnchorBlock(
            BlockBehaviour.Properties.of().strength(-1, 3600000).noCollission().noOcclusion().noLootTable().pushReaction(PushReaction.BLOCK)));
    public static final DeferredItem<Item> CORE = ITEMS.registerSimpleItem("threshold_core");
    public static final DeferredItem<ToolItem> ANCHOR_TOOL = ITEMS.register("anchor_tool", () -> new ToolItem(ToolItem.Kind.ANCHOR));
    public static final DeferredItem<ToolItem> PORTAL_TOOL = ITEMS.register("portal_tool", () -> new ToolItem(ToolItem.Kind.PORTAL));
    public static final DeferredItem<ToolItem> REMOVAL_TOOL = ITEMS.register("removal_tool", () -> new ToolItem(ToolItem.Kind.REMOVE));
    public static final DeferredItem<ToolItem> CREATION_TOOL = ITEMS.register("creation_tool", () -> new ToolItem(ToolItem.Kind.CREATE));
    static {
        GENERATORS.register("rooms", () -> RoomGenerator.CODEC);
        TABS.register("elsebase", () -> CreativeModeTab.builder().title(Component.literal("Elsebase"))
                .icon(() -> new ItemStack(PORTAL_TOOL.get())).displayItems((parameters, out) -> {
                    out.accept(ANCHOR_TOOL.get()); out.accept(PORTAL_TOOL.get()); out.accept(REMOVAL_TOOL.get()); out.accept(CREATION_TOOL.get()); out.accept(CORE.get());
                    for (var block : new DeferredBlock<?>[]{FLOOR, BORDER, WALL, CEILING, LIGHT}) out.accept(block.get());
                }).build());
    }
    private static DeferredBlock<StructuralBlock> structural(String name) {
        var block = BLOCKS.register(name, () -> new StructuralBlock(BlockBehaviour.Properties.of().strength(2, 6)
                .lightLevel(state -> 0)));
        ITEMS.registerSimpleBlockItem(name, block);
        return block;
    }
    public static BlockState structure(RoomLayout.Material material) {
        if (material == RoomLayout.Material.AIR) return Blocks.AIR.defaultBlockState();
        if (material == RoomLayout.Material.BEDROCK) return Blocks.BEDROCK.defaultBlockState();
        var block = switch (material) {
            case FLOOR -> FLOOR; case BORDER -> BORDER; case WALL -> WALL;
            case CEILING -> CEILING; case LIGHT -> LIGHT; default -> throw new IllegalArgumentException("Unknown material");
        };
        return block.get().defaultBlockState().setValue(StructuralBlock.STRUCTURAL, true);
    }
    public static void register(IEventBus bus) { BLOCKS.register(bus); ITEMS.register(bus); TABS.register(bus); GENERATORS.register(bus); BLOCK_ENTITIES.register(bus); }
    private Content() {}
}
