/*
 * This file is part of Discord-Troll application.
 *
 * Discord-Troll application is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Discord-Troll application is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Discord-Troll application.  If not, see <http://www.gnu.org/licenses/>.
 */

package oni.discord.troll;

import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.audit.ActionType;
import net.dv8tion.jda.core.audit.AuditLogEntry;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.Role;
import net.dv8tion.jda.core.entities.User;
import net.dv8tion.jda.core.events.guild.GuildBanEvent;
import net.dv8tion.jda.core.events.guild.GuildUnbanEvent;
import net.dv8tion.jda.core.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.core.events.guild.member.GuildMemberLeaveEvent;
import net.dv8tion.jda.core.events.guild.member.GuildMemberRoleAddEvent;
import net.dv8tion.jda.core.events.guild.member.GuildMemberRoleRemoveEvent;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * @author: hpr
 * @date: 31/12/2018
 */
public class TrollBot extends ListenerAdapter {

    private static Logger logger = LoggerFactory.getLogger(TrollBot.class);

    private static String ROLE_MEMBER = "roles.member.";
    private static String ROLE_MUTE = "roles.mute.";
    private static String ROLE_ADD_ROLE_CHANNEL = "roles.addRoleChannel.";
    private static String ROLE_ADD_ROLE = "roles.addRole.";
    private static String ROLE_OTHER_MUTE_ROLES = "roles.otherMuteRoles.";

