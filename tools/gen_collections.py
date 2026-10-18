# Regenerates src/main/resources/packmixer/collections.json. Edit and re-run (python3 tools/gen_collections.py) to add or split collections.
import json
G=[]
def S(n,**k): return dict(name=n,**k)
def C(group,id,name,prev,globs,tint=(),styles=(),cube=None,side=None,alt=None):
    d=dict(id=id,name=name,group=group,globs=list(globs),tint=list(tint),styles=list(styles))
    for k,v in(("preview",prev),("cube",cube),("side",side),("alt",alt)):
        if v: d[k]=v
    G.append(d)
B="textures/block/";I="textures/item/";H="textures/gui/sprites/hud/";E="textures/entity/";EQ=E+"equipment/";SP="textures/gui/sprites/"
RUBY=S("Ruby",hue=.45);GOLD=S("Gold",hue=.35,sat=1.2);VIO=S("Violet",hue=.75);FROST=S("Frost",hue=.5);DARK=S("Dark",bri=.6)
moons=["full_moon","waning_gibbous","third_quarter","waning_crescent","new_moon","waxing_crescent","first_quarter","waxing_gibbous"]
def blk(group,id,name,tex,globs=None,tint=None,styles=(),side=None):
    C(group,id,name,B+tex,globs or [B+tex],tint if tint is not None else [B+tex],styles,cube="block",side=side)
# ---- PvP ----
P="PvP"
C(P,"crystal","Crystal",E+"end_crystal/end_crystal.png",[E+"end_crystal/**"],[E+"end_crystal/end_crystal.png",E+"end_crystal/end_crystal_beam.png"],[S("Aqua",hue=.7),S("Lavender",hue=.95,sat=.6,bri=1.15),S("Blueberry",hue=.85),S("Ember",hue=.25)],cube="crystal")
C(P,"anchor","Anchor",B+"respawn_anchor_top.png",[B+"respawn_anchor_*","models/block/respawn_anchor*","blockstates/respawn_anchor.json"],[B+"respawn_anchor_%s.png"%x for x in["top","top_off","bottom","side0","side1","side2","side3","side4"]],[S("Ember",hue=.05),S("Void",hue=.6,bri=.85),S("Mono",sat=0)],cube="block",side=B+"respawn_anchor_side4.png")
blk(P,"glowstone","Glowstone","glowstone.png",styles=[S("Ember",hue=.05),S("Ice",hue=.5),S("Violet",hue=.7)])
blk(P,"obsidian","Obsidian","obsidian.png",styles=[S("Void",hue=.75,bri=1.4),S("Ember",hue=.05,bri=1.6),S("Ice",hue=.55,bri=1.8)])
blk(P,"crying","Crying Obsidian","crying_obsidian.png",styles=[S("Ember",hue=.5),S("Aqua",hue=.7)])
C(P,"totem","Totem",I+"totem_of_undying.png",[I+"totem_of_undying.png"],[I+"totem_of_undying.png"],[S("Frost",hue=.35),S("Crimson",hue=.67),VIO])
C(P,"fire","Fire",B+"fire_1.png",[B+"fire_*.png"],[B+"fire_0.png",B+"fire_1.png"],[S("Blue",hue=.58),S("Purple",hue=.75),S("Green",hue=.3)])
C(P,"soulfire","Soul Fire",B+"soul_fire_1.png",[B+"soul_fire_*.png"],[B+"soul_fire_0.png",B+"soul_fire_1.png"],[S("Red",hue=.5),S("Gold",hue=.3)])
blk(P,"stone","Stone","stone.png",[B+"stone.png",B+"smooth_stone*.png"],[B+"stone.png"],[S("Black",bri=.35),S("Light",sat=0,bri=1.3)])
blk(P,"cobble","Cobblestone & Bricks","cobblestone.png",[B+"cobblestone.png",B+"stone_bricks.png",B+"mossy_*.png"],[B+"cobblestone.png",B+"stone_bricks.png"],[S("Black",bri=.35),S("Light",sat=0,bri=1.3)])
blk(P,"deepslate","Deepslate","deepslate.png",[B+"*deepslate*.png"],[B+"deepslate.png",B+"deepslate_top.png",B+"cobbled_deepslate.png"],[S("Dark",bri=.6),S("Slate",hue=.6,sat=.5)])
blk(P,"snow","Snow","snow.png",[B+"snow.png",B+"grass_block_snow.png",B+"powder_snow*.png"],[B+"snow.png",B+"powder_snow.png"],[S("Ice",mul="C8E6FF"),S("Dim",bri=.8)])
SUN="textures/environment/celestial/sun.png"
C(P,"sunmoon","Sun & Moon",SUN,["textures/environment/celestial/**","textures/environment/sun.png","textures/environment/moon_phases.png"],[SUN]+["textures/environment/celestial/moon/%s.png"%m for m in moons],[S("Dusk",mul="FFC4A8"),S("Rose",hue=.9,mul="FFB3D1"),S("Midnight",bri=.7,mul="9FB4FF")],alt="textures/environment/sun.png")
C(P,"clouds","Clouds","textures/environment/clouds.png",["textures/environment/clouds.png"],["textures/environment/clouds.png"],[S("Dusk",mul="FFC4A8"),S("Rose",mul="FFB3D1"),S("Ice",mul="C8E6FF"),S("Dark",bri=.55)])
# ---- Items ----
T="Items"
for k,n in(("sword","Sword"),("axe","Axe"),("pickaxe","Pickaxe"),("shovel","Shovel"),("hoe","Hoe")):
    C(T,k,n,I+"netherite_%s.png"%k,[I+"*_%s.png"%k],[I+"%s_%s.png"%(m,k) for m in("netherite","diamond")],[RUBY,GOLD,VIO])
