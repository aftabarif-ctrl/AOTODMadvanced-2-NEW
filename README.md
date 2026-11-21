# ODM Gear & Beast Titan  (Fabric, Minecraft 26.3)

## Build
Requires **JDK 25** and Gradle 9.5+.

    gradle wrapper --gradle-version 9.5.1   # once, to generate gradlew
    ./gradlew build                          # jar ends up in build/libs/odm-titan-1.0.0.jar

`gradle.properties` targets `26.3-snapshot-5` + Fabric API `0.156.0+26.3`. Change `minecraft_version`
to `26.3` once the final release is available, and bump `fabric_api_version` to the newest `+26.3` build.

## What's in it
| Thing | How it works |
|---|---|
| **ODM Gear** | Right-click to put it in your **dedicated ODM slot** (does NOT use your chest/armour slots). Press **K** to take it back out. |
| **Hooks** | Hold **Z** (left) / **X** (right) - hooks fire where you look (blocks or mobs, 48 blocks), reel you in while held. Particle "wires" show the cables. |
| **Gas Boost** | Hold **V** to burst forward / glide. |
| **Gas tank** | 20,000 units. Each hook uses 1/tick, boost 2/tick -> ~8 minutes of continuous use. Shown on the **action bar as % with a bar**: `ODM GAS [||||||||||||........] 63%`. |
| **Refill** | **Gas Canister** (right-click, +25 %). Craft: iron + gunpowder (makes 2). |
| **Levi Spinning Strike** | Press **C** near a target (or while hooked to one): you corkscrew toward it with a spinning ring of blade flashes and a real 360 degree camera spin, then land 3 rapid slashes + a finishing hit. Costs 4 % gas. Hold **ODM Blades** in both hands for extra damage. |
| **ODM Blade** | Iron sword-like blade (7 dmg). |
| **Beast Titan** | ~8.8 blocks tall, 600 HP, **slow**. Smashes through blocks in its path and, from 8-56 blocks away, winds up and **throws a volley of 3 ground blocks at ~52 blocks/s**, causing damage, knockback and small craters. Summon with the **Beast Titan Horn** (bone blocks + netherite) or `/summon odmtitan:beast_titan`. |

Controls can all be rebound in Options > Controls > "ODM Gear".

## Tuning
`OdmManager.java` (top constants): gas costs, speeds, hook range, spin damage/cost.  
`OdmData.MAX_GAS` : tank size.  `ThrownBlocks.SPEED / DIRECT_DAMAGE`: block throw speed/damage.  
`BeastTitanEntity.BREAKS_BLOCKS`: set `false` to stop terrain destruction while walking.

## Known limits (be aware)
* **Untested build.** Written against the Minecraft 26.3 API names used by your original mod, but it has
  not been compiled or run. If `./gradlew build` shows an error, it will be a one-line API rename - paste it
  and it gets fixed.
* The ODM slot is a real, separate, persistent slot (survives death/relog) but is *not drawn as a box in the
  inventory screen* - status is shown by the action bar. A visible inventory box needs a custom screen/mixin.
* ODM gear is not rendered on the player's back (no equipment model); the hooks/cables/gas are shown with particles.
* Beast Titan uses the vanilla humanoid model (scaled up) with a custom fur texture, not a unique ape model.
