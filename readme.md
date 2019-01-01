# Discord Troll Bot

This is a discord bot that lives under the bridge it creates between two different discord servers.

## Dependencies

This bot was built on top of the excellent [JDA (Java Discord Api)](https://github.com/DV8FromTheWorld/JDA) library.

## Configuration

Make sure you have trollbot.properties in the directory where you run the bot. Example configuration file:

```
# bot secret key
bot.secret=
# name of the member role, needs to be the same on both discords
roles.member=
# name of the mute role, needs to be the same on both discords
roles.mute=Mute
# other roles that might remove member role (besides mute) that need to be taken into accord
roles.otherMuteRoles=Gatherban
guilds.1=
guilds.2=
```