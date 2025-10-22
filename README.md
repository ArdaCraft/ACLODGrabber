# ACLODGrabber

ACLODGrabber is a Minecraft Fabric mod that automatically downloads and installs Distant Horizons LOD files specifically for the ArdaCraft server.

## Requirements

- [Fabric Loader](https://fabricmc.net/)
- [Fabric API](https://www.curseforge.com/minecraft/mc-mods/fabric-api)
- [Distant Horizons](https://modrinth.com/mod/distanthorizons)

## Usage

The mod will automatically check for LOD updates when you start Minecraft. If new LODs are available, you'll see a prompt asking if you'd like to download them.

### Commands

- `/resetLODs` - Reset the mod's configuration and force a fresh LOD check

## Configuration

The mod creates a configuration file at `config/ACLODGrabber/config.json` with the following settings:

- `lastDownloadTime`: Timestamp of the latest LOD download

## Distant Horizons files

This mod creates a folder at `Distant_Horizons_server_data/Ardacraft` containing Distant Horizons data for the ArdaCraft Server.

## Acknowledgments

- **Forked from [WynnLODGrabber](https://github.com/DrBiznes/WynnLODGrabber)** by DrBiznes

## Links

- [ArdaCraft Website](https://ardacraft.me)
- [Discord Community](https://discord.gg/qcYBkCmAKZ)