package com.example.planservice.application;

import java.util.List;
import java.util.Objects;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.planservice.domain.label.repository.LabelRepository;
import com.example.planservice.domain.member.Member;
import com.example.planservice.domain.member.repository.MemberRepository;
import com.example.planservice.domain.plan.Plan;
import com.example.planservice.domain.tab.Tab;
import com.example.planservice.domain.tab.repository.TabRepository;
import com.example.planservice.domain.task.LabelOfTask;
import com.example.planservice.domain.task.Task;
import com.example.planservice.domain.task.repository.LabelOfTaskRepository;
import com.example.planservice.domain.task.repository.TaskRepository;
import com.example.planservice.exception.ApiException;
import com.example.planservice.exception.ErrorCode;
import com.example.planservice.presentation.dto.request.TaskCreateRequest;
import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TaskService {
    private final TaskRepository taskRepository;
    private final TabRepository tabRepository;
    private final MemberRepository memberRepository;
    private final PlanMembershipVerificationService planMembershipVerificationService;
    private final LabelRepository labelRepository;
    private final LabelOfTaskRepository labelOfTaskRepository;

    @Transactional
    public Long createTask(Long memberId, TaskCreateRequest request) {
        try {
            Plan plan = planMembershipVerificationService.verifyAndReturnPlan(request.getPlanId(), memberId);
            Tab tab = tabRepository.findById(request.getTabId())
                .orElseThrow(() -> new ApiException(ErrorCode.TAB_NOT_FOUND_IN_PLAN));
            Member manager = memberRepository.findById(request.getManagerId())
                .orElseThrow(() -> new ApiException(ErrorCode.MEMBER_NOT_FOUND_IN_PLAN));

            Task task = Task.builder()
                .tab(tab)
                .manager(manager)
                .name(request.getName())
                .description(request.getDescription())
                .build();

            Task oldLastTask = tab.makeLastTask(task);
            if (oldLastTask != null) {
                oldLastTask.connect(task);
            }

            Task savedTask = taskRepository.save(task);

            saveAllLabelOfTask(request.getLabels(), task, plan);
            return savedTask.getId();
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new ApiException(ErrorCode.REQUEST_CONFLICT);
        }
    }

    private void saveAllLabelOfTask(List<Long> labelIds, Task task, Plan plan) {
        List<LabelOfTask> labelsOfTask = labelRepository.findAllById(labelIds).stream()
            .filter(label -> Objects.equals(label.getPlan(), plan))
            .map(label -> LabelOfTask.create(label, task))
            .toList();
        labelOfTaskRepository.saveAll(labelsOfTask);
    }

}
