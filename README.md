# Domain Terraforming Initiative

A Starsector 0.98a mod. Terraforming is a megaproject: you build a Terraforming Control on a colony, then run long projects that change the world's conditions and, in the end, its planet type.

**Version 0.4.1 is a test version.** It has every terraforming structure, but not the quests yet. Please report bugs and ideas in the [issues](https://github.com/RSRaven/domain-terraforming-initiative/issues).

## What it adds

- **The Terraforming Control and 24 terraforming structures.** Each one works on one thing: toxic, dense, thin or no atmosphere; heat and cold; poor light and darkness; tectonic activity; extreme weather; pollution; an inimical biosphere; radiation. Others bring water with comets or take it away, improve the soil, seed marine life, or make a mild climate. When the conditions change enough, the planet changes its type.
- **Reverse projects:** the Atmospheric Siphon, the Deep Core Extractor and the Volatiles Extractor take resources from a world, at a price.
- **Colony Development:** Civic Integration, Labor Camps and the Purge Detachment work on the colony's people. Domed Cities (Sky Cities on a gas giant, Seafloor Cities on a water world), the City Agrav Grid and the Impact Defense Grid protect a colony from its hazards. Hydroponic Farms feed a colony that has no farmland.
- **24 special items** that make projects faster or cheaper. They wear out.
- **Projects take goods.** A short supply stops a project, and a long one makes it lose progress.
- **DTI Projects** in the Intel screen (Terraforming tag): every running project, by colony.
- **Settings** in LunaLib: switch terraforming or Colony Development off, turn single kinds of projects off, and change the numbers.

## Requirements

- Starsector 0.98a
- [LunaLib](https://fractalsoftworks.com/forum/index.php?topic=25658.0)

Optional:
- **Ashes of the Domain (Vaults of Knowledge):** the structures come with research, on their own tab with 28 techs. The AoTD workshop can make our items.
- **Console Commands:** test commands `DTI_Unlock`, `DTI_Status`, `DTI_Progress` and `DTI_Short`.

Tested with Nexerelin, AoTD, DIY Planets and TaSC.

## How to unlock the structures

The quests are not in the mod yet.
- **With AoTD:** research the techs on the DTI tab.
- **Without AoTD:** every structure is unlocked from the start. The LunaLib setting **Unlock without quests** (DTI, General tab) does this, and it is on by default until the quests are in the mod.

## Install

1. Unzip the folder into `Starsector/mods`.
2. Enable "Domain Terraforming Initiative" in the launcher (or in TriOS).

Start a new game if you can: marine life is placed on water worlds only when a new sector is made. Do not remove the mod from a save: the save needs its structures.

## Credits

Inspired by **DIY Planets** (kentington, now the community edition) and **Terraforming & Station Construction** (boggled). This mod takes ideas only: it has no code, text or art from them. The art was made for this mod.

## For modders

The Java source is in `src/`. To build `jars/dti.jar`, compile it for Java 8 with the game's jars, LunaLib, AoTD Vaults of Knowledge and Console Commands on the classpath.

## License

MIT, see [LICENSE](LICENSE).
