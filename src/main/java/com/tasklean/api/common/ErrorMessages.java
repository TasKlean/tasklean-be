package com.tasklean.api.common;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ErrorMessages {

    public static final String USER_NOT_FOUND = "User not found";
    public static final String GROUP_NOT_FOUND = "Group not found";
    public static final String GROUP_MEMBER_NOT_FOUND = "Group member not found";
    public static final String TASK_NOT_FOUND = "Task not found";
    public static final String CATEGORY_NOT_FOUND = "Category not found";
    public static final String TAG_NOT_FOUND = "Tag not found";
    public static final String DEVICE_NOT_FOUND = "Device not found";
    public static final String NOTIFICATION_NOT_FOUND = "Notification not found";
    public static final String ASSIGNED_MEMBER_NOT_FOUND = "Assigned member not found";
}
