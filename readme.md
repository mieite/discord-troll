# Discord TrollBot

This is a [Discord](https://discordapp.com) bot that lives under the bridge it creates between two Discord servers. The purpose of this
bot is to manage member roles, mute roles, kicks and bans across two Discord servers thus easing the job of moderators
and admins.

Kick and Ban propagation is prevented from Administrators, so if you want to get rid of your rival admins you have to
remove the role from them first on every server.

## How it works

### Startup

In startup bot goes through users, bans and kicks (found in audit log) for both servers and merges missing stuff so that
both servers are in the same state with few obvious caveats:

1. Audit log lasts only 90 days (meaning old kicks don't get to haunt you beyond that)
2. Kicks found in startup are given to users who have joined 2nd server after the kick time. Users get kicked if they
joined the other server before kick happened (and kick happened within 1 day period)
3. Roles not set for missing users (duh)

### Runtime

In runtime bot follows events provided by JDA and reacts to stuff as fast as events come.

## Dependencies

This bot was built on top of the excellent [JDA (Java Discord Api)](https://github.com/DV8FromTheWorld/JDA) library.

## Configuration

Make sure you have trollbot.properties in the directory where you run the bot. Example configuration file:

```
# enable or disable testing
testing.enabled=true|false
# bot secret key
bot.secret=
# name of the member role, needs to be the same on both discords
roles.member=Member
# name of the mute role, needs to be the same on both discords
roles.mute=Muted
# Comma separated list of other roles preventing member role that need to be taken into accord
roles.otherMuteRoles=Gatherban
# managed servers by id
guilds.1=123456
guilds.2=654321
```

## Required bot permissions

TrollBot requires the following permissions to work:

* **View Audit Log** (to handle kicks and bans)
* **Manage Roles** (to manage user roles. NOTE: bot role needs to be higher than member or mute roles)
* **Kick Members** (duh)
* **Ban Members** (duh)
* **Send Messages** (actually not required yet, since bot sends no messages beyond the testing stuff for developers)

## Running

```
mvn package
java -jar discord-troll-<version>-jar-with-dependencies.jar
```