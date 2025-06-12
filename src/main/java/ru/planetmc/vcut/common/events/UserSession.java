package ru.planetmc.vcut.common.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSession {
    private String userId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private List<UserActivity> activities = new ArrayList<>();
    private Integer uploadCount = 0;
    private Integer cutCount = 0;

    public UserSession addActivity(UserActivity activity) {
        if (startTime == null || activity.getTimestamp().isBefore(startTime)) {
            startTime = activity.getTimestamp();
        }
        if (endTime == null || activity.getTimestamp().isAfter(endTime)) {
            endTime = activity.getTimestamp();
        }

        activities.add(activity);

        if ("UPLOAD".equals(activity.getActivityType())) {
            uploadCount++;
        } else if ("CUT".equals(activity.getActivityType())) {
            cutCount++;
        }

        return this;
    }

    public UserSession merge(UserSession other) {
        if (other.getStartTime().isBefore(this.startTime)) {
            this.startTime = other.getStartTime();
        }
        if (other.getEndTime().isAfter(this.endTime)) {
            this.endTime = other.getEndTime();
        }

        this.activities.addAll(other.getActivities());
        this.uploadCount += other.getUploadCount();
        this.cutCount += other.getCutCount();

        return this;
    }
}