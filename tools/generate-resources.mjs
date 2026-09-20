/** Reproducible JSON assets and test fixture. Theme models reference Minecraft's shipped textures. */

import fs from 'node:fs';

import path from 'node:path';

import zlib from 'node:zlib';

const root = path.resolve('src/generated/resources');

function write(name, data) {

    const file = path.join(root, name);

    fs.mkdirSync(path.dirname(file), { recursive: true });

    fs.writeFileSync(file, Buffer.isBuffer(data) ? data : JSON.stringify(data, null, 2) + '\n');

}

for (const obsolete of ['assets/elsebase/models/item/structure_tool.json','data/elsebase/recipe/structure_tool.json'])

    fs.rmSync(path.join(root,obsolete),{force:true});

const names = ['floor', 'border', 'wall', 'ceiling', 'light'];

// Neutral fallback geometry; approved room appearances live in BuiltinThemes.
const fallbackTextures = ['smooth_stone','polished_andesite','stone_bricks','stone_bricks','sea_lantern'];

// smooth_quartz uses quartz_block_bottom in vanilla's model rather than a texture of its own.

function texture(name) { return 'minecraft:block/' + (name === 'smooth_quartz' ? 'quartz_block_bottom' : name); }

const allFaces = tex => Object.fromEntries(['north','south','east','west','up','down'].map(face=>[face,{texture:'#'+tex}]));

const box = (from,to,tex) => ({from,to,faces:allFaces(tex)});

// Full cubes retain culling; face strips add panel seams without extra world geometry.

function panelModel(main, trim) {

    const elements = [];

    for (const [face,y] of [['up',16],['down',0]]) {

        for (const [x,z,w,d,tex] of [[1,1,14,14,'main'],[0,0,16,1,'trim'],[0,15,16,1,'trim'],[0,1,1,14,'trim'],[15,1,1,14,'trim']])

            elements.push({from:[x,y,z],to:[x+w,y,z+d],faces:{[face]:{texture:'#'+tex,cullface:face}}});

    }

    for (const [lo,hi,tex] of [[0,1,'trim'],[1,15,'main'],[15,16,'trim']]) {

        elements.push({from:[0,lo,0],to:[16,hi,16],faces:Object.fromEntries(['north','south','east','west'].map(face=>[face,{texture:'#'+tex,cullface:face}]))});

    }

    return {textures:{main,trim,particle:main},elements};

}

{
    const textures = fallbackTextures;
    const prefix = '';

    names.forEach((name, index) => write(prefix + 'assets/elsebase/models/block/' + name + '.json',

        name === 'wall' || name === 'ceiling' || name === 'light' ? panelModel(texture(textures[index]),texture(textures[1])) :

        {parent:'minecraft:block/cube_bottom_top',textures:{top:texture(textures[index]),bottom:texture(name === 'floor' ? textures[3] : textures[index]),side:texture(textures[1])}}));

    // Static 1x2 doorway trim; a non-ticking renderer owns the live/fallback aperture.

    const rim = texture('copper_block');
    const inlay = texture('oxidized_copper');

    write(prefix+'assets/elsebase/models/block/portal_surface.json',{textures:{particle:'minecraft:block/nether_portal'},elements:[]});
    for (const half of ['lower','upper']) {

        const elements = [box([0,0,6],[1,16,10],'rim'),box([15,0,6],[16,16,10],'rim'),

            box([0.2,0,5.8],[0.8,16,6],'inlay'),box([15.2,0,5.8],[15.8,16,6],'inlay'),

            box([0.2,0,10],[0.8,16,10.2],'inlay'),box([15.2,0,10],[15.8,16,10.2],'inlay')];

        if (half === 'lower') elements.push(box([1,0,6],[15,0.5,10],'rim'));

        else elements.push(box([1,15,6],[15,16,10],'rim'),box([6.5,13.5,5.5],[9.5,15.5,6],'inlay'),box([6.5,13.5,10],[9.5,15.5,10.5],'inlay'));

        // The non-ticking portal surface renderer selects live view versus this resource-pack fallback.
        write(prefix+'assets/elsebase/models/block/portal_'+half+'.json',{render_type:'minecraft:translucent',textures:{rim,inlay,surface:'minecraft:block/nether_portal',particle:rim},elements});

    }

}

