package com.tasklean.api.domain.tag;

import com.tasklean.api.common.BaseEntity;
import com.tasklean.api.domain.group.Group;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "tag", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"group_id", "name"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tag extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_tag")
    private Long idTag;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "color", length = 7)
    private String color;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;
}