C(T,"bow","Bow",I+"bow.png",[I+"bow*.png"],[I+"bow.png",I+"bow_pulling_0.png",I+"bow_pulling_1.png",I+"bow_pulling_2.png"],[DARK,VIO])
C(T,"crossbow","Crossbow",I+"crossbow_standby.png",[I+"crossbow*.png"],[I+"crossbow_standby.png"],[DARK,VIO])
C(T,"arrow","Arrows",I+"arrow.png",[I+"arrow.png",I+"spectral_arrow.png",I+"tipped_arrow*.png"],[I+"arrow.png"],[RUBY,VIO])
C(T,"shield","Shield",E+"shield/shield_base_nopattern.png",[E+"shield/**",E+"shield*.png"],[E+"shield/shield_base_nopattern.png"],[DARK,VIO])
C(T,"trident","Trident",I+"trident.png",[I+"trident*.png",E+"trident*.png"],[I+"trident.png"],[RUBY,GOLD])
C(T,"mace","Mace",I+"mace.png",[I+"mace.png"],[I+"mace.png"],[RUBY,VIO])
C(T,"elytra","Elytra",I+"elytra.png",[I+"elytra.png",E+"elytra.png",E+"equipment/wings/**"],[I+"elytra.png"],[RUBY,FROST])
C(T,"rod","Fishing Rod",I+"fishing_rod.png",[I+"fishing_rod*.png",I+"carrot_on_a_stick.png"])
C(T,"flint","Flint & Steel / Shears",I+"flint_and_steel.png",[I+"flint_and_steel.png",I+"shears.png"])
C(T,"gapple","Golden Apple",I+"golden_apple.png",[I+"golden_apple.png",I+"golden_carrot.png"],[I+"golden_apple.png"],[FROST,S("Ruby",hue=.9)])
C(T,"egapple","Enchanted Golden Apple",I+"enchanted_golden_apple.png",[I+"enchanted_golden_apple.png"],[I+"enchanted_golden_apple.png"],[FROST,S("Ruby",hue=.9)])
C(T,"pearl","Ender Pearl",I+"ender_pearl.png",[I+"ender_pearl.png"],[I+"ender_pearl.png"],[S("Ruby",hue=.75),S("Gold",hue=.45)])
C(T,"xp","Experience Bottle",I+"experience_bottle.png",[I+"experience_bottle.png"],[I+"experience_bottle.png"],[S("Ruby",hue=.6),VIO])
C(T,"potion","Potions",I+"splash_potion.png",[I+"*potion*.png",I+"glass_bottle.png"],[I+"splash_potion.png",I+"potion.png"],[RUBY,VIO])
C(T,"armorworn","Armor (worn)",I+"netherite_chestplate.png",[EQ+"humanoid/**",EQ+"humanoid_leggings/**","textures/models/armor/**"],[EQ+"humanoid/diamond.png",EQ+"humanoid/netherite.png",EQ+"humanoid_leggings/diamond.png",EQ+"humanoid_leggings/netherite.png"],[S("Crimson",hue=.45),S("Midnight",bri=.6)])
for k,n in(("helmet","Helmets"),("chestplate","Chestplates"),("leggings","Leggings"),("boots","Boots")):
    C(T,k,n,I+"netherite_%s.png"%k,[I+"*_%s.png"%k],[I+"%s_%s.png"%(m,k) for m in("netherite","diamond")],[S("Crimson",hue=.45),S("Midnight",bri=.6)])