for (const name of names) {

    write('assets/elsebase/blockstates/' + name + '.json', {variants:{'':{model:'elsebase:block/' + name}}});

    write('assets/elsebase/models/item/' + name + '.json', {parent:'elsebase:block/' + name});

    write('data/elsebase/loot_table/blocks/' + name + '.json', {type:'minecraft:block',pools:[{

        rolls:1,entries:[{type:'minecraft:item',name:'elsebase:' + name}],

        conditions:[{condition:'minecraft:inverted',term:{condition:'minecraft:block_state_property',block:'elsebase:' + name,properties:{structural:'true'}}}]

    }]});

}

write('assets/elsebase/blockstates/portal.json',{variants:Object.fromEntries(['lower','upper'].flatMap(half=>[['south',0],['west',90],['north',180],['east',270]].map(([f,y])=>['facing='+f+',half='+half,{model:'elsebase:block/portal_'+half,y}])))});

// Remove the superseded opaque model when regenerating an existing checkout.

fs.rmSync(path.join(root,'assets/elsebase/models/block/portal.json'),{force:true});

write('assets/elsebase/models/block/anchor.json',{textures:{all:'minecraft:block/orange_glazed_terracotta',particle:'minecraft:block/orange_glazed_terracotta'},elements:[{from:[1,0,1],to:[15,0.5,15],faces:allFaces('all')}]});

write('assets/elsebase/blockstates/anchor.json',{variants:{'':{model:'elsebase:block/anchor'}}});

const toolParts = {

    anchor_tool:[box([7,1,7],[9,11,9],'rim'),box([6,11,6],[10,14,10],'inlay'),box([7,14,7],[9,16,9],'rim')],

    portal_tool:[box([7,1,7],[9,9,9],'rim'),box([9,2,7],[12,4,9],'rim'),box([5,9,7],[7,15,9],'rim'),box([9,9,7],[11,15,9],'rim'),box([7,14,7],[9,16,9],'inlay')],

    removal_tool:[box([7,1,7],[9,11,9],'rim'),box([3,10,6],[13,13,10],'rim'),box([4,13,7],[12,14,9],'inlay')],

    threshold_core:[box([4,4,6],[6,12,10],'rim'),box([10,4,6],[12,12,10],'rim'),box([6,3,6],[10,5,10],'rim'),box([6,11,6],[10,13,10],'rim'),box([7,6,7],[9,10,9],'inlay')]

};

toolParts.creation_tool = [box([7,1,7],[9,11,9],'rim'),box([3,10,6],[13,13,10],'inlay'),box([7,7,6],[9,16,10],'inlay')];

for (const [name,elements] of Object.entries(toolParts))

    write('assets/elsebase/models/item/'+name+'.json',{parent:'minecraft:block/block',textures:{rim:'minecraft:block/copper_block',inlay:'minecraft:block/oxidized_copper',particle:'minecraft:block/copper_block'},elements,

        display:{gui:{rotation:[15,-25,0],translation:[0,0,0],scale:[1,1,1]},firstperson_righthand:{rotation:[0,-90,25],translation:[1,3,1],scale:[0.6,0.6,0.6]},thirdperson_righthand:{rotation:[0,0,0],translation:[0,2,0],scale:[0.6,0.6,0.6]},ground:{translation:[0,3,0],scale:[0.4,0.4,0.4]}}});

const english={

    'elsebase.portal.loading':'Crossing the thresholdâ€¦',
    'key.categories.elsebase':'Elsebase','key.elsebase.portal':'Summon / recall instant portal',

    'item.elsebase.anchor_tool':'Spawn Anchor Tool','item.elsebase.portal_tool':'Portal Generator','item.elsebase.removal_tool':'Removal Tool','item.elsebase.creation_tool':'Creation Tool','item.elsebase.threshold_core':'Threshold Core',

    'block.elsebase.floor':'Workspace Floor','block.elsebase.border':'Cell Border','block.elsebase.wall':'Partition','block.elsebase.ceiling':'Ceiling Panel',

    'block.elsebase.light':'Decorative Light Panel','block.elsebase.portal':'Elsebase Threshold','block.elsebase.anchor':'Spawn Marker'

};