    private final Properties properties;
    private final JDA jda;
    private final Guild guild1;
    private final Guild guild2;
    private ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);

    public TrollBot(JDA jda, Properties properties) {
        this.jda = jda;
        this.properties = properties;

        this.guild1 = jda.getGuildById(properties.getProperty("guilds.1", "0"));
        this.guild2 = jda.getGuildById(properties.getProperty("guilds.2", "0"));

        if(guild1 == null || guild2 == null) {
            logger.error("Servers configured wrong or bot not invited to servers, aborting");
            System.exit(-1);
        }
        boolean problem = verifyRoles(guild1);
        problem |= verifyRoles(guild2);

        if(!problem) {
            logger.info("Roles verified successfully.");
        }
        logger.info("Merging user roles");
        initUserRoles();
        jda.addEventListener(this);

        boolean testingEnabled = Boolean.parseBoolean(properties.getProperty("testing.enabled", "false"));
        if(testingEnabled) {
            logger.info("Testing enabled.");
            executorService.scheduleAtFixedRate(new Runnable() {
                public void run() {
                    testRepeatFunction();
                }
            }, 1, 5, TimeUnit.SECONDS);
        }

        if(isRemindersEnabled()) {
            String propertyRunTime = properties.getProperty("nonMemberSpam.reminderMessage.runTime", "09:00");
            LocalTime time = LocalTime.parse(propertyRunTime).withNano(0);
            LocalDateTime firstRunTime = LocalDateTime.now().withHour(time.getHour()).withMinute(time.getMinute()).withSecond(0).withNano(0);
            if(LocalDateTime.now().isAfter(firstRunTime)) {
                firstRunTime = firstRunTime.plus(1, ChronoUnit.DAYS);
            }
            Duration initialDelay = Duration.between(LocalDateTime.now(), firstRunTime);

            executorService.scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {
                    remindNonMembers(guild1);
                }
            }, initialDelay.getSeconds(), TimeUnit.DAYS.toSeconds(1), TimeUnit.SECONDS);
        }
        logger.info("Initialization completed.");
    }

    private boolean isRemindersEnabled() {
        return properties.getProperty("nonMemberSpam.enabled", "false").equalsIgnoreCase("true");
    }

    private List<String> getOtherMuteRoles(Guild guild) {
        String otherMutes = getProperty(ROLE_OTHER_MUTE_ROLES, guild);
        if(otherMutes != null) {
            String[] split = otherMutes.split(",");
            return Arrays.asList(StringUtils.stripAll(split));
        }
        return new ArrayList<>();
    }

    private String getGuildId(Guild guild) {
        if(guild.equals(guild1)) {
            return "1";
        } else if(guild.equals(guild2)) {
            return "2";
        }
        return null;
    }

    private boolean verifyRoles(Guild guild) {
        boolean problem = false;
        if(!hasRole(guild.getRoles(), getProperty(ROLE_MEMBER, guild))) {
            logger.warn(guild.getName() + " missing member role");
            problem = true;
        }
        if(!hasRole(guild.getRoles(), getProperty(ROLE_MUTE, guild))) {
            logger.warn(guild.getName() + " missing mute role");
            problem = true;
        }
        return problem;
    }

    private String getProperty(String propertyPrefix, Guild guild) {
        return properties.getProperty(propertyPrefix + getGuildId(guild));
    }

    // check all user roles to verify that current status is OK
    // also check that all banned and kicked users have met their fate on all servers
    // also check unbans
    private void initUserRoles() {
        mergeMemberStatuses();
    }

    private void mergeMemberStatuses() {
        logger.debug("Merging " + guild1.getName() + " to " + guild2.getName());
        checkGuildMemberStatuses(guild1, guild2);
        logger.debug("Merging " + guild2.getName() + " to " + guild1.getName());
        checkGuildMemberStatuses(guild2, guild1);
    }

    private void checkGuildMemberStatuses(Guild source, Guild target) {
        List<Member> sourceMembers = source.getMembers();

        // check bans and kicks first
        mergeBans(source, target);
        mergeKicks(source, target);

        for(Member sourceMember : sourceMembers) {
            Member targetMember = target.getMember(sourceMember.getUser());
            if(targetMember == null) {
                continue;
            }
            // check member status
            if(hasRole(sourceMember.getRoles(), getProperty(ROLE_MEMBER, source)) && !hasRole(targetMember.getRoles(), getProperty(ROLE_MEMBER, target))) {
                // don't give member role to users who are still muted or have other roles that require user to
                // not have member role
                List<String> muteRoles = getMuteAndOthersList(target);
                if(!hasAnyRole(targetMember.getRoles(), muteRoles)) {
                    target.getController().addSingleRoleToMember(targetMember, getRoleById(target.getRoles(), getProperty(ROLE_MEMBER, target)))
                            .reason("Cloned from " + source.getName()).queue();
                }
            }
            // check mute status
            if(hasRole(sourceMember.getRoles(), getProperty(ROLE_MUTE, source)) && !hasRole(targetMember.getRoles(), getProperty(ROLE_MUTE, target))) {
                target.getController().addSingleRoleToMember(targetMember, getRoleById(target.getRoles(), getProperty(ROLE_MUTE, target)))
                            .reason("Cloned from " + source.getName()).queue();
            }
        }
    }

    private List<String> getMuteAndOthersList(Guild guild) {
        List<String> roles = new ArrayList<String>();
        roles.add(getProperty(ROLE_MUTE, guild));
        roles.addAll(getOtherMuteRoles(guild));
        return roles;
    }

    private void mergeBans(Guild source, Guild target) {
        logger.debug("Merging bans");
        List<Guild.Ban> targetBans = target.getBanList().complete();

        for(Guild.Ban ban : source.getBanList().complete()) {
            if(targetBans.stream().noneMatch(b -> ban.getUser().getId().equals(b.getUser().getId()))) {
                Member member = target.getMember(ban.getUser());
                if(member == null || !member.hasPermission(Permission.ADMINISTRATOR)) {
                    logger.debug("Missing ban found: " + ban.toString());
                    target.getController().ban(ban.getUser(), 0, ban.getReason() + " / cloned from " + source.getName()).queue();
                }
            }
        }
    }

    private void mergeKicks(Guild source, Guild target) {
        logger.debug("Merging kicks");
        source.getAuditLogs().type(ActionType.KICK).queue(new Consumer<List<AuditLogEntry>>() {
            @Override
            public void accept(List<AuditLogEntry> auditLogEntries) {
                for(AuditLogEntry logEntry : auditLogEntries) {
                    Member member = target.getMemberById(logEntry.getTargetId());
                    // dont autokick admins
                    if(member != null && !member.hasPermission(Permission.ADMINISTRATOR)
                            && logEntry.getCreationTime().isAfter(member.getJoinDate())
                            && logEntry.getCreationTime().isAfter(OffsetDateTime.now().minus(12, ChronoUnit.HOURS))) {
                        logger.debug("Missing kick found: " + logEntry.toString());
                        target.getController().kick(member, logEntry.getReason() + " / Cloned from " + source.getName()).queue();
                    }
                }
            }
        });
    }

    private boolean hasAnyRole(List<Role> roles, List<String> checkRoles) {
        for(String role : checkRoles) {
            if(hasRole(roles, role)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasRole(List<Role> roles, String checkRole) {
        return getRoleById(roles, checkRole) != null;
    }

    private Role getRoleById(List<Role> roles, String roleId) {
        return roles.stream().filter(x -> roleId.equalsIgnoreCase(x.getId())).findFirst().orElse(null);
    }

    private boolean hasBan(Guild guild, String userId) {
        List<Guild.Ban> banList = guild.getBanList().complete();
        return banList.stream().anyMatch(b -> b.getUser().getId().equals(userId));
    }

    private void testRepeatFunction() {
        //if(!validateGuildAmount()) return;
        guild2.getAuditLogs().user("240432007908294656").queue(new Consumer<List<AuditLogEntry>>() {
            @Override
            public void accept(List<AuditLogEntry> auditLogEntries) {
                for(AuditLogEntry entry : auditLogEntries) {
                    logger.info(entry.getCreationTime().toString());
                }
            }
        });

        //jda.getGuilds().get(0).getTextChannelById(529300591835480067L).sendMessage("Testing testing 123").queue();
    }

    @Override
    public void onGuildMemberRoleAdd(GuildMemberRoleAddEvent event) {
        if(!verifyGuild(event.getGuild())) {
            return;
        }

        logger.debug("Received ROLE ADD event from " + event.getGuild().getName());

        Guild toUpdateGuild = getOtherGuild(event.getGuild());
        Member otherGuildMember = toUpdateGuild.getMember(event.getMember().getUser());
        if(otherGuildMember == null) {
            return;
        }

        // don't give member role if user has mute or other roles preventing member roled
        if(hasRole(event.getRoles(), getProperty(ROLE_MEMBER, event.getGuild())) && !hasRole(otherGuildMember.getRoles(), getProperty(ROLE_MEMBER, toUpdateGuild))
                && !hasAnyRole(otherGuildMember.getRoles(), getMuteAndOthersList(toUpdateGuild))) {
            logger.debug("Cloning " + event.getRoles().get(0).getName() + " ADD to user " + otherGuildMember.getEffectiveName() + " / " + otherGuildMember.getUser().getId()
                    + " from server \"" + event.getGuild().getName() + "\" to " + toUpdateGuild.getName());
            toUpdateGuild.getController().addSingleRoleToMember(otherGuildMember, getRoleById(toUpdateGuild.getRoles(), getProperty(ROLE_MEMBER, toUpdateGuild)))
                    .reason("Cloned from " + event.getGuild().getName()).queue();
        }
        if(hasRole(event.getRoles(), getProperty(ROLE_MUTE, event.getGuild())) && !hasRole(otherGuildMember.getRoles(), getProperty(ROLE_MUTE, toUpdateGuild))) {
            logger.debug("Cloning " + event.getRoles().get(0).getName() + " ADD to user " + otherGuildMember.getEffectiveName() + " / " + otherGuildMember.getUser().getId()
                    + " from server \"" + event.getGuild().getName() + "\" to " + toUpdateGuild.getName());
            toUpdateGuild.getController().addSingleRoleToMember(otherGuildMember, getRoleById(toUpdateGuild.getRoles(), getProperty(ROLE_MUTE, toUpdateGuild)))
                    .reason("Cloned from " + event.getGuild().getName()).queue();
        }
    }

    @Override
    public void onGuildMemberRoleRemove(GuildMemberRoleRemoveEvent event) {
        if(!verifyGuild(event.getGuild())) {
            return;
        }
        logger.debug("Received ROLE REMOVE event from " + event.getGuild().getName());
        
        Guild toUpdateGuild = getOtherGuild(event.getGuild());
        Member otherGuildMember = toUpdateGuild.getMember(event.getMember().getUser());
        if(otherGuildMember == null) {
            return;
        }

        if(hasRole(event.getRoles(), getProperty(ROLE_MEMBER, event.getGuild())) && hasRole(otherGuildMember.getRoles(), getProperty(ROLE_MEMBER, toUpdateGuild))) {
            logger.debug("Cloning " + event.getRoles().get(0).getName() + " REMOVE to user " + otherGuildMember.getEffectiveName() + " / " + otherGuildMember.getUser().getId()
                    + " from server \"" + event.getGuild().getName() + "\" to " + toUpdateGuild.getName());
            toUpdateGuild.getController().removeSingleRoleFromMember(otherGuildMember, getRoleById(toUpdateGuild.getRoles(), getProperty(ROLE_MEMBER, toUpdateGuild)))
                                                    .reason("Cloned from " + event.getGuild().getName()).queue();
        }
        if(hasRole(event.getRoles(), getProperty(ROLE_MUTE, event.getGuild())) && hasRole(otherGuildMember.getRoles(), getProperty(ROLE_MUTE, toUpdateGuild))) {
            logger.debug("Cloning " + event.getRoles().get(0).getName() + " REMOVE to user " + otherGuildMember.getEffectiveName() + " / " + otherGuildMember.getUser().getId()
                    + " from server \"" + event.getGuild().getName() + "\" to " + toUpdateGuild.getName());
            toUpdateGuild.getController().removeSingleRoleFromMember(otherGuildMember, getRoleById(toUpdateGuild.getRoles(), getProperty(ROLE_MUTE, toUpdateGuild)))
                    .reason("Cloned from " + event.getGuild().getName()).queue();
        }

    }

    @Override
    public void onGuildBan(GuildBanEvent event) {
        if(!verifyGuild(event.getGuild())) {
            return;
        }
        logger.debug("Received BAN event from " + event.getGuild().getName());
        
        Guild toUpdateGuild = getOtherGuild(event.getGuild());
        Member otherGuildMember = toUpdateGuild.getMember(event.getUser());
        if(otherGuildMember == null || !otherGuildMember.hasPermission(Permission.ADMINISTRATOR)
                && !hasBan(toUpdateGuild, otherGuildMember.getUser().getId())) {
            logger.debug("Cloning BAN to user " + event.getUser().getName() + " / " + event.getUser().getId()
                    + " from server \"" + event.getGuild().getName() + "\" to " + toUpdateGuild.getName());
            toUpdateGuild.getController().ban(event.getUser(), 0, "Cloned from " + event.getGuild().getName()).queue();
        }
    }

    @Override
    public void onGuildUnban(GuildUnbanEvent event) {
        if(!verifyGuild(event.getGuild())) {
            return;
        }
        logger.debug("Received UNBAN event from " + event.getGuild().getName());
        
        Guild toUpdateGuild = getOtherGuild(event.getGuild());
        if(!hasBan(toUpdateGuild, event.getUser().getId())) {
            return;
        }
        Guild.Ban updateGuildBan = toUpdateGuild.getBanById(event.getUser().getId()).complete();
        if(updateGuildBan != null) {
            logger.debug("Cloning UNBAN to user " + event.getUser().getName() + " / " + event.getUser().getId()
                    + " from server \"" + event.getGuild().getName() + "\" to " + toUpdateGuild.getName());
            toUpdateGuild.getController().unban(event.getUser()).reason("Cloned from " + event.getGuild().getName()).queue();
        }
    }

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        if(!verifyGuild(event.getGuild())) {
            return;
        }
        Guild otherGuild = getOtherGuild(event.getGuild());
        Member otherGuildMember = otherGuild.getMember(event.getUser());
        boolean hasRole = false;
        if(otherGuildMember != null) {
            if (hasRole(otherGuildMember.getRoles(), getProperty(ROLE_MEMBER, otherGuild))) {
                logger.debug("Cloning member role to JOIN user " + event.getUser().getName() + " / " + event.getUser().getId()
                        + " from server \"" + event.getGuild().getName() + "\" to " + otherGuild.getName());
                event.getGuild().getController().addSingleRoleToMember(event.getMember(), getRoleById(event.getGuild().getRoles(), getProperty(ROLE_MEMBER, event.getGuild())))
                        .reason("Cloned from " + otherGuild.getName()).queue();
                hasRole = true;
            }
            if (hasRole(otherGuildMember.getRoles(), getProperty(ROLE_MUTE, otherGuild))) {
                logger.debug("Cloning mute to JOIN user " + event.getUser().getName() + " / " + event.getUser().getId()
                        + " from server \"" + event.getGuild().getName() + "\" to " + otherGuild.getName());
                event.getGuild().getController().addSingleRoleToMember(event.getMember(), getRoleById(event.getGuild().getRoles(), getProperty(ROLE_MUTE, event.getGuild())))
                        .reason("Cloned from " + otherGuild.getName()).queue();
                hasRole = true;
            }
        }
        if(isRemindersEnabled() && !hasRole) {
            String message = properties.getProperty("nonMemberSpam.joinMessage");
            event.getUser().openPrivateChannel().queue((channel) -> channel.sendMessage(message).queue());
        }
    }

    @Override
    public void onGuildMessageReceived(GuildMessageReceivedEvent event) {
        if(!verifyGuild(event.getGuild())) {
            return;
        }
        if(event.getChannel().getId().equals(getProperty(ROLE_ADD_ROLE_CHANNEL, event.getGuild()))) {
            Member member = event.getMember();
            String contentRaw = event.getMessage().getContentRaw();
            String addRoleId = getProperty(ROLE_ADD_ROLE, event.getGuild());
            Role addRole = getRoleById(event.getGuild().getRoles(), addRoleId);
            if(contentRaw.startsWith(".gatherer") && !hasRole(member.getRoles(), addRoleId)) {
                event.getGuild().getController().addSingleRoleToMember(member, addRole).queue();
                event.getChannel().sendMessage("<@" + member.getUser().getId() + "> kuuluu nyt kerääjiin").queue();
            } else if(contentRaw.startsWith(".ungatherer") && hasRole(member.getRoles(), addRoleId)) {
                event.getGuild().getController().removeSingleRoleFromMember(member, addRole).queue();
                event.getChannel().sendMessage("<@" + member.getUser().getId() + "> on luovuttaja.").queue();
            } else if(contentRaw.startsWith(".allgatherers")) {
                event.getChannel().sendMessage("Kerääjäklaanissa on yhteensä " + event.getGuild().getMembersWithRoles(addRole).size() + " jäsentä.").queue();
            }
        }
    }

    @Override
    public void onGuildMemberLeave(GuildMemberLeaveEvent event) {
        if(!verifyGuild(event.getGuild()) || hasBan(event.getGuild(), event.getUser().getId())) {
            return;
        }
        User leaver = event.getUser();

        Guild sourceGuild = event.getGuild();
        Guild toUpdateGuild = getOtherGuild(event.getGuild());

        // don't try to kick non-existing members or administrators
        if(!toUpdateGuild.isMember(leaver) || toUpdateGuild.getMember(leaver).hasPermission(Permission.ADMINISTRATOR) ) {
            return;
        }
        // check if leaving was a kick. compare dates since there's no direct relation between the event and the audit event
        List<AuditLogEntry> logEvents = sourceGuild.getAuditLogs().type(ActionType.KICK).complete();
        AuditLogEntry auditLogEntry = logEvents.stream().filter(log -> log.getTargetId().equals(event.getUser().getId())
                && dateBetweenRange(log.getCreationTime(), OffsetDateTime.now(), 10, ChronoUnit.SECONDS)).findFirst().orElse(null);
        if(auditLogEntry != null) {
            logger.debug("Cloning KICK to user " + leaver.getName() + " / " + leaver.getId() + " from server " + event.getGuild().getName()
                                                + " to server " + toUpdateGuild.getName());
            toUpdateGuild.getController().kick(leaver.getId(), auditLogEntry.getReason() + " / cloned from " + sourceGuild.getName()).queue();
        }
    }

    private boolean dateBetweenRange(OffsetDateTime target, OffsetDateTime compared, int range, TemporalUnit unit) {
        OffsetDateTime start = target.minus(range, unit);
        OffsetDateTime end = target.plus(range, unit);
        return compared.isAfter(start) && compared.isBefore(end);
    }

    private void remindNonMembers(Guild guild) {
        logger.info("sending reminder messages to users");
        String message = properties.getProperty("nonMemberSpam.reminderMessage");
        String memberRoleId = properties.getProperty("roles.member." + getGuildId(guild));
        int remindInterval = Integer.parseInt(properties.getProperty("nonMemberSpam.reminderMessage.intervalDays", "7"));
        List<Member> all = guild.getMembers();
        Role memberRole = guild.getRoleById(memberRoleId);
        int count = 0;
        for(Member member : all) {
            if(jda.getSelfUser().getId().equals(member.getUser().getId())
                    || member.getRoles().contains(memberRole)
                    || member.hasPermission(Permission.ADMINISTRATOR)
                    || member.getUser().isBot()) {
                continue;
            }
            Duration timeOnServer = Duration.between(member.getJoinDate(), OffsetDateTime.now());
            long days = timeOnServer.toDays();
            if(days % remindInterval == 0) {
                String personalizedMessage = message.replace("%days%", String.valueOf(days));
                member.getUser().openPrivateChannel().queue((channel) -> channel.sendMessage(personalizedMessage).queue());
                count++;
            }
        }
        logger.info("sent reminder messages to " + count + " users");
    }

    private boolean verifyGuild(Guild guild) {
        boolean result =  guild.getId().equals(guild1.getId()) || guild.getId().equals(guild2.getId());
        if(!result) {
            logger.warn("WARNING: Getting messages from non-configured server: " + guild.getName() + " / " + guild.getId());
        }
        return result;
    }

    private Guild getOtherGuild(Guild guild) {
        return guild.getId().equals(guild1.getId()) ? guild2 : guild1;
    }
}
