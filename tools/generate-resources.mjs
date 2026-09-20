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

const themes = {

    quiet_workshop: ['smooth_stone', 'polished_andesite', 'smooth_quartz', 'iron_block', 'sea_lantern'],

    arcane_archive: ['dark_oak_planks', 'chiseled_stone_bricks', 'bookshelf', 'deepslate_tiles', 'ochre_froglight_side'],

    verdant_cloister: ['mossy_stone_bricks', 'polished_andesite', 'mud_bricks', 'moss_block', 'verdant_froglight_side'],

    astral_observatory: ['deepslate_tiles', 'purpur_block', 'blue_terracotta', 'black_concrete', 'pearlescent_froglight_side'],

    deepstone_halls: ['polished_deepslate', 'chiseled_deepslate', 'deepslate_bricks', 'tuff_bricks', 'ochre_froglight_side'],

    porcelain_sanctuary: ['smooth_quartz', 'light_gray_concrete', 'white_concrete', 'quartz_block_bottom', 'sea_lantern'],

    service_layer: ['smooth_stone', 'yellow_concrete', 'light_gray_concrete', 'iron_block', 'sea_lantern']

};

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

for (const [theme, textures] of Object.entries(themes)) {

    const prefix = theme === 'quiet_workshop' ? '' : 'resourcepacks/' + theme + '/';

    if (prefix) write(prefix + 'pack.mcmeta', {pack:{pack_format:34,description:'Elsebase — ' + theme.replaceAll('_',' ')}});

    names.forEach((name, index) => write(prefix + 'assets/elsebase/models/block/' + name + '.json',

        name === 'wall' || name === 'ceiling' || name === 'light' ? panelModel(texture(textures[index]),texture(textures[1])) :

        {parent:'minecraft:block/cube_bottom_top',textures:{top:texture(textures[index]),bottom:texture(name === 'floor' ? textures[3] : textures[index]),side:texture(textures[1])}}));

    // Static 1x2 doorway trim; a non-ticking renderer owns the live/fallback aperture.

    const magical = ['arcane_archive','verdant_cloister','astral_observatory','deepstone_halls'].includes(theme);

    const rim = texture(magical ? textures[1] : 'copper_block');

    const inlay = texture(magical ? textures[4] : 'oxidized_copper');

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

    'elsebase.portal.loading':'Crossing the threshold…',
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




    'elsebase.portal.loading':'Durchgang wird vorbereitet…',
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

console.log('Generated Elsebase resources, seven theme palettes, recipes and GameTest fixture.');