Object.assign(english, {
    'elsebase.configuration.title':'Elsebase settings',
    'elsebase.configuration.portals':'Portals', 'elsebase.configuration.chunkloading':'Chunk loading',
    'elsebase.configuration.structure':'Structural tools', 'elsebase.configuration.allocation':'Personal area allocation',
    'elsebase.configuration.world':'Dimension',
    'elsebase.configuration.preview':'Portal previews', 'elsebase.configuration.budget':'Shared preview budget',
    'elsebase.configuration.render':'Rendering', 'elsebase.configuration.previewQuality':'Portal preview quality',
    'elsebase.configuration.immersivePortalTransition':'Immersive portal transition',
    'elsebase.configuration.allowInstant':'Allow instant portals',
    'elsebase.configuration.maxPermanentPairsPerPlayer':'Permanent portal pairs per player',
    'elsebase.configuration.excludedExternalDimensions':'Blocked entrance dimensions',
    'elsebase.configuration.maxMirroredEndpointChunks':'Maximum mirrored portal chunks',
    'elsebase.configuration.maxChangedBlocksPerTick':'Structural edit budget per tick',
    'elsebase.configuration.radius':'Initial allocation radius',
    'elsebase.configuration.minimumSpacing':'Minimum player spacing',
    'elsebase.configuration.allowNaturalMobSpawning':'Allow natural mob spawning',
    'elsebase.configuration.darkness':'Dark dimension (disable uniform brightness)',


    'tooltip.elsebase.anchor':'Right-click a floor to move your personal spawn marker.',

    'tooltip.elsebase.portal':'Link inside, then outside. Sneak-use a portal to remove it.',

    'tooltip.elsebase.remove':'Right-click to remove the highlighted surface; floor borders remain.',
    'tooltip.elsebase.aim':'Look at a wall, floor or ceiling. No mode switching.',

    'tooltip.elsebase.create':'Right-click to build solid walls or restore floors and ceilings.',




});

write('assets/elsebase/lang/en_us.json',english);

write('assets/elsebase/lang/de_de.json',{...english,
    'elsebase.configuration.title':'Elsebase-Einstellungen',
    'elsebase.configuration.portals':'Portale', 'elsebase.configuration.chunkloading':'Chunkloading',
    'elsebase.configuration.structure':'Strukturwerkzeuge', 'elsebase.configuration.allocation':'Persönliche Startbereiche',
    'elsebase.configuration.world':'Dimension',
    'elsebase.configuration.preview':'Portalvorschau', 'elsebase.configuration.budget':'Gemeinsames Vorschau-Budget',
    'elsebase.configuration.render':'Darstellung', 'elsebase.configuration.previewQuality':'Qualität der Portalvorschau',
    'elsebase.configuration.immersivePortalTransition':'Immersiver Portalübergang',
    'elsebase.configuration.allowInstant':'Instant-Portale erlauben',
    'elsebase.configuration.maxPermanentPairsPerPlayer':'Permanente Portalpaare pro Spieler',
    'elsebase.configuration.excludedExternalDimensions':'Dimensionen ohne neue Eingänge',
    'elsebase.configuration.maxMirroredEndpointChunks':'Maximale Anzahl vorgeladener Portalchunks',
    'elsebase.configuration.maxChangedBlocksPerTick':'Blockänderungen pro Tick',
    'elsebase.configuration.radius':'Radius für Startbereiche',
    'elsebase.configuration.minimumSpacing':'Mindestabstand zwischen Spielern',
    'elsebase.configuration.allowNaturalMobSpawning':'Natürliches Mob-Spawning erlauben',
    'elsebase.configuration.darkness':'Dunkle Dimension (pauschale Helligkeit aus)',


    'tooltip.elsebase.anchor':'Rechtsklick auf einen Boden: persönlichen Spawn-Anker versetzen.',

    'tooltip.elsebase.portal':'Innen, dann außen verknüpfen. Portal mit Shift-Rechtsklick entfernen.',

    'tooltip.elsebase.remove':'Rechtsklick: Markierte Fläche entfernen; Bodenränder bleiben erhalten.',
    'tooltip.elsebase.aim':'Wand, Boden oder Decke ansehen. Kein Moduswechsel nötig.',

    'tooltip.elsebase.create':'Rechtsklick: geschlossene Wände bauen oder Böden und Decken herstellen.',




    'elsebase.portal.loading':'Durchgang wird vorbereitetâ€¦',
    'key.elsebase.portal':'Instant-Portal rufen / versetzen',

    'item.elsebase.anchor_tool':'Spawn-Ankerwerkzeug','item.elsebase.portal_tool':'Portalgenerator','item.elsebase.removal_tool':'Löschwerkzeug','item.elsebase.creation_tool':'Herstellwerkzeug','item.elsebase.threshold_core':'Portalkern',

    'block.elsebase.floor':'Werkraumboden','block.elsebase.border':'Zellgrenze','block.elsebase.wall':'Trennwand','block.elsebase.ceiling':'Deckenpaneel',

    'block.elsebase.light':'Dekoratives Lichtpaneel','block.elsebase.portal':'Elsebase-Portal','block.elsebase.anchor':'Spawn-Indikator'

});

