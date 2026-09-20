package dev.elsebase;

import dev.elsebase.portal.Portals;
import dev.elsebase.structure.StructuralEditor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/** Reusable tools: structural actions use the player gaze; portal linking remains server-owned. */
public final class ToolItem extends Item {
    public enum Kind { ANCHOR, PORTAL, REMOVE, CREATE, PAINT, SCAN }
    private final Kind kind;
    public ToolItem(Kind kind) { super(new Item.Properties().stacksTo(1)); this.kind = kind; }
    public boolean structural() { return kind == Kind.REMOVE || kind == Kind.CREATE; }
    public boolean restores() { return kind == Kind.CREATE; }
    public Kind kind() { return kind; }
    public boolean themeTool() { return kind==Kind.CREATE || kind==Kind.PAINT || kind==Kind.SCAN; }
    private void edit(ServerPlayer player) {
        if (player.getCooldowns().isOnCooldown(this)) return;
        player.getCooldowns().addCooldown(this,5);
        if(themeTool() && player.isShiftKeyDown()) { dev.elsebase.template.TemplateServer.open(player); return; }
        if(kind==Kind.PAINT) dev.elsebase.template.TemplateServer.paint(player);
        else if(kind==Kind.SCAN) dev.elsebase.template.TemplateScanner.scan(player);
        else StructuralEditor.request(player,restores());
    }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, java.util.List<net.minecraft.network.chat.Component> lines, TooltipFlag flag) {
        lines.add(net.minecraft.network.chat.Component.translatable("tooltip.elsebase." + kind.name().toLowerCase(java.util.Locale.ROOT)));
        if (structural() || themeTool()) lines.add(net.minecraft.network.chat.Component.translatable("tooltip.elsebase.aim"));
    }
    @Override public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() instanceof ServerPlayer player) {
            if (structural() || themeTool()) { edit(player); return InteractionResult.SUCCESS; }
            if (player.getCooldowns().isOnCooldown(this)) return InteractionResult.FAIL;
            player.getCooldowns().addCooldown(this, 5);
            switch (kind) {
                case ANCHOR -> Portals.anchor(player, context.getClickedPos().relative(context.getClickedFace()));
                case PORTAL -> Portals.tool(player, context.getClickedPos(), context.getClickedPos().relative(context.getClickedFace()));
                case REMOVE, CREATE, PAINT, SCAN -> throw new IllegalStateException("Surface tools are handled before block tools");
            }
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }
    /** Structural tools take priority over opening a targeted machine/chest. */
    @Override public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        return structural() || themeTool() ? useOn(context) : InteractionResult.PASS;
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if ((structural() || themeTool()) && player instanceof ServerPlayer serverPlayer) edit(serverPlayer);
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }
}
