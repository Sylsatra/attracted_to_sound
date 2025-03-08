# 🔊 Sound Attract Mod – Bring Life to Your World! 🎶

The Sound Attract Mod makes mobs react to in-game sounds, adding realism and strategy to Minecraft! Now, zombies and other creatures will hear and move toward specific noises like levers, pistons, and trapdoors.

Mod showcase:
https://www.youtube-nocookie.com/embed/J-Qt4RjvDNE

# 🛠 Use Cases
Create stealth challenges where mobs are drawn to noise.
Build dynamic mob farms by using sounds to lure creatures.
Add realism to the game, making the world feel more alive.

# Default Config: soundattractmod.toml
Zombie and Husk will be attracted to lever sounds, piston sounds, and wooden trapdoor sounds.

Create stealth challenges where mobs are drawn to noise.
Build dynamic mob farms by using sounds to lure creatures.
Add realism to the game, making the world feel more alive.

# ⚙️ How to Configure

<details>
<summary>Spoiler</summary>

Customize the hearing range (hearingRadius), reaction speed (scanCooldownTicks), and behavior after arrival (arrivalDistance).
Choose which mobs react (entities) and what sounds attract them (sounds).
Modify the soundattractmod.toml file or use in-game config tools.

For Example, my Walking Among The Dinosaur config:

#Sound Attract Mod Configuration
#This mod allows specific entities to be attracted to certain sounds.
#Modify this config to control which mobs react and what sounds they respond to.
#For valid sound events, check: https://minecraft.fandom.com/wiki/Sounds.json#Java_Edition_values
[soundattract]
#How long (in ticks) a sound remains 'interesting' after being heard. 20 ticks = 1 second.
soundLifetimeTicks = 120
#Maximum distance (in blocks) at which mobs will notice an attracted sound.
hearingRadius = 32
#List of sound events that attract these entities.
#Example: Add "minecraft:entity.creeper.primed" to attract mobs when a creeper starts to explode.
#Find valid sounds here: https://minecraft.fandom.com/wiki/Sounds.json#Java_Edition_values
sounds = ["minecraft:block.lever.click", "minecraft:block.piston.extend", "minecraft:block.piston.contract", "minecraft:block.wooden_trapdoor.open", "minecraft:block.wooden_trapdoor.close", "minecraft:block.bamboo_wood_trapdoor.open", "minecraft:block.bamboo_wood_trapdoor.close", "minecraft:block.cherry_wood_trapdoor.open", "minecraft:block.cherry_wood_trapdoor.close", "minecraft:block.iron_trapdoor.open", "minecraft:block.iron_trapdoor.close", "minecraft:block.wooden_door.open", "minecraft:block.wooden_door.close", "minecraft:block.bamboo_wood_door.open", "minecraft:block.bamboo_wood_door.close", "minecraft:block.cherry_wood_door.open", "minecraft:block.cherry_wood_door.close", "minecraft:block.iron_door.open", "minecraft:block.iron_door.close", "minecraft:block.sand.fall", "minecraft:block.gravel.fall", "minecraft:block.anvil.land", "minecraft:block.anvil.use", "minecraft:block.anvil.destroy", "minecraft:block.dispenser.dispense", "minecraft:block.dispenser.launch", "minecraft:block.tripwire.click_on", "minecraft:block.tripwire.click_off", "minecraft:block.grass.break", "minecraft:block.scaffolding.break"]
#List of entity IDs that should be attracted to certain sounds.
#Example: Add "minecraft:skeleton" to make skeletons react to sounds.
entities = ["fossil:tyrannosaurus", "fossil:allosaurus", "fossil:spinosaurus", "fossil:megalodon", "fossil:mosasaurus", "fossil:liopleurodon", "fossil:sarcosuchus", "fossil:smilodon", "fossil:quetzalcoatlus", "fossil:deinonychus", "fossil:velociraptor", "fossil:dilophosaurus", "fossil:ceratosaurus"]
#How often (in ticks) the mob scans for new sounds. 20 = once per second.
scanCooldownTicks = 120
#The distance (in blocks) at which the mob is considered to have reached the sound.
#Example: If set to 4.0, mobs will stop moving toward the sound when within 4 blocks.
arrivalDistance = 10.0




</details>



👂 Mobs will hear you—use sound wisely! 🎵