write('data/elsebase/dimension/backdoor.json',{type:'elsebase:backdoor',generator:{type:'elsebase:rooms',biome:'elsebase:backdoor'}});

write('data/elsebase/dimension_type/backdoor.json',{

    ultrawarm:false,natural:false,piglin_safe:false,respawn_anchor_works:false,bed_works:false,has_raids:false,

    has_skylight:false,has_ceiling:true,coordinate_scale:1,ambient_light:0,fixed_time:18000,

    logical_height:128,min_y:0,height:128,infiniburn:'#minecraft:infiniburn_overworld',

    effects:'elsebase:backdoor',monster_spawn_block_light_limit:0,monster_spawn_light_level:0

});

write('data/elsebase/worldgen/biome/backdoor.json',{

    has_precipitation:false,temperature:0.8,downfall:0,

    effects:{fog_color:2236962,water_color:4159204,water_fog_color:329011,sky_color:0},

    spawners:{monster:[{type:'minecraft:zombie',weight:50,minCount:1,maxCount:3},{type:'minecraft:skeleton',weight:30,minCount:1,maxCount:2},{type:'minecraft:spider',weight:20,minCount:1,maxCount:2}]},

    spawn_costs:{},carvers:{},features:[]

});

function recipe(name,pattern,key) {

    write('data/elsebase/recipe/'+name+'.json',{type:'minecraft:crafting_shaped',category:'misc',pattern,key:Object.fromEntries(Object.entries(key).map(([k,v])=>[k,{item:v}])),result:{id:'elsebase:'+name,count:1}});

}

recipe('threshold_core',['ICI','CRC','ICI'],{I:'minecraft:iron_ingot',C:'minecraft:copper_ingot',R:'minecraft:redstone'});

recipe('portal_tool',[' C ',' IR','I  '],{I:'minecraft:iron_ingot',C:'minecraft:copper_ingot',R:'minecraft:redstone'});

recipe('creation_tool',[' C ','ICI',' S '],{I:'minecraft:iron_ingot',C:'minecraft:copper_ingot',S:'minecraft:stick'});

recipe('anchor_tool',['  C',' I ','S  '],{I:'minecraft:iron_ingot',C:'minecraft:copper_ingot',S:'minecraft:stick'});

recipe('removal_tool',['III',' C ',' S '],{I:'minecraft:iron_ingot',C:'minecraft:copper_ingot',S:'minecraft:stick'});

for (const [tag,blocks] of Object.entries({floor_blocks:['floor','border'],partition_blocks:['wall'],ceiling_blocks:['ceiling','light']}))

    write('data/elsebase/tags/block/'+tag+'.json',{replace:false,values:blocks.map(b=>'elsebase:'+b)});

