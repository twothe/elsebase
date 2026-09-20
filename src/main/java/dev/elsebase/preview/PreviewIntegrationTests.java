package dev.elsebase.preview;

import dev.elsebase.*;
import dev.elsebase.portal.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Server-side regression of production snapshot bounds/codecs, authorization and frame mapping. */
@net.neoforged.neoforge.gametest.PrefixGameTestTemplate(false)
public final class PreviewIntegrationTests {
    private PreviewIntegrationTests() {}
    @GameTest(template="empty",templateNamespace=Elsebase.ID)
    public static void snapshotsAndTransforms(GameTestHelper helper) {
        var level=helper.getLevel(); var p=helper.absolutePos(new BlockPos(1,2,1));
        var origin=new BlockPos(p.getX()&~15,p.getY()&~15,p.getZ()&~15);
        level.setBlockAndUpdate(p,Blocks.DIAMOND_BLOCK.defaultBlockState());
        var snapshot=PreviewServer.capture(level,7,origin);
        helper.assertTrue(snapshot!=null,"Loaded scene snapshot exists");
        int index=((p.getY()&15)*16+(p.getZ()&15))*16+(p.getX()&15);
        helper.assertTrue(net.minecraft.world.level.block.Block.stateById(snapshot.states()[index]).is(Blocks.DIAMOND_BLOCK),"Snapshot reads actual block data");
        var buffer=new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),level.registryAccess());
        try {
            PreviewProtocol.Section.CODEC.encode(buffer,snapshot);
            helper.assertTrue(buffer.readableBytes()<32768,"Section packet has a bounded small encoded size");
            var decoded=PreviewProtocol.Section.CODEC.decode(buffer);
            helper.assertTrue(Arrays.equals(decoded.states(),snapshot.states()) && Arrays.equals(decoded.light(),snapshot.light()) && Arrays.equals(decoded.biomes(),snapshot.biomes()),"Codec preserves blocks/light/biomes");
        } finally { buffer.release(); }
        level.setBlockAndUpdate(p,Blocks.GOLD_BLOCK.defaultBlockState());
        helper.assertTrue(!Arrays.equals(snapshot.states(),PreviewServer.capture(level,7,origin).states()),"Non-player block update is observable");
        var absent=new BlockPos(12000000,64,12000000);
        helper.assertTrue(PreviewServer.capture(level,7,absent)==null && level.getChunkSource().getChunkNow(absent.getX()>>4,absent.getZ()>>4)==null,"Preview never loads absent chunks");
        for(Direction sourceFacing : Direction.Plane.HORIZONTAL) for(Direction targetFacing : Direction.Plane.HORIZONTAL) {
            var source=new Endpoint(Level.OVERWORLD,new BlockPos(-120000,65,-16),sourceFacing);
            var target=new Endpoint(Elsebase.DIMENSION,new BlockPos(130000,65,130000),targetFacing);
            var point=source.center().add(.2,1.62,3.5);
            var mapped=PortalView.transform(point,source,target);
            helper.assertTrue(PortalView.transform(mapped,target,source).distanceToSqr(point)<1e-8,"All cardinal frame mappings round-trip at large coordinates");
            var front=source.center().add(Vec3.atLowerCornerOf(sourceFacing.getNormal()));
            helper.assertTrue(PortalView.transform(front,source,target).distanceToSqr(target.center().subtract(Vec3.atLowerCornerOf(targetFacing.getNormal())))<1e-8,"Front camera maps behind destination plane");
        }
        helper.succeed();
    }
    @SuppressWarnings("removal")
    @GameTest(template="empty",templateNamespace=Elsebase.ID)
    public static void visibilityAndAnchor(GameTestHelper helper) {
        var player=helper.makeMockServerPlayerInLevel(); var level=helper.getLevel();
        var source=new Endpoint(level.dimension(),helper.absolutePos(new BlockPos(2,2,2)),Direction.NORTH);
        var target=new Endpoint(Elsebase.DIMENSION,new BlockPos(9000,65,9000),Direction.SOUTH);
        player.teleportTo(level,source.center().x,source.center().y,source.center().z+2,180,0);
        level.setBlockAndUpdate(source.position(),PortalBlock.stateAt(source,source.position()));
        var privatePair=new PortalPair(UUID.randomUUID(),UUID.randomUUID(),false,target,source);
        helper.assertTrue(!PreviewServer.eligible(player,privatePair,source),"Foreign personal base preview is denied");
        var owned=new PortalPair(UUID.randomUUID(),player.getUUID(),false,target,source);
        helper.assertTrue(PreviewServer.eligible(player,owned,source),"Owner can preview nearby doorway");
        var data=WorldState.get(player.server); var home=data.home(player.server,player.getUUID());
        helper.assertTrue(PortalView.target(data,owned,source).equals(home.reference()),"Personal preview resolves anchor rather than recalled endpoint");
        var moved=new PortalPair(owned.id(),owned.owner(),false,target,new Endpoint(level.dimension(),source.position().east(3),Direction.NORTH));
        helper.assertTrue(!PreviewServer.eligible(player,moved,source),"Old source becomes ineligible immediately after recall");
        level.removeBlock(source.position(),false);
        helper.assertTrue(!PreviewServer.eligible(player,owned,source),"Removed source cannot keep a preview subscription");
        player.connection.disconnect(net.minecraft.network.chat.Component.literal("GameTest completed")); helper.succeed();
    }
}
