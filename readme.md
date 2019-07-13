# Discord TrollBot

This is a [Discord](https://discordapp.com) bot that lives under the bridge it creates between two Discord servers. The purpose of this
bot is to manage member roles, mute roles, kicks and bans across two Discord servers thus easing the job of moderators
and admins.

Kick and Ban propagation is prevented from Administrators, so if you want to get rid of your rival admins you have to
remove the role from them first on every server.

Changelog:

0.3:
 - added UTF-8 support to property files
 - added option to send greetings message and a reminder message to users on servers that require member roles

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
# id of the member role per guild, needs to be the same on both discords
roles.member.1=<id>
roles.member.2=<id>
# id of the mute role per guild, needs to be the same on both discords
roles.mute.1=<id>
roles.mute.2=<id>
# Comma separated list of other role id preventing member role that need to be taken into accord
roles.otherMuteRoles.1=<ids>
# Id of channel where users can request additional roles
roles.addRoleChannel.1=<id>
# Id of the role users can request
roles.addRole.1=<id>
# managed servers by id
guilds.1=<"master"_server_id>
guilds.2=<other_server_id>

# spam non member users
nonMemberSpam.enabled=true|false
# join message for new users giving instructions on how to gain member role, for example
nonMemberSpam.joinMessage=<message>
# reminder message on how to get member role, sent to users in intervals defined by other properties
nonMemberSpam.reminderMessage=<reminder message>
# reminder message interval days, used as a modulo from total days since joining
nonMemberSpam.reminderMessage.intervalDays=1+
# reminder message daily run time
nonMemberSpam.reminderMessage.runTime=<hh:mm>
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

## Commands

```
.gatherer = request role
.ungatherer = remove role
.allgatherers = gives the total amount of users holding the role
```