write('data/minecraft/tags/block/mineable/pickaxe.json',{replace:false,values:names.map(n=>'elsebase:'+n)});

write('pack.mcmeta',{pack:{pack_format:34,description:'Elsebase resources'}});



// GameTestServer bakes only the flat preset, ignoring ordinary added dimension entries.

// This pack is registered only in the dedicated GameTest launch, never in ordinary worlds.

write('gametest_pack/pack.mcmeta',{pack:{pack_format:48,description:'Elsebase GameTest dimension fixture'}});
write('gametest_pack/data/elsebase/tags/block/template_material_blacklist.json',{replace:false,values:['minecraft:emerald_block']});

write('gametest_pack/data/minecraft/worldgen/world_preset/flat.json',{dimensions:{

    'minecraft:overworld':{type:'minecraft:overworld',generator:{type:'minecraft:flat',settings:{biome:'minecraft:plains',features:false,lakes:false,layers:[{block:'minecraft:bedrock',height:1},{block:'minecraft:dirt',height:2},{block:'minecraft:grass_block',height:1}],structure_overrides:[]}}},

    'minecraft:the_nether':{type:'minecraft:the_nether',generator:{type:'minecraft:noise',biome_source:{type:'minecraft:multi_noise',preset:'minecraft:nether'},settings:'minecraft:nether'}},

    'minecraft:the_end':{type:'minecraft:the_end',generator:{type:'minecraft:noise',biome_source:{type:'minecraft:the_end'},settings:'minecraft:end'}},

    'elsebase:backdoor':{type:'elsebase:backdoor',generator:{type:'elsebase:rooms',biome:'elsebase:backdoor'}}

}});



// An empty 8x8x8 vanilla structure for development-only GameTests; no game assets are copied.

const utf = value => {const b=Buffer.from(value);const n=Buffer.alloc(2);n.writeUInt16BE(b.length);return Buffer.concat([n,b]);};

const int = value => {const b=Buffer.alloc(4);b.writeInt32BE(value);return b;};

const named=(type,name,data)=>Buffer.concat([Buffer.from([type]),utf(name),data]);

const list=(type,entries)=>Buffer.concat([Buffer.from([type]),int(entries.length),...entries]);

const fixture=Buffer.concat([Buffer.from([10]),utf(''),named(3,'DataVersion',int(3955)),

    named(9,'size',list(3,[int(8),int(8),int(8)])),

    named(9,'palette',list(10,[Buffer.concat([named(8,'Name',utf('minecraft:air')),Buffer.from([0])])])),

    named(9,'blocks',list(10,[])),named(9,'entities',list(10,[])),Buffer.from([0])]);

write('data/elsebase/structure/empty.nbt',zlib.gzipSync(fixture));

console.log('Generated Elsebase fallback resources, recipes and GameTest fixture.');




// Independently addressable template materials: every palette can coexist in the same chunk.
// Technical material checks are authoritative; packs may explicitly block additional materials.
write('data/elsebase/tags/block/template_material_blacklist.json',{replace:false,values:[]});
write('assets/elsebase/models/item/scanner.json',{parent:'minecraft:item/handheld',textures:{layer0:'elsebase:item/scanner'}});
write('data/elsebase/recipe/scanner.json',{type:'minecraft:crafting_shaped',pattern:[' GI',' S ',' S '],key:{G:{item:'minecraft:glass'},I:{item:'minecraft:iron_ingot'},S:{item:'minecraft:stick'}},result:{id:'elsebase:scanner',count:1}});
for(const locale of ['en_us','de_de']) {
 const file=`assets/elsebase/lang/${locale}.json`; const lang=JSON.parse(fs.readFileSync(path.join(root,file),'utf8'));
 lang['item.elsebase.scanner']=locale==='de_de'?'Vorlagen-Scanner':'Template Scanner';
 lang['tooltip.elsebase.scan']=locale==='de_de'?'Shift-Rechtsklick: Theme wählen und speichern. Rechtsklick: angeschaute Raumfläche in den Puffer scannen.':'Shift-right-click: choose and save a theme. Right-click: scan the aimed room surface into the buffer.';
 lang['item.elsebase.paint_tool']=locale==='de_de'?'Malwerkzeug':'Paint Tool';
 lang['tooltip.elsebase.paint']=locale==='de_de'?'Rechtsklick: Raumfläche umfärben. Shift-Rechtsklick: Theme wählen oder als Standard setzen.':'Right-click: repaint a room surface. Shift-right-click: choose a theme or set your default.';
 lang['tooltip.elsebase.create']+=(locale==='de_de'?' Shift-Rechtsklick: Bau-Theme wählen.':' Shift-right-click: select construction theme.');
 write(file,lang);
}

