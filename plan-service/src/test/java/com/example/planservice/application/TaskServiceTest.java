package com.example.planservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.example.planservice.application.dto.TaskUpdateServiceRequest;
import com.example.planservice.domain.label.Label;
import com.example.planservice.domain.label.repository.LabelRepository;
import com.example.planservice.domain.member.Member;
import com.example.planservice.domain.member.repository.MemberRepository;
import com.example.planservice.domain.memberofplan.MemberOfPlan;
import com.example.planservice.domain.memberofplan.repository.MemberOfPlanRepository;
import com.example.planservice.domain.plan.Plan;
import com.example.planservice.domain.plan.repository.PlanRepository;
import com.example.planservice.domain.tab.Tab;
import com.example.planservice.domain.tab.repository.TabRepository;
import com.example.planservice.domain.task.LabelOfTask;
import com.example.planservice.domain.task.Task;
import com.example.planservice.domain.task.repository.LabelOfTaskRepository;
import com.example.planservice.domain.task.repository.TaskRepository;
import com.example.planservice.exception.ApiException;
import com.example.planservice.exception.ErrorCode;
import com.example.planservice.presentation.dto.request.TaskChangeOrderRequest;
import com.example.planservice.presentation.dto.request.TaskCreateRequest;

@SpringBootTest
@Transactional
class TaskServiceTest {
    @Autowired
    TaskService taskService;

    @Autowired
    TaskRepository taskRepository;

    @Autowired
    TabRepository tabRepository;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    PlanRepository planRepository;

    @Autowired
    MemberOfPlanRepository memberOfPlanRepository;

    @Autowired
    LabelRepository labelRepository;

    @Autowired
    LabelOfTaskRepository labelOfTaskRepository;

    @Test
    @DisplayName("태스크를 생성한다")
    void testCreateTaskOrder() {
        // given
        Plan plan = createPlan();
        Tab tab = createTab(plan);
        Member loginMember = createMemberWithPlan(plan);
        Member taskManager = createMemberWithPlan(plan);

        TaskCreateRequest request = TaskCreateRequest.builder()
            .tabId(tab.getId())
            .managerId(taskManager.getId())
            .name("스프링공부하기")
            .description("1. 책을 편다. \n2. 글자를 읽는다. \n3. 책을닫는다\n")
            .startDate(null)
            .endDate(null)
            .labels(Collections.emptyList())
            .build();

        // when
        Long createdId = taskService.create(loginMember.getId(), request);

        // then
        Task task = taskRepository.findById(createdId).get();

        assertThat(task.getId()).isEqualTo(createdId);
        assertThat(task.getTab().getId()).isEqualTo(request.getTabId());
        assertThat(task.getManager().getId()).isEqualTo(request.getManagerId());
        assertThat(task.getName()).isEqualTo(request.getName());
        assertThat(task.getDescription()).isEqualTo(request.getDescription());
        assertThat(task.getStartDate()).isEqualTo(request.getStartDate());
        assertThat(task.getEndDate()).isEqualTo(request.getEndDate());

        Task lastDummyTask = tab.getLastDummyTask();
        assertThat(task).isEqualTo(lastDummyTask.getPrev());
    }

    @Test
    @DisplayName("담당자가 입력되지 않아도 태스크를 생성할 수 있다")
    void testCreateTaskOrderSuccessNoManager() {
        // given
        Plan plan = createPlan();
        Tab tab = createTab(plan);
        Member loginMember = createMemberWithPlan(plan);

        TaskCreateRequest request = TaskCreateRequest.builder()
            .tabId(tab.getId())
            .managerId(null)
            .name("스프링공부하기")
            .description("1. 책을 편다. \n2. 글자를 읽는다. \n3. 책을닫는다\n")
            .startDate(null)
            .endDate(null)
            .labels(Collections.emptyList())
            .build();

        // when
        Long createdId = taskService.create(loginMember.getId(), request);

        // then
        Task task = taskRepository.findById(createdId).get();

        assertThat(task.getManager()).isNull();
        assertThat(task.getId()).isEqualTo(createdId);
        assertThat(task.getTab().getId()).isEqualTo(request.getTabId());
        assertThat(task.getName()).isEqualTo(request.getName());
        assertThat(task.getDescription()).isEqualTo(request.getDescription());
        assertThat(task.getStartDate()).isEqualTo(request.getStartDate());
        assertThat(task.getEndDate()).isEqualTo(request.getEndDate());

        Task lastDummyTask = tab.getLastDummyTask();
        assertThat(task).isEqualTo(lastDummyTask.getPrev());
    }