# ---- Blocks ----
K="Blocks"
blk(K,"water","Water","water_still.png",[B+"water_*.png"],[])
C(K,"lava","Lava",B+"lava_still.png",[B+"lava_*.png"],[B+"lava_still.png",B+"lava_flow.png"],[S("Blue",hue=.55),S("Green",hue=.3)],cube="block")
blk(K,"grass","Grass & Dirt","dirt.png",[B+"grass_block_*.png",B+"dirt.png",B+"podzol_*.png",B+"mycelium_*.png"],[])
blk(K,"planks","Planks","oak_planks.png",[B+"*_planks.png"],[])
blk(K,"logs","Logs","oak_log.png",[B+"*_log.png",B+"*_log_top.png"],[],side=B+"oak_log.png")
blk(K,"ores","Ores","diamond_ore.png",[B+"*_ore.png",B+"ancient_debris_*.png"],[])
blk(K,"sand","Sand & Gravel","sand.png",[B+"sand.png",B+"red_sand.png",B+"gravel.png"],[])
blk(K,"glass","Glass","glass.png",[B+"glass.png",B+"*_stained_glass.png"],[])
blk(K,"nether","Netherrack","netherrack.png",[B+"netherrack.png",B+"nether_bricks.png",B+"soul_sand.png",B+"soul_soil.png",B+"basalt_*.png",B+"blackstone*.png"],[])
blk(K,"leaves","Leaves","oak_leaves.png",[B+"*_leaves.png"],[])
blk(K,"bedrock","Bedrock","bedrock.png",[B+"bedrock.png"],[B+"bedrock.png"],[DARK,VIO])
blk(K,"endstone","End Stone","end_stone.png",[B+"end_stone*.png",B+"purpur_*.png"],[])
blk(K,"cobweb","Cobweb","cobweb.png",[B+"cobweb.png"],[])
blk(K,"tnt","TNT","tnt_side.png",[B+"tnt_*.png"],[],side=B+"tnt_side.png")
# ---- World ----
W="World"
C(W,"endsky","End Sky","textures/environment/end_sky.png",["textures/environment/end_sky.png","textures/environment/celestial/end_flash.png","textures/environment/end_flash.png"],["textures/environment/end_sky.png"],[S("Violet",hue=.1),S("Dark",bri=.5)])
C(W,"weather","Rain & Snow","textures/environment/rain.png",["textures/environment/rain.png","textures/environment/snow.png"],["textures/environment/rain.png"],[S("Ice",mul="C8E6FF")])
C(W,"skybox","Skybox (OptiFine)",None,["optifine/sky/**","mcpatcher/sky/**"])
C(W,"particles","Particles","textures/particle/flash.png",["textures/particle/**"])
# ---- HUD & GUI ----
U="HUD & GUI"
C(U,"hotbar","Hotbar",H+"hotbar.png",[H+"hotbar.png",H+"hotbar_offhand_*.png","textures/gui/widgets.png"],[H+"hotbar.png"],[S("Violet",mul="C9B6FF"),S("Mint",mul="B6FFD6"),S("Dark",bri=.5)])
C(U,"hotbarsel","Hotbar Selection",H+"hotbar_selection.png",[H+"hotbar_selection.png"],[H+"hotbar_selection.png"],[S("Violet",mul="C9B6FF"),S("Mint",mul="B6FFD6"),S("Red",mul="FF6B6B")])
C(U,"hearts","Hearts",H+"heart/full.png",[H+"heart/**"],[H+"heart/full.png",H+"heart/half.png",H+"heart/container.png"],[S("Blue",hue=.6),S("Green",hue=.33),S("Gold",hue=.13)])
C(U,"hunger","Hunger",H+"food_full.png",[H+"food_*.png"],[H+"food_full.png",H+"food_half.png"],[S("Blue",hue=.6),S("Green",hue=.33)])
C(U,"armorbar","Armor Bar",H+"armor_full.png",[H+"armor_*.png"],[H+"armor_full.png",H+"armor_half.png"],[S("Gold",hue=.13),S("Red",hue=.9)])
C(U,"xpbar","XP Bar",H+"experience_bar_progress.png",[H+"experience_bar_*.png"],[H+"experience_bar_progress.png"],[S("Blue",hue=.45),S("Pink",hue=.8)])
C(U,"crosshair","Crosshair",H+"crosshair.png",[H+"crosshair*.png"],[H+"crosshair.png"],[S("Lime",mul="7CFF7C"),S("Violet",mul="C9A8FF"),S("Red",mul="FF6B6B")])
C(U,"inventory","Inventory","textures/gui/container/inventory.png",["textures/gui/container/**",SP+"container/**"],["textures/gui/container/inventory.png"],[S("Dark",bri=.5),S("Midnight",hue=.65,bri=.6)])
C(U,"buttons","Buttons & Widgets",SP+"widget/button.png",[SP+"widget/**"],[SP+"widget/button.png",SP+"widget/button_highlighted.png"],[S("Violet",hue=.75),S("Dark",bri=.5)])
C(U,"tooltips","Tooltips",SP+"tooltip/background.png",[SP+"tooltip/**"],[],[])
C(U,"menubg","Menu Backgrounds","textures/gui/menu_background.png",["textures/gui/menu_background.png","textures/gui/inworld_menu_background.png","textures/gui/title/**"])
C(U,"fonts","Fonts",None,["font/**","textures/font/**"])
# ---- Sounds (matches files replacing vanilla sound files; use "All Sounds" for packs with custom sound names) ----
A="Sounds"
C(A,"s_totem","Totem Pop Sound",None,["sounds/item/totem/**"])
C(A,"s_hit","Hit Sounds",None,["sounds/entity/player/attack/**"])
C(A,"s_hurt","Hurt Sounds",None,["sounds/damage/**","sounds/entity/player/hurt/**"])
C(A,"s_boom","Explosions",None,["sounds/random/explode*"])
C(A,"s_bow","Bow Sounds",None,["sounds/random/bow*"])
C(A,"s_anchor","Anchor Sounds",None,["sounds/block/respawn_anchor/**"])
C(A,"s_all","All Sounds",None,["sounds/**","sounds.json"])
json.dump(dict(collections=G),open("src/main/resources/packmixer/collections.json","w"),indent=0)
print(len(G),"collections")
