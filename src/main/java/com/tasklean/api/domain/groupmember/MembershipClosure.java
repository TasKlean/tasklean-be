package com.tasklean.api.domain.groupmember;

import com.tasklean.api.domain.auditlog.AuditAction;
import com.tasklean.api.domain.auditlog.AuditChanges;
import com.tasklean.api.domain.auditlog.AuditEntityType;
import com.tasklean.api.domain.auditlog.AuditLogService;
import com.tasklean.api.domain.group.Group;
import com.tasklean.api.domain.group.GroupService;
import com.tasklean.api.domain.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Ends a user's household life when their account closes, and keeps the households they leave
 * behind usable. A membership is not a credential, but it is a live relationship: left active, a
 * closed account stays on every roster as a current member and its avatar stays readable by them.
 *
 * <p>Its own component rather than a method on {@link GroupMemberService} because repairing a
 * household that just lost its last admin reaches back into {@link GroupService}, which already
 * depends on that service.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MembershipClosure {

    private final GroupMemberRepository groupMemberRepository;
    private final GroupService groupService;
    private final AuditLogService auditLogService;
    private final Clock clock;

    /**
     * Ends every active membership the user holds, then keeps each household manageable: the
     * longest-standing surviving member inherits {@code GROUP_ADMIN} when the departure took the
     * last one, and a household with nobody left in it is deleted.
     *
     * <p>Deliberately skips the last-admin guard the ordinary removal path applies. That rule
     * protects a living household from losing its only admin; refusing here would instead trap
     * somebody in an account they are entitled to close, so succession answers it rather than a
     * refusal.
     *
     * @param user         the account being closed
     * @param callerUserId the id of the authenticated caller, attributed as having ended the
     *                     memberships
     */
    @Transactional
    public void closeFor(User user, Long callerUserId) {
        List<GroupMember> memberships =
                groupMemberRepository.findByUserIdUserAndIsActiveTrue(user.getIdUser());
        boolean left = callerUserId.equals(user.getIdUser());

        LocalDateTime now = LocalDateTime.now(clock);
        for (GroupMember membership : memberships) {
            end(membership, left, now, callerUserId);
        }

        // Every membership is ended before any household is repaired: a succession check run inside
        // the loop above would still count the departing member as their group's admin.
        memberships.stream()
                .map(GroupMember::getGroup)
                // A membership can outlive its group's soft-deletion, and a deleted household has
                // nothing left to administer.
                .filter(group -> Boolean.TRUE.equals(group.getIsActive()))
                .forEach(group -> succeed(group, callerUserId));

        if (!memberships.isEmpty()) {
            log.info("Memberships ended with the account: userUid={} groups={}",
                    user.getUid(), memberships.size());
        }
    }

    private void end(GroupMember membership, boolean left, LocalDateTime now, Long callerUserId) {
        membership.setIsActive(false);
        membership.setDateLeft(now);
        membership.setRemovedBy(left ? membership : actingMember(callerUserId, membership.getGroup()));
        groupMemberRepository.save(membership);
        auditLogService.recordEvent(AuditEntityType.GROUP_MEMBER, membership.getIdGroupMember(),
                left ? AuditAction.MEMBER_LEFT : AuditAction.MEMBER_REMOVED,
                "Membership ended with the account's closure", membership.getGroup());
    }

    // One query answers all three questions: whether anybody is left, whether an admin is among
    // them, and who inherits if not.
    private void succeed(Group group, Long callerUserId) {
        List<GroupMember> survivors = groupMemberRepository.findSurvivingMemberships(group.getIdGroup());
        if (survivors.isEmpty()) {
            // An empty household still holds a live invite code, so whoever has it could join a
            // group nobody can administer, name or delete. Ending it is the honest outcome.
            groupService.deleteGroup(group.getUid(), callerUserId);
            return;
        }
        if (survivors.stream().anyMatch(member -> member.getRole() == GroupRole.GROUP_ADMIN)) {
            return;
        }
        promote(survivors.getFirst(), group);
    }

    // The heir is the longest-standing survivor, so the choice is the same on any two runs and
    // cannot be steered by whoever happens to be looked at first.
    private void promote(GroupMember heir, Group group) {
        AuditChanges changes = AuditChanges.create()
                .setVisible("role", heir::getRole, heir::setRole, GroupRole.GROUP_ADMIN);
        groupMemberRepository.save(heir);
        // Authorization-relevant state change → INFO.
        log.info("Group admin inherited: groupUid={} memberUid={}", group.getUid(), heir.getUid());
        auditLogService.recordEvent(AuditEntityType.GROUP_MEMBER, heir.getIdGroupMember(),
                AuditAction.ROLE_CHANGED, "Inherited group admin when the last admin's account closed",
                group, changes);
    }

    // Attribution points at a membership, not a user, so a caller who is not a member of the group
    // has nothing to point at and the column stays null - as everywhere else it is written.
    private GroupMember actingMember(Long callerUserId, Group group) {
        return groupMemberRepository.findByUserIdUserAndGroupIdGroup(callerUserId, group.getIdGroup())
                .filter(member -> Boolean.TRUE.equals(member.getIsActive()))
                .orElse(null);
    }
}