    @Test
    @DisplayName("태스크는 플랜에 소속된 사람만 만들 수 있다")
    void testCreateFailNotExistMemberInPlan() {
        // given
        Plan otherPlan = createPlan();
        Member memberInOtherPlan = createMemberWithPlan(otherPlan);

        Plan plan = createPlan();
        Tab tab = createTab(plan);
        Member manager = createMemberWithPlan(plan);

        TaskCreateRequest request = TaskCreateRequest.builder()
            .tabId(tab.getId())
            .managerId(manager.getId())
            .name("스프링공부하기")
            .description("1. 책을 편다. \n2. 글자를 읽는다. \n3. 책을닫는다\n")
            .startDate(null)
            .endDate(null)
            .build();

        // when & then
        assertThatThrownBy(() -> taskService.create(memberInOtherPlan.getId(), request))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining(ErrorCode.MEMBER_NOT_FOUND_IN_PLAN.getMessage());
    }

    @Test
    @DisplayName("태스크는 라벨을 달고 생성할 수 있다")
    void testCreateTaskWithLabels() {
        // given
        Plan plan = createPlan();
        Tab tab = createTab(plan);
        Member loginMember = createMemberWithPlan(plan);
        Member manager = createMemberWithPlan(plan);

        Label label1 = createLabelUsingTest(plan);
        Label label2 = createLabelUsingTest(plan);
        Label notUsingLabel = createLabelUsingTest(plan);

        TaskCreateRequest request = TaskCreateRequest.builder()
            .planId(plan.getId())
            .tabId(tab.getId())
            .managerId(manager.getId())
            .name("스프링공부하기")
            .description("1. 책을 편다. \n2. 글자를 읽는다. \n3. 책을닫는다\n")
            .labels(List.of(label1.getId(), label2.getId()))
            .build();

        // when
        Long createdId = taskService.create(loginMember.getId(), request);

        // then
        List<Label> labels = labelOfTaskRepository.findAll().stream()
            .filter(labelOfTask -> labelOfTask.getTask().getId() == createdId)
            .map(LabelOfTask::getLabel)
            .toList();
        assertThat(labels).hasSize(2)
            .contains(label1, label2);

        Task task = taskRepository.findById(createdId).get();
        assertThat(task.getId()).isEqualTo(createdId);
    }

    @Test
    @DisplayName("태스크를 만들 때 존재하지 않는 라벨을 달아도, 존재하는 라벨들만 사용해서 LabelOfTask를 만든다")
    void testCreateTaskFailLabelNotFound() throws Exception {
        // given
        Plan plan = createPlan();
        Tab tab = createTab(plan);
        Member loginMember = createMemberWithPlan(plan);
        Member manager = createMemberWithPlan(plan);

        Label label1 = createLabelUsingTest(plan);
        Long notRegisteredLabelId = 123123L;

        TaskCreateRequest request = TaskCreateRequest.builder()
            .planId(plan.getId())
            .tabId(tab.getId())
            .managerId(manager.getId())
            .name("스프링공부하기")
            .description("1. 책을 편다. \n2. 글자를 읽는다. \n3. 책을닫는다\n")
            .labels(List.of(label1.getId(), notRegisteredLabelId))
            .build();

        // when
        Long createdId = taskService.create(loginMember.getId(), request);

        // then
        List<Label> labels = labelOfTaskRepository.findAll().stream()
            .filter(labelOfTask -> labelOfTask.getTask().getId() == createdId)
            .map(LabelOfTask::getLabel)
            .toList();

        assertThat(labels).hasSize(1).contains(label1);
        Task task = taskRepository.findById(createdId).get();
        assertThat(task.getId()).isEqualTo(createdId);
    }

