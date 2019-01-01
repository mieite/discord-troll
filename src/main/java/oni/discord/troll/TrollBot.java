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
import net.dv8tion.jda.core.hooks.ListenerAdapter;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
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

    private final Properties properties;
    private final JDA jda;
    private final Guild guild1;
    private final Guild guild2;
    private String roleMember;
    private String roleMute;
    private List<String> roleOtherMutes;
    private ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);

    public TrollBot(JDA jda, Properties properties) {
        this.jda = jda;
        this.properties = properties;

        this.guild1 = jda.getGuildById(properties.getProperty("guilds.1", "0"));
        this.guild2 = jda.getGuildById(properties.getProperty("guilds.2", "0"));

        this.roleMember = properties.getProperty("roles.member");
        this.roleMute = properties.getProperty("roles.mute");
        String otherMutes = properties.getProperty("roles.otherMuteRoles");
        if(otherMutes != null) {
            String[] split = otherMutes.split(",");
            roleOtherMutes = Arrays.asList(StringUtils.stripAll(split));
        }

        if(guild1 == null || guild2 == null) {
            logger.error("Servers configured wrong or bot not invited to servers, aborting");
            System.exit(-1);
        }
        executorService.scheduleAtFixedRate(new Runnable() {
            public void run() {
                testRepeatFunction();
            }
        }, 1, 5, TimeUnit.SECONDS);
        logger.info("initialized");
        initUserRoles();
        jda.addEventListener();
    }

    // check all user roles to verify that current status is OK
    // also check that all banned and kicked users have met their fate on all servers
    // also check unbans
    private void initUserRoles() {
        mergeMemberStatuses();
    }

    private void mergeMemberStatuses() {
        checkGuildMemberStatuses(guild1, guild2);
        checkGuildMemberStatuses(guild2, guild1);
    }

    private void checkGuildMemberStatuses(Guild source, Guild target) {
        List<Member> sourceMembers = source.getMembers();

        // check bans and kicks first
        mergeBans(source, target);
        mergeKicks(source, target);

        for(Member sourceMember : sourceMembers) {
            // ignore admin roles
            if(sourceMember.hasPermission(Permission.ADMINISTRATOR)) {
                continue;
            }
            Member targetMember = target.getMember(sourceMember.getUser());
            // if user is missing ignore, bans and kicks have already been handled
            // also do nothing to admins
            if(targetMember == null || targetMember.hasPermission(Permission.ADMINISTRATOR)) {
                continue;
            }
            // check if sourceMember has member role
            if(hasRole(sourceMember.getRoles(), roleMember)) {
                // check that sourceMember is on the server and sourceMember status
                if(!hasRole(targetMember.getRoles(), roleMember)) {
                    // check that sourceMember isn't admin or muted or has other mute roles that remove member role
                    List<String> roles = Collections.singletonList(roleMute);
                    roles.addAll(roleOtherMutes);
                    if(!hasAnyRole(targetMember.getRoles(), roles)) {
                        target.getController().addRolesToMember(targetMember, getRoleByName(guild1.getRoles(), roleMember)).queue();
                    }
                }
            // check mute status
            } else if(hasRole(sourceMember.getRoles(), roleMute)) {
                target.getController().addRolesToMember(targetMember, getRoleByName(guild1.getRoles(), roleMute)).queue();
            }
        }
    }

    private void mergeBans(Guild source, Guild target) {
        List<Guild.Ban> targetBans = target.getBanList().complete();

        for(Guild.Ban ban : source.getBanList().complete()) {
            if(targetBans.stream().noneMatch(b -> ban.getUser().getId().equals(b.getUser().getId()))) {
                target.getController().ban(ban.getUser(), 0, ban.getReason() + " / cloned from " + source.getName()).queue();
            }
        }
    }

    private void mergeKicks(Guild source, Guild target) {
        source.getAuditLogs().type(ActionType.KICK).queue(new Consumer<List<AuditLogEntry>>() {
            @Override
            public void accept(List<AuditLogEntry> auditLogEntries) {
                for(AuditLogEntry logEntry : auditLogEntries) {
                    Member member = target.getMemberById(logEntry.getTargetId());
                    // dont autokick admins
                    if(member != null && !member.hasPermission(Permission.ADMINISTRATOR)
                            && logEntry.getCreationTime().isAfter(member.getJoinDate())) {
                        target.getController().kick(member, logEntry.getReason() + " / Cloned from " + source.getName());
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
        return getRoleByName(roles, checkRole) != null;
    }

    private Role getRoleByName(List<Role> roles, String role) {
        return roles.stream().filter(x -> role.equalsIgnoreCase(x.getName())).findFirst().get();
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

        Guild toUpdateGuild = getOtherGuild(event.getGuild());
        Member otherGuildMember = toUpdateGuild.getMember(event.getMember().getUser());
        if(otherGuildMember != null) {
            return;
        }

        if(hasRole(event.getRoles(), roleMember)) {
            toUpdateGuild.getController().addSingleRoleToMember(otherGuildMember, getRoleByName(toUpdateGuild.getRoles(), roleMember))
                    .reason("cloned from " + event.getGuild().getName()).queue();
        }
        if(hasRole(event.getRoles(), roleMute)) {
            toUpdateGuild.getController().addSingleRoleToMember(otherGuildMember, getRoleByName(toUpdateGuild.getRoles(), roleMute))
                    .reason("cloned from " + event.getGuild().getName()).queue();
        }
    }

    @Override
    public void onGuildMemberRoleRemove(GuildMemberRoleRemoveEvent event) {
        if(!verifyGuild(event.getGuild())) {
            return;
        }
        Guild toUpdateGuild = getOtherGuild(event.getGuild());
        Member otherGuildMember = toUpdateGuild.getMember(event.getMember().getUser());
        if(otherGuildMember == null) {
            return;
        }

        if(hasRole(event.getRoles(), roleMember)) {
            toUpdateGuild.getController().removeSingleRoleFromMember(otherGuildMember, getRoleByName(toUpdateGuild.getRoles(), roleMember))
                                                    .reason("cloned from " + event.getGuild().getName()).queue();
        }
        if(hasRole(event.getRoles(), roleMute)) {
            toUpdateGuild.getController().removeSingleRoleFromMember(otherGuildMember, getRoleByName(toUpdateGuild.getRoles(), roleMute))
                    .reason("cloned from " + event.getGuild().getName()).queue();
        }

    }

    @Override
    public void onGuildBan(GuildBanEvent event) {
        if(!verifyGuild(event.getGuild())) {
            return;
        }
        Guild toUpdateGuild = getOtherGuild(event.getGuild());
        Member otherGuildMember = toUpdateGuild.getMember(event.getUser());
        if(otherGuildMember == null || !otherGuildMember.hasPermission(Permission.ADMINISTRATOR)) {
            toUpdateGuild.getController().ban(event.getUser().getId(), 0, "cloned from " + event.getGuild().getName()).queue();
        }
    }

    @Override
    public void onGuildUnban(GuildUnbanEvent event) {
        if(!verifyGuild(event.getGuild())) {
            return;
        }
        Guild toUpdateGuild = getOtherGuild(event.getGuild());
        Guild.Ban updateGuildBan = toUpdateGuild.getBanById(event.getUser().getId()).complete();
        if(updateGuildBan != null) {
            toUpdateGuild.getController().unban(event.getUser().getId()).reason("cloned from " + event.getGuild().getName()).queue();
        }
    }

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        if(!verifyGuild(event.getGuild())) {
            return;
        }
        Guild otherGuild = getOtherGuild(event.getGuild());
        Member otherGuildMember = otherGuild.getMember(event.getUser());
        if(otherGuildMember == null) {
            return;
        }
        if(hasRole(otherGuildMember.getRoles(), roleMember)) {
            event.getGuild().getController().addSingleRoleToMember(event.getMember(), getRoleByName(event.getGuild().getRoles(), roleMember))
                                            .reason("cloned from " + otherGuild.getName()).queue();
        } else if(hasRole(otherGuildMember.getRoles(), roleMute)) {
            event.getGuild().getController().addSingleRoleToMember(event.getMember(), getRoleByName(event.getGuild().getRoles(), roleMute))
                    .reason("cloned from " + otherGuild.getName()).queue();
        }
    }

    @Override
    public void onGuildMemberLeave(GuildMemberLeaveEvent event) {
        if(!verifyGuild(event.getGuild())) {
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
                && dateBetweenRange(log.getCreationTime(), OffsetDateTime.now(), 10, ChronoUnit.SECONDS)).findFirst().get();
        if(auditLogEntry != null) {
            toUpdateGuild.getController().kick(leaver.getId(), auditLogEntry.getReason() + " / cloned from " + sourceGuild.getName()).queue();
        }
    }

    private boolean dateBetweenRange(OffsetDateTime target, OffsetDateTime compared, int range, TemporalUnit unit) {
        OffsetDateTime start = target.minus(range, unit);
        OffsetDateTime end = target.plus(range, unit);

        return compared.isAfter(start) && compared.isAfter(end);
    }

    private boolean verifyGuild(Guild guild) {
        return guild.getId().equals(guild1.getId()) || guild.getId().equals(guild2.getId());
    }

    private Guild getOtherGuild(Guild guild) {
        return guild.getId().equals(guild1.getId()) ? guild2 : guild1;
    }


}