write('assets/elsebase/models/item/paint_tool.json',{parent:'minecraft:item/handheld',textures:{layer0:'elsebase:item/paint_tool'}});
write('data/elsebase/recipe/paint_tool.json',{type:'minecraft:crafting_shaped',pattern:[' WW',' SC',' S '],key:{W:{item:'minecraft:white_wool'},C:{item:'minecraft:copper_ingot'},S:{item:'minecraft:stick'}},result:{id:'elsebase:paint_tool',count:1}});

// Authored 32px item silhouettes: a turquoise roller and a copper optical scanner.
function toolTexture(name,draw) {
    const pixels=Buffer.alloc(32*32*4);
    const rect=(x,y,w,h,color)=>{ for(let j=y;j<y+h;j++) for(let i=x;i<x+w;i++) {const n=(j*32+i)*4; pixels[n]=color>>16&255; pixels[n+1]=color>>8&255; pixels[n+2]=color&255; pixels[n+3]=255;} };
    draw(rect);
    const crc=buffer=>{let n=0xffffffff;for(const b of buffer){n^=b;for(let k=0;k<8;k++) n=(n>>>1)^((n&1)?0xedb88320:0);}return (n^0xffffffff)>>>0;};
    const chunk=(type,data)=>{const t=Buffer.from(type);const n=Buffer.alloc(4),c=Buffer.alloc(4);n.writeUInt32BE(data.length);c.writeUInt32BE(crc(Buffer.concat([t,data])));return Buffer.concat([n,t,data,c]);};
    const header=Buffer.alloc(13);header.writeUInt32BE(32,0);header.writeUInt32BE(32,4);header[8]=8;header[9]=6;
    const rows=Buffer.alloc(32*129);for(let y=0;y<32;y++) pixels.copy(rows,y*129+1,y*128,(y+1)*128);
    write('assets/elsebase/textures/item/'+name+'.png',Buffer.concat([Buffer.from([137,80,78,71,13,10,26,10]),chunk('IHDR',header),chunk('IDAT',zlib.deflateSync(rows)),chunk('IEND',Buffer.alloc(0))]));
}
toolTexture('paint_tool',r=>{
    r(5,3,23,9,0x182c32);r(7,4,18,7,0x2a9e9b);r(8,4,16,2,0xa5f5d4);r(7,9,18,2,0x155d68);
    r(26,10,2,7,0xd29761);r(15,15,13,2,0xd29761);r(14,17,3,4,0x774732);
    r(12,20,7,11,0x182c32);r(13,21,5,9,0xb56842);r(14,21,2,8,0xedb379);r(13,27,5,2,0x2a9e9b);
});
toolTexture('scanner',r=>{
    r(10,19,10,12,0x182c32);r(12,20,6,10,0xa86642);r(13,21,2,8,0xe0a56b);
    r(4,3,23,18,0x182c32);r(5,4,21,16,0xa86642);r(6,4,18,2,0xedbd7d);
    r(7,7,16,10,0x153d4b);r(9,8,12,8,0x258c9e);r(11,9,8,6,0x65d2cf);
    r(14,8,2,8,0xd5ffe5);r(10,11,10,2,0xd5ffe5);r(7,18,3,1,0x65d2cf);r(20,18,3,1,0x65d2cf);
});