    @Test
    @DisplayName("태스크를 만들 때 다른 플랜의 라벨을 달면, 우리 플랜에 존재하는 라벨만 사용해서 LabelOfTask를 만든다")
    void testCreateTaskFailLabelNotFoundThisPlan() throws Exception {
        // given
        Plan otherPlan = createPlan();
        Label otherPlansLabel = createLabelUsingTest(otherPlan);

        Plan plan = createPlan();
        Tab tab = createTab(plan);
        Member loginMember = createMemberWithPlan(plan);
        Member manager = createMemberWithPlan(plan);

        TaskCreateRequest request = TaskCreateRequest.builder()
            .planId(plan.getId())
            .tabId(tab.getId())
            .managerId(manager.getId())
            .name("스프링공부하기")
            .description("1. 책을 편다. \n2. 글자를 읽는다. \n3. 책을닫는다\n")
            .labels(List.of(otherPlansLabel.getId()))
            .build();

        // when
        Long createdId = taskService.create(loginMember.getId(), request);

        // then
        List<Label> labels = labelOfTaskRepository.findAll().stream()
            .filter(labelOfTask -> labelOfTask.getTask().getId() == createdId)
            .map(LabelOfTask::getLabel)
            .toList();
        assertThat(labels).isEmpty();

        Task task = taskRepository.findById(createdId).get();
        assertThat(task.getId()).isEqualTo(createdId);
    }

    @Test
    @DisplayName("존재하지 않는 플랜에는 태스크를 만들 수 없다")
    void createFailNotExistPlan() {
        // given
        Long notRegisteredPlanId = 3L;
        Long notRegisteredTabId = 2L;
        Plan otherPlan = createPlan();

        Member loginMember = createMemberWithPlan(otherPlan);
        Member manager = createMemberWithPlan(otherPlan);

        TaskCreateRequest request = TaskCreateRequest.builder()
            .planId(notRegisteredPlanId)
            .tabId(notRegisteredTabId)
            .managerId(manager.getId())
            .name("스프링공부하기")
            .description("1. 책을 편다. \n2. 글자를 읽는다. \n3. 책을닫는다\n")
            .startDate(null)
            .endDate(null)
            .labels(Collections.emptyList())
            .build();

        // when & then
        assertThatThrownBy(() -> taskService.create(loginMember.getId(), request))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining(ErrorCode.TAB_NOT_FOUND_IN_PLAN.getMessage());
    }

    @Test
    @DisplayName("태스크의 순서를 변경한다")
    void testChangeOrder() throws Exception {
        // given
        Plan plan = createPlan();
        Tab tab = createTab(plan);
        Member loginMember = createMemberWithPlan(plan);
        Task task1 = createTaskWithTab(tab);
        Task task2 = createTaskWithTab(tab);
        Task task3 = createTaskWithTab(tab);

        TaskChangeOrderRequest request = TaskChangeOrderRequest.builder()
            .planId(plan.getId())
            .targetTabId(tab.getId())
            .newPrevId(task3.getId())
            .targetId(task2.getId())
            .build();

        // when
        List<Long> taskIds = taskService.changeOrder(loginMember.getId(), request);

        // then
        assertThat(taskIds).hasSize(3)
            .containsExactly(task1.getId(), task3.getId(), task2.getId());
    }

    @Test
    @DisplayName("newPrev가 null이면 태스크를 탭의 첫 번째 순서로 옮긴다")
    void testChangeOrderIfNewPrevIsNull() throws Exception {
        // given
        Plan plan = createPlan();
        Tab tab = createTab(plan);
        Member loginMember = createMemberWithPlan(plan);
        Task task1 = createTaskWithTab(tab);
        Task task2 = createTaskWithTab(tab);

        TaskChangeOrderRequest request = TaskChangeOrderRequest.builder()
            .planId(plan.getId())
            .targetTabId(tab.getId())
            .newPrevId(null)
            .targetId(task2.getId())
            .build();

        // when
        List<Long> taskIds = taskService.changeOrder(loginMember.getId(), request);

        // then
        assertThat(taskIds).hasSize(2)
            .containsExactly(task2.getId(), task1.getId());
    }


    @Test
    @DisplayName("다른 탭에서 넘어온 태스크를 특정 탭에 추가한다")
    void testChangeOrderTargetIsFromOtherTab() throws Exception {
        // given
        Plan plan = createPlan();
        Tab tab = createTab(plan);
        Tab otherTab = createTab(plan);
        Member loginMember = createMemberWithPlan(plan);
        Task task1 = createTaskWithTab(tab);
        Task task2 = createTaskWithTab(tab);
        Task target = createTaskWithTab(otherTab);

        TaskChangeOrderRequest request = TaskChangeOrderRequest.builder()
            .planId(plan.getId())
            .targetTabId(tab.getId())
            .newPrevId(task2.getId())
            .targetId(target.getId())
            .build();

        // when
        List<Long> taskIds = taskService.changeOrder(loginMember.getId(), request);

        // then
        assertThat(taskIds).hasSize(3)
            .containsExactly(task1.getId(), task2.getId(), target.getId());
    }

    @Test
    @DisplayName("태스크의 순서는 플랜에 소속된 멤버만 바꿀 수 있다")
    void testChangeOrderFailNotAuthorized() throws Exception {
        // given
        Plan plan = createPlan();
        Plan otherPlan = createPlan();
        Tab tab = createTab(plan);
        Member unauthorizedMember = createMemberWithPlan(otherPlan);
        Task task1 = createTaskWithTab(tab);
        Task task2 = createTaskWithTab(tab);

        TaskChangeOrderRequest request = TaskChangeOrderRequest.builder()
            .planId(plan.getId())
            .targetTabId(tab.getId())
            .newPrevId(task2.getId())
            .targetId(task1.getId())
            .build();

        // when & then
        assertThatThrownBy(() -> taskService.changeOrder(unauthorizedMember.getId(), request))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining(ErrorCode.MEMBER_NOT_FOUND_IN_PLAN.getMessage());
    }

    @Test
    @DisplayName("존재하지 않는 탭으로 태스크를 옮길 수 없다")
    void testChangeOrderFailTabNotFound() throws Exception {
        // given
        Long notRegisteredTabId = 1243124512L;
        Plan plan = createPlan();
        Plan otherPlan = createPlan();
        Tab tab = createTab(plan);
        Member loginMember = createMemberWithPlan(plan);
        Task task1 = createTaskWithTab(tab);

        TaskChangeOrderRequest request = TaskChangeOrderRequest.builder()
            .planId(plan.getId())
            .targetTabId(notRegisteredTabId)
            .newPrevId(null)
            .targetId(task1.getId())
            .build();

        // when & then
        assertThatThrownBy(() -> taskService.changeOrder(loginMember.getId(), request))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining(ErrorCode.TAB_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("존재하지 않는 태스크를 옮길 수 없다")
    void testChangeOrderFailTaskNotFound() throws Exception {
        // given
        Long notRegisteredTaskId = 1243124512L;
        Plan plan = createPlan();
        Tab tab = createTab(plan);
        Member loginMember = createMemberWithPlan(plan);

        TaskChangeOrderRequest request = TaskChangeOrderRequest.builder()
            .planId(plan.getId())
            .targetTabId(tab.getId())
            .newPrevId(null)
            .targetId(notRegisteredTaskId)
            .build();

        // when & then
        assertThatThrownBy(() -> taskService.changeOrder(loginMember.getId(), request))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining(ErrorCode.TASK_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("더미태스크는 순서를 변경할 수 없다")
    void testChangeOrderFailBecauseTargetIsDummy() throws Exception {
        // given
        Plan plan = createPlan();
        Tab tab = createTab(plan);
        Member loginMember = createMemberWithPlan(plan);
        Task task1 = createTaskWithTab(tab);

        List<Task> tasks = taskRepository.findAllByTabId(tab.getId());

        List<Task> dummies = tasks.stream()
            .filter(task -> task.equals(tab.getFirstDummyTask()) || task.equals(tab.getLastDummyTask()))
            .toList();

        assertThat(dummies).hasSize(2);
        for (Task dummy : dummies) {
            TaskChangeOrderRequest request = TaskChangeOrderRequest.builder()
                .planId(plan.getId())
                .targetTabId(tab.getId())
                .newPrevId(task1.getId())
                .targetId(dummy.getId())
                .build();

            // when & then
            assertThatThrownBy(() -> taskService.changeOrder(loginMember.getId(), request))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining(ErrorCode.TASK_NOT_FOUND.getMessage());
        }
    }

    @Test
    @DisplayName("태스크를 옮길 때, 다른 플랜으로 옮길 수는 없다")
    void changeOrderFailOtherPlan() throws Exception {
        // given
        Plan plan = createPlan();
        Tab tab = createTab(plan);
        Member loginMember = createMemberWithPlan(plan);

        Plan otherPlan = createPlan();
        createMemberOfPlan(otherPlan, loginMember);
        Tab tabInOtherPlan = createTab(otherPlan);
        Task taskInOtherTask = createTaskWithTab(tabInOtherPlan);

        // when
        TaskChangeOrderRequest request = TaskChangeOrderRequest.builder()
            .planId(plan.getId())
            .targetTabId(tab.getId())
            .newPrevId(null)
            .targetId(taskInOtherTask.getId())
            .build();

        // when & then
        assertThatThrownBy(() -> taskService.changeOrder(loginMember.getId(), request))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining(ErrorCode.AUTHORIZATION_FAIL.getMessage());
    }

    @Test
    @DisplayName("태스크의 정보를 수정한다")
    void testUpdateContents() throws Exception {
        // given
        Plan plan = createPlan();
        Tab tab = createTab(plan);
        Member loginMember = createMemberWithPlan(plan);
        Member taskManager = createMemberWithPlan(plan);
        Task task = createTaskWithTab(tab);
        Label label1 = Label.builder()
            .plan(plan)
            .name("라벨1")
            .build();
        Label label2 = Label.builder()
            .plan(plan)
            .name("라벨2")
            .build();
        labelRepository.saveAll(List.of(label1, label2));

        labelOfTaskRepository.save(LabelOfTask.builder().label(label1).task(task).build());

        TaskUpdateServiceRequest request = TaskUpdateServiceRequest.builder()
            .taskId(task.getId())
            .memberId(loginMember.getId())
            .planId(plan.getId())
            .managerId(taskManager.getId())
            .name("변경된 이름")
            .description("이렇게 설명할게요")
            .startDate(LocalDateTime.now().minusDays(10))
            .endDate(LocalDateTime.now().plusDays(2))
            .labels(List.of(label1.getId(), label2.getId()))
            .build();

        // when
        Long updatedId = taskService.updateContents(request);

        // then
        Task updatedTask = taskRepository.findById(updatedId).get();
        assertThat(updatedTask).isEqualTo(task);
        assertThat(updatedTask)
            .extracting(Task::getTab, Task::getManager, Task::getName,
                Task::getDescription, Task::getStartDate, Task::getEndDate)
            .containsExactly(tab, taskManager, request.getName(),
                request.getDescription(), request.getStartDate(), request.getEndDate());

        List<LabelOfTask> labelOfTaskList = labelOfTaskRepository.findAllByTaskId(updatedId);
        assertThat(labelOfTaskList).hasSize(2)
            .extracting(LabelOfTask::getLabel)
            .contains(label1, label2);
    }

    @Test
    @DisplayName("태스크의 manager가 없어도 정보를 수정할 수 있다")
    void testUpdateContentsSuccessNoManager() throws Exception {
        // given
        Plan plan = createPlan();
        Tab tab = createTab(plan);
        Member loginMember = createMemberWithPlan(plan);
        Task task = createTaskWithTab(tab);
        Label label1 = Label.builder()
            .plan(plan)
            .name("라벨1")
            .build();
        Label label2 = Label.builder()
            .plan(plan)
            .name("라벨2")
            .build();
        labelRepository.saveAll(List.of(label1, label2));

        labelOfTaskRepository.save(LabelOfTask.builder().label(label1).task(task).build());

        TaskUpdateServiceRequest request = TaskUpdateServiceRequest.builder()
            .taskId(task.getId())
            .memberId(loginMember.getId())
            .planId(plan.getId())
            .managerId(null)
            .name("변경된 이름")
            .description("이렇게 설명할게요")
            .startDate(LocalDateTime.now().minusDays(10))
            .endDate(LocalDateTime.now().plusDays(2))
            .labels(List.of(label1.getId(), label2.getId()))
            .build();

        // when
        Long updatedId = taskService.updateContents(request);

        // then
        Task updatedTask = taskRepository.findById(updatedId).get();
        assertThat(updatedTask).isEqualTo(task);
        assertThat(updatedTask)
            .extracting(Task::getTab, Task::getManager, Task::getName,
                Task::getDescription, Task::getStartDate, Task::getEndDate)
            .containsExactly(tab, null, request.getName(),
                request.getDescription(), request.getStartDate(), request.getEndDate());

        List<LabelOfTask> labelOfTaskList = labelOfTaskRepository.findAllByTaskId(updatedId);
        assertThat(labelOfTaskList).hasSize(2)
            .extracting(LabelOfTask::getLabel)
            .contains(label1, label2);
    }

    private Task createTaskWithTab(Tab tab) {
        Task task = Task.builder()
            .tab(tab)
            .build();
        Task lastDummy = tab.getLastDummyTask();
        lastDummy.putInFront(task);

        taskRepository.save(task);
        return task;
    }

    private Plan createPlan() {
        Plan plan = Plan.builder().build();
        planRepository.save(plan);
        return plan;
    }

    private Member createMemberWithPlan(Plan plan) {
        Member member = createMember();
        createMemberOfPlan(plan, member);
        return member;
    }

    private Tab createTab(Plan plan) {
        Tab.TabBuilder builder = Tab.builder();
        if (plan != null) {
            builder.plan(plan);
        }
        Tab tab = builder.build();
        tabRepository.save(tab);

        List<Task> dummies = Task.createFirstAndLastDummy(tab);
        Task firstDummy = dummies.get(0);
        Task lastDummy = dummies.get(1);
        taskRepository.save(firstDummy);
        taskRepository.save(lastDummy);
        return tab;
    }

    private Member createMember() {
        Member member = Member.builder().build();
        memberRepository.save(member);
        return member;
    }

    private Task createTask() {
        Task task = Task.builder().build();
        taskRepository.save(task);
        return task;
    }

    @NotNull
    private MemberOfPlan createMemberOfPlan(Plan plan, Member member) {
        MemberOfPlan memberOfPlan = MemberOfPlan.builder()
            .plan(plan)
            .member(member)
            .build();
        memberOfPlanRepository.save(memberOfPlan);
        return memberOfPlan;
    }

    private Label createLabelUsingTest(Plan plan) {
        Label label = Label.builder()
            .plan(plan)
            .build();
        labelRepository.save(label);
        return label;
    }

}